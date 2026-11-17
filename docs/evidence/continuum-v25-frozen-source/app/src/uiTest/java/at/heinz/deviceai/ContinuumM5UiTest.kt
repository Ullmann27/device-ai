package at.heinz.deviceai
import android.app.AlertDialog
import android.widget.*
import android.view.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class ContinuumM5UiTest {
 private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap { views(v.getChildAt(it)) }else emptyList())
 @Test fun unsupportedSavedStateCannotDisplayAnApparentlyValidOldPlan() {
  val bundle=android.os.Bundle().apply {putInt("m5_state_version",99);putString("m5_transcript_v1","Planvorschau: veralteter Auftrag")}
  val c=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).create(bundle).start().resume()
  try {
   val all=views(c.get().findViewById(android.R.id.content))
   assertFalse(all.filterIsInstance<TextView>().any {it.text.toString().contains("veralteter Auftrag")})
   assertFalse(all.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}.isEnabled)
  } finally {c.pause().stop().destroy()}
 }
 @Test fun oldDialogCannotApproveANewConversationWithTheSamePlanRevision() {
  val c=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  try {
   val a=c.get();val all=views(a.findViewById(android.R.id.content))
   val input=all.filterIsInstance<EditText>().single()
   val send=all.filterIsInstance<Button>().single {it.text=="Anforderung ergänzen"}
   val handoff=all.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
   input.setText("Notizen offline");send.performClick();handoff.performClick()
   val stale=ShadowAlertDialog.getLatestAlertDialog()
   all.filterIsInstance<Button>().single {it.text=="Neuen Auftrag beginnen"}.performClick()
   input.setText("Notizen offline");send.performClick()
   stale.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
   assertNull(Shadows.shadowOf(a).nextStartedActivity)
  } finally {c.pause().stop().destroy()}
 }
 @Test fun recreationKeepsRequirementsAndDraftWithoutAuthorizingExecution() {
  val old=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  val a=old.get();var all=views(a.findViewById(android.R.id.content))
  val input=all.filterIsInstance<EditText>().single()
  input.setText("Notizen App");all.filterIsInstance<Button>().single { it.text=="Anforderung ergänzen" }.performClick()
  input.setText("Mein ungesendeter Entwurf")
  val state=android.os.Bundle();old.saveInstanceState(state).pause().stop().destroy()
  val newer=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).create(state).start().resume()
  try {
   all=views(newer.get().findViewById(android.R.id.content))
   val nextInput=all.filterIsInstance<EditText>().single()
   assertEquals("Mein ungesendeter Entwurf",nextInput.text.toString())
   nextInput.setText("offline");all.filterIsInstance<Button>().single { it.text=="Anforderung ergänzen" }.performClick()
   assertTrue(all.filterIsInstance<Button>().single { it.text=="Plan prüfen und an Builder übergeben" }.isEnabled)
   assertNull(Shadows.shadowOf(newer.get()).nextStartedActivity)
  } finally {newer.pause().stop().destroy()}
 }
 @Test fun changedUnsubmittedDraftInvalidatesOpenBuilderConfirmation() {
  val controller=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  try {
   val a=controller.get()
   val all=views(a.findViewById(android.R.id.content))
   val input=all.filterIsInstance<EditText>().single()
   val send=all.filterIsInstance<Button>().single {it.text=="Anforderung ergänzen"}
   val handoff=all.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
   input.setText("Notizen offline")
   send.performClick()
   assertTrue(handoff.isEnabled)
   handoff.performClick()
   val stale=ShadowAlertDialog.getLatestAlertDialog()
   input.setText("Bitte doch nicht bauen")
   assertFalse(handoff.isEnabled)
   stale.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
   assertNull(Shadows.shadowOf(a).nextStartedActivity)
   input.text.clear()
   assertFalse("Clearing an unsubmitted correction must not restore the old approval",handoff.isEnabled)
   input.setText("Notizen offline")
   send.performClick()
   assertTrue("A newly submitted explicit request can be reviewed again",handoff.isEnabled)
   assertNull(Shadows.shadowOf(a).nextStartedActivity)
  } finally {controller.pause().stop().destroy()}
 }
 @Test fun recreationWithAnUnsubmittedDraftNeverRestoresPlanApproval() {
  val original=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  val root=views(original.get().findViewById(android.R.id.content))
  val input=root.filterIsInstance<EditText>().single()
  val send=root.filterIsInstance<Button>().single {it.text=="Anforderung ergänzen"}
  val handoff=root.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
  input.setText("Notizen offline")
  send.performClick()
  assertTrue(handoff.isEnabled)
  input.setText("Stopp, noch nicht bauen")
  assertFalse(handoff.isEnabled)
  val saved=android.os.Bundle()
  original.saveInstanceState(saved).pause().stop().destroy()
  val restored=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).create(saved).start().resume()
  try {
   val restoredViews=views(restored.get().findViewById(android.R.id.content))
   val restoredInput=restoredViews.filterIsInstance<EditText>().single()
   val restoredHandoff=restoredViews.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
   assertEquals("Stopp, noch nicht bauen",restoredInput.text.toString())
   assertFalse("Restoration of unsent text must never silently restore approval",restoredHandoff.isEnabled)
   assertNull(Shadows.shadowOf(restored.get()).nextStartedActivity)
  } finally {restored.pause().stop().destroy()}
 }
 @Test fun consumedBuilderPlanCannotBeRearmedByActivityRecreation() {
  val current=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  val activity=current.get()
  val all=views(activity.findViewById(android.R.id.content))
  val input=all.filterIsInstance<EditText>().single()
  val send=all.filterIsInstance<Button>().single {it.text=="Anforderung ergänzen"}
  val handoff=all.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
  input.setText("Notizen offline")
  send.performClick()
  assertTrue(handoff.isEnabled)
  handoff.performClick()
  ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
  assertEquals(ContinuumMobileBuilderActivity::class.java.name,
   Shadows.shadowOf(activity).nextStartedActivity.component!!.className)
  assertFalse(handoff.isEnabled)
  val state=android.os.Bundle()
  current.saveInstanceState(state).pause().stop().destroy()
  assertTrue("An approved and consumed plan must not be persisted as a replayable request",
   state.getStringArrayList("m5_turns_v1").isNullOrEmpty())
  val recreated=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).create(state).start().resume()
  try {
   val resumed=views(recreated.get().findViewById(android.R.id.content))
   val nextHandoff=resumed.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
   assertFalse("Consumed handoff must remain unavailable after recreation",nextHandoff.isEnabled)
   assertNull(Shadows.shadowOf(recreated.get()).nextStartedActivity)
  } finally {recreated.pause().stop().destroy()}
 }
 @Test fun chatCarriedProjectTextIsOnlyAnUnsubmittedDraft() {
  val request=android.content.Intent(RuntimeEnvironment.getApplication(),ContinuumM5LabActivity::class.java)
   .putExtra("m5_initial_draft_v1","Ich möchte eine Notizen App nur lokal")
  val controller=Robolectric.buildActivity(ContinuumM5LabActivity::class.java,request).setup()
  try {
   val a=controller.get()
   val all=views(a.findViewById(android.R.id.content))
   val input=all.filterIsInstance<EditText>().single()
   val handoff=all.filterIsInstance<Button>().single {it.text=="Plan prüfen und an Builder übergeben"}
   assertEquals("Ich möchte eine Notizen App nur lokal",input.text.toString())
   assertFalse(handoff.isEnabled)
   assertNull(Shadows.shadowOf(a).nextStartedActivity)
   all.filterIsInstance<Button>().single {it.text=="Anforderung ergänzen"}.performClick()
   assertTrue(handoff.isEnabled)
   assertNull(Shadows.shadowOf(a).nextStartedActivity)
  } finally {controller.pause().stop().destroy()}
 }
 @Test fun naturalRequestRequiresPlanReviewAndOnlyOpensExistingBuilder() {
  val controller=Robolectric.buildActivity(ContinuumM5LabActivity::class.java).setup()
  try {
   val a=controller.get();val all=views(a.findViewById(android.R.id.content))
   val input=all.filterIsInstance<EditText>().single()
   val send=all.filterIsInstance<Button>().single { it.text=="Anforderung ergänzen" }
   val handoff=all.filterIsInstance<Button>().single { it.text=="Plan prüfen und an Builder übergeben" }
   assertTrue(all.filterIsInstance<Button>().any { it.text.toString().contains("Shadow: AUS") })
   input.setText("Servus, ich möchte eine App für Notizen");send.performClick();assertFalse(handoff.isEnabled)
   input.setText("Nur lokal, kein Internet");send.performClick();assertTrue(handoff.isEnabled)
   handoff.performClick();assertNull(Shadows.shadowOf(a).nextStartedActivity)
   ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
   val next=Shadows.shadowOf(a).nextStartedActivity
   assertEquals(ContinuumMobileBuilderActivity::class.java.name,next.component!!.className)
   assertEquals("m4-notes",next.getStringExtra("m5_project"))
   assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
  } finally {controller.pause().stop().destroy()}
 }
}
