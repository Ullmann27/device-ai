package at.heinz.deviceai

import at.heinz.deviceai.cognitive.DevelopmentBacklog
import org.junit.Assert.*
import org.junit.Test

class ContinuumCodingTaskTest {
 private fun task()=ContinuumCodingTask(
  id="job-android-intake",sourceCommit="a".repeat(40),kind=ContinuumCodingTask.Kind.BUGFIX,
  goal="Korrigiere die reine Berechnung",allowedPaths=listOf(".github/continuum/numeric-sample.mjs"),
  acceptance=listOf("Negative Zahlen liefern das festgelegte Ergebnis"),chain=ContinuumCodingTask.Chain.NODE,
  limits=ContinuumCodingTask.Limits(maxModelCalls=2,maxContextChars=8192))

 @Test fun completeTypedTaskIsOnlyADraftUntilSeparateExplicitConfirmation() {
  val c=ContinuumCodingConversation()
  val p=c.draftCodingTask(ContinuumCodingTask.Draft.from(task()))
  assertNotNull(p.codingTask)
  assertNull(p.project)
  assertTrue(p.message,p.message.contains("Worker-Konfiguration"))
  assertNull(c.approveCodingTask(p.revision,p.taskSha256!!,ownerConfirmed=false))
  assertEquals(p.taskSha256,c.approveCodingTask(p.revision,p.taskSha256,ownerConfirmed=true)?.taskSha256)
  assertNull(c.approveCodingTask(p.revision,p.taskSha256,ownerConfirmed=true))
 }

 @Test fun everyKindIsRepresentedWithoutInventingExecutionCapability() {
  assertEquals(setOf("BUGFIX","FEATURE","REFACTOR","TEST","COMPILER","DEPENDENCY"),ContinuumCodingTask.Kind.entries.map { it.name }.toSet())
  for(kind in ContinuumCodingTask.Kind.entries) assertTrue(task().copy(kind=kind).problems().isEmpty())
 }

 @Test fun invalidCommitPathsAcceptanceAndResourceLimitsAreRejected() {
  for(t in listOf(
   task().copy(sourceCommit="main"),task().copy(sourceCommit="a".repeat(39)),task().copy(goal=" "),
   task().copy(allowedPaths=emptyList()),task().copy(allowedPaths=List(4){ ".github/continuum/file$it.mjs" }),
   task().copy(allowedPaths=listOf(".github/continuum/../secret.mjs")),
   task().copy(allowedPaths=listOf("/tmp/code.mjs")),task().copy(allowedPaths=listOf("scripts/run.sh")),
   task().copy(allowedPaths=listOf("app/build.gradle.kts")),
   task().copy(allowedPaths=listOf(".github/continuum/numeric-sample.mjs",".github/continuum/numeric-sample.mjs")),
   task().copy(acceptance=emptyList()),task().copy(acceptance=listOf(" ")),
   task().copy(limits=ContinuumCodingTask.Limits(0,8192)),task().copy(limits=ContinuumCodingTask.Limits(4,8192)),
   task().copy(limits=ContinuumCodingTask.Limits(1,2047)),task().copy(limits=ContinuumCodingTask.Limits(1,24001))
  )) {
   assertFalse(t.toString(),t.problems().isEmpty())
   assertNull(ContinuumCodingTask.Draft.from(t).validatedTask())
  }
 }

 @Test fun sourceChainMustMatchSelectedModules() {
  val kotlin=task().copy(allowedPaths=listOf("app/src/main/java/at/heinz/deviceai/learning/Example.kt"),chain=ContinuumCodingTask.Chain.ANDROID)
  assertTrue(kotlin.problems().toString(),kotlin.problems().isEmpty())
  assertFalse(kotlin.copy(chain=ContinuumCodingTask.Chain.NODE).problems().isEmpty())
  assertFalse(task().copy(chain=ContinuumCodingTask.Chain.ANDROID).problems().isEmpty())
 }

 @Test fun canonicalTaskFingerprintBindsEveryGrantAndUsesExistingBacklogCanonicalization() {
  val t=task()
  val json=t.canonicalJson()
  assertEquals(json,DevelopmentBacklog.canonical(DevelopmentBacklog.parseStrict(json)))
  assertEquals(DevelopmentBacklog.sha256Of(json.toByteArray(Charsets.UTF_8)),t.fingerprint())
  for(changed in listOf(t.copy(sourceCommit="b".repeat(40)),t.copy(goal="Anderes Ziel"),
    t.copy(kind=ContinuumCodingTask.Kind.REFACTOR),t.copy(acceptance=listOf("Neues Kriterium")),
    t.copy(allowedPaths=listOf(".github/continuum/other.mjs")),t.copy(limits=ContinuumCodingTask.Limits(3,8192))))
   assertNotEquals(t.fingerprint(),changed.fingerprint())
 }

 @Test fun changedRequirementsAndUnrelatedYesCannotReuseAnOldConfirmation() {
  val c=ContinuumCodingConversation()
  val p=c.draftCodingTask(ContinuumCodingTask.Draft.from(task()))
  assertNull(c.accept("ja genau").codingTask)
  assertNull(c.approveCodingTask(p.revision,p.taskSha256!!,ownerConfirmed=true))
  val updated=c.draftCodingTask(ContinuumCodingTask.Draft.from(task().copy(goal="Anderes Ziel")))
  assertNull(c.approveCodingTask(updated.revision,p.taskSha256,ownerConfirmed=true))
  assertNotNull(c.approveCodingTask(updated.revision,updated.taskSha256!!,ownerConfirmed=true))
 }

 @Test fun mutableCallerListsCannotChangeAReviewSnapshot() {
  val paths=mutableListOf(".github/continuum/numeric-sample.mjs")
  val acceptance=mutableListOf("Negativer Fall ist geprüft")
  val c=ContinuumCodingConversation()
  val p=c.draftCodingTask(ContinuumCodingTask.Draft.from(task().copy(allowedPaths=paths,acceptance=acceptance)))
  paths[0]=".github/continuum/other.mjs"
  acceptance[0]="Anderer Fall"
  val confirmed=c.approveCodingTask(p.revision,p.taskSha256!!,ownerConfirmed=true)!!
  assertEquals(listOf(".github/continuum/numeric-sample.mjs"),confirmed.task.allowedPaths)
  assertEquals(listOf("Negativer Fall ist geprüft"),confirmed.task.acceptance)
 }
}
