package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer

/**
 * Jarvis Gedächtnis: maps Kern's memory [CockpitAnimationEvent]s onto the orb inputs of [JarvisSequenceEffects]
 * (Muster E flight, arrival pulse/neuron flash, recall light point, hypothesis marker). No other source; nothing is
 * shown without a real event. Must be called on the UI thread (Kern's MemoryEventMapper flushes there).
 */
class BrainOrbAnimationRenderer(private val effects: JarvisSequenceEffects, private val clock: () -> Long) : CockpitAnimationRenderer {
    override fun render(event: CockpitAnimationEvent) {
        val now = clock()
        when (event) {
            is CockpitAnimationEvent.DocumentLearned -> if (event.entriesAdded > 0) effects.dataFlight(event.entriesAdded, now)
            is CockpitAnimationEvent.RecallHits -> effects.recallLight(event.count, event.topScore, now)
            is CockpitAnimationEvent.MemoryStored -> when (event.status) {
                "HYPOTHETICAL" -> effects.hypothesisMarker(now)
                "CONFIRMED" -> effects.orbPulse(.6f, now)
                else -> Unit // UNCONFIRMED/REVOKED: no orb effect (no false confirmation)
            }
            else -> Unit
        }
    }
}
