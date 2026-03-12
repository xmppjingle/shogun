package com.xmppjingle.shogun

import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

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
            return crunched
        }

        fun uncrunch(payload: String, dict: ShogunDictionary): String =
                uncrunch(payload, dict.map)

        fun uncrunch(payload: String, dict: HashMap<String, Int>): String {
            var uncr = payload
            dict.forEach {
                uncr = uncr.replace("${it.value.toChar()}", it.key)
            }
            return uncr
        }

        /**
         * Parallel frequency analysis using Java 21 virtual threads.
         * Each word length is processed concurrently, then results are merged.
         */
        fun slash(minWl: Int, maxWl: Int, top: Int, payload: String, charset: Charset, excludeChars: List<Char> = emptyList()): List<Pair<String, Int>> {
            if (payload.isEmpty()) return emptyList()

            val effectiveMaxWl = minOf(maxWl, payload.length - 1)
            if (minWl > effectiveMaxWl) return emptyList()

            // For small ranges or short payloads, use single-threaded path
            val wordLengthRange = minWl..effectiveMaxWl
            if (wordLengthRange.count() <= 2 || payload.length < 200) {
                return slashSequential(minWl, effectiveMaxWl, top, payload, charset, excludeChars)
            }

            // Use virtual threads to parallelize across word lengths
            val merged = ConcurrentHashMap<String, Int>()
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = mutableListOf<Future<*>>()
                for (wl in wordLengthRange) {
                    futures.add(executor.submit {
                        val encoder = charset.newEncoder()
                        val localMap = slashForWordLength(wl, payload, encoder, excludeChars)
                        localMap.forEach { (word, score) ->
                            merged.merge(word, score) { a, b -> a + b }
                        }
                    })
                }
                futures.forEach { it.get() }
            }

            val ordered = merged.toList().sortedBy { (_, v) -> v }
            if (ordered.isEmpty()) return ordered
            val r = ordered.filter { it.second > it.first.length }.reversed()
            return if (r.isEmpty() || r.size < top) r else r.subList(0, top)
        }

        /**
         * Sequential fallback for small inputs (avoids virtual thread overhead).
         */
        private fun slashSequential(minWl: Int, maxWl: Int, top: Int, payload: String, charset: Charset, excludeChars: List<Char>): List<Pair<String, Int>> {
            val t = HashMap<String, Int>()
            val encoder = charset.newEncoder()
            for (wl in minWl..maxWl) {
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
                            t.computeIfPresent(word) { _, u -> u + wl + 2 }
                            t.computeIfAbsent(word) { wl }
                            i++
                        } else {
                            i += wl + 1
                        }
                    }
                }
            }

            val ordered = t.toList().sortedBy { (_, v) -> v }
            if (ordered.isEmpty()) return ordered
            val r = ordered.filter { it.second > it.first.length }.reversed()
            return if (r.isEmpty() || r.size < top) r else r.subList(0, top)
        }

        /**
         * Process a single word length - designed to be run in a virtual thread.
         */
        private fun slashForWordLength(wl: Int, payload: String, encoder: CharsetEncoder, excludeChars: List<Char>): HashMap<String, Int> {
            val t = HashMap<String, Int>()
            if (wl >= payload.length) return t

            var i = validCut(payload.slice(0..(wl - 1)), encoder, excludeChars)
            while (i < (payload.length - wl)) {
                val word = payload.slice(i..(wl + i - 1))
                val cut = validCut(word, encoder, excludeChars)
                if (cut != 0) {
                    i += cut
                } else {
                    val markChar = word[wl - 1]
                    if (encoder.canEncode(markChar) && !excludeChars.contains(markChar)) {
                        t.computeIfPresent(word) { _, u -> u + wl + 2 }
                        t.computeIfAbsent(word) { wl }
                        i++
                    } else {
                        i += wl + 1
                    }
                }
            }
            return t
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
