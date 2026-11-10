package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.CinemaWindowLightState
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.WindowProjectedMotion

/**
 * Maps Bot 04's projected window samples to the existing [AvatarDirector] glance
 * APIs so the eyes follow the moving hologram instead of jumping to its endpoint.
 * Does not own a clock and does not open a second renderer.
 */
class CinemaWindowGazeBridge(private val director: AvatarDirector) : CockpitAnimationRenderer {
    private val openWindows = linkedMapOf<String, String>()

    override fun render(event: CockpitAnimationEvent) {
        when (event) {
            is WindowProjectedMotion -> onMotion(event)
            is CockpitAnimationEvent.TurnEnded -> closeTurn(event.turnId)
            else -> Unit
        }
    }

    private fun onMotion(event: WindowProjectedMotion) {
        if (event.windowId.isBlank() || !event.gazeX.isFinite() || !event.gazeY.isFinite()) return
        if (event.phase == WindowProjectedMotion.Phase.CLOSED) {
            closeWindow(event.windowId)
            return
        }
        val target = GazeTarget(event.gazeX, event.gazeY)
        if (openWindows.containsKey(event.windowId)) {
            director.windowMoved(event.windowId, target)
        } else if (!busyForGlance() && director.windowOpened(event.windowId, target)) {
            openWindows[event.windowId] = event.turnId
        } else if (!busyForGlance()) {
            director.windowMoved(event.windowId, target)
        }
        CinemaWindowLightState.apply(event.accentArgb, event.gazeX, open = true)
    }

    private fun busyForGlance(): Boolean {
        val state = director.state
        return state == AvatarState.LISTENING || state == AvatarState.SPEAKING
    }

    private fun closeWindow(windowId: String) {
        openWindows.remove(windowId)
        director.windowClosed(windowId)
        if (openWindows.isEmpty()) CinemaWindowLightState.reset()
    }

    private fun closeTurn(turnId: String) {
        val closing = openWindows.filterValues { it == turnId }.keys.toList()
        for (id in closing) closeWindow(id)
    }
}
