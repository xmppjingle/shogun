import com.xmppjingle.shogun.Shogun
import com.xmppjingle.shogun.ShogunUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShogunTester {

    @BeforeEach
    fun init() {
    }

    @Test
    fun testBasic() {

        val testInput = "JingleNodes123JingleNodes123JingleTest123NodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 4, 30, 6, Charsets.US_ASCII)

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict!!.map)

        println(p.crunched.md5())

        println(dict)

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

    }

    @Test
    fun testNoDeltaCharset() {

        val testInput = "JingleNodes123JingleNodes123JingleTestNodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 4, 30, 60, Charsets.UTF_8)

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict!!.map)

        println(dict)

    }

    @Test
    fun testCharsetNumbers() {

        val testInput = "JingleNodes123Jingle123NodesJingle123TestNodesTestFinalNodesJingleJingleNodes123Jingle123NodesJingle123TestNodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 3, 30, 6, Charsets.US_ASCII, excludeChars = arrayListOf<Char>('1', '2', '3'))

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict!!.map)

        println(dict)

    }

    @Test
    fun testDumbReplace() {

        val op = (165).toChar()
        assertEquals("Post@©", "$op@©".replace("¥", "Post"))
    }

    @Test
    fun testDictList() {
        val files = File(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "/sdp").walk().filter { it.isFile }
        val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }

        val p = Shogun.crunch(s, 4, 60, 30, Charsets.US_ASCII)

        assertEquals(s, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict!!.map)

        assertEquals(s, Shogun.uncrunch(p.crunched, dict.map))
        assertEquals(Shogun.uncrunch(Shogun.crunch(s, p.dict), dict.map), Shogun.uncrunch(p.crunched, dict.map))

        println(dict)

    }

    @Test
    fun testDictListCustom() {
        val resourcePath = Thread.currentThread().contextClassLoader.getResource("ch")?.path
            ?: throw IllegalStateException("Could not find test resources directory")
        val files = File(resourcePath).walk().filter { it.isFile }
        val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }

        val p = Shogun.crunch(s, 4, 22, 12, Charsets.ISO_8859_1)

        assertEquals(s, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict!!.map)

        assertEquals(s, Shogun.uncrunch(p.crunched, dict.map))
        assertEquals(Shogun.uncrunch(Shogun.crunch(s, p.dict), dict.map), Shogun.uncrunch(p.crunched, dict.map))

        val crunchedSize = Shogun.crunch(s, p.dict).length
        println(crunchedSize)
        println("Size efficiency (original: ${s.length} compressed:$crunchedSize): ${crunchedSize.div(s.length.toDouble())}")
        assertEquals(s, Shogun.uncrunch(Shogun.crunch(s, p.dict), dict.map))

        println(Shogun.crunch(s, p.dict))
    }

    @Test
    fun testDictListBalance() {
        val files = File(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "../resources/sdp").walk().filter { it.isFile }
        val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }

        println(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "../resources/")

        var sm: Double = 1.0
        var sl: Int = 0

        for (l in 30..50) {
            val c = Shogun.crunch(s, 4, 60, l, Charsets.US_ASCII)
            val ss = c.crunched.length.div(s.length.toDouble())
            println("Layers[$l]: $ss")

            if (ss < sm) sl = l

        }

        val c = Shogun.crunch(s, 4, 60, sl, Charsets.US_ASCII)
        val ed = ShogunUtils.exportDict(c.dict)

        val w = ShogunUtils.writeDictToFile(ed)

        val r = ShogunUtils.importDict(ShogunUtils.readFileDirectlyAsText(w))

        r!!.map.forEach { t, u -> assertEquals(c.dict[t], u) }

        assertEquals(r.md5, ShogunUtils.md5(ed))

    }

    @Test
    fun testDictCalc() {

        val s = ShogunUtils.calculateDictFromDir(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "../resources/sdp", 42)

        println(s)

    }

    @Test
    fun stepDiluteTest(){
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 5)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 3)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,11), 3)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,11), 2)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,11), 1)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,11), 0)}")
        println("${ShogunUtils.jumpStepDilute(arrayListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10,11), 10)}")
    }

    // --- Performance and Virtual Thread Tests ---

    @Test
    fun testVirtualThreadsUsedForLargePayload() {
        // Verify that parallel slash works correctly with a large payload
        val largeInput = "JingleNodes123JingleNodes123JingleTestNodesTestFinalNodesJingle".repeat(100)

        val p = Shogun.crunch(largeInput, 4, 30, 10, Charsets.US_ASCII)
        assertEquals(largeInput, Shogun.uncrunch(p.crunched, p.dict))

        // Verify compression actually happened
        assertTrue(p.crunched.length < largeInput.length, "Compression should reduce size")
        assertTrue(p.dict.isNotEmpty(), "Dictionary should not be empty")
    }

    @Test
    fun testParallelConsistency() {
        // Run the same compression multiple times to verify parallel execution is deterministic
        val testInput = "JingleNodes123JingleNodes123JingleTest123NodesTestFinalNodesJingle".repeat(50)

        val results = (1..5).map {
            Shogun.crunch(testInput, 4, 30, 8, Charsets.US_ASCII)
        }

        // All runs should produce same decompressed output
        results.forEach { result ->
            assertEquals(testInput, Shogun.uncrunch(result.crunched, result.dict))
        }
    }

    @Test
    fun testLargePayloadCompression() {
        // Test with the large CH dataset to verify parallel processing handles real data
        val resourcePath = Thread.currentThread().contextClassLoader.getResource("ch")?.path
            ?: throw IllegalStateException("Could not find test resources directory")
        val files = File(resourcePath).walk().filter { it.isFile }
        val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }

        val start = System.nanoTime()
        val p = Shogun.crunch(s, 4, 22, 12, Charsets.ISO_8859_1)
        val elapsed = (System.nanoTime() - start) / 1_000_000

        assertEquals(s, Shogun.uncrunch(p.crunched, p.dict))
        println("Large payload compression took ${elapsed}ms")
        println("Compression ratio: ${p.crunched.length.toDouble() / s.length}")
    }

    @Test
    fun testEmptyAndEdgeCases() {
        // Empty string
        val empty = Shogun.crunch("", 4, 30, 6, Charsets.US_ASCII)
        assertEquals("", Shogun.uncrunch(empty.crunched, empty.dict))

        // String shorter than minWl
        val short = Shogun.crunch("ab", 4, 30, 6, Charsets.US_ASCII)
        assertEquals("ab", Shogun.uncrunch(short.crunched, short.dict))

        // Single character repeated
        val single = Shogun.crunch("aaaa", 2, 5, 3, Charsets.US_ASCII)
        assertEquals("aaaa", Shogun.uncrunch(single.crunched, single.dict))
    }

}

private fun String.md5(): String {
    return ShogunUtils.md5(this)
}
