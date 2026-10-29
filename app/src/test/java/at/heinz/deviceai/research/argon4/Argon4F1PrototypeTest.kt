package at.heinz.deviceai.research.argon4

import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Action
import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.EvidenceDecision
import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Signals
import at.heinz.deviceai.research.argon4.DialogStateKernel.Decision
import at.heinz.deviceai.research.argon4.JustifiedRelationStore.Triple
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** ARGON4 F1 prototypes on own synthetic sentences (no owner data). */
class Argon4F1PrototypeTest {
    private val day = LocalDate.of(2026, 10, 29)   // a Thursday
    private fun kernel() = DialogStateKernel("argon-session", { day })

    // ---- DialogStateKernel -----------------------------------------------------------------------------------

    @Test fun pronounsFollowGenderAndRecencyAcrossMoreThanFourTurns() {
        val k = kernel()
        k.observeUser("Meine Schwester Anna wohnt in Linz.")
        k.observeUser("Mein Kollege Max fährt morgen nach Graz.")
        repeat(5) { k.observeUser("Wie spät ist es eigentlich?") }      // fillers push Anna out of a four-pair window
        val r = k.observeUser("Wo wohnt sie?")
        assertEquals(Decision.RESOLVED, r.decision)
        assertEquals("anna", r.bindings.single().entity)
        assertEquals("Wo wohnt Anna?", r.rewritten)
        assertEquals("max", k.observeUser("Und wohin fährt er?").bindings.single().entity)
    }

    @Test fun sameGenderInOneSentenceAsksBackAndNoReferentAsksBack() {
        val k = kernel()
        assertEquals(Decision.CLARIFY, k.observeUser("Was macht sie gerade?").decision)
        k.observeUser("Meine Schwester Anna und meine Nachbarin Rita waren da.")
        val r = k.observeUser("Was hat sie gesagt?")
        assertEquals(Decision.CLARIFY, r.decision)
        assertTrue(r.reason.startsWith("AMBIGUOUS:sie"))
    }

    @Test fun anOwnerCorrectionRebindsAndExcludesTheWrongReferent() {
        val k = kernel()
        k.observeUser("Meine Schwester Anna wohnt in Linz.")
        k.observeUser("Meine Nachbarin Rita wohnt in Wels.")
        val first = k.observeUser("Wo arbeitet sie?")
        assertEquals("rita", first.bindings.single().entity)
        val corrected = k.observeUser("Nein, ich meinte Anna.")
        assertTrue(corrected.correction)
        assertEquals("Wo arbeitet Anna?", corrected.rewritten)
        // "sie" may still mean Anna later, but never Rita again in this session
        k.observeUser("Meine Nachbarin Rita hat einen Hund.")
        assertEquals("anna", k.observeUser("Und wann kommt sie?").bindings.single().entity)
        assertTrue(k.render().contains("sie≠rita"))
    }

    @Test fun aCorrectionWithoutAPriorQuestionAsksBack() {
        assertEquals(Decision.CLARIFY, kernel().observeUser("Nein, ich meinte Anna.").decision)
    }

    @Test fun timeExpressionsBecomeAbsoluteDatesAndCarryIntoFollowUps() {
        val k = kernel()
        assertEquals(LocalDate.of(2026, 10, 28), k.observeUser("Was habe ich gestern gemacht?").temporal!!.from)
        assertEquals(LocalDate.of(2026, 10, 28), k.observeUser("Und danach?").temporal!!.from)
        val week = k.parseTime("Was war letzte Woche?")!!
        assertEquals(LocalDate.of(2026, 10, 19), week.from); assertEquals(LocalDate.of(2026, 10, 25), week.to)
        assertEquals(LocalDate.of(2026, 10, 26), k.parseTime("Was war am Montag?")!!.from)
        assertEquals(LocalDate.of(2026, 11, 2), k.parseTime("Was werde ich am Montag tun?")!!.from)
        assertEquals(LocalDate.of(2026, 10, 24), k.parseTime("Das war vor fünf Tagen.")!!.from)
        assertEquals(LocalDate.of(2026, 3, 1), k.parseTime("Was war im März?")!!.from)
        assertEquals(LocalDate.of(2025, 12, 1), k.parseTime("Was war im Dezember?")!!.from)
        assertNull(k.parseTime("Wie geht es dir?"))
    }

    @Test fun placesAndThingsUseTheirOwnReferences() {
        val k = kernel()
        k.observeUser("Ich war in Gmunden und habe den Ofen repariert.")
        assertEquals("gmunden", k.observeUser("Wie war das Wetter dort?").bindings.single().entity)
        assertEquals("ofen", k.observeUser("Funktioniert er jetzt?").bindings.single().entity)
    }

    @Test fun aTopicSwitchForgetsAllReferentsAndTheStateStaysBounded() {
        val k = DialogStateKernel("s2", { day }, maxEntities = 3)
        k.observeUser("Meine Schwester Anna, meine Tochter Lea und meine Nachbarin Rita und meine Kollegin Eva kamen.")
        assertTrue(k.entities().size <= 3)
        k.observeUser("Anderes Thema.")
        assertTrue(k.entities().isEmpty())
        assertEquals(Decision.CLARIFY, k.observeUser("Was macht sie?").decision)
        assertTrue(k.render(80).length <= 80)
    }

    @Test fun politeSieAddressesTheAssistantNotAPerson() {
        val k = kernel()
        k.observeUser("Meine Schwester Anna kommt.")
        assertEquals(Decision.PASS, k.observeUser("Können Sie mir helfen?").decision)
    }

    // ---- ComputeAllocationPolicy ------------------------------------------------------------------------------

    private fun sig(e: EvidenceDecision, cov: Double, personal: Boolean, ready: Boolean = true, net: Boolean = false,
                    flags: Int = 0, unresolved: Boolean = false, tool: Boolean = false, calls: Int = 4) =
        Signals(e, cov, flags, unresolved, false, tool, personal, ready, net, remainingModelCalls = calls)

    @Test fun groundedEvidenceIsAnsweredDirectlyWithoutAnyModelCall() {
        val c = ComputeAllocationPolicy().choose(sig(EvidenceDecision.QUERY, 1.0, personal = true))
        assertEquals(Action.DIRECT_EVIDENCE, c.action)
    }

    @Test fun anUnsupportedPersonalQuestionIsNotHandedToTheModel() {
        val c = ComputeAllocationPolicy().choose(sig(EvidenceDecision.ABSTAIN, 0.0, personal = true))
        assertEquals(Action.ABSTAIN, c.action)
        assertTrue(c.ranking.first { it.first == Action.MODEL_ONE_PASS }.second < c.expectedUtility)
    }

    @Test fun anUnresolvedReferenceAsksBackAndBlocksModelPasses() {
        val c = ComputeAllocationPolicy().choose(sig(EvidenceDecision.NONE, 0.0, personal = true, unresolved = true))
        assertEquals(Action.CLARIFY, c.action)
        assertFalse(ComputeAllocationPolicy().feasible(Action.MODEL_DEEP, sig(EvidenceDecision.NONE, 0.0, false, unresolved = true)))
    }

    @Test fun aGeneralQuestionUsesTheModelOrConsentedResearchButNeverResearchWithoutConsent() {
        val p = ComputeAllocationPolicy()
        val offline = p.choose(sig(EvidenceDecision.NONE, 0.0, personal = false))
        assertTrue(offline.action in setOf(Action.MODEL_ONE_PASS, Action.MODEL_VERIFY, Action.MODEL_DEEP))
        assertFalse(p.feasible(Action.RESEARCH, sig(EvidenceDecision.NONE, 0.0, personal = false, net = false)))
        assertFalse(p.feasible(Action.RESEARCH, sig(EvidenceDecision.NONE, 0.0, personal = true, net = true)))
        assertEquals(Action.ABSTAIN, p.choose(sig(EvidenceDecision.NONE, 0.0, personal = false, ready = false)).action)
    }

    @Test fun claimCheckFlagsBuyExactlyOneVerificationPass() {
        val c = ComputeAllocationPolicy().choose(sig(EvidenceDecision.QUERY, 1.0, personal = false, flags = 2))
        assertEquals(Action.MODEL_VERIFY, c.action)
        assertFalse(ComputeAllocationPolicy().feasible(Action.MODEL_VERIFY, sig(EvidenceDecision.QUERY, 1.0, false, flags = 2, calls = 1)))
    }

    @Test fun verifiedOutcomesMoveTheDecisionAndThrottlingNeverBuysMoreCompute() {
        val p = ComputeAllocationPolicy()
        val s = sig(EvidenceDecision.NONE, 0.0, personal = false)
        val before = p.choose(s).action
        repeat(40) { p.observe(Action.MODEL_ONE_PASS, s, correct = false, confidentlyWrong = true) }
        repeat(40) { p.observe(Action.MODEL_VERIFY, s, correct = false, confidentlyWrong = true) }
        repeat(40) { p.observe(Action.MODEL_DEEP, s, correct = false, confidentlyWrong = true) }
        assertTrue(before != Action.ABSTAIN)
        assertEquals(Action.ABSTAIN, p.choose(s).action)
        val cool = ComputeAllocationPolicy().choose(sig(EvidenceDecision.NONE, 0.0, personal = false))
        val hot = ComputeAllocationPolicy().choose(s.copy(throttled = true, lowBattery = true))
        val cost = ComputeAllocationPolicy.Costs().seconds
        assertTrue(cost.getValue(hot.action) <= cost.getValue(cool.action))
    }

    @Test fun invalidSignalsAreRejected() {
        try { sig(EvidenceDecision.QUERY, 1.5, true); fail() } catch (_: IllegalArgumentException) {}
        try { sig(EvidenceDecision.QUERY, 1.0, true, calls = 17); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun theExistingRouterBuysFourPassesForALongGroundedQuestion() {
        val long = "Erinnerst du dich, wo meine Schwester wohnt? " + "Ich frage, weil ich die Fahrt plane und mehrere Bedingungen habe. ".repeat(10)
        assertEquals(4, ExistingRouterControl.extraModelCalls(long))
        assertEquals(Action.DIRECT_EVIDENCE, ComputeAllocationPolicy().choose(sig(EvidenceDecision.QUERY, 1.0, personal = true)).action)
    }

    // ---- JustifiedRelationStore -------------------------------------------------------------------------------

    private fun store(vararg facts: Pair<String, String>) = JustifiedRelationStore().apply {
        JustifiedRelationStore.kinshipRules().forEach { addRule(it) }
        facts.forEach { (id, text) -> assertFact(id, JustifiedRelationStore.parse(text)!!) }
    }

    @Test fun twoStepRelationsAreDerivedWithTheirReasonAndNeverConfirmed() {
        val s = store("f1" to "Anna ist meine Schwester.", "f2" to "Meine Tochter heißt Klara.")
        val aunt = s.query("Anna", "tante_von", "Klara").single()
        assertEquals("DERIVED_UNCONFIRMED", aunt.status)
        assertEquals(setOf(setOf("f1", "f2")), aunt.witnesses)
        assertTrue(s.explain(aunt.triple)!!.contains("abgeleitet, nicht bestätigt"))
    }

    @Test fun revokingASupportRetractsTheConclusionButAnAlternativeWitnessKeepsIt() {
        val s = store("f1" to "Anna wohnt in Linz.", "f2" to "Linz liegt in Oberösterreich.", "f3" to "Anna wohnt in Wels.",
            "f4" to "Wels liegt in Oberösterreich.")
        val region = Triple("Anna", "wohnt_in", "Oberösterreich")
        assertEquals(setOf(setOf("f1", "f2"), setOf("f3", "f4")), s.query("Anna", "wohnt_in", "Oberösterreich").single().witnesses)
        s.revoke("f1")
        assertEquals(setOf(setOf("f3", "f4")), s.query(region.s, region.p, region.o).single().witnesses)
        s.revoke("f4")
        assertTrue(s.query(region.s, region.p, region.o).isEmpty())
    }

    @Test fun chainsAreBoundedAndContradictionsAreReportedNotResolved() {
        val s = store("f1" to "Anna wohnt in Linz.", "f2" to "Linz liegt in Oberösterreich.", "f3" to "Oberösterreich liegt in Österreich.",
            "f4" to "Anna wohnt in Wien.")
        assertEquals(setOf("f1", "f2", "f3"), s.query("Anna", "wohnt_in", "Österreich").single().witnesses.single())
        val c = s.contradictions(setOf("wohnt_in")).single()
        assertEquals(setOf("Linz", "Wien"), c.values)
        // the store answers both; it never deletes one owner fact in favour of another
        assertEquals(2, s.query("Anna", "wohnt_in").count { it.triple.o == "Oberösterreich" || it.triple.o == "Österreich" })
    }

    @Test fun siblingsAreNotDerivedFromThemselvesAndUnparsableTextIsIgnored() {
        val s = store("f1" to "Meine Tochter heißt Klara.", "f2" to "Mein Sohn heißt Paul.")
        assertTrue(s.query("Klara", "geschwister_von", "Klara").isEmpty())
        assertEquals(1, s.query("Klara", "geschwister_von", "Paul").size)
        assertNull(JustifiedRelationStore.parse("Anna ist vielleicht meine Schwester."))
    }

    @Test fun unsafeRulesAreRejected() {
        try {
            JustifiedRelationStore().addRule(JustifiedRelationStore.Rule("bad", listOf(Triple("?a", "p", "?b")), Triple("?a", "q", "?z")))
            fail()
        } catch (_: IllegalArgumentException) {}
    }
}
