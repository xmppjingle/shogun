import com.xmppjingle.shogun.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompressorTest {

    // ==================== Test Data ====================

    private val shortText = "Hello, World!"
    private val repeatedText = "abcabcabcabcabcabcabcabcabcabc"
    private val protocolText = """
v=0
o=- 4943942346655114477 2 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0 1
a=msid-semantic: WMS
m=audio 9 UDP/TLS/RTP/SAVPF 111 103 104 9 102 0 8 106 105 13 110 112 113 126
c=IN IP4 0.0.0.0
a=rtcp:9 IN IP4 0.0.0.0
a=ice-ufrag:t76n
a=ice-pwd:5W0wAlnrb/D6/mtWbGVel5B2
a=ice-options:trickle renomination
a=fingerprint:sha-256 1D:E1:C8:3B:00:12:C9:16:EB:47:AB:72:2C:B5:D0:88:74:DF:3F:D7:A6:B6:FC:8F:C7:E6:07:C8:9E:6B:2D:E3
a=setup:actpass
a=mid:0
a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level
a=extmap:9 urn:ietf:params:rtp-hdrext:sdes:mid
a=recvonly
a=rtcp-mux
a=rtpmap:111 opus/48000/2
a=rtcp-fb:111 transport-cc
a=fmtp:111 minptime=10;useinbandfec=1
a=rtpmap:103 ISAC/16000
a=rtpmap:104 ISAC/32000
a=rtpmap:9 G722/8000
a=rtpmap:102 ILBC/8000
a=rtpmap:0 PCMU/8000
a=rtpmap:8 PCMA/8000
a=rtpmap:106 CN/32000
a=rtpmap:105 CN/16000
a=rtpmap:13 CN/8000
a=rtpmap:110 telephone-event/48000
a=rtpmap:112 telephone-event/32000
a=rtpmap:113 telephone-event/16000
a=rtpmap:126 telephone-event/8000
    """.trimIndent()

    private val emptyInput = ""
    private val singleChar = "a"
    private val binaryLike = ByteArray(256) { it.toByte() }

    private fun loadSdpFiles(): String {
        val resourcePath = Thread.currentThread().contextClassLoader.getResource("sdp")?.path
            ?: return protocolText
        return File(resourcePath).walk().filter { it.isFile }
            .joinToString("\n") { it.readText(Charsets.UTF_8) }
    }

    // ==================== Huffman Compressor Tests ====================

    @Test
    fun testHuffmanBasicRoundTrip() {
        val compressor = HuffmanCompressor()
        val input = shortText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Huffman roundtrip failed for short text")
    }

    @Test
    fun testHuffmanEmpty() {
        val compressor = HuffmanCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testHuffmanSingleSymbol() {
        val compressor = HuffmanCompressor()
        val input = "aaaaaaaaaa".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Huffman single symbol failed")
    }

    @Test
    fun testHuffmanRepeatedText() {
        val compressor = HuffmanCompressor()
        val input = repeatedText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Huffman repeated text failed")
        assertTrue(compressed.size <= input.size, "Huffman should compress repeated text")
    }

    @Test
    fun testHuffmanProtocolText() {
        val compressor = HuffmanCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Huffman protocol text roundtrip failed")
    }

    @Test
    fun testHuffmanLargePayload() {
        val compressor = HuffmanCompressor()
        val input = protocolText.repeat(50).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Huffman large payload failed")
        assertTrue(compressed.size < input.size, "Huffman should compress large repeated data")
    }

    @Test
    fun testHuffmanAllByteValues() {
        val compressor = HuffmanCompressor()
        val compressed = compressor.compress(binaryLike)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(binaryLike, decompressed, "Huffman all byte values roundtrip failed")
    }

    // ==================== Static Dictionary Compressor Tests ====================

    @Test
    fun testStaticDictBasicRoundTrip() {
        val compressor = StaticDictionaryCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "StaticDict roundtrip failed")
    }

    @Test
    fun testStaticDictEmpty() {
        val compressor = StaticDictionaryCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testStaticDictCompression() {
        val compressor = StaticDictionaryCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        assertTrue(compressed.size < input.size, "StaticDict should compress SDP text")
    }

    @Test
    fun testStaticDictNonSdpText() {
        val compressor = StaticDictionaryCompressor()
        val input = "This is a simple non-SDP text with no protocol keywords.".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "StaticDict non-SDP roundtrip failed")
    }

    @Test
    fun testStaticDictCustomDictionary() {
        val customDict = listOf("foo", "bar", "baz", "foobar")
        val compressor = StaticDictionaryCompressor(customDict)
        val input = "foobar foo bar baz foobar".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "StaticDict custom dictionary roundtrip failed")
    }

    @Test
    fun testStaticDictSdpFiles() {
        val compressor = StaticDictionaryCompressor()
        val sdp = loadSdpFiles()
        val input = sdp.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "StaticDict SDP files roundtrip failed")
        println("StaticDict: ${input.size} -> ${compressed.size} (${compressed.size * 100 / input.size}%)")
    }

    @Test
    fun testStaticDictTrainDictionary() {
        val compressor = StaticDictionaryCompressor()
        val samples = listOf(protocolText, protocolText.replace("111", "222"))
        val trained = compressor.trainDictionary(samples, maxEntries = 10)
        assertTrue(trained.isNotEmpty(), "Trained dictionary should not be empty")
        assertTrue(trained.size <= 10, "Trained dictionary should respect maxEntries")
    }

    // ==================== Delta Compressor Tests ====================

    @Test
    fun testDeltaNoBase() {
        val compressor = DeltaCompressor()
        val input = shortText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Delta no-base roundtrip failed")
    }

    @Test
    fun testDeltaEmpty() {
        val compressor = DeltaCompressor()
        // Delta with empty input and no base returns FULL marker + empty content
        val compressed = compressor.compress(byteArrayOf())
        val decompressed = compressor.decompress(compressed)
        assertEquals("", String(decompressed, Charsets.UTF_8), "Delta empty roundtrip failed")
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testDeltaIdenticalMessages() {
        val base = "line1\nline2\nline3"
        val compressor = DeltaCompressor(base)
        val input = base.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Delta identical roundtrip failed")
        assertTrue(compressed.size < input.size, "Delta with identical base should compress well")
    }

    @Test
    fun testDeltaSmallChange() {
        val base = "line1\nline2\nline3\nline4\nline5"
        val modified = "line1\nline2-modified\nline3\nline4\nline5"
        val compressor = DeltaCompressor(base)
        val compressed = compressor.compress(modified.toByteArray())
        val decompressed = compressor.decompress(compressed)
        assertEquals(modified, String(decompressed, Charsets.UTF_8), "Delta small change roundtrip failed")
    }

    @Test
    fun testDeltaAddedLines() {
        val base = "line1\nline2\nline3"
        val modified = "line1\nline2\nnewline\nline3"
        val compressor = DeltaCompressor(base)
        val compressed = compressor.compress(modified.toByteArray())
        val decompressed = compressor.decompress(compressed)
        assertEquals(modified, String(decompressed, Charsets.UTF_8), "Delta added lines roundtrip failed")
    }

    @Test
    fun testDeltaDeletedLines() {
        val base = "line1\nline2\nline3\nline4"
        val modified = "line1\nline3\nline4"
        val compressor = DeltaCompressor(base)
        val compressed = compressor.compress(modified.toByteArray())
        val decompressed = compressor.decompress(compressed)
        assertEquals(modified, String(decompressed, Charsets.UTF_8), "Delta deleted lines roundtrip failed")
    }

    @Test
    fun testDeltaSdpRenegotiation() {
        val sdpOffer = protocolText
        val sdpAnswer = protocolText
            .replace("a=ice-ufrag:t76n", "a=ice-ufrag:x9k2")
            .replace("a=ice-pwd:5W0wAlnrb/D6/mtWbGVel5B2", "a=ice-pwd:abc123def456ghi789")
            .replace("a=recvonly", "a=sendrecv")

        val compressor = DeltaCompressor(sdpOffer)
        val compressed = compressor.compress(sdpAnswer.toByteArray())
        val decompressed = compressor.decompress(compressed)
        assertEquals(sdpAnswer, String(decompressed, Charsets.UTF_8), "Delta SDP renegotiation failed")
        println("Delta SDP: ${sdpAnswer.length} -> ${compressed.size} (${compressed.size * 100 / sdpAnswer.length}%)")
    }

    // ==================== RLE Compressor Tests ====================

    @Test
    fun testRLEBasicRoundTrip() {
        val compressor = RLECompressor()
        val input = shortText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "RLE roundtrip failed")
    }

    @Test
    fun testRLEEmpty() {
        val compressor = RLECompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testRLELongRuns() {
        val compressor = RLECompressor()
        val input = "a".repeat(100).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "RLE long run roundtrip failed")
        assertTrue(compressed.size < input.size, "RLE should compress long runs")
    }

    @Test
    fun testRLENoRuns() {
        val compressor = RLECompressor()
        val input = "abcdefghij".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "RLE no-runs roundtrip failed")
    }

    @Test
    fun testRLEEscapeByte() {
        val compressor = RLECompressor()
        val input = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x01, 0x02, 0xFF.toByte())
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "RLE escape byte roundtrip failed")
    }

    @Test
    fun testRLEMixedContent() {
        val compressor = RLECompressor()
        val input = ("abc" + "d".repeat(50) + "efg" + "h".repeat(30) + "ijk").toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "RLE mixed content roundtrip failed")
        assertTrue(compressed.size < input.size, "RLE should compress mixed content with runs")
    }

    @Test
    fun testRLEAllByteValues() {
        val compressor = RLECompressor()
        val compressed = compressor.compress(binaryLike)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(binaryLike, decompressed, "RLE all byte values roundtrip failed")
    }

    // ==================== ANS Compressor Tests ====================

    @Test
    fun testANSBasicRoundTrip() {
        val compressor = ANSCompressor()
        val input = repeatedText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "ANS roundtrip failed for repeated text")
    }

    @Test
    fun testANSEmpty() {
        val compressor = ANSCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testANSSingleSymbol() {
        val compressor = ANSCompressor()
        val input = "aaaaaaaaaa".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "ANS single symbol failed")
    }

    @Test
    fun testANSProtocolText() {
        val compressor = ANSCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "ANS protocol text roundtrip failed")
    }

    @Test
    fun testANSSkewedDistribution() {
        val compressor = ANSCompressor()
        // Highly skewed: mostly 'a' with occasional other chars
        val sb = StringBuilder()
        for (i in 0 until 1000) {
            if (i % 100 == 0) sb.append('b')
            else sb.append('a')
        }
        val input = sb.toString().toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "ANS skewed distribution roundtrip failed")
        assertTrue(compressed.size < input.size, "ANS should compress skewed data well")
    }

    @Test
    fun testANSLargePayload() {
        val compressor = ANSCompressor()
        val input = protocolText.repeat(10).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "ANS large payload roundtrip failed")
    }

    // ==================== Template Compressor Tests ====================

    @Test
    fun testTemplateBasicRoundTrip() {
        val compressor = TemplateCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertEquals(protocolText, String(decompressed, Charsets.UTF_8), "Template roundtrip failed")
    }

    @Test
    fun testTemplateEmpty() {
        val compressor = TemplateCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testTemplateNonSdpText() {
        val compressor = TemplateCompressor()
        val text = "This is plain text\nwith no SDP patterns\nat all"
        val input = text.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertEquals(text, String(decompressed, Charsets.UTF_8), "Template non-SDP roundtrip failed")
    }

    @Test
    fun testTemplateRtpmap() {
        val compressor = TemplateCompressor()
        val line = "a=rtpmap:111 opus/48000/2"
        val input = line.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertEquals(line, String(decompressed, Charsets.UTF_8), "Template rtpmap failed")
    }

    @Test
    fun testTemplateCustomTemplate() {
        val customTemplate = TemplateCompressor.Template(
            id = 99, name = "custom",
            pattern = "X-Custom: \${0} value=\${1}",
            placeholders = listOf("\\w+", "\\d+")
        )
        val compressor = TemplateCompressor().withTemplate(customTemplate)
        val text = "X-Custom: mykey value=42"
        val compressed = compressor.compress(text.toByteArray())
        val decompressed = compressor.decompress(compressed)
        assertEquals(text, String(decompressed, Charsets.UTF_8), "Template custom roundtrip failed")
    }

    @Test
    fun testTemplateSdpFiles() {
        val compressor = TemplateCompressor()
        val sdp = loadSdpFiles()
        val input = sdp.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertEquals(sdp, String(decompressed, Charsets.UTF_8), "Template SDP files roundtrip failed")
    }

    // ==================== SMAZ Compressor Tests ====================

    @Test
    fun testSmazBasicRoundTrip() {
        val compressor = SmazCompressor()
        val input = shortText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "SMAZ roundtrip failed for short text")
    }

    @Test
    fun testSmazEmpty() {
        val compressor = SmazCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testSmazCommonWords() {
        val compressor = SmazCompressor()
        val input = "the quick brown fox".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "SMAZ common words roundtrip failed")
    }

    @Test
    fun testSmazProtocolTokens() {
        val compressor = SmazCompressor()
        val input = "SIP SDP RTP UDP TCP".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "SMAZ protocol tokens roundtrip failed")
    }

    @Test
    fun testSmazVeryShort() {
        val compressor = SmazCompressor()
        for (s in listOf("a", "ab", "abc", "hi", "ok")) {
            val input = s.toByteArray()
            val compressed = compressor.compress(input)
            val decompressed = compressor.decompress(compressed)
            assertArrayEquals(input, decompressed, "SMAZ very short '$s' failed")
        }
    }

    @Test
    fun testSmazLongerText() {
        val compressor = SmazCompressor()
        val input = "the quick brown fox jumps over the lazy dog and the cat".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "SMAZ longer text roundtrip failed")
    }

    // ==================== Brotli-Static Compressor Tests ====================

    @Test
    fun testBrotliBasicRoundTrip() {
        val compressor = BrotliCompressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Brotli roundtrip failed for protocol text")
    }

    @Test
    fun testBrotliEmpty() {
        val compressor = BrotliCompressor()
        assertArrayEquals(byteArrayOf(), compressor.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testBrotliShortText() {
        val compressor = BrotliCompressor()
        val input = shortText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Brotli short text roundtrip failed")
    }

    @Test
    fun testBrotliRepeatedText() {
        val compressor = BrotliCompressor()
        val input = "ABCDEF".repeat(100).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Brotli repeated text roundtrip failed")
        assertTrue(compressed.size < input.size, "Brotli should compress repeated text")
    }

    @Test
    fun testBrotliSdpFiles() {
        val compressor = BrotliCompressor()
        val sdp = loadSdpFiles()
        val input = sdp.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Brotli SDP files roundtrip failed")
        println("Brotli: ${input.size} -> ${compressed.size} (${compressed.size * 100 / input.size}%)")
    }

    @Test
    fun testBrotliCustomDictionary() {
        val customDict = listOf("foobar", "bazqux", "hello world")
        val compressor = BrotliCompressor(staticDict = customDict)
        val input = "foobar hello world bazqux foobar".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Brotli custom dict roundtrip failed")
    }

    // ==================== LZ4 Compressor Tests ====================

    @Test
    fun testLZ4BasicRoundTrip() {
        val compressor = LZ4Compressor()
        val input = repeatedText.repeat(10).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "LZ4 roundtrip failed")
    }

    @Test
    fun testLZ4Empty() {
        val compressor = LZ4Compressor()
        // LZ4 with empty input: roundtrip should produce empty
        val compressed = compressor.compress(byteArrayOf())
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(byteArrayOf(), decompressed, "LZ4 empty roundtrip failed")
        assertArrayEquals(byteArrayOf(), compressor.decompress(byteArrayOf()))
    }

    @Test
    fun testLZ4TooSmall() {
        val compressor = LZ4Compressor()
        val input = "ab".toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "LZ4 too-small roundtrip failed")
    }

    @Test
    fun testLZ4ProtocolText() {
        val compressor = LZ4Compressor()
        val input = protocolText.toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "LZ4 protocol text roundtrip failed")
    }

    @Test
    fun testLZ4LargePayload() {
        val compressor = LZ4Compressor()
        val input = protocolText.repeat(100).toByteArray()
        val compressed = compressor.compress(input)
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "LZ4 large payload roundtrip failed")
        assertTrue(compressed.size < input.size, "LZ4 should compress large repeated data")
        println("LZ4: ${input.size} -> ${compressed.size} (${compressed.size * 100 / input.size}%)")
    }

    @Test
    fun testLZ4Speed() {
        val compressor = LZ4Compressor()
        val input = protocolText.repeat(50).toByteArray()

        val startC = System.nanoTime()
        val compressed = compressor.compress(input)
        val compressTime = (System.nanoTime() - startC) / 1_000_000.0

        val startD = System.nanoTime()
        compressor.decompress(compressed)
        val decompressTime = (System.nanoTime() - startD) / 1_000_000.0

        println("LZ4 speed: compress=${compressTime}ms, decompress=${decompressTime}ms for ${input.size} bytes")
    }

    // ==================== Compression Pipeline Tests ====================

    @Test
    fun testPipelineChain() {
        val pipeline = CompressionPipeline.chain(
            StaticDictionaryCompressor(),
            HuffmanCompressor()
        )
        val registry = mapOf(
            "static-dict" to StaticDictionaryCompressor(),
            "huffman" to HuffmanCompressor()
        )

        val input = protocolText.toByteArray()
        val compressed = pipeline.compress(input)
        val decompressed = pipeline.decompress(compressed, registry)
        assertArrayEquals(input, decompressed, "Pipeline chain roundtrip failed")
    }

    @Test
    fun testPipelineSelectBest() {
        val input = protocolText.toByteArray()
        val result = CompressionPipeline.selectBest(
            input,
            HuffmanCompressor(),
            RLECompressor(),
            LZ4Compressor()
        )
        assertTrue(result.ratio <= 1.0, "Best compressor ratio should be <= 1.0")
        println("Best compressor: ${result.compressorName} with ratio ${result.ratio}")
    }

    @Test
    fun testPipelineSelectBestEmpty() {
        val result = CompressionPipeline.selectBest(byteArrayOf(), HuffmanCompressor())
        assertEquals(0.0, result.ratio, "Empty input should have 0 ratio")
    }

    @Test
    fun testPipelineParallelChunked() {
        val compressor = HuffmanCompressor()
        val input = protocolText.repeat(20).toByteArray()

        val compressed = CompressionPipeline.compressParallelChunked(input, compressor, 1024)
        val decompressed = CompressionPipeline.decompressParallelChunked(compressed, compressor)
        assertArrayEquals(input, decompressed, "Parallel chunked roundtrip failed")
    }

    @Test
    fun testPipelineParallelChunkedSmall() {
        val compressor = HuffmanCompressor()
        val input = shortText.toByteArray()

        val compressed = CompressionPipeline.compressParallelChunked(input, compressor, 8192)
        // Small input goes through single compress, not chunked
        val decompressed = compressor.decompress(compressed)
        assertArrayEquals(input, decompressed, "Parallel chunked small roundtrip failed")
    }

    @Test
    fun testPipelineBenchmark() {
        val input = protocolText.toByteArray()
        val results = CompressionPipeline.benchmark(
            input, 3,
            HuffmanCompressor(),
            RLECompressor(),
            LZ4Compressor(),
            StaticDictionaryCompressor(),
            BrotliCompressor()
        )

        assertTrue(results.isNotEmpty())
        for (result in results) {
            println(result)
            assertTrue(result.originalSize == input.size)
            assertTrue(result.avgCompressMs >= 0)
            assertTrue(result.avgDecompressMs >= 0)
        }
    }

    // ==================== Multi-Thread Safety Tests ====================

    @Test
    fun testHuffmanThreadSafety() {
        val compressor = HuffmanCompressor()
        val input = protocolText.toByteArray()
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..20).map {
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) errors.incrementAndGet()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "Huffman had thread safety issues")
    }

    @Test
    fun testLZ4ThreadSafety() {
        val compressor = LZ4Compressor()
        val input = protocolText.repeat(5).toByteArray()
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..20).map {
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) errors.incrementAndGet()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "LZ4 had thread safety issues")
    }

    @Test
    fun testANSThreadSafety() {
        val compressor = ANSCompressor()
        val input = repeatedText.repeat(5).toByteArray()
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..20).map {
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) errors.incrementAndGet()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "ANS had thread safety issues")
    }

    @Test
    fun testStaticDictThreadSafety() {
        val compressor = StaticDictionaryCompressor()
        val input = protocolText.toByteArray()
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..20).map {
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) errors.incrementAndGet()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "StaticDict had thread safety issues")
    }

    @Test
    fun testBrotliThreadSafety() {
        val compressor = BrotliCompressor()
        val input = protocolText.toByteArray()
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = (1..20).map {
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) errors.incrementAndGet()
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "Brotli had thread safety issues")
    }

    @Test
    fun testAllCompressorsParallel() {
        val input = protocolText.repeat(3).toByteArray()
        val compressors = listOf(
            HuffmanCompressor(),
            RLECompressor(),
            LZ4Compressor(),
            StaticDictionaryCompressor(),
            BrotliCompressor(),
            SmazCompressor(),
            ANSCompressor()
        )
        val errors = AtomicInteger(0)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val futures = compressors.map { compressor ->
                executor.submit {
                    try {
                        val compressed = compressor.compress(input)
                        val decompressed = compressor.decompress(compressed)
                        if (!input.contentEquals(decompressed)) {
                            println("FAIL: ${compressor.name}")
                            errors.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        println("ERROR in ${compressor.name}: ${e.message}")
                        errors.incrementAndGet()
                    }
                }
            }
            futures.forEach { it.get() }
        }
        assertEquals(0, errors.get(), "Some compressors failed in parallel execution")
    }

    // ==================== Cross-Compressor Comparison ====================

    @Test
    fun testCompressionRatioComparison() {
        val sdp = loadSdpFiles()
        val input = sdp.toByteArray()

        val compressors = listOf<Compressor>(
            HuffmanCompressor(),
            RLECompressor(),
            LZ4Compressor(),
            StaticDictionaryCompressor(),
            BrotliCompressor(),
            ANSCompressor()
        )

        println("\n=== Compression Ratio Comparison (${input.size} bytes) ===")
        for (compressor in compressors) {
            val compressed = compressor.compress(input)
            val decompressed = compressor.decompress(compressed)
            assertArrayEquals(input, decompressed, "${compressor.name} roundtrip verification failed")
            println("${compressor.name}: ${compressed.size} bytes (${compressed.size * 100 / input.size}%)")
        }
    }
}
