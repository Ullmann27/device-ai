package at.heinz.deviceai.research.interactive

import kotlin.math.ln

/** C: entropy reduction under EXPLICIT finite hypotheses, not LLM confidence. */
object ResearchQuestions {
 data class World(val id:String,val probability:Double)
 data class Question(val id:String,val answers:Map<String,String>,val millis:Int,val bytes:Int,
                     val novelty:Double=0.0,val contradiction:Double=0.0)
 data class Choice(val question:Question,val informationBits:Double,val utility:Double)
 fun choose(worlds:List<World>,questions:List<Question>,permission:Boolean,maxMillis:Int,maxBytes:Int):Choice? {
  require(worlds.size in 1..64 && worlds.map{it.id}.distinct().size==worlds.size)
  require(worlds.all{it.probability.isFinite()&&it.probability in 0.0..1.0})
  require(kotlin.math.abs(worlds.sumOf{it.probability}-1.0)<1e-9)
  require(questions.size<=64 && maxMillis in 0..60000 && maxBytes in 0..65536)
  val ids=worlds.map{it.id}.toSet()
  require(questions.all { it.millis in 1..60000&&it.bytes in 1..65536&&it.answers.keys==ids&&
   it.novelty.isFinite()&&it.novelty in 0.0..1.0&&it.contradiction.isFinite()&&it.contradiction in 0.0..1.0 })
  if(!permission)return null
  fun entropy(ps:List<Double>)=-ps.filter{it>0}.sumOf{it*ln(it)/ln(2.0)}
  val prior=entropy(worlds.map{it.probability})
  return questions.filter{it.millis<=maxMillis&&it.bytes<=maxBytes}.map { q ->
   val residual=worlds.groupBy{q.answers.getValue(it.id)}.values.sumOf { group ->
    val mass=group.sumOf{it.probability}
    if(mass==0.0)0.0 else mass*entropy(group.map{it.probability/mass})
   }
   val gain=(prior-residual).coerceAtLeast(0.0)
   Choice(q,gain,gain*(1.0+0.25*q.novelty+0.25*q.contradiction)/(q.millis+q.bytes/1024.0))
  }.filter{it.informationBits>=0.1}.sortedWith(compareByDescending<Choice>{it.utility}.thenBy{it.question.id}).firstOrNull()
 }
}

/** D: small explicit Boolean causal model. No causal relations inferred from prose.
 * Unknowns propagate; interventions cut incoming edges; original facts stay unchanged. */
class ResearchCounterfactual(facts:Map<String,Boolean?>,rules:List<Rule>) {
 data class Rule(val output:String,val inputs:List<String>,val kind:Kind,val source:String)
 enum class Kind { ALL, ANY, NOT }
 data class Result(val baseline:Map<String,Boolean?>,val intervention:Map<String,Boolean>,
                   val derived:Map<String,Boolean?>,val unknown:Set<String>,val sources:Map<String,String>)
 private val facts=facts.toMap()
 private val rules=rules.map{it.copy(inputs=it.inputs.toList())}
 init {
  require(facts.size<=64&&rules.size<=64&&rules.map{it.output}.distinct().size==rules.size)
  require(rules.none{it.output in facts}&&rules.all{it.inputs.size in 1..8&&it.source.isNotBlank()})
  require(rules.all{it.kind!=Kind.NOT||it.inputs.size==1})
  val available=facts.keys.toMutableSet()
  rules.forEach{r->require(r.output !in r.inputs&&r.inputs.all{it in available}) {"Unknown input or non-topological/cyclic graph"};available+=r.output}
 }
 fun run(change:Map<String,Boolean>):Result {
  val known=facts.keys+rules.map{it.output}
  require(change.keys.all{it in known}&&change.size<=8)
  val values=facts.toMutableMap();values.putAll(change)
  for(r in rules)if(r.output !in change){
   val v=r.inputs.map{values[it]}
   values[r.output]=when(r.kind){
    Kind.NOT->v.single()?.not()
    Kind.ALL->if(false in v)false else if(null in v)null else true
    Kind.ANY->if(true in v)true else if(null in v)null else false
   }
  }
  return Result(facts,change.toMap(),values.filterKeys{it !in facts&&it !in change},values.filterValues{it==null}.keys,rules.associate{it.output to it.source})
 }
}

/** E: claims are checked against a caller-supplied independent oracle. Repeating
 * the draft/model is never an oracle. The experiment supplies sealed truth tables. */
object ResearchCritic {
 data class Claim(val key:String,val value:Boolean?)
 data class Result(val accepted:List<Claim>,val unsupported:List<Claim>,val checks:Int,val abstained:Boolean)
 fun check(draft:List<Claim>,oracle:Map<String,Boolean?>,budget:Int):Result {
  require(draft.size<=64&&budget in 0..64&&draft.map{it.key}.distinct().size==draft.size)
  val checked=draft.take(budget)
  val accepted=checked.filter{it.key in oracle && oracle[it.key]!=null && oracle[it.key]==it.value}
  val unsupported=draft.filterNot{it in accepted}
  return Result(accepted,unsupported,checked.size,unsupported.isNotEmpty())
 }
}

/** G1: evidence-removal sensitivity. A certificate is a local measurement, not
 * fact authentication. Fresh copies prevent selector mutations changing inputs. */
object EvidenceSensitivity {
 data class Certificate(val answer:String?,val indispensable:Set<String>,val unsupported:Boolean,val calls:Int)
 fun probe(evidence:Map<String,String>,budget:Int,answer:(Map<String,String>)->String?):Certificate {
  require(evidence.size<=32&&budget in 1..33)
  val full=answer(evidence.toMap());var calls=1
  val necessary=linkedSetOf<String>()
  for(id in evidence.keys.sorted()) {
   if(calls==budget)break
   if(answer(evidence.filterKeys{it!=id})!=full)necessary+=id
   calls++
  }
  return Certificate(full,necessary,full!=null&&necessary.isEmpty(),calls)
 }
}

/** G2: reject retrieval that changes solely with candidate order. Uses a fixed
 * bounded cyclic permutation family, so it does not prove every permutation. */
object PermutationConsensus {
 data class Result(val answer:String?,val stable:Boolean,val calls:Int)
 fun select(ids:List<String>,budget:Int=8,rank:(List<String>)->String?):Result {
  require(ids.size in 1..32&&ids.distinct().size==ids.size&&budget in 1..32)
  val outputs=(0 until minOf(budget,ids.size)).map{i->rank(ids.drop(i)+ids.take(i))}
  require(outputs.all{it==null||it in ids})
  val stable=outputs.distinct().size==1 && outputs.size==ids.size
  return Result(if(stable)outputs.first() else null,stable,outputs.size)
 }
}
