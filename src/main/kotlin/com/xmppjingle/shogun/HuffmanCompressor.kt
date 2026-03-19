package com.xmppjingle.shogun

import java.util.PriorityQueue

/**
 * Huffman coding compressor.
 * Builds a frequency-based binary tree and encodes each byte with variable-length bit codes.
 */
class HuffmanCompressor : Compressor {
    override val name = "huffman"

    private sealed class Node(val freq: Int) : Comparable<Node> {
        override fun compareTo(other: Node) = this.freq - other.freq
    }
    private class Leaf(freq: Int, val byte: Byte) : Node(freq)
    private class Internal(val left: Node, val right: Node) : Node(left.freq + right.freq)

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        // Build frequency table
        val freq = IntArray(256)
        for (b in input) freq[b.toInt() and 0xFF]++

        // Count distinct symbols
        val distinctCount = freq.count { it > 0 }
        if (distinctCount == 1) {
            // Single symbol: header = [1 byte: symbol, 4 bytes: count]
            val symbol = freq.indexOfFirst { it > 0 }.toByte()
            return byteArrayOf(0, symbol) + intToBytes(input.size)
        }

        // Build Huffman tree
        val pq = PriorityQueue<Node>()
        for (i in 0..255) {
            if (freq[i] > 0) pq.add(Leaf(freq[i], i.toByte()))
        }
        while (pq.size > 1) {
            val left = pq.poll()
            val right = pq.poll()
            pq.add(Internal(left, right))
        }
        val root = pq.poll()

        // Build code table
        val codes = Array(256) { "" }
        fun buildCodes(node: Node, prefix: String) {
            when (node) {
                is Leaf -> codes[node.byte.toInt() and 0xFF] = prefix.ifEmpty { "0" }
                is Internal -> {
                    buildCodes(node.left, prefix + "0")
                    buildCodes(node.right, prefix + "1")
                }
            }
        }
        buildCodes(root, "")

        // Encode data as bit string, then pack into bytes
        val bits = StringBuilder()
        for (b in input) bits.append(codes[b.toInt() and 0xFF])

        val totalBits = bits.length
        val packedSize = (totalBits + 7) / 8
        val packed = ByteArray(packedSize)
        for (i in 0 until totalBits) {
            if (bits[i] == '1') {
                packed[i / 8] = (packed[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
            }
        }

        // Header: marker(1) + symbolCount(2) + [symbol(1)+codeLen(1)+code(variable)]... + totalBits(4) + packed
        val header = mutableListOf<Byte>()
        header.add(1) // marker for normal Huffman

        // Write symbol count
        header.add((distinctCount shr 8).toByte())
        header.add((distinctCount and 0xFF).toByte())

        // Write code table
        for (i in 0..255) {
            if (codes[i].isNotEmpty()) {
                header.add(i.toByte())
                val codeLen = codes[i].length
                header.add(codeLen.toByte())
                // Pack code bits
                val codeBytes = (codeLen + 7) / 8
                for (cb in 0 until codeBytes) {
                    var byte = 0
                    for (bit in 0 until 8) {
                        val idx = cb * 8 + bit
                        if (idx < codeLen && codes[i][idx] == '1') {
                            byte = byte or (1 shl (7 - bit))
                        }
                    }
                    header.add(byte.toByte())
                }
            }
        }

        // Total bits for knowing when to stop
        header.addAll(intToBytes(totalBits).toList())

        return header.toByteArray() + packed
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        var pos = 0
        val marker = input[pos++].toInt() and 0xFF

        if (marker == 0) {
            // Single symbol encoding
            val symbol = input[pos++]
            val count = bytesToInt(input, pos)
            return ByteArray(count) { symbol }
        }

        // Read symbol count
        val symbolCount = ((input[pos].toInt() and 0xFF) shl 8) or (input[pos + 1].toInt() and 0xFF)
        pos += 2

        // Read code table
        val codeToSymbol = HashMap<String, Byte>()
        for (s in 0 until symbolCount) {
            val symbol = input[pos++]
            val codeLen = input[pos++].toInt() and 0xFF
            val codeBytes = (codeLen + 7) / 8
            val sb = StringBuilder()
            for (cb in 0 until codeBytes) {
                val byte = input[pos++].toInt() and 0xFF
                for (bit in 0 until 8) {
                    if (sb.length < codeLen) {
                        sb.append(if ((byte and (1 shl (7 - bit))) != 0) '1' else '0')
                    }
                }
            }
            codeToSymbol[sb.toString()] = symbol
        }

        // Read total bits
        val totalBits = bytesToInt(input, pos)
        pos += 4

        // Decode
        val result = mutableListOf<Byte>()
        val current = StringBuilder()
        var bitsRead = 0
        while (bitsRead < totalBits && pos < input.size) {
            val byte = input[pos++].toInt() and 0xFF
            for (bit in 0 until 8) {
                if (bitsRead >= totalBits) break
                current.append(if ((byte and (1 shl (7 - bit))) != 0) '1' else '0')
                bitsRead++
                val symbol = codeToSymbol[current.toString()]
                if (symbol != null) {
                    result.add(symbol)
                    current.clear()
                }
            }
        }

        return result.toByteArray()
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )

    private fun bytesToInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)
}
