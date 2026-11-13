package at.heinz.deviceai.avatar

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.CockpitWorkReducer

interface ComputerWorkSounds : AutoCloseable {
    fun dispatched(event: CockpitWorkEvent)
    fun stop()
    override fun close() = stop()
}

object SilentComputerWorkSounds : ComputerWorkSounds {
    override fun dispatched(event: CockpitWorkEvent) = Unit
    override fun stop() = Unit
}

/** Execution owns timing, rendering only reacts. */
class ComputerWorkAvatarBridge(
    private val director: AvatarDirector,
    private val sounds: ComputerWorkSounds = SilentComputerWorkSounds,
    private val projectGaze: (CockpitWorkEvent) -> GazeTarget? = { null },
    private val onWorkState: (CockpitWorkReducer.Snapshot) -> Unit = {},
    private val nanos: () -> Long = System::nanoTime
) : CockpitAnimationRenderer, AutoCloseable {
    val reducer = CockpitWorkReducer()
    private var windowId: String? = null
    private var workGazeActive = false
    private var focusEvent: CockpitWorkEvent? = null
    private var lastTarget: GazeTarget? = null
    private var closed = false

    @Synchronized fun bind(taskId: String, sessionId: Long) {
        check(!closed)
        val nextWindow = "computer:$sessionId:$taskId"
        if (windowId == nextWindow) return
        windowId?.let(director::windowWorkEnded)
        director.clearWorkInput()
        sounds.stop()
        reducer.bind(taskId, sessionId)
        windowId = nextWindow
        workGazeActive = false
        focusEvent = null
        lastTarget = null
    }

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        if (closed || event !is CockpitWorkEvent || !reducer.apply(event, nanos())) return
        val window = windowId ?: return
        when (event.stage) {
            CockpitWorkEvent.Stage.ACCEPTED -> focusWork(event)
            CockpitWorkEvent.Stage.FOCUS, CockpitWorkEvent.Stage.DISPATCHED -> {
                focusWork(event)
                if (event.stage == CockpitWorkEvent.Stage.DISPATCHED) {
                    val kind = when (event.action) {
                        CockpitWorkEvent.Action.CLICK -> AvatarWorkInput.CLICK
                        CockpitWorkEvent.Action.SET_TEXT -> AvatarWorkInput.TEXT_SET
                        CockpitWorkEvent.Action.SCROLL -> AvatarWorkInput.SCROLL
                        else -> null
                    }
                    val age = (nanos() - event.occurredAtNanos) / 1_000_000
                    if (kind != null && age in 0..150) {
                        director.workInputPerformed("${event.taskId.length}:${event.taskId}:${event.sessionId}:${event.stepId}", kind, age)
                        sounds.dispatched(event)
                    }
                }
            }
            CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.PAUSED,
            CockpitWorkEvent.Stage.STOPPED, CockpitWorkEvent.Stage.ERROR,
            CockpitWorkEvent.Stage.COMPLETED -> {
                director.windowWorkEnded(window)
                workGazeActive = false
                focusEvent = null
                lastTarget = null
                director.clearWorkInput()
                sounds.stop()
            }
            else -> Unit
        }
        onWorkState(reducer.snapshot(nanos()))
    }

    private fun focusWork(event: CockpitWorkEvent) {
        focusEvent = event
        updateProjection(renewLease = true)
    }

    @Synchronized fun refreshProjection() = updateProjection(renewLease = false)

    private fun updateProjection(renewLease: Boolean) {
        if (closed) return
        val window = windowId ?: return
        val event = focusEvent ?: return
        val age = nanos() - event.occurredAtNanos
        val fresh = age >= 0L && age / 1_000_000 < AvatarPoseReducer.MAX_WINDOW_WORK_MS
        if (!fresh) focusEvent = null
        val target = if (fresh) projectGaze(event)?.takeIf { it.x.isFinite() && it.y.isFinite() } else null
        if (target == null) {
            if (workGazeActive) director.windowWorkEnded(window)
            workGazeActive = false
            lastTarget = null
            return
        }
        if (target == lastTarget && workGazeActive && !renewLease) return
        if (workGazeActive && !renewLease) director.windowMoved(window, target)
        else workGazeActive = director.windowWorkStarted(window, target)
        lastTarget = target
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        windowId?.let(director::windowWorkEnded)
        director.clearWorkInput()
        sounds.close()
        windowId = null
        focusEvent = null
        lastTarget = null
        workGazeActive = false
    }
}
