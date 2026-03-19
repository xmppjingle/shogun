package com.xmppjingle.shogun

/**
 * Template-based compression for structured protocols like SDP.
 * Defines skeleton templates and only transmits variable parts.
 *
 * A template is a pattern with numbered placeholders (e.g., "${0}", "${1}")
 * that get filled in during decompression.
 */
class TemplateCompressor(
    private val templates: List<Template> = DEFAULT_TEMPLATES
) : Compressor {
    override val name = "template"

    data class Template(
        val id: Int,
        val name: String,
        val pattern: String,
        val placeholders: List<String>  // regex patterns for each placeholder
    )

    companion object {
        val DEFAULT_TEMPLATES = listOf(
            // SDP audio media line
            Template(
                id = 0,
                name = "sdp-audio-media",
                pattern = "m=audio \${0} UDP/TLS/RTP/SAVPF \${1}",
                placeholders = listOf("\\d+", "[\\d ]+")
            ),
            // SDP video media line
            Template(
                id = 1,
                name = "sdp-video-media",
                pattern = "m=video \${0} UDP/TLS/RTP/SAVPF \${1}",
                placeholders = listOf("\\d+", "[\\d ]+")
            ),
            // SDP origin line
            Template(
                id = 2,
                name = "sdp-origin",
                pattern = "o=- \${0} \${1} IN IP4 \${2}",
                placeholders = listOf("\\d+", "\\d+", "[\\d.]+")
            ),
            // ICE candidate
            Template(
                id = 3,
                name = "ice-ufrag",
                pattern = "a=ice-ufrag:\${0}",
                placeholders = listOf(".+")
            ),
            Template(
                id = 4,
                name = "ice-pwd",
                pattern = "a=ice-pwd:\${0}",
                placeholders = listOf(".+")
            ),
            // Fingerprint
            Template(
                id = 5,
                name = "fingerprint",
                pattern = "a=fingerprint:sha-256 \${0}",
                placeholders = listOf("[A-F0-9:]+")
            ),
            // rtpmap
            Template(
                id = 6,
                name = "rtpmap",
                pattern = "a=rtpmap:\${0} \${1}",
                placeholders = listOf("\\d+", ".+")
            ),
            // fmtp
            Template(
                id = 7,
                name = "fmtp",
                pattern = "a=fmtp:\${0} \${1}",
                placeholders = listOf("\\d+", ".+")
            ),
            // Connection line
            Template(
                id = 8,
                name = "connection",
                pattern = "c=IN IP4 \${0}",
                placeholders = listOf("[\\d.]+")
            ),
            // rtcp line
            Template(
                id = 9,
                name = "rtcp",
                pattern = "a=rtcp:\${0} IN IP4 \${1}",
                placeholders = listOf("\\d+", "[\\d.]+")
            ),
            // extmap
            Template(
                id = 10,
                name = "extmap",
                pattern = "a=extmap:\${0} \${1}",
                placeholders = listOf("\\d+", ".+")
            )
        )

        const val TEMPLATE_MARKER: Byte = 0x54  // 'T'
        const val LITERAL_MARKER: Byte = 0x4C    // 'L'
        const val SEPARATOR: Byte = 0x1F          // Unit Separator
        const val LINE_END: Byte = 0x0A            // '\n'
    }

    override fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val text = String(input, Charsets.UTF_8)
        val lines = text.split("\n")
        val out = mutableListOf<Byte>()

        for (line in lines) {
            val match = matchTemplate(line)
            if (match != null) {
                out.add(TEMPLATE_MARKER)
                out.add(match.first.toByte()) // template ID
                out.add(SEPARATOR)
                // Write extracted values separated by SEPARATOR
                out.addAll(match.second.joinToString("\u001F").toByteArray(Charsets.UTF_8).toList())
                out.add(LINE_END)
            } else {
                out.add(LITERAL_MARKER)
                out.addAll(line.toByteArray(Charsets.UTF_8).toList())
                out.add(LINE_END)
            }
        }

        return out.toByteArray()
    }

    override fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()

        val lines = mutableListOf<String>()
        var pos = 0

        while (pos < input.size) {
            val marker = input[pos++]

            when (marker) {
                TEMPLATE_MARKER -> {
                    val templateId = input[pos++].toInt() and 0xFF
                    pos++ // skip separator

                    // Read values until LINE_END
                    val valEnd = findByte(input, LINE_END, pos)
                    val end = if (valEnd == -1) input.size else valEnd
                    val valStr = String(input.copyOfRange(pos, end), Charsets.UTF_8)
                    val values = valStr.split("\u001F")
                    pos = end + 1

                    // Reconstruct line from template
                    val template = templates.firstOrNull { it.id == templateId }
                    if (template != null) {
                        var line = template.pattern
                        for ((i, value) in values.withIndex()) {
                            line = line.replace("\${$i}", value)
                        }
                        lines.add(line)
                    }
                }
                LITERAL_MARKER -> {
                    val lineEnd = findByte(input, LINE_END, pos)
                    val end = if (lineEnd == -1) input.size else lineEnd
                    lines.add(String(input.copyOfRange(pos, end), Charsets.UTF_8))
                    pos = end + 1
                }
                LINE_END -> { /* skip stray newlines */ }
                else -> {
                    // Unknown marker, try to skip to next line
                    val lineEnd = findByte(input, LINE_END, pos)
                    pos = if (lineEnd == -1) input.size else lineEnd + 1
                }
            }
        }

        return lines.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    private fun matchTemplate(line: String): Pair<Int, List<String>>? {
        for (template in templates) {
            val values = extractValues(line, template)
            if (values != null) return template.id to values
        }
        return null
    }

    private fun extractValues(line: String, template: Template): List<String>? {
        // Build regex from template pattern
        var regexStr = Regex.escape(template.pattern)
        for (i in template.placeholders.indices) {
            regexStr = regexStr.replace(Regex.escape("\${$i}"), "(${template.placeholders[i]})")
        }

        val regex = try { Regex("^$regexStr$") } catch (_: Exception) { return null }
        val match = regex.matchEntire(line) ?: return null

        return match.groupValues.drop(1)  // drop full match
    }

    private fun findByte(data: ByteArray, byte: Byte, from: Int): Int {
        for (i in from until data.size) {
            if (data[i] == byte) return i
        }
        return -1
    }

    /** Register a custom template at runtime */
    fun withTemplate(template: Template): TemplateCompressor {
        return TemplateCompressor(templates + template)
    }
}
