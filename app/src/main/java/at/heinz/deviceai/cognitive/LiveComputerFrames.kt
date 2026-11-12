package at.heinz.deviceai.cognitive

/*
 * Live-computer frame contract (Code55, Agent C). Pure Kotlin, no Android types, so the policy is host-tested.
 *
 * Image source, operation and presentation stay separate: a producer (MediaProjection or an accessibility window
 * screenshot) offers frames, this holder decides whether a frame may be shown, and a renderer (Agent E's Filament
 * monitor, or any existing view) only leases the latest accepted frame. All three are coupled by task id, capture
 * session, source window and frame time. A frame that cannot be bound is refused and released at once; nothing is
 * queued, encoded or copied here.
 */

internal enum class LiveFrameOrigin {
    /** MediaProjection of the whole default display: every visible window, including Continuum's own. */
    PROJECTION_DISPLAY,
    /** MediaProjection of one app the owner picked in Android's own dialog (Android 14+). Android does not tell the
     *  app WHICH app was picked, so the producer must bind the package from a separate observation. */
    PROJECTION_SINGLE_APP,
    /** AccessibilityService.takeScreenshotOfWindow for one known window id (Android 14+). */
    ACCESSIBILITY_WINDOW
}

private val LIVE_PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
private val LIVE_ID = Regex("[A-Za-z0-9._:-]{1,128}")

/** What the monitor may show right now: one task, one capture session, one target app. */
internal data class LiveFrameBinding(
    val taskId: String,
    val captureSession: Long,
    val targetPackage: String,
    val ownPackage: String,
    val origin: LiveFrameOrigin,
    /** Without a new frame for this long the monitor is PAUSED; absence of frames is never shown as LIVE. */
    val staleAfterMillis: Long = 1_500,
    /** Frames closer together than this are dropped before any work (lean pipeline, no per-frame model call). */
    val minFrameIntervalMillis: Long = 100
) {
    init {
        require(taskId.matches(LIVE_ID)) { "INVALID_TASK_ID" }
        require(captureSession > 0) { "INVALID_CAPTURE_SESSION" }
        require(targetPackage.matches(LIVE_PACKAGE) && ownPackage.matches(LIVE_PACKAGE)) { "INVALID_PACKAGE" }
        // Binding the monitor to Continuum itself would be the screen-in-screen loop by construction.
        require(targetPackage != ownPackage) { "OWN_WINDOW_MIRROR" }
        require(staleAfterMillis in 100..30_000 && minFrameIntervalMillis in 0..5_000)
    }
}

/** Facts a producer measured for one frame at capture time. Nothing here is inferred later. */
internal data class LiveFrameMeta(
    val captureSession: Long,
    /** Monotonic producer timestamp (e.g. Image.getTimestamp); strictly increasing within one session. */
    val frameTimeNanos: Long,
    val width: Int,
    val height: Int,
    /** Package whose pixels the frame shows, as verified by the producer; null = not verified. */
    val sourcePackage: String?,
    /** Accessibility window id of the source, when known. */
    val sourceWindowId: Int?,
    /** Any Continuum window was visible on the captured display when the frame was taken. */
    val ownWindowVisible: Boolean,
    /** The source is protected (FLAG_SECURE) or Android refused its pixels; such frames are never shown. */
    val secureContent: Boolean = false,
    /** ScreenObservationBus revision at capture time, so an action can bind to exactly this observation. */
    val observationRevision: Long = -1
)

internal enum class LiveFrameRefusal {
    NOT_BOUND, STOPPED, WRONG_SESSION, INVALID_SIZE, OUT_OF_ORDER, RATE_LIMITED,
    SOURCE_UNVERIFIED, WRONG_SOURCE, OWN_WINDOW_MIRROR, SECURE_CONTENT
}

internal sealed interface LiveFrameOffer {
    data class Accepted(val sequence: Long) : LiveFrameOffer
    data class Refused(val reason: LiveFrameRefusal) : LiveFrameOffer
}

/** Monitor states the owner sees. LIVE only for a fresh frame of the bound session and source. */
internal enum class LiveMonitorState(val label: String) {
    NO_SOURCE("Keine Bildquelle"),
    WAITING("Warte auf erstes echtes Bild"),
    LIVE("Live"),
    PAUSED("Pausiert – kein frisches Bild"),
    /** Source hidden, protected, or only reachable through Continuum's own mirror. */
    COVERED("Verdeckt"),
    ENDED("Beendet")
}

/** Why the monitor is COVERED or PAUSED; null while LIVE. Producer pause reasons are fixed, never free text. */
internal enum class LiveMonitorReason(val label: String) {
    FRAME_STALE("kein neues Bild"),
    SOURCE_HIDDEN("Ziel-App nicht sichtbar"),
    OWN_WINDOW_MIRROR("eigenes Fenster im Bild – Spiegelschleife verhindert"),
    SECURE_CONTENT("geschützte App – Android gibt kein Bild frei"),
    WRONG_SOURCE("anderes Fenster als die Ziel-App"),
    SOURCE_UNVERIFIED("Bildquelle nicht bestätigt"),
    CAPTURE_STOPPED("Aufnahme beendet"),
    PERMISSION_REVOKED("Freigabe entzogen")
}

internal data class LiveMonitorStatus(
    val state: LiveMonitorState,
    val reason: LiveMonitorReason?,
    val binding: LiveFrameBinding?,
    val meta: LiveFrameMeta?,
    val sequence: Long,
    val ageMillis: Long?
) {
    /** Exactly one German status line; never claims LIVE for an unbound, stale or covered frame. */
    val label: String get() = if (reason == null) state.label else "${state.label} · ${reason.label}"
}

/**
 * A renderer's short-lived claim on the latest accepted frame. Close it after the frame was drawn/consumed (e.g. as
 * Filament's acquired-image release callback). Closing twice is harmless; a frame replaced while leased is released
 * when its lease closes, never earlier.
 */
internal class LiveFrameLease<F : Any> internal constructor(
    val frame: F,
    val meta: LiveFrameMeta,
    val sequence: Long,
    val binding: LiveFrameBinding,
    private val onClose: () -> Unit
) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) onClose() }
}

/** The small interface Agent E's monitor (or any existing view) consumes. No rendering, no copies, no queue. */
internal interface LiveMonitorFeed<F : Any> {
    /** Cheap, lock-protected status for the current frame tick. */
    fun status(nowNanos: Long): LiveMonitorStatus
    /** Latest accepted frame, or null (no frame / already leased / ended). One lease at a time. */
    fun acquire(): LiveFrameLease<F>?
    /** Increments on every accepted frame or state change, so a renderer can skip unchanged ticks. */
    fun version(): Long
}

/**
 * Latest-frame-only holder. At most two frames are alive at any time: the current one and one predecessor that a
 * renderer still leases. With an ImageReader this needs maxImages >= 3 (two retained + one being acquired).
 */
internal class LatestLiveFrameHolder<F : Any>(
    private val release: (F) -> Unit,
    private val clockNanos: () -> Long = System::nanoTime
) : LiveMonitorFeed<F> {
    private class Slot<F : Any>(val frame: F, val meta: LiveFrameMeta, val sequence: Long, val receivedNanos: Long,
                                val binding: LiveFrameBinding) {
        var leased = false
        var retired = false
    }

    private val lock = Any()
    private var binding: LiveFrameBinding? = null
    private var ended = false
    private var current: Slot<F>? = null
    private var retiredLeased: Slot<F>? = null
    private var sequence = 0L
    private var version = 0L
    private var lastAcceptedNanos = Long.MIN_VALUE
    /** Latest reason the source was refused/paused since the last accepted frame. */
    private var block: LiveMonitorReason? = null

    /**
     * Binds the monitor to a new task/session/target (start, or app switch). Every frame of the previous binding is
     * dropped immediately, so the previous app's pixels can never be shown for the new one.
     */
    fun bind(next: LiveFrameBinding) = synchronized(lock) {
        binding = next
        ended = false
        block = null
        lastAcceptedNanos = Long.MIN_VALUE
        dropCurrentLocked()
        version++
    }

    /** Capture ended (owner stop, MediaProjection.onStop, permission revoked). Frames are released, state ENDED. */
    fun end(reason: LiveMonitorReason = LiveMonitorReason.CAPTURE_STOPPED) = synchronized(lock) {
        ended = true
        block = reason
        dropCurrentLocked()
        version++
    }

    /** Producer evidence that the source is not visible right now (e.g. onCapturedContentVisibilityChanged(false)). */
    fun pause(reason: LiveMonitorReason) = synchronized(lock) {
        if (!ended && binding != null) {
            block = reason
            dropCurrentLocked()
            version++
        }
    }

    /** Lets a producer skip all per-frame work (IPC, buffer wrapping) when a frame would be refused anyway. */
    fun wantsFrame(session: Long): Boolean = synchronized(lock) {
        val b = binding ?: return@synchronized false
        !ended && b.captureSession == session && !rateLimitedLocked(b, clockNanos())
    }

    fun offer(meta: LiveFrameMeta, frame: F): LiveFrameOffer {
        val refusal: LiveFrameRefusal
        synchronized(lock) {
            val now = clockNanos()
            val b = binding
            refusal = refusalLocked(b, meta, now) ?: run {
                val previous = current
                if (previous != null) retireLocked(previous)
                val slot = Slot(frame, meta, ++sequence, now, b!!)
                current = slot
                lastAcceptedNanos = now
                block = null
                version++
                return LiveFrameOffer.Accepted(slot.sequence)
            }
            when (refusal) {
                LiveFrameRefusal.OWN_WINDOW_MIRROR -> coverLocked(LiveMonitorReason.OWN_WINDOW_MIRROR)
                LiveFrameRefusal.SECURE_CONTENT -> coverLocked(LiveMonitorReason.SECURE_CONTENT)
                LiveFrameRefusal.WRONG_SOURCE -> coverLocked(LiveMonitorReason.WRONG_SOURCE)
                LiveFrameRefusal.SOURCE_UNVERIFIED -> coverLocked(LiveMonitorReason.SOURCE_UNVERIFIED)
                else -> Unit
            }
        }
        release(frame)
        return LiveFrameOffer.Refused(refusal)
    }

    override fun acquire(): LiveFrameLease<F>? = synchronized(lock) {
        val slot = current ?: return@synchronized null
        if (ended || slot.leased || retiredLeased != null) return@synchronized null
        slot.leased = true
        LiveFrameLease(slot.frame, slot.meta, slot.sequence, slot.binding) { closeLease(slot) }
    }

    override fun status(nowNanos: Long): LiveMonitorStatus = synchronized(lock) {
        val b = binding
        val slot = current
        val age = slot?.let { ((nowNanos - it.receivedNanos).coerceAtLeast(0)) / 1_000_000 }
        val (state, reason) = when {
            ended -> LiveMonitorState.ENDED to (block ?: LiveMonitorReason.CAPTURE_STOPPED)
            b == null -> LiveMonitorState.NO_SOURCE to null
            block == LiveMonitorReason.FRAME_STALE || block == LiveMonitorReason.PERMISSION_REVOKED ->
                LiveMonitorState.PAUSED to block
            block != null -> LiveMonitorState.COVERED to block
            slot == null -> LiveMonitorState.WAITING to null
            age!! > b.staleAfterMillis -> LiveMonitorState.PAUSED to LiveMonitorReason.FRAME_STALE
            else -> LiveMonitorState.LIVE to null
        }
        LiveMonitorStatus(state, reason, b, slot?.meta, slot?.sequence ?: 0, age)
    }

    override fun version(): Long = synchronized(lock) { version }

    /** Frames still alive (current + leased predecessor); test and diagnostics hook for the 2-frame bound. */
    internal fun retainedFrames(): Int = synchronized(lock) {
        (if (current != null) 1 else 0) + (if (retiredLeased != null) 1 else 0)
    }

    private fun refusalLocked(b: LiveFrameBinding?, meta: LiveFrameMeta, now: Long): LiveFrameRefusal? {
        if (ended) return LiveFrameRefusal.STOPPED
        if (b == null) return LiveFrameRefusal.NOT_BOUND
        if (meta.captureSession != b.captureSession) return LiveFrameRefusal.WRONG_SESSION
        if (meta.width !in 1..MAX_EDGE || meta.height !in 1..MAX_EDGE ||
            meta.width.toLong() * meta.height > MAX_PIXELS) return LiveFrameRefusal.INVALID_SIZE
        val previous = current
        if (previous != null && meta.frameTimeNanos <= previous.meta.frameTimeNanos) return LiveFrameRefusal.OUT_OF_ORDER
        if (rateLimitedLocked(b, now)) return LiveFrameRefusal.RATE_LIMITED
        // Mirror guard before anything else about the source: Continuum's own pixels never enter its own monitor.
        if (meta.sourcePackage == b.ownPackage) return LiveFrameRefusal.OWN_WINDOW_MIRROR
        if (b.origin == LiveFrameOrigin.PROJECTION_DISPLAY && meta.ownWindowVisible) return LiveFrameRefusal.OWN_WINDOW_MIRROR
        if (meta.secureContent) return LiveFrameRefusal.SECURE_CONTENT
        val source = meta.sourcePackage ?: return LiveFrameRefusal.SOURCE_UNVERIFIED
        if (b.origin == LiveFrameOrigin.ACCESSIBILITY_WINDOW && meta.sourceWindowId == null)
            return LiveFrameRefusal.SOURCE_UNVERIFIED
        if (source != b.targetPackage) return LiveFrameRefusal.WRONG_SOURCE
        return null
    }

    private fun rateLimitedLocked(b: LiveFrameBinding, now: Long): Boolean =
        lastAcceptedNanos != Long.MIN_VALUE && now - lastAcceptedNanos < b.minFrameIntervalMillis * 1_000_000

    /** A covered source must not keep showing an older frame as if it were still current. */
    private fun coverLocked(reason: LiveMonitorReason) {
        block = reason
        dropCurrentLocked()
        version++
    }

    private fun dropCurrentLocked() {
        val slot = current ?: return
        current = null
        retireLocked(slot)
    }

    private fun retireLocked(slot: Slot<F>) {
        slot.retired = true
        if (current === slot) current = null
        // Only one lease exists at a time, so at most one leased predecessor is pending. release() must be cheap and
        // non-blocking (e.g. Image.close()).
        if (slot.leased) retiredLeased = slot else release(slot.frame)
    }

    private fun closeLease(slot: Slot<F>) = synchronized(lock) {
        slot.leased = false
        if (slot.retired && retiredLeased === slot) {
            retiredLeased = null
            release(slot.frame)
        }
    }

    internal companion object {
        const val MAX_EDGE = 2_048
        const val MAX_PIXELS = 2_100_000L
    }
}
