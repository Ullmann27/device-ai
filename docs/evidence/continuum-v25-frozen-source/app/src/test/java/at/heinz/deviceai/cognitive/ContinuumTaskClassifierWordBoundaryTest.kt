package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumTaskClassifierWordBoundaryTest {
    @Test
    fun substringsInsideOtherWordsDoNotTriggerDeviceOrPlanning() {
        val a = ContinuumTaskClassifier.classify("What would happen if we apply this mapping?")
        assertEquals(IntelligenceTaskClass.GENERAL, a.taskClass)
        assertFalse(a.expectedToolUse)

        val b = ContinuumTaskClassifier.classify("Give me an explanation of entropy")
        assertEquals(IntelligenceTaskClass.GENERAL, b.taskClass)
    }

    @Test
    fun realDeviceRequestStillDetected() {
        val r = ContinuumTaskClassifier.classify("öffne die App Einstellungen")
        assertEquals(IntelligenceTaskClass.DEVICE, r.taskClass)
        assertTrue(r.expectedToolUse)
    }

    @Test
    fun stemKeywordsStillMatchWordPrefixes() {
        assertEquals(IntelligenceTaskClass.RESEARCH, ContinuumTaskClassifier.classify("Bitte recherchiere das").taskClass)
        assertEquals(IntelligenceTaskClass.VERIFICATION, ContinuumTaskClassifier.classify("Prüfe diese Aussage").taskClass)
    }

    @Test
    fun multiWordNeedlesStillMatch() {
        assertEquals(IntelligenceTaskClass.MEMORY, ContinuumTaskClassifier.classify("Weißt du noch was wir sagten?").taskClass)
    }
}
