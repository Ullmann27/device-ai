package at.heinz.deviceai.avatar3d

import android.graphics.Bitmap
import android.opengl.Matrix
import android.view.Surface
import com.google.android.filament.*
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer

/** Shares engine, renderer, loader and clock. */
internal class FilamentDocumentStage(
    private val engine: Engine,
    private val renderer: Renderer,
    private val loader: AssetLoader,
    private val surface: Surface,
    bytes: ByteArray
) : AutoCloseable {
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var cameraEntity = 0
    private var asset: FilamentAsset? = null
    private var texture: Texture? = null
    private var pageMaterial: MaterialInstance? = null
    private var bitmap: Bitmap? = null
    private var pageVisible = false
    private var resultVisible: Boolean? = null
    private val matrix = FloatArray(16)
    private val pageMatrix = FloatArray(16)
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val nodes = mutableMapOf<String, Int>()

    init {
        try {
            require(bytes.size in 1..MAX_BYTES)
            scene = engine.createScene()
            cameraEntity = EntityManager.get().create()
            camera = engine.createCamera(cameraEntity)
            view = engine.createView().also {
                it.scene = scene
                it.camera = camera
                it.blendMode = View.BlendMode.TRANSLUCENT
                it.isPostProcessingEnabled = true
                it.setShadowingEnabled(false)
            }
            swapChain = engine.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)
            val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
            val loaded = requireNotNull(loader.createAsset(buffer))
            asset = loaded
            require(loaded.resourceUris.isEmpty())
            val resources = ResourceLoader(engine, true)
            try { resources.loadResources(loaded) } finally { resources.destroy() }
            for (name in NODE_NAMES) {
                val entity = loaded.getFirstEntityByName(name)
                require(entity != 0)
                nodes[name] = entity
            }
            pageMaterial = loaded.instance.materialInstances.firstOrNull { it.name == "pdf-page" }
            require(pageMaterial?.material?.hasParameter("baseColorMap") == true)
            loaded.releaseSourceData()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun render(frame: DocumentStageFrame, page: Bitmap?, bufferWidth: Int, bufferHeight: Int): Boolean {
        if (!surface.isValid || !frame.valid() || bufferWidth <= 0 || bufferHeight <= 0) return false
        if (asset == null) return false
        if (resultVisible != frame.result) {
            for (name in listOf("document-glass", "document-rim", "result-glass", "result-rim")) {
                if (name.startsWith(if (frame.result) "result" else "document")) scene!!.addEntity(nodes.getValue(name))
                else scene!!.removeEntity(nodes.getValue(name))
            }
            resultVisible = frame.result
        }
        updatePage(page)
        view!!.viewport = Viewport(0, 0, bufferWidth, bufferHeight)
        val distance = maxOf(frame.cameraDistance.toDouble(), maxOf(frame.width, frame.height).toDouble() * 2.0)
        val near = 1.0
        camera!!.setProjection(Camera.Projection.PERSPECTIVE, -frame.left / distance * near,
            (frame.viewportWidth - frame.left) / distance * near,
            -(frame.viewportHeight - frame.top) / distance * near,
            frame.top / distance * near, near, distance * 4.0)
        camera!!.lookAt(0.0, 0.0, distance, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        Matrix.setIdentityM(matrix, 0)
        Matrix.rotateM(matrix, 0, -frame.yawDegrees, 0f, 1f, 0f)
        Matrix.rotateM(matrix, 0, frame.pitchDegrees, 1f, 0f, 0f)
        Matrix.translateM(matrix, 0, frame.width * .5f, -frame.height * .5f, 0f)
        Matrix.scaleM(matrix, 0, frame.width, frame.height, minOf(frame.width, frame.height))
        val transforms = engine.transformManager
        for (name in listOf("document-glass", "document-rim", "result-glass", "result-rim"))
            transforms.setTransform(transforms.getInstance(nodes.getValue(name)), matrix)
        if (pageVisible && bitmap != null) {
            System.arraycopy(matrix, 0, pageMatrix, 0, 16)
            val aspect = bitmap!!.width.toFloat() / bitmap!!.height
            val usableWidth = frame.width * .82f
            val usableHeight = frame.height * .68f
            val pageHeight = minOf(usableHeight, usableWidth / aspect)
            val pageWidth = pageHeight * aspect
            Matrix.translateM(pageMatrix, 0, 0f, -.025f, .045f)
            Matrix.scaleM(pageMatrix, 0, pageWidth / frame.width, pageHeight / frame.height, 1f)
            transforms.setTransform(transforms.getInstance(nodes.getValue("pdf-page")), pageMatrix)
        }
        if (!renderer.beginFrame(swapChain!!, System.nanoTime())) return false
        try { renderer.render(view!!) } finally { renderer.endFrame() }
        return true
    }

    private fun updatePage(page: Bitmap?) {
        val valid = page?.takeUnless { it.isRecycled }
        if (bitmap === valid) return
        bitmap = null
        scene?.removeEntity(nodes.getValue("pdf-page"))
        pageVisible = false
        if (valid == null) return
        val maxEdge = maxOf(valid.width, valid.height)
        val scaled = if (maxEdge > 1024) Bitmap.createScaledBitmap(valid,
            maxOf(1, valid.width * 1024 / maxEdge), maxOf(1, valid.height * 1024 / maxEdge), true) else valid
        val candidate = Texture.Builder().width(scaled.width).height(scaled.height).levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
        try {
            TextureHelper.setBitmap(engine, candidate, 0, scaled)
            pageMaterial!!.setParameter("baseColorMap", candidate, sampler)
            val old = texture
            texture = candidate
            old?.let(engine::destroyTexture)
            bitmap = valid
            pageVisible = true
            scene!!.addEntity(nodes.getValue("pdf-page"))
        } catch (failure: Throwable) {
            engine.destroyTexture(candidate)
            throw failure
        }
        // Upload retains bitmap until consumed.
    }

    override fun close() {
        bitmap = null
        pageMaterial = null
        runCatching { asset?.let { scene?.removeEntities(it.entities); loader.destroyAsset(it) } }
        asset = null
        runCatching { texture?.let(engine::destroyTexture) }
        texture = null
        runCatching { view?.let(engine::destroyView) }
        view = null
        runCatching { scene?.let(engine::destroyScene) }
        scene = null
        if (cameraEntity != 0) {
            runCatching { engine.destroyCameraComponent(cameraEntity) }
            runCatching { EntityManager.get().destroy(cameraEntity) }
            cameraEntity = 0
        }
        runCatching { swapChain?.let(engine::destroySwapChain) }
        swapChain = null
        runCatching { engine.flushAndWait() }
    }

    companion object {
        const val ASSET_PATH = "cinema3d/document-workspace.glb"
        const val ASSET_SHA256 = "903811921bc8d2d8ff7f2f845b0d2272c1210e4439001b09f17011f4509cc258"
        const val MAX_BYTES = 524288
        private val NODE_NAMES = listOf("document-glass", "document-rim", "result-glass", "result-rim", "pdf-page")
    }
}
