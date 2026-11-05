package at.heinz.deviceai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Local sessions; never authentication authority. */
internal class AndroidOwnerFaceRecognition(
    private val store: OwnerFaceProfileStore,
    private val engineFactory: () -> OwnerFaceInference,
    private val worker: ExecutorService,
    private val main: Handler,
    private val elapsed: () -> Long,
    private val wall: () -> Long,
    private val onState: (OwnerFaceStatus) -> Unit
) : AutoCloseable {
    constructor(context: Context, onState: (OwnerFaceStatus) -> Unit) : this(
        OwnerFaceProfileStore(context.applicationContext),
        { OpenCvOwnerFaceEngine(context.applicationContext) },
        Executors.newSingleThreadExecutor { task -> Thread(task, "continuum-owner-face").apply { isDaemon = true } },
        Handler(Looper.getMainLooper()), SystemClock::elapsedRealtime, System::currentTimeMillis, onState
    )

    private val lock = Any()
    private val inFlight = AtomicBoolean(false)
    private var engine: OwnerFaceInference? = null
    private var policy: OwnerFacePolicy? = null
    private var profileLoaded = false
    private var profileOperation = 0L
    private var hasProfile = false
    private var profileName: String? = null
    private var enrollingName: String? = null
    private var startedAt = 0L
    private var lastIssuedAt = 0L
    private var lastSubmittedCaptureAt = -1L
    private var lastObservedAt = -1L
    private var watchdog: Runnable? = null
    @Volatile private var closed = false
    @Volatile private var activeToken = 0L
    @Volatile var status = OwnerFaceStatus(OwnerFacePhase.LOADING, message = "Lokales Gesichtsprofil wird geprüft.")
        private set
    val wantsFrames: Boolean get() = !closed && activeToken > 0
    val sessionToken: Long get() = activeToken

    init {
        worker.execute {
            var loaded: OwnerFaceProfile? = null
            try {
                loaded = store.load()
                synchronized(lock) {
                    if (!closed && profileOperation == 0L) {
                        hasProfile = loaded != null
                        profileName = loaded?.displayName
                        profileLoaded = true
                        publishLocked(if (hasProfile) OwnerFacePhase.READY else OwnerFacePhase.NOT_ENROLLED,
                            if (hasProfile) "Ein lokales Profil ist eingerichtet. Ein Abgleich braucht deine ausdrückliche Freigabe."
                            else "Noch kein Gesicht eingelernt. Ohne Einlernen gibt es keinen persönlichen Treffer.")
                    }
                }
            } catch (_: Exception) {
                synchronized(lock) {
                    if (!closed && profileOperation == 0L) {
                        profileLoaded = true
                        publishLocked(OwnerFacePhase.ERROR,
                            "Das gespeicherte Gesichtsprofil konnte nicht geöffnet werden. Du kannst es löschen oder neu einlernen.")
                    }
                }
            } finally { loaded?.clear() }
        }
    }

    fun beginEnrollment(displayName: String, ownerConsent: Boolean): Boolean =
        begin(displayName.trim(), ownerConsent)

    fun beginRecognition(ownerConsent: Boolean): Boolean = begin(null, ownerConsent)

    private fun begin(name: String?, consent: Boolean): Boolean {
        val token = synchronized(lock) {
            if (closed || !profileLoaded) return false
            invalidateLocked()
            if (!consent) {
                publishLocked(OwnerFacePhase.OFF, "Gesichtsabgleich bleibt aus.")
                return false
            }
            if (name != null && !OwnerFaceProfile.validName(name)) {
                publishLocked(OwnerFacePhase.ERROR, "Bitte einen kurzen Anzeigenamen ohne Steuerzeichen eingeben.")
                return false
            }
            if (name == null && !hasProfile) {
                publishLocked(OwnerFacePhase.NOT_ENROLLED, "Lerne zuerst ausdrücklich ein Gesicht ein.")
                return false
            }
            val fresh = nextSession.incrementAndGet()
            check(fresh > 0)
            activeToken = fresh
            enrollingName = name
            startedAt = elapsed()
            lastIssuedAt = startedAt
            lastSubmittedCaptureAt = -1L
            lastObservedAt = -1L
            publishLocked(OwnerFacePhase.LOADING, "Lokale Gesichtserkennung wird geladen.")
            armWatchdogLocked(fresh)
            fresh
        }
        try {
            worker.execute {
                if (!current(token)) return@execute
                var loaded: OwnerFaceProfile? = null
                try {
                    if (name == null) loaded = store.load()
                    if (name == null && loaded == null) {
                        finish(token, OwnerFacePhase.NOT_ENROLLED, "Kein gültiges lokales Gesichtsprofil vorhanden.")
                        return@execute
                    }
                    val readyEngine = engine ?: engineFactory().also { engine = it }
                    synchronized(lock) {
                        if (!currentLocked(token)) return@synchronized
                        check(readyEngine === engine)
                        policy = OwnerFacePolicy(name, loaded)
                        lastIssuedAt = elapsed()
                        publishLocked(if (name == null) OwnerFacePhase.RECOGNIZING else OwnerFacePhase.ENROLLING,
                            if (name == null) "Schau allein und bei gutem Licht in die Frontkamera."
                            else "Bitte allein in die Frontkamera schauen und den Kopf langsam leicht bewegen.")
                    }
                } catch (_: LinkageError) {
                    finish(token, OwnerFacePhase.UNAVAILABLE, "Die lokale native Gesichtserkennung ist nicht verfügbar.")
                } catch (_: Exception) {
                    finish(token, OwnerFacePhase.UNAVAILABLE, "Die lokalen Modelle oder das Gesichtsprofil konnten nicht geladen werden.")
                } finally { loaded?.clear() }
            }
        } catch (_: RejectedExecutionException) {
            finish(token, OwnerFacePhase.ERROR, "Die lokale Gesichtssitzung wurde beendet.")
            return false
        }
        return true
    }

    fun acceptFrame(frame: OwnerFaceFrame) {
        val token = frame.sessionToken
        val now = elapsed()
        val admitted = synchronized(lock) {
            val acceptable = currentLocked(token) && policy != null && frame.valid() && frame.observedAtElapsedMillis <= now &&
                now - frame.observedAtElapsedMillis <= OwnerFacePolicy.MAX_FRAME_AGE_MS &&
                (lastSubmittedCaptureAt < 0 ||
                    frame.observedAtElapsedMillis - lastSubmittedCaptureAt >= OwnerFacePolicy.MIN_SAMPLE_GAP_MS)
            if (!acceptable || !inFlight.compareAndSet(false, true)) false else {
                lastIssuedAt = now
                lastSubmittedCaptureAt = frame.observedAtElapsedMillis
                true
            }
        }
        if (!admitted) { frame.close(); return }
        try {
            worker.execute {
                var observation: OwnerFaceObservation? = null
                var completed: OwnerFaceProfile? = null
                try {
                    if (!current(token)) return@execute
                    val observed = checkNotNull(engine).observe(frame)
                    observation = observed
                    val decision = synchronized(lock) {
                        if (!currentLocked(token)) null else {
                            lastObservedAt = observed.observedAtElapsedMillis
                            checkNotNull(policy).accept(observed, elapsed(), wall())
                        }
                    } ?: return@execute
                    val completedProfile = decision.profile
                    completed = completedProfile
                    if (completedProfile != null) {
                        if (!current(token)) return@execute
                        val prepared = store.prepare(completedProfile)
                        try {
                            synchronized(lock) {
                                if (currentLocked(token)) {
                                    store.commit(prepared)
                                    hasProfile = true
                                    profileName = completedProfile.displayName
                                    invalidateLocked()
                                    publishLocked(OwnerFacePhase.ENROLLED,
                                        "Gesichtsprofil lokal und verschlüsselt gespeichert. Du kannst jetzt einen Abgleich starten.",
                                        OwnerFacePolicy.ENROLLMENT_SAMPLES)
                                }
                            }
                        } finally { store.discard(prepared) }
                    } else synchronized(lock) {
                        if (currentLocked(token)) publishLocked(decision.phase, message(decision.phase), decision.acceptedSamples)
                    }
                } catch (_: LinkageError) {
                    finish(token, OwnerFacePhase.UNAVAILABLE, "Die native Gesichtserkennung konnte nicht ausgeführt werden.")
                } catch (_: Exception) {
                    finish(token, OwnerFacePhase.ERROR, "Der lokale Gesichtsabgleich ist fehlgeschlagen. Kein Treffer wurde bestätigt.")
                } finally {
                    completed?.clear()
                    observation?.close()
                    frame.close()
                    inFlight.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            frame.close()
            inFlight.set(false)
            finish(token, OwnerFacePhase.ERROR, "Die lokale Gesichtssitzung wurde beendet.")
        }
    }

    fun stop() = synchronized(lock) {
        if (activeToken > 0) {
            invalidateLocked()
            publishLocked(OwnerFacePhase.OFF, "Gesichtsabgleich gestoppt. Es gibt keinen aktuellen persönlichen Treffer.")
        }
    }

    fun deleteProfile() {
        synchronized(lock) {
            if (closed) return
            invalidateLocked()
            val operation = ++profileOperation
            profileLoaded = false
            hasProfile = false
            profileName = null
            publishLocked(OwnerFacePhase.LOADING, "Lokales Gesichtsprofil wird gelöscht.")
            try {
                worker.execute {
                    try {
                        store.delete()
                        synchronized(lock) {
                            if (!closed && profileOperation == operation) {
                                profileLoaded = true
                                publishLocked(OwnerFacePhase.DELETED, "Gesichtsprofil und Schlüssel wurden gelöscht.")
                            }
                        }
                    } catch (_: Exception) {
                        synchronized(lock) {
                            if (!closed && profileOperation == operation) {
                                profileLoaded = true
                                publishLocked(OwnerFacePhase.ERROR,
                                    "Das Gesichtsprofil konnte nicht vollständig gelöscht werden. Bitte erneut versuchen.")
                            }
                        }
                    }
                }
            } catch (_: RejectedExecutionException) {
                profileLoaded = true
                publishLocked(OwnerFacePhase.ERROR, "Das Profil konnte nicht gelöscht werden. Bitte erneut öffnen und löschen.")
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            invalidateLocked()
            publishLocked(OwnerFacePhase.OFF, "Gesichtsabgleich geschlossen.")
            closed = true
            try { worker.execute { engine?.close(); engine = null } }
            catch (_: RejectedExecutionException) { engine = null }
            worker.shutdown()
        }
    }

    private fun finish(token: Long, phase: OwnerFacePhase, text: String) = synchronized(lock) {
        if (currentLocked(token)) {
            invalidateLocked()
            if (phase == OwnerFacePhase.NOT_ENROLLED) { hasProfile = false; profileName = null }
            publishLocked(phase, text)
        }
    }

    private fun current(token: Long): Boolean = synchronized(lock) { currentLocked(token) }
    private fun currentLocked(token: Long) = !closed && token > 0 && token == activeToken

    private fun invalidateLocked() {
        activeToken = 0
        policy?.close()
        policy = null
        enrollingName = null
        watchdog?.let(main::removeCallbacks)
        watchdog = null
    }

    private fun publishLocked(phase: OwnerFacePhase, text: String, samples: Int = 0) {
        val value = OwnerFaceStatus(phase, hasProfile, profileName, samples,
            if (enrollingName != null || phase == OwnerFacePhase.ENROLLED) OwnerFacePolicy.ENROLLMENT_SAMPLES
            else OwnerFacePolicy.REQUIRED_MATCHES, activeToken > 0, text)
        status = value
        main.post { synchronized(lock) { if (!closed && status === value) onState(value) } }
    }

    private fun armWatchdogLocked(token: Long) {
        val runnable = object : Runnable {
            override fun run() {
                synchronized(lock) {
                    if (!currentLocked(token)) return
                    val now = elapsed()
                    val loading = status.phase == OwnerFacePhase.LOADING
                    val deadline = if (enrollingName == null) 90_000L else 60_000L
                    if (now < startedAt || now - startedAt > deadline ||
                        loading && now - startedAt > 30_000L || !loading && now - lastIssuedAt > 5_000L) {
                        invalidateLocked()
                        publishLocked(OwnerFacePhase.ERROR, "Die Gesichtssitzung ist abgelaufen oder liefert keine aktuellen Bilder. Bitte ausdrücklich neu starten.")
                        return
                    }
                    if (status.phase == OwnerFacePhase.MATCH && (lastObservedAt < 0 ||
                            now - lastObservedAt > OwnerFacePolicy.MAX_FRAME_AGE_MS)) {
                        policy?.expireMatch()
                        publishLocked(OwnerFacePhase.NO_FACE, "Kein aktueller Gesichtsabgleich. Der vorherige Treffer ist abgelaufen.")
                    }
                    main.postDelayed(this, 250)
                }
            }
        }
        watchdog = runnable
        main.postDelayed(runnable, 250)
    }

    private fun message(phase: OwnerFacePhase): String = when (phase) {
        OwnerFacePhase.MATCH -> "Das Gesicht passt zum eingelernten Profil. Das ist kein Berechtigungsnachweis."
        OwnerFacePhase.NO_MATCH -> if (enrollingName != null) "Gesicht gewechselt. Das Einlernen beginnt neu."
            else "Kein ausreichender Abgleich zum eingelernten Profil."
        OwnerFacePhase.NO_FACE -> "Kein einzelnes Gesicht sichtbar. Bitte in die Frontkamera schauen."
        OwnerFacePhase.MULTIPLE_FACES -> "Mehrere Gesichter sichtbar. Es wird keine Person ausgewählt."
        OwnerFacePhase.LOW_QUALITY -> "Bitte für gutes Licht sorgen, näher herankommen und den Kopf ruhig halten."
        OwnerFacePhase.ENROLLING -> "Aktuelle Aufnahmen derselben Person werden eingelernt."
        OwnerFacePhase.RECOGNIZING -> "Der lokale Abgleich braucht mehrere aktuelle, passende Aufnahmen."
        OwnerFacePhase.NOT_ENROLLED -> "Kein gültiges lokales Gesichtsprofil vorhanden."
        else -> "Kein persönlicher Treffer bestätigt."
    }

    private companion object { val nextSession = AtomicLong() }
}
