package com.xmppjingle.shogun

/**
 * Delta compression for sequential messages.
 * Compresses text by encoding only the differences from a reference (base) text.
 * Ideal for SDP renegotiations and SIP dialog sequences.
 *
 * Format: Line-based diff encoding
 * - Lines matching the base are encoded as KEEP markers
 * - Changed/added lines are stored literally
 * - Removed lines are encoded as DELETE markers
 */
class DeltaCompressor(
    private var base: String = ""
) : Compressor {
    override val name = "delta"

    companion object {
        const val MARKER_KEEP: Byte = 0x4B       // 'K' - keep line from base
        const val MARKER_ADD: Byte = 0x41         // 'A' - add new line
        const val MARKER_DELETE: Byte = 0x44      // 'D' - delete line from base
        const val MARKER_FULL: Byte = 0x46        // 'F' - full content (no base)
        const val NEWLINE: Byte = 0x0A            // '\n'
    }

    /** Set or update the base reference for delta computation */
    fun setBase(newBase: String) {
        base = newBase
    }

    fun getBase(): String = base

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        val text = String(input, Charsets.UTF_8)

        if (base.isEmpty()) {
            // No base — store full content with FULL marker
            return byteArrayOf(MARKER_FULL) + input
        }

        val baseLines = base.lines()
        val inputLines = text.lines()

        // LCS-based diff
        val ops = computeDiff(baseLines, inputLines)

        val out = mutableListOf<Byte>()
        // Delta marker (not FULL)
        out.add(0x64) // 'd' for delta

        // Write base line count for validation
        out.addAll(intToBytes(baseLines.size).toList())

        for (op in ops) {
            when (op) {
                is DiffOp.Keep -> {
                    out.add(MARKER_KEEP)
                    out.add(NEWLINE)
                }
                is DiffOp.Add -> {
                    out.add(MARKER_ADD)
                    out.addAll(op.line.toByteArray(Charsets.UTF_8).toList())
                    out.add(NEWLINE)
                }
                is DiffOp.Delete -> {
                    out.add(MARKER_DELETE)
                    out.add(NEWLINE)
                }
            }
        }

        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val marker = input[0]

        if (marker == MARKER_FULL) {
            // Full content
            return input.copyOfRange(1, input.size)
        }

        // Delta decode
        val baseLines = base.lines().toMutableList()
        var pos = 1

        // Read base line count
        val expectedBaseLines = bytesToInt(input, pos)
        pos += 4

        val result = mutableListOf<String>()
        var baseIdx = 0

        while (pos < input.size) {
            val op = input[pos++]
            when (op) {
                MARKER_KEEP -> {
                    if (baseIdx < baseLines.size) {
                        result.add(baseLines[baseIdx])
                    }
                    baseIdx++
                    if (pos < input.size && input[pos] == NEWLINE) pos++ // skip newline
                }
                MARKER_ADD -> {
                    val lineEnd = input.indexOf(NEWLINE, pos)
                    val end = if (lineEnd == -1) input.size else lineEnd
                    result.add(String(input.copyOfRange(pos, end), Charsets.UTF_8))
                    pos = end + 1
                }
                MARKER_DELETE -> {
                    baseIdx++
                    if (pos < input.size && input[pos] == NEWLINE) pos++ // skip newline
                }
                NEWLINE -> { /* skip stray newlines */ }
            }
        }

        return result.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private sealed class DiffOp {
        data class Keep(val line: String) : DiffOp()
        data class Add(val line: String) : DiffOp()
        data class Delete(val line: String) : DiffOp()
    }

    private fun computeDiff(base: List<String>, target: List<String>): List<DiffOp> {
        val m = base.size
        val n = target.size

        // LCS table
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (base[i - 1] == target[j - 1]) dp[i - 1][j - 1] + 1
                else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }

        // Backtrack to produce ops
        val ops = mutableListOf<DiffOp>()
        var i = m; var j = n
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && base[i - 1] == target[j - 1] -> {
                    ops.add(DiffOp.Keep(base[i - 1]))
                    i--; j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    ops.add(DiffOp.Add(target[j - 1]))
                    j--
                }
                else -> {
                    ops.add(DiffOp.Delete(base[i - 1]))
                    i--
                }
            }
        }
        ops.reverse()
        return ops
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(),
        (value shr 8).toByte(), value.toByte()
    )

    private fun bytesToInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun ByteArray.indexOf(byte: Byte, fromIndex: Int): Int {
        for (i in fromIndex until size) {
            if (this[i] == byte) return i
        }
        return -1
    }
}
