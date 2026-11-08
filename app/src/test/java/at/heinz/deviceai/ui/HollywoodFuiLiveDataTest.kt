package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CalcStep
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HollywoodFuiLiveDataTest {
    @Test
    fun documentDoesNotInventPageCountAndClampsOnlyRealCounts() {
        val unknown = HollywoodFuiLiveData.document(
            " Vertrag.pdf\u0007 ",
            " application/pdf ",
            "Bitte prüfen"
        )
        assertEquals("Vertrag.pdf", unknown.documentName)
        assertNull(unknown.pageCount)

        val known = HollywoodFuiLiveData.document(
            "x.pdf", "application/pdf", null, pageCount = 9
        )
        assertEquals(4, known.pageCount)
    }

    @Test
    fun timelineUsesRealDistinctKeysAndKeepsActualStates() {
        val timeline = HollywoodFuiLiveData.Timeline.from(
            listOf(
                ProcessStep("a", "Analyse", StepState.ACTIVE),
                ProcessStep("a", "Analyse neuer Stand", StepState.DONE),
                ProcessStep("b", "Freigabe", StepState.WAITING)
            )
        )
        assertEquals(2, timeline.lines.size)
        assertEquals("Analyse", timeline.lines[0].label)
        assertEquals(StepState.ACTIVE, timeline.lines[0].state)
        assertEquals(StepState.WAITING, timeline.lines[1].state)
    }

    @Test
    fun calculationDropsNonfiniteStepsAndEvidenceClampsScore() {
        val calc = HollywoodFuiLiveData.calculation(
            " score ",
            listOf(CalcStep("gut", 2.0), CalcStep("nan", Double.NaN)),
            2.0,
            "pt"
        )
        assertEquals(1, calc.steps.size)
        assertEquals("gut", calc.steps.single().label)

        val evidence = HollywoodFuiLiveData.evidence(12, 2.5f, " Recall ")
        assertEquals(12, evidence.count)
        assertEquals(1f, evidence.topScore!!, 0f)
        assertEquals("Recall", evidence.sourceLabel)
        assertTrue(evidence.count > 8) // Renderer, not data, owns the display cap.
    }
}
