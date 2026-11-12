package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.LiveFrameBinding

/** Sequence follows capture, not feed identity. */
internal class CockpitPresentationSequence {
    private var binding: LiveFrameBinding? = null
    private var sequence = 0L
    private var occurredAtNanos = -1L

    fun accept(event: CockpitWorkEvent, current: LiveFrameBinding, nowNanos: Long): Boolean {
        if (event.taskId != current.taskId || event.sessionId != current.captureSession ||
            event.occurredAtNanos > nowNanos) return false
        if (binding != current) { reset(); binding = current }
        if (event.sequence <= sequence || event.occurredAtNanos < occurredAtNanos) return false
        sequence = event.sequence
        occurredAtNanos = event.occurredAtNanos
        return true
    }

    fun reset() { binding = null; sequence = 0L; occurredAtNanos = -1L }
}
