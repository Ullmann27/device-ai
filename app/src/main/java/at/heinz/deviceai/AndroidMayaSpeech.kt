package at.heinz.deviceai

import android.media.AudioAttributes
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

internal class ElevenLabsPcmRequest(
    private val connection: HttpsURLConnection = URL(ENDPOINT).openConnection() as HttpsURLConnection
) : MayaSpeech.Request {
    companion object {
        const val ENDPOINT = "https://api.elevenlabs.io/v1/text-to-speech/" +
            MayaSpeech.VOICE_ID + "/with-timestamps?output_format=pcm_24000"
        const val MAX_JSON_BYTES = (MayaSpeech.MAX_PCM_BYTES + 2) / 3 * 4 + 128_000
    }
    @Volatile private var closed = false

    override fun synthesize(text: String, key: String, allowed: () -> Boolean): ByteArray =
        synthesizeWithTiming(text, key, allowed).pcm

    override fun synthesizeWithTiming(text: String, key: String, allowed: () -> Boolean): MayaSpeech.Synthesis {
        check(!closed && allowed())
        connection.apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("xi-api-key", key)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
            .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.75)
                .put("style", 0.0).put("use_speaker_boost", true)).toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        check(!closed && allowed())
        connection.outputStream.use { check(!closed && allowed()); it.write(body) }
        check(connection.responseCode == 200) { "ELEVENLABS_UNAVAILABLE" }
        check(connection.contentType?.substringBefore(';')?.trim()?.lowercase() == "application/json")
        val deadline = SystemClock.elapsedRealtime() + 45_000
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                check(!closed && allowed() && SystemClock.elapsedRealtime() < deadline)
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= MAX_JSON_BYTES)
                output.write(buffer, 0, count)
            }
            check(!closed && allowed())
            val json = JSONObject(output.toString("UTF-8"))
            val encoded = json.getString("audio_base64")
            check(encoded.length <= (MayaSpeech.MAX_PCM_BYTES + 2) / 3 * 4)
            val pcm = Base64.getDecoder().decode(encoded)
            check(pcm.isNotEmpty() && pcm.size <= MayaSpeech.MAX_PCM_BYTES && pcm.size % 2 == 0)
            val ranges = runCatching {
                val alignment = json.optJSONObject("alignment") ?: return@runCatching emptyList()
                val characters = alignment.getJSONArray("characters")
                val starts = alignment.getJSONArray("character_start_times_seconds")
                val ends = alignment.getJSONArray("character_end_times_seconds")
                check(characters.length() <= 800 && starts.length() == characters.length() &&
                    ends.length() == characters.length())
                MayaSpeechAlignment.ranges(text,
                    (0 until characters.length()).map { characters.get(it) as String },
                    (0 until starts.length()).map { (starts.get(it) as Number).toDouble() },
                    (0 until ends.length()).map { (ends.get(it) as Number).toDouble() }, pcm.size / 2)
            }.getOrDefault(emptyList())
            check(!closed && allowed())
            MayaSpeech.Synthesis(pcm, ranges)
        }
    }
    override fun close() { closed = true; connection.disconnect() }
}

internal class MayaPcmPlayer(context: Context) : MayaSpeech.Player {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change -> if (change < 0) close() },
            android.os.Handler(android.os.Looper.getMainLooper())).build()
    private var track: AudioTrack? = null
    private var closed = false

    override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit) =
        play(pcm, allowed, started) {}

    override fun play(pcm: ByteArray, allowed: () -> Boolean, started: () -> Unit, progress: (Int) -> Unit) {
        val audio = synchronized(this) {
            check(!closed && allowed())
            check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(MayaSpeech.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size).build().also { track = it }
        }
        synchronized(this) {
            check(!closed && allowed() && audio.state != AudioTrack.STATE_UNINITIALIZED)
            check(audio.write(pcm, 0, pcm.size) == pcm.size)
            check(audio.state == AudioTrack.STATE_INITIALIZED)
            audio.play()
            started()
        }
        val deadline = SystemClock.elapsedRealtime() + pcm.size * 1000L / (MayaSpeech.SAMPLE_RATE * 2) + 5000
        while (allowed()) {
            val frame = synchronized(this) { check(!closed); audio.playbackHeadPosition }
            progress(frame)
            if (frame >= pcm.size / 2) return
            check(SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(20)
        }
    }
    @Synchronized override fun close() {
        closed = true
        track?.let { audio -> runCatching { audio.pause() }; runCatching { audio.release() } }
        track = null
        manager.abandonAudioFocusRequest(focus)
    }
}
