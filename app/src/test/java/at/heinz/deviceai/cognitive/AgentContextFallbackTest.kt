package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContextFallbackTest {
    @Test fun fallbackCannotReceiveOrReprintAnyMemoryRecords() {
        val exposed = AgentContextFallback.render(null)
        assertTrue(exposed.contains("no memory facts are supplied"))
        assertFalse(exposed.contains("RELEVANT MEMORY"))
        assertFalse(exposed.contains("owner-confirmed"))
        assertFalse(exposed.contains("SOURCE:"))
    }

    @Test fun boundedCorrectionIsExplicitlyUnverified() {
        val correction = AgentReflection(
            failure = "A prior response was incomplete.",
            evidence = "Insufficient evidence from tool execution.",
            correction = "Ask for a read-only verification result."
        )
        val text = AgentContextFallback.render(correction)
        assertTrue(text.contains("Unverified previous evaluation correction"))
        assertTrue(text.contains("Ask for a read-only verification result."))
        assertFalse(text.contains("VERIFIED OWNER FACT"))
    }
}
