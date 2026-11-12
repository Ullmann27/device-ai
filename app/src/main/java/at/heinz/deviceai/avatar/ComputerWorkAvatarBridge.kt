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
    private val nanos: () -> Long = System::nanoTime
) : CockpitAnimationRenderer, AutoCloseable {
    val reducer = CockpitWorkReducer()
    private var windowId: String? = null
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
    }

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        if (closed || event !is CockpitWorkEvent || !reducer.apply(event, nanos())) return
        val window = windowId ?: return
        val target = event.target?.let { GazeTarget((it.x - .5f) * .7f, -.18f - it.y * .15f) }
            ?: GazeTarget(0f, -.25f)
        when (event.stage) {
            CockpitWorkEvent.Stage.ACCEPTED -> director.windowWorkStarted(window, target)
            CockpitWorkEvent.Stage.FOCUS, CockpitWorkEvent.Stage.DISPATCHED -> {
                director.windowMoved(window, target)
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
                director.clearWorkInput()
                sounds.stop()
            }
            else -> Unit
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        windowId?.let(director::windowWorkEnded)
        director.clearWorkInput()
        sounds.close()
        windowId = null
    }
}
