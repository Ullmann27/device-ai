package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.CinemaWindowLightState
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.WindowProjectedMotion

/** Projects windows without another clock. */
class CinemaWindowGazeBridge(private val director: AvatarDirector) : CockpitAnimationRenderer {
    private val openWindows = linkedMapOf<String, WindowProjectedMotion>()
    private val gazeWindows = linkedSetOf<String>()
    private val retiredTurns = linkedSetOf<String>()
    private val closedWindows = linkedSetOf<String>()

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        when (event) {
            is WindowProjectedMotion -> onMotion(event)
            is CockpitAnimationEvent.TurnEnded -> closeTurn(event.turnId)
            else -> Unit
        }
    }

    private fun onMotion(event: WindowProjectedMotion) {
        if (event.turnId.isBlank() || event.windowId.isBlank() ||
            !event.gazeX.isFinite() || !event.gazeY.isFinite() || event.turnId in retiredTurns) return
        val current = openWindows[event.windowId]
        if (current != null && event.atMillis < current.atMillis) return
        if (event.phase == WindowProjectedMotion.Phase.CLOSED) {
            if (current?.turnId == event.turnId) closeWindow(current)
            remember(closedWindows, gazeId(event))
            return
        }
        val id = gazeId(event)
        if (id in closedWindows) return
        if (current != null && current.turnId != event.turnId) closeWindow(current)
        val target = GazeTarget(event.gazeX.coerceIn(-1f, 1f), event.gazeY.coerceIn(-1f, 1f))
        if (id in gazeWindows) {
            director.windowMoved(id, target)
        } else if (director.canAdmitWindowGlance() && director.windowOpened(id, target)) {
            gazeWindows += id
        }
        // Lighting also works during speech.
        openWindows.remove(event.windowId)
        openWindows[event.windowId] = event
        if (openWindows.size > MAX_WINDOWS) closeWindow(openWindows.values.first())
        publishLight()
    }

    private fun closeWindow(event: WindowProjectedMotion) {
        openWindows.remove(event.windowId)
        val id = gazeId(event)
        gazeWindows.remove(id)
        director.windowClosed(id)
        remember(closedWindows, id)
        publishLight()
    }

    private fun closeTurn(turnId: String) {
        if (turnId.isBlank()) return
        remember(retiredTurns, turnId)
        val closing = openWindows.values.filter { it.turnId == turnId }
        for (event in closing) closeWindow(event)
    }

    private fun publishLight() {
        val window = openWindows.values.lastOrNull()
        if (window == null) CinemaWindowLightState.reset()
        else CinemaWindowLightState.apply(window.accentArgb, window.gazeX, open = true, gazeY = window.gazeY)
    }

    private fun gazeId(event: WindowProjectedMotion) =
        "cinema:${event.turnId.length}:${event.turnId}:${event.windowId}"

    private fun remember(target: MutableSet<String>, value: String) {
        target += value
        if (target.size > MAX_RETIRED) {
            val first = target.iterator()
            first.next()
            first.remove()
        }
    }

    private companion object {
        const val MAX_WINDOWS = 16
        const val MAX_RETIRED = 64
    }
}
