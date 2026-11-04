package at.heinz.deviceai.chief

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.cognitive.AgentCognitiveEvent
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentRunResult
import at.heinz.deviceai.cognitive.AgentState
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.CognitiveWorkState
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.ExperienceOutcome
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.ValidationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * SPRINT-98: EIN Ende-zu-Ende-Test "erster Denk-Durchlauf" auf dem Integrationszweig von Continuum 1.
 * Kette: Ziel -> Plan -> Erinnern -> Handeln (riskant nur mit Freigabe; Nein und Ja) -> Pruefen ->
 * Lernen (Hypothese, nicht Fakt; Tagebuch) -> naechster Schritt.
 *
 * Echt: BoundedAgentOrchestrator, ToolGate/ToolRegistry, HybridMemoryRetriever (ueber den Orchestrator),
 * InMemoryExperienceStore, MemoryRecord-Invarianten, OwnerStatementModality.
 * Attrappen (erlaubt): nur das Modell (deterministisches Skript) und das Geraete-Backend (ToolRunner).
 * Fehlende Bausteine anderer Bots: @Ignore("PENDING – wartet auf <Bot>: <Klasse/Methode>").
 */
class FirstThinkingRunE2ETest {

    private val goal = "Ziel: Schaffe Platz im Download-Ordner, ohne etwas zu loeschen."
    private val day = 86_400_000L
    private val now = 500 * day

    /** Gedaechtnis (Owner-Fakten) – nur lesen. */
    private val memory = listOf(
        MemoryRecord("mem-archiv", MemoryType.SEMANTIC,
            "Download-Ordner: grosse Dateien bitte nach /sdcard/Archiv verschieben, nie loeschen",
            ProvenanceType.USER_PROVIDED, 0.95, 100 * day, 100 * day),
        MemoryRecord("mem-katze", MemoryType.EPISODIC, "Die Katze heisst Mimi",
            ProvenanceType.USER_PROVIDED, 0.9, 200 * day, 200 * day)
    )

    private class Trace {
        val events = Collections.synchronizedList(mutableListOf<AgentCognitiveEvent>())
        val modelSawMemoryIds = mutableListOf<List<String>>()
        val proposals = mutableListOf<String>()
        val backendCalls = mutableListOf<String>()
        val approvalsAsked = mutableListOf<Pair<String, Risk>>()
        lateinit var result: AgentRunResult
        val experiences = InMemoryExperienceStore()
        var memoryAfter: List<MemoryRecord> = emptyList()
    }

    /** Ein vollstaendiger Durchlauf; [ownerSaysYes] = Antwort des Besitzers auf die Freigabefrage. */
    private fun thinkingRun(ownerSaysYes: Boolean): Trace {
        val t = Trace()
        val mem = memory.toMutableList()
        val model = AgentModel { _, memories, _, obs ->
            t.modelSawMemoryIds += memories.map { it.id }
            when (obs.size) {
                0 -> AgentModelOutput("skript", "", ToolProposal("get_large_files", mapOf("roots" to "Download", "limit" to 3)))
                    .also { t.proposals += "get_large_files" }
                1 -> AgentModelOutput("skript", "", ToolProposal("move_file",
                    mapOf("source" to "/sdcard/Download/film.mp4", "destination" to "/sdcard/Archiv/film.mp4")))
                    .also { t.proposals += "move_file" }
                else -> {
                    val moved = obs.last().status == ToolExecutionStatus.SUCCEEDED
                    AgentModelOutput("skript", if (moved) "Verschoben: film.mp4 (2,1 GB) nach Archiv. Naechster Schritt: Rest pruefen."
                        else "Nicht verschoben – keine Freigabe. Naechster Schritt: auf deine Entscheidung warten.")
                }
            }
        }
        val backend = ToolRunner { call ->
            t.backendCalls += call.name
            when (call.name) {
                "get_large_files" -> "/sdcard/Download/film.mp4 2.1GB"
                "move_file" -> "moved /sdcard/Download/film.mp4 -> /sdcard/Archiv/film.mp4"
                else -> error("unerwartet: ${call.name}")
            }
        }
        val agent = BoundedAgentOrchestrator(
            model = model,
            memories = { mem.toList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), backend),
            evaluator = AgentEvaluator { response, record ->
                Evaluation(record?.status == ToolExecutionStatus.SUCCEEDED && response.startsWith("Verschoben"), 0.9, "pruefung")
            },
            experienceStore = t.experiences,
            approvalProvider = { p, risk -> t.approvalsAsked += p.toolName to risk; AgentToolApproval(ownerSaysYes) },
            clock = { now },
            callIdPrefix = "denk-${if (ownerSaysYes) "ja" else "nein"}",
            eventObserver = { t.events += it }
        )
        t.result = agent.run(goal)
        t.memoryAfter = mem.toList()
        return t
    }

    private val yes by lazy { thinkingRun(ownerSaysYes = true) }
    private val no by lazy { thinkingRun(ownerSaysYes = false) }

    // ------------------------------------------------------------ 1 Ziel
    @Test
    fun schritt1_ziel_wirdAngenommenUndQuittiertBevorGearbeitetWird() {
        val first = yes.events.first()
        assertEquals(CognitiveWorkState.UNDERSTANDING, first.state)
        assertEquals(0, first.sequence)
        assertEquals(AgentState.OBSERVE, yes.result.auditTrail.first().state)
        assertEquals(sha256Hex(goal), yes.result.taskEvidence!!.requestSha256)
    }

    // ------------------------------------------------------------ 2 Plan
    @Test
    fun schritt2_plan_entstehtVorDemErstenModellUndWerkzeugaufruf() {
        val states = yes.events.map { it.state }
        assertEquals(1, states.indexOf(CognitiveWorkState.PLANNING))
        val audit = yes.result.auditTrail.map { it.state }
        assertTrue(audit.indexOf(AgentState.PLAN) < audit.indexOf(AgentState.MODEL))
        assertTrue(audit.indexOf(AgentState.PLAN) < audit.indexOf(AgentState.GATED_EXECUTION))
        // Plan = registrierte Werkzeuge in fester Reihenfolge: erst lesen, dann (riskant) handeln.
        assertEquals(listOf("get_large_files", "move_file"), yes.proposals)
    }

    // ------------------------------------------------------------ 3 Erinnern
    @Test
    fun schritt3_erinnern_relevantesGedaechtnisErreichtJedenModellschritt() {
        assertTrue(yes.modelSawMemoryIds.isNotEmpty())
        yes.modelSawMemoryIds.forEach { assertTrue("Archiv-Regel fehlt im Modellschritt", "mem-archiv" in it) }
        assertEquals(listOf("mem-archiv"), yes.result.experience!!.memoryIds.filter { it == "mem-archiv" })
    }

    // ------------------------------------------------------------ 4 Handeln
    @Test
    fun schritt4a_handeln_nein_riskanteAktionLaeuftNie() {
        assertEquals(listOf("move_file" to Risk.CONFIRM), no.approvalsAsked)       // nur die riskante Aktion fragt
        assertEquals(listOf("get_large_files"), no.backendCalls)                     // Lesen ja, Verschieben nein
        val move = no.result.taskEvidence!!.calls.single { it.toolName == "move_file" }
        assertEquals(ToolExecutionStatus.REJECTED, move.status)
        assertEquals(false, move.ownerApproved)
        val states = no.events.map { it.state }
        assertTrue(CognitiveWorkState.WAITING_FOR_APPROVAL in states)
    }

    @Test
    fun schritt4b_handeln_ja_genauEineAusfuehrungNachFreigabe() {
        assertEquals(listOf("move_file" to Risk.CONFIRM), yes.approvalsAsked)
        assertEquals(listOf("get_large_files", "move_file"), yes.backendCalls)
        val move = yes.result.taskEvidence!!.calls.single { it.toolName == "move_file" }
        assertEquals(ToolExecutionStatus.SUCCEEDED, move.status)
        assertEquals(true, move.ownerApproved)
        val states = yes.events.map { it.state }
        val ask = states.indexOf(CognitiveWorkState.WAITING_FOR_APPROVAL)
        assertTrue(ask >= 0 && ask < states.lastIndexOf(CognitiveWorkState.EXECUTING))
    }

    // ------------------------------------------------------------ 5 Pruefen
    @Test
    fun schritt5_pruefen_nurFrischeBeobachtungGiltAlsVerifiziert() {
        assertTrue(yes.result.taskEvidence!!.evaluatorSucceeded)
        assertTrue(yes.result.taskEvidence!!.outcomeVerified)
        assertEquals(CognitiveWorkState.COMPLETED, yes.events.last().state)
        assertTrue(yes.result.taskEvidence!!.calls.all { it.freshOutput && it.outputSha256 != null || it.status != ToolExecutionStatus.SUCCEEDED })

        assertFalse(no.result.taskEvidence!!.outcomeVerified)
        assertNotNull(no.result.blocker)
        assertEquals(CognitiveWorkState.BLOCKED, no.events.last().state)
    }

    // ------------------------------------------------------------ 6 Lernen
    @Test
    fun schritt6_lernen_erfahrungWirdGespeichertAberNieAlsFaktImGedaechtnis() {
        val learned = yes.experiences.all().single()
        assertEquals(ExperienceOutcome.SUCCESS, learned.outcome)
        assertEquals(listOf("get_large_files", "move_file"), learned.toolIds)
        assertTrue(learned.confidenceAfter < 1.0)
        // Kein neuer Owner-Fakt: das Gedaechtnis ist nach dem Lauf unveraendert.
        assertEquals(memory, yes.memoryAfter)
        // Fehlschlag (Nein) wird als FAILURE gelernt, nicht als Erfolg.
        assertEquals(ExperienceOutcome.FAILURE, no.experiences.all().single().outcome)

        // Hypothese statt Fakt (vorhandene Invarianten): eine Lehre darf als UNVERIFIED/INFERENCE existieren,
        // VERIFIED ohne unabhaengige Evidenz ist unmoeglich, und als Vermutung formuliert wird sie nie bestaetigter Fakt.
        val lesson = "Vielleicht reicht es, grosse Videos ins Archiv zu verschieben"
        MemoryRecord("lehre-1", MemoryType.PROCEDURAL, lesson, ProvenanceType.INFERENCE, learned.confidenceAfter,
            now, now, validationState = ValidationState.UNVERIFIED)
        assertThrows(IllegalArgumentException::class.java) {
            MemoryRecord("lehre-2", MemoryType.PROCEDURAL, lesson, ProvenanceType.INFERENCE, 0.9, now, now,
                validationState = ValidationState.VERIFIED)
        }
        // OwnerStatementModality (#371) is not on this branch; that assertion stays on the integration branch.
    }

    // ------------------------------------------------------------ 7 Naechster Schritt
    @Test
    fun schritt7_naechsterSchritt_rundeEndetSauberUndDerNaechsteDurchlaufIstSofortMoeglich() {
        assertEquals(AgentState.RESPOND, yes.result.state)
        assertNull(yes.result.blocker)
        assertTrue(yes.result.response.contains("Naechster Schritt"))
        assertEquals(1, yes.events.count { it.state in setOf(CognitiveWorkState.COMPLETED, CognitiveWorkState.BLOCKED) })
        assertEquals(yes.events.indices.toList(), yes.events.map { it.sequence })
    }

    // ------------------------------------------------------------ Gesamte Kette
    /** Die ganze Kette in einem Durchlauf (Ja-Fall) – das Sprint-Ziel, soweit heute echt pruefbar. */
    @Test
    fun ersterDenkDurchlauf_ganzeKette_jaFall() {
        val s = yes.events.map { it.state }
        val order = listOf(CognitiveWorkState.UNDERSTANDING, CognitiveWorkState.PLANNING,
            CognitiveWorkState.WAITING_FOR_APPROVAL, CognitiveWorkState.EXECUTING,
            CognitiveWorkState.VERIFYING, CognitiveWorkState.COMPLETED)
        var cursor = -1
        order.forEach { st ->
            val i = s.withIndex().indexOfFirst { it.index > cursor && it.value == st }
            assertTrue("$st fehlt/falsche Reihenfolge in $s", i > cursor)
            cursor = i
        }
        assertTrue("mem-archiv" in yes.modelSawMemoryIds.first())
        assertEquals(1, yes.experiences.all().size)
        assertTrue(yes.result.response.startsWith("Verschoben"))
    }

    private fun sha256Hex(s: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
