package at.heinz.deviceai
import at.heinz.deviceai.learning.context.*
import org.junit.Assert.*
import org.junit.Test
class Ctx3ContextTest {
 private val a=Ctx3Entity("app-a","Fokus App",setOf("Fokus App","App"),setOf("sie"),setOf("s1"))
 private val b=Ctx3Entity("app-b","Liste App",setOf("Liste App","App"),setOf("sie"),setOf("s2"))
 @Test fun fiveTurnsKeepSourcesAndRequireAUniqueReferent() {
  val c=Ctx3Context()
  assertEquals(setOf("s1"),(c.decide("Erzähl von Fokus App",listOf(a,b),"v1") as Ctx3Decision.Query).sourceIds)
  assertEquals("Wo steht Fokus App?",(c.decide("Wo steht sie?",listOf(a,b),"v1") as Ctx3Decision.Query).query)
  assertTrue(c.decide("Erzähl von Fokus App und Liste App",listOf(a,b),"v1") is Ctx3Decision.Query)
  assertTrue(c.decide("Wo steht sie?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertEquals(setOf("s2"),(c.decide("Ich meine Liste App",listOf(a,b),"v1") as Ctx3Decision.Query).sourceIds)
 }
 @Test fun rejectedNamesAndChangedSourcesCannotBeResurrected() {
  val c=Ctx3Context();c.decide("Fokus App",listOf(a),"v1")
  assertTrue(c.decide("Nicht Fokus App",listOf(a),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo steht sie?",listOf(a),"v1") is Ctx3Decision.Clarify)
  c.decide("Fokus App",listOf(a),"v1")
  assertTrue(c.decide("Wo steht sie?",listOf(a),"v2") is Ctx3Decision.Clarify)
 }
 @Test fun overlappingAliasesAndQuotedCommandsFailClosed() {
  val c=Ctx3Context()
  assertTrue(c.decide("Wo steht die App?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wenn sie startet, installiere sie",listOf(a),"v1") is Ctx3Decision.Clarify)
  repeat(200){c.decide("Servus",listOf(a),"v1")}
  assertTrue(c.stateSize()<=8)
 }
}
