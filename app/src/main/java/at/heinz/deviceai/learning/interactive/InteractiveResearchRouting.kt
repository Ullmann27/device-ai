package at.heinz.deviceai.learning.interactive

import at.heinz.deviceai.learning.ContinuumArchiveAnswerPolicy
import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import at.heinz.deviceai.learning.context.Ctx3Entity

/** Pure opt-in route inspection. Reuses the actual archive gate; does not invoke
 * a model, bypass the main brain, or confer tool/storage permission. */
object InteractiveResearchRouting {
 enum class Route { CLARIFY, ARCHIVE_OBSERVATION, LOCAL_MODEL_NEEDED, MODEL_NOT_CONFIGURED }
 data class Plan(val route:Route,val promptContext:String,val archiveId:String?,val evidenceClass:String)
 fun inspect(context:InteractiveDialogueContext,sessionId:String,question:String,
             entities:List<Ctx3Entity>,revision:String,hits:List<ContinuumKnowledgeArchive.Hit>,
             localModelAvailable:Boolean):Plan {
  require(hits.size<=20)
  val d=context.plan(sessionId,question,entities,revision)
  if(d.clarification)return Plan(Route.CLARIFY,d.context,null,"UNCONFIRMED_CONVERSATION")
  // Keep the original question: rewriting a pronoun into a title must not turn
  // the existing archive veto into an automatic archival answer.
  val hit=ContinuumArchiveAnswerPolicy.select(question,hits)
  if(hit!=null)return Plan(Route.ARCHIVE_OBSERVATION,d.context,hit.source.id,"UNVERIFIED_PUBLIC_SOURCE")
  return Plan(if(localModelAvailable)Route.LOCAL_MODEL_NEEDED else Route.MODEL_NOT_CONFIGURED,
   d.context,null,"UNCONFIRMED_CONVERSATION")
 }
}
