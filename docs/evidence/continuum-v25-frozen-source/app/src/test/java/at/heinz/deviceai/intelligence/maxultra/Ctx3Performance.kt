package at.heinz.deviceai.intelligence.maxultra
import at.heinz.deviceai.learning.*
import java.io.File
import java.nio.file.Files
/** Synthetic DEV timing, not independent language validation or a phone benchmark. */
object Ctx3Performance {
 private fun name(i:Int):String="Person"+('A'+i/26)+('A'+i%26)
 @JvmStatic fun main(args:Array<String>) {
  require(args.size==1&&!File(args[0]).exists())
  val reports=arrayListOf<Map<String,Any>>()
  for(n in listOf(0,10,50,128,256))for(on in listOf(false,true))for(length in listOf(3,20)) {
   val dir=Files.createTempDirectory("ctx3-perf-").toFile()
   try {
    val core=ContinuumLearningCore(dir);val facts=(0 until n).map {name(it)+" wohnt in Graz."}
    core.syncOwnerFacts(facts);core.configureContextShadow(on)
    val nanos=arrayListOf<Long>();var errors=0;var outside=0;var clarifies=0;var heapMax=0L
    val runtime=Runtime.getRuntime();System.gc();val heapBefore=runtime.totalMemory()-runtime.freeMemory()
    for(round in 0..4) {
     val chat="perf-"+round
     for(i in 0 until length) {
      if(i==length/2&&facts.isNotEmpty())core.revokeOwnerFact(facts.first())
      val text=if(i==0)"Wo wohnt "+name(0)+"?" else "Wo wohnt er?"
      val start=System.nanoTime()
      try {core.replyFromMemory(text,chat)}catch(e:Exception){errors++}
      nanos.add(System.nanoTime()-start)
      core.contextShadowEvents(chat).lastOrNull()?.let {e->
       if(e.decision=="CLARIFY")clarifies++
       if(e.decision=="ERROR")errors++
       val current=core.activeOwnerFacts().map {it.id}.toSet()
       outside+=e.proposedIds.count {it !in current}
      }
      heapMax=maxOf(heapMax,runtime.totalMemory()-runtime.freeMemory())
     }
     if(facts.isNotEmpty())core.confirmOwnerFact(facts.first())
    }
    val s=nanos.sorted()
    reports.add(linkedMapOf("memories" to n,"shadow" to on,"sequenceLength" to length,"calls" to nanos.size,
     "medianMs" to s[(s.size-1)/2]/1e6,"p95Ms" to s[((s.size-1)*0.95).toInt()]/1e6,
     "observedAdditionalHeapPeakBytes" to maxOf(0,heapMax-heapBefore),"errors" to errors,"clarifies" to clarifies,"unauthorizedProposedIds" to outside))
   } finally {dir.deleteRecursively()}
  }
  File(args[0]).writeText(Ctx1Experiment.json(mapOf("type" to "SYNTHETIC_DEV_PERFORMANCE","notes" to "5 short/long sequences per size/mode; mid-sequence withdrawal then new source revision. In-process heap growth includes allocations/GC, not retained Android RAM. Fixed cold order.","results" to reports))+"\n")
 }
}
