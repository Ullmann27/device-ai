package at.heinz.deviceai

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import at.heinz.deviceai.localmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService

/** Actual owner-composer caller; requires the coordinator's bounded Activity patch. No model is run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumLiveCapabilitiesUiTest {
    private fun get(a: AgentActivity, n: String): Any? = AgentActivity::class.java.getDeclaredField(n).apply { isAccessible = true }.get(a)
    private fun set(a: AgentActivity, n: String, v: Any?) = AgentActivity::class.java.getDeclaredField(n).apply { isAccessible = true }.set(a, v)
    private fun call(a: AgentActivity, n: String, vararg args: Any?): Any? = AgentActivity::class.java.declaredMethods.single {
        it.name == n && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(a, *args)
    private fun texts(v: View): List<String> = (if (v is TextView) listOf(v.text.toString()) else emptyList()) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    private class Runtime : LocalModelRuntime {
        override var state = LocalModelState.EMPTY
        override var loadedModel: ImportedLocalModel? = null
        var cancels = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("Status must not load model")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("Status must not unload model")
        override fun stream(prompt: String, callback: LocalInferenceCallback) = error("Status must not infer")
        override fun cancel() { cancels++ }
    }
    private fun withActivity(test: (AgentActivity, Runtime) -> Unit) {
        val a = Robolectric.buildActivity(AgentActivity::class.java).get()
        val runtime = Runtime()
        set(a, "localRuntime", runtime)
        set(a, "bootstrapMemory", BootstrapMemoryStore(a))
        set(a, "chatContainer", LinearLayout(a)); set(a, "activityText", TextView(a))
        set(a, "voiceEnabled", false); set(a, "foregroundLearningActive", true)
        call(a, "buildComposer")
        try { test(a, runtime) } finally {
            for (n in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor")) (get(a, n) as ExecutorService).shutdownNow()
        }
    }
    private fun ask(a: AgentActivity): String {
        (get(a, "input") as EditText).setText("Was kannst du alles?")
        call(a, "sendCurrentInput", false)
        return texts(get(a, "chatContainer") as View).last { it.contains("Continuum · Aktuelle App-Fähigkeiten") }
    }
    @Test fun actualOwnerComposerReportsLiveReadinessWithoutModelOrBotStoreWrites() = withActivity { a, runtime ->
        val botRoot = java.io.File(a.filesDir, "chief-bots")
        assertFalse(botRoot.exists())
        val empty = ask(a)
        assertTrue(empty.contains("keines geladen")); assertTrue(empty.contains("Importierte Modelle: unbekannt"))
        runtime.state = LocalModelState.LOADING
        val loading = ask(a)
        assertTrue(loading.contains("wird geladen")); assertNotEquals(empty, loading)
        runtime.state = LocalModelState.READY
        runtime.loadedModel = ImportedLocalModel("public-model", "v1", "a".repeat(64), 1,
            LocalModelCapabilities(true, false, false, false, false, 512, null, setOf(LocalModelBackend.CPU)))
        assertTrue(ask(a).contains("public-model v1 ist geladen und bereit"))
        assertEquals(0, runtime.cancels); assertFalse(botRoot.exists()); assertNull(get(a, "activeBootstrapGate"))
    }
    @Test fun busyReadOnlySubmissionPreservesLeaseSelectedBotAndPendingLookup() = withActivity { a, runtime ->
        runtime.state = LocalModelState.GENERATING
        set(a, "busy", true); set(a, "botLookupPending", true); set(a, "activeBotId", "private-bot-id")
        val profile = at.heinz.deviceai.chief.ChiefBotProfile("private-bot-id", 1, "PRIVATE DISPLAY NAME",
            "PRIVATE ROLE", "PRIVATE COMMUNICATION", at.heinz.deviceai.chief.ChiefBotProfile.MODEL_POLICY,
            "bot-private-bot-id", "workspace-private-bot-id", at.heinz.deviceai.chief.ChiefBotResourceBudget(),
            at.heinz.deviceai.chief.ChiefBotProfile.PROVENANCE)
        set(a, "selectedBot", profile)
        val before = get(a, "botLookupRevision")
        val answer = ask(a)
        assertTrue(answer.contains("gerade mit einer Antwort beschäftigt")); assertFalse(answer.contains("private-bot-id")); assertFalse(answer.contains("PRIVATE"))
        assertSame(profile, get(a, "selectedBot"))
        assertEquals(true, get(a, "busy")); assertEquals(true, get(a, "botLookupPending"))
        assertEquals("private-bot-id", get(a, "activeBotId")); assertEquals(before, get(a, "botLookupRevision"))
        assertEquals(0, runtime.cancels)
        assertFalse(java.io.File(a.filesDir, "chief-bots").exists())
    }
    @Test fun sendButtonStillCancelsWhileItActsAsStop() = withActivity { a, runtime ->
        runtime.state = LocalModelState.GENERATING
        set(a, "busy", true); set(a, "activeLocalRequest", true)
        (get(a, "input") as EditText).setText("Was kannst du?")
        (get(a, "sendButton") as View).performClick()
        assertTrue("Existing Stop click must reach runtime cancellation", runtime.cancels > 0)
        assertFalse(texts(get(a, "chatContainer") as View).any { it.contains("Aktuelle App-Fähigkeiten") })
    }
}
