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
 @Test fun previousTopicCanBeReactivatedWithoutReplayingInterveningText() {
  val c=Ctx3Context()
  c.decide("Erzähl von Fokus App",listOf(a,b),"v1")
  c.decide("Erzähl von Liste App",listOf(a,b),"v1")
  val back=c.decide("Und was war davor?",listOf(a,b),"v1") as Ctx3Decision.Query
  assertEquals(setOf("s1"),back.sourceIds)
  assertTrue(back.query.contains("Fokus App"))
 }

 @Test fun explicitTwoWayAlternativeCanResolveDasAndereButNeverInventAThirdOption() {
  val c=Ctx3Context()
  c.decide("Vergleiche Fokus App und Liste App",listOf(a,b),"v1")
  c.decide("Ich meine Fokus App",listOf(a,b),"v1")
  val other=c.decide("Nicht das, das andere",listOf(a,b),"v1") as Ctx3Decision.Query
  assertEquals(setOf("s2"),other.sourceIds)
  assertTrue(other.query.contains("Liste App"))

  val third=Ctx3Entity("app-c","Dritte App",setOf("Dritte App"),setOf("sie"),setOf("s3"))
  val d=Ctx3Context()
  d.decide("Vergleiche Fokus App und Liste App und Dritte App",listOf(a,b,third),"v1")
  d.decide("Ich meine Fokus App",listOf(a,b,third),"v1")
  assertTrue(d.decide("Nicht das, das andere",listOf(a,b,third),"v1") is Ctx3Decision.Clarify)
 }

 @Test fun discourseHistoryResetsWhenSourceVersionChangesAndStaysBounded() {
  val c=Ctx3Context()
  c.decide("Fokus App",listOf(a,b),"v1")
  c.decide("Liste App",listOf(a,b),"v1")
  assertTrue(c.discourseStateSize()<=8)
  assertTrue(c.decide("Was war davor?",listOf(a,b),"v2") is Ctx3Decision.Clarify)
  repeat(30) { i ->
   c.decide(if(i%2==0)"Fokus App" else "Liste App",listOf(a,b),"v2")
  }
  assertTrue(c.discourseStateSize()<=8)
 }

 @Test fun rejectedNamesAndChangedSourcesCannotBeResurrected() {
  val c=Ctx3Context();c.decide("Fokus App",listOf(a),"v1")
  assertTrue(c.decide("Nicht Fokus App",listOf(a),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo steht sie?",listOf(a),"v1") is Ctx3Decision.Clarify)
  c.decide("Fokus App",listOf(a),"v1")
  assertTrue(c.decide("Wo steht sie?",listOf(a),"v2") is Ctx3Decision.Clarify)
 }
 @Test fun mixedUnknownsAndNonAdjacentNegationNeverReuseOldFocus() {
  val c=Ctx3Context();c.decide("Fokus App",listOf(a,b),"v1")
  assertTrue(c.decide("Wo steht die App?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo steht sie?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo steht Fokus App und Gustav?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo wohnt Gustav?",listOf(a,b),"v1") is Ctx3Decision.Abstain)
  c.decide("Fokus App",listOf(a,b),"v1")
  assertTrue(c.decide("Nicht die Fokus App",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wo steht sie?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  c.decide("Fokus App",listOf(a,b),"v1")
  assertTrue(c.decide("Sie meine ich nicht",listOf(a,b),"v1") is Ctx3Decision.Clarify)
 }
 @Test fun factualGenderIsNotWidenedByAnAttributeFact() {
  val e=Ctx3EntityIndex.fromFacts(mapOf("s1" to "Anna ist meine Schwester.","s2" to "Anna wohnt in Wels."))
  assertEquals(setOf("sie"),e.single().pronouns)
 }
 @Test fun overlappingAliasesAndQuotedCommandsFailClosed() {
  val c=Ctx3Context()
  assertTrue(c.decide("Wo steht die App?",listOf(a,b),"v1") is Ctx3Decision.Clarify)
  assertTrue(c.decide("Wenn sie startet, installiere sie",listOf(a),"v1") is Ctx3Decision.Clarify)
  repeat(200){c.decide("Servus",listOf(a),"v1")}
  assertTrue(c.stateSize()<=8)
 }
}
