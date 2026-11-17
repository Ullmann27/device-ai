package at.heinz.deviceai.cockpit

import at.heinz.deviceai.ui.JarvisSequenceEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves the render swap was the fixture: production maps label and enabled state by KIND, never by index. */
class PillGateMappingTest {
    @Test fun onlyGateAllowedPillIsEnabled() {
        val pills = ToolGatePillActionGate.canonicalPills()
        assertEquals(listOf(AnswerPillKind.STUDIE), pills.filter { it.enabled }.map { it.kind })
        assertEquals("web_search", pills.single { it.enabled }.toolName)
    }

    @Test fun labelsFollowKindNotIndex() {
        val labels = JarvisSequenceEffects.pillLabels(ToolGatePillActionGate.canonicalPills())
        assertEquals("Juristische Studie?", labels[AnswerPillKind.STUDIE.ordinal])
        AnswerPillKind.values().filter { it != AnswerPillKind.STUDIE }.forEach {
            assertTrue(labels[it.ordinal], labels[it.ordinal].endsWith("· " + ToolGatePillActionGate.SOURCE_MISSING))
        }
        // reversed order keeps each label on its own kind
        val rev = JarvisSequenceEffects.pillLabels(ToolGatePillActionGate.canonicalPills().reversed())
        assertEquals("Juristische Studie?", rev[AnswerPillKind.values().size - 1 - AnswerPillKind.STUDIE.ordinal])
    }
}
