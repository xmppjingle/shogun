package com.xmppjingle.shogun

/**
 * Run-Length Encoding compressor for structured fields.
 * Encodes repeated line prefixes and repeated byte sequences efficiently.
 *
 * Format:
 * - Runs of 3+ identical bytes: [ESCAPE, count, byte]
 * - Line prefix dedup: common prefixes in structured text (SDP a= lines etc.)
 * - Literal bytes pass through, with ESCAPE bytes doubled for escaping
 */
class RLECompressor : Compressor {
    override val name = "rle"

    companion object {
        const val ESCAPE: Byte = 0xFF.toByte()
        const val MIN_RUN: Int = 3
        const val MAX_RUN: Int = 255
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val out = mutableListOf<Byte>()
        var i = 0

        while (i < input.size) {
            // Check for a run of identical bytes
            var runLen = 1
            while (i + runLen < input.size && input[i + runLen] == input[i] && runLen < MAX_RUN) {
                runLen++
            }

            if (runLen >= MIN_RUN) {
                out.add(ESCAPE)
                out.add(runLen.toByte())
                out.add(input[i])
                i += runLen
            } else {
                // Literal byte - escape if it's the escape byte
                if (input[i] == ESCAPE) {
                    out.add(ESCAPE)
                    out.add(0) // 0 count means literal escape
                }
                out.add(input[i])
                i++
            }
        }

        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val out = mutableListOf<Byte>()
        var i = 0

        while (i < input.size) {
            if (input[i] == ESCAPE && i + 1 < input.size) {
                val count = input[i + 1].toInt() and 0xFF
                if (count == 0) {
                    // Escaped literal 0xFF
                    i += 2
                    if (i < input.size) {
                        out.add(input[i])
                        i++
                    }
                } else {
                    // Run of 'count' copies of next byte
                    if (i + 2 < input.size) {
                        val byte = input[i + 2]
                        repeat(count) { out.add(byte) }
                    }
                    i += 3
                }
            } else {
                out.add(input[i])
                i++
            }
        }

        return out.toByteArray()
    }
}
