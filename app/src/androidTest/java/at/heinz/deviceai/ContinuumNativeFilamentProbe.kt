package at.heinz.deviceai

import android.app.Instrumentation
import android.content.res.AssetManager
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import at.heinz.deviceai.avatar3d.AvatarFrameReceipt
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.CockpitPanelContent
import at.heinz.deviceai.avatar3d.CockpitPanelUploadState
import at.heinz.deviceai.avatar3d.CockpitSceneAssets
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.avatar3d.FilamentAvatarRenderer
import at.heinz.deviceai.avatar3d.FilamentDocumentStage
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.CockpitWorkReducer
import at.heinz.deviceai.cognitive.ComputerApp
import at.heinz.deviceai.cognitive.ComputerGoal
import at.heinz.deviceai.cognitive.OwnerComputerIntent
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.ui.AndroidContinuumPainter
import at.heinz.deviceai.ui.CinematicWorkstationLayout
import at.heinz.deviceai.ui.ComputerPanelState
import at.heinz.deviceai.ui.ContinuumDashboardState
import at.heinz.deviceai.ui.JarvisShellLayout
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Native scene; synthetic context only. */
internal object ContinuumNativeFilamentProbe {
    fun run(instrumentation: Instrumentation): Bundle {
        val result = Bundle().apply {
            putInt("sdk", Build.VERSION.SDK_INT)
            putString("evidence_type", "Native Filament panel uploads/submission; synthetic context; offscreen surface")
            putString("context", "SYNTHETIC FILAMENT TEST; no capture, action or inference")
        }
        var renderer: FilamentAvatarRenderer? = null
        var output: ImageReader? = null
        var failure: Throwable? = null
        try {
            check(Build.VERSION.SDK_INT == 35) { "FILAMENT_PROBE_REQUIRES_API_35" }
            check(Build.SUPPORTED_ABIS.contains("x86_64")) { "FILAMENT_PROBE_REQUIRES_X86_64" }
            val assets = instrumentation.targetContext.assets
            val avatar = pinned(assets, AvatarGlbContract.ASSET_PATH,
                AvatarGlbContract.MAX_BYTES, AvatarGlbContract.ASSET_SHA256)
            val metadata = AvatarGlbContract.validate(avatar)
            val cockpit = CockpitSceneAssets.load(assets)
            val document = pinned(assets, FilamentDocumentStage.ASSET_PATH,
                FilamentDocumentStage.MAX_BYTES, FilamentDocumentStage.ASSET_SHA256)
            val shell = CinematicWorkstationLayout.create(884f, 760f)
            val stageBox = requireNotNull(shell[JarvisShellLayout.Slot.AVATAR])
            val monitor = requireNotNull(shell[JarvisShellLayout.Slot.WORK_PROCESS])
            val frame = CockpitStageFrame(stageBox.width.toInt().toFloat(), stageBox.height.toInt().toFloat(),
                monitor.left - stageBox.left, monitor.top - stageBox.top,
                minOf(stageBox.width.toInt().toFloat(), monitor.right - stageBox.left),
                minOf(stageBox.height.toInt().toFloat(), monitor.bottom - stageBox.top))
            fun local(box: JarvisShellLayout.Box) = RectF(box.left - stageBox.left, box.top - stageBox.top,
                box.right - stageBox.left, box.bottom - stageBox.top)
            val head = local(requireNotNull(shell.headBounds))
            val orb = local(requireNotNull(shell.orbBounds))
            val (width, height) = frame.bufferSize()
            val uploads = AtomicInteger()
            val outputFrames = AtomicInteger()
            val outputFailure = AtomicReference<Throwable?>()
            val content = syntheticContent { uploads.incrementAndGet() }
            val pose = AvatarPose(AvatarState.WAITING_FOR_APPROVAL, AvatarExpression.ATTENTIVE,
                0f, 0f, 0f, Viseme.SIL, 0f, 0f)
            lateinit var engine: Engine
            lateinit var scene: Scene
            lateinit var panels: Any
            lateinit var uploadState: CockpitPanelUploadState
            lateinit var pages: IntArray
            var baselineEntities = 0
            var renderedFrames = 0
            var presentedFrames = 0

            fun draw(): Boolean {
                val actual = requireNotNull(renderer)
                val receipt = AvatarFrameReceipt()
                val rendered = actual.render(pose, renderedFrames / 60f, false, width, height, receipt = receipt)
                if (rendered) {
                    val required = AvatarFrameReceipt.FILAMENT_BEGIN or AvatarFrameReceipt.FILAMENT_RENDERED
                    check((receipt.stageFlags and required) == required) { "NO_NATIVE_RENDER_RECEIPT" }
                    renderedFrames++
                    if (actual.cockpitPanelsPresented(content, frame)) presentedFrames++
                }
                engine.flushAndWait()
                outputFailure.get()?.let { throw it }
                return rendered
            }

            instrumentation.onMain {
                output = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also { reader ->
                    reader.setOnImageAvailableListener({ available ->
                        try {
                            available.acquireLatestImage()?.use { image ->
                                check(image.width == width && image.height == height) { "OUTPUT_SIZE_MISMATCH" }
                                check(image.planes.isNotEmpty() && image.planes[0].buffer.hasRemaining()) {
                                    "EMPTY_NATIVE_OUTPUT"
                                }
                                outputFrames.incrementAndGet()
                            }
                        } catch (error: Throwable) { outputFailure.compareAndSet(null, error) }
                    }, Handler(Looper.getMainLooper()))
                }
                val actual = FilamentAvatarRenderer.create(requireNotNull(output).surface, avatar, metadata)
                renderer = actual
                engine = field(actual, "engine") as Engine
                scene = field(actual, "scene") as Scene
                check(engine.isValid) { "FILAMENT_STARTUP_FAILED" }
                baselineEntities = scene.entityCount
                actual.configureCockpit(cockpit, frame, head, orb, feed = null,
                    panelBytes = document, panelPainter = AndroidContinuumPainter(assets), panelContent = content)
                check(actual.cockpitReady) { "COCKPIT_STARTUP_FAILED" }
                check(!actual.cockpitPanelsPresented(content, frame)) { "PRESENTED_BEFORE_UPLOAD" }
                draw()
                val stage = requireNotNull(field(actual, "cockpitStage"))
                check(field(stage, "contextPanelsFailed") == false) { "NATIVE_PANEL_STARTUP_FAILED" }
                panels = checkNotNull(field(stage, "contextPanels")) { "NATIVE_PANELS_MISSING" }
                uploadState = field(panels, "state") as CockpitPanelUploadState
                pages = (field(panels, "pages") as IntArray).copyOf()
                check(pages.size == 2 && pages.all { it != 0 } && pages.distinct().size == 2) { "TWO_QUADS_REQUIRED" }
                check(!actual.cockpitPanelsPresented(content, frame) && pages.none(scene::hasEntity)) {
                    "PRESENTED_BEFORE_UPLOAD_CALLBACKS"
                }
            }
            await("UPLOAD_CALLBACKS_TIMEOUT") {
                instrumentation.onMain {
                    engine.flushAndWait()
                    outputFailure.get()?.let { throw it }
                    uploadState.drawable
                }
            }
            instrumentation.onMain {
                check(uploads.get() == 1 && uploadState.inFlight == null) { "INCOMPLETE_PANEL_UPLOADS" }
                check(!requireNotNull(renderer).cockpitPanelsPresented(content, frame)) { "UPLOAD_IS_NOT_SUBMISSION" }
                check(pages.none(scene::hasEntity)) { "QUADS_VISIBLE_BEFORE_SHOW" }
            }
            await("NATIVE_PRESENTATION_TIMEOUT") {
                instrumentation.onMain { draw() && requireNotNull(renderer).cockpitPanelsPresented(content, frame) }
            }
            await("NATIVE_OUTPUT_TIMEOUT") {
                outputFailure.get()?.let { throw it }
                outputFrames.get() > 0
            }
            instrumentation.onMain {
                val actual = requireNotNull(renderer)
                val materials = (field(panels, "materials") as Array<*>).map { it as MaterialInstance }
                val textures = (field(panels, "textures") as Array<*>).map { it as Texture }
                check(materials.size == 2 && materials.map { it.nativeObject }.distinct().size == 2) {
                    "DISTINCT_MATERIALS_REQUIRED"
                }
                check(textures.size == 2 && textures.map { it.nativeObject }.distinct().size == 2) {
                    "DISTINCT_TEXTURES_REQUIRED"
                }
                val requested = checkNotNull(uploadState.requested)
                for (index in pages.indices) {
                    val instance = engine.renderableManager.getInstance(pages[index])
                    check(scene.hasEntity(pages[index]) && instance != 0 &&
                        engine.renderableManager.getPrimitiveCount(instance) == 1) { "QUAD_NOT_IN_NATIVE_SCENE" }
                    check(materials[index].name == if (index == 0) "cockpit-plan" else "cockpit-result") {
                        "WRONG_PANEL_MATERIAL"
                    }
                    check(engine.renderableManager.getMaterialInstanceAt(instance, 0).nativeObject ==
                        materials[index].nativeObject) { "PANEL_MATERIAL_NOT_BOUND" }
                    check(engine.isValidExpensiveMaterialInstance(materials[index]) && engine.isValidTexture(textures[index])) {
                        "INVALID_NATIVE_PANEL_RESOURCE"
                    }
                    check(textures[index].getWidth(0) == requested.width &&
                        textures[index].getHeight(0) == requested.height &&
                        requested.width in 1..768 && requested.height in 1..768) { "WRONG_NATIVE_TEXTURE_SIZE" }
                }
                check(presentedFrames > 0 && uploadState.presented(content, frame)) { "NO_SUBMITTED_PANEL_FRAME" }
                result.putInt("native_quads", pages.size)
                result.putInt("native_materials", materials.size)
                result.putInt("native_textures", textures.size)
                result.putInt("upload_completions", uploads.get())
                result.putInt("rendered_frames", renderedFrames)
                result.putInt("presented_frames", presentedFrames)
                result.putInt("output_frames", outputFrames.get())
                actual.configureCockpit(cockpit, frame, head, orb, feed = null, panelContent = null)
                check(!actual.cockpitPanelsPresented(content, frame)) { "CLEAR_KEPT_PRESENTATION" }
            }
            await("CLEAR_FRAME_TIMEOUT") { instrumentation.onMain { draw() } }
            instrumentation.onMain {
                val actual = requireNotNull(renderer)
                check(pages.none(scene::hasEntity) && !uploadState.drawable &&
                    !actual.cockpitPanelsPresented(content, frame)) { "CLEAR_KEPT_QUADS" }
                result.putBoolean("content_cleared", true)
                val materials = (field(panels, "materials") as Array<*>).map { it as MaterialInstance }
                val textures = (field(panels, "textures") as Array<*>).map { it as Texture }
                actual.clearCockpitStage()
                engine.flushAndWait()
                check(!actual.cockpitReady && !actual.cockpitPanelsPresented(content, frame)) { "STAGE_NOT_CLEARED" }
                check(field(panels, "closed") == true && field(panels, "asset") == null &&
                    uploadState.requested == null && uploadState.inFlight == null) { "PANELS_NOT_CLOSED" }
                check((field(panels, "materials") as Array<*>).all { it == null } &&
                    (field(panels, "textures") as Array<*>).all { it == null }) { "PANEL_REFERENCES_RETAINED" }
                check(scene.entityCount == baselineEntities &&
                    pages.all { !scene.hasEntity(it) && engine.renderableManager.getInstance(it) == 0 }) {
                    "NATIVE_SCENE_LEAK"
                }
                check(materials.all { destroyed { it.nativeObject } } &&
                    textures.all { destroyed { it.nativeObject } }) { "NATIVE_PANEL_RESOURCES_RETAINED" }
                result.putBoolean("panels_released", true)
                actual.close()
                check(!engine.isValid) { "ENGINE_NOT_RELEASED" }
                renderer = null
                result.putBoolean("engine_released", true)
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            try {
                instrumentation.onMain {
                    try { renderer?.close() } finally { output?.close() }
                }
            } catch (cleanup: Throwable) {
                if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
            }
        }
        result.putString("status", if (failure == null) "ok" else "failed")
        result.putString("stream", failure?.let { "Native Filament probe failed: " + it.stackTraceToString() + "\n" }
            ?: "Native Filament panels: two uploads, native submission, clear and release passed.\n")
        return result
    }

    private fun syntheticContent(requestFrame: () -> Unit): CockpitPanelContent {
        val taskId = "synthetic-filament-test"
        val reducer = CockpitWorkReducer().apply { bind(taskId, 1L) }
        listOf(CockpitWorkEvent.Stage.ACCEPTED, CockpitWorkEvent.Stage.ASK_OWNER).forEachIndexed { index, stage ->
            val sequence = index + 1L
            check(reducer.apply(CockpitWorkEvent(taskId, 1L, sequence, sequence, sequence, stage), sequence))
        }
        val computer = checkNotNull(ComputerPanelState.from(taskId, 1L,
            OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN), reducer.snapshot(2L),
            OwnerComputerWorkflow.Result(OwnerComputerWorkflow.Status.ASK_OWNER,
                "SYNTHETIC FILAMENT TEST. Keine echte App-Aktion oder Beobachtung.")))
        check(!computer.verified && computer.snapshot.observationRevision == 0L)
        return checkNotNull(CockpitPanelContent.from(ContinuumDashboardState(computer = computer),
            1f, 1f, requestFrame))
    }

    private fun pinned(assets: AssetManager, path: String, limit: Int, expected: String): ByteArray {
        val bytes = assets.open(path).use { it.readBytes() }
        check(bytes.size in 1..limit) { "ASSET_SIZE: $path" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == expected) { "ASSET_HASH: $path" }
        return bytes
    }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun destroyed(handle: () -> Long): Boolean =
        runCatching(handle).exceptionOrNull() is IllegalStateException

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        do {
            if (condition()) return
            SystemClock.sleep(20L)
        } while (SystemClock.elapsedRealtime() < deadline)
        error(message)
    }

    private fun <T> Instrumentation.onMain(action: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
