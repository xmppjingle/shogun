package com.xmppjingle.shogun

/**
 * Static/Shared Dictionary compressor (zstd-style).
 * Uses a pre-trained dictionary of common phrases to replace frequent substrings
 * with short token references. Dictionary is shared between compressor and decompressor.
 */
class StaticDictionaryCompressor(
    private val dictionary: List<String> = DEFAULT_SDP_DICTIONARY
) : Compressor {
    override val name = "static-dict"

    companion object {
        /** Pre-trained dictionary of common SDP/SIP substrings, ordered by expected frequency */
        val DEFAULT_SDP_DICTIONARY = listOf(
            "a=rtpmap:",
            "a=rtcp-fb:",
            "a=fmtp:",
            "a=extmap:",
            "a=ice-",
            "a=fingerprint:sha-256 ",
            "a=setup:",
            "a=mid:",
            "a=rtcp:",
            "a=recvonly",
            "a=sendrecv",
            "a=sendonly",
            "a=rtcp-mux",
            "a=rtcp-rsize",
            "a=group:BUNDLE ",
            "a=msid-semantic: WMS",
            "m=audio ",
            "m=video ",
            "m=application ",
            "c=IN IP4 ",
            "c=IN IP6 ",
            "UDP/TLS/RTP/SAVPF",
            "RTP/SAVPF",
            "transport-cc",
            "goog-remb",
            "nack pli",
            "ccm fir",
            "telephone-event/",
            "urn:ietf:params:rtp-hdrext:",
            "http://www.webrtc.org/experiments/rtp-hdrext/",
            "level-asymmetry-allowed=1",
            "packetization-mode=1",
            "profile-level-id=",
            "opus/48000/2",
            "H264/90000",
            "VP8/90000",
            "VP9/90000",
            "rtx/90000",
            "red/90000",
            "ulpfec/90000",
            "PCMU/8000",
            "PCMA/8000",
            "CN/8000",
            "CN/16000",
            "CN/32000",
            "G722/8000",
            "ISAC/16000",
            "ISAC/32000",
            "minptime=10;useinbandfec=1",
            "v=0\r\n",
            "v=0\n",
            "IN IP4 0.0.0.0",
            "IN IP4 127.0.0.1",
            "t=0 0",
            "s=-",
            "o=- ",
            "ice-options:trickle",
            " renomination",
            "ssrc-audio-level",
            "sdes:mid",
            "abs-send-time",
            "video-orientation",
            "playout-delay",
            "video-content-type",
            "video-timing"
        )

        // Escape byte marking the start of a dictionary reference
        const val ESCAPE_BYTE: Byte = 0x01
        // Escape byte for literal 0x01 in input
        const val ESCAPE_LITERAL: Byte = 0x02
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        var text = String(input, Charsets.UTF_8)
        // Sorted by length descending for greedy matching
        val sortedDict = dictionary.withIndex().sortedByDescending { it.value.length }

        // Replace dictionary entries with escape + index tokens
        // First, escape existing escape bytes
        val placeholder = "\u0001"
        val literalPlaceholder = "\u0002"
        text = text.replace(placeholder, literalPlaceholder + placeholder)

        for ((index, phrase) in sortedDict) {
            if (index > 254) break // max 255 entries (1-byte index)
            text = text.replace(phrase, "$placeholder${(index + 3).toChar()}")
        }

        return text.toByteArray(Charsets.UTF_8)
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        var text = String(input, Charsets.UTF_8)
        val placeholder = "\u0001"
        val literalPlaceholder = "\u0002"

        // Restore dictionary entries (reverse order to handle overlaps)
        for ((index, phrase) in dictionary.withIndex()) {
            if (index > 254) break
            text = text.replace("$placeholder${(index + 3).toChar()}", phrase)
        }

        // Restore escaped escape bytes
        text = text.replace(literalPlaceholder + placeholder, placeholder)

        return text.toByteArray(Charsets.UTF_8)
    }

    /**
     * Train a dictionary from a corpus of text samples.
     * Returns a list of the most frequent substrings sorted by compression value.
     */
    fun trainDictionary(samples: List<String>, minLen: Int = 4, maxLen: Int = 60, maxEntries: Int = 64): List<String> {
        val corpus = samples.joinToString("\n")
        val freq = HashMap<String, Int>()

        for (wl in minLen..minOf(maxLen, corpus.length - 1)) {
            for (i in 0..corpus.length - wl) {
                val sub = corpus.substring(i, i + wl)
                if (sub.contains('\u0001') || sub.contains('\u0002')) continue
                freq[sub] = (freq[sub] ?: 0) + 1
            }
        }

        // Score = frequency * (length - 2) - accounts for the 2-byte token cost
        return freq.entries
            .filter { it.value > 1 }
            .sortedByDescending { it.value * (it.key.length - 2) }
            .map { it.key }
            .take(maxEntries)
    }
}
