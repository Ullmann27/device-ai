package at.heinz.deviceai.cognitive

/** Bridges execution onto the existing bus. */
internal class LiveActionCockpitAdapter(
    private val emitter: CockpitWorkEmitter,
    private val observationEpoch: Long
) : LiveActionEventSink {
    private var observedRevision = 0L
    private var beforeRevision = 0L
    private var pendingStep: String? = null
    private var pendingKind: NodeActionKind? = null

    @Synchronized override fun emit(event: LiveActionEvent) {
        if (event.taskId != emitter.taskId) return
        when (event) {
            is LiveActionEvent.Dispatched -> {
                if (pendingStep != null || event.observationRevision <= 0 ||
                    event.observationRevision < observedRevision) return
                pendingStep = event.stepId; pendingKind = event.kind
                beforeRevision = event.observationRevision
                emitter.emit(CockpitWorkEvent.Stage.DISPATCHED, event.stepId, event.kind.cockpitAction(),
                    event.target?.let { CockpitWorkEvent.Target(it.centerX, it.centerY) },
                    event.observationRevision, event.frameSequence)
            }
            is LiveActionEvent.Observed -> {
                if (event.stepId != pendingStep || event.stamp.taskId != emitter.taskId ||
                    event.stamp.epoch != observationEpoch || event.stamp.revision <= beforeRevision ||
                    event.stamp.revision <= observedRevision) return
                observedRevision = event.stamp.revision
                emitter.emit(CockpitWorkEvent.Stage.OBSERVED, event.stepId,
                    observationRevision = observedRevision, frameSequence = event.stamp.frameSequence)
            }
            is LiveActionEvent.Verified -> {
                if (event.stepId != pendingStep || event.kind != pendingKind) return
                if (event.verdict.confirmed && observedRevision > beforeRevision) {
                    emitter.emit(CockpitWorkEvent.Stage.VERIFIED, event.stepId, event.kind.cockpitAction(),
                        observationRevision = observedRevision)
                } else emitter.emit(CockpitWorkEvent.Stage.PAUSED, event.stepId)
                pendingStep = null; pendingKind = null
            }
        }
    }

    private fun NodeActionKind.cockpitAction() = when (this) {
        NodeActionKind.CLICK, NodeActionKind.SEND -> CockpitWorkEvent.Action.CLICK
        NodeActionKind.SET_TEXT -> CockpitWorkEvent.Action.SET_TEXT
        NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD -> CockpitWorkEvent.Action.SCROLL
    }
}
