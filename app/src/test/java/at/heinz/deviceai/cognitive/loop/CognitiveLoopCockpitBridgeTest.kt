package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveLoopCockpitBridgeTest {
    private val bus = CockpitAnimationBus()
    private val seen = mutableListOf<CockpitAnimationEvent>().also { list -> bus.subscribe { list += it } }
    private val bridge = CognitiveLoopCockpitBridge(bus)

    @Test fun everyLoopEventIsOnTheBusAsExtension() {
        val e = CognitiveLoopEvent.GoalSet("t", 5, 0, "Ziel")
        bridge.onEvent(e)
        assertTrue(seen.contains(CognitiveLoopAnimationEvent(e)))
        assertEquals(CognitiveLoopAnimationEvent.KIND, (seen.first { it is CognitiveLoopAnimationEvent } as CockpitAnimationEvent.Extension).kind)
    }

    @Test fun toolRequestStartsTheToolActionAndTimelineFollowsTheSteps() {
        bridge.onEvent(CognitiveLoopEvent.GoalSet("t", 1, 0, "Ziel"))
        bridge.onEvent(CognitiveLoopEvent.PlanMade("t", 2, 1, listOf(PlannedStep("Akku lesen", "get_battery_info"))))
        bridge.onEvent(CognitiveLoopEvent.ToolRequested("t", 3, 1, "get_battery_info"))
        assertTrue(CockpitAnimationEvent.ActionStarted("t", 3, CockpitAction.TOOL, "get_battery_info") in seen)
        val timeline = seen.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>().last()
        assertEquals("t", timeline.turnId)
        assertEquals(StepState.ACTIVE, timeline.steps.last().state)
        assertTrue(timeline.steps.first().state == StepState.DONE)
    }

    @Test fun abortIsCalmNeverAnAlarm() {
        bridge.onEvent(CognitiveLoopEvent.GoalSet("t", 1, 0, "Ziel"))
        bridge.onEvent(CognitiveLoopEvent.Aborted("t", 2, 1, AbortReason.ERROR, "Das hat nicht geklappt."))
        assertTrue(seen.none { it is CockpitAnimationEvent.AlarmRaised })
        val last = seen.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>().last().steps.last()
        assertEquals(StepState.FAILED, last.state)
        assertEquals("Das hat nicht geklappt.", last.label)
    }

    @Test fun ownerQuestionShowsAWaitingStepAndFinishMarksAllDone() {
        bridge.onEvent(CognitiveLoopEvent.GoalSet("t", 1, 0, "Ziel"))
        bridge.onEvent(CognitiveLoopEvent.AskOwner("t", 2, 1, "Darf ich?", listOf(LoopOption("ja", "Ja"))))
        assertEquals(StepState.WAITING, seen.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>().last().steps.last().state)
        bridge.onEvent(CognitiveLoopEvent.Finished("t", 3, 2, "fertig"))
        assertTrue(seen.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>().last().steps.all { it.state == StepState.DONE })
    }
}
