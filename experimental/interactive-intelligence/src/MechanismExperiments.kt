package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.learning.TrainableSubwordEncoder
import at.heinz.deviceai.learning.SubwordIntentExample
import java.io.File

object MechanismExperiments {
 fun run(out:File):List<String> {
  val lines=mutableListOf("experiment\tarm\tcorrect\ttotal\twrong\tabstain\tcost\tmeaning")
  // C: balanced indistinguishable worlds; target is NOT in prompt/training.
  // Exactly one truthful oracle answer distinguishes them. This is information
  // acquisition, not proof of general question generation or human preference.
  for(seed in listOf(73L,101L,211L)) {
   val worlds=listOf(ResearchQuestions.World("a",0.5),ResearchQuestions.World("b",0.5))
   val qs=listOf(ResearchQuestions.Question("meaning",mapOf("a" to "A","b" to "B"),10,20),
    ResearchQuestions.Question("irrelevant",mapOf("a" to "same","b" to "same"),1,1))
   val selected=ResearchQuestions.choose(worlds,qs,true,10,20)
   check(selected?.question?.id=="meaning")
   val model=TrainableSubwordEncoder(seed)
   val pre=if(model.intentScore("Was bedeutet das?")>=0.5)1 else 0
   // Same ambiguous input with balanced labels: more repetitions cannot reveal
   // which independently selected world is currently intended.
   val training=listOf(SubwordIntentExample("Was bedeutet das?",0),SubwordIntentExample("Was bedeutet das?",1))
   val report=model.trainIntent(training,steps=400,maxMillis=4000)
   val post=if(model.intentScore("Was bedeutet das?")>=0.5)1 else 0
   val targets=(0 until 40).map{it%2}
   lines+="C\tNO_QUESTION_$seed\t${targets.count{it==pre}}\t40\t${targets.count{it!=pre}}\t0\t0\tbalanced_alias_game"
   lines+="C\tEXTRA_TRAINING_$seed\t${targets.count{it==post}}\t40\t${targets.count{it!=post}}\t0\t${report.steps}\tactual_SWB1_updates"
   val successes=targets.count{t->selected.question.answers.getValue(if(t==0)"a" else "b")==if(t==0)"A" else "B"}
   lines+="C\tONE_ORACLE_QUESTION_$seed\t$successes\t40\t${40-successes}\t0\t40\tone_truthful_answer_per_case"
  }
  // D: all 16 independently enumerated combinations. A denser atmosphere alone
  // is not sufficient: breathable oxygen AND safe pressure are separate premises.
  var directCorrect=0;var causalCorrect=0;var total=0
  val evidence=mutableListOf("pressure\toxygen\tchangedPressure\tchangedOxygen\texpected\tcausal\tdirect")
  for(p in listOf(false,true))for(o in listOf(false,true))for(cp in listOf(false,true))for(co in listOf(false,true)) {
   val facts=mapOf("safePressure" to p,"oxygen" to o,"radiationKnown" to null)
   val graph=ResearchCounterfactual(facts,listOf(ResearchCounterfactual.Rule("breathable",listOf("safePressure","oxygen"),ResearchCounterfactual.Kind.ALL,"synthetic-condition-table-v1")))
   val result=graph.run(mapOf("safePressure" to cp,"oxygen" to co))
   // Oracle expressed independently as four allowed/forbidden rows.
   val oracle=when(cp to co){true to true->true;false to false,false to true,true to false->false;else->error("unreachable")}
   val causal=result.derived.getValue("breathable")
   val direct=cp // declared pressure-only heuristic, NEVER a Gemma/model answer
   if(oracle==causal)causalCorrect++;if(oracle==direct)directCorrect++;total++
   check(facts["safePressure"]==p && "radiationKnown" in result.unknown)
   evidence+="$p\t$o\t$cp\t$co\t$oracle\t$causal\t$direct"
  }
  File(out,"counterfactual-oracle.tsv").writeText(evidence.joinToString("\n")+"\n")
  lines+="D\tPRESSURE_ONLY_HEURISTIC\t$directCorrect\t$total\t${total-directCorrect}\t0\t16\tNOT_GEMMA"
  lines+="D\tEXPLICIT_CAUSAL_MODEL\t$causalCorrect\t$total\t${total-causalCorrect}\t0\t16\tfinite_boolean_oracle"
  // E: deterministic externally labelled drafts with incorrect and unsupported claims.
  var uncheckedWrong=0;var keptWrong=0;var accepted=0;var rejected=0
  repeat(40){i->
   val truth=mapOf("known" to (i%2==0),"unknown" to null)
   val draft=listOf(ResearchCritic.Claim("known",i%3==0),ResearchCritic.Claim("unknown",true))
   uncheckedWrong+=draft.count{truth[it.key]==null||truth[it.key]!=it.value}
   val checked=ResearchCritic.check(draft,truth,2)
   keptWrong+=checked.accepted.count{truth[it.key]!=it.value};accepted+=checked.accepted.size;rejected+=checked.unsupported.size
  }
  lines+="E\tUNCHECKED_SYNTHETIC_DRAFT\t${80-uncheckedWrong}\t80\t$uncheckedWrong\t0\t0\tno_model_generation"
  lines+="E\tORACLE_CRITIC\t$accepted\t80\t$keptWrong\t$rejected\t80\tclaim_filter_not_Gemma_quality"
  // G1 counterprobe deliberately tests redundancy, where leave-one-out cannot
  // separate two sufficient pieces from an unsupported constant answer.
  val single=EvidenceSensitivity.probe(mapOf("a" to "fact"),3){it.values.firstOrNull()}
  val duplicate=EvidenceSensitivity.probe(mapOf("a" to "fact","b" to "fact"),3){it.values.firstOrNull()}
  val invented=EvidenceSensitivity.probe(mapOf("a" to "fact"),3){"invented"}
  val g1=listOf(!single.unsupported,!duplicate.unsupported,invented.unsupported).count{it}
  lines+="G1\tLEAVE_ONE_OUT\t$g1\t3\t${3-g1}\t0\t${single.calls+duplicate.calls+invented.calls}\tredundancy_counterexample_candidate_rejected"
  // G2: same candidates, same ranker, order alone varies. Rejection is useful
  // but cannot establish truth of an order-invariant wrong answer.
  var wrongBefore=0;var wrongAfter=0;var solved=0;var abstained=0
  for(i in 0 until 24){
   val ids=if(i%2==0)listOf("good","bad") else listOf("bad","good")
   val orderSensitive=i%3!=0
   val rank:(List<String>)->String?=if(orderSensitive)({it.first()}) else ({"good"})
   if(rank(ids)!="good")wrongBefore++
   val r=PermutationConsensus.select(ids,2,rank)
   if(r.answer==null)abstained++ else if(r.answer=="good")solved++ else wrongAfter++
  }
  lines+="G2\tSINGLE_ORDER\t${24-wrongBefore}\t24\t$wrongBefore\t0\t24\torder_sensitive_synthetic_ranker"
  lines+="G2\tPERMUTATION_CONSENSUS\t$solved\t24\t$wrongAfter\t$abstained\t48\tcoverage_tradeoff_not_truth_proof"
  return lines
 }
}
