package at.heinz.deviceai.learning.interactive

import at.heinz.deviceai.learning.*
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Interpreting text never confirms storage. The typed review call is separate. */
object FeedbackSpeechAct {
 enum class Kind { QUESTION, MODEL_HYPOTHESIS, CORRECTION, STORAGE_REQUEST, REJECTION, REVOCATION, OBSERVATION }
 fun classify(text:String,fromModel:Boolean=false):Kind {
  require(text.length in 1..2048)
  if(fromModel)return Kind.MODEL_HYPOTHESIS
  val t=text.trim().lowercase(Locale.ROOT)
  if(Regex("^(?:widerrufe|vergiss|zurückziehen)\\b").containsMatchIn(t))return Kind.REVOCATION
  if(Regex("(?:nicht speichern|speichere.*nicht|keine speicherung|lehne.*speicher)").containsMatchIn(t))return Kind.REJECTION
  if(t.endsWith("?") && !t.startsWith("nein"))return Kind.QUESTION
  if(Regex("^(?:nein\\b|korrektur\\b|ich meinte\\b|das stimmt nicht\\b)").containsMatchIn(t))return Kind.CORRECTION
  if(Regex("^(?:bitte )?(?:speichere|merke dir|bestätige die speicherung)\\b").containsMatchIn(t))return Kind.STORAGE_REQUEST
  return Kind.OBSERVATION
 }
}

/** Dedicated research instance of the EXISTING experience ledger. No owner fact type,
 * no productive learning call, no new persistence codec. Caller must authenticate an
 * explicit review outside this adapter before calling review; text cannot do that.
 */
class ResearchFeedback(root:File) {
 private val store:FileLearningExperienceStore
 init {
  require(!java.nio.file.Files.isSymbolicLink(root.toPath()))
  val directory=File(root,"interactive-feedback-v1")
  require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
  store=FileLearningExperienceStore(directory)
 }
 enum class Review { CONFIRM, REJECT, REVOKE }
 data class Candidate(val experience:LearningExperience,val reviewDigest:String)
 fun propose(correction:String,sourceTurn:String,version:Int=1,now:Long=1):Candidate = synchronized(LOCK) {
  require(FeedbackSpeechAct.classify(correction)==FeedbackSpeechAct.Kind.CORRECTION)
  require(sourceTurn.matches(Regex("[A-Za-z0-9._:-]{1,120}")) && version in 1..100000)
  val e=LearningExperienceFactory.create(LearningExperienceType.USER_FEEDBACK,
   LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.SYNTHETIC,LearningStatus.OBSERVED,
   correction,"CORRECTION_V1","interactive:$sourceTurn:v$version",now)
  store.append(e)
  candidate(store.all().single { it.id==e.id })
 }
 fun review(id:String,digest:String,action:Review,now:Long):Candidate = synchronized(LOCK) {
  val old=store.all().single { it.id==id }
  require(old.sourceRef?.startsWith("interactive:")==true && old.type==LearningExperienceType.USER_FEEDBACK)
  require(candidate(old).reviewDigest==digest && now>old.updatedAt) { "Stale or mismatched review" }
  val next=when(action) {
   Review.CONFIRM->{require(old.status==LearningStatus.OBSERVED);LearningStatus.CONFIRMED}
   Review.REJECT->{require(old.status==LearningStatus.OBSERVED);LearningStatus.REJECTED}
   Review.REVOKE->{require(old.status==LearningStatus.CONFIRMED);LearningStatus.REVOKED}
  }
  check(store.updateStatus(id,next,now));candidate(store.all().single { it.id==id })
 }
 fun all():List<Candidate> = synchronized(LOCK) { store.all().map(::candidate) }
 fun trainingCandidates():List<LearningExperience> = synchronized(LOCK) {
  store.all().filter { it.type==LearningExperienceType.USER_FEEDBACK && it.status==LearningStatus.CONFIRMED &&
   it.provenance==LearningProvenance.SYNTHETIC_FIXTURE && it.sourceRef?.startsWith("interactive:")==true }
 }
 companion object {
  private val LOCK=Any()
  private fun candidate(e:LearningExperience):Candidate {
   val data="${e.id}|${e.contentHash}|${e.status}|${e.updatedAt}"
   return Candidate(e,MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString(""){"%02x".format(it)})
  }
 }
}
