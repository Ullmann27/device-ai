package at.heinz.deviceai.avatar3d

import android.opengl.Matrix
import android.view.Surface
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer

/** Existing avatar engine owns resources. */
internal class FilamentCockpitStage(
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
    private var asset: FilamentAsset? = null
    private var ambientLight: IndirectLight? = null
    private var cameraEntity = 0
    private var lightEntity = 0
    private var roomInstance = 0
    private var monitorInstance = 0
    private var lastFrame: CockpitStageFrame? = null
    private val transform = FloatArray(16)

    init {
        try {
            require(bytes.size in 1..MAX_BYTES)
            scene = engine.createScene()
            cameraEntity = EntityManager.get().create()
            camera = engine.createCamera(cameraEntity).also { it.setExposure(11f, 1f / 125f, 100f) }
            view = engine.createView().also {
                it.scene = scene
                it.camera = camera
                it.blendMode = View.BlendMode.TRANSLUCENT
                it.isPostProcessingEnabled = true
                it.setShadowingEnabled(false)
                it.bloomOptions = View.BloomOptions().apply {
                    enabled = true; strength = .16f; resolution = 128; levels = 3
                    quality = View.QualityLevel.LOW; threshold = true
                }
            }
            swapChain = engine.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)
            val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
            val loaded = requireNotNull(loader.createAsset(buffer))
            asset = loaded
            require(loaded.resourceUris.isEmpty())
            val resources = ResourceLoader(engine, true)
            try { resources.loadResources(loaded) } finally { resources.destroy() }
            roomInstance = engine.transformManager.getInstance(loaded.getFirstEntityByName("cockpit-room"))
            monitorInstance = engine.transformManager.getInstance(loaded.getFirstEntityByName("cockpit-monitor"))
            require(roomInstance != 0 && monitorInstance != 0)
            scene!!.addEntities(loaded.entities)
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(.62f, .80f, 1f).intensity(95_000f)
                .direction(-.32f, -.55f, -1f).castShadows(false).build(engine, lightEntity)
            scene!!.addEntity(lightEntity)
            ambientLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(.90f, .98f, 1.10f))
                .intensity(20_000f).build(engine)
            scene!!.indirectLight = ambientLight
            loaded.releaseSourceData()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun render(frame: CockpitStageFrame, bufferWidth: Int, bufferHeight: Int): Boolean {
        if (!surface.isValid || !frame.valid() || bufferWidth <= 0 || bufferHeight <= 0 || asset == null) return false
        if (frame != lastFrame) {
            val d = frame.distance.toDouble()
            camera!!.setProjection(Camera.Projection.PERSPECTIVE,
                -frame.width * .5 / d, frame.width * .5 / d,
                -frame.height * .5 / d, frame.height * .5 / d, 1.0, d * 4.0)
            camera!!.lookAt(0.0, 0.0, d, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            Matrix.setIdentityM(transform, 0)
            Matrix.scaleM(transform, 0, frame.width, frame.height, minOf(frame.width, frame.height))
            engine.transformManager.setTransform(roomInstance, transform)
            Matrix.setIdentityM(transform, 0)
            Matrix.translateM(transform, 0,
                (frame.monitorLeft + frame.monitorRight - frame.width) * .5f,
                (frame.height - frame.monitorTop - frame.monitorBottom) * .5f, frame.depthOffset)
            Matrix.rotateM(transform, 0, frame.yawDegrees, 0f, 1f, 0f)
            Matrix.rotateM(transform, 0, frame.pitchDegrees, 1f, 0f, 0f)
            Matrix.scaleM(transform, 0, frame.monitorWidth, frame.monitorHeight,
                minOf(frame.monitorWidth, frame.monitorHeight))
            engine.transformManager.setTransform(monitorInstance, transform)
            lastFrame = frame
        }
        view!!.viewport = Viewport(0, 0, bufferWidth, bufferHeight)
        if (!renderer.beginFrame(swapChain!!, System.nanoTime())) return false
        try { renderer.render(view!!) } finally { renderer.endFrame() }
        return true
    }

    override fun close() {
        runCatching { asset?.let { scene?.removeEntities(it.entities); loader.destroyAsset(it) } }
        asset = null
        if (lightEntity != 0) {
            runCatching { scene?.removeEntity(lightEntity); engine.destroyEntity(lightEntity) }
            runCatching { EntityManager.get().destroy(lightEntity) }
            lightEntity = 0
        }
        runCatching { scene?.indirectLight = null }
        runCatching { ambientLight?.let(engine::destroyIndirectLight) }; ambientLight = null
        runCatching { view?.let(engine::destroyView) }; view = null
        runCatching { scene?.let(engine::destroyScene) }; scene = null
        if (cameraEntity != 0) {
            runCatching { engine.destroyCameraComponent(cameraEntity) }
            runCatching { EntityManager.get().destroy(cameraEntity) }
            cameraEntity = 0
        }
        runCatching { swapChain?.let(engine::destroySwapChain) }; swapChain = null
        runCatching { engine.flushAndWait() }
    }

    companion object {
        const val ASSET_PATH = "cinema3d/cockpit-workstation.glb"
        const val ASSET_SHA256 = "8dfe8e9ae86969c53f5a4c60474cae2cb72f23c517bca6a9134edd8d4c98db9c"
        const val MAX_BYTES = 32_768
    }
}
