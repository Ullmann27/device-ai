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
    private var coreLightInstance = 0
    private var coreLightLumens = AvatarCoreLighting.BASE_LUMENS
    private var fillRed = .18f
    private var fillGreen = .82f
    private var fillBlue = 1f
    private var fillX = SpatialWindowLight.REST_X
    private var fillY = SpatialWindowLight.REST_Y
    private var fillZ = SpatialWindowLight.REST_Z
    private var skinRoughness = SpatialWindowLight.AUTHORED_SKIN_ROUGHNESS
    private var faceMaterial: MaterialInstance? = null
    private var faceEmission: AvatarGlbContract.SurfaceEmission? = null
    private var surfaceGain = 1f
    private lateinit var mapper: AvatarMorphMapper
    private var morphInstances = intArrayOf()
    private data class Joint(val instance: Int, val rest: FloatArray)
    private val joints = mutableMapOf<String, Joint>()
    private val rotation = FloatArray(16)
    private val transformed = FloatArray(16)
    private lateinit var focus: AvatarGlbContract.Focus
    private var viewportWidth = 0
    private var viewportHeight = 0
    /** Cockpit hero framing: head centre at [anchorX] of the viewport width, [closeUp] share of the bust height. */
    @Volatile private var anchorX = .5
    @Volatile private var closeUp = 1.0
    @Volatile private var framingVersion = 0
    private var appliedFraming = -1
    /** Projection window of the current viewport/framing; recomputed only when either changes. */
    private var window: AvatarFraming.Window? = null
    private var restWindow: AvatarFraming.Window? = null

    @Volatile private var keepOut: AvatarFraming.KeepOut? = null

    /**
     * Same rig, morphs and asset; only the orthographic window moves (asymmetric frustum) and tightens.
     * [keepOut]: stage area the posed face must not enter (the cockpit dialogue card), see [AvatarFraming].
     */
    fun setFraming(anchorX: Float, closeUp: Float, keepOut: AvatarFraming.KeepOut? = null) {
        require(anchorX.isFinite() && anchorX in .2f..1f && closeUp.isFinite() && closeUp in .4f..1f)
        if (this.anchorX == anchorX.toDouble() && this.closeUp == closeUp.toDouble() && this.keepOut == keepOut) return
        this.anchorX = anchorX.toDouble(); this.closeUp = closeUp.toDouble(); this.keepOut = keepOut; framingVersion++
    }
    private var bloomSettings: com.google.android.filament.View.BloomOptions? = null
    private var reducedQuality = false

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
            }.also { options -> bloomSettings = options }
        }
        swapChain = e.createSwapChain(surface, SwapChainFlags.CONFIG_TRANSPARENT)
        provider = UbershaderProvider(e)
        loader = AssetLoader(e, requireNotNull(provider), EntityManager.get())
        val glb = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        val a = requireNotNull(loader!!.createAsset(glb)) { "GLB loader rejected candidate" }; asset = a
        require(a.resourceUris.isEmpty()) { "External resources forbidden" }
        val resources = ResourceLoader(e, true)
        try { resources.loadResources(a) } finally { resources.destroy() }
        mapper = AvatarMorphMapper(metadata.targets, metadata.focus.azimuthDegrees)
        morphInstances = metadata.morphNodeNames.map { name ->
            val entity = a.getFirstEntityByName(name); require(entity != 0)
            val instance = e.renderableManager.getInstance(entity); require(instance != 0)
            require(e.renderableManager.getMorphTargetCount(instance) == metadata.targets.size)
            instance
        }.toIntArray()
        for (name in listOf("Neck", "Head", "EyeLeft", "EyeRight", "Jaw")) {
            val instance = e.transformManager.getInstance(a.getFirstEntityByName(name)); require(instance != 0)
            joints[name] = Joint(instance, e.transformManager.getTransform(instance, FloatArray(16)))
        }
        focus = metadata.focus
        val cameraAngle = Math.toRadians(focus.azimuthDegrees)
        camera!!.lookAt(focus.centerX + kotlin.math.sin(cameraAngle) * 2.0, focus.centerY,
            focus.centerZ + kotlin.math.cos(cameraAngle) * 2.0,
            focus.centerX, focus.centerY, focus.centerZ, 0.0, 1.0, 0.0)
        scene!!.addEntities(a.entities)
        // Owner decision 2026-11-05: the visible tooth rows read as uneven in the hologram. They stay in the
        // packaged GLB (bytes, pin and rig unchanged) but are not rendered; the dark mouth interior remains.
        a.getEntitiesByPrefix(HIDDEN_NODE_PREFIX).forEach { scene!!.removeEntity(it) }
        // Filament supports one directional key. Bounded point fills add real
        // cyan/violet facial volume without an IBL download or a new render pass.
        addLight(LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(.50f, .82f, 1f).intensity(80_000f)
            .direction(-.35f, -.40f, -1f).castShadows(false))
        addLight(LightManager.Builder(LightManager.Type.POINT)
            .color(.55f, .24f, 1f).intensity(28_000f)
            .position(-.38f, .32f, .38f).falloff(1.5f).castShadows(false))
        val coreLight = addLight(LightManager.Builder(LightManager.Type.POINT)
            .color(.18f, .82f, 1f).intensity(AvatarCoreLighting.BASE_LUMENS)
            .position(.32f, .35f, -.16f).falloff(1.2f).castShadows(false))
        coreLightInstance = e.lightManager.getInstance(coreLight)
        faceEmission = metadata.surfaceEmission
        faceMaterial = metadata.surfaceEmission?.let { authored ->
            a.instance.materialInstances.firstOrNull {
                it.name == authored.materialName && it.material.hasParameter("emissiveFactor")
            }
        }
        // The current portrait reference includes a readable iris and pupil.
        // Keep the authored glTF materials and eye meshes; a global cyan
        // emissive override would erase the violet/cyan surface design.
        a.releaseSourceData()
    }

    private var lastGlow = -1f

    /** Real speech may brighten bloom slightly; it never changes the authored material hues. */
    private fun applyGlow(speak: Float) {
        val k = if (speak.isFinite()) speak.coerceIn(0f, 1f) else 0f
        if (kotlin.math.abs(k - lastGlow) < .01f) return
        lastGlow = k
        bloomSettings?.let { options ->
            options.strength = .35f + .12f * k
            view?.bloomOptions = options
        }
    }

    /** Same animated model at every quality level; only bounded bloom buffers change. */
    fun setQuality(reduced: Boolean) {
        if (reducedQuality == reduced) return
        reducedQuality = reduced
        bloomSettings?.let { options ->
            options.resolution = if (reduced) 64 else 128
            options.levels = if (reduced) 3 else 4
            view?.bloomOptions = options
        }
    }

    private fun addLight(builder: LightManager.Builder): Int {
        val entity = EntityManager.get().create()
        // Track before build: partial initialization follows the same close path.
        lightEntities += entity
        builder.build(requireNotNull(engine), entity)
        requireNotNull(scene).addEntity(entity)
        return entity
    }

    private fun applyCoreLight(energy: Float, enabled: Boolean,
                               windowAccentArgb: Int?, windowOpen: Boolean, gazeX: Float, gazeY: Float) {
        val target = SpatialWindowLight.tint(windowAccentArgb, energy,
            windowOpen, enabled && !reducedQuality, gazeX, gazeY)
        val light = requireNotNull(engine).lightManager
        if (kotlin.math.abs(target.lumens - coreLightLumens) >= 1f) {
            light.setIntensity(coreLightInstance, target.lumens)
            coreLightLumens = target.lumens
        }
        if (kotlin.math.abs(target.red - fillRed) + kotlin.math.abs(target.green - fillGreen) +
            kotlin.math.abs(target.blue - fillBlue) >= .002f) {
            light.setColor(coreLightInstance, target.red, target.green, target.blue)
            fillRed = target.red; fillGreen = target.green; fillBlue = target.blue
        }
        if (kotlin.math.abs(target.positionX - fillX) + kotlin.math.abs(target.positionY - fillY) +
            kotlin.math.abs(target.positionZ - fillZ) >= .002f) {
            light.setPosition(coreLightInstance, target.positionX, target.positionY, target.positionZ)
            fillX = target.positionX; fillY = target.positionY; fillZ = target.positionZ
        }
        applySkinRoughness(target.roughness)
    }

    private fun applySkinRoughness(roughness: Float) {
        val material = faceMaterial ?: return
        if (kotlin.math.abs(roughness - skinRoughness) < .001f) return
        val name = when {
            material.material.hasParameter("roughnessFactor") -> "roughnessFactor"
            material.material.hasParameter("roughness") -> "roughness"
            else -> return
        }
        try {
            material.setParameter(name, roughness)
            skinRoughness = roughness
        } catch (_: RuntimeException) {
            // Authored GLB roughness stays; do not claim a live roughness response.
        }
    }

    private fun applySurfaceRadiance(energy: Float, enabled: Boolean) {
        val material = faceMaterial ?: return
        val authored = requireNotNull(faceEmission)
        val gain = AvatarSurfaceRadiance.gain(energy, enabled && !reducedQuality)
        if (kotlin.math.abs(gain - surfaceGain) < .001f) return
        try {
            material.setParameter("emissiveFactor", authored.red * gain, authored.green * gain, authored.blue * gain)
            surfaceGain = gain
        } catch (_: RuntimeException) {
            faceMaterial = null
        }
    }

    /** Called by the existing View frame clock; never posts a callback of its own. */
    fun render(pose: AvatarPose, seconds: Float, motion: Boolean, width: Int, height: Int,
               framing: AvatarFraming.Window? = null, diagnosticBlink: Float? = null,
               receipt: AvatarFrameReceipt? = null, coreEnergy: Float = 0f,
               cinematicLighting: Boolean = false, windowAccentArgb: Int? = null,
               windowOpen: Boolean = false, gazeBlinkEnabled: Boolean = false,
               eyeNeckCouplingEnabled: Boolean = true,
               sentenceGesturesEnabled: Boolean = true): Boolean {
        val e = engine ?: return false
        if (width <= 0 || height <= 0 || !surface.isValid) return false
        if (viewportWidth != width || viewportHeight != height || appliedFraming != framingVersion) {
            viewportWidth = width; viewportHeight = height; appliedFraming = framingVersion
            view!!.viewport = Viewport(0, 0, width, height)
            // Same window as the 2D head aura and the probe; it keeps the full pose reach inside the stage.
            restWindow = AvatarFraming.window(focus, width.toDouble() / height, anchorX, closeUp, keepOut)
        }
        val fitted = framing ?: requireNotNull(restWindow)
        if (window != fitted) {
            window = fitted
            camera!!.setProjection(Camera.Projection.ORTHO, fitted.left, fitted.right,
                -fitted.halfHeight, fitted.halfHeight, .05, 10.0)
        }
        mapper.eyeNeckCouplingEnabled = eyeNeckCouplingEnabled
        mapper.sentenceGesturesEnabled = sentenceGesturesEnabled
        mapper.update(pose, seconds, motion, gazeBlinkEnabled = gazeBlinkEnabled)
        mapper.applyDiagnosticBlink(diagnosticBlink)
        applyGlow(if (motion) pose.speakEnergy else 0f)
        val flyIn = CinemaWindowLightState.snapshot
        val accent = flyIn.accentOr(windowAccentArgb)
        val open = windowOpen || flyIn.open
        // Light follows glass, not eyes.
        val side = if (flyIn.open) flyIn.gazeX else if (pose.gazeDirected) pose.gazeX else 0f
        val elevation = if (flyIn.open) flyIn.gazeY else if (pose.gazeDirected) pose.gazeY else 0f
        applyCoreLight(coreEnergy, cinematicLighting && motion, accent, open, side, elevation)
        applySurfaceRadiance(coreEnergy, cinematicLighting && motion)
        // Cosmetic view drift uses the existing clock. Reduced motion restores
        // the same rest camera immediately; it does not change the actual pose.
        // A close-up keeps the crown margin and crops from below (neck and shoulders dissolve in the view mask).
        val centerY = fitted.cameraCenterY
        val px = 2.0 * fitted.halfHeight / height
        val moving = motion && seconds.isFinite()
        val dy = if (moving) kotlin.math.sin(seconds * (2.0 * Math.PI / 4.0)) * 2.5 * 2.6 * px else 0.0
        val distance = 2.0 + if (moving) kotlin.math.sin(seconds * .21) * .06 else 0.0
        val cameraAngle = Math.toRadians(focus.azimuthDegrees)
        camera!!.lookAt(focus.centerX + kotlin.math.sin(cameraAngle) * distance, centerY - dy,
            focus.centerZ + kotlin.math.cos(cameraAngle) * distance,
            focus.centerX, centerY - dy, focus.centerZ, 0.0, 1.0, 0.0)
        for (instance in morphInstances) e.renderableManager.setMorphWeights(instance, mapper.weights, 0)
        receipt?.stageFlags = receipt?.stageFlags?.or(AvatarFrameReceipt.MORPH_UPLOADED) ?: 0
        // The neck carries most of the turn, the head the rest: the skin blends Neck→Head inside the chin band.
        rotate("Neck", mapper.neckPitch, mapper.neckYaw)
        rotate("Head", mapper.headPitch - mapper.neckPitch, mapper.headYaw - mapper.neckYaw)
        rotate("EyeLeft", mapper.eyePitch, mapper.eyeYaw); rotate("EyeRight", mapper.eyePitch, mapper.eyeYaw)
        rotate("Jaw", mapper.jawPitch, 0f)
        asset!!.instance.animator.updateBoneMatrices()
        receipt?.stageFlags = receipt?.stageFlags?.or(AvatarFrameReceipt.JOINTS_UPLOADED) ?: 0
        val r = renderer!!
        if (!r.beginFrame(swapChain!!, System.nanoTime())) return false
        receipt?.stageFlags = receipt?.stageFlags?.or(AvatarFrameReceipt.FILAMENT_BEGIN) ?: 0
        try {
            r.render(view!!)
            receipt?.stageFlags = receipt?.stageFlags?.or(AvatarFrameReceipt.FILAMENT_RENDERED) ?: 0
        } finally { r.endFrame() }
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
        faceMaterial = null
        faceEmission = null
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
        /** GLB nodes kept in the asset but not rendered (ToothUpper0..7, ToothLower0..7). */
        const val HIDDEN_NODE_PREFIX = "Tooth"
        fun create(surface: Surface, bytes: ByteArray, metadata: AvatarGlbContract.Metadata): FilamentAvatarRenderer {
            val candidate = FilamentAvatarRenderer(surface)
            try { candidate.initialize(bytes, metadata); return candidate }
            catch (failure: Throwable) { candidate.close(); throw failure }
        }
    }
}
