package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumCognitiveRuntime
import at.heinz.deviceai.learning.ContinuumCre1State
import at.heinz.deviceai.learning.ContinuumLearningCore
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Four predeclared synthetic pairs; exactly eight SWB1/CRE1 chat turns.
 * Uses the actual CognitiveDecisionBridge path from AgentActivity, but no
 * Gemma runtime is available in JVM. Gemma=NOT_RUN, never mock successful.
 */
class ContinuumEvidenceSwitchProbeTest {
    private data class Receipt(val ids:Set<String>,val answer:String)
    private fun turn(core:ContinuumLearningCore,dir:java.io.File,session:String,
                     query:String,expected:String,forbidden:String):Receipt {
        val trace=CognitiveTurnTrace(session)
        val time=System.nanoTime()
        val answer=CognitiveDecisionBridge(
            ContinuumCognitiveRuntime(dir,core,session,turnTrace=trace),null
        ).respond(query)
        val elapsed=(System.nanoTime()-time).coerceAtLeast(0L)/1_000_000L
        val ids=answer.turn.dialogue.memoryIds.toSet()
        val authorized=core.activeOwnerFacts().single { it.content==expected }.id
        println("EVIDENCE_PAIR session=$session ms=$elapsed gemma=NOT_RUN gemmaCalls=0 " +
            "cre1=${answer.turn.cre1State} ids=${ids.sorted()} " +
            "modelId=NOT_RUN answer=${answer.finalAnswer.replace("\n"," ").take(240)}")
        assertEquals("Exactly one authorized source ID",setOf(authorized),ids)
        assertEquals(ids,answer.evidenceHits.map{it.id}.toSet())
        assertTrue(answer.turn.grounded)
        assertTrue("Correct approved statement missing",answer.finalAnswer.contains(expected))
        assertFalse("Stale/foreign answer persisted",answer.finalAnswer.contains(forbidden))
        assertEquals(ContinuumCre1State.UNTRAINED_SHADOW,answer.turn.cre1State)
        assertTrue(answer.turn.trace.contains("CRE1_SHADOW"))
        assertEquals(0,trace.finish(TraceOutcome.MEMORY_EXACT).modelInvocations)
        assertFalse(answer.usedLocalModel)
        assertNull(answer.modelId)
        assertEquals(1,core.dialogueHistory().size)
        return Receipt(ids,answer.finalAnswer)
    }
    private fun pair(id:String,query:String,a:String,b:String,distractor:String?=null) {
        val dirs=listOf(
            Files.createTempDirectory("continuum-$id-a-").toFile(),
            Files.createTempDirectory("continuum-$id-b-").toFile()
        )
        try {
            val receipts=dirs.mapIndexed { i,dir ->
                val core=ContinuumLearningCore(dir)
                core.confirmOwnerFact(if(i==0)a else b)
                if(distractor!=null)core.confirmOwnerFact(distractor)
                turn(core,dir,"$id-$i",query,if(i==0)a else b,if(i==0)b else a)
            }
            assertNotEquals(receipts[0].ids,receipts[1].ids)
            assertNotEquals(receipts[0].answer,receipts[1].answer)
        } finally { dirs.forEach { it.deleteRecursively() } }
    }
    @Test fun entitySwitch()=pair(
        "entity","Wo legt Projekt Orion seine Proben ab?",
        "Projekt Orion legt seine Proben in Kammer Rot ab.",
        "Projekt Orion legt seine Proben in Kammer Blau ab.")
    @Test fun negationSwitch()=pair(
        "negation","Ist die Testtür Delta geöffnet?",
        "Die Testtür Delta ist geöffnet.",
        "Die Testtür Delta ist nicht geöffnet.")
    @Test fun lookalikeEntityMustNotSupplyAnswer()=pair(
        "lookalike","Wo steht Gerät Kappa 7?",
        "Gerät Kappa 7 steht in Raum Silber.",
        "Gerät Kappa 7 steht in Raum Kupfer.",
        "Gerät Kappa 70 steht in Raum Gold.")
    @Test fun revocationChangesAuthorizedAnswerWithoutStaleEvidence() {
        val dir=Files.createTempDirectory("continuum-revoke-").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            val query="Welchen Schlüssel nutzt Labor Sigma?"
            val old="Labor Sigma nutzt Schlüssel Grün."
            val new="Labor Sigma nutzt Schlüssel Lila."
            core.confirmOwnerFact(old)
            val first=turn(core,dir,"revoke-before",query,old,new)
            assertEquals(1,core.revokeOwnerFact(old))
            core.confirmOwnerFact(new)
            val second=turn(core,dir,"revoke-after",query,new,old)
            assertFalse(second.ids.any { it in first.ids })
            assertTrue(core.activeOwnerFacts().none { it.id in first.ids })
        } finally { dir.deleteRecursively() }
    }
}
