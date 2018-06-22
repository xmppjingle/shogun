package com.xmppjingle.shogun

import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import java.io.File
import java.security.MessageDigest

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
            return Klaxon().toJsonString(map)
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

    }

}