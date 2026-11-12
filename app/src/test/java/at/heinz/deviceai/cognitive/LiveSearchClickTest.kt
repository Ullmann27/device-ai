package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LiveSearchClickTest {
    private val button = ClickNode("Suchen", true, true, false)
    private val field = ClickNode("Suchen", true, false, false, editable = true)
    private fun frame(revision: Long, vararg nodes: ClickNode) =
        ClickFrame("com.whatsapp", 3, revision, "digest-$revision", true, true, nodes.toList())
    private val request = ClickRequest("com.whatsapp", 3, 10, "Suchen", "Suchen", expectedEditable = true)
    private fun port(before: ClickFrame, after: ClickFrame) = object : ClickObservationPort {
        override fun checkActive() {}
        override fun current() = before
        override fun clickOnce(request: ClickRequest) = true
        override fun awaitPostcondition() = after
    }

    @Test fun searchButtonBecomesSameNamedEditableField() {
        assertEquals(11L, VerifiedNodeClick.execute(request, true, port(frame(10, button), frame(11, field))).revision)
    }

    @Test fun unchangedButtonCannotProveSearchOpened() {
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(request, true, port(frame(10, button), frame(11, button)))
        }
    }

    @Test fun passwordOrDuplicateFieldIsNotAccepted() {
        for (nodes in listOf(arrayOf(field.copy(sensitive = true)), arrayOf(field, field))) {
            assertThrows(IllegalStateException::class.java) {
                VerifiedNodeClick.execute(request, true, port(frame(10, button), frame(11, *nodes)))
            }
        }
    }

    @Test fun oldDefaultKeepsPreexistingLabelProtection() {
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(request.copy(expectedEditable = false), true,
                port(frame(10, button), frame(11, field)))
        }
    }
}
