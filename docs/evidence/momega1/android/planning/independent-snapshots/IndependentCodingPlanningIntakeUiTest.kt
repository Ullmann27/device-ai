package at.heinz.deviceai

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/** Real existing Activity dialog, no startup/training/services. Frozen before feature implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class IndependentCodingPlanningIntakeUiTest {
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
    private fun intake():Pair<AgentActivity,AlertDialog> {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        AgentActivity::class.java.getDeclaredMethod("showCodingTaskDraftDialog").apply {isAccessible=true}.invoke(a)
        return a to ShadowAlertDialog.getLatestAlertDialog()
    }
    @Test fun optionalModelPlanningEntryExistsWithinExistingManualDraftDialog() {
        val(a,d)=intake()
        try {
            val all=views(d.window!!.decorView)
            assertEquals(1,all.filterIsInstance<Button>().count {it.text=="MIT LOKALEM MODELL ENTWERFEN"})
            assertEquals(8,all.filterIsInstance<EditText>().size)
            assertTrue(all.filterIsInstance<Button>().any {it.text=="ENTWURF PRÜFEN"})
            assertNull(shadowOf(a).nextStartedActivity);assertNull(shadowOf(a).nextStartedService)
        } finally {d.dismiss()}
    }
    @Test fun missingOrInvalidOwnerMetadataCannotLeaveDialogOrTouchRuntime() {
        val(a,d)=intake()
        try {
            val all=views(d.window!!.decorView);val f=all.filterIsInstance<EditText>()
            val b=all.filterIsInstance<Button>().single {it.text=="MIT LOKALEM MODELL ENTWERFEN"}
            // localRuntime intentionally uninitialized: invalid intake must reject before runtime access.
            b.performClick();assertTrue(d.isShowing)
            f[0].setText("Fix negative distance");f[2].setText("abc123");f[3].setText("src/distance.mjs")
            b.performClick();assertTrue(d.isShowing)
            f[2].setText("a".repeat(40));f[3].setText("src/../owner-data/private.mjs")
            b.performClick();assertTrue(d.isShowing)
            assertNull(shadowOf(a).nextStartedActivity);assertNull(shadowOf(a).nextStartedService)
        } finally {d.dismiss()}
    }
}
