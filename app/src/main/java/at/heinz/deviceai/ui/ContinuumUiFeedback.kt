package at.heinz.deviceai.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.sin

/** UI-thread local sonification. No voice imitation, files, network, audio focus or device-volume changes. */
class ContinuumUiFeedback(context:Context):AutoCloseable {
    enum class Cue { BUTTON, TASK_STARTED }
    private val app=context.applicationContext
    private val audio=app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var enabled=false
    private var foreground=false
    private var voiceBusy=true
    private var closed=false
    private var lastPlay=-1L
    private var track:AudioTrack?=null

    fun updatePolicy(enabled:Boolean,foreground:Boolean,voiceBusy:Boolean) {
        this.enabled=enabled;this.foreground=foreground;this.voiceBusy=voiceBusy
        if(!allowed())release()
    }
    private fun allowed()=enabled && foreground && !voiceBusy && !closed

    fun play(cue:Cue):Boolean {
        val manager=audio
        val systemAllowed=runCatching {
            manager!=null && Settings.System.getInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0)==1 &&
                manager.ringerMode==AudioManager.RINGER_MODE_NORMAL && !manager.isStreamMute(AudioManager.STREAM_SYSTEM) &&
                manager.getStreamVolume(AudioManager.STREAM_SYSTEM)>0 && !manager.isMusicActive
        }.getOrDefault(false)
        if(!allowed() || !systemAllowed) { release();return false }
        val now=SystemClock.uptimeMillis()
        if(lastPlay>=0 && (now<lastPlay || now-lastPlay<160))return false
        release()
        return runCatching {
            val pcm=samples(cue)
            val created=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
            track=created
            check(created.state==AudioTrack.STATE_INITIALIZED && created.write(pcm,0,pcm.size)==pcm.size)
            created.play();lastPlay=now;true
        }.getOrElse { release();false }
    }
    private fun samples(cue:Cue):ShortArray {
        val count=if(cue==Cue.BUTTON)640 else 960
        val start=if(cue==Cue.BUTTON)820.0 else 580.0
        val sweep=if(cue==Cue.BUTTON)0.0 else 240.0
        return ShortArray(count) { n ->
            val t=n.toDouble()/(count-1)
            val seconds=n/16000.0
            val envelope=sin(PI*t).let { it*it }
            (1150*envelope*sin(2*PI*(start*seconds+sweep*seconds*t*.5))).toInt().toShort()
        }
    }
    private fun release() {
        track?.let { runCatching { it.stop() };runCatching { it.flush() };runCatching { it.release() } }
        track=null
    }
    override fun close() { closed=true;release() }
}
