package at.heinz.deviceai.cognitive

import at.heinz.deviceai.BootstrapBrain
import at.heinz.deviceai.BootstrapKind
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3: gaps found by the offline eval (docs/evidence/m3-eval), each pinned as a unit test. */
class M3EvalGapsTest {
    private fun tool(text: String) = BootstrapBrain.decide(text, emptyList()).let { if (it.kind == BootstrapKind.DEVICE_TOOL) it.tool else null }

    @Test fun naturalReadOnlyDevicePhrasingsRoute() {
        assertEquals("get_battery_info", tool("Wie ist mein Akkustand?"))
        assertEquals("get_storage_info", tool("Wie voll ist mein Speicher?"))
        assertEquals("list_installed_apps", tool("Welche Apps sind installiert?"))
        assertEquals("get_network_summary", tool("Bin ich mit dem WLAN verbunden?"))
        // "nicht X, sondern Y": the read verb carries over to the contrast clause.
        assertEquals("get_battery_info", tool("Prüf nicht das WLAN, sondern den Akku"))
        // Controls: knowledge questions and mutations stay away from tools.
        assertEquals(null, tool("Erkläre mir, wie ein Lithium-Akku funktioniert"))
        assertEquals(null, tool("Was ist eigentlich Arbeitsspeicher?"))
    }

    @Test fun wederNochNegatesEveryReadOnlyTool() {
        assertEquals(null, tool("Zeig mir weder Downloads noch Apps"))
        assertEquals(null, tool("Weder den Akku noch den Speicher prüfen"))
    }

    @Test fun moreClockPhrasings() {
        assertTrue(LocalClockIntent.isClockQuestion("Den wievielten haben wir heute?"))
        assertTrue(LocalClockIntent.isClockQuestion("Sag mir bitte die Uhrzeit"))
        assertFalse(LocalClockIntent.isClockQuestion("Wie spät ist es in Tokio?"))
    }

    @Test fun ellipticalUndWelcheIsAFollowUp() {
        assertTrue(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn("Und welche hat jetzt mehr?"))
        assertFalse(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn("Was ist die Hauptstadt von Italien?"))
    }

    @Test fun contractedPrepositionImIsAnEntityDeterminer() {
        val p = OwnerStatementTimeline.project(listOf("Im Kühlschrank sind 5 Äpfel."),
            "Jetzt sind im Kühlschrank 3 Äpfel statt 5. Wie viele habe ich gegessen?")!!
        assertEquals(5L to 3L, p.entry("Kühlschrank")!!.let { it.original to it.current })
    }

    private val ring = "In der roten Kiste liegen 4 Ringe, in der blauen 7."
    private val q = "Welche Kiste hatte ursprünglich mehr?"

    @Test fun contextDenialIsRejectedOnlyWhenOwnerContextWasSupplied() {
        val framed = LocalFollowUpTaskFrame.compose(q, q, listOf(ring))
        listOf("Ich habe keinen Zugriff auf frühere Nachrichten, daher kann ich das nicht sagen.",
            "Ich kann mich nicht an vorherige Nachrichten erinnern.",
            "Als Sprachmodell habe ich kein Gedächtnis für frühere Teile unseres Gesprächs.",
            "Du hast mir in diesem Gespräch keine Angaben zu den Kisten gemacht.").forEach {
            assertFalse(it, LocalDialogueQualityGate.evaluate(framed, it).success)
            assertFalse(it, LocalDialogueQualityGate.evaluate(q, it, priorOwnerContextSupplied = true).success)
            // Without supplied context the same honest statement is acceptable.
            assertTrue(it, LocalDialogueQualityGate.evaluate(q, it).success)
        }
        assertTrue(LocalDialogueQualityGate.evaluate(framed, "Ich weiß es nicht.").success)
        assertTrue(LocalDialogueQualityGate.evaluate(framed, "Ursprünglich hatte die blaue Kiste mit 7 Ringen mehr.").success)
    }

    @Test fun boundedLoopSelfCorrectsAContextDenialOnce() {
        val framed = LocalFollowUpTaskFrame.compose(q, q, listOf(ring))
        var calls = 0
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, reflection, _ -> calls++
                AgentModelOutput("m", if (reflection == null) "Ich habe keinen Zugriff auf frühere Nachrichten." else "Blau, 7 Ringe.") },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(), ToolRunner { error("no tool") }),
            evaluator = AgentEvaluator { r, _ -> LocalDialogueQualityGate.evaluate(q, r, priorOwnerContextSupplied = true) },
            experienceStore = InMemoryExperienceStore(), limits = AgentLimits(maxRetries = 1), clock = { 1L })
        val result = agent.run(framed)
        assertEquals("Blau, 7 Ringe.", result.response)
        assertEquals(2, calls)
    }

    private fun mem(vararg texts: String) = texts.mapIndexed { i, t ->
        MemoryRecord("m${i + 1}", MemoryType.SEMANTIC, t, ProvenanceType.USER_PROVIDED, 0.9, 1L, 1L) }
    private fun top(query: String, vararg texts: String) =
        HybridMemoryRetriever.retrieve(query, mem(*texts), limit = 3, nowMillis = 2L).firstOrNull()?.memory?.id

    @Test fun germanInflectionUmlautAndCompoundsRetrieve() {
        assertEquals("m1", top("Wie heißt mein Kind?", "Meine Kinder heißen Lena und Max.", "Ich arbeite in Linz."))
        assertEquals("m1", top("Wie heißen meine Neffen?", "Mein Neffe heißt Jonas.", "Ich habe Äpfel gekauft."))
        assertEquals("m1", top("Wann ist mein Termin beim Zahnarzt?", "Mein Zahnarzttermin ist am Freitag.", "Ich spiele Gitarre."))
        assertEquals("m1", top("Wo leben meine Grosseltern?", "Meine Großeltern leben in Salzburg.", "Ich fahre Rad."))
        // Unrelated query still retrieves nothing.
        assertEquals(null, top("Was ist die Hauptstadt von Australien?", "Ich wohne in Wien.", "Meine Mutter heißt Anna."))
    }
}
