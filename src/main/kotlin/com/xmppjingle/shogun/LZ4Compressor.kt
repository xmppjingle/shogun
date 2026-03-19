package com.xmppjingle.shogun

/**
 * LZ4-inspired fast compressor.
 * Prioritizes speed over compression ratio, using a hash-based approach
 * for finding matches in a sliding window.
 *
 * Format per sequence:
 * - Token byte: [literal_len:4][match_len:4]
 * - If literal_len == 15: additional length bytes (each 255 adds 255, last < 255)
 * - Literal bytes
 * - Offset (2 bytes, little-endian) - distance back to match
 * - If match_len == 15: additional length bytes
 */
class LZ4Compressor : Compressor {
    override val name = "lz4"

    companion object {
        const val MIN_MATCH = 4
        const val HASH_LOG = 14
        const val HASH_SIZE = 1 shl HASH_LOG
        const val MAX_DISTANCE = 65535
        const val ML_BITS = 4
        const val ML_MASK = (1 shl ML_BITS) - 1
        const val RUN_BITS = 4
        const val RUN_MASK = (1 shl RUN_BITS) - 1
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        if (input.size < MIN_MATCH) {
            // Too small to compress - just store with header
            return byteArrayOf(0) + intToBytes(input.size) + input
        }

        val out = mutableListOf<Byte>()
        out.add(1) // marker for compressed data
        out.addAll(intToBytes(input.size).toList())

        val hashTable = IntArray(HASH_SIZE) { -1 }
        var anchor = 0  // start of current literal run
        var ip = 0      // current position

        while (ip < input.size - MIN_MATCH) {
            // Hash current position
            val h = hash(input, ip)
            val ref = hashTable[h]
            hashTable[h] = ip

            // Check for match
            if (ref >= 0 && ip - ref <= MAX_DISTANCE && matchAt(input, ref, ip)) {
                // Found a match - extend it
                var matchLen = MIN_MATCH
                while (ip + matchLen < input.size && ref + matchLen < ip &&
                    input[ref + matchLen] == input[ip + matchLen]) {
                    matchLen++
                }

                // Encode literal run length
                val litLen = ip - anchor
                val token: Int
                if (litLen >= RUN_MASK) {
                    token = (RUN_MASK shl ML_BITS)
                } else {
                    token = (litLen shl ML_BITS)
                }

                // Encode match length
                val ml = matchLen - MIN_MATCH
                val tokenByte = if (ml >= ML_MASK) {
                    token or ML_MASK
                } else {
                    token or ml
                }

                out.add(tokenByte.toByte())

                // Write extra literal length bytes
                if (litLen >= RUN_MASK) {
                    var remaining = litLen - RUN_MASK
                    while (remaining >= 255) {
                        out.add(255.toByte())
                        remaining -= 255
                    }
                    out.add(remaining.toByte())
                }

                // Write literals
                for (i in anchor until ip) {
                    out.add(input[i])
                }

                // Write offset (little-endian)
                val offset = ip - ref
                out.add((offset and 0xFF).toByte())
                out.add(((offset shr 8) and 0xFF).toByte())

                // Write extra match length bytes
                if (ml >= ML_MASK) {
                    var remaining = ml - ML_MASK
                    while (remaining >= 255) {
                        out.add(255.toByte())
                        remaining -= 255
                    }
                    out.add(remaining.toByte())
                }

                ip += matchLen
                anchor = ip
            } else {
                ip++
            }
        }

        // Write final literals
        val litLen = input.size - anchor
        if (litLen > 0) {
            val token = if (litLen >= RUN_MASK) (RUN_MASK shl ML_BITS) else (litLen shl ML_BITS)
            out.add(token.toByte())

            if (litLen >= RUN_MASK) {
                var remaining = litLen - RUN_MASK
                while (remaining >= 255) {
                    out.add(255.toByte())
                    remaining -= 255
                }
                out.add(remaining.toByte())
            }

            for (i in anchor until input.size) {
                out.add(input[i])
            }
        }

        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val marker = input[0].toInt() and 0xFF
        if (marker == 0) {
            // Uncompressed
            val size = bytesToInt(input, 1)
            return input.copyOfRange(5, 5 + size)
        }

        val origSize = bytesToInt(input, 1)
        val out = ByteArray(origSize)
        var op = 0
        var ip = 5

        while (ip < input.size && op < origSize) {
            val token = input[ip++].toInt() and 0xFF

            // Decode literal length
            var litLen = token ushr ML_BITS
            if (litLen == RUN_MASK) {
                while (ip < input.size) {
                    val b = input[ip++].toInt() and 0xFF
                    litLen += b
                    if (b < 255) break
                }
            }

            // Copy literals
            val litsToCopy = minOf(litLen, origSize - op, input.size - ip)
            System.arraycopy(input, ip, out, op, litsToCopy)
            ip += litsToCopy
            op += litsToCopy

            if (op >= origSize) break

            // Decode match offset
            if (ip + 1 >= input.size) break
            val offset = (input[ip].toInt() and 0xFF) or ((input[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2

            if (offset == 0) break

            // Decode match length
            var matchLen = (token and ML_MASK) + MIN_MATCH
            if ((token and ML_MASK) == ML_MASK) {
                while (ip < input.size) {
                    val b = input[ip++].toInt() and 0xFF
                    matchLen += b
                    if (b < 255) break
                }
            }

            // Copy match
            val matchStart = op - offset
            if (matchStart < 0) break
            val toCopy = minOf(matchLen, origSize - op)
            for (j in 0 until toCopy) {
                out[op + j] = out[matchStart + j]
            }
            op += toCopy
        }

        return if (op == origSize) out else out.copyOf(op)
    }

    private fun hash(data: ByteArray, pos: Int): Int {
        val v = ((data[pos].toInt() and 0xFF) or
                ((data[pos + 1].toInt() and 0xFF) shl 8) or
                ((data[pos + 2].toInt() and 0xFF) shl 16) or
                ((data[pos + 3].toInt() and 0xFF) shl 24))
        return ((v.toLong() * 2654435761L) ushr (32 - HASH_LOG)).toInt() and (HASH_SIZE - 1)
    }

    private fun matchAt(data: ByteArray, ref: Int, cur: Int): Boolean {
        return ref >= 0 && ref + MIN_MATCH <= data.size && cur + MIN_MATCH <= data.size &&
                data[ref] == data[cur] && data[ref + 1] == data[cur + 1] &&
                data[ref + 2] == data[cur + 2] && data[ref + 3] == data[cur + 3]
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
}
