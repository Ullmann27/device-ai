package at.heinz.deviceai.avatar3d

import android.graphics.RectF
import android.opengl.Matrix
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.LiveMonitorFeed
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer

/** Assets inside the existing scene. */
internal class FilamentCockpitStage(
    private val engine: Engine,
    private val scene: Scene,
    private val loader: AssetLoader,
    assets: CockpitSceneAssets
) : AutoCloseable {
    private var workstation: FilamentAsset? = null
    private var orb: FilamentAsset? = null
    private var monitorMaterial: Material? = null
    private var ambientLight: IndirectLight? = null
    private var monitorInstance: MaterialInstance? = null
    private var pixels: CockpitLiveTexture? = null
    private var roomTransform = 0
    private var monitorTransform = 0
    private var orbTransform = 0
    private val orbitTransforms = mutableListOf<Pair<Int, FloatArray>>()
    private val transform = FloatArray(16)
    private val orbitRotation = FloatArray(16)
    private val orbitCombined = FloatArray(16)
    val pixelsVisible get() = pixels?.pixelsVisible == true
    var label = "Keine Bildquelle verbunden"
        private set

    init {
        try {
            workstation = load(assets.workstation)
            orb = load(assets.orb)
            roomTransform = namedTransform(requireNotNull(workstation), "cockpit-room")
            monitorTransform = namedTransform(requireNotNull(workstation), "cockpit-monitor")
            orbTransform = namedTransform(requireNotNull(orb), "continuum-orb")
            repeat(5) { index ->
                val instance = namedTransform(requireNotNull(orb), "orb-orbit-$index")
                orbitTransforms += instance to engine.transformManager.getTransform(instance, FloatArray(16))
            }
            val entity = requireNotNull(workstation).getFirstEntityByName("monitor-live-pixels")
            val renderable = engine.renderableManager.getInstance(entity)
            require(renderable != 0 && engine.renderableManager.getPrimitiveCount(renderable) == 1)
            val payload = ByteBuffer.allocateDirect(assets.monitorMaterial.size).put(assets.monitorMaterial).apply { flip() }
            monitorMaterial = Material.Builder().payload(payload, assets.monitorMaterial.size).build(engine)
            monitorInstance = requireNotNull(monitorMaterial).createInstance()
            engine.renderableManager.setMaterialInstanceAt(renderable, 0, requireNotNull(monitorInstance))
            pixels = CockpitLiveTexture(engine, requireNotNull(monitorInstance))
            ambientLight = IndirectLight.Builder().irradiance(1, floatArrayOf(.90f, .98f, 1.10f))
                .intensity(20_000f).build(engine)
            scene.indirectLight = ambientLight
        } catch (failure: Throwable) { close(); throw failure }
    }

    private fun load(bytes: ByteArray): FilamentAsset {
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        val loaded = requireNotNull(loader.createAsset(buffer))
        try {
            require(loaded.resourceUris.isEmpty())
            val resources = ResourceLoader(engine, true)
            try { resources.loadResources(loaded) } finally { resources.destroy() }
            scene.addEntities(loaded.entities)
            loaded.releaseSourceData()
            return loaded
        } catch (failure: Throwable) { loader.destroyAsset(loaded); throw failure }
    }

    private fun namedTransform(asset: FilamentAsset, name: String): Int =
        engine.transformManager.getInstance(asset.getFirstEntityByName(name)).also { require(it != 0) { name } }

    fun bind(feed: LiveMonitorFeed<AndroidLiveFrame>?) { pixels?.bind(feed) }
    fun workEvent(event: CockpitWorkEvent) { pixels?.workEvent(event) }

    fun update(frame: CockpitStageFrame, orbBounds: RectF, phase: Float, motion: Boolean) {
        require(frame.valid())
        val unit = frame.worldScale
        Matrix.setIdentityM(transform, 0)
        Matrix.scaleM(transform, 0, frame.width * unit, frame.height * unit, 1f)
        engine.transformManager.setTransform(roomTransform, transform)
        engine.transformManager.setTransform(monitorTransform, frame.monitorTransform())
        engine.transformManager.setTransform(orbTransform, frame.orbTransform(
            orbBounds.left, orbBounds.top, orbBounds.right, orbBounds.bottom))
        orbitTransforms.forEachIndexed { index, (instance, rest) ->
            Matrix.setIdentityM(orbitRotation, 0)
            val pose = CockpitOrbMotion.sample(index, if (motion) phase else .4f)
            Matrix.rotateM(orbitRotation, 0, -pose.pitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(orbitRotation, 0, pose.yawDegrees, 0f, 1f, 0f)
            Matrix.multiplyMM(orbitCombined, 0, rest, 0, orbitRotation, 0)
            engine.transformManager.setTransform(instance, orbitCombined)
        }
        pixels?.update(frame, System.nanoTime())
        label = pixels?.label ?: "3D-Bildquelle nicht verfügbar"
    }

    override fun close() {
        runCatching { pixels?.close() }; pixels = null
        for (loaded in listOfNotNull(workstation, orb)) {
            runCatching { scene.removeEntities(loaded.entities); loader.destroyAsset(loaded) }
        }
        workstation = null; orb = null
        runCatching { monitorInstance?.let(engine::destroyMaterialInstance) }; monitorInstance = null
        runCatching { monitorMaterial?.let(engine::destroyMaterial) }; monitorMaterial = null
        runCatching { scene.indirectLight = null }
        runCatching { ambientLight?.let(engine::destroyIndirectLight) }; ambientLight = null
        orbitTransforms.clear()
    }

    companion object {
        const val ASSET_PATH = "cinema3d/cockpit-workstation.glb"
        const val MAX_BYTES = 2_000_000
    }
}
