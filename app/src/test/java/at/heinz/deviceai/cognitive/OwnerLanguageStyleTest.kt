package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Code55 field finding F: standard German, consistent "du", no dialect invitation in the local model prompt. */
class OwnerLanguageStyleTest {
    @Test fun theInstructionDemandsStandardGermanAndDuAndForbidsDialect() {
        val s = OwnerLanguageStyle.INSTRUCTION
        assertTrue(s.contains("Standarddeutsch"))
        assertTrue(s.contains("keinen Dialekt"))
        assertTrue(s.contains("„du“"))
        assertTrue(s.contains("Formatvorgaben"))
        assertFalse(s.contains("österreichisch"))
    }

    @Test fun theLocalModelPromptUsesTheSharedInstructionAndNoLongerInvitesDialect() {
        val activity = File("src/main/java/at/heinz/deviceai/AgentActivity.kt").readText()
        assertTrue(activity.contains("OwnerLanguageStyle.INSTRUCTION"))
        assertFalse(activity.contains("österreichischen Sprachgefühl"))
    }
}
