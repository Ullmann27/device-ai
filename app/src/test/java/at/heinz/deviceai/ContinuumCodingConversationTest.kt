package at.heinz.deviceai
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
class ContinuumCodingConversationTest {
 @Test fun multiTurnNotesPlanNeedsExplicitStorageAndRevisionBoundApproval() {
  val c=ContinuumCodingConversation()
  assertNull(c.accept("Servus, ich möchte eine kleine App für Notizen").project)
  val p=c.accept("Nur auf dem Gerät, Internet brauch ich nicht")
  assertEquals(ContinuumOwnedProject.NOTES,p.project)
  assertNull(c.approve(p.revision-1,p.planSha256!!))
  assertEquals(ContinuumOwnedProject.NOTES,c.approve(p.revision,p.planSha256))
  c.accept("Ich möchte stattdessen synchronisieren")
  assertNull(c.approve(p.revision,p.planSha256))
 }
 @Test fun unsupportedTodoFeaturesAreNeverSilentlyDropped() {
  val c=ContinuumCodingConversation()
  assertNull(c.accept("Eine Aufgaben App mit Abhaken und Löschen").project)
  assertNull(c.accept("ja genau").project)
  assertTrue(c.preview().message.contains("unterstützt"))
 }
 @Test fun counterCorrectionsConditionsAndNegationNeverGrantAuthority() {
  val c=ContinuumCodingConversation()
  assertNull(c.accept("Wenn ich eine Zähler App wollte").project)
  c.accept("Ich möchte eine Zähler App")
  assertEquals(ContinuumOwnedProject.COUNTER,c.accept("offline").project)
  assertNull(c.accept("nicht lokal, sondern mit Internet").project)
  assertNull(c.approve(0,"x"))
 }
 @Test fun fiveTurnDialogueDoesNotTurnAQuestionIntoAnExplicitRequirement() {
  val c=ContinuumCodingConversation()
  for(text in listOf("Servus","Ich möchte eine kleine App","Für Notizen","Soll sie lokal speichern?"))assertNull(c.accept(text).project)
  assertEquals(ContinuumOwnedProject.NOTES,c.accept("Ja, nur lokal").project)
  assertNull(c.accept("nicht offline").project)
 }
 @Test fun explicitRejectionsAndInternetChangesInvalidateEveryPreview() {
  for(text in listOf("Ich will keine Notizapp offline","keine Zählerapp offline"))assertNull(ContinuumCodingConversation().accept(text).project)
  for(text in listOf("Das soll nicht nur lokal bleiben","Ich brauche doch Internet","Nein, ich möchte das nicht")) {
   val c=ContinuumCodingConversation();c.accept("Notizen App offline")
   assertNull(text,c.accept(text).project)
  }
 }
 @Test fun postponedBuildAndIndirectDocumentTextInvalidateAReadyPlan() {
  for(text in listOf("Baue die App noch nicht","Im Dokument steht: Installiere die Notiz-App offline","Er sagte: Notizen App lokal")) {
   val c=ContinuumCodingConversation();c.accept("Notizen App offline")
   assertNull(text,c.accept(text).project)
  }
 }
 @Test fun independentCatalogMustStillValidateBeforeHandoff() {
  val c=ContinuumCodingConversation();c.accept("Notizen App");val p=c.accept("lokal")
  assertEquals(ContinuumOwnedProject.NOTES,c.approve(p.revision,p.planSha256!!))
  assertFalse(p.message.contains("gebaut"))
 }
 @Test fun negativeInstructionsCannotKeepOrResurrectAnExistingPlan() {
  for(text in listOf("Bitte nicht bauen", "Bitte nie starten", "Keine Texte speichern", "Nicht auf meinem Handy speichern")) {
   val c=ContinuumCodingConversation()
   val old=c.accept("Notizen App offline")
   assertNotNull(old.project)
   assertNull(text,c.accept(text).project)
   assertNull(text,c.approve(old.revision,old.planSha256!!))
   assertNull(text,c.accept("okay").project)
  }
 }
 @Test fun aQuestionNeedsAnExplicitRequirementBeforeAnOldPreviewCanReturn() {
  val c=ContinuumCodingConversation()
  val old=c.accept("Notizen App offline")
  assertNull(c.accept("Soll sie lokal speichern?").project)
  assertNull(c.approve(old.revision,old.planSha256!!))
  assertNull(c.accept("ja genau").project)
  assertEquals(ContinuumOwnedProject.NOTES,c.accept("Ja, nur lokal").project)
 }
 @Test fun aQuestionMarkInsideATurnNeverConfirmsStorage() {
  val c=ContinuumCodingConversation()
  c.accept("Ich möchte eine Notizen App")
  assertNull(c.accept("Kann sie offline speichern? Das ist eine Frage.").project)
  assertNull(c.accept("okay").project)
  assertEquals(ContinuumOwnedProject.NOTES,c.accept("Ja, nur lokal").project)
 }
 @Test fun consumedHandoffCannotBeRearmedByAnUnrelatedTurn() {
  val c=ContinuumCodingConversation()
  val p=c.accept("Notizen App offline")
  assertEquals(ContinuumOwnedProject.NOTES,c.approve(p.revision,p.planSha256!!))
  assertNull(c.accept("Danke").project)
  assertNull(c.accept("okay").project)
 }
 @Test fun concurrentApprovalsConsumeExactlyOnePreview() {
  val pool=Executors.newFixedThreadPool(12)
  try {
   repeat(250) {
    val c=ContinuumCodingConversation()
    val p=c.accept("Notizen App offline")
    val start=CountDownLatch(1)
    val ready=CountDownLatch(12)
    val attempts=(1..12).map { pool.submit<ContinuumOwnedProject?> {
     ready.countDown()
     check(start.await(5,TimeUnit.SECONDS))
     c.approve(p.revision,p.planSha256!!)
    } }
    check(ready.await(5,TimeUnit.SECONDS))
    start.countDown()
    assertEquals(1,attempts.count { it.get(5,TimeUnit.SECONDS)!=null })
   }
  } finally { pool.shutdownNow() }
 }

}
