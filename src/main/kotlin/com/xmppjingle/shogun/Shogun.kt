package com.xmppjingle.shogun

import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.util.*

class Shogun {

    companion object {

        fun crunch(payload: String, minWl: Int, maxWl: Int, layers: Int, charset: Charset) = crunch(payload, minWl, maxWl, layers, charset, emptyList())
        fun crunch(payload: String, minWl: Int, maxWl: Int, layers: Int, charset: Charset, opening: List<String>): Pair<String, HashMap<String, Char>> {

            println("Original Size: ${payload.length}")

            val dict = HashMap<String, Char>()
            var cr = payload
            for (i in 0..layers) {
//            println(cr)
                var word: String
                if (i < opening.size) {
                    word = opening[i]
                } else {
                    val ordered = slash(minWl, maxWl, layers, cr, charset)
//            ordered.forEach({ println(it) })
                    if (ordered.isEmpty()) break
                    val entry = ordered[0]
                    word = entry.first
                }
                val rp = (257 + i).toChar()
//                println("$entry for $rp")
                dict.put(word, rp)
                cr = cr.replace(word, "$rp", false)
            }

//            println("{{$cr}}")
            println("Slashed Size: ${cr.length}")

            return Pair(cr, dict)

        }

        fun hanoi(payload: String, dict: HashMap<String, Char>): String {
            var uncr = payload
            dict.forEach {
                uncr = uncr.replace("${it.value}", it.key)
//                println("Hanoi Partial: ${uncr}")
            }
            println("Hanoi Stacked Size: ${uncr.length}")

            return uncr
        }

        fun slash(minWl: Int, maxWl: Int, top: Int, payload: String, charset: Charset): List<Pair<String, Int>> {
            val t = HashMap<String, Int>()
            val encoder = charset.newEncoder()
            for (wl in minWl..(maxWl)) {
//                println("Breaking on $wl...")
                if (wl >= payload.length) break
                var i = validCut(payload.slice(0..(wl - 1)), encoder)
                while (i < (payload.length - wl)) {
                    val word = payload.slice(i..(wl + i - 1))
                    val cut = validCut(word, encoder)
                    if (cut != 0) {
                        i += cut
                    } else {
                        if (encoder.canEncode(word[wl - 1])) {
                            t.computeIfPresent(word, { _, u -> u + wl + 2 })
                            t.computeIfAbsent(word, { wl })
//                        println(word)
                            i++
                        } else {
                            i += wl + 1
                        }
                    }
                }
            }
            val ordered = t.toList().sortedBy { (k, v) -> v /*+ (k.length * wordLenBonus)*/ }
            if (ordered.isEmpty()) return ordered
            return ordered.filter { it.second > it.first.length }.reversed()//.subList(0, if (ordered.size > top) top else ordered.size)
        }

        fun validCut(word: String, encoder: CharsetEncoder): Int {
            var i = 0
            var j = 0
            while (j < word.length) {
                if (!encoder.canEncode(word[j])) {
                    i = j + 1
                }
                j++
            }
            return i
        }

    }


}