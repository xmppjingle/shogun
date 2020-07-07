package com.xmppjingle.shogun

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Render
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil

class ShogunUtils {

    companion object {

        fun md5(input: String): String {
            val HEX_CHARS = "0123456789ABCDEF"
            val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            val result = StringBuilder(bytes.size * 2)
            bytes.forEach {
                val i = it.toInt()
                result.append(HEX_CHARS[i shr 4 and 0x0f])
                result.append(HEX_CHARS[i and 0x0f])
            }
            return result.toString()
        }

        fun exportDict(map: HashMap<String, Int>): String {
            return Klaxon().toJsonString(map.map { (k, v) -> Render.escapeString(k) to v }.toMap())
        }

        fun importDict(json: String): ShogunDictionary? {
            val mapConverter = object : Converter {
                override fun fromJson(jv: JsonValue): HashMap<String, Any?> = HashMap(jv.obj!!)
                override fun canConvert(cls: Class<*>): Boolean = true
                override fun toJson(value: Any): String = ""
            }
            return ShogunDictionary(md5(json), Klaxon().converter(mapConverter).parse(json)!!)
        }

        fun readFileDirectlyAsText(fileName: String): String = readFileDirectlyAsText(File(fileName))
        fun readFileDirectlyAsText(file: File): String = file.readText(Charsets.UTF_8)

        fun writeDictToFile(jsonDict: String): File =
                md5(jsonDict).let { fname ->
                    val file = File(fname)
                    file.writeText(jsonDict)
                    file
                }

        fun calculateDictFromDir(dir: String, depth: Int, normalizeEOL: Boolean = false): String {
            val files = File(dir).walk().filter { it.isFile }
            val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }
                    .let { if (normalizeEOL) ShogunUtils.normalizeEOL(it) else it }
            val c = Shogun.crunch(s, 4, 60, depth, Charsets.US_ASCII)
            return ShogunUtils.exportDict(c.dict)
        }

        fun normalizeEOL(str: String): String = str
                .replace("\r\n", "\n")
                .replace("\r", "\n")

        fun <T> jumpStepDilute(list: ArrayList<T>, maxSize: Int): ArrayList<T> =
                if (list.isEmpty() || list.size < maxSize) list
                else {
                    arrayListOf<T>().let { dilute ->
                        if (maxSize > 0) {
                            val mantissa = ceil((list.size / maxSize).toDouble()).toInt()
                            for (i in 0 until (maxSize - 1)) {
                                dilute.add(list[i * mantissa])
                            }
                            dilute.add(list.last())
                        }
                        dilute
                    }
                }

    }

}