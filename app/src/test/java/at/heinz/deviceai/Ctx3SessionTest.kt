package at.heinz.deviceai
import at.heinz.deviceai.learning.*
import at.heinz.deviceai.learning.context.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.Executors
class Ctx3SessionTest {
 @Test fun discourseReturnNeverCrossesConversationNamespace() {
  val dir=Files.createTempDirectory("ctx3-discourse-session").toFile()
  val bytes=Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
  val max=LearningExperienceFactory.create(
   LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,
   LearningStatus.CONFIRMED,"Max wohnt in Graz.",now=1)
  val anna=LearningExperienceFactory.create(
   LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,
   LearningStatus.CONFIRMED,"Anna wohnt in Wels.",now=2)
  try {
   Ctx3Shadow.configure(dir,true)
   Ctx3Shadow.evaluate(dir,bytes,listOf(max,anna),"Wo wohnt Max?",emptyList(),"chat-a")
   Ctx3Shadow.evaluate(dir,bytes,listOf(max,anna),"Wo wohnt Anna?",emptyList(),"chat-a")
   Ctx3Shadow.evaluate(dir,bytes,listOf(max,anna),"Und davor?",emptyList(),"chat-a")
   assertEquals("DISCOURSE_PREVIOUS",Ctx3Shadow.events(dir,"chat-a").last().referenceResolution)
   assertEquals(listOf(max.id),Ctx3Shadow.events(dir,"chat-a").last().proposedIds)

   Ctx3Shadow.evaluate(dir,bytes,listOf(max,anna),"Und davor?",emptyList(),"chat-b")
   assertEquals("CLARIFY",Ctx3Shadow.events(dir,"chat-b").last().decision)
   assertTrue(Ctx3Shadow.events(dir,"chat-b").last().proposedIds.isEmpty())
  } finally {dir.deleteRecursively()}
 }

 @Test fun sessionsNamespacesWithdrawalAndConcurrentQueriesStayIsolated() {
  val dir=Files.createTempDirectory("ctx3-sessions").toFile()
  val other=Files.createTempDirectory("ctx3-profile").toFile()
  val bytes=Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
  val fact=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Max wohnt in Graz.",now=1)
  try {
   Ctx3Shadow.configure(dir,true);Ctx3Shadow.configure(other,true)
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt Max?",emptyList(),"chat-a")
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt er?",emptyList(),"chat-b")
   assertEquals("CLARIFY",Ctx3Shadow.events(dir,"chat-b").last().decision)
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt er?",emptyList(),"chat-a")
   assertEquals(listOf(fact.id),Ctx3Shadow.events(dir,"chat-a").last().proposedIds)
   Ctx3Shadow.evaluate(other,bytes,listOf(fact),"Wo wohnt er?",emptyList(),"chat-a")
   assertEquals("CLARIFY",Ctx3Shadow.events(other,"chat-a").last().decision)
   val pool=Executors.newFixedThreadPool(2)
   try {val jobs=(0..9).map {i->pool.submit {Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt er?",emptyList(),if(i%2==0)"chat-a" else "chat-b")}};jobs.forEach {it.get()} }
   finally {pool.shutdownNow()}
   assertTrue(Ctx3Shadow.events(dir,"chat-b").all {it.proposedIds.isEmpty()})
   Ctx3Shadow.evaluate(dir,bytes,emptyList(),"Wo wohnt er?",emptyList(),"chat-a")
   assertEquals("CLARIFY",Ctx3Shadow.events(dir,"chat-a").last().decision)
   Ctx3Shadow.reset(dir,"chat-a")
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt er?",emptyList(),"chat-a")
   assertEquals("CLARIFY",Ctx3Shadow.events(dir,"chat-a").last().decision)
   for(id in listOf("","../x","bad id","x".repeat(81))) assertThrows(IllegalArgumentException::class.java) {
    Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Max",emptyList(),id)
   }
  } finally {dir.deleteRecursively();other.deleteRecursively()}
 }
}
