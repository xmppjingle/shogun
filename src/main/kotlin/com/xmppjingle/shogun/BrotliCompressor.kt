package com.xmppjingle.shogun

/**
 * Brotli-inspired compressor with a static dictionary.
 * Implements LZ77-style sliding window compression with a pre-trained
 * static dictionary of common protocol strings.
 *
 * This is a pure-Kotlin implementation inspired by Brotli's approach
 * of using a static dictionary for better compression of known domains.
 *
 * Format:
 * - [0x00] [len_hi] [len_lo] [offset_hi] [offset_lo]: back-reference (length, distance)
 * - [0x01] [dict_index]: static dictionary reference
 * - [0x02] [count] [bytes...]: literal run
 * - Other bytes: literal single byte (0x03..0xFF)
 */
class BrotliCompressor(
    private val staticDict: List<String> = DEFAULT_STATIC_DICT,
    private val windowSize: Int = 4096,
    private val minMatchLen: Int = 3
) : Compressor {
    override val name = "brotli-static"

    companion object {
        val DEFAULT_STATIC_DICT = listOf(
            // Common SDP/SIP/HTTP strings
            "a=rtpmap:", "a=rtcp-fb:", "a=fmtp:", "a=extmap:",
            "a=ice-ufrag:", "a=ice-pwd:", "a=ice-options:",
            "a=fingerprint:sha-256 ", "a=setup:actpass", "a=setup:active",
            "a=setup:passive", "a=mid:", "a=rtcp:", "a=rtcp-mux",
            "a=rtcp-rsize", "a=recvonly", "a=sendrecv", "a=sendonly",
            "a=inactive", "a=group:BUNDLE ",
            "a=msid-semantic: WMS", "a=candidate:",
            "m=audio ", "m=video ", "m=application ",
            "c=IN IP4 0.0.0.0", "c=IN IP4 127.0.0.1", "c=IN IP6 ::",
            "UDP/TLS/RTP/SAVPF", "RTP/SAVPF", "RTP/AVP",
            "transport-cc", "goog-remb", "nack pli", "ccm fir",
            "opus/48000/2", "H264/90000", "VP8/90000", "VP9/90000",
            "rtx/90000", "red/90000", "ulpfec/90000",
            "PCMU/8000", "PCMA/8000", "G722/8000",
            "ISAC/16000", "ISAC/32000", "CN/8000", "CN/16000", "CN/32000",
            "telephone-event/48000", "telephone-event/32000",
            "telephone-event/16000", "telephone-event/8000",
            "minptime=10;useinbandfec=1",
            "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=",
            "urn:ietf:params:rtp-hdrext:", "ssrc-audio-level",
            "sdes:mid", "toffset",
            "http://www.webrtc.org/experiments/rtp-hdrext/",
            "abs-send-time", "video-orientation",
            "transport-wide-cc-extensions-01",
            "playout-delay", "video-content-type", "video-timing",
            "http://www.ietf.org/id/draft-holmer-rmcat-",
            "urn:3gpp:video-orientation",
            "v=0\r\no=- ", "v=0\no=- ",
            "s=-\r\nt=0 0\r\n", "s=-\nt=0 0\n",
            "IN IP4 ", "IN IP6 ",
            // Common SIP headers
            "SIP/2.0", "Via: SIP/2.0/",
            "Content-Type: application/sdp",
            "Content-Length: ", "Call-ID: ", "CSeq: ",
            "From: ", "To: ", "Contact: ",
            "Max-Forwards: ", "User-Agent: ",
            "INVITE ", "ACK ", "BYE ", "CANCEL ",
            "REGISTER ", "OPTIONS ", "200 OK",
            "180 Ringing", "100 Trying", "183 Session Progress",
            "sip:", "sips:",
            // Common HTTP
            "HTTP/1.1", "HTTP/2", "Content-Type: ",
            "Accept: ", "Authorization: ", "Cache-Control: "
        )

        const val BACKREF_MARKER: Byte = 0x00
        const val DICTREF_MARKER: Byte = 0x01
        const val LITERAL_RUN_MARKER: Byte = 0x02
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val text = String(input, Charsets.UTF_8)
        val out = mutableListOf<Byte>()
        var i = 0
        val literalBuffer = mutableListOf<Byte>()

        fun flushLiterals() {
            if (literalBuffer.isEmpty()) return
            // Write literal run
            var pos = 0
            while (pos < literalBuffer.size) {
                val runLen = minOf(255, literalBuffer.size - pos)
                out.add(LITERAL_RUN_MARKER)
                out.add(runLen.toByte())
                for (j in 0 until runLen) out.add(literalBuffer[pos + j])
                pos += runLen
            }
            literalBuffer.clear()
        }

        while (i < text.length) {
            // Try static dictionary match (longest first)
            var dictMatch = -1
            var dictLen = 0
            for ((idx, entry) in staticDict.withIndex()) {
                if (idx > 254) break
                if (i + entry.length <= text.length &&
                    text.substring(i, i + entry.length) == entry &&
                    entry.length > dictLen) {
                    dictMatch = idx
                    dictLen = entry.length
                }
            }

            // Try LZ77 back-reference
            var bestRefLen = 0
            var bestRefDist = 0
            val searchStart = maxOf(0, i - windowSize)
            for (j in searchStart until i) {
                var matchLen = 0
                while (i + matchLen < text.length &&
                    j + matchLen < i &&
                    text[j + matchLen] == text[i + matchLen] &&
                    matchLen < 65535) {
                    matchLen++
                }
                if (matchLen >= minMatchLen && matchLen > bestRefLen) {
                    bestRefLen = matchLen
                    bestRefDist = i - j
                }
            }

            // Choose best option
            when {
                dictLen >= bestRefLen && dictLen >= minMatchLen -> {
                    flushLiterals()
                    out.add(DICTREF_MARKER)
                    out.add(dictMatch.toByte())
                    i += dictLen
                }
                bestRefLen >= minMatchLen -> {
                    flushLiterals()
                    out.add(BACKREF_MARKER)
                    out.add((bestRefLen shr 8).toByte())
                    out.add((bestRefLen and 0xFF).toByte())
                    out.add((bestRefDist shr 8).toByte())
                    out.add((bestRefDist and 0xFF).toByte())
                    i += bestRefLen
                }
                else -> {
                    // Buffer literal byte
                    val ch = text[i].toString().toByteArray(Charsets.UTF_8)
                    literalBuffer.addAll(ch.toList())
                    i++
                }
            }
        }

        flushLiterals()
        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val out = mutableListOf<Byte>()
        var i = 0

        while (i < input.size) {
            when (input[i]) {
                BACKREF_MARKER -> {
                    i++
                    if (i + 4 > input.size) break
                    val len = ((input[i].toInt() and 0xFF) shl 8) or (input[i + 1].toInt() and 0xFF)
                    val dist = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
                    i += 4
                    val start = out.size - dist
                    for (j in 0 until len) {
                        out.add(out[start + j])
                    }
                }
                DICTREF_MARKER -> {
                    i++
                    if (i >= input.size) break
                    val dictIdx = input[i].toInt() and 0xFF
                    i++
                    if (dictIdx < staticDict.size) {
                        out.addAll(staticDict[dictIdx].toByteArray(Charsets.UTF_8).toList())
                    }
                }
                LITERAL_RUN_MARKER -> {
                    i++
                    if (i >= input.size) break
                    val len = input[i].toInt() and 0xFF
                    i++
                    for (j in 0 until len) {
                        if (i < input.size) {
                            out.add(input[i])
                            i++
                        }
                    }
                }
                else -> {
                    out.add(input[i])
                    i++
                }
            }
        }

        return out.toByteArray()
    }
}
