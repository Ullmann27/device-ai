package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import at.heinz.deviceai.cognitive.LiveMonitorState
import at.heinz.deviceai.cognitive.LiveMonitorStatus
import at.heinz.deviceai.cognitive.LiveMonitorSubmission

internal data class Command03FrameAck(val sessionId: String, val generation: Long, val sequence: Long)

/** A transient, bounded encoding of an already admitted Android frame; never a capture source. */
internal data class Command03EncodedFrame(val width: Int, val height: Int, val jpeg: ByteArray) {
    fun valid(): Boolean = width in 1..MAX_EDGE && height in 1..MAX_EDGE &&
        jpeg.size in 4..MAX_JPEG_BYTES && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte() &&
        jpeg[jpeg.lastIndex - 1] == 0xff.toByte() && jpeg.last() == 0xd9.toByte()

    companion object {
        const val MAX_EDGE = 640
        const val MAX_JPEG_BYTES = 131_072
        const val MIN_FRAME_INTERVAL_MS = 200L
    }
}

internal data class Command03ComputerPacket(
    val sessionId: String, val generation: Long, val sequence: Long, val active: Boolean,
    val status: String, val reason: String, val binding: LiveFrameBinding? = null,
    val sourceEpoch: Long? = null, val ageMs: Long? = null, val staleAfterMs: Long? = null,
    val frame: Command03EncodedFrame? = null,
    /**
     * Geometry of exactly this frame, as its producer measured it: the feed's own sequence number, the observation
     * revision it was captured at and the part of the target window it shows (normalized window coordinates). The
     * page needs them to place a real dispatched action on the image it shows; null where the frame carries none.
     */
    val sourceSequence: Long? = null, val observationRevision: Long? = null,
    val viewport: at.heinz.deviceai.cognitive.LiveTargetBounds? = null
) {
    val acknowledgement get() = Command03FrameAck(sessionId, generation, sequence)
}

/**
 * UI-thread owner of a presentation lease on the existing feed. One encoder task and one unacknowledged
 * image at most. Encoding, JavaScript delivery and decode are not render receipts: only the Stage's
 * visual-state callback followed by its visible onDraw may call [drawn].
 */
internal class Command03ComputerProjection<F : Any>(
    private val work: (() -> Unit) -> Unit,
    private val ui: (() -> Unit) -> Unit,
    private val encode: (F) -> Command03EncodedFrame?,
    private val emit: (Command03ComputerPacket) -> Unit,
    private val invalidateView: () -> Unit,
    private val clock: () -> Long = System::nanoTime
) {
    private data class Pending<F : Any>(val ack: Command03FrameAck, val feed: LiveMonitorFeed<F>,
        val imported: LiveMonitorSubmission.ImportedFrame)

    private var session = ""
    private var feed: LiveMonitorFeed<F>? = null
    private var generation = 0L
    private var sequence = 0L
    private var active = false
    private var hidden = false
    private var binding: LiveFrameBinding? = null
    private var sourceEpoch = -1L
    private var lastState: Pair<LiveMonitorState, String>? = null
    private var encoderBusy = false
    private var lastEncodeAt = Long.MIN_VALUE
    private var lastSourceSequence = -1L
    private var lastFrameAck: Command03FrameAck? = null
    private var pending: Pending<F>? = null
    private var rendered: Pending<F>? = null

    fun bind(sessionId: String, next: LiveMonitorFeed<F>?) {
        if (session == sessionId && feed === next) return
        suspend()
        session = sessionId
        feed = next
        hidden = false
    }

    /** Synchronous fence for pause, native overlays, reparent, session change, Stop and destruction. */
    fun suspend() {
        if (active || pending != null) {
            feed?.monitorSubmission?.invalidate()
            invalidateView()
        }
        if (active) emit(Command03ComputerPacket(session, generation, ++sequence, false,
            LiveMonitorState.ENDED.name, "Live-Bild angehalten"))
        active = false
        pending = null
        rendered = null
        lastFrameAck = null
        binding = null
        sourceEpoch = -1
        lastState = null
        lastSourceSequence = -1
        // A still-running encoder retains its single slot until its lease is released.
    }

    fun tick(visible: Boolean) {
        val source = feed
        if (!visible || hidden || source == null || !Command03BridgeContract.validSession(session)) {
            suspend()
            return
        }
        val now = clock()
        val status = source.status(now)
        if (status.binding == null || status.state == LiveMonitorState.ENDED || status.state == LiveMonitorState.NO_SOURCE) {
            suspend()
            return
        }
        if (!active || binding != status.binding || sourceEpoch != status.sourceEpoch) {
            suspend()
            generation++
            sequence = 0
            active = true
            binding = status.binding
            sourceEpoch = status.sourceEpoch
            lastEncodeAt = Long.MIN_VALUE
        }
        if (status.state != LiveMonitorState.LIVE) {
            source.monitorSubmission.invalidate()
            pending = null
            rendered = null
            lastFrameAck = null
            invalidateView()
            publishStatus(status)
            return
        }
        if (pending != null && !source.monitorSubmission.usable(pending?.imported, status, now)) {
            source.monitorSubmission.invalidate()
            pending = null
            rendered = null
            lastFrameAck = null
            invalidateView()
        }
        if (encoderBusy || pending != null || status.sequence == lastSourceSequence ||
            (lastEncodeAt != Long.MIN_VALUE && now - lastEncodeAt < Command03EncodedFrame.MIN_FRAME_INTERVAL_MS * 1_000_000)) return
        val lease = source.acquire() ?: return
        val sampledAt = clock()
        val sampled = source.status(sampledAt)
        val imported = source.monitorSubmission.imported(sampled, sampledAt)
        if (imported == null || lease.binding != sampled.binding || lease.meta != sampled.meta ||
            lease.sequence != sampled.sequence || !source.monitorSubmission.usable(imported, sampled, sampledAt)) {
            lease.close()
            return
        }
        val ownerSession = session
        val ownerGeneration = generation
        encoderBusy = true
        lastEncodeAt = sampledAt
        try {
            work {
                val encoded = try { runCatching { encode(lease.frame) }.getOrNull()?.takeIf { it.valid() } }
                    finally { lease.close() }
                ui {
                    encoderBusy = false
                    if (!active || session != ownerSession || generation != ownerGeneration || feed !== source) return@ui
                    val deliveredAt = clock()
                    val current = source.status(deliveredAt)
                    if (encoded == null || !source.monitorSubmission.usable(imported, current, deliveredAt)) {
                        source.monitorSubmission.invalidate()
                        rendered = null
                        invalidateView()
                        lastState = null
                        publishStatus(current, "PAUSED", "Bild nicht verfügbar oder nicht mehr frisch")
                        return@ui
                    }
                    val age = requireNotNull(sampled.ageMillis) + (deliveredAt - sampledAt) / 1_000_000
                    if (age >= requireNotNull(sampled.binding).staleAfterMillis.coerceAtMost(1_500)) return@ui
                    val packet = Command03ComputerPacket(session, generation, ++sequence, true,
                        LiveMonitorState.LIVE.name, sampled.label, sampled.binding, sampled.sourceEpoch,
                        age, requireNotNull(sampled.binding).staleAfterMillis.coerceAtMost(1_500), encoded,
                        sourceSequence = sampled.sequence,
                        observationRevision = sampled.meta?.observationRevision?.takeIf { it >= 0 },
                        viewport = sampled.meta?.sourceViewport)
                    pending = Pending(packet.acknowledgement, source, imported)
                    lastFrameAck = packet.acknowledgement
                    lastSourceSequence = sampled.sequence
                    lastState = LiveMonitorState.LIVE to sampled.label
                    emit(packet)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            lease.close()
            encoderBusy = false
            source.monitorSubmission.invalidate()
            rendered = null
            invalidateView()
            publishStatus(status, "PAUSED", "Bildübertragung derzeit nicht verfügbar")
        }
    }

    private fun publishStatus(status: LiveMonitorStatus, state: String = status.state.name,
        reason: String = status.label) {
        val key = status.state to reason
        if (lastState == key) return
        lastState = key
        emit(Command03ComputerPacket(session, generation, ++sequence, true, state, reason,
            status.binding, status.sourceEpoch))
    }

    fun drawn(ack: Command03FrameAck, visible: Boolean): Boolean {
        val frame = pending ?: return false
        if (!visible || !active || ack != frame.ack || session != ack.sessionId || generation != ack.generation ||
            feed !== frame.feed) return false
        val now = clock()
        val accepted = frame.feed.monitorSubmission.submitted(frame.imported, frame.feed.status(now), now)
        if (accepted) { rendered = frame; pending = null }
        return accepted
    }

    /** Returns true only for loss of the current surface, not replacement of an older displayed image. */
    fun hidden(ack: Command03FrameAck): Boolean {
        if (!active || ack.sessionId != session || ack.generation != generation) return false
        val shown = rendered
        if (shown?.ack == ack) {
            shown.feed.monitorSubmission.withdraw(shown.imported)
            rendered = null
            if (pending != null && pending?.ack != ack) return false
        } else if (ack != lastFrameAck) return false
        hide()
        return true
    }

    fun hide() {
        hidden = true
        suspend()
    }
}
