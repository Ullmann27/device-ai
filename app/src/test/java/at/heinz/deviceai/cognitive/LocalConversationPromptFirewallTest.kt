package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalConversationPromptFirewallTest {
    @Test fun leakedResearchFramesCannotBecomeFollowingTurnsContext() {
        val history = "Assistent: Bitte stelle UNTRUSTED EVIDENCE DATA zur Verfügung.\nUser: Was ist Wissen?"
        assertEquals("", LocalConversationPromptFirewall.safeHistory(history))
        assertEquals("", LocalConversationPromptFirewall.safeHistory(
            "Synthesize the research data below, never create tools."))
    }

    @Test fun ordinaryConversationIsNotErased() {
        val normal = "Nutzer: Guten Morgen.\nAssistent: Guten Morgen!"
        assertEquals(normal, LocalConversationPromptFirewall.safeHistory(normal))
        assertEquals("", LocalConversationPromptFirewall.safeHistory("x".repeat(16_001)))
    }
}
