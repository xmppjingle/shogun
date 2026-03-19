package com.xmppjingle.shogun

import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Hybrid compression pipeline that chains multiple compressors.
 * Supports multi-threaded execution for comparing/selecting the best compressor
 * and for parallel compression of chunked data.
 *
 * Features:
 * - Sequential chaining: compress through a pipeline of compressors
 * - Parallel selection: try multiple compressors concurrently, pick best result
 * - Chunked parallel: split data into chunks, compress in parallel, merge
 * - All parallelism uses Java 21 virtual threads
 */
class CompressionPipeline(
    private val compressors: List<Compressor> = emptyList()
) {
    companion object {
        // Header format: [magic(2)] [version(1)] [compressor_count(1)] [compressor_ids...] [data]
        val MAGIC = byteArrayOf(0x53, 0x47) // "SG" for ShoGun
        const val VERSION: Byte = 1

        /** Create a pipeline with the given compressors applied in order */
        fun chain(vararg compressors: Compressor): CompressionPipeline =
            CompressionPipeline(compressors.toList())

        /**
         * Try all given compressors in parallel using virtual threads,
         * return the result with the best compression ratio.
         */
        fun selectBest(input: ByteArray, vararg compressors: Compressor): CompressionResult {
            if (compressors.isEmpty()) return CompressionResult(input, "none", 1.0)
            if (input.isEmpty()) return CompressionResult(byteArrayOf(), compressors[0].name, 0.0)

            val results = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = compressors.map { compressor ->
                    executor.submit<CompressionResult> {
                        val compressed = compressor.compress(input)
                        CompressionResult(
                            compressed,
                            compressor.name,
                            compressed.size.toDouble() / input.size
                        )
                    }
                }
                futures.map { it.get() }
            }

            return results.minByOrNull { it.ratio } ?: CompressionResult(input, "none", 1.0)
        }

        /**
         * Split input into chunks, compress each chunk in parallel using virtual threads.
         */
        fun compressParallelChunked(
            input: ByteArray,
            compressor: Compressor,
            chunkSize: Int = 8192
        ): ByteArray {
            if (input.isEmpty()) return byteArrayOf()
            if (input.size <= chunkSize) return compressor.compress(input)

            val chunks = input.toList().chunked(chunkSize).map { it.toByteArray() }

            val compressedChunks = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = chunks.map { chunk ->
                    executor.submit<ByteArray> { compressor.compress(chunk) }
                }
                futures.map { it.get() }
            }

            // Pack: [chunk_count(4)] + for each chunk: [chunk_size(4)] [chunk_data...]
            val out = mutableListOf<Byte>()
            out.addAll(intToBytes(compressedChunks.size).toList())
            for (chunk in compressedChunks) {
                out.addAll(intToBytes(chunk.size).toList())
                out.addAll(chunk.toList())
            }
            return out.toByteArray()
        }

        /**
         * Decompress chunked data compressed with compressParallelChunked.
         */
        fun decompressParallelChunked(
            input: ByteArray,
            compressor: Compressor
        ): ByteArray {
            if (input.isEmpty()) return byteArrayOf()

            var pos = 0
            val chunkCount = bytesToInt(input, pos); pos += 4
            val chunks = mutableListOf<ByteArray>()

            for (i in 0 until chunkCount) {
                val chunkSize = bytesToInt(input, pos); pos += 4
                chunks.add(input.copyOfRange(pos, pos + chunkSize))
                pos += chunkSize
            }

            // Decompress chunks in parallel
            val decompressedChunks = Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = chunks.map { chunk ->
                    executor.submit<ByteArray> { compressor.decompress(chunk) }
                }
                futures.map { it.get() }
            }

            val totalSize = decompressedChunks.sumOf { it.size }
            val result = ByteArray(totalSize)
            var offset = 0
            for (chunk in decompressedChunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.size)
                offset += chunk.size
            }
            return result
        }

        /**
         * Benchmark all provided compressors against the input, returning timing and ratio info.
         * Uses virtual threads for parallel benchmarking.
         */
        fun benchmark(
            input: ByteArray,
            iterations: Int = 5,
            vararg compressors: Compressor
        ): List<BenchmarkResult> {
            return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = compressors.map { compressor ->
                    executor.submit<BenchmarkResult> {
                        var totalCompressNs = 0L
                        var totalDecompressNs = 0L
                        var compressed = byteArrayOf()

                        for (i in 0 until iterations) {
                            val startC = System.nanoTime()
                            compressed = compressor.compress(input)
                            totalCompressNs += System.nanoTime() - startC

                            val startD = System.nanoTime()
                            compressor.decompress(compressed)
                            totalDecompressNs += System.nanoTime() - startD
                        }

                        BenchmarkResult(
                            compressorName = compressor.name,
                            originalSize = input.size,
                            compressedSize = compressed.size,
                            ratio = compressed.size.toDouble() / input.size,
                            avgCompressMs = totalCompressNs / iterations / 1_000_000.0,
                            avgDecompressMs = totalDecompressNs / iterations / 1_000_000.0
                        )
                    }
                }
                futures.map { it.get() }
            }
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

    data class CompressionResult(
        val data: ByteArray,
        val compressorName: String,
        val ratio: Double
    )

    data class BenchmarkResult(
        val compressorName: String,
        val originalSize: Int,
        val compressedSize: Int,
        val ratio: Double,
        val avgCompressMs: Double,
        val avgDecompressMs: Double
    ) {
        override fun toString(): String =
            "$compressorName: ratio=%.3f, compress=%.2fms, decompress=%.2fms (%d -> %d bytes)".format(
                ratio, avgCompressMs, avgDecompressMs, originalSize, compressedSize
            )
    }

    /**
     * Compress through the pipeline: each compressor's output feeds the next.
     */
    fun compress(input: ByteArray): ByteArray {
        if (compressors.isEmpty()) return input

        var data = input
        val header = mutableListOf<Byte>()
        header.addAll(MAGIC.toList())
        header.add(VERSION)
        header.add(compressors.size.toByte())

        // Store compressor names for decompression
        for (c in compressors) {
            val nameBytes = c.name.toByteArray(Charsets.UTF_8)
            header.add(nameBytes.size.toByte())
            header.addAll(nameBytes.toList())
        }

        for (c in compressors) {
            data = c.compress(data)
        }

        return header.toByteArray() + data
    }

    /**
     * Decompress through the pipeline in reverse order.
     */
    fun decompress(input: ByteArray, registry: Map<String, Compressor>): ByteArray {
        if (input.size < 4) return input

        // Verify magic
        if (input[0] != MAGIC[0] || input[1] != MAGIC[1]) return input

        var pos = 3 // skip magic + version
        val count = input[pos++].toInt() and 0xFF

        val compressorNames = mutableListOf<String>()
        for (i in 0 until count) {
            val nameLen = input[pos++].toInt() and 0xFF
            compressorNames.add(String(input.copyOfRange(pos, pos + nameLen), Charsets.UTF_8))
            pos += nameLen
        }

        var data = input.copyOfRange(pos, input.size)

        // Decompress in reverse order
        for (name in compressorNames.reversed()) {
            val compressor = registry[name] ?: throw IllegalArgumentException("Unknown compressor: $name")
            data = compressor.decompress(data)
        }

        return data
    }
}
