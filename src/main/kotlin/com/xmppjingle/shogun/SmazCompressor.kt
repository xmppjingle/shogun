package com.xmppjingle.shogun

/**
 * SMAZ-inspired compressor for very short strings.
 * Uses a codebook of common short substrings (bigrams, trigrams, common words)
 * to replace them with single-byte codes. Optimized for strings < 256 bytes.
 *
 * Encoding:
 * - 0x00..0xFD: codebook entry index
 * - 0xFE [len] [bytes...]: verbatim run (len = 1..255)
 * - 0xFF: reserved / escape
 */
class SmazCompressor : Compressor {
    override val name = "smaz"

    companion object {
        /** Codebook of common short strings, ordered by expected frequency */
        val CODEBOOK: Array<String> = arrayOf(
            // Single common chars and spaces
            " ", "the", "e", "t", "a", "of", "o", "and", "i", "n",
            "s", "e ", "r", " the", ".com", "in", "er", " a", "to",
            "in", " t", "s ", " s", "or", "es", " o", "d ", "re",
            "an", " b", "at", " i", " w", "no", "se", " c", "le",
            "on", "en", "te", "he", "ha", "ne", "al", "co", "ea",
            "it", "is", "ou", "ar", "ed", "nd", "th", " f", "as",
            "ll", "io", "ve", " m", "st", "nt", "ng", "ic", "me",
            "de", " h", "ly", "ge", " n", " p", "ur", " d", "ti",
            "el", "ec", "li", "ra", "ri", "ro", "ct", "si", "di",
            "ce", "la", "ta", "ma", "ch", "ni", "om", "ss", "et",
            "ns", "pe", "ad", "em", "nc", "il", "rt", "fi", "am",
            // Common protocol tokens
            "SIP", "sip", "SDP", "sdp", "RTP", "UDP", "TCP", "TLS",
            "IP4", "IP6", "audio", "video", "application",
            "IN ", "a=", "m=", "o=", "s=", "t=", "c=", "v=",
            "\r\n", "\n", "=0", ":0", "0.0", "127", "192", "10.",
            "http", "https", "www", "org", "net",
            // Common words
            "the ", " is ", " to ", " of ", " in ", " for ", " on ",
            " and ", " a ", " an ", "from", "with", "that", "this",
            "not", "but", "are", "was", "all", "can", "had", "her",
            "one", "our", "out", "you", "been", "have", "will",
            // Digits and punctuation
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "00", "01", "10", "11", "20", "://", ".", ",", ";",
            ":", "/", "-", "_", "@", "#", "!", "?", "'", "\"",
            "(", ")", "[", "]", "{", "}", "<", ">", "&", "=",
            "+", "*", "%", "$", "^", "~", "|", "\\",
            // Short common prefixes
            "re", "un", "in", "im", "pre", "dis", "mis", "over",
            "ing", "tion", "ment", "ness", "able", "ible",
            "ful", "less", "ous", "ive", "ent", "ant",
            // More SIP/SDP specific
            "Via", "From", "To", "Call", "CSeq", "Contact",
            "Content", "Max", "User", "Agent", "Allow",
            "Supported", "Accept", "Require",
            "INVITE", "ACK", "BYE", "CANCEL", "REGISTER",
            "OPTIONS", "INFO", "UPDATE", "PRACK", "SUBSCRIBE",
            "NOTIFY", "REFER", "MESSAGE", "PUBLISH"
        )

        const val VERBATIM_MARKER: Byte = 0xFE.toByte()
        const val MAX_CODEBOOK_SIZE = 254 // 0x00..0xFD
    }

    // Build a trie for efficient longest-match lookup
    private val effectiveCodebook = CODEBOOK.take(MAX_CODEBOOK_SIZE)

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val text = String(input, Charsets.UTF_8)
        val out = mutableListOf<Byte>()
        var i = 0

        while (i < text.length) {
            // Try longest match first
            var bestLen = 0
            var bestIdx = -1

            for ((idx, entry) in effectiveCodebook.withIndex()) {
                if (i + entry.length <= text.length &&
                    text.substring(i, i + entry.length) == entry &&
                    entry.length > bestLen) {
                    bestLen = entry.length
                    bestIdx = idx
                }
            }

            if (bestIdx >= 0) {
                out.add(bestIdx.toByte())
                i += bestLen
            } else {
                // Verbatim: collect consecutive unmatched chars
                val start = i
                i++
                while (i < text.length) {
                    var found = false
                    for (entry in effectiveCodebook) {
                        if (i + entry.length <= text.length &&
                            text.substring(i, i + entry.length) == entry &&
                            entry.length > 1) {
                            found = true
                            break
                        }
                    }
                    if (found) break
                    i++
                    if (i - start >= 255) break
                }
                val verbatim = text.substring(start, i).toByteArray(Charsets.UTF_8)
                out.add(VERBATIM_MARKER)
                out.add(verbatim.size.toByte())
                out.addAll(verbatim.toList())
            }
        }

        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val out = StringBuilder()
        var i = 0

        while (i < input.size) {
            val code = input[i].toInt() and 0xFF
            i++

            if (code == 0xFE) {
                // Verbatim
                if (i < input.size) {
                    val len = input[i].toInt() and 0xFF
                    i++
                    if (i + len <= input.size) {
                        out.append(String(input.copyOfRange(i, i + len), Charsets.UTF_8))
                        i += len
                    }
                }
            } else if (code < effectiveCodebook.size) {
                out.append(effectiveCodebook[code])
            }
            // else: skip unknown codes
        }

        return out.toString().toByteArray(Charsets.UTF_8)
    }
}
