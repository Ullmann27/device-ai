package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.GazeTarget

/** Presentation state, never execution authority. */
internal class ToolWorkSceneLease {
    data class Key(val session: String, val scope: String, val generation: Long, val callId: String)
    var active: Key? = null
        private set
    var running: Boolean = false
        private set
    private var latestStart = Long.MIN_VALUE
    private val finished = linkedSetOf<String>()
    private var historyScope: Triple<String, String, Long>? = null

    fun offer(key: Key, session: String, scope: String?, generation: Long,
              foreground: Boolean, startedAt: Long): Boolean {
        if (!eligible(key, session, scope, generation, foreground)) return false
        val scopeKey = Triple(key.session, key.scope, key.generation)
        if (historyScope != scopeKey) {
            finished.clear()
            latestStart = Long.MIN_VALUE
            historyScope = scopeKey
        }
        if (key.callId in finished ||
            active == key || startedAt < latestStart) return false
        active?.let { finished += it.callId }
        while (finished.size > 64) finished.remove(finished.first())
        active = key
        running = true
        latestStart = startedAt
        return true
    }

    fun current(key: Key, session: String, scope: String?, generation: Long,
                foreground: Boolean): Boolean = active == key &&
        eligible(key, session, scope, generation, foreground)

    fun ended(key: Key): Boolean {
        if (active != key) return false
        running = false
        finished += key.callId
        return true
    }

    fun closed(key: Key) {
        if (active != key) return
        finished += key.callId
        active = null
        running = false
    }

    fun clear() {
        active = null
        running = false
        finished.clear()
        historyScope = null
        latestStart = Long.MIN_VALUE
    }

    private fun eligible(key: Key, session: String, scope: String?, generation: Long,
                         foreground: Boolean): Boolean = foreground && key.session == session &&
        key.scope == scope && key.generation == generation

    companion object {
        fun workingGaze(target: GazeTarget): GazeTarget = GazeTarget(
            (target.x * 1.70f).coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_X, AvatarPoseReducer.MAX_DIRECTED_GAZE_X),
            (target.y * 1.25f).coerceIn(-AvatarPoseReducer.MAX_DIRECTED_GAZE_Y, AvatarPoseReducer.MAX_DIRECTED_GAZE_Y))
    }
}
