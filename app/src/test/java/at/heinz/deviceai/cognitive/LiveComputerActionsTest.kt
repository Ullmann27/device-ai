package at.heinz.deviceai.cognitive

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionReceipt
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.ToolReceiptTransition
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.toolArgumentFingerprint
import org.junit.Assert.*
import org.junit.Test

class LiveComputerActionsTest {
    private val pkg = "com.whatsapp"
    private fun stamp(revision: Long, digest: String, at: Long = revision * 10, task: String = "task-1", epoch: Long = 3,
                      app: String = pkg) = ObservationStamp(task, epoch, revision, app, 42, digest, at)
    private val bounds = LiveTargetBounds(.1f, .1f, .9f, .2f)
    private val search = ActionNode("Suchen", "com.whatsapp:id/search_src_text", true, true, true, false, true, false, bounds)
    private val list = ActionNode("", "com.whatsapp:id/list", true, true, false, true, false, false, LiveTargetBounds(0f, .2f, 1f, 1f))
    private val send = ActionNode("Senden", "com.whatsapp:id/send", true, true, true, false, false, false, LiveTargetBounds(.85f, .9f, .95f, .98f))
    private val compose = ActionNode("Nachricht", "com.whatsapp:id/entry", true, true, true, false, true, false, bounds,
        textDigest = liveSha256("Ich bin in 20 Minuten da."))

    private class Port(var before: NodeActionFrame, var after: NodeActionFrame?) : NodeActionPort {
        var dispatches = 0
        var accept: Boolean? = true
        var failCheckAt = Int.MAX_VALUE
        var checks = 0
        override fun checkActive() { if (++checks >= failCheckAt) error("CANCELLED") }
        override fun current() = before
        override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean {
            dispatches++
            return accept ?: error("BINDER_DIED")
        }
        override fun awaitPostcondition() = after
    }
    private fun frame(stamp: ObservationStamp, vararg nodes: ActionNode, safe: Boolean = true, complete: Boolean = true) =
        NodeActionFrame(stamp, safe, complete, nodes.toList())
    private fun request(kind: NodeActionKind, selector: NodeSelector, text: String? = null, revision: Long = 10) =
        NodeActionRequest("task-1", "step-1", pkg, 3, revision, kind, selector, text)
    private fun movement(port: Port, request: NodeActionRequest) {
        port.after = port.after!!.copy(scrollEvidence = LiveScrollEvidence(port.before.stamp, request.fingerprint,
            100, 101, port.after!!.stamp.revision, deltaY = 120))
    }
    private val events = mutableListOf<LiveActionEvent>()
    private val journal = LiveActionJournal("task-1")
    private val action = VerifiedNodeAction(journal) { events += it }

    @Test fun verifierConfirmsOnlyANewChangedMatchingObservation() {
        val b = stamp(10, "a")
        assertEquals(EffectVerdict.CONFIRMED, ActionEffectVerifier.verify(b, true, stamp(11, "b"), true))
        assertEquals(EffectVerdict.NOT_DISPATCHED, ActionEffectVerifier.verify(b, false, null, false))
        assertEquals(EffectVerdict.UNCLEAR_DISPATCH, ActionEffectVerifier.verify(b, null, stamp(11, "b"), true))
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, ActionEffectVerifier.verify(b, true, null, true))
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, ActionEffectVerifier.verify(b, true, stamp(10, "b"), true))
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, ActionEffectVerifier.verify(b, true, stamp(11, "b", at = 100), true))
        assertEquals(EffectVerdict.UNCLEAR_UNCHANGED, ActionEffectVerifier.verify(b, true, stamp(11, "a"), true))
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, ActionEffectVerifier.verify(b, true, stamp(11, "b"), false))
        for (other in listOf(stamp(11, "b", task = "task-2"), stamp(11, "b", epoch = 4),
            stamp(11, "b", app = "com.facebook.katana"), stamp(11, "b").copy(windowId = 43)))
            assertEquals(EffectVerdict.UNCLEAR_CONTEXT_CHANGED, ActionEffectVerifier.verify(b, true, other, true))
        assertTrue(EffectVerdict.values().filter { it.unclear }.none { it.confirmed })
        assertFalse(EffectVerdict.NOT_DISPATCHED.unclear)
    }

    @Test fun setTextIsConfirmedOnlyWhenTheFieldHoldsTheDraft() {
        val typed = search.copy(textDigest = liveSha256("Anna"))
        val port = Port(frame(stamp(10, "a"), search, list), frame(stamp(11, "b"), typed, list))
        val result = action.execute(request(NodeActionKind.SET_TEXT, NodeSelector(viewId = search.viewId), "Anna"), port)
        assertEquals(EffectVerdict.CONFIRMED, result.verdict)
        assertTrue(result.nodeFound && result.accepted == true && result.postObserved && result.confirmed)
        assertEquals(1, port.dispatches)
        val dispatched = events.filterIsInstance<LiveActionEvent.Dispatched>().single()
        assertEquals(bounds, dispatched.target)
        assertEquals(10, dispatched.observationRevision)
        assertEquals(EffectVerdict.CONFIRMED, events.filterIsInstance<LiveActionEvent.Verified>().single().verdict)
        // Wrong text in the field after the action: accepted, observed, but NOT confirmed.
        val other = VerifiedNodeAction(LiveActionJournal("task-1"))
        val wrong = Port(frame(stamp(10, "a"), search), frame(stamp(11, "b"), search.copy(textDigest = liveSha256("Ann"))))
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET,
            other.execute(request(NodeActionKind.SET_TEXT, NodeSelector(viewId = search.viewId), "Anna"), wrong).verdict)
    }

    @Test fun acceptedScrollWithoutChangeIsUnclearAndNeverRepeated() {
        val port = Port(frame(stamp(10, "a"), list), frame(stamp(11, "a"), list))
        val scroll = request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        val first = action.execute(scroll, port)
        assertEquals(EffectVerdict.UNCLEAR_UNCHANGED, first.verdict)
        assertEquals(true, first.accepted)
        assertFalse(first.confirmed)
        // Same action again, even on a newer observation: blocked by the journal, no second dispatch.
        port.before = frame(stamp(12, "c"), list)
        val again = action.execute(scroll.copy(revision = 12), port)
        assertEquals(EffectVerdict.NOT_DISPATCHED, again.verdict)
        assertEquals("JOURNAL_BLOCKED_AFTER_UNCLEAR", again.reason)
        assertEquals(1, port.dispatches)
    }

    @Test fun afterUnclearOnlyAFresherObservationAdmitsOtherActions() {
        val port = Port(frame(stamp(10, "a"), list, search), null)
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION,
            action.execute(request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true)), port).verdict)
        val type = request(NodeActionKind.SET_TEXT, NodeSelector(viewId = search.viewId), "Anna")
        assertEquals("JOURNAL_NEEDS_FRESH_OBSERVATION", action.execute(type, port).reason)
        port.before = frame(stamp(11, "b"), list, search)
        port.after = frame(stamp(12, "c"), list, search.copy(textDigest = liveSha256("Anna")))
        assertEquals(EffectVerdict.CONFIRMED, action.execute(type.copy(revision = 11), port).verdict)
        assertEquals(2, port.dispatches)
    }

    @Test fun duplicateOnTheSameObservationIsRefusedButARealNextScrollIsAllowed() {
        val port = Port(frame(stamp(10, "a"), list), frame(stamp(11, "b"), list))
        val scroll = request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        movement(port, scroll)
        assertEquals(EffectVerdict.CONFIRMED, action.execute(scroll, port).verdict)
        assertEquals("JOURNAL_DUPLICATE_ON_SAME_OBSERVATION", action.execute(scroll, port).reason)
        port.before = frame(stamp(11, "b"), list); port.after = frame(stamp(12, "c"), list)
        movement(port, scroll.copy(revision = 11))
        assertEquals(EffectVerdict.CONFIRMED, action.execute(scroll.copy(revision = 11), port).verdict)
        assertEquals(2, port.dispatches)
    }

    @Test fun staleUnsafeAmbiguousOrSensitiveTargetsNeverDispatch() {
        val text = request(NodeActionKind.SET_TEXT, NodeSelector(viewId = search.viewId), "Anna")
        val cases = listOf(
            frame(stamp(9, "a"), search) to "STALE_OR_UNSAFE_SCREEN",
            frame(stamp(10, "a", app = "com.facebook.katana"), search) to "STALE_OR_UNSAFE_SCREEN",
            frame(stamp(10, "a", task = "task-2"), search) to "STALE_OR_UNSAFE_SCREEN",
            frame(stamp(10, "a"), search, safe = false) to "STALE_OR_UNSAFE_SCREEN",
            frame(stamp(10, "a"), search, complete = false) to "STALE_OR_UNSAFE_SCREEN",
            frame(stamp(10, "a")) to "TARGET_NOT_FOUND",
            frame(stamp(10, "a"), search, search) to "AMBIGUOUS_TARGET",
            frame(stamp(10, "a"), search.copy(password = true)) to "UNSAFE_TEXT_TARGET",
            frame(stamp(10, "a"), search.copy(editable = false)) to "UNSAFE_TEXT_TARGET",
            frame(stamp(10, "a"), search.copy(textDigest = liveSha256("Anna"))) to "POSTCONDITION_ALREADY_PRESENT"
        )
        for ((before, reason) in cases) {
            val port = Port(before, frame(stamp(11, "b"), search))
            val result = VerifiedNodeAction(LiveActionJournal("task-1")).execute(text, port)
            assertEquals(reason, result.reason)
            assertEquals(EffectVerdict.NOT_DISPATCHED, result.verdict)
            assertEquals(0, port.dispatches)
        }
        assertEquals("CLICK_USES_VERIFIED_NODE_CLICK",
            action.execute(request(NodeActionKind.CLICK, NodeSelector(label = "Anna")), Port(frame(stamp(10, "a")), null)).reason)
        assertThrows(IllegalArgumentException::class.java) { NodeSelector(label = "x", viewId = "y") }
        assertThrows(IllegalArgumentException::class.java) { request(NodeActionKind.SET_TEXT, NodeSelector(label = "x"), "a\nb") }
        assertThrows(IllegalArgumentException::class.java) { request(NodeActionKind.SET_TEXT, NodeSelector(label = "x"), null) }
    }

    @Test fun refusedDispatchCanBeRetriedOnlyOnAFreshObservation() {
        val port = Port(frame(stamp(10, "a"), list), frame(stamp(11, "b"), list)).apply { accept = false }
        val scroll = request(NodeActionKind.SCROLL_BACKWARD, NodeSelector(singleScrollable = true))
        assertEquals(EffectVerdict.NOT_DISPATCHED, action.execute(scroll, port).verdict)
        assertTrue(events.none { it is LiveActionEvent.Dispatched })
        assertEquals("JOURNAL_DUPLICATE_ON_SAME_OBSERVATION", action.execute(scroll, port).reason)
        port.accept = true; port.before = frame(stamp(12, "c"), list); port.after = frame(stamp(13, "d"), list)
        movement(port, scroll.copy(revision = 12))
        assertEquals(EffectVerdict.CONFIRMED, action.execute(scroll.copy(revision = 12), port).verdict)
    }

    @Test fun unrelatedBadgeChangeCannotConfirmScroll() {
        val port = Port(frame(stamp(10, "badge-1"), list), frame(stamp(11, "badge-2"), list))
        val scroll = request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        assertEquals(EffectVerdict.UNCLEAR_EXPECTATION_NOT_MET, action.execute(scroll, port).verdict)
        assertEquals(1, port.dispatches)
    }

    @Test fun scrollEvidenceMustMatchActionWindowRevisionAndTime() {
        val scroll = request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        val before = stamp(10, "a")
        val valid = LiveScrollEvidence(before, scroll.fingerprint, 100, 101, 11, deltaY = 120)
        val after = frame(stamp(11, "b"), list)
        assertTrue(after.copy(scrollEvidence = valid).confirmsScroll(scroll, before))
        val invalid = listOf(valid.copy(before = before.copy(windowId = 43)),
            valid.copy(before = before.copy(epoch = 4)), valid.copy(before = before.copy(taskId = "task-2")),
            valid.copy(before = before.copy(revision = 9)), valid.copy(fingerprint = "another-container"),
            valid.copy(eventAtMillis = 99), valid.copy(revision = 10), valid.copy(revision = 12),
            valid.copy(deltaY = 0))
        for (proof in invalid) assertFalse(proof.toString(), after.copy(scrollEvidence = proof).confirmsScroll(scroll, before))
        assertFalse(after.copy(stamp = after.stamp.copy(windowId = 43), scrollEvidence = valid).confirmsScroll(scroll, before))
    }

    @Test fun uncertainJournalOverflowNeverForgetsEarlierActions() {
        val journal = LiveActionJournal("task-1", capacity = 2)
        for (i in 1..3) {
            val before = stamp(i.toLong(), "screen-$i")
            assertEquals(LiveActionJournal.Admission.ALLOWED, journal.admit("action-$i", before))
            journal.finish("action-$i", before, EffectVerdict.UNCLEAR_DISPATCH, null)
        }
        assertEquals(LiveActionJournal.Admission.BLOCKED_AFTER_UNCLEAR, journal.admit("action-1", stamp(10, "fresh")))
        assertEquals(LiveActionJournal.Admission.BLOCKED_AFTER_UNCLEAR, journal.admit("new-action", stamp(11, "fresher")))
    }

    @Test fun failuresAfterDispatchBecomeUnclearNotExceptionsOrRetries() {
        val thrown = Port(frame(stamp(10, "a"), list), frame(stamp(11, "b"), list)).apply { accept = null }
        val scroll = request(NodeActionKind.SCROLL_FORWARD, NodeSelector(singleScrollable = true))
        val r1 = action.execute(scroll, thrown)
        assertEquals(EffectVerdict.UNCLEAR_DISPATCH, r1.verdict)
        assertTrue(r1.reason.startsWith("DISPATCH_FAILED"))
        val late = Port(frame(stamp(10, "a"), list), frame(stamp(11, "b"), list)).apply { failCheckAt = 3 }
        val r2 = VerifiedNodeAction(LiveActionJournal("task-1")).execute(scroll, late)
        assertEquals(EffectVerdict.UNCLEAR_NO_NEW_OBSERVATION, r2.verdict)
        assertTrue(r2.reason.startsWith("POSTCONDITION_INTERRUPTED"))
        assertEquals(1, late.dispatches)
        // Cancellation before dispatch propagates: nothing happened, nothing journaled as unclear.
        val early = Port(frame(stamp(10, "a"), list), null).apply { failCheckAt = 1 }
        val j = LiveActionJournal("task-1")
        assertThrows(IllegalStateException::class.java) { VerifiedNodeAction(j).execute(scroll, early) }
        assertEquals(0, early.dispatches)
        assertEquals(LiveActionJournal.Admission.ALLOWED, j.admit(scroll.fingerprint, stamp(10, "a")))
    }

    @Test fun sendIsImpossibleWithoutTheExactToolGateApproval() {
        val draft = "Ich bin in 20 Minuten da."
        val sendRequest = request(NodeActionKind.SEND, NodeSelector(viewId = send.viewId), draft)
        val port = Port(frame(stamp(10, "a"), compose, send), frame(stamp(11, "b"), compose.copy(textDigest = null), send))
        assertEquals("SEND_REQUIRES_EXACT_OWNER_APPROVAL", action.execute(sendRequest, port).reason)
        // Setting text or scrolling can never target a send button.
        assertEquals("UNSAFE_TEXT_TARGET", VerifiedNodeAction(LiveActionJournal("task-1"))
            .execute(request(NodeActionKind.SET_TEXT, NodeSelector(viewId = send.viewId), draft), port).reason)
        assertEquals(0, port.dispatches)

        // Production: no registered tool can mint a send approval, whatever the receipt says.
        val args = mapOf<String, Any?>("packageOrName" to pkg, "text" to draft)
        for (spec in ToolRegistry.all()) {
            val call = ToolCallRequest("call-1", spec.name, args)
            assertNull(SendApproval.mint(receipt(spec.name, spec.risk, args), call, "task-1"))
        }
        assertTrue(SendApproval.SEND_TOOLS.isEmpty())

        // Binding rules, checked with a registered DESTRUCTIVE tool standing in for a future send tool.
        val stand = "delete_file"
        val call = ToolCallRequest("call-1", stand, args)
        val tools = setOf(stand)
        assertNull("not committed", SendApproval.mint(receipt(stand, Risk.DESTRUCTIVE, args, committed = false), call, "task-1", tools))
        assertNull("other draft", SendApproval.mint(receipt(stand, Risk.DESTRUCTIVE, args + ("text" to "Anders")), call, "task-1", tools))
        assertNull("other call", SendApproval.mint(receipt(stand, Risk.DESTRUCTIVE, args), call.copy(callId = "call-2"), "task-1", tools))
        val approval = SendApproval.mint(receipt(stand, Risk.DESTRUCTIVE, args), call, "task-1", tools)!!
        assertFalse(approval.consume("task-1", pkg, "Ich bin in 30 Minuten da."))
        val ok = action.execute(sendRequest, port, approval)
        assertEquals(EffectVerdict.CONFIRMED, ok.verdict)
        assertEquals(1, port.dispatches)
        // Single use: the same approval never covers a second send.
        port.before = frame(stamp(12, "c"), compose, send); port.after = frame(stamp(13, "d"), send)
        assertEquals("SEND_APPROVAL_DOES_NOT_COVER_THIS_DRAFT",
            VerifiedNodeAction(LiveActionJournal("task-1")).execute(sendRequest.copy(revision = 12), port, approval).reason)
        assertEquals(1, port.dispatches)
    }

    @Test fun sendGuardCoversLabelsAndViewIdsInGermanAndEnglish() {
        for (label in listOf("Senden", "senden", "Send", "Absenden", "Posten", "Gefällt mir", "Teilen", "Beitreten", "Anrufen", "Senden…"))
            assertTrue(label, SendActionGuard.isSendLike(label))
        for (id in listOf("com.whatsapp:id/send", "com.facebook.katana:id/post_button", "x:id/btn_share", "x:id/like"))
            assertTrue(id, SendActionGuard.isSendLike(null, id))
        for (label in listOf("Anna", "Suchen", "Chats", "Downloads", "Sendeliste anzeigen", "Akku"))
            assertFalse(label, SendActionGuard.isSendLike(label))
        assertFalse(SendActionGuard.isSendLike(null, "com.whatsapp:id/search_src_text"))
        // The existing click candidate refuses send-like targets before any observation.
        val port = object : ClickObservationPort {
            var touched = 0
            override fun current(): ClickFrame { touched++; error("unused") }
            override fun clickOnce(request: ClickRequest): Boolean { touched++; return true }
            override fun awaitPostcondition(): ClickFrame { touched++; error("unused") }
            override fun checkActive() { touched++ }
        }
        assertEquals("SEND_REQUIRES_EXACT_OWNER_APPROVAL", assertThrows(IllegalStateException::class.java) {
            VerifiedNodeClick.execute(ClickRequest(pkg, 3, 10, "Senden", "Gesendet"), true, port)
        }.message)
        assertEquals(0, port.touched)
    }

    private fun receipt(tool: String, risk: Risk, args: Map<String, Any?>, committed: Boolean = true): ToolExecutionReceipt {
        val states = mutableListOf(ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED, ToolReceiptState.START_PENDING)
        if (committed) states += ToolReceiptState.DISPATCH_COMMITTED
        return ToolExecutionReceipt("call-1", tool, toolArgumentFingerprint(args), emptyMap(), risk, 1,
            states.map { ToolReceiptTransition(it, 1) })
    }
}
