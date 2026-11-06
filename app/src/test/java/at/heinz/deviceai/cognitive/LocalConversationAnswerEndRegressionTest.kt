package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalConversationAnswerEndRegressionTest {
    @Test fun actualContextContainsTheLastAnswerSection() {
        val context = LocalConversationContext()
        val answer = "ALTER ANFANG. " + "Erklärung. ".repeat(500) + "LETZTER ABSCHNITT: hier fortsetzen."
        context.record("a", "Erkläre den Ablauf.", answer)
        assertTrue("The actual retained response end must reach history",
            context.render("a").contains("LETZTER ABSCHNITT: hier fortsetzen."))
    }
}
