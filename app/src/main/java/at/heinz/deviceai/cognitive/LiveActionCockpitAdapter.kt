package at.heinz.deviceai.cognitive

/** Bridges execution onto the existing bus. */
internal class LiveActionCockpitAdapter(
    private val emitter: CockpitWorkEmitter,
    private val observationEpoch: Long
) : LiveActionEventSink {
    private var observedRevision = 0L
    private var observedStep: String? = null

    @Synchronized override fun emit(event: LiveActionEvent) {
        if (event.taskId != emitter.taskId) return
        when (event) {
            is LiveActionEvent.Dispatched -> {
                observedRevision = 0; observedStep = null
                emitter.emit(CockpitWorkEvent.Stage.DISPATCHED, event.stepId, event.kind.cockpitAction(),
                    event.target?.let { CockpitWorkEvent.Target(it.centerX, it.centerY) },
                    event.observationRevision, event.frameSequence)
            }
            is LiveActionEvent.Observed -> {
                if (event.stamp.epoch != observationEpoch) return
                observedRevision = event.stamp.revision; observedStep = event.stepId
                emitter.emit(CockpitWorkEvent.Stage.OBSERVED, event.stepId,
                    observationRevision = observedRevision, frameSequence = event.stamp.frameSequence)
            }
            is LiveActionEvent.Verified -> if (event.verdict.confirmed && observedStep == event.stepId) {
                emitter.emit(CockpitWorkEvent.Stage.VERIFIED, event.stepId, event.kind.cockpitAction(),
                    observationRevision = observedRevision)
            } else emitter.emit(CockpitWorkEvent.Stage.PAUSED, event.stepId)
        }
    }

    private fun NodeActionKind.cockpitAction() = when (this) {
        NodeActionKind.CLICK, NodeActionKind.SEND -> CockpitWorkEvent.Action.CLICK
        NodeActionKind.SET_TEXT -> CockpitWorkEvent.Action.SET_TEXT
        NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD -> CockpitWorkEvent.Action.SCROLL
    }
}
