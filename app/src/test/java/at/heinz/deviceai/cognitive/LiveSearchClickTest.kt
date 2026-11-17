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

    @Test fun semanticSearchAcceptsUniqueRealIdWithDifferentHint() {
        val req = request.copy(expectedLabel = "", expectedSearchField = true)
        for (hint in listOf("Fragen oder suchen", "Suche", "")) {
            val observed = field.copy(label = hint, viewId = "com.whatsapp:id/search_src_text", enabled = true)
            assertEquals(11L, VerifiedNodeClick.execute(req, true, port(frame(10, button), frame(11, observed))).revision)
        }
        assertEquals(11L, VerifiedNodeClick.execute(req, true,
            port(frame(10, button), frame(11, field.copy(label = "Suche", enabled = true)))).revision)
    }

    @Test fun semanticSearchRejectsAmbiguousWrongRoleAndStaleObservations() {
        val req = request.copy(expectedLabel = "", expectedSearchField = true)
        val real = field.copy(label = "Fragen oder suchen", viewId = "com.whatsapp:id/search_src_text", enabled = true)
        val invalid = listOf(frame(11, real, real.copy(label = "Search")),
            frame(11, real.copy(editable = false)), frame(11, real.copy(enabled = false)),
            frame(11, real.copy(sensitive = true)), frame(11, real.copy(visible = false)),
            frame(11, real.copy(viewId = "com.whatsapp:id/message")),
            frame(11, real.copy(viewId = "com.whatsapp:id/menuitem_search")),
            frame(10, real), frame(11, real).copy(digest = "digest-10"))
        for (after in invalid) {
            assertThrows(IllegalStateException::class.java) {
                VerifiedNodeClick.execute(req, true, port(frame(10, button), after))
            }
        }
    }

    @Test fun semanticSearchCannotIgnorePreexistingFieldOrOwnerRefusal() {
        val req = request.copy(expectedLabel = "", expectedSearchField = true)
        val real = field.copy(enabled = true)
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(req, true, port(frame(10, button, real), frame(11, real)))
        }
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(req, false, port(frame(10, button), frame(11, real)))
        }
    }

    @Test fun searchRoleCannotBeCombinedWithAnotherExpectation() {
        val req = request.copy(expectedLabel = "", expectedSearchField = true)
        for (invalid in listOf(req.copy(expectedLabel = "Nachricht"), req.copy(expectedHeading = true),
                req.copy(expectedEditable = false), req.copy(expectedComposeField = true))) {
            assertThrows(IllegalArgumentException::class.java) {
                VerifiedNodeClick.execute(invalid, true, port(frame(10, button), frame(11, field)))
            }
        }
    }

    @Test fun composerRoleRequiresOneNewRealFieldAndOwnerApproval() {
        val contact = button.copy(label = "Anna")
        val req = request.copy(label = "Anna", expectedLabel = "", expectedComposeField = true)
        val message = field.copy(label = "Message", enabled = true)
        for (label in listOf("Message", "Nachricht")) {
            assertEquals(11L, VerifiedNodeClick.execute(req, true,
                port(frame(10, contact), frame(11, message.copy(label = label)))).revision)
        }
        for (after in listOf(frame(11, message, message.copy(label = "Nachricht")),
                frame(11, message.copy(sensitive = true)), frame(11, message.copy(enabled = false)),
                frame(11, message.copy(editable = false)), frame(11, message.copy(visible = false)),
                frame(11, message.copy(viewId = "com.whatsapp:id/send_button")),
                frame(11, message.copy(viewId = "com.whatsapp:id/search_src_text")),
                frame(10, message), frame(11, message).copy(digest = "digest-10"))) {
            assertThrows(IllegalStateException::class.java) {
                VerifiedNodeClick.execute(req, true, port(frame(10, contact), after))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(req, false, port(frame(10, contact), frame(11, message)))
        }
        assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(req, true, port(frame(10, contact, message), frame(11, message)))
        }
    }
}
