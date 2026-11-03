package at.heinz.deviceai

import android.content.Context
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Looper
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowAudioTrack
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],shadows=[Pcm16FeedbackAudioTrackShadow::class])
class ContinuumUiFeedbackTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun type():Class<*> = runCatching { Class.forName("at.heinz.deviceai.ui.ContinuumUiFeedback") }.getOrNull()
        .also { assertNotNull("Local lifecycle-bound feedback is missing",it) }!!
    private fun feedback()=type().getConstructor(Context::class.java).newInstance(app)
    private fun policy(f:Any,enabled:Boolean=true,foreground:Boolean=true,voice:Boolean=false) {
        type().getMethod("updatePolicy",Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,Boolean::class.javaPrimitiveType)
            .invoke(f,enabled,foreground,voice)
    }
    private fun play(f:Any):Boolean {
        val cue=Class.forName(type().name+"\$Cue").enumConstants.first()
        return type().methods.single { it.name=="play" }.invoke(f,cue) as Boolean
    }
    private fun track(f:Any):AudioTrack?=type().getDeclaredField("track").apply { isAccessible=true }.get(f) as AudioTrack?
    private fun close(f:Any) { type().getMethod("close").invoke(f) }
    private fun system():AudioManager {
        Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        return (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            ringerMode=AudioManager.RINGER_MODE_NORMAL;setStreamVolume(AudioManager.STREAM_SYSTEM,5,0)
        }
    }
    @Test fun generatedChirpsAreShortQuietFinitePcmWithNoAssets() {
        val f=feedback()
        try {
            val cueType=Class.forName(type().name+"\$Cue")
            val samples=type().getDeclaredMethod("samples",cueType).apply { isAccessible=true }
            for(cue in cueType.enumConstants) {
                val pcm=samples.invoke(f,cue) as ShortArray
                assertTrue(pcm.size in 1..960)
                assertEquals(0,pcm.first().toInt());assertEquals(0,pcm.last().toInt())
                assertTrue(pcm.any { it.toInt()!=0 })
                assertTrue(pcm.all { kotlin.math.abs(it.toInt())<=1500 })
            }
        } finally { close(f) }
    }
    @Test fun defaultMuteVoiceAndBackgroundNeverPlayOrRetainResources() {
        system();val f=feedback()
        try {
            assertFalse(play(f));assertNull(track(f))
            policy(f,voice=true);assertFalse(play(f));assertNull(track(f))
            policy(f,foreground=false);assertFalse(play(f));assertNull(track(f))
            policy(f,enabled=false);assertFalse(play(f));assertNull(track(f))
        } finally { close(f) }
    }
    @Test fun actualBoundedTrackPlaysWithoutChangingSystemVolumeThenReleasesOnVoiceOrClose() {
        val audio=system();val volume=audio.getStreamVolume(AudioManager.STREAM_SYSTEM);val f=feedback()
        policy(f)
        assertTrue(play(f));val first=track(f)!!
        assertEquals(AudioTrack.PLAYSTATE_PLAYING,first.playState)
        assertEquals(volume,audio.getStreamVolume(AudioManager.STREAM_SYSTEM))
        assertFalse("Button bursts are rate limited",play(f))
        policy(f,voice=true);assertNull(track(f));assertEquals(AudioTrack.STATE_UNINITIALIZED,first.state)
        shadowOf(Looper.getMainLooper()).idleFor(200,TimeUnit.MILLISECONDS)
        policy(f);assertTrue(play(f));val second=track(f)!!
        close(f);assertNull(track(f));assertEquals(AudioTrack.STATE_UNINITIALIZED,second.state)
        policy(f);assertFalse("A destroyed helper cannot revive playback",play(f))
    }
    @Test fun systemSoundToggleAndSilentRingerCannotBeBypassed() {
        val audio=system();val f=feedback();policy(f)
        try {
            Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0);assertFalse(play(f))
            Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
            audio.ringerMode=AudioManager.RINGER_MODE_SILENT;assertFalse(play(f));assertNull(track(f))
        } finally { close(f) }
    }
}

/**
 * Robolectric 4.14.1 lacks only native_write_short. Bridge that native seam to its
 * existing byte-write shadow; public Android write/state/play/release stay real.
 * Host contract only: this does not emulate a speaker or prove audible playback.
 */
@Implements(AudioTrack::class)
class Pcm16FeedbackAudioTrackShadow:ShadowAudioTrack() {
    @Implementation
    protected fun native_write_short(pcm:ShortArray,offset:Int,count:Int,format:Int,blocking:Boolean):Int {
        require(format==AudioFormat.ENCODING_PCM_16BIT && offset>=0 && count in 0..960 && offset+count<=pcm.size)
        val bytes=ByteArray(count*2)
        repeat(count) { n ->
            bytes[n*2]=(pcm[offset+n].toInt() and 255).toByte()
            bytes[n*2+1]=((pcm[offset+n].toInt() shr 8) and 255).toByte()
        }
        return super.native_write_byte(bytes,0,bytes.size,format,blocking)/2
    }
}
