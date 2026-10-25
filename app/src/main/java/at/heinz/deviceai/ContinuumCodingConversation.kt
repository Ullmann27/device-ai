package at.heinz.deviceai

/** Bounded template dialogue and separate general task drafts. Interpretation never authorizes a tool. */
internal class ContinuumCodingConversation {
 data class Preview(val revision:Int,val message:String,val project:ContinuumOwnedProject?,val planSha256:String?,
  val explicit:List<String>,val inferred:List<String> = listOf("Zwei Ansichten; bestehende geprüfte M4-Vorlage"),
  val codingDraft:ContinuumCodingTask.Draft?=null,val codingTask:ContinuumCodingTask?=null,val taskSha256:String?=null)
 private var revision=0
 private var project:ContinuumOwnedProject?=null
 private var local=false
 private var issue:String?=null
 private var conditional=false
 private var questionPending=false
 private var codingDraft:ContinuumCodingTask.Draft?=null
 private var codingReviewPending=false
 private var last=Preview(0,"Welche kleine App möchtest du: Notizen oder Zähler?",null,null,emptyList())
 @Synchronized fun preview()=last
 @Synchronized fun accept(input:String):Preview {
  revision++
  if(input.isBlank()||input.length>512||revision>32){issue="Dialoggrenze erreicht. Bitte neuen Auftrag starten.";return if(codingDraft!=null)renderCoding(issue) else render()}
  val t=input.trim().lowercase(java.util.Locale.ROOT)
  if(codingDraft!=null||Regex("\\b(fehler|bug|bugfix|feature|refaktor|refactoring|compiler|abhängigkeit|abhängigkeiten|kotlin|javascript|quellcode|repository)\\b").containsMatchIn(t)) {
   project=null;local=false
   if(t.startsWith("nein")||t in setOf("abbrechen","stopp","stop")) {
    codingDraft=null;codingReviewPending=false
    last=Preview(revision,"Coding-Auftragsentwurf verworfen. Kein Auftrag übergeben.",null,null,emptyList(),emptyList())
    return last
   }
   val existing=codingDraft
   codingDraft=existing?.copy(goal=existing.goal+"\n"+input.trim())?:ContinuumCodingTask.Draft(goal=input.trim())
   codingReviewPending=true
   val indirect=input.contains('?')||input.any { it in "\"„“»«" }||
    Regex("\\b(wenn|falls|würde|hätte|dokument|zitat|sagte|sagt|laut|nicht|kein(?:e|en|em|er|es)?)\\b").containsMatchIn(t)
   return renderCoding(if(indirect)"Bitte formuliere den tatsächlichen Coding-Auftrag ausdrücklich. Fragen, Zitate und Bedingungen sind keine Freigabe."
    else if(existing!=null)"Weitere Anforderung erfasst: Bitte die Auftragsfelder erneut ausdrücklich prüfen."
    else null)
  }
  if(Regex("\\b(wenn|falls|würde|hätte)\\b").containsMatchIn(t)||input.any { it in "\"„“»«" }) {
   conditional=true
   issue="Bedingter oder zitierter Wunsch: Bitte den tatsächlichen Auftrag ausdrücklich formulieren.";return render()
  }
  if(t.startsWith("nein")||t in setOf("abbrechen","stopp","stop")) {project=null;local=false;issue="Auftrag verworfen. Bitte neuen Auftrag beginnen.";return render()}
  if(input.contains("?")) {
   questionPending=true
   return render()
  }
  if(t.contains("noch nicht")||Regex("\\b(dokument|zitat|zitiere|sagte|sagt|laut)\\b").containsMatchIn(t)) {
   issue="Zurückgestellter oder indirekter Auftrag: keine Freigabe. Bitte neuen ausdrücklichen Auftrag beginnen.";return render()
  }
  // The only supported negative statements describe absence of Internet. Other
  // negations are unresolved requirements, never permission to keep a ready plan.
  val noInternet=Regex("\\b(?:kein internet|internet\\s+(?:brauch(?:e)?\\s+ich\\s+)?nicht)\\b")
  if(Regex("\\b(nicht|kein(?:e|en|em|er|es)?|nie|niemals|keinesfalls|nichts|weder|ohne)\\b").containsMatchIn(noInternet.replace(t,""))) {
   issue="Verneinter oder zurückgenommener Auftrag. Bitte einen neuen eindeutigen Auftrag starten.";return render()
  }
  val unsupported=Regex("\\b(abhaken|löschen|loeschen|synchronisieren|sync|cloud|aufgaben|todo)\\b").containsMatchIn(t)
  if(unsupported){issue="Diese Funktion wird von den zwei Vorlagen noch nicht unterstützt. Bitte einen neuen begrenzten Auftrag starten.";return render()}
  if(conditional&&(t.startsWith("ich möchte")||t.startsWith("ich will"))){issue=null;conditional=false}
  if(issue!=null)return render()
  val notes=Regex("\\b(notizen|notiz|notizapp|texte?|aufschreiben)\\b").containsMatchIn(t)
  val counter=Regex("\\b(zähler|zaehler|zählen|zaehlen|zählerapp)\\b").containsMatchIn(t)
  if(notes&&counter||Regex("\\b(nicht|keine?)\\s+(?:die\\s+)?(?:notizen|notiz|notizapp|zähler|zaehler|zählerapp|zaehlerapp)\\b").containsMatchIn(t)) {
   issue="Die Projektart ist widersprüchlich. Bitte einen neuen eindeutigen Auftrag starten.";return render()
  }
  if(notes||counter) {
   val wanted=if(notes)ContinuumOwnedProject.NOTES else ContinuumOwnedProject.COUNTER
   if(project!=null&&project!=wanted)local=false
   project=wanted
  }
  val offline=Regex("\\b(offline|lokal|nur auf dem gerät|nur auf meinem handy)\\b").containsMatchIn(t)||
   Regex("internet\\s+(?:brauch(?:e)?\\s+ich\\s+)?nicht").containsMatchIn(t)||t.contains("kein internet")
  if(Regex("nicht(?:\\s+\\w+){0,2}\\s+(?:lokal|offline)").containsMatchIn(t)||t.contains("nicht kein")||
   (t.contains("internet")&&!offline)||t.contains("online")) {
   issue="Internet/Synchronisierung wird von diesen Offline-Plänen nicht unterstützt.";local=false
  } else if(offline)local=true
  if(notes||counter||offline)questionPending=false
  return render()
 }
 /** Typed field entry is separate from free text and from the owner's eventual confirmation. */
 @Synchronized fun draftCodingTask(draft:ContinuumCodingTask.Draft):Preview {
  revision++
  project=null;local=false;issue=null;questionPending=false;conditional=false
  codingDraft=draft.snapshot();codingReviewPending=false
  return renderCoding(if(revision>32)"Dialoggrenze erreicht. Bitte neuen Auftrag starten." else null)
 }
 private fun renderCoding(reason:String?):Preview {
  val draft=codingDraft
  val task=if(reason==null&&!codingReviewPending&&revision<=32)draft?.validatedTask() else null
  val missing=draft?.problems().orEmpty()
  val message=buildString {
   if(reason!=null)append(reason).append('\n')
   if(missing.isNotEmpty())append("Für den Coding-Auftragsentwurf fehlen oder stimmen noch nicht:\n").append(missing.joinToString("\n"))
   else if(task==null)append("Bitte Commit, Dateien und Akzeptanzkriterien in den Auftragsfeldern erneut prüfen.")
   else append("Auftragsentwurf vollständig. Fingerabdruck: ").append(task.fingerprint()).append('\n')
   append("\nEntwurf ohne Ausführung. Worker-Konfiguration und ausdrückliche Besitzerbestätigung sind für eine spätere Übergabe erforderlich.")
   if(draft?.kind in setOf(ContinuumCodingTask.Kind.TEST,ContinuumCodingTask.Kind.DEPENDENCY))
    append(" TEST/DEPENDENCY: zunächst Untersuchung; Test- und Builddateien bleiben geschützt.")
  }
  last=Preview(revision,message,null,null,listOfNotNull(draft?.goal?.takeIf { it.isNotBlank() }),emptyList(),draft,task,task?.fingerprint())
  return last
 }
 /** One explicit UI confirmation consumes this exact revision; no worker is invoked here. */
 @Synchronized fun approveCodingTask(expectedRevision:Int,expectedHash:String,ownerConfirmed:Boolean):ContinuumCodingTask.Authorization? {
  if(!ownerConfirmed||expectedRevision!=revision)return null
  val task=last.codingTask?:return null
  if(expectedHash!=last.taskSha256||expectedHash!=task.fingerprint())return null
  codingDraft=null;codingReviewPending=false
  revision++
  last=Preview(revision,"Dieser Auftragsentwurf wurde ausdrücklich bestätigt. Kein Worker gestartet; Übergabe benötigt Worker-Konfiguration.",null,null,emptyList(),emptyList())
  return ContinuumCodingTask.Authorization(task,expectedHash)
 }
 private fun render():Preview {
  val ready=project?.takeIf { issue==null&&local&&!questionPending }
  val message=issue?:when {
   questionPending->"Bitte formuliere oder bestätige die gewünschte Anforderung ausdrücklich: Notizen, Zähler oder lokale Speicherung."
   project==null->"Welche App meinst du: Notizen oder Zähler?"
   !local->"Sollen die Daten nur lokal auf deinem Gerät bleiben? Diese Vorlage unterstützt keine Synchronisierung."
   else->"Planvorschau: ${project!!.title}, nur lokale Speicherung, keine Berechtigungen. Zwei Ansichten, feste geprüfte Vorlage. Noch nichts erzeugt oder gestartet."
  }
  last=Preview(revision,message,ready,ready?.planSha256,listOfNotNull(project?.let { "Projekt: ${it.title}" },if(local)"Speicherung: lokal" else null))
  return last
 }
 /** A stale UI confirmation is never valid for changed requirements. Single-use. */
 @Synchronized fun approve(expectedRevision:Int,expectedHash:String):ContinuumOwnedProject? {
  val p=last.project?:return null
  if(expectedRevision!=revision||expectedHash!=p.planSha256)return null
  project=null;local=false;questionPending=false
  revision++;last=last.copy(revision=revision,project=null,planSha256=null,message="Zur Besitzerkontrolle an den Builder übergeben; noch kein Build gestartet.")
  return p
 }
}
