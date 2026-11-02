package at.heinz.deviceai
import at.heinz.deviceai.learning.*
import at.heinz.deviceai.learning.context.*
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
class Ctx3ShadowTest {
 @Test fun actualLearningCoreUsesTheSameOptInAndKeepsBaselineAnswers() {
  val dir=Files.createTempDirectory("ctx3-core-test").toFile()
  try {
   val core=ContinuumLearningCore(dir)
   assertFalse(core.contextShadowEnabled())
   val before=core.replyFromMemory("Servus")
   core.configureContextShadow(true)
   val after=core.replyFromMemory("Servus")
   assertEquals(before.answer,after.answer)
   assertEquals(1,core.contextShadowEvents().size)
  } finally {dir.deleteRecursively()}
 }
 @Test fun independentCoreConversationsCannotBorrowOneAnothersPronouns() {
  val dir=Files.createTempDirectory("ctx3-core-isolation").toFile()
  try {
   val a=ContinuumLearningCore(dir);val b=ContinuumLearningCore(dir)
   a.configureContextShadow(true);a.confirmOwnerFact("Max wohnt in Graz.")
   a.replyFromMemory("Wo wohnt Max?");b.replyFromMemory("Wo wohnt er?")
   assertEquals("CLARIFY",b.contextShadowEvents().last().decision)
  } finally {dir.deleteRecursively()}
 }
 @Test fun offByDefaultAndOptInNeverWritesDialogueOrCitesRevokedFacts() {
  val dir=Files.createTempDirectory("ctx3-shadow-test").toFile()
  try {
   val bytes=Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
   val fact=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Max wohnt in Wels.",now=1)
   assertFalse(Ctx3Shadow.enabled(dir))
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt Max?",emptyList(),"test-chat");assertTrue(Ctx3Shadow.events(dir,"test-chat").isEmpty())
   Ctx3Shadow.configure(dir,true)
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt Max?",emptyList(),"test-chat")
   assertEquals(listOf(fact.id),Ctx3Shadow.events(dir,"test-chat").last().proposedIds)
   Ctx3Shadow.evaluate(dir,bytes,emptyList(),"Wo wohnt er?",emptyList(),"test-chat")
   assertEquals("CLARIFY",Ctx3Shadow.events(dir,"test-chat").last().decision)
   assertFalse(java.io.File(dir,"dialogue-v1.bin").exists())
   repeat(40){Ctx3Shadow.evaluate(dir,bytes,emptyList(),"Servus",emptyList(),"test-chat")}
   assertEquals(32,Ctx3Shadow.events(dir,"test-chat").size)
   Ctx3Shadow.configure(dir,false);assertTrue(Ctx3Shadow.events(dir,"test-chat").isEmpty())
  } finally {dir.deleteRecursively()}
 }
}
