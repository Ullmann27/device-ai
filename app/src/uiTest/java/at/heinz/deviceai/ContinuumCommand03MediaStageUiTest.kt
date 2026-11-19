package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import at.heinz.deviceai.cognitive.LiveFrameBinding
import at.heinz.deviceai.cognitive.LiveFrameOrigin
import at.heinz.deviceai.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Tests the actual Stage boundary with controlled provider callbacks; no Android GPU/pixel claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w392dp-h852dp-port-mdpi",
    shadows = [ContinuumCommand03MediaStageUiTest.VisualWebView::class])
// Robolectric 4.14.1 WindowSessionDelegate grants APP_VISIBLE by default only for native graphics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumCommand03MediaStageUiTest {
    @Implements(WebView::class)
    class VisualWebView : Command03HostWebView() {
        val callbacks = ArrayDeque<Pair<Long, WebView.VisualStateCallback>>()
        @Implementation(minSdk = 23)
        protected fun postVisualStateCallback(id: Long, callback: WebView.VisualStateCallback) {
            callbacks.addLast(id to callback)
        }
        fun visualReady() { val (id, callback) = callbacks.removeFirst(); callback.onComplete(id) }
    }

    @Before fun prepareHostFrames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private val session = "11111111-1111-4111-8111-111111111111"
    private lateinit var scope: Command03Binding
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
                .appendQueryParameter("pageId", scope.pageId).appendQueryParameter("viewId", scope.viewId)
                .appendQueryParameter("viewRevision", scope.viewRevision.toString())
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
        val stage = ContinuumCommand03OriginalStage(controller.get(), { _, _ -> }, { _, _ -> }, { true },
            onComputerFrameDrawn = { drawn += it })
        scope = Command03Binding(stage.pageId, "22222222-2222-4222-8222-222222222222", 1L, session)
        assertTrue(stage.bind(scope))
        controller.get().setContentView(stage)
        val decor = controller.get().window.decorView
        val pump = CockpitHostFramePump { decor }
        controller.get().window.setLayout(392, 852)
        repeat(3) {
            decor.measure(View.MeasureSpec.makeMeasureSpec(392, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 392, 852)
            shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
            pump.drawRequestedFrame()
        }
        controller.windowFocusChanged(true)
        stage.webViewClient.onPageFinished(stage, ContinuumCommand03OriginalStage.INDEX)
        stage.setLivePresentationActive(true)
        try { body(stage, shadowOf(stage) as VisualWebView, drawn) }
        finally { pump.close(); stage.destroy(); controller.pause().stop().destroy() }
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
        assertEquals(scope.pageId, json.getString("pageId"))
        assertEquals(scope.viewId, json.getString("viewId"))
        assertEquals(scope.viewRevision, json.getLong("viewRevision"))
        assertTrue(json.get("captureSession") is String)
        assertEquals("7", json.getString("captureSession"))
        assertEquals(0L, json.getLong("ageMs"))
        assertEquals(1_500L, json.getLong("staleAfterMs"))
        assertEquals(2, json.getInt("width"))
        assertTrue(json.getString("frameDataUri").startsWith("data:image/jpeg;base64,/9j/"))
        // Synthetic 2x2 image only. The DOM suite can consume this actual native serialization verbatim.
        println("COMMAND03_COMPUTER_WIRE:$json")
    }

    @Test fun theMonitorPayloadNamesTheBoundTargetAndCarriesFrameGeometryOnlyWithARealFrame() = withStage { stage, _, _ ->
        fun sent() = JSONObject(shadowOf(stage).lastEvaluatedJavascript
            .removePrefix("window.ContinuumNative?.computer(").removeSuffix(");"))
        stage.presentComputer(packet().copy(sourceSequence = 41, observationRevision = 6,
            viewport = at.heinz.deviceai.cognitive.LiveTargetBounds(.25f, 0f, 1f, .5f)))
        val live = sent()
        assertEquals("com.example.target", live.getString("targetPackage"))
        assertEquals(41L, live.getLong("sourceSequence"))
        assertEquals(6L, live.getLong("observationRevision"))
        val viewport = live.getJSONArray("viewport")
        assertEquals(listOf(.25, 0.0, 1.0, .5), (0 until viewport.length()).map { viewport.getDouble(it) })
        // A frame without measured geometry sends none; nothing is assumed to be the full window.
        stage.presentComputer(packet(sequence = 2))
        val plain = sent()
        assertTrue(plain.has("frameDataUri"))
        assertFalse(plain.has("viewport") || plain.has("sourceSequence") || plain.has("observationRevision"))
        // A status without an image still names the target, and no geometry at all.
        stage.presentComputer(Command03ComputerPacket(session, 1, 3, true, "PAUSED", "Pausiert", binding, 0,
            sourceSequence = 42, viewport = at.heinz.deviceai.cognitive.LiveTargetBounds(0f, 0f, 1f, 1f)))
        val paused = sent()
        assertEquals("PAUSED", paused.getString("status"))
        assertEquals("com.example.target", paused.getString("targetPackage"))
        assertFalse(paused.has("frameDataUri") || paused.has("viewport") || paused.has("sourceSequence"))
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
                "pdf" -> stage.presentDocument(JSONObject().put("sessionId", session).put("name", "Synthetisch.pdf")
                    .put("pageId", scope.pageId).put("viewId", scope.viewId).put("viewRevision", scope.viewRevision))
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
