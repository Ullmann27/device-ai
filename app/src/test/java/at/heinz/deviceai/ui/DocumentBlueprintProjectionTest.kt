package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.DocumentDraftAction
import at.heinz.deviceai.cockpit.DocumentDraftReadyEvent
import at.heinz.deviceai.cockpit.DocumentPipelineStepEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBlueprintProjectionTest {
    @Test
    fun questionStaysAQuestionAndInventNothing() {
        val projection = DocumentBlueprintProjection()
        assertNull(projection.snapshot())
        assertNull(DocumentBlueprintView.visible(null))

        projection.onQuestion(
            CockpitAnimationEvent.DocumentQuestion(
                "document-1", 1L, "Projektübersicht.pdf", "application/pdf",
                "Lies die PDF und erstelle eine Zusammenfassung als Entwurf.", pageCount = 4
            )
        )
        val data = requireNotNull(projection.snapshot())
        val visible = requireNotNull(DocumentBlueprintView.visible(data))
        assertEquals("Lies die PDF und erstelle eine Zusammenfassung als Entwurf.", visible.question)
        assertEquals("1/4", visible.pageLabel)
        assertEquals(listOf("Dokument öffnen"), visible.steps.map { it.label })
        assertEquals(StepState.DONE, visible.steps.single().state)
        assertNull(visible.draftTitle)
        assertNull(visible.draftGoal)
        assertTrue(visible.buttons.isEmpty())
        assertNull(visible.progressPercent)
    }

    @Test
    fun unrelatedMemoryLearningNeverCreatesDocumentProjection() {
        val projection = DocumentBlueprintProjection()
        projection.onLearned(CockpitAnimationEvent.DocumentLearned(
            "memory", 1L, "foreign-archive", "application/pdf", entriesAdded = 7
        ))
        assertTrue(!projection.hasDocument)
        assertNull(projection.snapshot())
    }

    @Test
    fun noPageLabelWithoutMeasuredPageCount() {
        val projection = DocumentBlueprintProjection()
        projection.onQuestion(
            CockpitAnimationEvent.DocumentQuestion(
                "t", 1L, "scan.pdf", "application/pdf", "Was steht darin?"
            )
        )
        val visible = requireNotNull(DocumentBlueprintView.visible(projection.snapshot()))
        assertEquals("Was steht darin?", visible.question)
        assertNull(visible.pageLabel)
        assertTrue(visible.buttons.isEmpty())
        assertNull(visible.draftBody)
    }

    @Test
    fun stepsAdvanceOnlyFromRealPipelineEvents() {
        val projection = DocumentBlueprintProjection()
        projection.onQuestion(
            CockpitAnimationEvent.DocumentQuestion("t", 1L, "a.pdf", "application/pdf", "Prüfen?", 2)
        )
        projection.onTimeline(
            listOf(
                ProcessStep("ack", "Auftrag angenommen", StepState.DONE),
                ProcessStep("work", "Bearbeitung", StepState.ACTIVE)
            )
        )
        assertEquals(
            listOf("Dokument öffnen"),
            DocumentBlueprintView.visible(projection.snapshot())!!.steps.map { it.label }
        )

        projection.onPipelineStep(
            DocumentPipelineStepEvent("t", 2L, DocumentPipelineStepEvent.KEY_CAPTURE, "Inhalte erfassen", StepState.DONE)
        )
        projection.onPipelineStep(
            DocumentPipelineStepEvent("t", 3L, DocumentPipelineStepEvent.KEY_ANALYZE, "Analysieren", StepState.ACTIVE)
        )
        val visible = requireNotNull(DocumentBlueprintView.visible(projection.snapshot()))
        assertEquals(
            listOf("Dokument öffnen", "Inhalte erfassen", "Analysieren"),
            visible.steps.map { it.label }
        )
        assertEquals(StepState.DONE, visible.steps[0].state)
        assertEquals(StepState.DONE, visible.steps[1].state)
        assertEquals(StepState.ACTIVE, visible.steps[2].state)
        assertEquals(listOf(0, 1), DocumentBlueprintView.lightBridgeFrom(visible.steps))
    }

    @Test
    fun draftSectionsAndButtonsComeOnlyFromRealTextAndAuthorisedActions() {
        val projection = DocumentBlueprintProjection()
        projection.onQuestion(
            CockpitAnimationEvent.DocumentQuestion("t", 1L, "a.pdf", "application/pdf", "Zusammenfassen", 3)
        )
        projection.onDraft(
            DocumentDraftReadyEvent(
                "t", 2L,
                "Freier Entwurf ohne Überschriften.",
                coveredChars = 40,
                totalChars = 80,
                actions = emptySet()
            )
        )
        val unstructured = requireNotNull(DocumentBlueprintView.visible(projection.snapshot()))
        assertEquals("Zusammenfassen", unstructured.question)
        assertEquals("Freier Entwurf ohne Überschriften.", unstructured.draftBody)
        assertNull(unstructured.draftGoal)
        assertNull(unstructured.draftApproach)
        assertNull(unstructured.draftOpenItems)
        assertTrue(unstructured.buttons.isEmpty())
        assertEquals(50, unstructured.progressPercent)

        projection.onDraft(
            DocumentDraftReadyEvent(
                "t", 3L,
                "Ziel: Arbeitsabläufe vereinfachen.\nVorgehen: Schritte klar gliedern.\nOffene Punkte: Prioritäten abstimmen.",
                coveredChars = 80,
                totalChars = 80,
                actions = setOf(DocumentDraftAction.CONTINUE, DocumentDraftAction.SAVE)
            )
        )
        val structured = requireNotNull(DocumentBlueprintView.visible(projection.snapshot()))
        assertEquals("Arbeitsabläufe vereinfachen.", structured.draftGoal)
        assertEquals("Schritte klar gliedern.", structured.draftApproach)
        assertEquals("Prioritäten abstimmen.", structured.draftOpenItems)
        assertNull(structured.draftBody)
        assertEquals(listOf("Weiterbearbeiten", "Speichern"), structured.buttons)
        assertEquals(100, structured.progressPercent)
        assertTrue(structured.buttons.none { it == "Entwurf öffnen" })
    }

    @Test
    fun openPdfCannotInheritUnverifiedBackgroundLearningMetadata() {
        val projection = DocumentBlueprintProjection()
        projection.onQuestion(CockpitAnimationEvent.DocumentQuestion(
            "document-episode", 1L, "Meine-Akte.pdf", "application/pdf",
            "Was steht in der Akte?", pageCount = 3
        ))
        val before = requireNotNull(projection.snapshot())
        assertNull(before.entriesAdded)
        assertEquals(1, before.steps.size)

        projection.onLearned(CockpitAnimationEvent.DocumentLearned(
            "memory", 2L, "foreign-ledger-id", "application/pdf", entriesAdded = 8
        ))
        projection.onLearned(CockpitAnimationEvent.DocumentLearned(
            "document-episode", 3L, "Meine-Akte.pdf", "application/pdf", entriesAdded = 99
        ))
        assertEquals(before, projection.snapshot())

        projection.onPipelineStep(DocumentPipelineStepEvent(
            "document-episode", 4L, DocumentPipelineStepEvent.KEY_CAPTURE,
            "Inhalte erfassen", StepState.DONE
        ))
        val after = requireNotNull(projection.snapshot())
        assertNull(after.entriesAdded)
        assertEquals(2, after.steps.size)
    }

}
