package at.heinz.deviceai

/** Bounded requirements dialogue for two existing recipes. Interpretation never authorizes a tool. */
internal class ContinuumCodingConversation {
 data class Preview(val revision:Int,val message:String,val project:ContinuumOwnedProject?,val planSha256:String?,
  val explicit:List<String>,val inferred:List<String> = listOf("Zwei Ansichten; bestehende geprüfte M4-Vorlage"))
 private var revision=0
 private var project:ContinuumOwnedProject?=null
 private var local=false
 private var issue:String?=null
 private var conditional=false
 private var last=Preview(0,"Welche kleine App möchtest du: Notizen oder Zähler?",null,null,emptyList())
 fun preview()=last
 fun accept(input:String):Preview {
  revision++
  if(input.isBlank()||input.length>512||revision>32){issue="Dialoggrenze erreicht. Bitte neuen Auftrag starten.";return render()}
  val t=input.lowercase(java.util.Locale.ROOT)
  if(Regex("\\b(wenn|falls|würde|hätte)\\b").containsMatchIn(t)||input.any { it in "\"„“»«" }) {
   conditional=true
   issue="Bedingter oder zitierter Wunsch: Bitte den tatsächlichen Auftrag ausdrücklich formulieren.";return render()
  }
  if(t.startsWith("nein")||t in setOf("abbrechen","stopp","stop")) {project=null;local=false;issue="Auftrag verworfen. Bitte neuen Auftrag beginnen.";return render()}
  if(input.trimEnd().endsWith("?")) {
   last=Preview(revision,"Bitte formuliere oder bestätige die gewünschte Anforderung ausdrücklich.",null,null,last.explicit)
   return last
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
  return render()
 }
 private fun render():Preview {
  val ready=project?.takeIf { issue==null&&local }
  val message=issue?:when {
   project==null->"Welche App meinst du: Notizen oder Zähler?"
   !local->"Sollen die Daten nur lokal auf deinem Gerät bleiben? Diese Vorlage unterstützt keine Synchronisierung."
   else->"Planvorschau: ${project!!.title}, nur lokale Speicherung, keine Berechtigungen. Zwei Ansichten, feste geprüfte Vorlage. Noch nichts erzeugt oder gestartet."
  }
  last=Preview(revision,message,ready,ready?.planSha256,listOfNotNull(project?.let { "Projekt: ${it.title}" },if(local)"Speicherung: lokal" else null))
  return last
 }
 /** A stale UI confirmation is never valid for changed requirements. Single-use. */
 fun approve(expectedRevision:Int,expectedHash:String):ContinuumOwnedProject? {
  val p=last.project?:return null
  if(expectedRevision!=revision||expectedHash!=p.planSha256)return null
  revision++;last=last.copy(revision=revision,project=null,planSha256=null,message="Zur Besitzerkontrolle an den Builder übergeben; noch kein Build gestartet.")
  return p
 }
}
