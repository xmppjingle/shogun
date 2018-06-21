import com.xmppjingle.shogun.Shogun
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

        assertEquals(testInput, Shogun.uncrunch(p.first, p.second))

        val jsonDict = Shogun.exportDict(p.second)

        println(jsonDict)

        val dict = Shogun.importDict(jsonDict)

        assertEquals(p.second, dict)

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
        val s = files.joinToString { Shogun.readFileDirectlyAsText(it) }

        val p = Shogun.crunch(s, 4, 60, 30, Charsets.US_ASCII)

        assertEquals(s, Shogun.uncrunch(p.first, p.second))

        val jsonDict = Shogun.exportDict(p.second)

        println(jsonDict)

        val dict = Shogun.importDict(jsonDict)

        assertEquals(p.second, dict)

        println(dict)

    }

    fun testDictListBalance() {
        val files = File(Thread.currentThread().contextClassLoader.getResources(".").nextElement().path + "/sdp").walk().filter { it.isFile }
        val s = files.joinToString { Shogun.readFileDirectlyAsText(it) }

        for (l in 10..50)
            println("Layers[$l]: ${(Shogun.crunch(s, 4, 60, l, Charsets.US_ASCII).first.length).div(s.length.toDouble())}")

    }

}