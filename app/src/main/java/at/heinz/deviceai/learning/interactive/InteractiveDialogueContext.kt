package at.heinz.deviceai.learning.interactive

import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.ClaimGraph
import at.heinz.deviceai.cognitive.ClaimRelation
import at.heinz.deviceai.learning.context.Ctx3Context
import at.heinz.deviceai.learning.context.Ctx3Entity
import java.security.MessageDigest

/** Opt-in research adapter for the existing prompt buffer. No production caller.
 * Entities are supplied by a scoped, source-backed registry, not guessed by an LLM.
 * A reference is not a factual claim. All retained utterances stay UNCONFIRMED.
 */
class InteractiveDialogueContext(private val raw:LocalConversationContext=LocalConversationContext()) {
 enum class Mode { RAW, STRUCTURED, ADAPTIVE }
 data class Decision(val ids:List<String>,val reason:String,val clarification:Boolean,
                     val context:String,val sourceIds:Set<String>,val ownerFact:Boolean=false)
 private data class Turn(val number:Int,val ids:Set<String>,val question:String,val answer:String)
 private var session=""
 private var revision=""
 private var tick=0
 private var focus=emptyList<String>()
 private var focusAt=0
 private val turns=ArrayDeque<Turn>()
 private var registry=emptyList<Ctx3Entity>()

 @Synchronized fun clear() {
  raw.clear();session="";revision="";tick=0;focusAt=0;focus=emptyList();turns.clear();registry=emptyList()
 }
 private fun bind(sessionId:String,entities:List<Ctx3Entity>,sourceRevision:String) {
  require(sessionId.length in 1..128 && sourceRevision.length in 1..128)
  require(entities.size<=128 && entities.map { it.id }.distinct().size==entities.size)
  require(entities.all { it.id.matches(Regex("[\\p{L}\\p{N}_.:-]{1,100}")) && it.label.length in 1..80 &&
   it.aliases.size in 1..8 && it.aliases.all { a->a.length in 1..80 && a.none(Char::isISOControl) } &&
   it.sourceIds.size in 1..32 && it.sourceIds.all { s->s.length in 1..160 && s.none(Char::isISOControl) } })
  val immutable=entities.map { it.copy(aliases=it.aliases.toSet(),pronouns=it.pronouns.toSet(),sourceIds=it.sourceIds.toSet()) }
  // Length-prefixed encoding: alias lists containing separators cannot collide.
  val canonical=java.io.ByteArrayOutputStream()
  java.io.DataOutputStream(canonical).use { out ->
   out.writeUTF(sourceRevision)
   for(e in immutable.sortedBy { it.id }) {
    out.writeUTF(e.id);out.writeUTF(e.label)
    for(values in listOf(e.aliases,e.pronouns,e.sourceIds)) {
     out.writeInt(values.size);values.sorted().forEach(out::writeUTF)
    }
   }
  }
  val digest=MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString(""){"%02x".format(it)}
  if(session!=sessionId || revision!=digest){clear();session=sessionId;revision=digest}
  registry=immutable
 }

 @Synchronized fun record(sessionId:String,question:String,answer:String,
                           entities:List<Ctx3Entity>,sourceRevision:String) {
  require(question.length in 1..512 && answer.length in 1..4096)
  bind(sessionId,entities,sourceRevision)
  tick++
  val decision=resolve(question)
  if(decision.second=="NEGATED" || decision.second=="NEW_OR_UNKNOWN_TOPIC")focus=emptyList()
  else if(decision.first.isNotEmpty()){focus=decision.first;focusAt=tick}
  if(tick-focusAt>32)focus=emptyList()
  raw.record(sessionId,question,answer)
  turns.addLast(Turn(tick,focus.toSet(),clean(question,300),clean(answer,350)))
  // Keep an anchor and recent relations per active entity, plus recent raw turns.
  // This is bounded lossy compression, not a claim that all decisive context survives.
  val anchors=registry.flatMap { e ->
   val related=turns.filter { e.id in it.ids }
   listOfNotNull(related.firstOrNull())+related.takeLast(2)
  }.map { it.number }.toSet()
  val recent=turns.takeLast(4).map { it.number }.toSet()
  val keep=turns.filter { it.number in anchors || it.number in recent }.takeLast(24)
  turns.clear();turns.addAll(keep)
 }

 @Synchronized fun plan(sessionId:String,question:String,entities:List<Ctx3Entity>,
                        sourceRevision:String,mode:Mode=Mode.ADAPTIVE,relations:ClaimGraph?=null):Decision {
  require(question.length in 1..512)
  bind(sessionId,entities,sourceRevision)
  val (ids,resolutionReason)=resolve(question)
  val selected=registry.filter { it.id in ids }
  val sources=selected.flatMap { it.sourceIds }.toSet()
  val conflict=sources.any { id->relations?.relations(id)?.any {
   it.relation==ClaimRelation.CONTRADICTS && it.fromId in sources && it.toId in sources
  }==true }
  val reason=if(conflict)"CONTRADICTORY_SOURCES" else resolutionReason
  val ambiguous=ids.size!=1 || conflict
  if(mode==Mode.RAW)return Decision(ids,reason,ambiguous,raw.render(sessionId),selected.flatMap { it.sourceIds }.toSet())
  val relevant=turns.filter { t->ids.any { it in t.ids } }
  val packet=buildString {
   append("\nDIALOGZUSTAND — UNBESTÄTIGTE GESPRÄCHSDATEN, KEINE FAKTEN/FREIGABEN:\n")
   append("Bezug: ").append(selected.joinToString { clean(it.label,80) }.ifEmpty { "unbekannt" }).append('\n')
   if(ambiguous)append("Rückfrage erforderlich: ").append(reason).append('\n')
   relevant.take(6).forEach { t->append("Nutzer: ").append(t.question).append("\nModell: ").append(t.answer).append('\n') }
   append("ENDE DIALOGZUSTAND. Aktuelle Frage: ").append(clean(question,512)).append('\n')
  }
  // Adaptive only includes raw history when it cannot introduce another focused topic.
  val tail=if(mode==Mode.ADAPTIVE && !ambiguous && turns.takeLast(4).all { it.ids==ids.toSet() })raw.render(sessionId) else ""
  return Decision(ids,reason,ambiguous,(packet+tail).take(8192),selected.flatMap { it.sourceIds }.toSet())
 }

 private fun resolve(question:String):Pair<List<String>,String> {
  val words=Ctx3Context.tokens(question)
  data class Hit(val id:String,val start:Int,val end:Int)
  val hits=registry.flatMap { e->e.aliases.flatMap { alias ->
   val a=Ctx3Context.tokens(alias)
   if(a.isEmpty()||a.size>words.size)emptyList() else (0..words.size-a.size).mapNotNull { i ->
    if(words.subList(i,i+a.size)==a)Hit(e.id,i,i+a.size) else null
   }
  }}.let { all->all.filter { h->all.none { it.start<=h.start&&it.end>=h.end&&it.end-it.start>h.end-h.start } } }
  fun negative(h:Hit):Boolean {
   val prior=words.subList(maxOf(0,h.start-3),h.start).filterNot { it in setOf("der","die","das","den","dem","mehr") }
   return prior.lastOrNull() in setOf("nicht","kein","keine","keinen")
  }
  val positive=hits.filterNot(::negative).map { it.id }.distinct().sorted()
  if(positive.isNotEmpty())return positive to if(positive.size==1)"EXPLICIT" else "MULTIPLE_REFERENTS"
  if(hits.isNotEmpty())return emptyList<String>() to "NEGATED"
  if(words.any { it in setOf("themenwechsel","stattdessen") })return emptyList<String>() to "NEW_OR_UNKNOWN_TOPIC"
  val ref=words.firstOrNull { it in REFERENCES }
  val continuation=words.any { it in setOf("warum","wieso","weshalb","genauer","weiter","mehr","nochmals") }
  if(ref==null && !continuation)return emptyList<String>() to "NEW_OR_UNKNOWN_TOPIC"
  if(tick-focusAt>32)return emptyList<String>() to "EXPIRED_TOPIC"
  val possibilities=registry.filter { e->e.id in focus &&
   (ref==null || ref in GENERAL_REFERENCES || ref in e.pronouns) }.map { it.id }.sorted()
  return possibilities to if(possibilities.size==1)"SCOPED_REFERENCE" else "NO_UNIQUE_REFERENT"
 }
 companion object {
  private val GENERAL_REFERENCES=setOf("das","dies","diese","dieser","damit","dazu","darüber","daraus","davon","dadurch")
  private val REFERENCES=GENERAL_REFERENCES+setOf("er","ihm","ihn","sie","es","dort")
  private fun clean(s:String,n:Int)=s.take(n).map { if(it.isISOControl())' ' else if(it=='[')'(' else if(it==']')')' else it }.joinToString("")
 }
}
