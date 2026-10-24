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
