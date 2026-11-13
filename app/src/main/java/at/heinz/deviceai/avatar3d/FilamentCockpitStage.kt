package at.heinz.deviceai.avatar3d

import android.graphics.RectF
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
    assets: CockpitSceneAssets,
    cacheTransforms: Boolean = true,
    private val panelBytes: ByteArray? = null,
    private val panelPainter: at.heinz.deviceai.ui.AndroidContinuumPainter? = null
) : AutoCloseable {
    private var workstation: FilamentAsset? = null
    private var orb: FilamentAsset? = null
    /** Code56 rear housing details (vents, VESA, arm, I/O, cables); follows the monitor matrix every frame. */
    private var monitorRear: FilamentAsset? = null
    private var monitorRearTransform = 0
    private var monitorMaterial: Material? = null
    private var ambientLight: IndirectLight? = null
    private var studioReflections: Texture? = null
    private var glowEntity = 0
    private var glowInstance = 0
    private val glow = CockpitMonitorGlow()
    private val glowTransform = CockpitMonitorLightTransform(
        { x, y, z -> engine.lightManager.setPosition(glowInstance, x, y, z) },
        { falloff -> engine.lightManager.setFalloff(glowInstance, falloff) }
    )
    private var monitorInstance: MaterialInstance? = null
    private var pixels: CockpitLiveTexture? = null
    private var contextPanels: CockpitContextPanels? = null
    private var contextContent: CockpitPanelContent? = null
    private var contextPanelsFailed = false
    private var roomTransform = 0
    private var sideMonitorTransform = 0
    private var monitorTransform = 0
    private var orbTransform = 0
    private val orbitTransforms = mutableListOf<Pair<Int, FloatArray>>()
    private val transform = FloatArray(16)
    private val orbitCombined = FloatArray(16)
    private val transformUpdates = CockpitTransformUpdates(engine.transformManager::setTransform, cacheTransforms)
    val pixelsVisible get() = pixels?.pixelsVisible == true
    var label = "Keine Bildquelle verbunden"
        private set

    init {
        try {
            workstation = load(assets.workstation)
            orb = load(assets.orb)
            roomTransform = namedTransform(requireNotNull(workstation), "cockpit-room")
            sideMonitorTransform = namedTransform(requireNotNull(workstation), "cockpit-side-monitors")
            monitorTransform = namedTransform(requireNotNull(workstation), "cockpit-monitor")
            // Optional, separately pinned asset: a failure leaves the previous scene intact.
            assets.monitorRear?.let { bytes ->
                runCatching { load(bytes) }.onSuccess { loaded ->
                    runCatching { namedTransform(loaded, "monitor-rear-details") }
                        .onSuccess { monitorRear = loaded; monitorRearTransform = it }
                        .onFailure { runCatching { scene.removeEntities(loaded.entities); loader.destroyAsset(loaded) } }
                }
            }
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
            // Glass and metal need something to reflect. The studio cubemap is baked once; if this device cannot
            // create it, the stage keeps the previous constant ambient light instead of failing.
            studioReflections = runCatching { studioCubemap() }.getOrNull()
            ambientLight = IndirectLight.Builder().irradiance(1, floatArrayOf(.90f, .98f, 1.10f))
                .apply { studioReflections?.let(::reflections) }
                .intensity(20_000f).build(engine)
            scene.indirectLight = ambientLight
            // Light the real monitor casts on desk and keyboard; dark until real frames are visible.
            glowEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.POINT).color(.74f, .88f, 1f).intensity(0f)
                .position(0f, 0f, 0f).falloff(1f).castShadows(false).build(engine, glowEntity)
            scene.addEntity(glowEntity)
            glowInstance = engine.lightManager.getInstance(glowEntity)
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

    private fun studioCubemap(): Texture {
        val size = CockpitStudioEnvironment.FACE_SIZE
        val faces = CockpitStudioEnvironment.faces(size)
        val bytes = java.nio.ByteBuffer.allocateDirect(faces.size * 4).order(java.nio.ByteOrder.nativeOrder())
        bytes.asFloatBuffer().put(faces)
        val texture = Texture.Builder().width(size).height(size).levels(CockpitStudioEnvironment.levels(size))
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.R11F_G11F_B10F).build(engine)
        try {
            val faceBytes = size * size * 3 * 4
            texture.generatePrefilterMipmap(engine,
                Texture.PixelBufferDescriptor(bytes, Texture.Format.RGB, Texture.Type.FLOAT),
                // Filament samples reflection cubemaps X-mirrored (PrefilterOptions default); faces() is plain OpenGL layout.
                IntArray(6) { it * faceBytes }, Texture.PrefilterOptions().apply { sampleCount = 16; mirror = true })
            return texture
        } catch (failure: Throwable) { engine.destroyTexture(texture); throw failure }
    }

    /** Position and strength follow the real monitor and the real frame state; uploads happen only on change. */
    private fun updateGlow(matrix: FloatArray, nowNanos: Long) {
        if (glowInstance == 0) return
        glowTransform.update(matrix)
        glow.step(pixelsVisible, nowNanos)?.let { engine.lightManager.setIntensity(glowInstance, it) }
    }

    private fun namedTransform(asset: FilamentAsset, name: String): Int =
        engine.transformManager.getInstance(asset.getFirstEntityByName(name)).also { require(it != 0) { name } }

    fun panelContent(content: CockpitPanelContent?) {
        if (contextContent?.key != content?.key ||
            contextContent?.observationRevision != content?.observationRevision) contextPanels?.frameFailed()
        contextContent = content
    }
    fun panelsPresented(content: CockpitPanelContent?, frame: CockpitStageFrame?) =
        contextPanels?.presented(content, frame) == true
    fun bind(feed: LiveMonitorFeed<AndroidLiveFrame>?) { pixels?.bind(feed) }
    fun workEvent(event: CockpitWorkEvent) { pixels?.workEvent(event) }
    fun frameSubmitted(nowNanos: Long) {
        pixels?.frameSubmitted(nowNanos)
        contextPanels?.frameSubmitted()
        label = pixels?.label ?: "3D-Bildquelle nicht verfügbar"
    }
    fun frameFailed() {
        pixels?.frameFailed()
        contextPanels?.frameFailed()
        label = pixels?.label ?: "3D-Bildquelle nicht verfügbar"
    }

    fun update(frame: CockpitStageFrame, orbBounds: RectF, phase: Float, motion: Boolean,
               monitorTurnDegrees: Float = 0f) {
        require(frame.valid())
        CockpitWorldTransforms.room(frame, transform)
        transformUpdates.setTransform(roomTransform, transform)
        if (frame.monitorWidth / frame.width > .70f) {
            transform[0] = 0f
            transform[5] = 0f
            transform[10] = 0f
        }
        transformUpdates.setTransform(sideMonitorTransform, transform)
        if (!contextPanelsFailed) runCatching {
            if (contextPanels == null && contextContent != null && panelBytes != null && panelPainter != null &&
                frame.monitorWidth / frame.width <= .70f) {
                contextPanels = CockpitContextPanels(engine, scene, loader, panelBytes, sideMonitorTransform, panelPainter)
            }
            contextPanels?.update(contextContent, frame)
        }.onFailure {
            contextPanelsFailed = true
            contextPanels?.close()
            contextPanels = null
            android.util.Log.w("ContinuumCockpit", "Context panels unavailable", it)
        }
        val monitorMatrix = frame.monitorTransform(monitorTurnDegrees)
        transformUpdates.setTransform(monitorTransform, monitorMatrix)
        if (monitorRearTransform != 0) transformUpdates.setTransform(monitorRearTransform, monitorMatrix)
        CockpitWorldTransforms.orb(frame, orbBounds.left, orbBounds.top, orbBounds.right, orbBounds.bottom, transform)
        transformUpdates.setTransform(orbTransform, transform)
        orbitTransforms.forEachIndexed { index, (instance, rest) ->
            CockpitWorldTransforms.ringTransform(rest, index, phase, motion, orbitCombined)
            transformUpdates.setTransform(instance, orbitCombined)
        }
        val now = System.nanoTime()
        pixels?.update(frame, now)
        updateGlow(monitorMatrix, now)
        label = pixels?.label ?: "3D-Bildquelle nicht verfügbar"
    }

    override fun close() {
        transformUpdates.clear()
        runCatching { pixels?.close() }; pixels = null
        runCatching { contextPanels?.close() }; contextPanels = null
        contextContent = null
        for (loaded in listOfNotNull(workstation, orb, monitorRear)) {
            runCatching { scene.removeEntities(loaded.entities); loader.destroyAsset(loaded) }
        }
        workstation = null; orb = null; monitorRear = null; monitorRearTransform = 0
        runCatching { monitorInstance?.let(engine::destroyMaterialInstance) }; monitorInstance = null
        runCatching { monitorMaterial?.let(engine::destroyMaterial) }; monitorMaterial = null
        runCatching { scene.indirectLight = null }
        runCatching { ambientLight?.let(engine::destroyIndirectLight) }; ambientLight = null
        runCatching { studioReflections?.let(engine::destroyTexture) }; studioReflections = null
        if (glowEntity != 0) runCatching {
            scene.removeEntity(glowEntity); engine.lightManager.destroy(glowEntity)
            EntityManager.get().destroy(glowEntity)
        }
        glowEntity = 0; glowInstance = 0; glow.reset(); glowTransform.clear()
        orbitTransforms.clear()
    }

    companion object {
        const val ASSET_PATH = "cinema3d/cockpit-workstation.glb"
        const val MAX_BYTES = 2_000_000
    }
}


/** Two contextual quads, same scene. */
private class CockpitContextPanels(
    private val engine: Engine,
    private val scene: Scene,
    private val loader: AssetLoader,
    bytes: ByteArray,
    parent: Int,
    private val painter: at.heinz.deviceai.ui.AndroidContinuumPainter
) : AutoCloseable {
    private val owner = Thread.currentThread()
    private val handler = android.os.Handler(requireNotNull(android.os.Looper.myLooper()))
    private val state = CockpitPanelUploadState()
    private val textPainter = at.heinz.deviceai.ui.JarvisTextScalePainter(painter)
    private val pages = IntArray(2)
    private val materials = arrayOfNulls<MaterialInstance>(2)
    private val textures = arrayOfNulls<Texture>(2)
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private var asset: FilamentAsset? = null
    private var textureWidth = 0
    private var textureHeight = 0
    private var shown = false
    private var closed = false

    init {
        try {
            require(bytes.size in 1..FilamentDocumentStage.MAX_BYTES)
            val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
            val instances = arrayOfNulls<com.google.android.filament.gltfio.FilamentInstance>(2)
            val loaded = requireNotNull(loader.createInstancedAsset(buffer, instances))
            asset = loaded
            require(loaded.resourceUris.isEmpty())
            val resources = ResourceLoader(engine, true)
            try { resources.loadResources(loaded) } finally { resources.destroy() }
            val transforms = engine.transformManager
            for (index in pages.indices) {
                val instance = requireNotNull(instances[index])
                val entity = instance.entities.single { loaded.getName(it) == "pdf-page" }
                pages[index] = entity
                val renderable = engine.renderableManager.getInstance(entity)
                require(renderable != 0 && engine.renderableManager.getPrimitiveCount(renderable) == 1)
                val original = engine.renderableManager.getMaterialInstanceAt(renderable, 0)
                require(original.material.hasParameter("baseColorMap"))
                val material = MaterialInstance.duplicate(original, if (index == 0) "cockpit-plan" else "cockpit-result")
                materials[index] = material
                engine.renderableManager.setMaterialInstanceAt(renderable, 0, material)
                val transform = transforms.getInstance(entity)
                transforms.setParent(transform, parent)
                transforms.setTransform(transform, floatArrayOf(
                    .20f, 0f, 0f, 0f, 0f, .30f, 0f, 0f, 0f, 0f, 1f, 0f,
                    if (index == 0) -.38f else .38f, -.22f, 0f, 1f))
            }
            loaded.releaseSourceData()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun presented(content: CockpitPanelContent?, frame: CockpitStageFrame?) = state.presented(content, frame)
    fun frameSubmitted() { if (shown) state.frameSubmitted() else state.frameFailed() }
    fun frameFailed() = state.frameFailed()

    fun update(content: CockpitPanelContent?, frame: CockpitStageFrame) {
        check(Thread.currentThread() === owner)
        if (closed) return
        state.request(content, frame)
        show(state.drawable)
        val ticket = state.begin() ?: return
        upload(requireNotNull(content), ticket)
    }

    private fun show(value: Boolean) {
        if (shown == value) return
        for (page in pages) if (value) scene.addEntity(page) else scene.removeEntity(page)
        shown = value
    }

    private fun prepareTextures(width: Int, height: Int) {
        if (textureWidth == width && textureHeight == height) return
        for (index in textures.indices) {
            val candidate = Texture.Builder().width(width).height(height).levels(1)
                .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
            try {
                requireNotNull(materials[index]).setParameter("baseColorMap", candidate, sampler)
            } catch (failure: Throwable) {
                engine.destroyTexture(candidate)
                throw failure
            }
            val previous = textures[index]
            textures[index] = candidate
            previous?.let(engine::destroyTexture)
        }
        textureWidth = width
        textureHeight = height
    }

    private fun upload(content: CockpitPanelContent, ticket: CockpitPanelUploadState.Ticket) {
        val request = ticket.request
        prepareTextures(request.width, request.height)
        val bitmaps = arrayOfNulls<android.graphics.Bitmap>(2)
        try {
            textPainter.fontScale = request.key.fontScale
            val box = at.heinz.deviceai.ui.JarvisShellLayout.Box(0f, 0f,
                request.logicalWidth, request.logicalHeight)
            for (index in bitmaps.indices) {
                val bitmap = android.graphics.Bitmap.createBitmap(request.width, request.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                bitmaps[index] = bitmap
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(0xff041320.toInt())
                canvas.scale(request.width / request.logicalWidth, request.height / request.logicalHeight)
                painter.begin(canvas)
                content.draw(textPainter, box, index == 1)
            }
        } catch (failure: Throwable) {
            bitmaps.forEach { it?.recycle() }
            throw failure
        }
        var remaining = bitmaps.size
        var scheduled = 0
        try {
            for (index in bitmaps.indices) {
                val bitmap = requireNotNull(bitmaps[index])
                com.google.android.filament.android.TextureHelper.setBitmap(engine,
                    requireNotNull(textures[index]), 0, bitmap, handler, Runnable {
                        bitmap.recycle()
                        if (closed) return@Runnable
                        remaining--
                        if (remaining == 0) {
                            state.complete(ticket)
                            content.requestFrame()
                        }
                    })
                scheduled++
            }
        } catch (failure: Throwable) {
            for (index in scheduled until bitmaps.size) bitmaps[index]?.recycle()
            throw failure
        }
    }

    override fun close() {
        check(Thread.currentThread() === owner)
        if (closed) return
        closed = true
        state.close()
        for (page in pages) if (page != 0) runCatching { scene.removeEntity(page) }
        shown = false
        runCatching { asset?.let(loader::destroyAsset) }
        asset = null
        for (index in materials.indices) {
            runCatching { materials[index]?.let(engine::destroyMaterialInstance) }
            materials[index] = null
            runCatching { textures[index]?.let(engine::destroyTexture) }
            textures[index] = null
        }
    }
}
