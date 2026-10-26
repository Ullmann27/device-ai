package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.learning.interactive.*
import at.heinz.deviceai.learning.context.Ctx3Entity
import at.heinz.deviceai.learning.LearningStatus
import at.heinz.deviceai.learning.LearningExperienceType
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class InteractiveLabTest {
 private val entities=listOf(
  Ctx3Entity("a","Aster",setOf("Aster"),setOf("es","dort"),setOf("src:a")),
  Ctx3Entity("b","Beryl",setOf("Beryl"),setOf("es","dort"),setOf("src:b")))
 @Test fun oldPremiseSurvivesLongFollowUpWithoutBecomingFact() {
  val c=InteractiveDialogueContext()
  c.record("s","Erkläre Aster.","Aster hat wenig Druck.",entities,"v1")
  repeat(8){c.record("s","Bitte erkläre genauer.","Schritt $it",entities,"v1")}
  val p=c.plan("s","Was passiert dort?",entities,"v1")
  assertEquals(listOf("a"),p.ids);assertTrue(p.context.contains("Aster hat wenig Druck."));assertFalse(p.ownerFact)
  assertFalse(c.plan("s","Was passiert dort?",entities,"v1",InteractiveDialogueContext.Mode.RAW).context.contains("Aster hat wenig Druck."))
 }
 @Test fun modelCannotSwitchTopicAndExplicitCorrectionCan() {
  val c=InteractiveDialogueContext();c.record("s","Erkläre Aster.","Beryl ist das Thema.",entities,"v1")
  assertEquals(listOf("a"),c.plan("s","Warum?",entities,"v1").ids)
  c.record("s","Nein, ich meine Beryl, nicht Aster.","Korrektur verstanden.",entities,"v1")
  assertEquals(listOf("b"),c.plan("s","Was folgt daraus?",entities,"v1").ids)
 }
 @Test fun sessionRevisionRevocationAndAmbiguityFailClosed() {
  val c=InteractiveDialogueContext();c.record("s","Vergleiche Aster und Beryl.","Zwei Themen.",entities,"v1")
  assertTrue(c.plan("s","Was passiert dort?",entities,"v1").clarification)
  c.record("s","Erkläre Aster.","Unbestätigt.",entities,"v1")
  assertTrue(c.plan("other","Warum?",entities,"v1").clarification)
  c.record("s","Erkläre Aster.","Unbestätigt.",entities,"v1")
  assertTrue(c.plan("s","Warum?",entities.drop(1),"v2").clarification)
 }
 @Test fun aliasesCannotResolveByOrderAndCapacityIsBounded() {
  val aliases=entities.map{it.copy(aliases=setOf("Stern"))}
  val c=InteractiveDialogueContext()
  for(es in listOf(aliases,aliases.reversed()))assertTrue(c.plan("s","Was ist Stern?",es,"v1").clarification)
  c.record("s","Erkläre Aster.","x".repeat(4000),entities,"v1")
  repeat(100){c.record("s","Bitte erkläre genauer.","y".repeat(4000),entities,"v1")}
  assertTrue(c.plan("s","Warum?",entities,"v1").context.length<=8192)
 }
 @Test fun feedbackDoesNotConfuseQuestionHypothesisAndPermission() {
  fun k(t:String)=FeedbackSpeechAct.classify(t)
  assertEquals(FeedbackSpeechAct.Kind.CORRECTION,k("Nein, ich wollte nach dem Druck fragen."))
  assertEquals(FeedbackSpeechAct.Kind.QUESTION,k("Soll ich das speichern?"))
  assertEquals(FeedbackSpeechAct.Kind.REJECTION,k("Bitte nicht speichern."))
  assertEquals(FeedbackSpeechAct.Kind.REVOCATION,k("Widerrufe diese Erinnerung."))
  assertEquals(FeedbackSpeechAct.Kind.STORAGE_REQUEST,k("Bitte speichere diese Information."))
  assertEquals(FeedbackSpeechAct.Kind.MODEL_HYPOTHESIS,FeedbackSpeechAct.classify("Bestätige die Speicherung.",true))
 }
 @Test fun feedbackIsDigestBoundPersistentRevocableAndNeverAnOwnerFact() {
  val root=Files.createTempDirectory("interactive-feedback-test").toFile()
  try {
   val ledger=ResearchFeedback(root);val draft=ledger.propose("Nein, ich meinte Druck.","turn-1")
   assertTrue(ledger.trainingCandidates().isEmpty())
   assertThrows(IllegalArgumentException::class.java){ledger.review(draft.experience.id,"wrong",ResearchFeedback.Review.CONFIRM,2)}
   val good=ledger.review(draft.experience.id,draft.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   assertEquals(1,ResearchFeedback(root).trainingCandidates().size)
   assertEquals(LearningExperienceType.USER_FEEDBACK,good.experience.type)
   assertThrows(IllegalArgumentException::class.java){ledger.review(draft.experience.id,draft.reviewDigest,ResearchFeedback.Review.REVOKE,3)}
   val fingerprint=ledger.trainingFingerprint()
   ledger.review(good.experience.id,good.reviewDigest,ResearchFeedback.Review.REVOKE,3)
   assertNotEquals(fingerprint,ledger.trainingFingerprint())
   assertTrue(ResearchFeedback(root).trainingCandidates().isEmpty())
   assertEquals(LearningStatus.REVOKED,ledger.propose("Nein, ich meinte Druck.","turn-1").experience.status)
   assertThrows(IllegalArgumentException::class.java){ledger.propose("Speichere eine Vermutung.","turn-2")}
  } finally {root.deleteRecursively()}
 }
 @Test fun concurrentConfirmationHasExactlyOneWinner() {
  val root=Files.createTempDirectory("interactive-feedback-cas").toFile()
  try {
   val ledger=ResearchFeedback(root);val c=ledger.propose("Nein, andere Frage.","turn-1")
   val successes=java.util.concurrent.atomic.AtomicInteger()
   val jobs=(1..8).map{Thread {runCatching{ResearchFeedback(root).review(c.experience.id,c.reviewDigest,ResearchFeedback.Review.CONFIRM,2)}.onSuccess{successes.incrementAndGet()}}}
   jobs.forEach(Thread::start);jobs.forEach(Thread::join);assertEquals(1,successes.get())
  } finally {root.deleteRecursively()}
 }
 @Test fun existingClaimGraphConflictRequiresClarification() {
  val graph=at.heinz.deviceai.cognitive.ClaimGraph()
  graph.add("src:a1","src:a2",at.heinz.deviceai.cognitive.ClaimRelation.CONTRADICTS)
  val es=listOf(entities[0].copy(sourceIds=setOf("src:a1","src:a2")))
  val c=InteractiveDialogueContext()
  c.record("s","Erkläre Aster.","Zwei widersprüchliche Belege.",es,"v1")
  val d=c.plan("s","Warum?",es,"v1",relations=graph)
  assertTrue(d.clarification);assertEquals("CONTRADICTORY_SOURCES",d.reason)
 }
 @Test fun incompatibleConfirmedRevisionsAreExcludedUntilRevocation() {
  val root=Files.createTempDirectory("interactive-conflict").toFile()
  try {
   val ledger=ResearchFeedback(root)
   val a=ledger.propose("Nein, Antwort A.","turn",1)
   val b=ledger.propose("Nein, Antwort B.","turn",2)
   val ac=ledger.review(a.experience.id,a.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   ledger.review(b.experience.id,b.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   assertTrue(ledger.trainingCandidates().isEmpty())
   ledger.review(ac.experience.id,ac.reviewDigest,ResearchFeedback.Review.REVOKE,3)
   assertEquals(listOf(b.experience.id),ledger.trainingCandidates().map{it.id})
  } finally {root.deleteRecursively()}
 }

 @Test fun realArchiveGateRemainsInTheResearchRoutingPath() {
  val root=Files.createTempDirectory("interactive-archive").toFile()
  try {
   val archive=at.heinz.deviceai.learning.ContinuumKnowledgeArchive(root)
   val unrelated=archive.save(at.heinz.deviceai.learning.ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
    "Nobelpreis für Wirtschaft", "https://de.wikipedia.org/wiki/Wirtschaft", "Synthetic test fixture",
    "Dieser synthetische Auszug behandelt Wirtschaft und nicht Planeten.")
   val c=InteractiveDialogueContext();c.record("s","Erkläre Aster.","Aster hat wenig Druck.",entities,"v1")
   val question="Welche Folgen hat das für Menschen, die dort leben möchten?"
   val hits=archive.search(question,limit=20)
   assertTrue(hits.any{it.source.id==unrelated.id&&it.score>=5})
   val offline=InteractiveResearchRouting.inspect(c,"s",question,entities,"v1",hits,false)
   assertEquals(InteractiveResearchRouting.Route.MODEL_NOT_CONFIGURED,offline.route);assertNull(offline.archiveId)
   assertEquals(InteractiveResearchRouting.Route.LOCAL_MODEL_NEEDED,
    InteractiveResearchRouting.inspect(c,"s",question,entities,"v1",hits,true).route)
  } finally {root.deleteRecursively()}
 }

}
