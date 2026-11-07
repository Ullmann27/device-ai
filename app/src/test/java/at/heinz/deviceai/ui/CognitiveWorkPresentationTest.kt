package at.heinz.deviceai.ui
import org.junit.Assert.*
import org.junit.Test
class CognitiveWorkPresentationTest {
    private fun phase(busy:Boolean=false,tool:String?=null,outcome:String?=null)=
        CognitiveWorkPresentation.phase(false,false,false,false,busy,tool,outcome)
    @Test fun modelCompletionCannotBecomeVerifiedSuccess(){
        assertEquals(CognitiveWorkPresentation.Phase.UNCONFIRMED,phase(outcome="MODEL_INTERPRETATION"))
        assertEquals(CognitiveWorkPresentation.Phase.VERIFIED,phase(outcome="ACTION_VERIFIED"))
    }
    @Test fun aLaunchAttemptIsNotASuccess(){
        assertEquals(CognitiveWorkPresentation.Phase.NAVIGATION,phase(true,"launch_app","ACTION_VERIFIED"))
        assertEquals(CognitiveWorkPresentation.Phase.BLOCKED,phase(outcome="BLOCKED"))
        assertEquals(CognitiveWorkPresentation.Phase.CANCELLED,phase(outcome="CANCELLED"))
    }
    @Test fun privateOrHostileToolNameNeverReachesDisplay(){
        val p=phase(true,"PRIVATE SCREEN INSTRUCTIONS")
        assertEquals(CognitiveWorkPresentation.Phase.TOOL,p)
        assertFalse(CognitiveWorkPresentation.summary(p,2000).contains("PRIVATE"))
    }
    @Test fun noInventedPercentageAndMonotonicElapsed(){
        assertFalse(CognitiveWorkPresentation.summary(phase(true),999999).contains("%"))
        assertEquals(0L,CognitiveWorkPresentation.elapsed(100,50))
        assertEquals(100L,CognitiveWorkPresentation.elapsed(100,200))
    }
}
