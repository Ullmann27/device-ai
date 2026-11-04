package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent

/** Loop event on the cockpit bus for ActionScene / head / orb (renderers that do not know the kind ignore it). */
class CognitiveLoopAnimationEvent(val loopEvent: CognitiveLoopEvent) : CockpitAnimationEvent.Extension(KIND) {
    override val turnId: String get() = loopEvent.turnId
    override val atMillis: Long get() = loopEvent.atMillis
    override fun equals(other: Any?) = other is CognitiveLoopAnimationEvent && other.loopEvent == loopEvent
    override fun hashCode() = loopEvent.hashCode()
    override fun toString() = "CognitiveLoopAnimationEvent($loopEvent)"
    companion object { const val KIND = "cognitive_loop" }
}

/** RED stub. */
class CognitiveLoopCockpitBridge(private val bus: CockpitAnimationBus) : CognitiveLoopListener {
    override fun onEvent(event: CognitiveLoopEvent): Unit = TODO("bridge")
}
