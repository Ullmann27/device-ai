package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import org.junit.Assert.*
import org.junit.Test

class OwnerComputerWorkflowTest {
    private val pkg = "com.whatsapp"
    private val bounds = LiveTargetBounds(.1f, .1f, .8f, .2f)
    private fun node(label: String, editable: Boolean = false, clickable: Boolean = false,
                     value: String? = null, scrollable: Boolean = false) =
        ActionNode(label, null, true, true, clickable, scrollable, editable, false, bounds, value?.let(::liveSha256))
    private fun frame(revision: Long, nodes: List<ActionNode>) = NodeActionFrame(
        ObservationStamp("task", 47, revision, pkg, 4, "tree-$revision", revision * 1_000_000, revision),
        true, true, nodes)

    private inner class FakePort(var frame: NodeActionFrame) : OwnerComputerWorkflow.Port {
        val actions = mutableListOf<NodeActionRequest>()
        val clicks = mutableListOf<String>()
        val clickExpectations = mutableListOf<ClickRequest>()
        var clickRevisionDelta = 1L
        var refused = false
        var revoke = false
        var afterAction: (() -> Unit)? = null
        var afterClick: ((String, String) -> List<ActionNode>)? = null
        override fun checkActive() { if (revoke) throw SecurityException("REVOKED") }
        override fun observe() = frame
        override fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult {
            checkActive()
            actions += request
            val before = frame
            if (refused) return NodeActionResult(request.kind, EffectVerdict.UNCLEAR_UNCHANGED,
                true, true, false, before.stamp, null, "UNCLEAR")
            val target = before.nodes.single { request.selector.matches(it) }
            events.emit(LiveActionEvent.Dispatched("task", request.stepId, request.kind, pkg, target.bounds,
                before.stamp.revision, before.stamp.frameSequence))
            val nodes = before.nodes.map {
                if (request.selector.matches(it) && request.kind == NodeActionKind.SET_TEXT)
                    it.copy(textDigest = liveSha256(checkNotNull(request.text))) else it
            }
            frame = frame(before.stamp.revision + 1, nodes)
            events.emit(LiveActionEvent.Observed("task", request.stepId, frame.stamp))
            events.emit(LiveActionEvent.Verified("task", request.stepId, request.kind, EffectVerdict.CONFIRMED))
            afterAction?.invoke()
            return NodeActionResult(request.kind, EffectVerdict.CONFIRMED, true, true, true, before.stamp, frame.stamp, "CONFIRMED")
        }
        override fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                           expectedEditable: Boolean, expectedHeading: Boolean, expectedSearchField: Boolean,
                           expectedComposeField: Boolean,
                           onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame {
            checkActive()
            clicks += label
            clickExpectations += ClickRequest(pkg, before.stamp.epoch, before.stamp.revision, label,
                expectedLabel, expectedEditable, expectedHeading, expectedSearchField, expectedComposeField)
            onDispatched(bounds, before.stamp.frameSequence)
            val nodes = afterClick?.invoke(label, expectedLabel)
                ?: listOf(node(when {
                    expectedSearchField -> "Suchen"
                    expectedComposeField -> "Nachricht"
                    else -> expectedLabel
                }, editable = expectedEditable)
                    .copy(heading = expectedHeading), node("Anna", clickable = true))
            frame = frame(before.stamp.revision + clickRevisionDelta, nodes)
            return frame
        }
    }

    private fun workflow(bus: CockpitAnimationBus = CockpitAnimationBus()) =
        OwnerComputerWorkflow("task", pkg, 47, CockpitWorkEmitter("task", 9, bus))
    private fun search() = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Anna")

    @Test fun missingLiveFrameFailureNamesTheConcreteReasonWithoutAction() {
        var actions = 0
        val port = object : OwnerComputerWorkflow.Port {
            override fun checkActive() {}
            override fun observe(): NodeActionFrame = throw IllegalStateException("NO_FRESH_MONITOR_FRAME")
            override fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult {
                actions++; error("no action allowed")
            }
            override fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                               expectedEditable: Boolean, expectedHeading: Boolean, expectedSearchField: Boolean,
                               expectedComposeField: Boolean,
                               onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame {
                actions++; error("no click allowed")
            }
        }
        val result = workflow().run(OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN_DOWNLOADS), port)
        assertEquals(OwnerComputerWorkflow.Status.FAILED, result.status)
        assertTrue(result.message, result.message.contains("(Diagnose: NO_FRESH_MONITOR_FRAME)"))
        assertTrue(result.message.contains("Die letzte Wirkung ist nicht bestätigt"))
        assertEquals(0, actions)
    }

    @Test fun actualSearchClickThenSetTextWithFreshObservations() {
        val port = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
        val seen = mutableListOf<CockpitWorkEvent>()
        val bus = CockpitAnimationBus().also { it.subscribe { e -> if (e is CockpitWorkEvent) seen += e } }
        val result = workflow(bus).run(search(), port)
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, result.status)
        assertEquals(listOf("Suchen"), port.clicks)
        assertTrue(port.clickExpectations.single().expectedSearchField)
        assertEquals("", port.clickExpectations.single().expectedLabel)
        assertEquals(listOf(NodeActionKind.SET_TEXT), port.actions.map { it.kind })
        assertEquals("Anna", port.actions.single().text)
        assertEquals(2, seen.count { it.stage == CockpitWorkEvent.Stage.DISPATCHED })
        assertEquals(2, seen.count { it.stage == CockpitWorkEvent.Stage.VERIFIED })
        assertEquals(CockpitWorkEvent.Stage.COMPLETED, seen.last().stage)
    }

    @Test fun alreadyVisibleSearchDoesNotClickDecoratively() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        val result = workflow().run(search(), port)
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, result.status)
        assertTrue(port.clicks.isEmpty())
        assertEquals(1, port.actions.size)
    }

    @Test fun differingSearchHintUsesObservedUniqueIdAndExactQuery() {
        val port = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Fragen oder suchen", editable = true)
            .copy(viewId = "$pkg:id/search_src_text"), node("Anna", clickable = true)) }
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, workflow().run(search(), port).status)
        assertEquals(listOf("Suchen"), port.clicks)
        assertEquals("$pkg:id/search_src_text", port.actions.single().selector.viewId)
        assertEquals("Anna", port.actions.single().text)
    }

    @Test fun ambiguousNewSearchFieldsStopBeforeEnteringText() {
        val port = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Fragen oder suchen", editable = true)
            .copy(viewId = "$pkg:id/search_src_text"), node("Suche", editable = true)) }
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, workflow().run(search(), port).status)
        assertEquals(1, port.clicks.size)
        assertTrue(port.actions.isEmpty())
    }

    @Test fun newSearchNeedsActualEditableRoleAndFreshRevision() {
        val searchField = node("Fragen oder suchen", editable = true).copy(viewId = "$pkg:id/search_src_text")
        for (invalid in listOf(searchField.copy(editable = false), searchField.copy(enabled = false),
                searchField.copy(password = true), searchField.copy(viewId = "$pkg:id/message"))) {
            val port = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
            port.afterClick = { _, _ -> listOf(invalid) }
            assertEquals(OwnerComputerWorkflow.Status.PAUSED, workflow().run(search(), port).status)
            assertEquals(1, port.clicks.size)
            assertTrue(port.actions.isEmpty())
        }
        val stale = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
        stale.afterClick = { _, _ -> listOf(searchField) }
        stale.clickRevisionDelta = 0
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, workflow().run(search(), stale).status)
        assertEquals(1, stale.clicks.size)
        assertTrue(stale.actions.isEmpty())
    }

    @Test fun existingQueryIsObservedWithoutRetyping() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true, value = "Anna"))))
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, workflow().run(search(), port).status)
        assertTrue(port.actions.isEmpty())
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun uncertainTextIsNeverRepeatedOrReportedDone() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true)))).also { it.refused = true }
        val flow = workflow()
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, flow.run(search(), port).status)
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, flow.run(search(), port).status)
        assertEquals(1, port.actions.size)
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun ambiguousContactsAreNotChosen() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true),
            node("Anna", clickable = true))))
        val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.DRAFT_MESSAGE, "Anna", "Hallo"), port)
        assertEquals(OwnerComputerWorkflow.Status.ASK_OWNER, result.status)
        assertEquals(1, port.actions.size)
        assertTrue(port.clicks.isEmpty())
        assertFalse(result.draftPrepared)
    }

    @Test fun draftChecksChatHeaderAndActualComposeText() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Anna").copy(heading = true), node("Nachricht", editable = true, value = ""), node("Senden", clickable = true)) }
        val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.DRAFT_MESSAGE, "Anna", "Hallo!"), port)
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, result.status)
        assertTrue(result.draftPrepared)
        assertEquals(listOf("Anna"), port.clicks)
        assertFalse(port.clickExpectations.single().expectedSearchField)
        assertTrue(port.clickExpectations.single().expectedComposeField)
        assertEquals(listOf("Anna", "Hallo!"), port.actions.map { it.text })
        assertFalse(port.actions.any { it.kind == NodeActionKind.SEND })
    }

    @Test fun englishDraftUsesObservedComposerAfterDifferentSearchHints() {
        for (hint in listOf("Search", "Search...", "Search…", "Ask or search")) {
            val search = node(hint, editable = true, value = "Anna").copy(viewId = "$pkg:id/search_src_text")
            val port = FakePort(frame(1, listOf(search, node("Anna", clickable = true))))
            port.afterClick = { _, _ -> listOf(node("Anna").copy(heading = true),
                node("Message", editable = true, value = ""), node("Send", clickable = true)) }
            val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP,
                ComputerGoal.DRAFT_MESSAGE, "Anna", "Hello!"), port)
            assertEquals(hint, OwnerComputerWorkflow.Status.VERIFIED, result.status)
            assertTrue(result.draftPrepared)
            assertEquals(listOf("Anna"), port.clicks)
            assertEquals(listOf("Hello!"), port.actions.map { it.text })
            assertFalse(port.actions.any { it.kind == NodeActionKind.SEND })
        }
    }

    @Test fun invalidOrAmbiguousComposerCannotReceiveDraft() {
        val compose = node("Message", editable = true, value = "")
        val cases = listOf(listOf(compose, compose.copy(label = "Nachricht")),
            listOf(compose.copy(enabled = false)), listOf(compose.copy(visible = false)),
            listOf(compose.copy(editable = false)), listOf(compose.copy(password = true)),
            listOf(compose.copy(viewId = "$pkg:id/send_button")),
            listOf(compose.copy(viewId = "$pkg:id/search_src_text")))
        for (fields in cases) {
            val port = FakePort(frame(1, listOf(node("Search...", editable = true, value = "Anna"),
                node("Anna", clickable = true))))
            port.afterClick = { _, _ -> listOf(node("Anna").copy(heading = true)) + fields }
            val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP,
                ComputerGoal.DRAFT_MESSAGE, "Anna", "Hello!"), port)
            assertEquals(OwnerComputerWorkflow.Status.PAUSED, result.status)
            assertFalse(result.draftPrepared)
            assertTrue(port.actions.isEmpty())
            assertEquals(listOf("Anna"), port.clicks)
        }
    }

    @Test fun mismatchedChatNeverReceivesDraft() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Hanna"), node("Nachricht", editable = true)) }
        val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.DRAFT_MESSAGE, "Anna", "Hallo"), port)
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, result.status)
        assertEquals(1, port.actions.size)
        assertFalse(result.draftPrepared)
    }

    @Test fun explicitSendStillCannotBypassExistingToolGate() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Anna").copy(heading = true), node("Nachricht", editable = true, value = ""), node("Senden", clickable = true)) }
        val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEND_MESSAGE, "Anna", "Hallo"), port)
        assertEquals(OwnerComputerWorkflow.Status.ASK_OWNER, result.status)
        assertTrue(result.draftPrepared)
        assertFalse(port.clicks.contains("Senden"))
        assertFalse(port.actions.any { it.kind == NodeActionKind.SEND })
    }

    @Test fun revokeBeforeActionMakesNoInput() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true)))).also { it.revoke = true }
        assertEquals(OwnerComputerWorkflow.Status.FAILED, workflow().run(search(), port).status)
        assertTrue(port.actions.isEmpty())
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun stopDuringActionCancelsNextStepAndLateSuccess() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        val flow = workflow()
        port.afterAction = { flow.cancel() }
        assertEquals(OwnerComputerWorkflow.Status.CANCELLED, flow.run(search(), port).status)
        assertEquals(1, port.actions.size)
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun ownerCorrectionStopsExecutingDraftBeforeContactAndLateCompletion() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        val seen = mutableListOf<CockpitWorkEvent>()
        val bus = CockpitAnimationBus().also { it.subscribe { e -> if (e is CockpitWorkEvent) seen += e } }
        val flow = workflow(bus)
        port.afterAction = {
            assertTrue(OwnerCorrectionIntent.interruptsWork("Stopp, das meine ich nicht."))
            flow.cancel()
        }
        val result = flow.run(OwnerComputerIntent(ComputerApp.WHATSAPP,
            ComputerGoal.DRAFT_MESSAGE, "Anna", "Guten Morgen"), port)
        assertEquals(OwnerComputerWorkflow.Status.CANCELLED, result.status)
        assertFalse(result.draftPrepared)
        assertEquals(listOf(NodeActionKind.SET_TEXT), port.actions.map { it.kind })
        assertEquals("Anna", port.actions.single().text)
        assertTrue(port.clicks.isEmpty())
        val stopped = seen.indexOfFirst { it.stage == CockpitWorkEvent.Stage.STOPPED }
        assertTrue(stopped >= 0)
        assertTrue(seen.take(stopped).any { it.stage == CockpitWorkEvent.Stage.VERIFIED })
        assertFalse(seen.drop(stopped).any {
            it.stage in setOf(CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Stage.COMPLETED)
        })
    }

    @Test fun changedSourceAndAmbiguousSearchAreBlocked() {
        val wrong = FakePort(frame(1, listOf(node("Suchen", editable = true))).let {
            it.copy(stamp = it.stamp.copy(epoch = 48))
        })
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, workflow().run(search(), wrong).status)
        assertTrue(wrong.actions.isEmpty())
        val ambiguous = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Search", editable = true))))
        assertEquals(OwnerComputerWorkflow.Status.ASK_OWNER, workflow().run(search(), ambiguous).status)
        assertTrue(ambiguous.actions.isEmpty())
    }

    @Test fun rotatedWindowDuringSearchStopsBeforeFurtherInput() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true))))
        port.afterAction = {
            port.frame = port.frame.copy(stamp = port.frame.stamp.copy(windowId = 99))
        }
        val result = workflow().run(search(), port)
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, result.status)
        assertEquals(1, port.actions.size)
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun replacementCaptureSessionCannotConfirmAnOldComputerTask() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true))))
        port.afterAction = {
            port.frame = port.frame.copy(stamp = port.frame.stamp.copy(captureSession = 99))
        }
        val result = workflow().run(search(), port)
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, result.status)
        assertEquals(1, port.actions.size)
        assertTrue(port.clicks.isEmpty())
    }

    @Test fun lostPermissionAfterSearchClickPreventsTextEntry() {
        val port = FakePort(frame(1, listOf(node("Suchen", clickable = true))))
        port.afterClick = { _, _ ->
            port.revoke = true
            listOf(node("Suchen", editable = true))
        }
        val result = workflow().run(search(), port)
        assertEquals(OwnerComputerWorkflow.Status.FAILED, result.status)
        assertEquals(listOf("Suchen"), port.clicks)
        assertTrue(port.actions.isEmpty())
    }

    @Test fun existingDraftIsNotOverwritten() {
        val port = FakePort(frame(1, listOf(node("Suchen", editable = true), node("Anna", clickable = true))))
        port.afterClick = { _, _ -> listOf(node("Anna").copy(heading = true), node("Nachricht", editable = true, value = "Privater vorhandener Entwurf")) }
        val result = workflow().run(OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.DRAFT_MESSAGE, "Anna", "Hallo"), port)
        assertEquals(OwnerComputerWorkflow.Status.ASK_OWNER, result.status)
        assertEquals(listOf("Anna"), port.actions.map { it.text })
        assertFalse(result.draftPrepared)
    }

    @Test fun downloadsRequireNewHeadingNotJustChangedPixels() {
        val intent = OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN_DOWNLOADS)
        val verified = FakePort(frame(1, listOf(node("Downloads", clickable = true))))
        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, workflow().run(intent, verified).status)
        assertEquals(listOf("Downloads"), verified.clicks)
        assertFalse(verified.clickExpectations.single().expectedSearchField)
        val unproved = FakePort(frame(1, listOf(node("Downloads", clickable = true))))
        unproved.afterClick = { _, _ -> listOf(node("Downloads", clickable = true), node("Datei.pdf")) }
        assertEquals(OwnerComputerWorkflow.Status.PAUSED, workflow().run(intent, unproved).status)
        assertEquals(1, unproved.clicks.size)
    }
}
