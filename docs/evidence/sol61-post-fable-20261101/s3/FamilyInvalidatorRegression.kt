package at.heinz.deviceai.learning
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class FamilyInvalidatorRegression {
 @Test fun laterFamilyNegationMustInvalidateEarlierAge() = checkChange("Meine Tochter Klara ist nicht 29.", null)
 @Test fun laterFamilyAgeWithUnitMustReplaceEarlierAge() = checkChange("Meine Tochter Klara ist 30 Jahre alt.", "30 Jahre")
 private fun checkChange(next:String, expected:String?) {
  val dir=Files.createTempDirectory("age-invalidator").toFile();var now=1000L
  try {
   val core=ContinuumLearningCore(dir){now++}
   core.confirmOwnerFact("Meine Tochter Klara ist 29.");core.confirmOwnerFact(next)
   val answer=core.replyFromMemory("Wie alt ist Klara?","age-invalidator").answer
   if(expected==null)assertFalse(answer,answer.contains("Aus deiner bestätigten Altersangabe"))
   else assertTrue(answer,answer.startsWith("Aus deiner bestätigten Altersangabe: $expected\n"))
  } finally {dir.deleteRecursively()}
 }
}
