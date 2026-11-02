package at.heinz.deviceai

import at.heinz.deviceai.cognitive.DevelopmentBacklog
import java.util.Collections

/** EXP-02/05/07/08/11/12: requirements data, never a command or an execution grant. */
internal data class ContinuumCodingTask(
 val id:String,
 val sourceCommit:String,
 val kind:Kind,
 val goal:String,
 val allowedPaths:List<String>,
 val acceptance:List<String>,
 val chain:Chain,
 val limits:Limits
) {
 enum class Kind { BUGFIX, FEATURE, REFACTOR, TEST, COMPILER, DEPENDENCY }
 enum class Chain(val wire:String) { NODE("node"), ANDROID("android") }
 data class Limits(val maxModelCalls:Int,val maxContextChars:Int)

 /** Incomplete drafts retain missing fields. No guessed source, scope, kind or acceptance. */
 data class Draft(
  val id:String="",
  val sourceCommit:String="",
  val kind:Kind?=null,
  val goal:String="",
  val allowedPaths:List<String> = emptyList(),
  val acceptance:List<String> = emptyList(),
  val chain:Chain?=null,
  val limits:Limits?=null
 ) {
  fun problems():List<String> = buildList {
   if(!Regex("[a-z][a-z0-9-]{2,63}").matches(id))add("Auftrags-ID: 3..64 kleine Buchstaben, Ziffern oder Bindestriche")
   if(!Regex("[a-f0-9]{40}").matches(sourceCommit))add("Quell-Commit: vollständige 40-stellige Git-SHA angeben")
   if(kind==null)add("Auftragsart ausdrücklich wählen: BUGFIX, FEATURE, REFACTOR, TEST, COMPILER oder DEPENDENCY")
   if(!validText(goal,600))add("Konkretes Ziel: 1..600 Zeichen angeben")
   if(allowedPaths.size !in 1..3||allowedPaths.distinct().size!=allowedPaths.size||allowedPaths.any { !isCodingPath(it) })
    add("Dateien: 1..3 unterschiedliche erlaubte JS-/Kotlin-Quellpfade ausdrücklich auswählen; keine Tests, Build- oder Steuerdateien")
   if(acceptance.size !in 1..8||acceptance.any { !validText(it,300) })add("Akzeptanzkriterien: 1..8 prüfbare Kriterien mit je 1..300 Zeichen angeben")
   if(chain==null)add("Feste Prüfkette ausdrücklich wählen: node oder android")
   else if(allowedPaths.any { if(it.endsWith(".kt"))chain!=Chain.ANDROID else chain!=Chain.NODE })
    add("Prüfkette passt nicht zu den ausgewählten Quelldateien; JS und Kotlin getrennt beauftragen")
   if(limits==null||limits.maxModelCalls !in 1..3||limits.maxContextChars !in 2048..24000)
    add("Ressourcenlimits: 1..3 Modellaufrufe und 2048..24000 Kontextzeichen ausdrücklich festlegen")
  }

  fun validatedTask():ContinuumCodingTask? {
   if(problems().isNotEmpty())return null
   return ContinuumCodingTask(id,sourceCommit,kind!!,goal,immutable(allowedPaths),immutable(acceptance),chain!!,limits!!)
  }
  fun snapshot()=copy(allowedPaths=immutable(allowedPaths),acceptance=immutable(acceptance))
  companion object {
   fun from(task:ContinuumCodingTask)=Draft(task.id,task.sourceCommit,task.kind,task.goal,
    immutable(task.allowedPaths),immutable(task.acceptance),task.chain,task.limits)
  }
 }

 fun problems():List<String> = Draft.from(this).problems()

 /** Same sorted-key JSON and UTF-8 SHA-256 as the canonical backlog/Node task contract. */
 fun canonicalJson():String {
  require(problems().isEmpty()) { "Ungültiger Coding-Auftragsentwurf" }
  return DevelopmentBacklog.canonical(mapOf(
   "schema" to "continuum-coding-task","version" to 1,"id" to id,"sourceCommit" to sourceCommit,
   "kind" to kind.name,"goal" to goal,"allowedPaths" to allowedPaths,"acceptance" to acceptance,
   "chain" to chain.wire,"limits" to mapOf("maxModelCalls" to limits.maxModelCalls,"maxContextChars" to limits.maxContextChars)))
 }
 fun fingerprint():String = DevelopmentBacklog.sha256Of(canonicalJson().toByteArray(Charsets.UTF_8))

 /** Explicit owner review of this exact draft. Worker capability/scope gates still apply. */
 data class Authorization(val task:ContinuumCodingTask,val taskSha256:String)

 companion object {
  private fun <T> immutable(values:List<T>):List<T> = Collections.unmodifiableList(ArrayList(values))
  private fun validText(value:String,max:Int)=value.isNotBlank()&&value.length<=max&&
   value.none { it.code in 0..8||it.code in 11..12||it.code in 14..31||it.code==127 }&&
   Charsets.UTF_8.newEncoder().canEncode(value)

  /** Intake is deliberately no broader than coding-task.mjs plus the existing repair critic. */
  fun isCodingPath(path:String):Boolean {
   if(path.length>180||!Regex("(?:lib/|src/|backend/src/|app/src/main/java/|\\.github/continuum/)[A-Za-z0-9_./-]+\\.(?:mjs|js|kt)").matches(path))return false
   if(path.contains("..")||path.split('/').any { it.isEmpty()||it=="."||it==".." })return false
   if(Regex("(^|/)(?:node_modules|build|dist|secrets?|credentials?|private|owner-data)(/|\\.)",RegexOption.IGNORE_CASE).containsMatchIn(path))return false
   if(Regex("(ToolGate|InternetConsent|SecureToken|Signer|Identity|Governance|Credential|Secret|Permission|ConsentPolicy)",RegexOption.IGNORE_CASE).containsMatchIn(path))return false
   if(Regex("(^|/)(test|fixtures)/|\\.test\\.m?js$").containsMatchIn(path))return false
   if(path.startsWith(".github/continuum/")) {
    val name=path.removePrefix(".github/continuum/")
    if(Regex("(?:coding-|ccc1-|isolated-|local-brain|local-numeric|step-watchdog|behaviour-probe|contract-|teamleader-|owner-|backlog-|development-|autonomous-|repair-|developer-agent|code-analyzer|kotlin-tokenizer|m4-|cegis-|cefr-|local-m4-).*").matches(name))return false
    if(name in setOf("node-contract-synthesizer.mjs","kotlin-delegate-synthesizer.mjs","kotlin-delegate-acceptance.mjs","local-teamleader-loop.mjs"))return false
   }
   return true
  }
 }
}
