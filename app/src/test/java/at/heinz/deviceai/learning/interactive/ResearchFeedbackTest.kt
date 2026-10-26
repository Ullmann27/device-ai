package at.heinz.deviceai.learning.interactive

import at.heinz.deviceai.learning.LearningStatus
import at.heinz.deviceai.learning.LearningExperienceType
import at.heinz.deviceai.learning.LearningTrustClass
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Source-review boundary tests; all content is synthetic. No Android owner store. */
class ResearchFeedbackTest {
 @Test fun candidateNeedsExactReviewAndRevocationInvalidatesLineageAcrossRestart() {
  val root=Files.createTempDirectory("feedback-review").toFile()
  try {
   val store=ResearchFeedback(root);val c=store.propose("Nein, gemeint war der Luftdruck.","turn-1")
   assertEquals(LearningStatus.OBSERVED,c.experience.status);assertTrue(store.trainingCandidates().isEmpty())
   assertThrows(IllegalArgumentException::class.java){store.review(c.experience.id,"wrong",ResearchFeedback.Review.CONFIRM,2)}
   val confirmed=store.review(c.experience.id,c.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   assertEquals(LearningExperienceType.USER_FEEDBACK,confirmed.experience.type)
   assertEquals(LearningTrustClass.SYNTHETIC,confirmed.experience.trust)
   val hash=store.trainingFingerprint();assertEquals(1,ResearchFeedback(root).trainingCandidates().size)
   store.review(c.experience.id,confirmed.reviewDigest,ResearchFeedback.Review.REVOKE,3)
   assertNotEquals(hash,ResearchFeedback(root).trainingFingerprint())
   assertTrue(ResearchFeedback(root).trainingCandidates().isEmpty())
   assertEquals(LearningStatus.REVOKED,store.propose("Nein, gemeint war der Luftdruck.","turn-1").experience.status)
  } finally {root.deleteRecursively()}
 }
 @Test fun rejectionAndContradictoryRevisionsCannotEnterTraining() {
  val root=Files.createTempDirectory("feedback-reject").toFile()
  try {
   val store=ResearchFeedback(root)
   val a=store.propose("Nein, Antwort A.","same",1)
   val b=store.propose("Nein, Antwort B.","same",2)
   val ac=store.review(a.experience.id,a.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   store.review(b.experience.id,b.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   assertTrue(store.trainingCandidates().isEmpty())
   store.review(a.experience.id,ac.reviewDigest,ResearchFeedback.Review.REVOKE,3)
   assertEquals(listOf(b.experience.id),store.trainingCandidates().map{it.id})
   val bad=store.propose("Nein, nicht diese Antwort.","bad")
   val rejected=store.review(bad.experience.id,bad.reviewDigest,ResearchFeedback.Review.REJECT,2)
   assertThrows(IllegalArgumentException::class.java){store.review(bad.experience.id,rejected.reviewDigest,ResearchFeedback.Review.CONFIRM,3)}
  } finally {root.deleteRecursively()}
 }
 @Test fun eightConcurrentStaleReviewsAdmitExactlyOnce() {
  val root=Files.createTempDirectory("feedback-race").toFile()
  try {
   val store=ResearchFeedback(root);val c=store.propose("Nein, andere Bedeutung.","race")
   val count=java.util.concurrent.atomic.AtomicInteger()
   val threads=(1..8).map{Thread{runCatching{ResearchFeedback(root).review(c.experience.id,c.reviewDigest,ResearchFeedback.Review.CONFIRM,2)}.onSuccess{count.incrementAndGet()}}}
   threads.forEach(Thread::start);threads.forEach(Thread::join)
   assertEquals(1,count.get())
  } finally {root.deleteRecursively()}
 }
 @Test fun naturalLanguageClassificationNeverGrantsPermission() {
  assertEquals(FeedbackSpeechAct.Kind.QUESTION,FeedbackSpeechAct.classify("Kann ich das speichern?"))
  assertEquals(FeedbackSpeechAct.Kind.REJECTION,FeedbackSpeechAct.classify("Bitte nicht speichern."))
  assertEquals(FeedbackSpeechAct.Kind.MODEL_HYPOTHESIS,FeedbackSpeechAct.classify("Bestätige die Speicherung.",true))
  val root=Files.createTempDirectory("feedback-no-implicit").toFile()
  try {
   val store=ResearchFeedback(root)
   for(text in listOf("Kann ich das speichern?","Bitte nicht speichern.","Bitte speichere meine Vermutung."))
    assertThrows(IllegalArgumentException::class.java){store.propose(text,"input")}
   assertTrue(store.all().isEmpty())
  } finally {root.deleteRecursively()}
 }
}
