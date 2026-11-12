package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

/** Field screenshot 12.11.2026: raw native token overflow shown to the owner. */
class LocalModelTokenOverflowTest {
    private val field = "Status Code: 3. Message: Input token ids are too long. Exceeding the maximum number of " +
        "tokens allowed: 4220 >= 4096"

    @Test fun theFieldOverflowBecomesAnHonestGermanMessage() {
        assertTrue(LocalModelTokenOverflow.isTokenOverflow(field))
        val text = LocalModelTokenOverflow.ownerMessage(field)
        assertTrue(text.contains("4220 von höchstens 4096 Token"))
        assertTrue(text.contains("nichts abgeschnitten"))
        assertFalse(text.contains("Input token ids"))
    }

    @Test fun otherFailuresAreNotMistakenForAnOverflow() {
        assertFalse(LocalModelTokenOverflow.isTokenOverflow(null))
        assertFalse(LocalModelTokenOverflow.isTokenOverflow("LOCAL_CONTINUATION_UNAVAILABLE"))
        assertFalse(LocalModelTokenOverflow.isTokenOverflow("Thermal status severe"))
        assertTrue(LocalModelTokenOverflow.ownerMessage("input token ids are too long").contains("zu lang"))
    }
}
