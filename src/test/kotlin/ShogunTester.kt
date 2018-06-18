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

        val p = Shogun.crunch(testInput, 5, 12, 4, Charsets.US_ASCII)

        assertEquals(testInput, Shogun.hanoi(p.first, p.second))

    }


}