package com.xmppjingle.shogun

/**
 * Asymmetric Numeral Systems (rANS) compressor.
 * More efficient than Huffman for skewed distributions typical of SDP/SIP payloads.
 *
 * Uses range-based ANS (rANS) with a fixed precision of 12 bits for the
 * cumulative frequency table.
 */
class ANSCompressor : Compressor {
    override val name = "ans"

    companion object {
        const val PROB_BITS = 12
        const val PROB_SCALE = 1 shl PROB_BITS  // 4096
        const val RANS_BYTE_L = 1 shl 23
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        // Build frequency table
        val freq = IntArray(256)
        for (b in input) freq[b.toInt() and 0xFF]++

        // Normalize frequencies to sum to PROB_SCALE
        val normFreq = normalizeFrequencies(freq, input.size)

        // Build cumulative frequency table
        val cumFreq = IntArray(257)
        for (i in 0..255) cumFreq[i + 1] = cumFreq[i] + normFreq[i]

        // Encode in reverse order (rANS encodes backwards)
        var state = RANS_BYTE_L.toLong()
        val encoded = mutableListOf<Byte>()

        for (i in input.size - 1 downTo 0) {
            val sym = input[i].toInt() and 0xFF
            val start = cumFreq[sym]
            val symFreq = normFreq[sym]

            if (symFreq == 0) continue // shouldn't happen with proper normalization

            // Renormalize: output bytes while state is too large
            while (state >= (symFreq.toLong() shl (31 - PROB_BITS))) {
                encoded.add((state and 0xFF).toByte())
                state = state shr 8
            }

            // rANS encode step
            state = ((state / symFreq) shl PROB_BITS) + (state % symFreq) + start
        }

        // Flush final state (4 bytes)
        val stateBytes = mutableListOf<Byte>()
        for (j in 0 until 4) {
            stateBytes.add((state and 0xFF).toByte())
            state = state shr 8
        }

        // Header: inputSize(4) + freqTable(256*2) + stateBytes(4) + encodedData
        val header = mutableListOf<Byte>()

        // Input size
        header.addAll(intToBytes(input.size).toList())

        // Frequency table (normalized)
        for (i in 0..255) {
            header.add((normFreq[i] shr 8).toByte())
            header.add((normFreq[i] and 0xFF).toByte())
        }

        // Final state
        header.addAll(stateBytes)

        // Encoded data (reversed since we built it backwards)
        encoded.reverse()
        header.addAll(encoded)

        return header.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        var pos = 0

        // Read input size
        val outputSize = bytesToInt(input, pos); pos += 4

        // Read frequency table
        val normFreq = IntArray(256)
        for (i in 0..255) {
            normFreq[i] = ((input[pos].toInt() and 0xFF) shl 8) or (input[pos + 1].toInt() and 0xFF)
            pos += 2
        }

        // Build cumulative frequency table
        val cumFreq = IntArray(257)
        for (i in 0..255) cumFreq[i + 1] = cumFreq[i] + normFreq[i]

        // Build reverse lookup table for fast symbol finding
        val symLookup = IntArray(PROB_SCALE)
        for (s in 0..255) {
            for (j in cumFreq[s] until cumFreq[s + 1]) {
                if (j < PROB_SCALE) symLookup[j] = s
            }
        }

        // Read final state
        var state = 0L
        for (j in 3 downTo 0) {
            state = (state shl 8) or (input[pos + j].toInt() and 0xFF).toLong()
        }
        pos += 4

        // Decode
        val output = ByteArray(outputSize)
        var encPos = pos

        for (i in 0 until outputSize) {
            // Find symbol from state
            val slot = (state and (PROB_SCALE - 1).toLong()).toInt()
            val sym = symLookup[slot]
            output[i] = sym.toByte()

            val start = cumFreq[sym]
            val symFreq = normFreq[sym]

            // rANS decode step
            state = symFreq.toLong() * (state shr PROB_BITS) + (state and (PROB_SCALE - 1).toLong()) - start

            // Renormalize
            while (state < RANS_BYTE_L && encPos < input.size) {
                state = (state shl 8) or (input[encPos].toInt() and 0xFF).toLong()
                encPos++
            }
        }

        return output
    }

    private fun normalizeFrequencies(freq: IntArray, total: Int): IntArray {
        val norm = IntArray(256)
        var normTotal = 0

        // First pass: scale frequencies
        for (i in 0..255) {
            if (freq[i] > 0) {
                norm[i] = maxOf(1, (freq[i].toLong() * PROB_SCALE / total).toInt())
                normTotal += norm[i]
            }
        }

        // Adjust to exactly PROB_SCALE
        while (normTotal != PROB_SCALE) {
            // Find the symbol with the largest frequency to adjust
            val maxIdx = (0..255).filter { norm[it] > 1 || (normTotal < PROB_SCALE && norm[it] > 0) }
                .maxByOrNull { freq[it] } ?: break

            if (normTotal > PROB_SCALE) {
                if (norm[maxIdx] > 1) {
                    norm[maxIdx]--
                    normTotal--
                } else break
            } else {
                norm[maxIdx]++
                normTotal++
            }
        }

        return norm
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
