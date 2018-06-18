package com.xmppjingle.shogun

import java.nio.charset.Charset
import java.util.*

class Shogun {

    companion object {

        fun crunch(payload: String, minWl: Int, maxWl: Int, layers: Int, charset: Charset): Pair<String, HashMap<String, Char>> {

            println("Original Size: ${payload.length}")

            val dict = HashMap<String, Char>()
            var cr = payload
            for (i in 0..layers) {
//            println(cr)
                val ordered = slash(minWl, maxWl, layers, cr, charset)
//            ordered.forEach({ println(it) })
                if (ordered.isEmpty()) break
                val entry = ordered[0]
                val rp = (166 + i).toChar()
//                println("$entry for $rp")
                dict.put(entry.first, rp)
                cr = cr.replace(entry.first, "$rp", false)
            }

//            println("{{$cr}}")
            println("Slashed Size: ${cr.length}")

            return Pair(cr, dict)

        }

        fun hanoi(payload: String, dict: HashMap<String, Char>): String {
            var uncr = payload
            dict.forEach {
                uncr = uncr.replace("${it.value}", it.key)
                println("Hanoi Partial: ${uncr}")
            }
            println("Hanoi Stacked Size: ${uncr.length}")

            return uncr
        }

        fun slash(minWl: Int, maxWl: Int, top: Int, payload: String, charset: Charset): List<Pair<String, Int>> {
            val t = HashMap<String, Int>()
            val encoder = charset.newEncoder()
            for (wl in minWl..(maxWl)) {
//                println("Breaking on $wl...")
                if(wl >= payload.length) break
                var i = 0
                val firstWord = payload.slice(i..(wl + i))
                var j = 0
                while (j < firstWord.length) {
                    if (!encoder.canEncode(firstWord[j])) {
                        i = j + 1
                        break
                    }
                    j++
                }

                while (i < (payload.length - wl - 1)) {
                    val word = payload.slice(i..(wl + i))
                    if (encoder.canEncode(word[wl - 1])) {
                        t.computeIfPresent(word, { _, u -> u + wl + 1 })
                        t.computeIfAbsent(word, { wl })
                        println(word)
                        i++
                    } else {
                        i += wl
                    }
                }
            }
            val ordered = t.toList().sortedBy { (_, v) -> v }
            if (ordered.isEmpty()) return ordered
            return ordered.reversed().subList(0, if (ordered.size > top) top else ordered.size)
        }

    }


}