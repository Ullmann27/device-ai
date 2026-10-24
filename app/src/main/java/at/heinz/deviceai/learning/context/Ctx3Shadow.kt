package at.heinz.deviceai.learning.context

import at.heinz.deviceai.learning.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Opt-in, read-only comparison. No response replacement, fact insertion, tool call or weight update. */
object Ctx3Shadow {
 data class Event(val inputSha256:String,val decision:String,val reason:String,val baselineIds:List<String>,val proposedIds:List<String>)
 private data class Session(val context:Ctx3Context=Ctx3Context(),val events:MutableList<Event> = arrayListOf())
 private val sessions=linkedMapOf<String,Session>()
 private fun flag(dir:File)=File(dir,"ctx3-shadow-v1.enabled")
 @Synchronized fun enabled(dir:File)=runCatching { flag(dir).readText()=="SHADOW-v1\n" }.getOrDefault(false)
 @Synchronized fun configure(dir:File,on:Boolean) {
  check(dir.isDirectory||dir.mkdirs())
  val tmp=File.createTempFile("ctx3-",".tmp",dir)
  try {
   tmp.writeText(if(on)"SHADOW-v1\n" else "OFF-v1\n")
   Files.move(tmp.toPath(),flag(dir).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
  } finally {tmp.delete()}
  sessions.remove(dir.canonicalPath)
 }
 @Synchronized fun events(dir:File):List<Event> = sessions[dir.canonicalPath]?.events?.toList().orEmpty()
 @Synchronized fun evaluate(dir:File,bytes:ByteArray,experiences:List<LearningExperience>,input:String,baseline:List<String>) {
  if(!enabled(dir))return
  val key=dir.canonicalPath
  val s=sessions.getOrPut(key){Session()}
  while(sessions.size>4)sessions.remove(sessions.keys.first())
  val facts=experiences.filter { it.type==LearningExperienceType.USER_FACT&&
   it.provenance==LearningProvenance.USER_CONFIRMED&&it.trust==LearningTrustClass.OWNER_CONFIRMED&&
   it.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING) }
  val event=runCatching {
   require(facts.size<=256&&facts.all { it.content.length<=4096 })
   val pool=facts.associate { it.id to it.content }
   val version=ContinuumMemoryDialogue.hash(pool.entries.sortedBy { it.key }.joinToString("\u0000"){it.key+"\u0000"+it.value}.toByteArray())
   val entities=Ctx3EntityIndex.fromFacts(pool)
   val decision=s.context.decide(input,entities,version)
   when(decision) {
    is Ctx3Decision.Query->{
     val allowed=if(decision.sourceIds.isEmpty())facts else facts.filter { it.id in decision.sourceIds }
     val hits=ContinuumMemoryDialogue.rank(bytes,decision.query,allowed).take(3).map { it.id }
     s.context.observe(hits.toSet(),entities)
     Event(ContinuumMemoryDialogue.hash(input.toByteArray()),"QUERY","CURRENT_CONFIRMED_SOURCE_SCOPE",baseline.take(3),hits)
    }
    is Ctx3Decision.Clarify->Event(ContinuumMemoryDialogue.hash(input.toByteArray()),"CLARIFY",decision.reason,baseline.take(3),emptyList())
    is Ctx3Decision.Abstain->Event(ContinuumMemoryDialogue.hash(input.toByteArray()),"ABSTAIN",decision.reason,baseline.take(3),emptyList())
   }
  }.getOrElse {s.context.reset();Event(ContinuumMemoryDialogue.hash(input.take(512).toByteArray()),"ERROR","SAFE_SHADOW_FAILURE",baseline.take(3),emptyList())}
  s.events.add(event);while(s.events.size>32)s.events.removeAt(0)
 }
}
