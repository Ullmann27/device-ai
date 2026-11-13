package at.heinz.deviceai.cognitive

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Bounded per-session counters for the live-computer image chain (Code56 diagnosis events).
 *
 * The Code55 field test only showed a black monitor; nobody could tell whether consent, the capture service, the
 * window gate, the GPU copy or the Filament submission failed. These counters record fixed event codes only – no
 * pixels, no text, no package lists – and are reset for each capture session. Counting is lock-free so the frame
 * path stays cheap; the owner sees one compact German line when a task fails.
 */
internal object LiveCaptureDiagnostics {
    enum class Event(val label: String) {
        PROJECTION_CONSENT_REQUESTED("Freigabe angefragt"),
        PROJECTION_CONSENT_ACCEPTED("Freigabe erteilt"),
        PROJECTION_CONSENT_DENIED("Freigabe abgelehnt"),
        CAPTURE_SERVICE_STARTED("Aufnahmedienst gestartet"),
        CAPTURED_CONTENT_VISIBLE("Bildschirm sichtbar gemeldet"),
        CAPTURED_CONTENT_HIDDEN("Bildschirm verborgen gemeldet"),
        CAPTURE_FRAME_RECEIVED("Bilder empfangen"),
        TARGET_WINDOW_VISIBLE("Zielfenster bestätigt"),
        FRAME_SUBMITTED_TO_MONITOR("Bilder an Monitor übergeben"),
        MONITOR_PRESENTED("im 3D-Monitor dargestellt"),
        ACTION_DISPATCHED("Eingaben ausgelöst"),
        ACTION_EFFECT_CONFIRMED("Wirkungen bestätigt"),
        CAPTURE_STOPPED("Aufnahme beendet")
    }

    enum class Rejection(val label: String) {
        CONTENT_NOT_VISIBLE("Bildschirm nicht sichtbar"),
        OBSERVATION_UNAVAILABLE("Beobachtung nicht verbunden"),
        TARGET_WINDOW_UNVERIFIED("Zielfenster nicht eindeutig/unverdeckt"),
        OWN_WINDOW_UNVERIFIED("eigenes Fenster nicht eindeutig"),
        CROP_UNVERIFIED("Ausschnitt nicht berechenbar"),
        PROTECTION_UNCONFIRMED("Schutzprüfung offen"),
        GEOMETRY_RESET("Fensterlage neu gebunden"),
        SOURCE_CHANGED_DURING_COPY("Quelle während Kopie geändert"),
        COPY_FAILED("GPU-Kopie fehlgeschlagen"),
        HOLDER_REFUSED("vom Monitor abgelehnt")
    }

    private val session = AtomicLong(0)
    private val events = AtomicLongArray(Event.entries.size)
    private val rejections = AtomicLongArray(Rejection.entries.size)

    private val consent = setOf(Event.PROJECTION_CONSENT_REQUESTED, Event.PROJECTION_CONSENT_ACCEPTED,
        Event.PROJECTION_CONSENT_DENIED)

    /** New capture session: previous frame/action counts never leak into it; the consent that led to it stays. */
    fun begin(captureSession: Long) {
        session.set(captureSession)
        for (event in Event.entries) if (event !in consent) events.set(event.ordinal, 0)
        for (i in 0 until rejections.length()) rejections.set(i, 0)
    }

    fun record(captureSession: Long, event: Event) {
        if (captureSession > 0 && captureSession == session.get()) events.incrementAndGet(event.ordinal)
    }

    /** Consent happens before a capture session exists; it is counted for the session that follows it. */
    fun recordPending(event: Event) { if (session.get() == 0L) events.incrementAndGet(event.ordinal) }

    fun reject(captureSession: Long, rejection: Rejection) {
        if (captureSession > 0 && captureSession == session.get()) rejections.incrementAndGet(rejection.ordinal)
    }

    fun count(event: Event): Long = events.get(event.ordinal)
    fun count(rejection: Rejection): Long = rejections.get(rejection.ordinal)

    /** Clears the session binding so consent counting for the next task starts from zero. */
    fun prepareConsent() {
        session.set(0)
        for (i in 0 until events.length()) events.set(i, 0)
        for (i in 0 until rejections.length()) rejections.set(i, 0)
    }

    /** One German line, e.g. „Bildkette: Bilder empfangen 14 · im 3D-Monitor dargestellt 0 · verworfen: …“. */
    fun summary(): String {
        val shown = listOf(Event.PROJECTION_CONSENT_ACCEPTED, Event.CAPTURE_SERVICE_STARTED,
            Event.CAPTURE_FRAME_RECEIVED, Event.TARGET_WINDOW_VISIBLE, Event.FRAME_SUBMITTED_TO_MONITOR,
            Event.MONITOR_PRESENTED, Event.ACTION_DISPATCHED, Event.ACTION_EFFECT_CONFIRMED)
            .joinToString(" · ") { "${it.label} ${count(it)}" }
        val rejected = Rejection.entries.filter { count(it) > 0 }
            .sortedByDescending { count(it) }.take(3).joinToString(", ") { "${it.label} ${count(it)}" }
        return "Bildkette: $shown" + if (rejected.isEmpty()) "" else " · verworfen: $rejected"
    }
}
