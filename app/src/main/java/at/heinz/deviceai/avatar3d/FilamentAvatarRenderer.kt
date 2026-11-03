package at.heinz.deviceai.avatar3d

import android.opengl.Matrix
import android.view.Surface
import at.heinz.deviceai.avatar.AvatarPose
import com.google.android.filament.*
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer

/** UI-thread owned, only allocated for an explicitly selected, validated, visible 3D asset. */
class FilamentAvatarRenderer private constructor(private val surface: Surface) : AutoCloseable {
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var view: com.google.android.filament.View? = null
    private var scene: Scene? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var provider: UbershaderProvider? = null
    private var loader: AssetLoader? = null
    private var asset: FilamentAsset? = null
    private var cameraEntity = 0
    private var lightEntity = 0
    private lateinit var mapper: AvatarMorphMapper
    private var morphInstances = intArrayOf()
    private data class Joint(val instance: Int, val rest: FloatArray)
    private val joints = mutableMapOf<String, Joint>()
    private val rotation = FloatArray(16)
    private val transformed = FloatArray(16)
    private var halfHeight = .25
    private var viewportWidth = 0
    private var viewportHeight = 0

    private fun initialize(bytes: ByteArray, metadata: AvatarGlbContract.Metadata) {
        Gltfio.init()
        val e = Engine.create(Engine.Backend.OPENGL); engine = e
        renderer = e.createRenderer().also { r ->
            r.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
        }
        scene = e.createScene()
        cameraEntity = EntityManager.get().create()
        camera = e.createCamera(cameraEntity).also { it.setExposure(16f, 1f / 125f, 100f) }
        view = e.createView().also {
            it.scene = scene; it.camera = camera
            it.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
            it.isPostProcessingEnabled = false
        }
        swapChain = e.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)
        provider = UbershaderProvider(e)
        loader = AssetLoader(e, requireNotNull(provider), EntityManager.get())
        val glb = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        val a = requireNotNull(loader!!.createAsset(glb)) { "GLB loader rejected candidate" }; asset = a
        require(a.resourceUris.isEmpty()) { "External resources forbidden" }
        val resources = ResourceLoader(e, true)
        try { resources.loadResources(a) } finally { resources.destroy() }
        mapper = AvatarMorphMapper(metadata.targets)
        morphInstances = metadata.morphNodeNames.map { name ->
            val entity = a.getFirstEntityByName(name); require(entity != 0)
            val instance = e.renderableManager.getInstance(entity); require(instance != 0)
            require(e.renderableManager.getMorphTargetCount(instance) == metadata.targets.size)
            instance
        }.toIntArray()
        for (name in listOf("Head", "EyeLeft", "EyeRight", "Jaw")) {
            val instance = e.transformManager.getInstance(a.getFirstEntityByName(name)); require(instance != 0)
            joints[name] = Joint(instance, e.transformManager.getTransform(instance, FloatArray(16)))
        }
        val bounds = a.boundingBox
        val center = bounds.center; val extent = bounds.halfExtent
        require(center.all { it.isFinite() } && extent.all { it.isFinite() && it > 0f })
        halfHeight = maxOf(extent[1].toDouble(), extent[0].toDouble()) * 1.18
        camera!!.lookAt(center[0].toDouble(), center[1].toDouble(), center[2] + 2.0,
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(), 0.0, 1.0, 0.0)
        lightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(.72f, .90f, 1f).intensity(90_000f)
            .direction(-.4f, -.5f, -1f).castShadows(false).build(e, lightEntity)
        scene!!.addEntities(a.entities); scene!!.addEntity(lightEntity)
        a.releaseSourceData()
    }

    /** Called by the existing View frame clock; never posts a callback of its own. */
    fun render(pose: AvatarPose, seconds: Float, motion: Boolean, width: Int, height: Int): Boolean {
        val e = engine ?: return false
        if (width <= 0 || height <= 0 || !surface.isValid) return false
        if (viewportWidth != width || viewportHeight != height) {
            viewportWidth = width; viewportHeight = height
            view!!.viewport = Viewport(0, 0, width, height)
            val aspect = width.toDouble() / height
            val vertical = halfHeight / minOf(aspect, 1.0)
            camera!!.setProjection(Camera.Projection.ORTHO, -vertical * aspect, vertical * aspect, -vertical, vertical, .05, 10.0)
        }
        mapper.update(pose, seconds, motion)
        for (instance in morphInstances) e.renderableManager.setMorphWeights(instance, mapper.weights, 0)
        rotate("Head", mapper.headPitch, mapper.headYaw)
        rotate("EyeLeft", mapper.eyePitch, mapper.eyeYaw); rotate("EyeRight", mapper.eyePitch, mapper.eyeYaw)
        rotate("Jaw", mapper.jawPitch, 0f)
        asset!!.instance.animator.updateBoneMatrices()
        val r = renderer!!
        if (!r.beginFrame(swapChain!!, System.nanoTime())) return false
        try { r.render(view!!) } finally { r.endFrame() }
        return true
    }

    private fun rotate(name: String, pitch: Float, yaw: Float) {
        val joint = joints.getValue(name)
        Matrix.setIdentityM(rotation, 0); Matrix.rotateM(rotation, 0, yaw, 0f, 1f, 0f); Matrix.rotateM(rotation, 0, pitch, 1f, 0f, 0f)
        Matrix.multiplyMM(transformed, 0, joint.rest, 0, rotation, 0)
        engine!!.transformManager.setTransform(joint.instance, transformed)
    }

    override fun close() {
        val e = engine ?: return
        // Each release is independent so a lost surface cannot skip the remaining native cleanup.
        runCatching { asset?.let { scene?.removeEntities(it.entities); loader?.destroyAsset(it) } }; asset = null
        runCatching { loader?.destroy() }; loader = null
        runCatching { provider?.destroyMaterials() }; runCatching { provider?.destroy() }; provider = null
        if (lightEntity != 0) { runCatching { e.destroyEntity(lightEntity) }; runCatching { EntityManager.get().destroy(lightEntity) }; lightEntity = 0 }
        runCatching { view?.let(e::destroyView) }; view = null
        runCatching { scene?.let(e::destroyScene) }; scene = null
        if (cameraEntity != 0) { runCatching { e.destroyCameraComponent(cameraEntity) }; runCatching { EntityManager.get().destroy(cameraEntity) }; cameraEntity = 0 }
        runCatching { renderer?.let(e::destroyRenderer) }; renderer = null
        runCatching { swapChain?.let(e::destroySwapChain) }; swapChain = null
        runCatching { e.flushAndWait() }; runCatching { e.destroy() }; engine = null
        joints.clear()
    }

    companion object {
        fun create(surface: Surface, bytes: ByteArray, metadata: AvatarGlbContract.Metadata): FilamentAvatarRenderer {
            val candidate = FilamentAvatarRenderer(surface)
            try { candidate.initialize(bytes, metadata); return candidate }
            catch (failure: Throwable) { candidate.close(); throw failure }
        }
    }
}
