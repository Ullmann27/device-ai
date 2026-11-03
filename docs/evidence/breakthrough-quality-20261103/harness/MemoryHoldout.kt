package at.heinz.deviceai.learning
import java.io.File
import java.nio.file.Files
import java.util.Random
import org.json.JSONObject
import org.json.JSONArray
object MemoryHoldout {
 private val allowed=setOf(LearningStatus.OBSERVED,LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING)
 private fun oracle(items:List<LearningExperience>,type:LearningExperienceType,n:Int):List<LearningExperience> = items.filter { it.type==type && it.status in allowed }.sortedWith(Comparator { a,b ->
  if(a.updatedAt!=b.updatedAt) a.updatedAt.compareTo(b.updatedAt) else if(a.createdAt!=b.createdAt) a.createdAt.compareTo(b.createdAt) else a.id.compareTo(b.id)
 }).takeLast(n)
 @JvmStatic fun main(args:Array<String>) {
  val cases=JSONObject(File(args[0]).readText()).getJSONArray("cases"); val out=JSONArray()
  for(i in 0 until cases.length()) {
   val c=cases.getJSONObject(i);val row=JSONObject().put("id",c.getString("id")).put("scenario",c.getString("scenario"));out.put(row)
   val dir=Files.createTempDirectory("qa-replay-${c.getString("id")}-").toFile(); row.put("temporaryDirectory",dir.absolutePath)
   try {
    val random=Random(c.getLong("seed"));val types=c.getJSONArray("types");val statuses=c.getJSONArray("statuses")
    var items=(0 until c.getInt("records")).map { n -> LearningExperienceFactory.create(
     LearningExperienceType.valueOf(types.getString(n%types.length())),LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
     LearningStatus.valueOf(statuses.getString(random.nextInt(statuses.length()))),"QA synthetic ${c.getString("id")} record $n",now=1000L+n/3)
     .let { it.copy(updatedAt=it.createdAt+random.nextInt(50)) } }
    val store=FileLearningExperienceStore(dir);check(store.appendMissing(items)==items.size)
    val limit=c.getInt("limit");val chosen=LearningExperienceType.USER_FACT
    fun verifyAll() {
     for(t in LearningExperienceType.entries) for(n in listOf(limit,1,128,7,64,limit)) {
      check(store.replay(t,n)==oracle(items,t,n)) { "wrong ordering/filter/limit for $t/$n" }
      check(store.replay(t,n)==oracle(items,t,n)) { "wrong hot replay for $t/$n" }
     }
    }
    verifyAll();store.replay(chosen,limit);store.replay(chosen,limit)
    when(c.getString("scenario")) {
     "TYPE_FILTER_AND_RECENCY" -> { val mutable=store.replay(chosen,128).toMutableList();mutable.clear();check(store.replay(chosen,128)==oracle(items,chosen,128)) }
     "REVOKE_FROM_SECOND_INSTANCE" -> {
      val target=oracle(items,chosen,128).last();check(FileLearningExperienceStore(dir).updateStatus(target.id,LearningStatus.REVOKED,9000))
      items=items.map { if(it.id==target.id)it.copy(status=LearningStatus.REVOKED,updatedAt=9000) else it };verifyAll()
     }
     "CORRECTION_STATUS_AND_REPLAY" -> {
      val second=FileLearningExperienceStore(dir)
      items.take(13).forEach { old -> val s=if(old.status in allowed)LearningStatus.REJECTED else LearningStatus.CONFIRMED;check(second.updateStatus(old.id,s,10000));items=items.map { if(it.id==old.id)it.copy(status=s,updatedAt=10000) else it } }
      verifyAll()
     }
     "DELETE_AND_RECREATE" -> {
      val moved=File(dir.parentFile,dir.name+"-retained");check(dir.renameTo(moved));check(store.replay(chosen,limit).isEmpty())
      items=listOf(LearningExperienceFactory.create(chosen,LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Replacement ${c.getString("id")}",now=12000))
      check(FileLearningExperienceStore(dir).appendMissing(items)==1);verifyAll()
     }
     "UNSELECTED_SEGMENT_CORRUPTION","SELECTED_SEGMENT_CORRUPTION","SAME_LENGTH_TAMPER" -> {
      val selected=oracle(items,chosen,limit).map { it.id }.toSet()
      val segments=File(dir,"experiences-v3").listFiles()!!.filter { it.extension=="seg" }
      val file=if(c.getString("scenario")=="UNSELECTED_SEGMENT_CORRUPTION")segments.first { LearningExperienceCodec.decode(it.readBytes()).none { r->r.id in selected } }
       else segments.first { LearningExperienceCodec.decode(it.readBytes()).any { r->r.id in selected } }
      val bytes=file.readBytes();val mtime=Files.getLastModifiedTime(file.toPath());val damaged=bytes.copyOf();damaged[damaged.size/2]=(damaged[damaged.size/2].toInt() xor 1).toByte();file.writeBytes(damaged);Files.setLastModifiedTime(file.toPath(),mtime)
      check(runCatching { store.replay(chosen,limit) }.isFailure) { "cached result escaped corrupt segment" }
      check(runCatching { store.replay(chosen,limit) }.isFailure) { "corrupt segment accepted on retry" }
      file.writeBytes(bytes);Files.setLastModifiedTime(file.toPath(),mtime);verifyAll()
     }
     "LIMIT_REJECTION" -> { for(n in listOf(Int.MIN_VALUE,-1,0,129,Int.MAX_VALUE))check(runCatching { store.replay(chosen,n) }.isFailure);verifyAll() }
     else -> error("unknown scenario")
    }
    row.put("pass",true).put("records",items.size)
   } catch(e:Throwable) { row.put("pass",false).put("error",e.stackTraceToString()) }
  }
  File(args[1]).writeText(JSONObject().put("cases",out).toString(2)+"\n")
  println("cases=${out.length()} pass=${(0 until out.length()).count { out.getJSONObject(it).getBoolean("pass") }}")
 }
}
