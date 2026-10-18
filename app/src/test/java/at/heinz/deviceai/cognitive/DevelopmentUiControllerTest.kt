package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentUiControllerTest {
    private fun controller() = DevelopmentUiController { "task-ui" }.also {
        it.setGoal("Fix greeting")
        assertTrue(it.addSource("app/Greeter.kt", "fun greet() = 1\n"))
    }

    private fun completed(): DevelopmentOutcome {
        val src = SourceSnapshot.of("app/Greeter.kt", "fun greet() = 1\n")
        val task = DevelopmentTask("task-ui", "Fix greeting", listOf(src))
        val dev = """{"summary":"s","edits":[{"path":"app/Greeter.kt","expectedBaseSha256":"${src.sha256}","replacement":"fun greet() = 2\n"}]}"""
        val replies = ArrayDeque(listOf(dev, """{"verdict":"ACCEPT","findings":[]}""", """{"tests":[]}""",
            """{"decision":"RECOMMEND","rationale":"ok"}"""))
        return DevelopmentCoordinator({ ReasoningPassResult("m@1", replies.removeFirst(), 1) }, {}).run(task)
    }

    @Test
    fun noModelFailsVisiblyWithoutCloudFallback() {
        val c = controller()
        assertNull(c.start(modelReady = false))
        assertEquals(DevelopmentUiPhase.FAILED, c.state.phase)
        assertTrue(c.state.error!!.contains("keinen Cloud-Ersatz"))
        assertFalse(c.state.canExport)
    }

    @Test
    fun startWithoutSourcesOrGoalFailsVisibly() {
        val c = DevelopmentUiController { "t" }
        c.setGoal("x")
        assertNull(c.start(true))
        assertEquals(DevelopmentUiPhase.FAILED, c.state.phase)
        assertNotNull(c.state.error)
    }

    @Test
    fun runningJobTransitionsToDoneAndAllowsExportOnlyThen() {
        val c = controller()
        assertNull(c.exportText())
        val task = c.start(true)
        assertNotNull(task)
        assertTrue(c.state.canStop)
        assertFalse(c.state.canEdit)
        assertNull(c.exportText())
        c.onJobState(DevelopmentJobState.RUNNING_CRITIC)
        assertTrue(c.state.status.startsWith("2/4"))
        c.onOutcome(completed())
        assertEquals(DevelopmentUiPhase.DONE, c.state.phase)
        assertTrue(c.exportText()!!.contains("NOT EXECUTED"))
        assertFalse(c.state.canStop)
    }

    @Test
    fun stopLeadsToCancelledWithoutPreview() {
        val c = controller()
        c.start(true)
        c.stop()
        assertEquals("Wird beendet …", c.state.status)
        c.onOutcome(DevelopmentOutcome(DevelopmentJobState.CANCELLED, null, DevelopmentFailure(DevelopmentFailureKind.MODEL_FAILURE, "c")))
        assertEquals(DevelopmentUiPhase.CANCELLED, c.state.phase)
        assertNull(c.exportText())
    }

    @Test
    fun failureAndTimeoutAreSurfacedAndInputIsLockedWhileRunning() {
        val c = controller()
        c.start(true)
        assertFalse(c.addSource("a/Other.kt", "x"))
        c.setGoal("changed")
        assertEquals("Fix greeting", c.state.goal)
        c.onOutcome(DevelopmentOutcome(DevelopmentJobState.TIMED_OUT, null, DevelopmentFailure(DevelopmentFailureKind.MODEL_FAILURE, "late")))
        assertEquals(DevelopmentUiPhase.FAILED, c.state.phase)
        assertEquals("Zeitlimit erreicht.", c.state.status)
        assertTrue(c.state.canEdit)
    }

    @Test
    fun pendingSourceReadBlocksStartAndImmediatelyRevokesStaleProposal() {
        val c = controller()
        assertNotNull(c.start(true))
        c.onOutcome(completed())
        assertNotNull(c.exportText())

        assertTrue(c.beginSourceImport())
        assertTrue(c.state.sourceImportPending)
        assertFalse(c.state.canStart)
        assertFalse(c.state.canExport)
        assertNull(c.exportText())
        assertNull(c.start(true))
        assertFalse(c.beginSourceImport())
        assertTrue(c.addSource("app/NewFile.kt", "fun extra() = 3\n"))

        c.finishSourceImport()
        assertFalse(c.state.sourceImportPending)
        assertTrue(c.state.canStart)
        assertEquals(2, c.state.sources.size)
        assertNull(c.exportText())
        assertNotNull(c.start(true))
    }

    @Test
    fun failedOrCancelledSourcePickerLeavesExistingSelectionUsable() {
        val c = controller()
        assertTrue(c.beginSourceImport())
        c.finishSourceImport() // picker returned no readable files
        assertTrue(c.state.canStart)
        assertEquals(1, c.state.sources.size)
        c.clearSources()
        assertEquals(0, c.state.sources.size)
        assertTrue(c.beginSourceImport())
        c.clearSources() // disallowed while asynchronous selection is in flight
        assertFalse(c.state.canStart)
        c.finishSourceImport()
        assertEquals(0, c.state.sources.size)
    }

    @Test
    fun runningJobCannotBeginAnotherSourceImport() {
        val c = controller()
        assertNotNull(c.start(true))
        assertFalse(c.beginSourceImport())
    }

    @Test
    fun sourceSelectionRejectsForbiddenDuplicateAndExcessFiles() {
        val c = controller()
        assertFalse(c.addSource(".git/config", "x"))
        assertNotNull(c.state.error)
        assertFalse(c.addSource("app/Greeter.kt", "again"))
        assertTrue(c.addSource("a/B.kt", "1"))
        assertTrue(c.addSource("a/C.kt", "1"))
        assertTrue(c.addSource("a/D.kt", "1"))
        assertFalse(c.addSource("a/E.kt", "1"))
        assertEquals(4, c.state.sources.size)
    }
}
