package com.xmppjingle.shogun

/**
 * Common interface for all compression algorithms in Shogun.
 * Each compressor can compress and decompress byte arrays.
 */
interface Compressor {
    /** Unique name identifying this compressor */
    val name: String

    /** Compress input bytes, returning compressed bytes */
    fun compress(input: ByteArray): ByteArray

    /** Decompress previously compressed bytes back to original */
    fun decompress(input: ByteArray): ByteArray
}
