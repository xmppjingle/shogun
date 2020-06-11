package com.xmppjingle.shogun

import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder

class Shogun {

    data class Crunched(
            val crunched: String,
            val dict: HashMap<String, Int>
    )

    companion object {

        fun crunch(payload: String, minWl: Int, maxWl: Int, layers: Int, charset: Charset) = crunch(payload, minWl, maxWl, layers, charset, emptyList())
        fun crunch(payload: String, minWl: Int, maxWl: Int, layers: Int, charset: Charset, opening: List<String> = emptyList(), excludeChars: List<Char> = emptyList()): Crunched {

            val charsetSize = calcCharsetLength(charset)
            val charsetDelta = calcCharsetLength(Charsets.UTF_8) - charsetSize - 5
            val depth = if (layers < charsetDelta) layers else charsetDelta
            val dict = HashMap<String, Int>()
            var cr = payload

            for (i in 0..(depth - 1)) {
                val word = if (i < opening.size) {
                    opening[i]
                } else {
                    val ordered = slash(minWl, maxWl, 3, cr, charset, excludeChars)
                    if (ordered.isEmpty()) break
                    val entry = ordered[0]
                    entry.first
                }
                val rp = (charsetSize + i)
                dict.put(word, rp)
                cr = cr.replace(word, "${rp.toChar()}", false)
            }

            return Crunched(cr, dict)

        }

        fun crunch(payload: String, dict: ShogunDictionary): String =
                crunch(payload, dict.map)

        fun crunch(payload: String, dict: HashMap<String, Int>): String {
            var crunched = payload
            dict.forEach {
                crunched = crunched.replace(it.key, "${it.value.toChar()}")
            }
            println("Crunch Size: ${crunched.length}")

            return crunched
        }

        fun uncrunch(payload: String, dict: ShogunDictionary): String =
                uncrunch(payload, dict.map)

        fun uncrunch(payload: String, dict: HashMap<String, Int>): String {
            var uncr = payload
            dict.forEach {
                uncr = uncr.replace("${it.value.toChar()}", it.key)
            }
            println("Uncrunch Size: ${uncr.length}")

            return uncr
        }

        fun slash(minWl: Int, maxWl: Int, top: Int, payload: String, charset: Charset, excludeChars: List<Char> = emptyList()): List<Pair<String, Int>> {
            val t = HashMap<String, Int>()
            val encoder = charset.newEncoder()
            for (wl in minWl..(maxWl)) {
                if (wl >= payload.length) break
                var i = validCut(payload.slice(0..(wl - 1)), encoder, excludeChars)
                while (i < (payload.length - wl)) {
                    val word = payload.slice(i..(wl + i - 1))
                    val cut = validCut(word, encoder, excludeChars)
                    if (cut != 0) {
                        i += cut
                    } else {
                        val markChar = word[wl - 1]
                        if (encoder.canEncode(markChar) && !excludeChars.contains(markChar)) {
                            t.computeIfPresent(word, { _, u -> u + wl + 2 })
                            t.computeIfAbsent(word, { wl })
                            i++
                        } else {
                            i += wl + 1
                        }
                    }
                }
            }

            val ordered = t.toList().sortedBy { (_, v) -> v /*+ (k.length * wordLenBonus)*/ }
            if (ordered.isEmpty()) return ordered
            val r = ordered.filter { it.second > it.first.length }.reversed() //.subList(0, if (ordered.size > top) top else ordered.size)
            return if (r.isEmpty() || r.size < top) r else r.subList(0, top)
        }

        fun validCut(word: String, encoder: CharsetEncoder, excludeChars: List<Char>): Int {
            var i = 0
            var j = 0
            while (j < word.length) {
                if (!encoder.canEncode(word[j]) || excludeChars.contains(word[j])) {
                    i = j + 1
                }
                j++
            }
            return i
        }

        fun calcCharsetLength(charset: Charset): Int {
            var i = 0
            val encoder = charset.newEncoder()
            while (encoder.canEncode(i.toChar())) {
                i++
            }
            return i + 10
        }

    }

}