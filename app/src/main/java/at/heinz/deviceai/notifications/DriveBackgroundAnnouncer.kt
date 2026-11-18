package at.heinz.deviceai.notifications

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import at.heinz.deviceai.cognitive.JarvisVoiceProfile
import java.util.Locale

/**
 * Speaks allowed notifications while the drive mode is on and Continuum's own screen is NOT in front
 * (for example while a navigation app is shown). With the screen in front, AgentActivity presents the
 * announcement itself and coordinates it with the microphone; this class then stays silent.
 *
 * It lives in the notification listener service, which Android keeps bound after the owner's one-time
 * access grant. It only speaks: no microphone, no dialogue, no action. The decision what may be said is
 * the same [NotificationAnnouncementPolicy] the foreground path uses, so codes, links and unselected apps
 * are handled identically. Only an offline German voice is used; if the device has none, nothing is said.
 *
 * Not verified on a device: audio focus and routing from a bound background service (speaker, Bluetooth,
 * car) depend on the Android version and vendor.
 */
internal class DriveBackgroundAnnouncer(context: Context) : ContinuumNotificationBus.Subscriber {
    private val appContext: Context = context.applicationContext ?: context
    private val handler = Handler(Looper.getMainLooper())
    private val policy = NotificationAnnouncementPolicy()
    private var speech: TextToSpeech? = null
    private var ready = false
    private var utterance = 0L

    fun start() = ContinuumNotificationBus.subscribe(this)

    fun stop() {
        ContinuumNotificationBus.unsubscribe(this)
        handler.post { release() }
    }

    override fun onNotificationPosted(notification: IncomingNotification) {
        handler.post { announce(notification) }
    }

    /** A removed notification may come again with the same text (next message in the same chat). */
    override fun onNotificationRemoved(key: String) {
        handler.post { policy.forget(key) }
    }

    private fun announce(notification: IncomingNotification) {
        if (foregroundPresenterActive || !driveModeActive(appContext)) {
            // Outside the drive the engine is not kept alive.
            if (!driveModeActive(appContext)) release()
            return
        }
        val decision = policy.decide(notification, NotificationAnnouncementSettings.load(appContext),
            AnnouncementState(conversationActive = false, driveModeActive = true,
                // The engine queues; the policy's own rate limit bounds how much can pile up.
                assistantSpeaking = false, listening = false, nowMs = SystemClock.elapsedRealtime()))
        val text = (decision as? AnnouncementDecision.Announce)?.spokenText ?: return
        val engine = speech
        if (engine != null && ready) { speak(engine, text); return }
        if (engine == null) speech = TextToSpeech(appContext) { status ->
            handler.post {
                val created = speech
                if (status != TextToSpeech.SUCCESS || created == null || !configure(created)) { release(); return@post }
                ready = true
                speak(created, text)
            }
        }
    }

    /** German, offline only: private notification content never leaves the device for synthesis. */
    private fun configure(engine: TextToSpeech): Boolean {
        val voice = runCatching {
            engine.voices?.filter { it.locale?.language == Locale.GERMAN.language && !it.isNetworkConnectionRequired }
                ?.minByOrNull { if (it.locale?.country == "DE") 0 else 1 }
        }.getOrNull() ?: return false
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return false
        engine.setSpeechRate(JarvisVoiceProfile.speechRate)
        engine.setPitch(JarvisVoiceProfile.pitch)
        engine.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        return true
    }

    private fun speak(engine: TextToSpeech, text: String) {
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "continuum-drive-" + ++utterance)
    }

    private fun release() {
        runCatching { speech?.stop(); speech?.shutdown() }
        speech = null
        ready = false
    }

    companion object {
        private const val VOICE_PREFERENCES = "continuum-voice"
        private const val DRIVE_ACTIVE = "drive_mode_active"
        private const val DRIVE_SINCE = "drive_mode_since_ms"
        /** A drive mode that was never switched off (process death) ends by itself. */
        const val MAX_DRIVE_MS = 12L * 60L * 60L * 1_000L

        /** True while AgentActivity is resumed and presents announcements itself. */
        @Volatile var foregroundPresenterActive: Boolean = false

        fun markDriveMode(context: Context, active: Boolean, nowWallMs: Long = System.currentTimeMillis()) {
            context.getSharedPreferences(VOICE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(DRIVE_ACTIVE, active).putLong(DRIVE_SINCE, if (active) nowWallMs else 0L).apply()
        }

        fun driveModeActive(context: Context, nowWallMs: Long = System.currentTimeMillis()): Boolean {
            val prefs = context.getSharedPreferences(VOICE_PREFERENCES, Context.MODE_PRIVATE)
            return prefs.getBoolean(DRIVE_ACTIVE, false) &&
                driveStillValid(prefs.getLong(DRIVE_SINCE, 0L), nowWallMs)
        }

        /** Pure part of the expiry rule, kept separate so it is unit-testable. */
        fun driveStillValid(sinceWallMs: Long, nowWallMs: Long): Boolean =
            sinceWallMs > 0L && nowWallMs >= sinceWallMs && nowWallMs - sinceWallMs < MAX_DRIVE_MS
    }
}
