package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import at.heinz.deviceai.learning.context.Ctx3Shadow

/** Separate visible research panel. Never changes the premium chat's answer or dispatches a build. */
class ContinuumM5LabActivity:Activity() {
 private var conversation=ContinuumCodingConversation()
 private val acceptedTurns=ArrayList<String>()
 private lateinit var draftInput:EditText
 private lateinit var transcriptView:TextView
 override fun onSaveInstanceState(outState:Bundle) {
  outState.putInt("m5_state_version",1)
  outState.putBoolean("m5_requires_restart",conversation.preview().revision>32)
  outState.putStringArrayList("m5_turns_v1",ArrayList(acceptedTurns))
  if(::draftInput.isInitialized)outState.putString("m5_draft_v1",draftInput.text.toString().take(512))
  if(::transcriptView.isInitialized)outState.putString("m5_transcript_v1",transcriptView.text.toString().takeLast(12000))
  super.onSaveInstanceState(outState)
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val restored=runCatching {savedInstanceState?.getStringArrayList("m5_turns_v1")}.getOrNull()
  val restoreValid=savedInstanceState?.getInt("m5_state_version")==1&&savedInstanceState.getBoolean("m5_requires_restart")==false&&restored!=null&&restored.size<=32&&restored.all {it.length<=512}
  if(restoreValid) {
   for(turn in restored!!){acceptedTurns.add(turn);conversation.accept(turn)}
  }
  val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) }
  fun text(value:String)=TextView(this).apply { text=value;textSize=16f;setPadding(0,12,0,12);column.addView(this) }
  fun button(title:String,run:()->Unit)=Button(this).apply { text=title;isAllCaps=false;setOnClickListener { run() };column.addView(this) }
  text("M5 · Kontext- und Auftragslabor · FORSCHUNG")
  text("Sichtbare Chatantwort: bestehender Kern. CTX3 läuft nur nach Einschalten im Schatten; Abweichungen sind kein Qualitätsnachweis. Kein Gewichtetraining.")
  val core=at.heinz.deviceai.learning.ContinuumLearningCore(filesDir)
  val shadowStatus=text("")
  val shadow=Button(this).apply {isAllCaps=false;column.addView(this)}
  fun refreshShadow() {
   val on=core.contextShadowEnabled()
   shadow.text="CTX3 Shadow: "+if(on)"EIN" else "AUS"
   val e=core.recentContextShadowEvents().lastOrNull()
   shadowStatus.text="Aktiver Antwortkern: bestehender Gedächtnisdialog. Forschung: CTX3 ${if(on)"SHADOW" else "OFF"}."+
    (e?.let { "\nHistorische Sitzung: ${it.conversationSha256.take(12)}; Quellenrevision: ${it.sourceVersion.take(12)}\nEntscheidung: ${it.decision}; Grund: ${it.reason}\nBaseline-Quellen: ${it.baselineIds}\nShadow-Quellen: ${it.proposedIds}" }?:"\nNoch kein Shadow-Bericht in dieser Sitzung.")
  }
  shadow.setOnClickListener {
   runCatching {core.configureContextShadow(!core.contextShadowEnabled())}.onFailure {shadowStatus.text="Schalter konnte nicht gesichert werden."}
   refreshShadow()
  }
  button("Shadow-Bericht aktualisieren"){refreshShadow()};refreshShadow()
  text("Ohne gesicherten Android-Zustand bitte den unbestätigten Auftrag neu eingeben. Freigaben werden nie wiederhergestellt.")
  text("Gespräch → geprüfter Projektplan. Unterstützt: Notizen oder Zähler, nur lokal. Keine Aufgabenverwaltung mit Abhaken/Löschen oder Synchronisierung.")
  val transcript=text(if(restoreValid)savedInstanceState?.getString("m5_transcript_v1")?.takeLast(12000)?:conversation.preview().message else conversation.preview().message)
  transcriptView=transcript
  if(acceptedTurns.isNotEmpty())transcript.append("\nAngaben wiederhergestellt. Bitte Plan erneut kontrollieren; keine Freigabe übernommen.")
  val input=EditText(this).apply { hint="Dein App-Wunsch";maxLines=4;filters=arrayOf(android.text.InputFilter.LengthFilter(512));column.addView(this) }
  draftInput=input
  input.setText(savedInstanceState?.getString("m5_draft_v1")?.take(512).orEmpty())
  val handoff=Button(this).apply {text="Plan prüfen und an Builder übergeben";isAllCaps=false;isEnabled=false}
  // A changed but unsent draft revokes a ready plan, including an already open dialog.
  // Changes remain revoking even if the user deletes the draft back to its old text.
  var draftVersion=0L
  input.addTextChangedListener(object:TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) = Unit
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) = Unit
   override fun afterTextChanged(s:Editable?) {draftVersion++;handoff.isEnabled=false}
  })
  fun catalogValid()=runCatching {
   assets.open("continuum-owned-project-plans-v1.json").bufferedReader().use {ContinuumOwnedProjectPlan.readCatalog(it.readText())};true
  }.getOrDefault(false)
  button("Anforderung ergänzen") {
   val value=input.text.toString();val p=conversation.accept(value);input.text.clear()
   if(acceptedTurns.size<32)acceptedTurns.add(value)
   transcript.text=(transcript.text.toString()+"\nDu: "+value+"\nContinuum: "+p.message).takeLast(12000)
   handoff.isEnabled=p.project!=null&&catalogValid()
  }
  handoff.isEnabled=conversation.preview().project!=null&&input.text.isEmpty()&&catalogValid()
  column.addView(handoff)
  handoff.setOnClickListener {
   if(input.text.isNotEmpty())return@setOnClickListener
   val p=conversation.preview();val project=p.project?:return@setOnClickListener
   val requestedConversation=conversation
   val shownDraftVersion=draftVersion
   AlertDialog.Builder(this).setTitle("Projektplan kontrollieren")
    .setMessage(p.message+"\nExplizit: "+p.explicit.joinToString()+"\nVorlagenannahmen: "+p.inferred.joinToString()+
     "\nPaket: "+project.packageName+"\nPlanhash: "+project.planSha256+"\nRevision: "+p.revision+
     "\nDie Bestätigung öffnet nur den vorhandenen Builder. Erzeugen und Bauen brauchen dort erneut deine Bestätigung.")
    .setNegativeButton("Abbrechen",null).setPositiveButton("Plan übernehmen") { _,_->
     if(!isFinishing&&!isDestroyed&&conversation===requestedConversation&&draftVersion==shownDraftVersion&&input.text.isEmpty()&&catalogValid())conversation.approve(p.revision,p.planSha256!!)?.let { selected->
      startActivity(Intent(this,ContinuumMobileBuilderActivity::class.java)
       .putExtra("m5_project",selected.id).putExtra("m5_plan_sha",selected.planSha256))
      handoff.isEnabled=false
     }
    }.show()
  }
  button("Neuen Auftrag beginnen"){conversation=ContinuumCodingConversation();acceptedTurns.clear();transcript.text=conversation.preview().message;handoff.isEnabled=false;input.text.clear()}
  button("Zurück"){finish()}
  setContentView(ScrollView(this).apply {addView(column)})
 }
}
