package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class WorkingTaskContextTest {
    private val session = "omega-development"
    private val alpha = WorkingTaskReference("alpha", "Licht Alpha", "source-a", setOf("Alphaversion"))
    private val beta = WorkingTaskReference("beta", "Licht Beta", "source-b", setOf("Betaversion"))
    private fun seed(revision: Long = 1) = WorkingTaskSeed("animation-task", revision,
        "Animation verbessern", "owner-goal", listOf(
            WorkingTaskConstraint("avatar", "Originalkopf beibehalten", "owner-avatar"),
            WorkingTaskConstraint("scope", "Nur den Lichtübergang ändern", "owner-scope")
        ), listOf(alpha, beta), "beta")
    private fun context() = LocalConversationContext(true).apply { assertTrue(bindWorkingTask(session, seed())) }
    private fun plan(c: LocalConversationContext, text: String) =
        (c.resolveWorkingTask(session, text) as WorkingTaskDecision.Resolved).plan

    @Test fun defaultOffKeepsExistingBehavior() {
        val c = LocalConversationContext()
        assertFalse(c.bindWorkingTask(session, seed()))
        assertEquals(WorkingTaskDecision.Disabled, c.resolveWorkingTask(session, "die andere"))
        assertNull(c.workingTaskSnapshot(session))
    }

    @Test fun ownerScenarioResolvesPresentedAlternative() {
        val c = context()
        val text = "Du, schau mal die Animation von vorher. Die ist noch nicht so, wie ich das haben wollte. " +
            "Die andere war besser. Weißt eh welche. Schau dir das nochmal an und mach es besser. " +
            "Aber lass die restlichen Sachen so, wie wir es besprochen haben."
        assertNull(c.inspectRequest(session, text))
        val p = plan(c, text)
        assertEquals("alpha", p.reference.id)
        assertEquals(seed().constraints, p.constraints)
        assertEquals(text, p.originalRequest)
        assertEquals(3, p.steps.size)
        assertTrue(c.render(session).contains("Originalkopf beibehalten"))
        assertTrue(c.render(session).contains("keine Werkzeugfreigabe"))
    }

    @Test fun targetWithoutPresentedFocusIsAmbiguous() {
        val c = LocalConversationContext(true)
        c.bindWorkingTask(session, seed().copy(selectedReferenceId = null))
        assertTrue(c.resolveWorkingTask(session, "Die andere war besser.") is WorkingTaskDecision.Clarify)
    }

    @Test fun moreThanFourTurnsPreserveBoundedCommitment() {
        val c = context()
        repeat(40) { c.record(session, "Nebenthema Nummer $it", "Antwort $it") }
        assertEquals(4, c.sessionEvidence(session)!!.size)
        assertEquals(seed().constraints, plan(c, "Die Animation davor verbessern.").constraints)
    }

    @Test fun namedCorrectionKeepsConstraints() {
        val c = context()
        assertNull(c.inspectRequest(session, "Nein, ich meinte Licht Alpha."))
        assertEquals("alpha", plan(c, "Nein, ich meinte Licht Alpha.").reference.id)
        assertEquals(seed().constraints, c.workingTaskSnapshot(session)!!.seed.constraints)
    }

    @Test fun topicDetourSuspendsWithoutDroppingCommitment() {
        val c = context()
        val before = plan(c, "Licht Alpha verbessern")
        c.inspectRequest(session, "Neues Thema")
        assertFalse(c.isWorkingTaskPlanCurrent(session, before))
        assertTrue(c.workingTaskSnapshot(session)!!.suspended)
        assertEquals(seed().constraints, c.workingTaskSnapshot(session)!!.seed.constraints)
        assertTrue(c.resolveWorkingTask(session, "Die andere") is WorkingTaskDecision.Clarify)
        assertEquals("alpha", plan(c, "Zurück zur Animation Licht Alpha.").reference.id)
    }

    @Test fun stopInvalidatesCurrentPlan() {
        val c = context()
        val before = plan(c, "Licht Alpha verbessern")
        c.inspectRequest(session, "Stopp!")
        assertFalse(c.isWorkingTaskPlanCurrent(session, before))
        assertTrue(c.workingTaskSnapshot(session)!!.suspended)
        assertEquals(WorkingTaskDecision.NoMatch, c.resolveWorkingTask(session, "Ja"))
    }

    @Test fun unrelatedRequestDoesNotLeakTaskIntoPrompt() {
        val c = context()
        plan(c, "Licht Alpha verbessern")
        assertEquals(WorkingTaskDecision.NoMatch, c.resolveWorkingTask(session, "Wie warm ist Wien?"))
        assertEquals("", c.render(session))
        assertEquals(seed().goal, c.workingTaskSnapshot(session)!!.seed.goal)
    }

    @Test fun repeatedInspectionDoesNotToggleOther() {
        val c = context()
        c.inspectRequest(session, "Die andere war besser.")
        val first = plan(c, "Die andere war besser.")
        repeat(4) { assertEquals(first, plan(c, "Die andere war besser.")) }
        assertEquals("beta", c.workingTaskSnapshot(session)!!.selectedReferenceId)
    }

    @Test fun genericAssentCannotResolveOrRetainPlan() {
        for (text in listOf("Ja", "Okay!", "passt", "genau", "mach weiter")) {
            val c = context()
            val before = plan(c, "Licht Alpha verbessern")
            assertEquals(WorkingTaskDecision.NoMatch, c.resolveWorkingTask(session, text))
            assertFalse(c.isWorkingTaskPlanCurrent(session, before))
        }
    }

    @Test fun rejectionDoesNotReviveSelectedPronoun() {
        val c = context()
        assertTrue(c.resolveWorkingTask(session, "Nicht die zweite.") is WorkingTaskDecision.Clarify)
        assertTrue(c.resolveWorkingTask(session, "Mach sie besser.") is WorkingTaskDecision.Clarify)
        assertEquals("alpha", plan(c, "Ich meinte Licht Alpha.").reference.id)
    }

    @Test fun contrastResolvesOnlyDistinctPositiveTarget() {
        val c = context()
        assertEquals("alpha", plan(c, "Nicht die zweite, sondern die erste.").reference.id)
        assertEquals("alpha", plan(c, "Nicht Licht Beta, sondern Licht Alpha.").reference.id)
        assertTrue(c.resolveWorkingTask(session, "Nicht die zweite, sondern die zweite.") is WorkingTaskDecision.Clarify)
    }

    @Test fun predicateNegationIsNotReferenceRejection() {
        val c = context()
        assertEquals("beta", plan(c, "Warum startet die zweite nicht?").reference.id)
    }

    @Test fun namedNegationDoesNotSelectRejectedVersion() {
        for (request in listOf("Ich will nicht Licht Beta", "Licht Beta ist es nicht", "Ich meinte Licht Beta nicht")) {
            val c = context()
            assertTrue(request, c.resolveWorkingTask(session, request) is WorkingTaskDecision.Clarify)
        }
    }

    @Test fun aliasCollisionAsksInsteadOfOrdering() {
        val c = LocalConversationContext(true)
        c.bindWorkingTask(session, seed().copy(references = listOf(
            alpha.copy(aliases = setOf("Licht")), beta.copy(aliases = setOf("Licht")))))
        assertTrue(c.resolveWorkingTask(session, "Licht verbessern") is WorkingTaskDecision.Clarify)
    }

    @Test fun revokedSourceCannotProducePlan() {
        val c = context()
        val previous = plan(c, "Licht Alpha verbessern")
        assertTrue(c.invalidateWorkingTaskReference(session, "alpha"))
        assertFalse(c.isWorkingTaskPlanCurrent(session, previous))
        assertTrue(c.resolveWorkingTask(session, "Licht Alpha verbessern") is WorkingTaskDecision.Clarify)
    }

    @Test fun sourceAndConstraintRevisionsInvalidateOldPlan() {
        val c = context()
        val old = plan(c, "Licht Alpha verbessern")
        c.bindWorkingTask(session, seed(2).copy(references = listOf(alpha.copy(sourceRevision = "source-a2"), beta)))
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
        val next = plan(c, "Licht Alpha verbessern")
        assertNotEquals(old.bindingSha256, next.bindingSha256)
        assertTrue(c.isWorkingTaskPlanCurrent(session, next))
    }

    @Test fun conflictingSameRevisionBlocksWithoutErasingGoal() {
        val c = context()
        val old = plan(c, "Licht Alpha verbessern")
        assertFalse(c.bindWorkingTask(session, seed().copy(goal = "Alles austauschen")))
        assertEquals(seed().goal, c.workingTaskSnapshot(session)!!.seed.goal)
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
        assertTrue(c.resolveWorkingTask(session, "Licht Alpha verbessern") is WorkingTaskDecision.Clarify)
        assertFalse(c.bindWorkingTask(session, seed()))
        assertTrue(c.bindWorkingTask(session, seed(2)))
        assertEquals("alpha", plan(c, "Licht Alpha verbessern").reference.id)
    }

    @Test fun duplicateConstraintKeysFailClosed() {
        val c = context()
        val conflicting = seed().constraints + WorkingTaskConstraint("avatar", "Kopf ersetzen", "contradiction")
        assertFalse(c.bindWorkingTask(session, seed(2).copy(constraints = conflicting)))
        assertTrue(c.resolveWorkingTask(session, "Licht Alpha verbessern") is WorkingTaskDecision.Clarify)
    }

    @Test fun staleRegistrationDoesNotReplaceNewerTask() {
        val c = context()
        assertTrue(c.bindWorkingTask(session, seed(2)))
        assertFalse(c.bindWorkingTask(session, seed(1)))
        assertEquals(2L, c.workingTaskSnapshot(session)!!.seed.revision)
    }

    @Test fun sessionResetDestroysTaskAndPlan() {
        val c = context()
        val old = plan(c, "Licht Alpha verbessern")
        c.record("another", "Neue Unterhaltung", "Antwort")
        assertNull(c.workingTaskSnapshot(session))
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
        assertEquals(WorkingTaskDecision.NoMatch, c.resolveWorkingTask(session, "Licht Alpha verbessern"))
    }

    @Test fun inputBudgetCannotClipNegation() {
        val c = context()
        assertTrue(c.resolveWorkingTask(session, "Licht Alpha " + "x".repeat(512) + " nicht") is WorkingTaskDecision.Clarify)
        assertTrue(c.resolveWorkingTask(session, "Mach sie besser") is WorkingTaskDecision.Clarify)
    }

    @Test fun mutableInputListsCannotChangeBoundState() {
        val references = mutableListOf(alpha, beta)
        val constraints = seed().constraints.toMutableList()
        val c = LocalConversationContext(true)
        assertTrue(c.bindWorkingTask(session, seed().copy(references = references, constraints = constraints)))
        references.clear()
        constraints.clear()
        assertEquals("alpha", plan(c, "Die andere war besser").reference.id)
        assertEquals(2, c.workingTaskSnapshot(session)!!.seed.constraints.size)
    }

    @Test fun planTamperingIsNotCurrent() {
        val c = context()
        val p = plan(c, "Licht Alpha verbessern")
        assertFalse(c.isWorkingTaskPlanCurrent(session, p.copy(goal = "Alles verändern")))
        assertFalse(c.isWorkingTaskPlanCurrent(session, p.copy(reference = beta)))
    }

    @Test fun tinyPromptBudgetReturnsNoPartialCommitment() {
        val c = context()
        plan(c, "Licht Alpha verbessern")
        assertEquals("", c.render(session, maxBytes = 20))
    }

    @Test fun longInspectedCorrectionInvalidatesPreviousPlan() {
        val c = context()
        val old = plan(c, "Licht Alpha verbessern")
        assertEquals(ContextDirectiveKind.CLARIFY_SCOPE,
            c.inspectRequest(session, "Ich meinte " + "x".repeat(520))!!.kind)
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
    }

    @Test fun negativeReplacementNeverSelectsReplacement() {
        val c = context()
        assertTrue(c.resolveWorkingTask(session, "Nicht Licht Alpha, sondern nicht Licht Beta") is WorkingTaskDecision.Clarify)
    }

    @Test fun trailingSelectorRejectionInvalidatesFocus() {
        for (text in listOf("Die zweite nicht", "Die andere nicht", "Die erste ist es nicht")) {
            val c = context()
            assertTrue(c.resolveWorkingTask(session, text) is WorkingTaskDecision.Clarify)
            assertTrue(c.resolveWorkingTask(session, "Mach sie besser") is WorkingTaskDecision.Clarify)
        }
    }

    @Test fun conflictingSelectorsAndUnknownVersionsClarify() {
        for (text in listOf("Licht Alpha, die zweite", "Licht Alpha oder Licht Beta", "Animation Gamma verbessern")) {
            val c = context()
            assertTrue(text, c.resolveWorkingTask(session, text) is WorkingTaskDecision.Clarify)
        }
    }

    @Test fun oldPlanCannotReturnAfterClearOrRebinding() {
        val c = context()
        val old = plan(c, "Licht Alpha verbessern")
        c.clear()
        c.bindWorkingTask(session, seed())
        val next = plan(c, "Licht Alpha verbessern")
        assertNotEquals(old.bindingSha256, next.bindingSha256)
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
        c.bindWorkingTask(session, seed())
        val again = plan(c, "Licht Alpha verbessern")
        assertNotEquals(next.bindingSha256, again.bindingSha256)
        assertFalse(c.isWorkingTaskPlanCurrent(session, next))
    }

    @Test fun oldPlanCannotTransferAcrossContextInstances() {
        val first = context()
        val old = plan(first, "Licht Alpha verbessern")
        val second = context()
        val fresh = plan(second, "Licht Alpha verbessern")
        assertNotEquals(old.bindingSha256, fresh.bindingSha256)
        assertFalse(second.isWorkingTaskPlanCurrent(session, old))
    }

    @Test fun resumedSituationProjectsWorkingGoal() {
        val c = context()
        c.inspectRequest(session, "Neues Thema")
        plan(c, "Zurück zur Animation Licht Alpha")
        assertEquals(seed().goal, c.situationSnapshot(session)!!.currentGoal)
        assertEquals(seed().taskId, c.situationSnapshot(session)!!.currentTask)
    }

    @Test fun boundsRejectInsteadOfTruncating() {
        val invalid = listOf(
            seed().copy(references = (0..8).map { alpha.copy(id = "reference-$it") }),
            seed().copy(goal = "x".repeat(241)),
            seed().copy(goalSourceId = "unsafe\nsource"),
            seed().copy(references = listOf(alpha, alpha)),
            seed().copy(selectedReferenceId = "missing")
        )
        invalid.forEach { assertFalse(LocalConversationContext(true).bindWorkingTask(session, it)) }
        val c = context()
        assertTrue(c.resolveWorkingTask(session, " ".repeat(520) + "Licht Alpha") is WorkingTaskDecision.Clarify)
    }

    @Test fun exposedPoliteRejectionIsNotPredicateNegation() {
        for (request in listOf("Licht Beta bitte nicht.", "Bitte Licht Beta nicht.", "Ich meine Licht Beta bitte nicht.")) {
            val c = context()
            assertTrue(request, c.resolveWorkingTask(session, request) is WorkingTaskDecision.Clarify)
        }
        val c = context()
        assertEquals("beta", plan(c, "Warum startet Licht Beta nicht?").reference.id)
        assertEquals("beta", plan(c, "Startet Licht Beta nicht?").reference.id)
    }

    @Test fun exposedConstraintCorrectionNeedsTrustedRebinding() {
        val c = context()
        val withVoice = seed(2).copy(constraints = seed().constraints +
            WorkingTaskConstraint("voice", "Stimme unverändert lassen", "owner-voice"))
        assertTrue(c.bindWorkingTask(session, withVoice))
        val old = plan(c, "Licht Alpha verbessern")
        val change = "Korrektur: Die Stimme darf jetzt heller werden. Avatar bleibt unverändert."
        assertTrue(c.resolveWorkingTask(session, change) is WorkingTaskDecision.Clarify)
        assertFalse(c.isWorkingTaskPlanCurrent(session, old))
        assertEquals(withVoice.constraints, c.workingTaskSnapshot(session)!!.seed.constraints)
        assertTrue(c.resolveWorkingTask(session, "Licht Alpha verbessern") is WorkingTaskDecision.Clarify)
        assertTrue(c.bindWorkingTask(session, withVoice.copy(revision = 3,
            constraints = withVoice.constraints.map {
                if (it.key == "voice") it.copy(value = "Stimme darf heller werden", sourceId = "owner-voice-correction") else it
            })))
        assertEquals("alpha", plan(c, "Licht Alpha verbessern").reference.id)
    }
}
