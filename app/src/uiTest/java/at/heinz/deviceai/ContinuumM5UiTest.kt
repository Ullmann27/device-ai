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
