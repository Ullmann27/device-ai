package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.learning.*
import at.heinz.deviceai.learning.context.*
import java.io.File
import java.nio.file.Files
import org.json.JSONArray

/** Independent-author TEST3. Run only after the preregistered code freeze. No parameter training. */
object Ctx3Experiment {
 const val SEALED_SHA256="e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077"
 private fun strings(a:JSONArray)=List(a.length()){a.getString(it)}
 @JvmStatic fun main(args:Array<String>) {
  require(args.size==3&&Regex("[a-f0-9]{40}").matches(args[2])) {"usage: <sealed-corpus.json> <new-report.json> <frozen-commit>"}
  val raw=File(args[0]).readBytes();require(raw.size<=128*1024)
  val sha=ContinuumMemoryDialogue.hash(raw);require(sha==SEALED_SHA256)
  val output=File(args[1]);require(!output.exists()) {"Do not overwrite a research report"}
  val corpus=JSONArray(raw.toString(Charsets.UTF_8));val bytes=Ctx1Experiment.swb1()
  val systems=linkedMapOf<String,Any>();var heapPeak=0L
  for(arm in 0..3) {
   val counts=linkedMapOf<String,Int>();val nanos=ArrayList<Long>();var extra=0;var stateMax=0;var historyMax=0L
   val errors=arrayListOf<String>()
   fun add(k:String){counts[k]=(counts[k]?:0)+1}
   val dir=Files.createTempDirectory("ctx3-final-").toFile()
   try {
    for(d in 0 until corpus.length()) {
     val dialog=corpus.getJSONObject(d);val facts=strings(dialog.getJSONArray("facts"))
     val exps=facts.map {LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,it,now=1)}
     val byId=exps.associate {it.id to it.content};val entities=Ctx3EntityIndex.fromFacts(byId)
     val core=ContinuumMemoryDialogue(dir);core.clear()
     var ctx=Ctx1DialogueContext(facts,arm==2);val ctx3=Ctx3Context()
     val turns=dialog.getJSONArray("turns")
     for(i in 0 until turns.length()) {
      val t=turns.getJSONObject(i);val input=t.getString("text");val expected=t.getString("expected")
      val gold=strings(t.getJSONArray("gold")).toSet()
      require(expected in setOf("QUERY","CLARIFY","ABSTAIN")&&facts.containsAll(gold)&&input.length<=512)
      require(expected!="QUERY"||gold.isNotEmpty())
      var cited=emptyList<String>();var clarified=false;val start=System.nanoTime()
      try {
       when(arm) {
        0->cited=core.reply(bytes,exps,input,i+1L).memoryIds.map {byId.getValue(it)}
        1,2->when(val q=ctx.decide(input)) {
         is Ctx1DialogueContext.Ask->{
          cited=core.reply(bytes,exps.filter {q.scope==null||it.content in q.scope},q.query.take(512),i+1L).memoryIds.map {byId.getValue(it)}
          ctx.observe(cited)
         }
         Ctx1DialogueContext.Clarify->clarified=true
         Ctx1DialogueContext.Unknown->Unit
        }
        3->when(val q=ctx3.decide(input,entities,sha+dialog.getString("id"))) {
         is Ctx3Decision.Query->{
          val hits=ContinuumMemoryDialogue.rank(bytes,q.query,exps.filter {q.sourceIds.isEmpty()||it.id in q.sourceIds}).take(3)
          cited=hits.map {it.text};ctx3.observe(hits.map {it.id}.toSet(),entities)
         }
         is Ctx3Decision.Clarify->clarified=true
         is Ctx3Decision.Abstain->Unit
        }
       }
       extra+=cited.drop(1).count {it !in gold}
       val outcome=when(expected) {
        "QUERY"->when {clarified->"UNNEEDED_CLARIFY";cited.isEmpty()->"MISSED";cited.first() in gold->"CORRECT";else->"WRONG"}
        "CLARIFY"->when {clarified->"CLARIFIED";cited.isEmpty()->"MISSED_CLARIFY";else->"WRONG"}
        else->when {clarified->"UNNEEDED_CLARIFY";cited.isEmpty()->"ABSTAINED";else->"WRONG"}
       }
       add(outcome)
      } catch(e:Exception) {
       add("CRASH");errors.add(dialog.getString("id")+":"+i+":"+e.javaClass.simpleName)
       ctx=Ctx1DialogueContext(facts,arm==2);ctx3.reset();core.clear()
      }
      nanos+=System.nanoTime()-start
      stateMax=maxOf(stateMax,if(arm==3)ctx3.stateSize() else if(arm==0)0 else ctx.stateSize())
      historyMax=maxOf(historyMax,File(dir,"dialogue-v1.bin").length())
      heapPeak=maxOf(heapPeak,Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
     }
    }
   } finally {dir.deleteRecursively()}
   val sorted=nanos.sorted()
   val report=linkedMapOf<String,Any>("counts" to counts,"extraWrongSources" to extra,"maxStateEntries" to stateMax,
    "maxHistoryBytes" to historyMax,"turnMillisMedian" to sorted[(sorted.size-1)/2]/1e6,
    "turnMillisP95" to sorted[((sorted.size-1)*0.95).toInt()]/1e6,"crashes" to errors)
   systems[if(arm==3)"CTX3" else "B$arm"]=report
  }
  val result=linkedMapOf<String,Any>("experiment" to "CTX3","split" to "INDEPENDENT_TEST3","commit" to args[2],"corpusSha256" to sha,
   "dialogs" to corpus.length(),"turns" to (0 until corpus.length()).sumOf {corpus.getJSONObject(it).getJSONArray("turns").length()},
   "systems" to systems,"wholeJvmObservedHeapPeakBytes" to heapPeak,
   "notes" to "Cold fixed arm order; heap peak is whole JVM, not per model or Android. CTX3 uses the exact shadow rank path; B0/B1/B2 use the existing persisted dialogue. Crashes are outcomes, not omitted turns.")
  check(output.createNewFile());output.writeText(Ctx1Experiment.json(result)+"\n");println(Ctx1Experiment.json(result))
 }
}
