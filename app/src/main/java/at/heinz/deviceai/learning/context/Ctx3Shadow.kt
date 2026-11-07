package at.heinz.deviceai.learning.context

import at.heinz.deviceai.learning.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Opt-in, read-only comparison. No response replacement, fact insertion, tool call or weight update. */
object Ctx3Shadow {
 data class Event(
  val inputSha256:String,val decision:String,val reason:String,val baselineIds:List<String>,val proposedIds:List<String>,
  val conversationSha256:String="",val modelSha256:String="",val sourceVersion:String="",
  val stateTelegramStatus:String="",val stateTelegramSha256:String="",val stateTelegramBytes:Int=0,
  val rawSelectedBytes:Int=0,val stateKeyCount:Int=0,val stateConflictCount:Int=0,val stateUnresolvedCount:Int=0,
  val referenceResolution:String=""
 )
 private data class Session(val context:Ctx3Context=Ctx3Context(),val events:MutableList<Event> = arrayListOf(),var touched:Long=System.nanoTime())
 private data class Key(val directory:String,val conversationId:String)
 private val sessions=linkedMapOf<Key,Session>()
 private fun key(dir:File,id:String):Key {
  require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}").matches(id)){"Invalid conversation identity"}
  return Key(dir.canonicalPath,id)
 }
 @Synchronized fun reset(dir:File,conversationId:String){sessions.remove(key(dir,conversationId))}
 private fun flag(dir:File)=File(dir,"ctx3-shadow-v1.enabled")
 @Synchronized fun enabled(dir:File)=runCatching { flag(dir).let {it.isFile&&it.length() in 7..16&&it.readText()=="SHADOW-v1\n"} }.getOrDefault(false)
 @Synchronized fun configure(dir:File,on:Boolean) {
  check(dir.isDirectory||dir.mkdirs())
  val tmp=File.createTempFile("ctx3-",".tmp",dir)
  try {
   tmp.writeText(if(on)"SHADOW-v1\n" else "OFF-v1\n")
   Files.move(tmp.toPath(),flag(dir).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
  } finally {tmp.delete()}
  sessions.keys.removeAll {it.directory==dir.canonicalPath}
 }
 @Synchronized fun events(dir:File,conversationId:String):List<Event> = sessions[key(dir,conversationId)]?.events?.toList().orEmpty()
 /** Owner-local diagnostics only; never used as context for another conversation. */
 @Synchronized fun recentEvents(dir:File):List<Event> = sessions.filterKeys {it.directory==dir.canonicalPath}.values.flatMap {it.events}.takeLast(32)
 @Synchronized fun evaluate(dir:File,bytes:ByteArray,experiences:List<LearningExperience>,input:String,baseline:List<String>,conversationId:String,
  trace:at.heinz.deviceai.cognitive.CognitiveTurnTrace?=null) {
  val key=key(dir,conversationId)
  if(!enabled(dir)){trace?.event(at.heinz.deviceai.cognitive.TraceStage.CTX3_OFF);return}
  trace?.event(at.heinz.deviceai.cognitive.TraceStage.CTX3_SHADOW)
  val now=System.nanoTime()
  sessions.entries.removeAll {now-it.value.touched>15*60*1_000_000_000L}
  val s=sessions.remove(key)?:Session()
  s.touched=now;sessions[key]=s
  while(sessions.size>16)sessions.remove(sessions.keys.first())
  val temporalRecords=experiences.filter { it.type==LearningExperienceType.USER_FACT&&
   it.provenance==LearningProvenance.USER_CONFIRMED&&it.trust==LearningTrustClass.OWNER_CONFIRMED }
  val facts=temporalRecords.filter {
   it.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING)
  }
  val event=runCatching {
   require(facts.size<=256&&facts.all { it.content.length<=4096 })
   val pool=facts.associate { it.id to it.content }
   // Revocation/confirmation changes reference identity even when the literal text is unchanged.
   val version=ContinuumMemoryDialogue.hash(temporalRecords.sortedBy { it.id }.joinToString("\u0000"){
    it.id+"\u0000"+it.contentHash+"\u0000"+it.status.name+"\u0000"+it.updatedAt
   }.toByteArray())
   val entities=Ctx3EntityIndex.fromFacts(pool)
   val decision=s.context.decide(input,entities,version)
   val result=when(decision) {
    is Ctx3Decision.Query->{
     val allowed=if(decision.sourceIds.isEmpty())facts else facts.filter { it.id in decision.sourceIds }
     val hits=ContinuumMemoryDialogue.rank(bytes,decision.query,allowed).take(3).map { it.id }
     s.context.observe(hits.toSet(),entities)
     val telegram=if(hits.isEmpty())null else runCatching {
      Ctx3StateTelegram.compile(
       ContinuumTemporalMemory.fromLedger(temporalRecords),hits.toSet(),System.currentTimeMillis())
     }.getOrNull()
     Event(
      ContinuumMemoryDialogue.hash(input.toByteArray()),"QUERY",
      if(decision.sourceIds.isEmpty())"UNRESOLVED_RETRIEVAL_SCOPE" else "CURRENT_CONFIRMED_SOURCE_SCOPE",
      baseline.take(3),hits,
      stateTelegramStatus=when { hits.isEmpty()->"NO_SELECTED_SOURCES";telegram==null->"SAFE_SHADOW_FAILURE";else->"OK" },
      stateTelegramSha256=telegram?.sha256.orEmpty(),
      stateTelegramBytes=telegram?.telegramBytes?:0,
      rawSelectedBytes=telegram?.rawSelectedBytes?:0,
      stateKeyCount=telegram?.keyCount?:0,
      stateConflictCount=telegram?.conflictCount?:0,
      stateUnresolvedCount=telegram?.unresolvedCount?:0,
      referenceResolution=decision.resolution
     )
    }
    is Ctx3Decision.Clarify->Event(ContinuumMemoryDialogue.hash(input.toByteArray()),"CLARIFY",decision.reason,baseline.take(3),emptyList())
    is Ctx3Decision.Abstain->Event(ContinuumMemoryDialogue.hash(input.toByteArray()),"ABSTAIN",decision.reason,baseline.take(3),emptyList())
   }
   result.copy(conversationSha256=ContinuumMemoryDialogue.hash(conversationId.toByteArray()),modelSha256=ContinuumMemoryDialogue.hash(bytes),sourceVersion=version)
  }.getOrElse {s.context.reset();Event(ContinuumMemoryDialogue.hash(input.take(512).toByteArray()),"ERROR","SAFE_SHADOW_FAILURE",baseline.take(3),emptyList())}
  s.events.add(event);while(s.events.size>32)s.events.removeAt(0)
 }
}
