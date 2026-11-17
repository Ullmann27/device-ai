package at.heinz.deviceai.learning.context

import java.util.Locale

/** Explicit evidence identities. Not personal memory, tool authority or a trained model. */
data class Ctx3Entity(val id:String,val label:String,val aliases:Set<String>,val pronouns:Set<String>,val sourceIds:Set<String>)
sealed interface Ctx3Decision {
 data class Query(val query:String,val sourceIds:Set<String> = emptySet()):Ctx3Decision
 data class Clarify(val reason:String):Ctx3Decision
 data class Abstain(val reason:String):Ctx3Decision
}

/** Candidate-set reference gate; registry parsing is deliberately separate from reference decisions. */
class Ctx3Context {
 private var version=""
 private var focus=emptyList<Ctx3Entity>()
 private var selectedFocusIndex=-1
 private var age=0
 fun reset(){focus=emptyList();selectedFocusIndex=-1;age=0;version=""}
 fun stateSize()=focus.size
 fun decide(input:String,entities:List<Ctx3Entity>,sourceVersion:String):Ctx3Decision {
  if(version!=sourceVersion){reset();version=sourceVersion}
  age++;if(age>32){focus=emptyList();selectedFocusIndex=-1}
  if(input.isBlank()||input.length>512||entities.size>128)return Ctx3Decision.Clarify("INPUT_OR_REGISTRY_LIMIT")
  if(entities.any { it.label.isBlank()||it.label.length>80||it.aliases.size>8||it.sourceIds.isEmpty()||it.sourceIds.size>128 })
   return Ctx3Decision.Clarify("INVALID_SOURCE_REGISTRY")
  val words=tokens(input)
  if(words.any { it in setOf("wenn","falls","würde","hätte","installier","installiere","lösche","starte","öffne") }||
   input.any { it in "\"„“»«" })return Ctx3Decision.Clarify("CONDITIONAL_OR_ACTION_NOT_AUTHORIZED")
  data class Mention(val e:Ctx3Entity,val start:Int,val end:Int)
  val hits=entities.flatMap { e->e.aliases.flatMap { alias->
   val a=tokens(alias)
   if(a.isEmpty()||a.size>8)emptyList() else (0..words.size-a.size).mapNotNull { i->
    if(words.subList(i,i+a.size)==a)Mention(e,i,i+a.size)else null
   }
  }}
  // Prefer a full compound alias over contained generic nouns. Equal alias spans
  // retain every entity: a collision is ambiguity, never registry order.
  val mentions=hits.filter { h->hits.none { it.start<=h.start&&it.end>=h.end&&(it.end-it.start)>(h.end-h.start) } }
  val trailingCorrection = words.lastOrNull()=="nicht"&&words.any { it in setOf("meine","meinte","gemeint") }
  fun negative(i:Int):Boolean {
   if(trailingCorrection)return true
   var before=i-1
   while(before>=0&&words[before] in setOf("der","die","das","den","dem","mehr"))before--
   return before>=0&&words[before] in setOf("nicht","kein","keine","keinen")
  }
  val rejected=mentions.filter { negative(it.start) }.map { it.e.id }.toSet()
  val positive=mentions.filterNot { negative(it.start) }.sortedBy { it.start }.map { it.e }.distinctBy { it.id }
  if(rejected.isNotEmpty()) {
   val selectedId=focus.getOrNull(selectedFocusIndex)?.id
   focus=focus.filterNot { it.id in rejected }
   selectedFocusIndex=selectedId?.let { id->focus.indexOfFirst { it.id==id } } ?: -1
  }
  val covered=hits.flatMap { (it.start until it.end).toList() }.toSet()
  val ordinary=setOf("wer","was","wie","wo","warum","geht","startet","und","ich","nicht","nein","erzähl","erzähle","kannst","kann","bitte","servus","hallo","farbe","alter","name","jahre","hilfe","text","app","gerät","projekt","datei","notizen","zähler","montag","dienstag","mittwoch","donnerstag","freitag","samstag","sonntag","sie","er","es","nimm","meine","meinte","gemeint","der","die","das","davor","andere","anderen","anderes","anderer","erste","ersten","erstes","erster","zweite","zweiten","zweites","zweiter","variante","option")
  val unknown=Regex("[\\p{L}\\p{N}]+").findAll(input).map { it.value }.withIndex().any {
   it.index !in covered&&it.value.first().isUpperCase()&&it.value.lowercase(Locale.ROOT) !in ordinary
  }
  if(unknown){focus=emptyList();selectedFocusIndex=-1;return if(positive.isNotEmpty())Ctx3Decision.Clarify("MIXED_UNKNOWN_REFERENT")else Ctx3Decision.Abstain("NO_CONFIRMED_ENTITY")}
  if(positive.isNotEmpty()) {
   if(rejected.isNotEmpty()&&positive.size!=1){focus=emptyList();selectedFocusIndex=-1;return Ctx3Decision.Clarify("MULTIPLE_REPLACEMENTS")}
   // A single surface span which denotes several entities is not a multi-entity question.
   if(mentions.filterNot { negative(it.start) }.groupBy { it.start to it.end }.any { it.value.map { m->m.e.id }.distinct().size>1 })
    {focus=emptyList();selectedFocusIndex=-1;return Ctx3Decision.Clarify("ALIAS_COLLISION")}
   focus=positive.takeLast(8);selectedFocusIndex=if(focus.size==1)0 else -1;age=0
   return Ctx3Decision.Query(input,positive.flatMap { it.sourceIds }.toSet())
  }
  if(rejected.isNotEmpty()){focus=emptyList();selectedFocusIndex=-1;return Ctx3Decision.Clarify("ONLY_NEGATED_ENTITIES")}

  // Ordered deictic selectors are deterministic only inside an already established candidate set.
  // They never invent a new entity and fail closed when no unique position exists.
  val selectors=orderedDeictics(input)
  if(selectors.isNotEmpty()) {
   val accepted=selectors.filterNot {
    val before=tokens(input.substring(0,it.range.first))
    val after=tokens(input.substring(it.range.last+1))
    val predicates=setOf("startet","geht")
    val predicateNegation=(before.lastOrNull() in predicates&&after==listOf("nicht"))||
     (after.size==2&&after[0] in predicates&&after[1]=="nicht")
    // Unknown negations remain rejected.
    negative(before.size)||(words.lastOrNull()=="nicht"&&!predicateNegation)
   }
   val contrast=selectors.size==2&&accepted.size==1&&accepted.single()==selectors.last()&&
    selectors.all { it.kind=="FIRST"||it.kind=="SECOND" }&&selectors.first().kind!=selectors.last().kind&&
    Regex("(?iu)\\bsondern\\b").containsMatchIn(input.substring(selectors.first().range.last+1,selectors.last().range.first))
   if(accepted.size!=1||(selectors.size>1&&!contrast)) {
    focus=emptyList();selectedFocusIndex=-1
    return Ctx3Decision.Clarify("NO_UNIQUE_POSITIVE_ORDERED_REFERENT")
   }
   val deictic=accepted.single()
   fun unresolved(reason:String):Ctx3Decision.Clarify {
    if(selectors.size>accepted.size){focus=emptyList();selectedFocusIndex=-1}
    return Ctx3Decision.Clarify(reason)
   }
   if(focus.size<2)return unresolved("NO_ORDERED_ALTERNATIVES")
   val targetIndex=when(deictic.kind) {
    "FIRST"->0
    "SECOND"->1
    "PREVIOUS"->if(selectedFocusIndex>0)selectedFocusIndex-1 else -1
    "OTHER"->if(focus.size==2&&selectedFocusIndex in focus.indices)1-selectedFocusIndex else -1
    else->-1
   }
   if(targetIndex !in focus.indices)return unresolved("NO_UNIQUE_ORDERED_REFERENT")
   val target=focus[targetIndex]
   selectedFocusIndex=targetIndex;age=0
   return Ctx3Decision.Query(input.replaceRange(deictic.range,target.label),target.sourceIds)
  }

  val ref=words.withIndex().firstOrNull { it.value in setOf("er","ihn","ihm","sie","es","damit","dazu","darüber","des") }
  if(ref!=null) {
   if(negative(ref.index)||trailingCorrection){focus=emptyList();selectedFocusIndex=-1;return Ctx3Decision.Clarify("REJECTED_PRONOUN")}
   val possible=focus.filter { ref.value in it.pronouns || ref.value in setOf("damit","dazu","darüber","des") }.distinctBy { it.id }
   if(possible.size!=1)return Ctx3Decision.Clarify("NO_UNIQUE_REFERENT")
   val target=possible.single();selectedFocusIndex=focus.indexOfFirst { it.id==target.id };age=0
   val rewritten=input.replace(Regex("(?iu)(?<![\\p{L}\\p{N}])"+Regex.escape(ref.value)+"(?![\\p{L}\\p{N}])"),Regex.escapeReplacement(target.label))
   return Ctx3Decision.Query(rewritten,target.sourceIds)
  }
  if(Regex("(?u)\\b(?:von|über|sondern|ist)\\s+(\\p{Lu}[\\p{L}-]+)").findAll(input).any { m->
    val word=m.groupValues[1].lowercase(Locale.ROOT)
    word !in setOf("Montag","Dienstag","Mittwoch","Donnerstag","Freitag","Samstag","Sonntag").map { it.lowercase(Locale.ROOT) }&&
     entities.none { e->e.aliases.any { word in tokens(it) } }
   })return Ctx3Decision.Abstain("NO_CONFIRMED_ENTITY")
  return Ctx3Decision.Query(input)
 }
 fun observe(ids:Set<String>,entities:List<Ctx3Entity>) {
  val matches=entities.filter { it.sourceIds.intersect(ids).isNotEmpty() }
  if(matches.size==1) {
   val match=matches.single()
   val existing=focus.indexOfFirst { it.id==match.id }
   if(existing>=0&&focus.size>1)selectedFocusIndex=existing
   else {focus=matches;selectedFocusIndex=0}
   age=0
  }
 }

 private data class Deictic(val kind:String,val range:IntRange)

 private fun orderedDeictics(input:String):List<Deictic> = ORDERED_PATTERNS.flatMap { (kind,pattern)->
  pattern.findAll(input).map { Deictic(kind,it.range) }.toList()
 }.sortedBy { it.range.first }

 companion object {
  private val ORDERED_PATTERNS=listOf(
   "PREVIOUS" to "(?iu)\\b(?:der|die|das|variante|option)\\s+davor\\b",
   "OTHER" to "(?iu)\\b(?:der|die|das)\\s+ander(?:e|en|es|er)\\b",
   "FIRST" to "(?iu)\\b(?:die\\s+)?erste(?:n|s|r)?(?:\\s+variante|\\s+option)?\\b",
   "SECOND" to "(?iu)\\b(?:die\\s+)?zweite(?:n|s|r)?(?:\\s+variante|\\s+option)?\\b"
  ).map { (kind,pattern)->kind to Regex(pattern) }
  fun tokens(text:String)=Regex("[\\p{L}\\p{N}]+",RegexOption.IGNORE_CASE).findAll(text).map { it.value.lowercase(Locale.ROOT) }.toList()
 }
}

/** Conservative surface reader; unsupported fact grammar stays unsupported. No truth inferred from a turn. */
object Ctx3EntityIndex {
 fun fromFacts(facts:Map<String,String>):List<Ctx3Entity> {
  require(facts.size<=256&&facts.all { it.value.length<=4096 })
  val names=linkedMapOf<String,MutableSet<String>>()
  val genders=mutableMapOf<String,MutableSet<String>>()
  for((id,text) in facts) {
   val matches=listOfNotNull(
    Regex("^(?!Ich\\b|Wir\\b|Mein\\b|Meine\\b|Unser\\b|Unsere\\b|Der\\b|Die\\b|Das\\b)(\\p{Lu}[\\p{L}-]+(?: \\p{Lu}[\\p{L}-]+)*)(?=,| ist | wohnt | lebt | hat | arbeitet | frisst | startet )").find(text)?.groupValues?.get(1),
    Regex("\\b(?:heißt|namens) (\\p{Lu}[\\p{L}-]+(?: \\p{Lu}[\\p{L}-]+)*)").find(text)?.groupValues?.get(1),
    Regex("^(?:Mein|Meine|Unser|Unsere|Der|Die|Das) (\\p{Lu}[\\p{L}-]+)(?= ist | hat | steht | liegt )").find(text)?.groupValues?.get(1))
   for(name in matches) {
    names.getOrPut(name){linkedSetOf()}.add(id)
    val g=when {
     Regex("\\b(?:meine|eine|unsere) (?:Schwester|Kollegin|Tante|Nichte|Katze)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text)->setOf("sie")
     Regex("\\b(?:mein|einen|unser) (?:Bruder|Onkel|Cousin|Hund)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text)->setOf("er","ihn","ihm")
     text.startsWith("Die ")||text.startsWith("Meine ")->setOf("sie")
     text.startsWith("Das ")->setOf("es")
     else->setOf("er","ihn","ihm","sie","es")
    }
    if(name !in genders)genders[name]=g.toMutableSet() else genders.getValue(name).retainAll(g)
   }
  }
  return names.entries.take(128).map { (name,ids)->Ctx3Entity(name.lowercase(Locale.ROOT),name,setOf(name),genders.getValue(name),ids.toSet()) }
 }
}
