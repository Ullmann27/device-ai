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
 @Test fun offByDefaultAndOptInNeverWritesDialogueOrCitesRevokedFacts() {
  val dir=Files.createTempDirectory("ctx3-shadow-test").toFile()
  try {
   val bytes=Base64.getDecoder().decode(javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
   val fact=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Max wohnt in Wels.",now=1)
   assertFalse(Ctx3Shadow.enabled(dir))
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt Max?",emptyList());assertTrue(Ctx3Shadow.events(dir).isEmpty())
   Ctx3Shadow.configure(dir,true)
   Ctx3Shadow.evaluate(dir,bytes,listOf(fact),"Wo wohnt Max?",emptyList())
   assertEquals(listOf(fact.id),Ctx3Shadow.events(dir).last().proposedIds)
   Ctx3Shadow.evaluate(dir,bytes,emptyList(),"Wo wohnt er?",emptyList())
   assertEquals("CLARIFY",Ctx3Shadow.events(dir).last().decision)
   assertFalse(java.io.File(dir,"dialogue-v1.bin").exists())
   repeat(40){Ctx3Shadow.evaluate(dir,bytes,emptyList(),"Servus",emptyList())}
   assertEquals(32,Ctx3Shadow.events(dir).size)
   Ctx3Shadow.configure(dir,false);assertTrue(Ctx3Shadow.events(dir).isEmpty())
  } finally {dir.deleteRecursively()}
 }
}
