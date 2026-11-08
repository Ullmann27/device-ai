package at.heinz.deviceai

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.cognitive.CockpitConversationPresentation
import at.heinz.deviceai.cognitive.LocalChatPromptBudget
import at.heinz.deviceai.ui.HollywoodFuiConstruct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Argon4CockpitAndBudgetRegressionTest {
    private fun budget(task: String) = LocalChatPromptBudget.limit(
        task = task,
        hasMemories = false,
        hasConversationContext = false,
        hasGroundedEvidence = false,
        hasObservations = false,
        hasReflection = false
    )

    @Test
    fun briefFirstTurnAndFullComplexOrLongTurnUseTheirCorrectBudgets() {
        assertEquals(
            LocalChatPromptBudget.BRIEF_FIRST_TURN_CHARACTERS,
            budget("Was kannst du?")
        )
        assertEquals(
            LocalChatPromptBudget.FULL_CHARACTERS,
            budget("Prüfe die installierte App und zeige mir ihren Status.")
        )
        assertEquals(
            LocalChatPromptBudget.BRIEF_FIRST_TURN_CHARACTERS,
            budget("a".repeat(240))
        )
        assertEquals(
            LocalChatPromptBudget.FULL_CHARACTERS,
            budget("a".repeat(241))
        )
    }

    @Test
    fun handsFreePinsHomeAndLiveNative3dCannotBeDetachedForReparent() {
        assertFalse(
            CockpitConversationPresentation.showChat(
                explicitChatSelected = false,
                conversationActive = true,
                keyboardCompact = true,
                handsFreeActive = true,
                pinHomeCockpit = true
            )
        )
        assertFalse(
            CockpitConversationPresentation.mayReparentHologram(
                handsFreeActive = true,
                native3dEnabled = false
            )
        )
        assertFalse(
            CockpitConversationPresentation.mayReparentHologram(
                handsFreeActive = false,
                native3dEnabled = true
            )
        )
        assertTrue(
            CockpitConversationPresentation.mayReparentHologram(
                handsFreeActive = false,
                native3dEnabled = false
            )
        )
    }

    @Test
    fun acknowledgementUsesTimelineOnlyAndNeverEmitsDuplicateThinkAction() {
        val events = mutableListOf<CockpitAnimationEvent>()
        val bus = CockpitAnimationBus().also { it.subscribe { event -> events += event } }
        CockpitAnimationTriggers(bus, clock = { 1_000L })
            .commandAcknowledged("turn-1", "Auftrag")

        assertEquals(1, events.filterIsInstance<CockpitAnimationEvent.CommandFlyIn>().size)
        assertEquals(1, events.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>().size)
        assertTrue(events.filterIsInstance<CockpitAnimationEvent.ActionStarted>().isEmpty())
    }

    @Test
    fun fuiMaterializationUsesExactThreePhaseContract() {
        val start = HollywoodFuiConstruct.frame(0L)
        assertEquals(0f, start.brackets, 0f)
        assertEquals(0f, start.grid, 0f)
        assertEquals(0f, start.data, 0f)

        val brackets = HollywoodFuiConstruct.frame(120L)
        assertEquals(1f, brackets.brackets, 0f)
        assertEquals(0f, brackets.grid, 0f)

        val frame = HollywoodFuiConstruct.frame(250L)
        assertEquals(1f, frame.grid, 0f)
        assertEquals(0f, frame.data, 0f)
        assertTrue(frame.scanEnabled)

        val data = HollywoodFuiConstruct.frame(450L)
        assertEquals(1f, data.data, 0f)
    }
}
