import com.xmppjingle.shogun.Shogun
import com.xmppjingle.shogun.ShogunUtils
import org.junit.jupiter.api.Assertions.assertEquals
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

        val testInput = "JingleNodesJingleNodesJingleTestNodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 4, 30, 6, Charsets.US_ASCII)

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict)

        println(p.crunched.md5())

        println(dict)

    }

    @Test
    fun testNoDeltaCharset() {

        val testInput = "JingleNodesJingleNodesJingleTestNodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 4, 30, 6, Charsets.UTF_8)

        assertEquals(testInput, Shogun.uncrunch(p.crunched, p.dict))

        val jsonDict = ShogunUtils.exportDict(p.dict)

        println(jsonDict)

        val dict = ShogunUtils.importDict(jsonDict)

        assertEquals(p.dict, dict)

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

        assertEquals(p.dict, dict)

        println(dict)

    }

    fun testDictListBalance() {
        val files = File(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "/sdp").walk().filter { it.isFile }
        val s = files.joinToString { ShogunUtils.readFileDirectlyAsText(it) }

        for (l in 10..50)
            println("Layers[$l]: ${(Shogun.crunch(s, 4, 60, l, Charsets.US_ASCII).crunched.length).div(s.length.toDouble())}")

    }

}

private fun String.md5():String {
    return ShogunUtils.md5(this)
}
