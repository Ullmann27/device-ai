package at.heinz.deviceai.jarvis

/** Host regression for callback/session isolation, without Android SDK or model dependencies. */
object StaleRecognizerSessionHarness {
    @JvmStatic
    fun main(args: Array<String>) {
        val knowledge = JarvisVoiceConversationPolicy.evaluate("Was ist eine Erinnerung?")
        val risky = JarvisVoiceConversationPolicy.evaluate("Schalte den Herd aus")
        val empty = JarvisVoiceConversationPolicy.evaluate(" ")
        data class Case(
            val name: String,
            val decision: JarvisVoiceDecision,
            val busy: Boolean = false,
            val shutdown: Boolean = false,
            val alive: Boolean = true,
            val started: String? = "chat-a",
            val current: String = "chat-a",
            val expected: JarvisVoiceDispatch
        )
        val scenarios = listOf(
            Case("unbound knowledge", knowledge, started = null, expected = JarvisVoiceDispatch.IGNORE),
            Case("old chat knowledge", knowledge, current = "chat-b", expected = JarvisVoiceDispatch.IGNORE),
            Case("old chat risky", risky, current = "chat-b", expected = JarvisVoiceDispatch.IGNORE),
            Case("current chat knowledge", knowledge, expected = JarvisVoiceDispatch.AUTO_DIALOGUE),
            Case("current chat risky", risky, expected = JarvisVoiceDispatch.DRAFT),
            Case("current chat busy", knowledge, busy = true, expected = JarvisVoiceDispatch.DRAFT),
            Case("current chat shutdown", knowledge, shutdown = true, expected = JarvisVoiceDispatch.DRAFT),
            Case("current chat dead", knowledge, alive = false, expected = JarvisVoiceDispatch.DRAFT),
            Case("blank current chat", empty, expected = JarvisVoiceDispatch.IGNORE),
            Case("blank old chat", empty, current = "chat-b", expected = JarvisVoiceDispatch.IGNORE),
            Case("both session ids same nonempty", knowledge, started = "other", current = "other", expected = JarvisVoiceDispatch.AUTO_DIALOGUE),
            Case("empty old session id", knowledge, started = "", expected = JarvisVoiceDispatch.IGNORE)
        )
        for (scenario in scenarios) {
            val actual = JarvisVoiceDispatchGate.decide(
                scenario.decision, scenario.busy, scenario.shutdown,
                scenario.alive, scenario.started, scenario.current
            )
            check(actual == scenario.expected) {
                "FAIL " + scenario.name + ": expected " + scenario.expected + ", got " + actual
            }
        }
        println("VOICE1_STALE_CALLBACK_HARNESS PASS " + scenarios.size + "/" + scenarios.size)
    }
}
