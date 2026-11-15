package at.heinz.deviceai

import android.app.Activity
import android.net.Uri
import android.webkit.WebResourceRequest
import at.heinz.deviceai.ui.Command03Command
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

    private fun request(value: String, id: String = "request-1", mainFrame: Boolean = true,
        kind: String = "command", sessionId: String = session): WebResourceRequest {
        val uri = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
            .appendQueryParameter("value", value).appendQueryParameter("id", id)
            .appendQueryParameter("sessionId", sessionId).build()
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
            onNativeDraft = {})
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
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", id = "session", sessionId = "-".repeat(36)))
        assertFalse(payload(stage, "commandResult").getBoolean("accepted"))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", id = "frame", mainFrame = false))
        stage.webViewClient.shouldOverrideUrlLoading(stage, request("Text", kind = "arbitrary-tool"))
        assertTrue(calls.isEmpty())
    }

    @Test fun transcriptQueuedBeforeReadinessAndLongLiveResponseKeepTheirCompleteText() = withStage { stage, _ ->
        val text = "Antwort\n" + "x".repeat(13_000) + " vollständiges Ende"
        stage.presentTranscript(session, JSONArray().put(JSONObject().put("user", false).put("text", text)))
        pageReady(stage)
        val transcript = payload(stage, "transcript")
        assertFalse(transcript.getBoolean("append"))
        assertEquals(text, transcript.getJSONArray("entries").getJSONObject(0).getString("text"))
        stage.presentMessage(session, false, text)
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
}
