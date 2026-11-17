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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ContinuumCodingPlanningUiTest {
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun open():Pair<AgentActivity,AlertDialog> {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        AgentActivity::class.java.getDeclaredMethod("showCodingTaskDraftDialog").apply { isAccessible=true }.invoke(a)
        return a to ShadowAlertDialog.getLatestAlertDialog()
    }
    @Test fun existingDraftFormOffersExplicitLocalModelPlanningEntry() {
        val (a,d)=open()
        try {
            val button=views(d.window!!.decorView).filterIsInstance<Button>()
                .firstOrNull { it.text=="MIT LOKALEM MODELL ENTWERFEN" }
            assertNotNull("The existing draft form has no actual local model planning caller",button)
            assertNull(shadowOf(a).nextStartedActivity)
            assertNull(shadowOf(a.application).nextStartedService)
        } finally { d.dismiss() }
    }
    @Test fun incompleteOwnerScopeCannotStartPlanningOrDispatch() {
        val (a,d)=open()
        try {
            val button=views(d.window!!.decorView).filterIsInstance<Button>()
                .firstOrNull { it.text=="MIT LOKALEM MODELL ENTWERFEN" }
            assertNotNull("Explicit planning entry missing",button)
            button!!.performClick()
            assertTrue("Incomplete owner scope must remain visible for correction",d.isShowing)
            assertNull(shadowOf(a).nextStartedActivity)
            assertNull(shadowOf(a.application).nextStartedService)
        } finally { d.dismiss() }
    }
    @Test fun modelDraftPrefillRequiresFreshOwnerReviewAndNeverDispatches() {
        val a=Robolectric.buildActivity(AgentActivity::class.java).get()
        val method=AgentActivity::class.java.declaredMethods.firstOrNull {
            it.name=="showCodingTaskDraftDialog" && it.parameterTypes.contentEquals(arrayOf(ContinuumCodingTask.Draft::class.java)) }
        assertNotNull("Validated model candidates cannot enter the existing full owner review",method)
        val draft=ContinuumCodingTask.Draft("job-owner-pinned","a".repeat(40),ContinuumCodingTask.Kind.BUGFIX,
            "Bewahre negative Ergebnisse",listOf("lib/distance.mjs"),listOf("Negative Werte bleiben negativ"),
            ContinuumCodingTask.Chain.NODE,ContinuumCodingTask.Limits(1,8192))
        method!!.apply { isAccessible=true }.invoke(a,draft)
        val d=ShadowAlertDialog.getLatestAlertDialog()
        try {
            val widgets=views(d.window!!.decorView)
            val fields=widgets.filterIsInstance<EditText>()
            assertEquals(draft.goal,fields[0].text.toString())
            assertEquals(draft.sourceCommit,fields[2].text.toString())
            assertEquals(draft.allowedPaths.single(),fields[3].text.toString())
            val prepare=widgets.filterIsInstance<Button>().single { it.text=="AUFTRAG AN TERMUX ÜBERGEBEN" }
            assertFalse("A model draft is not owner review",prepare.isEnabled)
            widgets.filterIsInstance<Button>().single { it.text=="ENTWURF PRÜFEN" }.performClick()
            assertTrue(prepare.isEnabled)
            fields[0].setText("Anderes Ziel")
            assertFalse(prepare.isEnabled)
            assertNull(shadowOf(a).nextStartedActivity)
            assertNull(shadowOf(a.application).nextStartedService)
        } finally { d.dismiss() }
    }
}
