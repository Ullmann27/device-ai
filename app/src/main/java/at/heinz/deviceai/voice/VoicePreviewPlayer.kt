package at.heinz.deviceai.voice

import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Plays a voice's hosted ElevenLabs sample (`preview_url` of GET /v2/voices). Nothing is synthesised and no API key
 * is sent: the URL is a plain HTTPS file. Only one sample plays at a time; [stop] ends it at once.
 * Whether ElevenLabs charges for fetching a sample is not stated in its documentation (see the research note).
 */
internal class VoicePreviewPlayer {
    private var player: MediaPlayer? = null

    /** Starts [url]; [done] runs on the main thread with true when it played to the end. */
    fun play(url: String, done: (Boolean) -> Unit): Boolean {
        stop()
        val safe = ElevenLabsVoiceCatalog.safePreviewUrl(url) ?: return false
        return runCatching {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(safe)
            media.setOnPreparedListener { if (player === it) it.start() }
            media.setOnCompletionListener { if (player === it) { stop(); done(true) } }
            media.setOnErrorListener { mp, _, _ -> if (player === mp) { stop(); done(false) }; true }
            media.prepareAsync()
            true
        }.getOrElse { stop(); false }
    }

    val playing: Boolean get() = player != null

    fun stop() {
        val media = player ?: return
        player = null
        runCatching { media.stop() }
        runCatching { media.release() }
    }
}
