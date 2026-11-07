package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalSpeechInputPolicyTest {
    @Test fun systemRecognizerCannotBeAssumedOfflineUnderAnyMode(){
        for(mode in InternetAccessMode.values()){
            assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE,LocalSpeechInputPolicy.route(mode,false,false,true))
            assertEquals(LocalSpeechInputPolicy.Route.ON_DEVICE,LocalSpeechInputPolicy.route(mode,false,true,true))
        }
    }
    @Test fun offlineVetoAndOffAlwaysPreventExternalAudioEvenWithOlderGrant(){
        assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE,LocalSpeechInputPolicy.route(InternetAccessMode.OFF,false,false,true,true))
        assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE,LocalSpeechInputPolicy.route(InternetAccessMode.ALWAYS_ON,true,false,true,true))
        assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE,LocalSpeechInputPolicy.route(InternetAccessMode.ASK,false,false,true,false))
    }
    @Test fun microphoneNeedsItsOwnAndroidPermission(){
        assertEquals(LocalSpeechInputPolicy.Route.REQUEST_MICROPHONE,LocalSpeechInputPolicy.route(InternetAccessMode.OFF,false,true,false))
    }
    @Test fun unknownOrNetworkTtsVoiceCannotBeCalledOffline(){
        assertFalse(LocalSpeechInputPolicy.maySpeak(null));assertFalse(LocalSpeechInputPolicy.maySpeak(true))
        assertTrue(LocalSpeechInputPolicy.maySpeak(false))
    }
    @Test fun cancelledSupersededAndRepeatedTranscriptsNeverReturnAsNewInput(){
        val session=EphemeralSpeechTurn();val old=session.start();session.cancel()
        assertNull(session.accept(old,"old command"))
        val a=session.start();val b=session.start();assertNull(session.accept(a,"late"))
        assertEquals("Open Settings",session.accept(b,"Open\nSettings"));assertNull(session.accept(b,"repeat"))
    }
    @Test fun recognizerResultAfterDeadlineIsRejected(){
        var now=0L;val session=EphemeralSpeechTurn{now};val token=session.start();now=31_000_000_000L
        assertNull(session.accept(token,"late result"))
    }
    @Test fun textsStayConsistentWithOnDeviceOnlyBehaviour(){
        val all=LocalSpeechInputPolicy.Failure.values().map(LocalSpeechInputPolicy::message)
        assertTrue(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.NETWORK).contains("kein Cloud-Ersatz"))
        assertTrue(LocalSpeechInputPolicy.message(LocalSpeechInputPolicy.Failure.START_FAILED).contains("kein Cloud-Ersatz"))
        assertFalse(all.any{it.contains("online",true)||it.contains("Cloud-Spracherkennung")})
        assertTrue(LocalSpeechInputPolicy.unavailableReason(30).contains("Android 12"))
        assertTrue(LocalSpeechInputPolicy.unavailableReason(30).contains("keine Audioübertragung"))
        assertFalse(LocalSpeechInputPolicy.unavailableReason(31).contains("Android 12"))
    }
}
