import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.learning.ContinuumLearningCore
import java.nio.file.Files
fun main() {
 val root=Files.createTempDirectory("scope-probe").toFile()
 try {
  val core=ContinuumLearningCore(root);val context=LocalConversationContext()
  core.confirmOwnerFact("Projekt Saphir verwendet lokale Belege.")
  val first=core.replyFromMemory("Was verwendet Projekt Saphir?","scope-probe")
  check(first.grounded)
  context.inspectRequest("scope-probe","Nein, ich meinte Projekt Kobalt.")
  val stale=core.replyFromMemory("und wie?","scope-probe")
  println("baselineAfterCorrection.grounded=${stale.grounded}")
  println("baselineAfterCorrection.resolvedQuery=${stale.resolvedQuery}")
  check(core.resetDialogueConversation("scope-probe"))
  val reset=ContinuumLearningCore(root).replyFromMemory("und wie?","scope-probe")
  println("existingReset.grounded=${reset.grounded}")
  println("existingReset.resolvedQuery=${reset.resolvedQuery}")
  check(!reset.grounded)
 } finally { root.deleteRecursively() }
}
