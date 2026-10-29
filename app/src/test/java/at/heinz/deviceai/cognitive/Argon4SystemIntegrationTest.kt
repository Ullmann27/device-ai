package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class Argon4SystemIntegrationTest {
    @Test fun existingConversationResolvesOnlyInShadowAndNeverChangesModelContext() {
        val enabled = LocalConversationContext()
        val disabled = LocalConversationContext(Argon4ConversationShadow(Argon4ShadowMode.OFF))
        for (context in listOf(enabled, disabled)) {
            assertNull(context.inspectRequest("chat", "Meine Schwester Anna wohnt in Linz."))
            context.record("chat", "Hallo", "Guten Tag")
            assertNull(context.inspectRequest("chat", "Wo wohnt sie?"))
        }
        assertEquals(Argon4ShadowStatus.OBSERVED, enabled.argonShadowReport().status)
        assertEquals(1, enabled.argonShadowReport().bindings)
        assertEquals(Argon4ShadowStatus.OFF, disabled.argonShadowReport().status)
        assertEquals(disabled.render("chat"), enabled.render("chat"))
        assertFalse(enabled.argonShadowReport().toString().contains("Anna"))
        enabled.inspectRequest("other", "Wo wohnt sie?")
        assertTrue(enabled.argonShadowReport().wouldClarify)
        enabled.clear()
        assertEquals(Argon4ShadowStatus.NO_TURN, enabled.argonShadowReport().status)
    }

    @Test fun observerFailureCannotChangeNormalDiscourseControlAndLifetimeIsBounded() {
        val c = LocalConversationContext(Argon4ConversationShadow(kernelFactory = { error("private") }))
        assertEquals(ContextDirectiveKind.SCOPE_UPDATED,
            c.inspectRequest("one", "Ich meinte Projekt Atlas.")!!.kind)
        assertEquals(Argon4ShadowStatus.FAILED, c.argonShadowReport().status)
        assertFalse(c.argonShadowReport().toString().contains("private"))
        var created = 0
        val s = Argon4ConversationShadow(kernelFactory = {
            created++; at.heinz.deviceai.research.argon4.DialogStateKernel(it)
        })
        repeat(129) { s.observeUser("bounded", "Wie geht es dir?") }
        assertEquals(3, created)
        assertTrue(s.report().entities <= 12)
    }

    private fun hits() = listOf(
        ContinuumMemoryHit("f1", "Anna ist meine Schwester.", "Bestätigte Nutzererinnerung", null, .9, .8),
        ContinuumMemoryHit("f2", "Meine Tochter heißt Klara.", "Bestätigte Nutzererinnerung", null, .9, .8))
    private fun turn(h: List<ContinuumMemoryHit>) = ContinuumDialogueTurn(1, "Wer?", "Wer?", "baseline",
        "test", "0".repeat(64), h.map { it.id }, h.isNotEmpty())

    @Test fun exactAuthorizedEvidenceCanDeriveOnlyAnUnconfirmedDiagnosticAndRevocationDropsIt() {
        val h = hits()
        assertEquals(1, Argon4EvidenceShadow.observe(turn(h), h).derivedUnconfirmed)
        val remaining = h.take(1)
        assertEquals(0, Argon4EvidenceShadow.observe(turn(remaining), remaining).derivedUnconfirmed)
        assertEquals(Argon4ShadowStatus.NO_EVIDENCE, Argon4EvidenceShadow.observe(turn(emptyList()), emptyList()).status)
        assertEquals(Argon4ShadowStatus.REJECTED_EVIDENCE,
            Argon4EvidenceShadow.observe(turn(h), h.reversed()).status)
        val sources = h.map { it.copy(kind = "Unbestätigte Quellenbeobachtung") }
        assertEquals(0, Argon4EvidenceShadow.observe(turn(sources), sources).derivedUnconfirmed)
    }

    @Test fun realCoreShadowAndOffKeepAnswersPromptsEvidenceAndWeightsIdentical() {
        val dir = Files.createTempDirectory("argon-runtime").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Mira wohnt in Linz.")
            val hash = core.modelHash()
            val off = ContinuumCognitiveRuntime(dir, core, "off",
                argonShadowMode = Argon4ShadowMode.OFF).reply("Wo wohnt Mira?")
            val shadow = ContinuumCognitiveRuntime(dir, core, "shadow").reply("Wo wohnt Mira?")
            assertEquals(off.answer, shadow.answer)
            assertEquals(off.localModelPrompt, shadow.localModelPrompt)
            assertEquals(off.evidenceHits, shadow.evidenceHits)
            assertEquals(hash, core.modelHash())
            assertEquals(Argon4ShadowStatus.OBSERVED, shadow.argonShadow.status)
            assertEquals(Argon4ShadowStatus.OFF, off.argonShadow.status)
            core.revokeOwnerFact("Mira wohnt in Linz.")
            val revoked = ContinuumCognitiveRuntime(dir, core, "shadow").reply("Wo wohnt Mira?")
            assertFalse(revoked.grounded)
            assertEquals(0, revoked.argonShadow.authorizedFacts)
        } finally { dir.deleteRecursively() }
    }

    @Test fun researchRouterCannotSpendExtraModelCallsOrAuthorizeTools() {
        var enabledCalls = 0; var disabledCalls = 0
        val enabled = ChatReasoningRouter(ContinuumReasoningAdapter {
            enabledCalls++; ReasoningPassResult("fixture", "advice", 1)
        }, {})
        val disabled = ChatReasoningRouter(ContinuumReasoningAdapter {
            disabledCalls++; ReasoningPassResult("fixture", "advice", 1)
        }, {}, argonShadowMode = Argon4ShadowMode.OFF)
        val task = "Vergleiche diese Konzepte mit Widerspruch und Bedingung. " + "x".repeat(700)
        assertEquals(disabled.advise("d", task, "ctx", modelReady = true),
            enabled.advise("e", task, "ctx", modelReady = true))
        assertEquals(disabledCalls, enabledCalls)
        assertNotNull(enabled.argonShadowChoice)
        assertNull(disabled.argonShadowChoice)
        val before = enabledCalls
        assertNull(enabled.advise("tool", "Öffne die App und zeige den Akku", "ctx", modelReady = true))
        assertEquals(before, enabledCalls)
    }
}
