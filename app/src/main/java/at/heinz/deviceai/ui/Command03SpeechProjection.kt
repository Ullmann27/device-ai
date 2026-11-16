package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AudioLevelSource
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarPoseReducer

internal data class Command03SpeechPacket(
    val sessionId: String, val generation: Long, val sequence: Long, val active: Boolean,
    val utteranceId: String? = null, val viseme: String = "SIL", val weight: Float = 0f,
    val jawOpen: Float = 0f, val durationMs: Long = 100,
    val source: AudioLevelSource? = null
)

/** Consumes the existing Director's admitted events and reference reducer; no phoneme or audio synthesis. */
internal class Command03SpeechProjection(private val emit: (Command03SpeechPacket) -> Unit) {
    private val pose = AvatarPoseReducer()
    private var session = ""
    private var utterance: String? = null
    private var generation = 0L
    private var sequence = 0L
    private var source: AudioLevelSource? = null
    private var lastAudioAt = Long.MIN_VALUE
    private val retired = linkedSetOf<String>()

    fun accept(sessionId: String, currentUtterance: String?, event: AvatarEvent) {
        if (!Command03BridgeContract.validSession(sessionId)) return
        if (session != sessionId) { clear(); session = sessionId }
        val id = when (event) {
            is AvatarEvent.SpeechStarted -> event.utteranceId
            is AvatarEvent.VisemeCue -> event.utteranceId
            is AvatarEvent.AudioLevel -> event.utteranceId
            is AvatarEvent.SpeechEnded -> event.utteranceId
            else -> return
        }
        if (id != currentUtterance || id in retired) return
        if (event is AvatarEvent.SpeechStarted) {
            if (utterance == id) return
            clear()
            generation++
            sequence = 0
            utterance = id
        } else if (id != utterance) return
        if (event is AvatarEvent.AudioLevel) {
            if (!event.level.isFinite() || event.atMillis <= lastAudioAt) return
            source = event.source
            lastAudioAt = event.atMillis
        }
        pose.apply(event)
        if (event is AvatarEvent.SpeechEnded) {
            retired.add(id)
            while (retired.size > 64) retired.remove(retired.first())
            clear()
        }
    }

    fun sample(sessionId: String, currentUtterance: String?, nowMillis: Long, visible: Boolean) {
        val id = utterance ?: return
        if (!visible || session != sessionId || id != currentUtterance) { clear(); return }
        val frame = pose.pose(nowMillis)
        emit(Command03SpeechPacket(session, generation, ++sequence, true, id, frame.viseme.name,
            frame.visemeWeight, frame.jawOpen, source = source))
    }

    /** Reparent may reattach the Director's current utterance; ended utterances remain retired. */
    fun clear() {
        val previous = utterance
        utterance = null
        source = null
        lastAudioAt = Long.MIN_VALUE
        pose.clearSpeech()
        if (previous != null) emit(Command03SpeechPacket(session, generation, ++sequence, false, previous))
    }
}
