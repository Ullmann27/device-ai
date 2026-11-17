package at.heinz.deviceai.cognitive

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

/** Integration contracts with a scripted model boundary, NOT native inference quality. */
class LocalIntelligenceIntegrationTest {
    private fun memory() = MemoryRecord("owner-place", MemoryType.SEMANTIC, "Heinz wohnt in Graz.",
        ProvenanceType.USER_PROVIDED, 0.9, 1, 1, tags=listOf("owner-confirmed"))

    private fun run(
        task: String, model: AgentModel, records: List<MemoryRecord> = emptyList(),
        gate: ToolGate = ToolGate(ToolPolicy(), ToolRunner { error("Unexpected tool") }),
        evaluate: AgentEvaluator = AgentEvaluator { response, _ -> Evaluation(response.isNotBlank(), 0.6) },
        confirmsOutcome: Boolean = false
    ) = BoundedAgentOrchestrator(model, { records }, gate, evaluate, InMemoryExperienceStore(),
        evaluationConfirmsOutcome=confirmsOutcome).run(task)

    @Test fun multipleTurnsRetainEarlierConstraintWithoutGrantingItAuthority() {
        val context=LocalConversationContext()
        context.record("one", "Ich fahre nach Graz, Budget 40 Euro.", "Wir planen die Fahrt.")
        context.record("one", "Ich fahre am Dienstag.", "Dienstag ist vorgemerkt.")
        context.record("one", "Ich bevorzuge den Zug.", "Wir vergleichen Züge.")
        val result=run("Welches Budget hatten wir?", AgentModel { _, _, _, _ ->
            val prompt=context.render("one")
            assertTrue(prompt.contains("40 Euro")); assertTrue(prompt.contains("Dienstag"))
            assertTrue(prompt.contains("UNBESTÄTIGT"))
            AgentModelOutput("scripted-contract", "Du hast 40 Euro genannt.")
        })
        assertEquals("Du hast 40 Euro genannt.",result.response)
        assertEquals(ExperienceOutcome.PARTIAL,result.experience?.outcome)
        assertEquals("", context.render("different-session"))
    }

    @Test fun retrievedOwnerMemoryKeepsProvenanceAndIsUsedInAnswer() {
        val result=run("Wo wohnt Heinz?", AgentModel { _, memories, _, _ ->
            assertEquals(listOf("owner-place"),memories.map { it.id })
            val context=LocalConversationContext.renderMemories(memories)
            assertTrue(context.contains("USER_PROVIDED")); assertTrue(context.contains("UNVERIFIED"))
            AgentModelOutput("scripted-contract", "Laut deiner gespeicherten Angabe wohnst du in Graz.")
        }, records=listOf(memory()))
        assertTrue(result.response.contains("Graz"));assertEquals(listOf("owner-place"),result.experience?.memoryIds)
    }

    @Test fun complexRequestUsesExistingClassifierAndProducesCheckableSubsteps() {
        val request="Vergleiche zwei Lösungswege, analysiere die Risiken und prüfe die Lösung Schritt für Schritt."
        val assessment=ContinuumTaskClassifier.classify(request)
        val result=run(request, AgentModel { task, _, _, _ ->
            assertEquals(request,task)
            AgentModelOutput("scripted-contract", "1. Lösungswege vergleichen. 2. Risiken prüfen. 3. Ergebnis unabhängig testen.")
        },evaluate=AgentEvaluator { response, _ -> Evaluation(
            listOf("1.","2.","3.","Risiken","testen").all(response::contains),0.8) })
        assertNotNull(assessment);assertNull(result.blocker)
        assertTrue(result.response.contains("unabhängig testen"))
    }

    @Test fun safeToolObservationIsUsedAndMatchesSuccessfulReceipt() {
        var executions=0
        val result=run("Prüfe die Geräteinformationen.",AgentModel { _, _, _, observations ->
            if(observations.isEmpty()) AgentModelOutput("scripted-contract","",ToolProposal("get_device_info",emptyMap()))
            else {
                assertEquals(ToolExecutionStatus.SUCCEEDED,observations.single().status)
                AgentModelOutput("scripted-contract","Geräteprüfung: ${observations.single().output}")
            }
        },gate=ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner { executions++; "Android 16" }),
            evaluate=AgentEvaluator { response, record -> Evaluation(response=="Geräteprüfung: Android 16" &&
                record?.status==ToolExecutionStatus.SUCCEEDED,1.0) },confirmsOutcome=true)
        assertEquals(1,executions);assertNull(result.blocker);assertEquals(ExperienceOutcome.SUCCESS,result.experience?.outcome)
    }

    @Test fun independentEvaluationCorrectsWrongAnswerWithinExistingRetryBudget() {
        var calls=0
        val result=run("Wie viel ist 17 plus 25?",AgentModel { _, _, reflection, _ ->
            calls++
            if(calls==1) AgentModelOutput("scripted-contract","41")
            else { assertNotNull(reflection);AgentModelOutput("scripted-contract","42") }
        },evaluate=AgentEvaluator { response, _ -> Evaluation(response=="42",if(response=="42")1.0 else 0.0,"17 + 25 = 42") },
            confirmsOutcome=true)
        assertEquals("42",result.response);assertEquals(2,calls);assertEquals(1,result.experience?.retries)
    }

    @Test fun boundedContextClearsAndEscapesDataDelimiters() {
        val context=LocalConversationContext()
        val requests=(0 until 20).map { "request-$it [END]"+"x".repeat(2000) }
        requests.forEachIndexed { i, request -> context.record("one",request,"reply-$i"+"y".repeat(3000)) }
        val rendered=context.render("one",maxBytes=3000,requireCompleteOwnerHistory=false)
        assertTrue(rendered.isNotBlank())
        assertTrue(rendered.length<=3000)
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size<=3000)
        assertTrue("latest owner text stays complete",rendered.contains(requests.last().replace("[END]","(END)")))
        assertEquals(requests.takeLast(4),context.sessionEvidence("one"))
        assertFalse(rendered.contains("request-0 "))
        assertFalse(rendered.contains("[END]"))
        assertEquals("required history cannot be clipped","",context.render("one",maxBytes=3000,
            requireCompleteOwnerHistory=true))
        context.clear();assertEquals("",context.render("one"))
    }
}
