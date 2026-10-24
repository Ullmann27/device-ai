package at.heinz.deviceai
import org.junit.Assert.*
import org.junit.Test
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
 @Test fun independentCatalogMustStillValidateBeforeHandoff() {
  val c=ContinuumCodingConversation();c.accept("Notizen App");val p=c.accept("lokal")
  assertEquals(ContinuumOwnedProject.NOTES,c.approve(p.revision,p.planSha256!!))
  assertFalse(p.message.contains("gebaut"))
 }
}
