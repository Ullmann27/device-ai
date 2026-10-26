package at.heinz.deviceai.research.interactive

import org.junit.Assert.*
import org.junit.Test

class BoundedCognitionTest {
 @Test fun curiosityNeedsPermissionFiniteValuesBudgetAndActualInformation() {
  val w=listOf(ResearchQuestions.World("a",0.5),ResearchQuestions.World("b",0.5))
  val q=ResearchQuestions.Question("meaning",mapOf("a" to "A","b" to "B"),10,10)
  assertNull(ResearchQuestions.choose(w,listOf(q),false,10,10))
  assertNull(ResearchQuestions.choose(w,listOf(q),true,9,10))
  assertNull(ResearchQuestions.choose(w,listOf(q.copy(answers=mapOf("a" to "same","b" to "same"))),true,10,10))
  assertEquals(1.0,ResearchQuestions.choose(w,listOf(q),true,10,10)!!.informationBits,1e-9)
  assertThrows(IllegalArgumentException::class.java){ResearchQuestions.choose(w,listOf(q.copy(novelty=Double.NaN)),true,10,10)}
 }
 @Test fun interventionsCutEdgesButCannotTurnUnknownIntoFact() {
  val rules=listOf(ResearchCounterfactual.Rule("breathable",listOf("oxygen","pressure"),ResearchCounterfactual.Kind.ALL,"test-oracle"))
  val facts=mapOf("oxygen" to null,"pressure" to false)
  val graph=ResearchCounterfactual(facts,rules)
  assertNull(graph.run(mapOf("pressure" to true)).derived["breathable"])
  assertEquals(false,graph.run(emptyMap()).derived["breathable"])
  val intervention=graph.run(mapOf("breathable" to true))
  assertEquals(true,intervention.intervention["breathable"]);assertFalse("breathable" in intervention.derived)
  assertEquals(false,facts["pressure"])
  assertThrows(IllegalArgumentException::class.java){graph.run(mapOf("invented" to true))}
  assertThrows(IllegalArgumentException::class.java){ResearchCounterfactual(emptyMap(),listOf(ResearchCounterfactual.Rule("a",listOf("a"),ResearchCounterfactual.Kind.NOT,"bad")))}
 }
 @Test fun criticNeverTreatsItsOwnDraftOrUnknownAsEvidence() {
  val draft=listOf(ResearchCritic.Claim("real",false),ResearchCritic.Claim("made-up",true))
  val r=ResearchCritic.check(draft,mapOf("real" to true),2)
  assertTrue(r.accepted.isEmpty());assertEquals(2,r.unsupported.size)
  assertTrue(ResearchCritic.check(draft,mapOf("real" to false),0).accepted.isEmpty())
 }
 @Test fun leaveOneOutHasKnownRedundancyFailureAndIsNotPromoted() {
  val r=EvidenceSensitivity.probe(mapOf("a" to "same","b" to "same"),3){it.values.firstOrNull()}
  assertTrue("Known failure: redundant truthful supports incorrectly look unsupported",r.unsupported)
 }
 @Test fun consensusRejectsOrderEffectsPartialCoverageAndOutOfSetOutputs() {
  val ids=listOf("a","b","c")
  assertNull(PermutationConsensus.select(ids,3){it.first()}.answer)
  assertNull(PermutationConsensus.select(ids,1){"a"}.answer)
  assertEquals("a",PermutationConsensus.select(ids,3){"a"}.answer)
  assertThrows(IllegalArgumentException::class.java){PermutationConsensus.select(ids,3){"injected"}}
 }
}
