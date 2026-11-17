package at.heinz.deviceai

import android.app.Activity
import android.net.Uri
import android.webkit.WebResourceRequest
import at.heinz.deviceai.ui.Command03Command
import at.heinz.deviceai.ui.Command03Binding
import at.heinz.deviceai.ui.ContinuumCommand03OriginalStage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises the actual WebView client and its native transport, without a browser/GPU claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumCommand03StageUiTest {
    private val session = "11111111-1111-4111-8111-111111111111"
    private lateinit var scope: Command03Binding

    private fun request(value: String, id: String = "request-1", mainFrame: Boolean = true,
        kind: String = "command", sessionId: String = session, binding: Command03Binding = scope): WebResourceRequest {
        val uri = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
            .appendQueryParameter("value", value).appendQueryParameter("id", id)
            .appendQueryParameter("sessionId", sessionId)
            .appendQueryParameter("pageId", binding.pageId).appendQueryParameter("viewId", binding.viewId)
            .appendQueryParameter("viewRevision", binding.viewRevision.toString())
            .appendQueryParameter("draftText", value).appendQueryParameter("clientEditRevision", "1")
            .appendQueryParameter("selectionStart", value.length.toString())
            .appendQueryParameter("selectionEnd", value.length.toString()).build()
        return object : WebResourceRequest {
            override fun getUrl() = uri
            override fun isForMainFrame() = mainFrame
            override fun isRedirect() = false
            override fun hasGesture() = true
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = emptyMap<String, String>()
        }
    }

    private fun withStage(block: (ContinuumCommand03OriginalStage, MutableList<Command03Command>) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val calls = mutableListOf<Command03Command>()
        val stage = ContinuumCommand03OriginalStage(controller.get(),
            onNativeAction = { _, _ -> },
            onNativeCommand = { command, complete -> calls.add(command); complete(true, null) },
            onNativeDraft = { true })
        scope = Command03Binding(stage.pageId, "22222222-2222-4222-8222-222222222222", 1L, session)
        assertTrue(stage.bind(scope))
        try { block(stage, calls) } finally { stage.destroy(); controller.pause().stop().destroy() }
    }

    private fun pageReady(stage: ContinuumCommand03OriginalStage) =
        stage.webViewClient.onPageFinished(stage, ContinuumCommand03OriginalStage.INDEX)
    private fun payload(stage: ContinuumCommand03OriginalStage, method: String): JSONObject {
        val script = shadowOf(stage).lastEvaluatedJavascript
        val prefix = "window.ContinuumNative?.$method("
        assertTrue("Expected $method delivery, received $script", script.startsWith(prefix))
        return JSONObject(script.removePrefix(prefix).removeSuffix(");"))
    }
    private fun state(binding: Command03Binding) = JSONObject().put("pageId", binding.pageId)
        .put("viewId", binding.viewId).put("viewRevision", binding.viewRevision).put("sessionId", binding.sessionId)

    @Test fun multilineSubmissionIsAcknowledgedAndRepeatedIdentityExecutesOnlyOnce() = withStage { stage, calls ->
        pageReady(stage)
        val command = "Ein langer Auftrag\n" + "Text ".repeat(700) + "\tEnde"
        val request = request(command)
        assertTrue(stage.webViewClient.shouldOverrideUrlLoading(stage, request))
        assertEquals(command, calls.single().text)
        val first = payload(stage, "commandResult")
        assertTrue(first.getBoolean("accepted"))
        assertEquals(session, first.getString("sessionId"))
        assertTrue(stage.webViewClient.shouldOverrideUrlLoading(stage, request))
        assertEquals(1, calls.size)
        assertEquals(first.toString(), payload(stage, "commandResult").toString())
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Anderer Inhalt"))
        assertFalse(payload(stage, "commandResult").getBoolean("accepted"))
        assertEquals(1, calls.size)
    }

    @Test fun oversizedMalformedAndSubframeSubmissionsNeverReachTheNativeHandler() = withStage { stage, calls ->
        pageReady(stage)
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("a".repeat(11_501)))
        assertEquals("too-long", payload(stage, "commandResult").getString("reason"))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text\u0000Ende", id = "control"))
        assertEquals("invalid-input", payload(stage, "commandResult").getString("reason"))
        val previous = shadowOf(stage).lastEvaluatedJavascript
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", id = "session", sessionId = "-".repeat(36)))
        assertEquals("Malformed scope must not issue an acknowledgement to another page", previous,
            shadowOf(stage).lastEvaluatedJavascript)
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", id = "frame", mainFrame = false))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", kind = "arbitrary-tool"))
        assertTrue(calls.isEmpty())
    }

    @Test fun transcriptQueuedBeforeReadinessAndLongLiveResponseKeepTheirCompleteText() = withStage { stage, _ ->
        val text = "Antwort\n" + "x".repeat(13_000) + " vollständiges Ende"
        stage.presentTranscript(scope, JSONArray().put(JSONObject().put("user", false).put("text", text)))
        pageReady(stage)
        val transcript = payload(stage, "transcript")
        assertFalse(transcript.getBoolean("append"))
        assertEquals(text, transcript.getJSONArray("entries").getJSONObject(0).getString("text"))
        stage.presentMessage(scope, false, text)
        val message = payload(stage, "message")
        assertEquals(session, message.getString("sessionId"))
        assertEquals(text, message.getString("text"))
    }

    @Test fun aPendingAcknowledgementIsDeliveredWhenTheLocalPageBecomesReady() = withStage { stage, calls ->
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Mehrzeilig\nzweite Zeile"))
        assertEquals(1, calls.size)
        pageReady(stage)
        val result = payload(stage, "commandResult")
        assertEquals("request-1", result.getString("id"))
        assertTrue(result.getBoolean("accepted"))
    }

    @Test fun staleViewMessagesDocumentsSourcesAndCommandsCannotReenterAfterReturningToTheSameSession() = withStage { stage, calls ->
        pageReady(stage)
        val old = scope
        val next = old.copy(viewId = "33333333-3333-4333-8333-333333333333", viewRevision = 2)
        stage.present(state(next))
        val currentScript = shadowOf(stage).lastEvaluatedJavascript
        assertFalse(stage.bind(old))
        stage.present(state(old))
        stage.presentMessage(old, false, "Alte Antwort")
        stage.presentTranscript(old, JSONArray().put(JSONObject().put("text", "Alter Verlauf")))
        stage.presentDocument(state(old).put("name", "Alt.pdf"))
        stage.presentSources(state(old).put("sourceSetId", "44444444-4444-4444-8444-444444444444"))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Alter Auftrag", binding = old))
        assertEquals(currentScript, shadowOf(stage).lastEvaluatedJavascript)
        assertTrue(calls.isEmpty())
        scope = next
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Aktueller Auftrag"))
        assertEquals("Aktueller Auftrag", calls.single().text)
        assertEquals(next.viewId, payload(stage, "commandResult").getString("viewId"))
    }

    @Test fun pageReloadRevokesOldActionsAndQueuedPresentationBeforeTheNewPageIsBound() = withStage { stage, calls ->
        val old = scope
        stage.present(state(old))
        stage.presentMessage(old, false, "Nicht nachladen")
        stage.showSection("chat", keyboard = true)
        pageReady(stage)
        stage.webViewClient.onPageStarted(stage, ContinuumCommand03OriginalStage.INDEX, null)
        assertNotEquals(old.pageId, stage.pageId)
        assertNull(stage.binding)
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Alter Auftrag", binding = old))
        assertTrue(calls.isEmpty())
        scope = old.copy(pageId = stage.pageId)
        stage.present(state(scope))
        pageReady(stage)
        assertEquals(scope.pageId, payload(stage, "receive").getString("pageId"))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Nachladen ist gebunden"))
        assertEquals(1, calls.size)
    }


    @Test fun sameViewBindingContradictionsCannotDisableNativeCommands() = withStage { stage, calls ->
        pageReady(stage)
        val old = scope
        val otherSession = "44444444-4444-4444-8444-444444444444"
        assertFalse(stage.bind(old.copy(viewRevision = 2)))
        assertFalse(stage.bind(old.copy(sessionId = otherSession)))
        assertFalse(stage.bind(old.copy(viewRevision = 2, sessionId = otherSession)))
        assertFalse(stage.bind(old.copy(viewId = "33333333-3333-4333-8333-333333333333")))
        assertEquals(old, stage.binding)
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Alter Chat bleibt bedienbar"))
        assertEquals("Alter Chat bleibt bedienbar", calls.single().text)
        assertTrue(payload(stage, "commandResult").getBoolean("accepted"))
        val next = old.copy(viewId = "33333333-3333-4333-8333-333333333333",
            viewRevision = 2, sessionId = otherSession)
        assertTrue(stage.bind(next))
        assertEquals(next, stage.binding)
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Alte Ansicht", binding = old))
        assertEquals(1, calls.size)
        scope = next
        // The helper defaults to the old session; use the new one here.
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Neuer Chat", sessionId = otherSession))
        assertEquals(2, calls.size)
        assertEquals(otherSession, payload(stage, "commandResult").getString("sessionId"))
    }

    @Test fun rejectedDraftReturnsTheLocalizedStaleEditReason() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val calls = mutableListOf<Command03Command>()
        val stage = ContinuumCommand03OriginalStage(controller.get(),
            onNativeAction = { _, _ -> },
            onNativeCommand = { command, _ -> calls.add(command) },
            onNativeDraft = { false })
        try {
            scope = Command03Binding(stage.pageId, "22222222-2222-4222-8222-222222222222", 1L, session)
            assertTrue(stage.bind(scope))
            pageReady(stage)
            stage.webViewClient.shouldOverrideUrlLoading(stage, request("Neue Frage"))
            assertEquals("stale-edit", payload(stage, "commandResult").getString("reason"))
            assertTrue(calls.isEmpty())
        } finally {stage.destroy(); controller.pause().stop().destroy()}
    }

    @Test fun rejectedDraftCannotTriggerANativeRouteButScopedStopStaysAvailable() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val routes = mutableListOf<String>()
        val stage = ContinuumCommand03OriginalStage(controller.get(), { route, _ -> routes += route }, { _, _ -> }, { false })
        try {
            scope = Command03Binding(stage.pageId, "22222222-2222-4222-8222-222222222222", 1, session)
            assertTrue(stage.bind(scope))
            stage.webViewClient.shouldOverrideUrlLoading(stage, request("voice-input", kind = "route"))
            stage.webViewClient.shouldOverrideUrlLoading(stage, request("settings", kind = "route"))
            assertTrue(routes.isEmpty())
            val stop = Uri.Builder().scheme("continuum-native").authority("action").appendPath("route")
                .appendQueryParameter("value", "stop").appendQueryParameter("pageId", scope.pageId)
                .appendQueryParameter("viewId", scope.viewId).appendQueryParameter("viewRevision", "1")
                .appendQueryParameter("sessionId", session).build()
            val base = request("stop", kind = "route")
            stage.webViewClient.shouldOverrideUrlLoading(stage, object : WebResourceRequest by base { override fun getUrl() = stop })
            assertEquals(listOf("stop"), routes)
            stage.webViewClient.shouldOverrideUrlLoading(stage, request("stop", kind = "route",
                binding = scope.copy(viewRevision = 0)))
            assertEquals(listOf("stop"), routes)
        } finally { stage.destroy(); controller.pause().stop().destroy() }
    }
}
