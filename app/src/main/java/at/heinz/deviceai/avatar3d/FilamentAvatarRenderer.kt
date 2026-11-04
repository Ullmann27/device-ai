package at.heinz.deviceai.avatar3d

import android.opengl.Matrix
import android.view.Surface
import at.heinz.deviceai.avatar.AvatarPose
import com.google.android.filament.*
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer

/** UI-thread owned, only allocated for a selected, validated, visible 3D asset. */
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
    private val lightEntities = mutableListOf<Int>()
    private lateinit var mapper: AvatarMorphMapper
    private var morphInstances = intArrayOf()
    private data class Joint(val instance: Int, val rest: FloatArray)
    private val joints = mutableMapOf<String, Joint>()
    private val rotation = FloatArray(16)
    private val transformed = FloatArray(16)
    private lateinit var focus: AvatarGlbContract.Focus
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
        // A fixed studio exposure keeps the low-reflectance face readable. It is
        // independent of app/model activity and does not pump with speech energy.
        camera = e.createCamera(cameraEntity).also { it.setExposure(11f, 1f / 125f, 100f) }
        view = e.createView().also {
            it.scene = scene; it.camera = camera
            it.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
            // Filament's post pipeline supplies color grading and gamma encoding.
            // Disabling it also disables color correctness (pinned View.java 1.56).
            // The existing View owns these bounded buffers; its normal destroyView
            // path releases them, with no added frame callback or native entity.
            it.isPostProcessingEnabled = true
            it.bloomOptions = com.google.android.filament.View.BloomOptions().apply {
                enabled = true
                strength = .35f   // VIDEO-SPEC / creative review: strong bloom
                resolution = 128
                levels = 4
                quality = com.google.android.filament.View.QualityLevel.LOW
                threshold = true
                highlight = 64f
                lensFlare = false
                starburst = false
                chromaticAberration = 0f
            }
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
        focus = metadata.focus
        camera!!.lookAt(focus.centerX, focus.centerY, focus.centerZ + 2.0,
            focus.centerX, focus.centerY, focus.centerZ, 0.0, 1.0, 0.0)
        scene!!.addEntities(a.entities)
        // Filament supports one directional key. Bounded point fills add real
        // cyan/violet facial volume without an IBL download or a new render pass.
        addLight(LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(.50f, .82f, 1f).intensity(80_000f)
            .direction(-.35f, -.40f, -1f).castShadows(false))
        addLight(LightManager.Builder(LightManager.Type.POINT)
            .color(.55f, .24f, 1f).intensity(28_000f)
            .position(-.38f, .32f, .38f).falloff(1.5f).castShadows(false))
        addLight(LightManager.Builder(LightManager.Type.POINT)
            .color(.18f, .82f, 1f).intensity(24_000f)
            .position(.32f, .35f, -.16f).falloff(1.2f).castShadows(false))
        // VIDEO-SPEC: eyes without pupils (cyan/white). Hide iris/pupil meshes via layer mask; asset unchanged.
        for (name in listOf("IrisLeftMesh", "IrisRightMesh", "PupilLeftMesh", "PupilRightMesh")) {
            val entity = a.getFirstEntityByName(name)
            if (entity == 0) continue
            val ri = e.renderableManager.getInstance(entity)
            if (ri != 0) e.renderableManager.setLayerMask(ri, 0xff, 0)
        }
        for (name in listOf("EyeballLeftMesh", "EyeballRightMesh")) {
            val entity = a.getFirstEntityByName(name)
            if (entity != 0) eyeMaterials += materialsOf(e, entity)
        }
        // Holo glow: emissive of every glTF material instance, driven by the TTS envelope in render().
        holoMaterials += a.instance.materialInstances.filter { mi -> runCatching { mi.material.hasParameter("emissiveFactor") }.getOrDefault(false) }
        a.releaseSourceData()
    }

    private val holoMaterials = ArrayList<com.google.android.filament.MaterialInstance>()
    private val eyeMaterials = ArrayList<com.google.android.filament.MaterialInstance>()

    private fun materialsOf(e: Engine, entity: Int): List<com.google.android.filament.MaterialInstance> {
        val ri = e.renderableManager.getInstance(entity)
        if (ri == 0) return emptyList()
        return (0 until e.renderableManager.getPrimitiveCount(ri)).map { e.renderableManager.getMaterialInstanceAt(ri, it) }
            .filter { mi -> runCatching { mi.material.hasParameter("emissiveFactor") }.getOrDefault(false) }
    }

    private var lastGlow = -1f

    /** #00FFFF..#0088FF emissive, quiet at rest and brighter with the real speech envelope (no invented speech). */
    private fun applyGlow(speak: Float) {
        val k = .22f + .78f * speak.coerceIn(0f, 1f)
        if (kotlin.math.abs(k - lastGlow) < .01f) return
        lastGlow = k
        for (mi in holoMaterials) runCatching { mi.setParameter("emissiveFactor", 0f, .53f + .47f * k * k, 1f * k) }
        for (mi in eyeMaterials) runCatching { mi.setParameter("emissiveFactor", .75f, 1f, 1f) }
    }

    private fun addLight(builder: LightManager.Builder) {
        val entity = EntityManager.get().create()
        // Track before build: partial initialization follows the same close path.
        lightEntities += entity
        builder.build(requireNotNull(engine), entity)
        requireNotNull(scene).addEntity(entity)
    }

    /** Called by the existing View frame clock; never posts a callback of its own. */
    fun render(pose: AvatarPose, seconds: Float, motion: Boolean, width: Int, height: Int): Boolean {
        val e = engine ?: return false
        if (width <= 0 || height <= 0 || !surface.isValid) return false
        if (viewportWidth != width || viewportHeight != height) {
            viewportWidth = width; viewportHeight = height
            view!!.viewport = Viewport(0, 0, width, height)
            val aspect = width.toDouble() / height
            val vertical = focus.verticalHalfSpan(aspect)
            camera!!.setProjection(Camera.Projection.ORTHO, -vertical * aspect, vertical * aspect, -vertical, vertical, .05, 10.0)
        }
        mapper.update(pose, seconds, motion)
        applyGlow(if (motion) pose.speakEnergy else 0f)
        // VIDEO-SPEC C: idle float (4 s sine, ~2.5 px) + slow camera drift; static when motion is off.
        if (motion && seconds.isFinite()) {
            val vertical = focus.verticalHalfSpan(width.toDouble() / height)
            val px = 2.0 * vertical / height
            val dy = kotlin.math.sin(seconds * (2.0 * Math.PI / 4.0)) * 2.5 * 2.6 * px
            val dz = kotlin.math.sin(seconds * .21) * .06
            camera!!.lookAt(focus.centerX, focus.centerY - dy, focus.centerZ + 2.0 + dz,
                focus.centerX, focus.centerY - dy, focus.centerZ, 0.0, 1.0, 0.0)
        }
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
        for (entity in lightEntities) {
            runCatching { scene?.removeEntity(entity) }
            runCatching { e.destroyEntity(entity) }
            runCatching { EntityManager.get().destroy(entity) }
        }
        lightEntities.clear()
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
