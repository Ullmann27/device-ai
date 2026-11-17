package at.heinz.deviceai.avatar3d
class CandidateStage(graph: ReplayGraph, manager: RecordingTransformManager, cacheTransforms: Boolean = true) {
    private val engine = RecordingEngine(manager)
    private val roomTransform = graph.id("cockpit-room")
    private val sideMonitorTransform = graph.id("cockpit-side-monitors")
    private val monitorTransform = graph.id("cockpit-monitor")
    private val orbTransform = graph.id("continuum-orb")
    private val orbitTransforms = (0..4).map { graph.id("orb-orbit-$it").let { id -> id to graph.rest.getValue(id) } }
    private val transform = FloatArray(16)
    private val orbitCombined = FloatArray(16)
    private val transformUpdates = CockpitTransformUpdates(engine.transformManager::setTransform, cacheTransforms)
    fun update(frame: CockpitStageFrame, orbBounds: ReplayBounds, phase: Float, motion: Boolean) {
        require(frame.valid())
        CockpitWorldTransforms.room(frame, transform)
        transformUpdates.setTransform(roomTransform, transform)
        if (frame.monitorWidth / frame.width > .70f) {
            transform[0] = 0f
            transform[5] = 0f
            transform[10] = 0f
        }
        transformUpdates.setTransform(sideMonitorTransform, transform)
        transformUpdates.setTransform(monitorTransform, frame.monitorTransform())
        CockpitWorldTransforms.orb(frame, orbBounds.left, orbBounds.top, orbBounds.right, orbBounds.bottom, transform)
        transformUpdates.setTransform(orbTransform, transform)
        orbitTransforms.forEachIndexed { index, (instance, rest) ->
            CockpitWorldTransforms.ringTransform(rest, index, phase, motion, orbitCombined)
            transformUpdates.setTransform(instance, orbitCombined)
        }
    }
}
