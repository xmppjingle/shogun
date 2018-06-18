import com.xmppjingle.shogun.Shogun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShogunTester {

    @BeforeEach
    fun init(){}

    @Test
    fun testBasic() {

        val testInput = "JingleNodesJingleNodesJingleTestNodesTestFinalNodesJingle"

        val p = Shogun.crunch(testInput, 4, 30, 6, Charsets.US_ASCII)

        assertEquals(testInput, Shogun.hanoi(p.first, p.second))

    }

    @Test
    fun testDumbReplace(){

        val op = (165).toChar()
        assertEquals("Post@©", "$op@©".replace("¥", "Post"))
    }


}