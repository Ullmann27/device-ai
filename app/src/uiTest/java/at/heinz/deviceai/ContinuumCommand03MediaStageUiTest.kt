package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveFrameOrigin
import at.heinz.deviceai.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowWebView
import java.io.ByteArrayOutputStream

/** Tests the actual Stage boundary with controlled provider callbacks; no Android GPU/pixel claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ContinuumCommand03MediaStageUiTest.VisualWebView::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumCommand03MediaStageUiTest {
    @Implements(WebView::class)
    class VisualWebView : ShadowWebView() {
        val callbacks = ArrayDeque<Pair<Long, WebView.VisualStateCallback>>()
        @Implementation(minSdk = 23)
        protected fun postVisualStateCallback(id: Long, callback: WebView.VisualStateCallback) {
            callbacks.addLast(id to callback)
        }
        fun visualReady() { val (id, callback) = callbacks.removeFirst(); callback.onComplete(id) }
    }

    private val session = "11111111-1111-4111-8111-111111111111"
    private val binding = LiveFrameBinding("computer-synthetic-wire", 7, "com.example.target", "at.heinz.deviceai",
        LiveFrameOrigin.ACCESSIBILITY_WINDOW)
    private fun packet(generation: Long = 1, sequence: Long = 1): Command03ComputerPacket {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)) } finally { bitmap.recycle() }
        return Command03ComputerPacket(session, generation, sequence, true, "LIVE", "Live", binding, 0, 0, 1_500,
            Command03EncodedFrame(2, 2, output.toByteArray()))
    }
    private fun request(ack: Command03FrameAck, main: Boolean = true, kind: String = "frame-presented") =
        object : WebResourceRequest {
            override fun getUrl() = Uri.Builder().scheme("continuum-native").authority("action").appendPath(kind)
                .appendQueryParameter("sessionId", ack.sessionId).appendQueryParameter("generation", ack.generation.toString())
                .appendQueryParameter("sequence", ack.sequence.toString()).build()
            override fun isForMainFrame() = main
            override fun isRedirect() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = emptyMap<String, String>()
        }
    private fun withStage(body: (ContinuumCommand03OriginalStage, VisualWebView, MutableList<Command03FrameAck>) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val drawn = mutableListOf<Command03FrameAck>()
        val stage = ContinuumCommand03OriginalStage(controller.get(), { _, _ -> }, { _, _ -> }, {},
            onComputerFrameDrawn = { drawn += it })
        controller.get().setContentView(stage)
        val decor = controller.get().window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(392, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 392, 852)
        stage.measure(View.MeasureSpec.makeMeasureSpec(392, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
        stage.layout(0, 0, 392, 852)
        stage.webViewClient.onPageFinished(stage, ContinuumCommand03OriginalStage.INDEX)
        stage.setLivePresentationActive(true)
        try { body(stage, shadowOf(stage) as VisualWebView, drawn) }
        finally { stage.destroy(); controller.pause().stop().destroy() }
    }
    private fun acknowledge(stage: ContinuumCommand03OriginalStage, ack: Command03FrameAck, main: Boolean = true) =
        stage.webViewClient.shouldOverrideUrlLoading(stage, request(ack, main))
    private fun assertReadyForComputerAck(stage: ContinuumCommand03OriginalStage, ack: Command03FrameAck) {
        fun field(name: String): Any? = ContinuumCommand03OriginalStage::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.get(stage)
        val rect = Rect()
        val globallyVisible = stage.getGlobalVisibleRect(rect)
        val state = "attached=${stage.isAttachedToWindow}, shown=${stage.isShown}, window=${stage.windowVisibility}, " +
            "size=${stage.width}x${stage.height}, globalVisible=$globallyVisible, rect=$rect, " +
            "active=${field("livePresentationActive")}, pageReady=${field("pageReady")}, disposed=${field("disposed")}, " +
            "ack=$ack, frame=${field("computerFrame")}, " +
            "remainingNs=${(field("computerFrameDeadline") as Long) - System.nanoTime()}"
        val ready = ContinuumCommand03OriginalStage::class.java.getDeclaredMethod("canDrawComputer", Command03FrameAck::class.java)
            .apply { isAccessible = true }.invoke(stage, ack) as Boolean
        assertTrue("The positive host must satisfy the actual draw gate before the ACK: $state", ready)
    }
    private fun draw(stage: ContinuumCommand03OriginalStage, hardwareFlag: Boolean) {
        // Only the flag is controlled. This checks policy ordering, not hardware renderer output.
        val canvas = object : Canvas() { override fun isHardwareAccelerated() = hardwareFlag }
        ContinuumCommand03OriginalStage::class.java.getDeclaredMethod("onDraw", Canvas::class.java)
            .apply { isAccessible = true }.invoke(stage, canvas)
    }

    @Test fun actualSerializedComputerPayloadUsesTheStrictBrowserWireTypes() = withStage { stage, _, _ ->
        stage.presentComputer(packet())
        val script = shadowOf(stage).lastEvaluatedJavascript
        val json = JSONObject(script.removePrefix("window.ContinuumNative?.computer(").removeSuffix(");"))
        assertEquals("LIVE", json.getString("status"))
        assertTrue(json.get("captureSession") is String)
        assertEquals("7", json.getString("captureSession"))
        assertEquals(0L, json.getLong("ageMs"))
        assertEquals(1_500L, json.getLong("staleAfterMs"))
        assertEquals(2, json.getInt("width"))
        assertTrue(json.getString("frameDataUri").startsWith("data:image/jpeg;base64,/9j/"))
        // Synthetic 2x2 image only. The DOM suite can consume this actual native serialization verbatim.
        println("COMMAND03_COMPUTER_WIRE:$json")
    }

    @Test fun decodeAckAndVisualCallbackNeedASubsequentHardwareDraw() = withStage { stage, shadow, drawn ->
        val packet = packet()
        stage.presentComputer(packet)
        assertReadyForComputerAck(stage, packet.acknowledgement)
        acknowledge(stage, packet.acknowledgement)
        assertTrue(drawn.isEmpty())
        assertEquals(1, shadow.callbacks.size)
        draw(stage, true)
        assertTrue("A draw before the provider callback does not prove this DOM update", drawn.isEmpty())
        shadow.visualReady()
        assertTrue(drawn.isEmpty())
        draw(stage, false)
        assertTrue("A software snapshot cannot authorize external computer work", drawn.isEmpty())
        draw(stage, true)
        assertEquals(listOf(packet.acknowledgement), drawn)
        acknowledge(stage, packet.acknowledgement)
        assertTrue(shadow.callbacks.isEmpty())
    }

    @Test fun oldSessionGenerationAndSubframeAcknowledgementsCannotArmADraw() = withStage { stage, shadow, drawn ->
        val packet = packet()
        stage.presentComputer(packet)
        assertReadyForComputerAck(stage, packet.acknowledgement)
        acknowledge(stage, packet.acknowledgement.copy(sessionId = "22222222-2222-4222-8222-222222222222"))
        acknowledge(stage, packet.acknowledgement.copy(generation = 2))
        acknowledge(stage, packet.acknowledgement, main = false)
        assertTrue(shadow.callbacks.isEmpty())
        draw(stage, true)
        assertTrue(drawn.isEmpty())
    }

    @Test fun pdfPauseAndReparentFencesRejectLateProviderCallbacks() {
        for (boundary in listOf("pdf", "pause", "reparent")) withStage { stage, shadow, drawn ->
            val packet = packet()
            stage.presentComputer(packet)
            assertReadyForComputerAck(stage, packet.acknowledgement)
            acknowledge(stage, packet.acknowledgement)
            assertEquals("The $boundary fixture must first enqueue a real provider callback", 1, shadow.callbacks.size)
            when (boundary) {
                "pdf" -> stage.presentDocument(JSONObject().put("sessionId", session).put("name", "Synthetisch.pdf"))
                "pause" -> stage.setLivePresentationActive(false)
                else -> {
                    val parent = stage.parent as android.view.ViewGroup
                    parent.removeView(stage)
                    parent.addView(stage)
                }
            }
            shadow.visualReady()
            draw(stage, true)
            assertTrue("Late callback after $boundary must not submit the previous image", drawn.isEmpty())
        }
    }
}
