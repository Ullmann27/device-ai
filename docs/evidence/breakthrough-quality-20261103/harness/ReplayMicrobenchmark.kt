package at.heinz.deviceai.learning
import java.nio.file.Files
import org.json.JSONObject
import org.json.JSONArray
object ReplayMicrobenchmark {
 @JvmStatic fun main(args:Array<String>) {
  val reports=JSONArray()
  for(size in listOf(384,2048)) {
   val dir=Files.createTempDirectory("qa-replay-bench-$size-").toFile();val store=FileLearningExperienceStore(dir)
   val items=(0 until size).map { n -> LearningExperienceFactory.create(if(n%2==0)LearningExperienceType.USER_FACT else LearningExperienceType.SOURCE_TEXT,
    LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Synthetic benchmark record $n",now=1000L+n) }
   check(store.appendMissing(items)==size)
   for(kind in listOf("same_type","alternating_types")) {
    repeat(4) { store.replay(if(kind=="same_type"||it%2==0)LearningExperienceType.USER_FACT else LearningExperienceType.SOURCE_TEXT,64) }
    val samples=(0 until 21).map { i ->
     val type=if(kind=="same_type"||i%2==0)LearningExperienceType.USER_FACT else LearningExperienceType.SOURCE_TEXT
     val start=System.nanoTime();val actual=store.replay(type,64);val elapsed=(System.nanoTime()-start)/1e6
     check(actual==items.filter { it.type==type }.takeLast(64));elapsed
    }.sorted()
    reports.put(JSONObject().put("records",size).put("workload",kind).put("samplesMillis",JSONArray(samples)).put("medianMillis",samples[10]).put("p95Millis",samples[19]))
   }
  }
  println(JSONObject().put("scope","Host JVM controlled microbenchmark; same-type repeat only, no dialogue/device/learning quality claim").put("reports",reports).toString(2))
 }
}
