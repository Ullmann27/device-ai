package at.heinz.deviceai.cognitive
import org.junit.Test
import org.junit.Assert.assertFalse
/** Same executable test for the old and repaired full main compilation. */
class OfflineDefaultRegression {
 @Test fun noImplicitOnlineAuthorizationFromSavedResearch() {
  val ctx=LocalConversationContext()
  val time=1_761_955_200_000L
  ctx.recordResearch("s1",ResearchDialogueHandoff.Digest("Wer leitet die EZB?","Ein externer Quelltext.",
   listOf(ResearchDialogueHandoff.SourceRef("EZB","https://www.ecb.europa.eu/x",time)),false,time))
  assertFalse("No access state was supplied: research must remain historical",
   ctx.render("s1",time+1000).contains("Als aktuell verwendbar."))
 }
}
