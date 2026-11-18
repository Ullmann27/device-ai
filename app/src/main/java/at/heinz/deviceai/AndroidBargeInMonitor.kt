package at.heinz.deviceai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import at.heinz.deviceai.cognitive.VoiceBargeInDetector

/**
 * Listens for the owner's voice while the assistant is speaking and reports a voice barge-in.
 *
 * Android adapter around [VoiceBargeInDetector]. The on-device SpeechRecognizer and an
 * [AudioRecord] cannot reliably hold the microphone at the same time, so this monitor owns the
 * microphone only while the assistant talks and gives it back completely (stop and release)
 * before anyone is told about a barge-in and before [stop] returns. The caller starts the
 * recognizer only after that.
 *
 * Privacy: audio samples never leave the read loop. They are not stored, logged or forwarded;
 * the only value derived from a frame is one RMS level, and the frame buffer is wiped when the
 * loop ends.
 *
 * Whether the platform echo canceller is good enough for a given device, speaker volume and audio
 * route can only be verified on that device; see [VoiceBargeInDetector].
 */
internal class AndroidBargeInMonitor(context: Context, private val onBargeIn: () -> Unit) {
    private val appContext: Context = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null

    /** RECORD_AUDIO is granted; without it [start] returns false. */
    val microphoneGranted: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The device offers a platform acoustic echo canceller. Without it the assistant's own voice
     * reaches the microphone unreduced and a voice barge-in is much less reliable.
     */
    val echoCancellationAvailable: Boolean
        get() = try { AcousticEchoCanceler.isAvailable() } catch (_: RuntimeException) { false }

    /** The device offers a platform noise suppressor. */
    val noiseSuppressionAvailable: Boolean
        get() = try { NoiseSuppressor.isAvailable() } catch (_: RuntimeException) { false }

    /** The monitor currently holds the microphone. */
    val running: Boolean get() = session?.capturing == true

    /** The echo canceller is attached and enabled for the capture that is running now. */
    val echoCancellationActive: Boolean
        get() = session?.let { it.capturing && it.echoCancellerEnabled } ?: false

    /**
     * Opens the microphone and watches for a barge-in until one is detected or [stop] is called.
     * A monitor that is already running is stopped first.
     *
     * @param playbackLevel normalised level (0..1) of the assistant's audio that is audible now,
     *   for example `{ voiceSignal.level(SystemClock.elapsedRealtime()) }`. It is called on the
     *   monitor thread for every 20 ms frame and must be thread-safe and non-blocking.
     * @return false, with nothing started, without the RECORD_AUDIO permission or when the
     *   microphone cannot be opened (for example because another app holds it).
     */
    @Synchronized fun start(playbackLevel: () -> Float): Boolean {
        stop()
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return false
        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferBytes <= 0) return false
        val record = try {
            // VOICE_COMMUNICATION asks the platform for its echo-cancelled voice capture path.
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBufferBytes, FRAME_BYTES * BUFFER_FRAMES))
        } catch (_: RuntimeException) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            try { record.release() } catch (_: RuntimeException) { }
            return false
        }
        val echoCanceller = attachEchoCanceller(record.audioSessionId)
        val created = Session(record, echoCanceller, attachNoiseSuppressor(record.audioSessionId),
            echoCancellerEnabled = echoCanceller != null)
        val recording = try {
            record.startRecording()
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (_: RuntimeException) {
            false
        }
        if (!recording) {
            created.release()
            return false
        }
        val reader = Thread({ readFrames(created, playbackLevel) }, "continuum-barge-in")
        reader.isDaemon = true
        created.reader = reader
        session = created
        reader.start()
        return true
    }

    /**
     * Stops listening and releases the microphone before it returns. Idempotent; safe on the main
     * thread (the wait for the reader thread is bounded by [READER_JOIN_TIMEOUT_MS]) and inside
     * the barge-in callback. A barge-in that was detected but not yet delivered is dropped.
     */
    @Synchronized fun stop() {
        val current = session ?: return
        session = null
        current.cancelled = true
        current.interruptRead()
        val reader = current.reader
        if (reader != null && reader !== Thread.currentThread()) {
            try {
                reader.join(READER_JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        // Also when the reader did not finish in time: the microphone must be free on return.
        current.release()
    }

    private fun readFrames(current: Session, playbackLevel: () -> Float) {
        val frame = ByteArray(FRAME_BYTES)
        val detector = VoiceBargeInDetector()
        detector.arm()
        var bargeIn = false
        var shortReads = 0
        try {
            while (!current.cancelled) {
                val read = try {
                    current.record.read(frame, 0, FRAME_BYTES)
                } catch (_: RuntimeException) {
                    AudioRecord.ERROR
                }
                if (read < 0) break
                if (read != FRAME_BYTES) {
                    // A stopped or starved capture returns short reads; never spin on them.
                    if (++shortReads > MAX_SHORT_READS) break
                    continue
                }
                shortReads = 0
                val rms = HologramPcmLevel.rms(frame, AudioFormat.ENCODING_PCM_16BIT)
                // A failing level source makes the frame invalid, which can never trigger.
                val level = try { playbackLevel() } catch (_: RuntimeException) { Float.NaN }
                if (detector.onFrame(rms, level, FRAME_MS) == VoiceBargeInDetector.Decision.BARGE_IN) {
                    bargeIn = true
                    break
                }
            }
        } finally {
            frame.fill(0)
            // The microphone is released before the main thread hears about the barge-in.
            current.release()
        }
        if (bargeIn && !current.cancelled) mainHandler.post { deliver(current) }
    }

    private fun deliver(finished: Session) {
        val stillCurrent = synchronized(this) {
            if (session === finished && !finished.cancelled) {
                session = null
                true
            } else false
        }
        if (stillCurrent) onBargeIn()
    }

    private fun attachEchoCanceller(audioSession: Int): AcousticEchoCanceler? {
        var effect: AcousticEchoCanceler? = null
        return try {
            if (!AcousticEchoCanceler.isAvailable()) return null
            effect = AcousticEchoCanceler.create(audioSession) ?: return null
            effect.setEnabled(true)
            if (effect.enabled) effect else { effect.release(); null }
        } catch (_: RuntimeException) {
            try { effect?.release() } catch (_: RuntimeException) { }
            null
        }
    }

    private fun attachNoiseSuppressor(audioSession: Int): NoiseSuppressor? {
        var effect: NoiseSuppressor? = null
        return try {
            if (!NoiseSuppressor.isAvailable()) return null
            effect = NoiseSuppressor.create(audioSession) ?: return null
            effect.setEnabled(true)
            if (effect.enabled) effect else { effect.release(); null }
        } catch (_: RuntimeException) {
            try { effect?.release() } catch (_: RuntimeException) { }
            null
        }
    }

    /** One capture from [start] to its release; a new start never reuses an old capture. */
    private class Session(
        val record: AudioRecord,
        private val echoCanceller: AcousticEchoCanceler?,
        private val noiseSuppressor: NoiseSuppressor?,
        val echoCancellerEnabled: Boolean
    ) {
        @Volatile var cancelled = false
        @Volatile var reader: Thread? = null
        @Volatile private var released = false

        val capturing: Boolean get() = !released

        /** Makes a blocking read on the reader thread return. */
        @Synchronized fun interruptRead() {
            if (released) return
            try { record.stop() } catch (_: RuntimeException) { }
        }

        /** Stops the capture and frees the effects and the microphone; idempotent. */
        @Synchronized fun release() {
            if (released) return
            try { record.stop() } catch (_: RuntimeException) { }
            try { echoCanceller?.release() } catch (_: RuntimeException) { }
            try { noiseSuppressor?.release() } catch (_: RuntimeException) { }
            try { record.release() } catch (_: RuntimeException) { }
            released = true
        }
    }

    companion object {
        /** 16 kHz mono is the common voice capture format and enough for a level measurement. */
        const val SAMPLE_RATE_HZ = 16_000

        /** 20 ms frames: short enough for the timing rules of the detector, cheap to process. */
        const val FRAME_MS = 20

        private const val FRAME_BYTES = SAMPLE_RATE_HZ / 1_000 * FRAME_MS * 2

        /** Capture buffer of at least this many frames, so a briefly delayed reader loses nothing. */
        private const val BUFFER_FRAMES = 8

        /** Upper bound for the wait in [stop]; a read returns within one frame once stopped. */
        const val READER_JOIN_TIMEOUT_MS = 250L

        private const val MAX_SHORT_READS = 25
    }
}
