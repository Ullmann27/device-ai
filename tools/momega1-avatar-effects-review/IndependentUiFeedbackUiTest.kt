package at.heinz.deviceai

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.provider.Settings
import at.heinz.deviceai.ui.ContinuumUiFeedback
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Real AudioTrack public constructor/write/play state machine; missing host short-write JNI is controlled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],shadows=[IndependentUiFeedbackUiTest.ShortWriteTrack::class])
class IndependentUiFeedbackUiTest {
    @Implements(AudioTrack::class)
    class ShortWriteTrack:ShadowAudioTrack() {
        @Implementation fun native_write_short(data:ShortArray,offset:Int,count:Int,format:Int,blocking:Boolean):Int {
            writes.add(data.copyOfRange(offset,offset+count));return count
        }
        companion object {val writes=mutableListOf<ShortArray>()}
    }
    private val app get()=RuntimeEnvironment.getApplication()
    private val audio get()=app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Before fun setup() {
        ShortWriteTrack.writes.clear()
        Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        audio.ringerMode=AudioManager.RINGER_MODE_NORMAL;audio.setStreamVolume(AudioManager.STREAM_SYSTEM,5,0)
        shadowOf(audio).setIsMusicActive(false);shadowOf(audio).setIsStreamMute(AudioManager.STREAM_SYSTEM,false)
    }
    private fun enabled()=ContinuumUiFeedback(app).apply {updatePolicy(true,true,false)}
    private fun track(f:ContinuumUiFeedback)=ContinuumUiFeedback::class.java.getDeclaredField("track").apply {isAccessible=true}.get(f) as AudioTrack?
    private fun tick(ms:Long)=ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
    @Test fun musicStreamZeroAndSystemMuteNeverAllocateOrWrite() {
        val f=enabled()
        try {
            shadowOf(audio).setIsMusicActive(true);assertFalse(f.play(ContinuumUiFeedback.Cue.BUTTON))
            shadowOf(audio).setIsMusicActive(false);audio.setStreamVolume(AudioManager.STREAM_SYSTEM,0,0)
            assertFalse(f.play(ContinuumUiFeedback.Cue.BUTTON))
            audio.setStreamVolume(AudioManager.STREAM_SYSTEM,5,0);shadowOf(audio).setIsStreamMute(AudioManager.STREAM_SYSTEM,true)
            assertFalse(f.play(ContinuumUiFeedback.Cue.BUTTON));assertNull(track(f));assertTrue(ShortWriteTrack.writes.isEmpty())
        }finally {f.close()}
    }
    @Test fun everyOwnerVoiceAndForegroundRevocationReleasesExistingTrack() {
        val f=enabled()
        try {
            for(policy in listOf(Triple(false,true,false),Triple(true,false,false),Triple(true,true,true))) {
                f.updatePolicy(true,true,false);tick(200);assertTrue(f.play(ContinuumUiFeedback.Cue.TASK_STARTED))
                val old=checkNotNull(track(f));assertEquals(AudioTrack.PLAYSTATE_PLAYING,old.playState)
                f.updatePolicy(policy.first,policy.second,policy.third)
                assertNull(track(f));assertEquals(AudioTrack.STATE_UNINITIALIZED,old.state)
            }
        }finally {f.close()}
    }
    @Test fun exactCooldownBoundaryAndReplacementRetainOnlyOneTrack() {
        val f=enabled()
        try {
            assertTrue(f.play(ContinuumUiFeedback.Cue.BUTTON));val old=checkNotNull(track(f))
            tick(159);assertFalse(f.play(ContinuumUiFeedback.Cue.TASK_STARTED));assertSame(old,track(f))
            tick(1);assertTrue(f.play(ContinuumUiFeedback.Cue.TASK_STARTED));assertNotSame(old,track(f))
            assertEquals(AudioTrack.STATE_UNINITIALIZED,old.state);assertEquals(2,ShortWriteTrack.writes.size)
        }finally {f.close()}
    }
    @Test fun systemOptOutOnNextPlayReleasesPreviousAudioAndCloseCannotRevive() {
        val f=enabled();assertTrue(f.play(ContinuumUiFeedback.Cue.BUTTON));val old=checkNotNull(track(f))
        Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,0)
        assertFalse(f.play(ContinuumUiFeedback.Cue.BUTTON));assertNull(track(f));assertEquals(AudioTrack.STATE_UNINITIALIZED,old.state)
        f.close();f.updatePolicy(true,true,false);Settings.System.putInt(app.contentResolver,Settings.System.SOUND_EFFECTS_ENABLED,1)
        tick(200);assertFalse(f.play(ContinuumUiFeedback.Cue.BUTTON));assertNull(track(f))
    }
    @Test fun frameworkReceivesBoundedPcmWithoutFocusOrVolumeMutation() {
        val f=enabled();val volumes=(0..5).map {audio.getStreamVolume(it)}
        try {
            assertTrue(f.play(ContinuumUiFeedback.Cue.BUTTON));tick(160);assertTrue(f.play(ContinuumUiFeedback.Cue.TASK_STARTED))
            assertEquals(listOf(640,960),ShortWriteTrack.writes.map {it.size})
            for(pcm in ShortWriteTrack.writes) {
                assertEquals(0,pcm.first().toInt());assertEquals(0,pcm.last().toInt())
                assertTrue(pcm.any {it.toInt()!=0});assertTrue(pcm.all {kotlin.math.abs(it.toInt())<=1150})
            }
            assertEquals(volumes,(0..5).map {audio.getStreamVolume(it)})
            assertNull(shadowOf(audio).lastAudioFocusRequest);assertNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
            assertEquals(16000,track(f)!!.sampleRate);assertEquals(1,track(f)!!.channelCount)
        }finally {f.close()}
    }
}
