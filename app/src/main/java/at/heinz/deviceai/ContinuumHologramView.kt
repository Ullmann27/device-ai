package at.heinz.deviceai

import android.animation.ValueAnimator
import android.app.AlertDialog
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.ContinuumAvatarRenderer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.widget.Toast
import android.view.View
import android.view.TextureView
import android.view.Surface
import android.graphics.SurfaceTexture
import android.widget.FrameLayout
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarCameraFraming
import at.heinz.deviceai.avatar3d.AvatarCameraMotion
import at.heinz.deviceai.avatar3d.AvatarEntranceMotion
import at.heinz.deviceai.avatar3d.AvatarFraming
import at.heinz.deviceai.avatar3d.FilamentAvatarRenderer
import at.heinz.deviceai.avatar3d.AvatarFrameReceipt
import at.heinz.deviceai.avatar3d.AvatarTraceRingBuffer
import at.heinz.deviceai.avatar3d.AvatarProofFrame
import at.heinz.deviceai.avatar3d.AvatarProofCompletion
import at.heinz.deviceai.avatar3d.DeterministicAvatarProofRunner
import at.heinz.deviceai.ui.AndroidContinuumPainter
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.ContinuumTaskEffects
import at.heinz.deviceai.ui.JarvisSequenceView
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.FontFamily
import at.heinz.deviceai.ui.TextAlign
import at.heinz.deviceai.ui.TextStyle
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Hardware accelerated living Continuum core. Drawing is done by [ContinuumHologramRenderer] (shared with the JVM
 * preview); this view keeps the real state, speech energy, reduced-motion and power-save policies.
 */
class ContinuumHologramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), AvatarSink {
    enum class State { READY, LISTENING, THINKING, SPEAKING, RESEARCH, LEARNING }

    private val avatarRenderer = ContinuumAvatarRenderer()
    private val avatarPose = AvatarPoseReducer()
    private val appearancePrefs = context.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE)
    private val cinematicPrefs = at.heinz.deviceai.ui.JarvisCinematicSettings.preferences(context)
    private val hasSavedAppearance = appearancePrefs.contains("native-head") || appearancePrefs.contains("native-head-3d")
    // Upgrade legacy enabled heads.
    // Explicit 3D choices remain authoritative.
    private var head3dEnabled = appearancePrefs.getBoolean("native-head-3d",
        appearancePrefs.getBoolean("native-head", !hasSavedAppearance))
    private var headEnabled = head3dEnabled || appearancePrefs.getBoolean("native-head", !hasSavedAppearance)
    private var native3d: FilamentAvatarRenderer? = null
    private var nativeTexture: TextureView? = null
    private var nativeSurface: Surface? = null
    private var assetBytes: ByteArray? = null
    private var assetMetadata: AvatarGlbContract.Metadata? = null
    private var assetLoading = false
    private var assetGeneration = 0
    private var native3dFailed = false
    private var native3dDrawn = false
    private var documentStageBytes: ByteArray? = null
    private class DocumentStageBinding(val id: String, val surface: Surface, val ready: (Boolean) -> Unit) {
        var frame: at.heinz.deviceai.avatar3d.DocumentStageFrame? = null
        var page: android.graphics.Bitmap? = null
        var bufferWidth = 0
        var bufferHeight = 0
        var failed = false
        var drawn = false
        fun report(value: Boolean) {
            if (drawn != value) { drawn = value; ready(value) }
        }
    }
    private var documentStageBinding: DocumentStageBinding? = null
    private val avatarTrace = AvatarTraceRingBuffer()
    private val pendingAvatarTraceFlags = java.util.concurrent.atomic.AtomicInteger(0)
    private var avatarProofStartedAt = Long.MIN_VALUE
    private var avatarProofStartCallbacks = 0L
    private var avatarProofStartDraws = 0L
    private var avatarProofStartTraceFrame = 0L
    private val avatarProofFinish = Runnable { finishAvatarProof() }
    private val presentationBudgetObservers = linkedSetOf<() -> Unit>()
    private var avatarSeconds = 0f
    private val avatarInvalidationPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val avatarInvalidation = Runnable {
        avatarInvalidationPending.set(false)
        if (isAttachedToWindow && foregroundActive && isShown) invalidate()
    }
    private val renderer = ContinuumHologramRenderer()
    private val avatarBrandFill = Fill.Solid(0xff9aefff.toInt())
    private val avatarBrandStyle = TextStyle(FontFamily.DISPLAY, 700, 14f, letterSpacing = 2f)
    private val painter = AndroidContinuumPainter(context.assets)
    private val taskEffects = ContinuumTaskEffects()
    private var cockpitSequence: JarvisSequenceView? = null
    internal val memoryScene = at.heinz.deviceai.ui.video3.Video3MemoryScene()
    private val memoryTunnel = at.heinz.deviceai.ui.video3.Video3WarpScene(paper = true)
    internal var memorySceneHostReady: (() -> Boolean)? = null
    internal var memorySceneInvalidation: (() -> Unit)? = null
    internal val memorySceneOrbBounds = RectF()
    private val memoryBackdrop = at.heinz.deviceai.ui.Fill.Solid(at.heinz.deviceai.ui.JarvisDesign.Colors.background)
    private var cockpitHeadBounds: Rect? = null
    private var cockpitOrbBounds: Rect? = null
    private val headDrawBounds = RectF()
    private val orbDrawBounds = RectF()
    private var taskEffectsNotBeforeMs = SystemClock.uptimeMillis()
    private val audio = HologramAudioEnvelope()
    // State order equals ContinuumHologramRenderer.Mode order (READY … LEARNING).
    private val stateWeights = FloatArray(State.entries.size).apply { this[0] = 1f }
    private val frameWeights = FloatArray(State.entries.size)
    private val renderFrame = ContinuumHologramRenderer.Frame(.4f, .18f, 0f, frameWeights)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var currentState = State.READY
    @Volatile private var pendingAudioLevel = 0f
    @Volatile private var pendingAudioMs = 0L
    private var consumedAudioMs = 0L
    private var animationsEnabled = true
    // A visible window is not proof that its Activity is still foregrounded.
    // The owner Activity explicitly pauses/resumes this one animation loop.
    private var foregroundActive = false
    private var motionAllowed = false
    private var visibleFrameCount = 0L
    private var drawnFrameCount = 0L
    private var framePending = false
    private var observersRegistered = false
    private var lastFrameMs = 0L
    private var phase = .4f
    private var energy = .18f
    private var audioEnergy = 0f
    private var thermalStatus = 0
    // Stored as Any so API26–28 never resolve the API29 listener class on construction.
    private var thermalListener: Any? = null

    private fun onThermalStatus(status: Int) {
        thermalStatus = status
        updateMotionPolicy()
    }

    @android.annotation.TargetApi(29)
    private fun observeThermalPressure() {
        thermalStatus = powerManager.currentThermalStatus
        val listener = PowerManager.OnThermalStatusChangedListener { onThermalStatus(it) }
        powerManager.addThermalStatusListener(context.mainExecutor, listener)
        thermalListener = listener
    }

    @android.annotation.TargetApi(29)
    private fun releaseThermalObserver() {
        (thermalListener as? PowerManager.OnThermalStatusChangedListener)?.let {
            powerManager.removeThermalStatusListener(it)
        }
        thermalListener = null
    }

    private val drawFrame = Runnable {
        framePending = false
        if (canAnimateFrame()) {
            visibleFrameCount++
            // The motion clock advances independently of a completed Canvas draw.
            // Invalidation requests only rendering, not the next state transition.
            advanceFrame()
            if (memoryScene.isActive) {
                if (memorySceneHostReady?.invoke() != true) clearMemoryScene()
                else {
                    if (memoryScene.sample(memorySceneTime()) == null) clearMemoryScene()
                    memorySceneInvalidation?.invoke()
                }
            }
            postInvalidateOnAnimation()
            scheduleFrame()
        }
    }

    /** Owns the single visible-view frame callback, even before the first draw. */
    private fun scheduleFrame() {
        if (canAnimateFrame() && !framePending) {
            framePending = true
            // Vsync-paced with a positive delay; moderate heat reduces requests, severe heat stops them.
            val delay = (renderBudget().frameDelayMillis - 16L).coerceAtLeast(0L)
            postOnAnimationDelayed(drawFrame, delay)
        }
    }
    private val motionObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = updateMotionPolicy()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateMotionPolicy()
    }

    init {
        setWillNotDraw(false)
        // State has an accessible text label beside the hero; avoid a duplicate announcement.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        // A long-press gives the owner a concrete reason when animation looks frozen.
        setOnLongClickListener {
            showAppearanceChooser()
            true
        }
    }

    /** Existing Director calls from TTS or UI; bounded reducer retains no spoken text. */
    override fun onAvatarEvent(event: AvatarEvent) {
        pendingAvatarTraceFlags.updateAndGet { it or AvatarFrameReceipt.AVATAR_EVENT }
        // Continuous sensor-driven movement obeys the same visibility/reduced-motion policy.
        // Inactive views must not accept a late camera sample after their clear boundary.
        if (event is AvatarEvent.GazeObserved && !canAnimateFrame()) return
        synchronized(avatarPose) { avatarPose.apply(event) }
        // Speech levels are drawn by the existing loop. State/end events also refresh static views.
        if (event !is AvatarEvent.AudioLevel && event !is AvatarEvent.VisemeCue &&
            avatarInvalidationPending.compareAndSet(false, true)) mainHandler.post(avatarInvalidation)
    }

    /** The cockpit adapter adds effects to this existing surface; state, audio and 3D ownership remain here. */
    fun setCockpitSequence(sequence: JarvisSequenceView?) {
        if (cockpitSequence === sequence) return
        require(sequence?.parent == null) { "Cockpit sequence already belongs to a view" }
        clearMemoryScene()
        cockpitSequence?.let { old -> old.onMemoryScene = null; old.clear(); removeView(old) }
        cockpitSequence = sequence
        sequence?.let {
            it.onMemoryScene = { now ->
                if (canAnimateFrame() && memorySceneHostReady?.invoke() == true) {
                    memoryScene.start(now)
                    memorySceneInvalidation?.invoke()
                    invalidate()
                    true
                } else false
            }
            it.setMotionAllowed(canAnimateFrame())
            val budget = renderBudget()
            it.setFrameBudget(budget.frameDelayMillis, budget.reducedBloom)
            it.setCardsDocked(workWindowActive)
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            updateOrbTarget(width, height)
        }
    }

    /** Ref5 HEAD and ORB in local pixels. Null restores the existing phone/legacy composition. */
    private var headAnchorX = .5f
    private var headCloseUp = 1f

    private var headKeepOut: at.heinz.deviceai.avatar3d.AvatarFraming.KeepOut? = null
    private var auraFocus: AvatarGlbContract.Focus? = null
    private var auraWidth = -1f
    private var auraHeight = -1f
    private var auraAnchor = Float.NaN
    private var auraCloseUp = Float.NaN
    private var auraKeepOut: at.heinz.deviceai.avatar3d.AvatarFraming.KeepOut? = null
    private var auraCameraProfile = AvatarCameraFraming.Profile.LEGACY
    private var auraScale = 1f
    private var auraAnchorEffective = .5f
    private val cameraMotion = AvatarCameraMotion()
    private val entranceMotion = AvatarEntranceMotion()
    private var workWindowActive = false
    private var workWindowPulseStartedAtMs = -1L
    private var workWindowAccentArgb: Int? = null

    /** Visual-only scene light; owning window still controls permissions and receipts. */
    fun setWorkWindowAccent(accentArgb: Int?) {
        if (workWindowAccentArgb == accentArgb) return
        workWindowAccentArgb = accentArgb
        invalidate()
    }
    private var cameraFraming: AvatarCameraFraming? = null
    private var cameraWindow: AvatarFraming.Window? = null
    private var cameraDirection = 0f
    private var cameraProfile = AvatarCameraFraming.Profile.LEGACY
    private var cameraProfileBlendFrom: AvatarFraming.Window? = null
    private var cameraProfileBlendStartedAtMs = -1L

    /** Actual accepted work-window presentation only. */
    fun setWorkWindowCamera(
        active: Boolean,
        direction: Float = 0f,
        immediate: Boolean = false,
        profile: AvatarCameraFraming.Profile = AvatarCameraFraming.Profile.LEGACY
    ) {
        require(direction.isFinite() && direction in -1f..1f)
        // The face and the orb stay free while a window works: the sequence cards dock into its status strip.
        val firstWindowFrame = active && !workWindowActive
        workWindowActive = active
        if (!active) workWindowAccentArgb = null
        val now = SystemClock.uptimeMillis()
        if (firstWindowFrame) workWindowPulseStartedAtMs = now
        if (!active) workWindowPulseStartedAtMs = -1L
        if (active && profile != cameraProfile) {
            // Freeze the exact currently visible projection before changing profile. The next frames blend
            // from that safe window into the new safe target while the existing camera motion keeps running.
            updateAuraFraming()
            cameraProfileBlendFrom = cameraWindow
            cameraProfileBlendStartedAtMs = now
            cameraProfile = profile
        }
        cockpitSequence?.setCardsDocked(active)
        if (immediate || !canAnimateFrame() || !hasWindowFocus() || !headEnabled || cockpitHeadBounds == null) {
            resetCamera()
        } else {
            if (active) cameraDirection = direction
            cameraMotion.setWorkActive(active, now)
        }
        invalidate()
    }

    private fun resetCamera() {
        cameraMotion.reset()
        cameraDirection = 0f
        cameraProfileBlendFrom = null
        cameraProfileBlendStartedAtMs = -1L
        cameraWindow = cameraFraming?.rest
        cameraWindow?.let { auraScale = it.headScale.toFloat(); auraAnchorEffective = it.anchorX.toFloat() }
    }

    internal fun cameraProgress(): Float = cameraMotion.progress

    internal fun playAvatarEntrance(step: at.heinz.deviceai.cockpit.TimelineStep): Boolean {
        val cue = step.cue
        if (cue != at.heinz.deviceai.cockpit.Cue.AVATAR_FROM_DEPTH &&
            cue != at.heinz.deviceai.cockpit.Cue.AVATAR_SHRINK) return false
        if (!headEnabled || !canAnimateFrame()) {
            resetAvatarEntrance()
            return true
        }
        val now = SystemClock.uptimeMillis()
        if (cue == at.heinz.deviceai.cockpit.Cue.AVATAR_FROM_DEPTH) entranceMotion.start(now)
        else entranceMotion.shrink(now)
        invalidate()
        return true
    }

    internal fun resetAvatarEntrance() {
        entranceMotion.reset()
        invalidate()
    }

    internal fun entranceScale(): Float = entranceMotion.scale

    private fun transformHeadEntrance(canvas: Canvas, units: Float = 1f) {
        val scale = entranceMotion.scale
        if (scale == 1f) return
        canvas.clipRect(headDrawBounds.left / units, headDrawBounds.top / units,
            headDrawBounds.right / units, headDrawBounds.bottom / units)
        canvas.scale(scale, scale, headDrawBounds.centerX() / units, headDrawBounds.centerY() / units)
    }

    /**
     * Head size and effective anchor of the live 3D window (the calibrated values for the 2D head), so the aura
     * rings stay around the fitted head. Same [at.heinz.deviceai.avatar3d.AvatarFraming] window as the renderer;
     * Geometry is cached between layout changes.
     */
    private fun updateAuraFraming() {
        val focus = if (head3dEnabled && (native3dDrawn || native3d != null)) assetMetadata?.focus else null
        val w = headDrawBounds.width(); val h = headDrawBounds.height()
        if (focus == null || w <= 0f || h <= 0f) {
            auraFocus = null; auraScale = 1f; auraAnchorEffective = headAnchorX
            cameraFraming = null; cameraWindow = null
            return
        }
        if (focus !== auraFocus || w != auraWidth || h != auraHeight || headAnchorX != auraAnchor ||
            headCloseUp != auraCloseUp || headKeepOut != auraKeepOut || cameraProfile != auraCameraProfile) {
            auraFocus = focus; auraWidth = w; auraHeight = h; auraAnchor = headAnchorX; auraCloseUp = headCloseUp
            auraKeepOut = headKeepOut
            auraCameraProfile = cameraProfile
            cameraFraming = AvatarCameraFraming(focus, w.toDouble() / h,
                headAnchorX.toDouble(), headCloseUp.toDouble(), headKeepOut, cameraProfile)
        }
        val targetWindow = requireNotNull(cameraFraming).sample(cameraMotion.progress, cameraDirection).let {
            if (layeredCockpit) at.heinz.deviceai.avatar3d.AvatarFraming.pullBack(it,
                at.heinz.deviceai.ui.CinematicWorkstationLayout.AVATAR_SCALE.toDouble()) else it
        }
        val fromWindow = cameraProfileBlendFrom
        cameraWindow = if (fromWindow != null && cameraProfileBlendStartedAtMs >= 0L) {
            val raw = ((SystemClock.uptimeMillis() - cameraProfileBlendStartedAtMs) / 260f).coerceIn(0f, 1f)
            if (raw >= 1f) {
                cameraProfileBlendFrom = null
                cameraProfileBlendStartedAtMs = -1L
                targetWindow
            } else {
                val smooth = raw * raw * (3f - 2f * raw)
                AvatarCameraFraming.blend(fromWindow, targetWindow, smooth)
            }
        } else targetWindow
        val window = requireNotNull(cameraWindow)
        auraScale = window.headScale.toFloat(); auraAnchorEffective = window.anchorX.toFloat()
    }

    /**
     * Cockpit framing of the live head. [keepOut]: stage-normalized area (the dialogue card) the posed face must not
     * enter; the 3D window may then move the head towards the stage centre before it shrinks it.
     */
    fun setHeadFraming(anchorX: Float, closeUp: Float, keepOut: at.heinz.deviceai.avatar3d.AvatarFraming.KeepOut? = null) {
        require(anchorX.isFinite() && anchorX in .2f..1f && closeUp.isFinite() && closeUp in .4f..1f)
        if (headAnchorX == anchorX && headCloseUp == closeUp && headKeepOut == keepOut) return
        headAnchorX = anchorX
        headCloseUp = closeUp
        headKeepOut = keepOut
        resetCamera()
        invalidate()
    }

    private var layeredCockpit = false

    fun setCockpitRegions(head: Rect?, orb: Rect?, layered: Boolean = false) {
        require((head == null) == (orb == null)) { "Head and orb bounds must be supplied together" }
        if (head != null && orb != null) {
            require(!head.isEmpty && !orb.isEmpty && head.left >= 0 && head.top >= 0 && orb.left >= 0 && orb.top >= 0)
            require(layered || !Rect.intersects(head, orb)) { "Head and orb drawing regions must not overlap" }
        }
        if (cockpitHeadBounds == head && cockpitOrbBounds == orb && layeredCockpit == layered) return
        layeredCockpit = layered
        resetCamera()
        cockpitHeadBounds = head?.let(::Rect)
        cockpitOrbBounds = orb?.let(::Rect)
        updateOrbTarget(width, height)
        requestLayout()
        invalidate()
    }

    private fun resolveHeadBounds(widthPx: Int, heightPx: Int) {
        val explicit = cockpitHeadBounds
        if (explicit != null) headDrawBounds.set(explicit) else {
            val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
            headDrawBounds.set(0f, 0f, widthPx * if (cockpit) .66f else 1f,
                heightPx * if (cockpit) .92f else 1f)
        }
    }

    private fun resolveOrbBounds(widthPx: Int, heightPx: Int) {
        val explicit = cockpitOrbBounds
        if (!headEnabled) orbDrawBounds.set(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        else if (explicit != null) orbDrawBounds.set(explicit)
        else {
            val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
            val x = widthPx * if (cockpit) .57f else .72f
            val y = heightPx * if (cockpit) .09f else .30f
            orbDrawBounds.set(x, y, x + widthPx * if (cockpit) .43f else .28f,
                y + heightPx * if (cockpit) .76f else .40f)
        }
    }

    private fun updateOrbTarget(widthPx: Int, heightPx: Int) {
        val sequence = cockpitSequence ?: return
        if (widthPx <= 0 || heightPx <= 0) return
        resolveOrbBounds(widthPx, heightPx)
        // The sequence keeps the real head and orb free (owner review P0); without explicit regions nothing is protected.
        val d = resources.displayMetrics.density
        resolveHeadBounds(widthPx, heightPx)
        sequence.setHeadRegion(headDrawBounds.left / d, headDrawBounds.top / d,
            headDrawBounds.right / d, headDrawBounds.bottom / d)
        sequence.effects.setProtectedAreas(cockpitHeadBounds?.let { floatArrayOf(it.left / d, it.top / d, it.right / d, it.bottom / d) },
            cockpitOrbBounds?.let { floatArrayOf(it.left / d, it.top / d, it.right / d, it.bottom / d) })
        sequence.effects.orbTargetX = (orbDrawBounds.centerX() / widthPx).coerceIn(0f, 1f)
        sequence.effects.orbTargetY = ((orbDrawBounds.top + orbDrawBounds.height() * renderer.geometry.centerY) / heightPx).coerceIn(0f, 1f)
    }

    /** Explicit local appearance preference; the original orb remains selectable. */
    fun setNativeHeadEnabled(enabled: Boolean) {
        headEnabled = enabled
        head3dEnabled = false
        appearancePrefs.edit().putBoolean("native-head", enabled).putBoolean("native-head-3d", false).apply()
        release3d(clearAsset = true)
        updateMotionPolicy()
    }

    fun isNativeHeadEnabled(): Boolean = headEnabled

    fun isNative3dEnabled(): Boolean = head3dEnabled

    /** Appearance only; preserves the Director, brain, TTS, and shared pose reducer. */
    fun setNative3dEnabled(enabled: Boolean) {
        head3dEnabled = enabled
        if (enabled) headEnabled = true
        native3dFailed = false
        appearancePrefs.edit().putBoolean("native-head", headEnabled).putBoolean("native-head-3d", enabled).apply()
        if (!enabled) release3d(clearAsset = true)
        updateMotionPolicy()
    }

    private fun canDisplay3d() = head3dEnabled && foregroundActive && isAttachedToWindow && isShown &&
        windowVisibility == VISIBLE

    private fun update3d() {
        if (!canDisplay3d()) { release3d(clearAsset = !head3dEnabled); return }
        if (native3dFailed) return
        if (assetBytes == null) {
            if (assetLoading) return
            assetLoading = true
            val generation = assetGeneration
            // Packaged local bytes only; no model, owner data, permission or network access.
            Thread({
                val candidate = runCatching {
                    val bytes = context.assets.open(AvatarGlbContract.ASSET_PATH).use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val read = input.read(chunk); if (read < 0) break
                            require(out.size() + read <= AvatarGlbContract.MAX_BYTES)
                            out.write(chunk, 0, read)
                        }
                        out.toByteArray()
                    }
                    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString("") { "%02x".format(it) }
                    require(digest == AvatarGlbContract.ASSET_SHA256) { "Packaged avatar revision mismatch" }
                    val documentBytes = runCatching {
                        val contract = at.heinz.deviceai.avatar3d.FilamentDocumentStage
                        val document = context.assets.open(contract.ASSET_PATH).use { stream ->
                            val out = java.io.ByteArrayOutputStream()
                            val part = ByteArray(8192)
                            while (true) {
                                val count = stream.read(part)
                                if (count < 0) break
                                require(out.size() + count <= contract.MAX_BYTES)
                                out.write(part, 0, count)
                            }
                            out.toByteArray()
                        }
                        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(document)
                            .joinToString("") { "%02x".format(it) }
                        require(sha == contract.ASSET_SHA256)
                        document
                    }.getOrNull()
                    Triple(bytes, AvatarGlbContract.validate(bytes), documentBytes)
                }
                mainHandler.post {
                    assetLoading = false
                    if (generation != assetGeneration) { if (canDisplay3d()) update3d(); return@post }
                    candidate.onSuccess { (bytes, metadata, documentBytes) ->
                        assetBytes = bytes
                        assetMetadata = metadata
                        documentStageBytes = documentBytes
                        update3d()
                        invalidate()
                    }
                        .onFailure { fail3d() }
                }
            }, "continuum-avatar-asset").start()
            return
        }
        if (nativeTexture == null) {
            val texture = TextureView(context).apply {
                isOpaque = false
                alpha = 0f
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            nativeTexture = texture
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    nativeSurface = Surface(surface)
                    ensure3dRenderer(); invalidate()
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { invalidate() }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    documentStageBinding?.report(false)
                    native3d?.close(); native3d = null
                    nativeSurface?.release(); nativeSurface = null
                    native3dDrawn = false
                    return true
                }
            }
            // The sequence is an overlay, so a newly created 3D surface must remain below it.
            addView(texture, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        ensure3dRenderer()
    }

    private fun ensure3dRenderer() {
        if (native3d != null || native3dFailed || !canDisplay3d()) return
        val surface = nativeSurface ?: return
        if (!surface.isValid) return
        val bytes = assetBytes ?: return
        val metadata = assetMetadata ?: return
        runCatching { FilamentAvatarRenderer.create(surface, bytes, metadata) }
            .onSuccess { native3d = it; it.setQuality(renderBudget().reducedBloom) }.onFailure { fail3d() }
    }

    /** Presentation only; no document access. */
    private data class CockpitBinding(
        val surface: Surface,
        val bytes: ByteArray,
        val ready: (Boolean) -> Unit,
        var frame: at.heinz.deviceai.avatar3d.CockpitStageFrame? = null,
        var width: Int = 0,
        var height: Int = 0,
        var reported: Boolean = false,
        var failed: Boolean = false
    ) {
        fun report(value: Boolean) {
            if (reported != value) { reported = value; ready(value) }
        }
    }
    private var cockpitBinding: CockpitBinding? = null

    internal fun attachCockpitStage(surface: Surface, ready: (Boolean) -> Unit) {
        detachCockpitStage()
        val bytes = runCatching {
            val contract = at.heinz.deviceai.avatar3d.FilamentCockpitStage
            val bytes = context.assets.open(contract.ASSET_PATH).use {
                it.readBytes().also { bytes -> require(bytes.size <= contract.MAX_BYTES) }
            }
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(sha == contract.ASSET_SHA256)
            bytes
        }.getOrNull() ?: return
        cockpitBinding = CockpitBinding(surface, bytes, ready)
        invalidate()
    }

    internal fun presentCockpitStage(frame: at.heinz.deviceai.avatar3d.CockpitStageFrame,
                                     bufferWidth: Int, bufferHeight: Int) {
        val binding = cockpitBinding ?: return
        if (!frame.valid()) return
        binding.frame = frame
        binding.width = bufferWidth
        binding.height = bufferHeight
        invalidate()
    }

    internal fun detachCockpitStage() {
        cockpitBinding?.report(false)
        cockpitBinding = null
        native3d?.clearCockpitStage()
    }

    private fun renderCockpitStage() {
        val binding = cockpitBinding ?: return
        if (!canDisplay3d() || binding.failed) { binding.report(false); return }
        val frame = binding.frame ?: return
        val native = native3d ?: return
        runCatching {
            native.renderCockpitStage(binding.surface, binding.bytes, frame, binding.width, binding.height)
        }.onSuccess { if (it) binding.report(true) }.onFailure {
            binding.failed = true
            binding.report(false)
            native.clearCockpitStage()
        }
    }

    internal fun attachDocumentStage(id: String, surface: Surface, ready: (Boolean) -> Unit) {
        documentStageBinding?.report(false)
        native3d?.clearDocumentStage()
        documentStageBinding = DocumentStageBinding(id, surface, ready)
    }

    internal fun presentDocumentStage(frame: at.heinz.deviceai.avatar3d.DocumentStageFrame,
                                     page: android.graphics.Bitmap?, bufferWidth: Int, bufferHeight: Int) {
        val binding = documentStageBinding?.takeIf { it.id == frame.windowId } ?: return
        if (!frame.valid()) return
        val changedPage = binding.page !== page
        val changedBuffer = binding.bufferWidth != bufferWidth || binding.bufferHeight != bufferHeight
        val changed = changedPage || changedBuffer || binding.frame != frame
        binding.frame = frame
        binding.page = page
        binding.bufferWidth = bufferWidth
        binding.bufferHeight = bufferHeight
        if (changedPage || changedBuffer || !frame.pageRectValid || frame.opacity <= 0f ||
            page == null || page.isRecycled) binding.report(false)
        if (changed && canDisplay3d()) invalidate()
    }

    internal fun detachDocumentStage(id: String) {
        val binding = documentStageBinding?.takeIf { it.id == id } ?: return
        binding.report(false)
        binding.page = null
        binding.frame = null
        documentStageBinding = null
        native3d?.clearDocumentStage()
    }

    private fun renderDocumentStage() {
        val binding = documentStageBinding ?: return
        val budget = renderBudget()
        val admitted = canDisplay3d() && budget.animate &&
            budget.tier != at.heinz.deviceai.ui.JarvisRenderQuality.Tier.LOW &&
            budget.tier != at.heinz.deviceai.ui.JarvisRenderQuality.Tier.SUSPENDED &&
            at.heinz.deviceai.ui.JarvisCinematicSettings.enabled(context)
        if (!admitted || binding.failed) {
            binding.report(false)
            native3d?.clearDocumentStage()
            return
        }
        val frame = binding.frame ?: return
        if (!frame.pageRectValid || frame.opacity <= 0f || binding.page?.isRecycled != false) {
            binding.report(false)
            return
        }
        val bytes = documentStageBytes ?: return
        val native = native3d ?: return
        if (binding.bufferWidth <= 0 || binding.bufferHeight <= 0) return
        runCatching {
            native.renderDocumentStage(binding.surface, bytes, frame, binding.page,
                binding.bufferWidth, binding.bufferHeight)
        }.onSuccess { rendered -> if (rendered) binding.report(frame.pageRectValid && frame.opacity > 0f &&
            binding.page?.isRecycled == false) }
            .onFailure {
                binding.failed = true
                binding.report(false)
                native.clearDocumentStage()
            }
    }

    private fun fail3d() {
        native3dFailed = true
        release3d(clearAsset = true)
        if (isAttachedToWindow && foregroundActive) Toast.makeText(context,
            "3D-Kopf derzeit nicht verfügbar", Toast.LENGTH_LONG).show()
        invalidate()
    }

    private fun release3d(clearAsset: Boolean) {
        cockpitBinding?.report(false)
        documentStageBinding?.report(false)
        native3d?.close(); native3d = null; native3dDrawn = false
        nativeTexture?.let { texture -> texture.surfaceTextureListener = null; removeView(texture) }
        nativeTexture = null
        nativeSurface?.release(); nativeSurface = null
        if (clearAsset) { assetGeneration++; assetBytes = null; assetMetadata = null; documentStageBytes = null }
    }

    fun showAppearanceChooser() {
        val presentation = context.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE)
        val selected = if (presentation.getBoolean("compact", false)) 3 else if (head3dEnabled) 2 else if (headEnabled) 1 else 0
        AlertDialog.Builder(context).setTitle("Continuum · Darstellung")
            .setSingleChoiceItems(arrayOf("Kugel · Original", "Kopf · Native 2D-Vorschau", "Kopf · Native 3D-Vorschau", "Kompakt · Kugel"), selected) { dialog, item ->
                val choice = presentation.edit().putBoolean("compact", item == 3)
                if (headEnabled) choice.putBoolean("avatar-3d", head3dEnabled)
                if (item == 1 || item == 2) choice.putBoolean("avatar-3d", item == 2)
                if (item == 2) setNative3dEnabled(true) else setNativeHeadEnabled(item == 1)
                choice.apply()
                dialog.dismiss()
            }
            .setPositiveButton("15s Animationstest") { _, _ -> startAvatarProof() }
            .setNeutralButton("Film-Effekte") { _, _ ->
                // Code52 is a Cinema-only release: an alternate master OFF switch in the
                // avatar chooser would silently disable all 15 effects after migration.
                AlertDialog.Builder(context).setTitle("Arbeitsfenster · Cinema 52")
                    .setMessage("Cinema-Glas, Fensterflug, Avatar-Licht und Kugelkopplung sind " +
                        "im Cinema-Release standardmäßig aktiviert. Die Android-Einstellungen " +
                        "für reduzierte Bewegung und die thermischen Schutzgrenzen gelten weiterhin.")
                    .setNeutralButton("Bewegung prüfen") { _, _ ->
                        Toast.makeText(context, motionDiagnostics(), Toast.LENGTH_LONG).show()
                    }
                    .setPositiveButton("Schließen", null).show()
            }
            .setNegativeButton("Schließen", null).show()
    }

    private fun startAvatarProof() {
        if (!canAnimateFrame()) {
            Toast.makeText(context, motionDiagnostics(), Toast.LENGTH_LONG).show()
            return
        }
        cancelAvatarProof()
        setNative3dEnabled(true)
        avatarProofStartCallbacks = visibleFrameCount
        avatarProofStartDraws = drawnFrameCount
        avatarProofStartTraceFrame = avatarTrace.frameNumber()
        avatarProofStartedAt = SystemClock.uptimeMillis()
        mainHandler.postDelayed(avatarProofFinish, DeterministicAvatarProofRunner.DURATION_MS + 100L)
        Toast.makeText(context, "15s Avatar-Proof: Idle · Blink · Blick · Mund · Denken · Karte", Toast.LENGTH_LONG).show()
        invalidate()
        scheduleFrame()
    }

    private fun finishAvatarProof() {
        if (avatarProofStartedAt == Long.MIN_VALUE) return
        avatarProofStartedAt = Long.MIN_VALUE
        val summary = AvatarProofCompletion.summarize(
            (visibleFrameCount - avatarProofStartCallbacks).coerceAtLeast(0L),
            (drawnFrameCount - avatarProofStartDraws).coerceAtLeast(0L),
            avatarTrace.diagnoseSince(avatarProofStartTraceFrame)
        )
        invalidate()
        val activity = context as? android.app.Activity ?: return
        if (!foregroundActive || !isAttachedToWindow || !isShown || activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(context)
            .setTitle(if (summary.live) "Avatar-Proof · LIVE" else "Avatar-Proof · Fehler gefunden")
            .setMessage(
                "15-Sekunden-Proof abgeschlossen.\n" +
                    "Animations-Callbacks: ${summary.callbackDelta}\n" +
                    "Wirkliche Canvas-Zeichnungen: ${summary.drawDelta}\n\n" +
                    summary.verdict +
                    "\n\nKein Modell, Web, ASR oder TTS wurde für diesen Test verwendet."
            )
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun cancelAvatarProof() {
        avatarProofStartedAt = Long.MIN_VALUE
        mainHandler.removeCallbacks(avatarProofFinish)
    }

    private fun avatarProofFrame(nowMillis: Long): AvatarProofFrame? {
        val started = avatarProofStartedAt
        if (started == Long.MIN_VALUE) return null
        val elapsed = nowMillis - started
        if (elapsed < 0L || elapsed >= DeterministicAvatarProofRunner.DURATION_MS) return null
        return DeterministicAvatarProofRunner.frame(elapsed)
    }

    /** Call on the UI thread. State changes crossfade over the existing orb. */
    fun setState(state: State) {
        if (state == currentState) return
        currentState = state
        pendingAudioLevel = 0f
        pendingAudioMs = 0L
        consumedAudioMs = 0L
        audio.reset()
        audioEnergy = 0f
        if (!canAnimateFrame()) settleState()
        renderBudget().let { cockpitSequence?.setFrameBudget(it.frameDelayMillis, it.reducedBloom) }
        invalidate()
    }

    /** Safe from a TTS callback thread. Supply actual PCM RMS in the range 0..1. */
    fun setAudioLevel(level: Float) {
        if (currentState != State.SPEAKING) return
        pendingAudioLevel = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        pendingAudioMs = SystemClock.uptimeMillis()
    }

    /** Explicit Activity lifecycle. Keeps phase continuity without background callbacks. */
    fun setForegroundActive(active: Boolean) {
        if (foregroundActive == active) return
        foregroundActive = active
        if (!active) cancelAvatarProof()
        if (!active) synchronized(avatarPose) {
            avatarPose.clearSpeech()
            avatarPose.clearObservedGaze(SystemClock.elapsedRealtime())
        }
        updateMotionPolicy()
    }

    /** Explicit reduced motion preference; system animator scale is also respected. */
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        updateMotionPolicy()
    }

    /** UI thread, explicit owner-work scope only. Hidden/inactive views cannot queue arrivals. */
    fun setTaskEffectScope(scope: String?) {
        if (scope != null && canAnimateFrame()) taskEffects.setScope(scope) else clearTaskEffects()
        invalidate()
    }

    /** Capture uptime at the actual work boundary, before posting to the UI. No default timestamp. */
    fun onTaskEffect(scope: String, event: ContinuumTaskEffects.Event, occurredAtMillis: Long): Boolean {
        if (!canAnimateFrame()) { clearTaskEffects(); return false }
        if (occurredAtMillis <= taskEffectsNotBeforeMs) return false
        return taskEffects.accept(scope,event,occurredAtMillis,SystemClock.uptimeMillis()).also {
            if(it) invalidate()
        }
    }

    private fun clearTaskEffects() {
        taskEffects.clear()
        clearMemoryScene()
        cockpitSequence?.clear()
        resetAvatarEntrance()
        taskEffectsNotBeforeMs = SystemClock.uptimeMillis()
    }

    private fun memorySceneTime() = cockpitSequence?.clock?.invoke() ?: SystemClock.uptimeMillis()

    internal fun clearMemoryScene() {
        val active = memoryScene.isActive
        memoryScene.clear()
        memorySceneOrbBounds.setEmpty()
        if (active) { memorySceneInvalidation?.invoke(); invalidate() }
    }

    internal fun drawMemoryScene(canvas: Canvas, widthPx: Int, heightPx: Int, viewportTopPx: Int = 0) {
        if (!canAnimateFrame() || memorySceneHostReady?.invoke() != true) {
            clearMemoryScene(); return
        }
        val scene = memoryScene.sample(memorySceneTime()) ?: run { clearMemoryScene(); return }
        val d = resources.displayMetrics.density
        val w = widthPx / d; val h = heightPx / d
        if (w <= 0f || h <= 0f) { clearMemoryScene(); return }
        val saved = canvas.save()
        try {
        canvas.scale(d, d)
        painter.begin(canvas)
        painter.fillRect(0f, 0f, w, h, memoryBackdrop, scene.opacity)
        if (scene.elapsedMs < 3_000L) {
            memoryTunnel.render(painter, w, h, scene.elapsedMs / 1000f, !renderBudget().reducedBloom,
                (1f - scene.orbProgress) * scene.opacity)
        }
        if (scene.orbProgress > 0f) {
            resolveOrbBounds(width, height)
            val grow = scene.orbProgress * scene.orbProgress * (3f - 2f * scene.orbProgress)
            val back = scene.returnProgress * scene.returnProgress * (3f - 2f * scene.returnProgress)
            val largeW = minOf(w * .86f, h * 1.15f) * (.12f + .88f * grow)
            val largeH = minOf(h * .92f, w * 1.04f) * (.12f + .88f * grow)
            val startL = w * .52f - largeW / 2f
            val startT = h * .43f - largeH * renderer.geometry.centerY
            fun mix(a: Float, b: Float) = a + (b - a) * back
            val l = mix(startL, (left + orbDrawBounds.left) / d)
            val t = mix(startT, (top + orbDrawBounds.top - viewportTopPx) / d)
            val ow = mix(largeW, orbDrawBounds.width() / d)
            val oh = mix(largeH, orbDrawBounds.height() / d)
            memorySceneOrbBounds.set(l * d, t * d + viewportTopPx, (l + ow) * d, (t + oh) * d + viewportTopPx)
            val orb = if (grow < 1f) canvas.saveLayerAlpha(l, t, l + ow, t + oh, (grow * 255f).toInt())
                else canvas.save()
            val originalEnergy = renderFrame.energy
            try {
            canvas.clipRect(l, t, l + ow, t + oh)
            canvas.translate(l, t)
            renderer.layout(ow, oh)
            val now = memorySceneTime()
            renderFrame.energy = maxOf(originalEnergy, cockpitSequence?.effects?.orbPulseLevel(now) ?: 0f,
                cockpitSequence?.effects?.recallBrightness(now) ?: 0f)
            renderer.draw(painter, renderFrame, transparentBackdrop = true)
            } finally {
                renderFrame.energy = originalEnergy
                canvas.restoreToCount(orb)
            }
        }
        } finally { canvas.restoreToCount(saved) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!observersRegistered) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            context.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            if (Build.VERSION.SDK_INT >= 29) observeThermalPressure()
            observersRegistered = true
        }
        updateMotionPolicy()
    }

    override fun onDetachedFromWindow() {
        cancelAvatarProof()
        resetCamera()
        resetAvatarEntrance()
        cockpitSequence?.setMotionAllowed(false)
        clearTaskEffects()
        // A fold/navigation reparent destroys the surface, but retains validated immutable asset bytes.
        release3d(clearAsset = false)
        mainHandler.removeCallbacks(avatarInvalidation)
        avatarInvalidationPending.set(false)
        synchronized(avatarPose) {
            avatarPose.clearSpeech()
            avatarPose.clearObservedGaze(SystemClock.elapsedRealtime())
        }
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        audio.reset()
        if (observersRegistered) {
            context.contentResolver.unregisterContentObserver(motionObserver)
            context.unregisterReceiver(powerReceiver)
            if (Build.VERSION.SDK_INT >= 29) releaseThermalObserver()
            observersRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) cancelAvatarProof()
        updateMotionPolicy()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) cancelAvatarProof()
        if (isAttachedToWindow) updateMotionPolicy()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) cancelAvatarProof()
        if (isAttachedToWindow) updateMotionPolicy()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { resetCamera(); resetAvatarEntrance() }
        // Re-evaluate scheduling after Samsung Fold window/cover transitions.
        if (isAttachedToWindow) updateMotionPolicy()
    }

    /** Actual View visibility, not a callback-cached flag that can go stale. */
    private fun canAnimateFrame() =
        foregroundActive && motionAllowed && isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun renderBudget(): at.heinz.deviceai.ui.JarvisRenderQuality.Budget {
        val cinemaOptIn = at.heinz.deviceai.ui.JarvisCinematicSettings.cinemaOptIn(context)
        val exp15 = at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
            at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_15_FRAME_BUDGET)
        val base = at.heinz.deviceai.ui.JarvisRenderQuality.adaptiveAnimation(
            foregroundActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE,
            powerManager.isPowerSaveMode, thermalStatus,
            !animationsEnabled || !ValueAnimator.areAnimatorsEnabled(),
            currentState == State.THINKING || currentState == State.RESEARCH || currentState == State.LEARNING,
            cinemaOptIn, exp15)
        val phase = when(currentState) {
            State.READY -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.IDLE
            State.LISTENING -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.LISTENING
            State.THINKING -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.THINKING
            State.SPEAKING -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.SPEAKING
            State.RESEARCH -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.RESEARCH
            State.LEARNING -> at.heinz.deviceai.ui.JarvisComputePhasePolicy.Phase.LEARNING
        }
        val phased = at.heinz.deviceai.ui.JarvisComputePhasePolicy.apply(
            base, phase, diagnosticProofActive = avatarProofStartedAt != Long.MIN_VALUE)
        // When speech is already playing the heavy LLM phase is normally over. Keep
        // essential lip/eye motion readable even in LOW, while decoration stays off.
        val delay = at.heinz.deviceai.ui.AvatarEssentialMotionPolicy.frameDelayMillis(
            phased, currentState == State.SPEAKING && foregroundActive)
        return if (delay != phased.frameDelayMillis)
            phased.copy(frameDelayMillis = delay, particleCount = 0)
        else phased
    }

    internal fun presentationRenderBudget() = renderBudget()

    internal fun observePresentationBudget(listener: () -> Unit): AutoCloseable {
        presentationBudgetObservers.add(listener)
        return AutoCloseable { presentationBudgetObservers.remove(listener) }
    }

    /** No private content. Owner can inspect this by long-pressing the hologram. */
    /** Read only on the Android UI thread. Compares scheduled callbacks and real View draws. */
    data class MotionSample(val callbackCount: Long, val drawCount: Long, val phase: Float, val reason: String)

    fun sampleMotion(): MotionSample = MotionSample(visibleFrameCount, drawnFrameCount, phase, motionDiagnostics())

    internal fun motionDiagnostics(): String = when {
        !animationsEnabled -> "Avatar: In-App-Animation ausgeschaltet"
        !ValueAnimator.areAnimatorsEnabled() -> "Avatar: Android-Animationen ausgeschaltet. Bedienungshilfen/Entwickleroptionen prüfen."
        !foregroundActive -> "Avatar: Activity nicht im Vordergrund."
        !isAttachedToWindow -> "Avatar: View nicht am Fenster."
        !isShown || windowVisibility != VISIBLE -> "Avatar: View derzeit unsichtbar."
        thermalStatus >= 3 && headEnabled ->
            "Avatar: ESSENTIAL · Temperatur hoch · Dekoration pausiert · " + avatarTrace.diagnose()
        powerManager.isPowerSaveMode && headEnabled ->
            "Avatar: ESSENTIAL · Energiesparmodus · Dekoration pausiert · " + avatarTrace.diagnose()
        thermalStatus >= 3 -> "Hologramm: Temperatur hoch · Bewegung pausiert."
        powerManager.isPowerSaveMode -> "Hologramm: Energiesparmodus · Bewegung pausiert."
        else -> "Avatar: LIVE · Callbacks $visibleFrameCount · Canvas $drawnFrameCount · Phase ${"%.2f".format(java.util.Locale.ROOT, phase)} · ${avatarTrace.diagnose()}"
    }

    private fun updateMotionPolicy() {
        // Reduced-motion remains authoritative. Power-save/thermal pressure no longer
        // kills mouth/eyes/head completely; JarvisRenderQuality suppresses decoration.
        val policyBudget = renderBudget()
        motionAllowed = at.heinz.deviceai.ui.AvatarEssentialMotionPolicy.frameLoopAllowed(
            foreground = foregroundActive,
            animationsEnabled = animationsEnabled,
            systemAnimatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
            avatarEnabled = headEnabled,
            budget = policyBudget
        )
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        if (!canAnimateFrame()) {
            resetCamera()
            resetAvatarEntrance()
            clearTaskEffects()
            synchronized(avatarPose) { avatarPose.clearObservedGaze(SystemClock.elapsedRealtime()) }
            settleState()
            audio.reset()
            audioEnergy = 0f
            pendingAudioMs = 0L
            consumedAudioMs = 0L
        }
        val presentationBudget = renderBudget()
        cockpitSequence?.setMotionAllowed(
            at.heinz.deviceai.ui.AvatarEssentialMotionPolicy.decorativeAllowed(
                canAnimateFrame(), presentationBudget))
        presentationBudget.let { cockpitSequence?.setFrameBudget(it.frameDelayMillis, it.reducedBloom) }
        update3d()
        native3d?.setQuality(renderBudget().reducedBloom)
        presentationBudgetObservers.toList().forEach { it() }
        invalidate()
        scheduleFrame()
    }

    private fun settleState() {
        stateWeights.fill(0f)
        stateWeights[currentState.ordinal] = 1f
        energy = stateEnergy(currentState)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = resolveSize((412f * resources.displayMetrics.density).toInt(), widthMeasureSpec)
        val measuredHeight = resolveSize((307f * resources.displayMetrics.density).toInt(), heightMeasureSpec)
        // Keep the actual TextureView and Canvas composition on the same grid.
        resolveHeadBounds(measuredWidth, measuredHeight)
        val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
        (nativeTexture?.layoutParams as? LayoutParams)?.let {
            it.width = if (cockpitHeadBounds != null || cockpit) headDrawBounds.width().toInt() else (measuredWidth * .72f).toInt()
            it.height = if (cockpitHeadBounds != null || cockpit) headDrawBounds.height().toInt() else LayoutParams.MATCH_PARENT
            it.leftMargin = headDrawBounds.left.toInt(); it.topMargin = headDrawBounds.top.toInt()
        }
        updateOrbTarget(measuredWidth, measuredHeight)
        super.onMeasure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }

    /** Phone or Fold composition of the reference images (see ContinuumHologramRenderer.Geometry). */
    fun setGeometry(geometry: ContinuumHologramRenderer.Geometry) {
        val changed = renderer.geometry != geometry
        renderer.geometry = geometry
        if (geometry != ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT) setCockpitRegions(null, null)
        if (changed) requestLayout()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        if (width <= 0 || height <= 0) return
        val nowMillis = SystemClock.uptimeMillis()
        val receipt = avatarTrace.beginFrame(System.nanoTime()).also {
            it.stageFlags = AvatarFrameReceipt.FRAME_DRAW or pendingAvatarTraceFlags.getAndSet(0)
            it.stateOrdinal = currentState.ordinal
            if (head3dEnabled) it.stageFlags = it.stageFlags or AvatarFrameReceipt.HEAD_3D_SELECTED
            if (nativeSurface?.isValid == true) it.stageFlags = it.stageFlags or AvatarFrameReceipt.SURFACE_VALID
            val texture = nativeTexture
            if (texture != null && texture.isShown && texture.width > 0 && texture.height > 0 && texture.alpha > .01f)
                it.stageFlags = it.stageFlags or AvatarFrameReceipt.TEXTURE_VISIBLE
        }
        val proof = avatarProofFrame(nowMillis)
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        frameWeights.indices.forEach { frameWeights[it] = stateWeights[it] }
        renderFrame.phase = phase
        renderFrame.energy = proof?.orbEnergy ?: energy
        renderFrame.audio = if (proof != null) proof.pose.speakEnergy else audioEnergy
        val effects = cockpitSequence?.effects
        val windowPulse = if (workWindowActive && canAnimateFrame() &&
            at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
                at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_11_ORB_LINK) &&
            workWindowPulseStartedAtMs >= 0L)
            at.heinz.deviceai.ui.JarvisWindowOrbPulse.energy(nowMillis - workWindowPulseStartedAtMs)
            else 0f
        val coreEnergy = maxOf(renderFrame.energy, effects?.orbPulseLevel(nowMillis) ?: 0f,
            effects?.recallBrightness(nowMillis) ?: 0f, windowPulse)
        val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
        renderer.showBrand = at.heinz.deviceai.ui.AvatarPresentation.showOrbBrand(layeredCockpit, headEnabled)
        if (memoryScene.isActive && (memorySceneHostReady?.invoke() != true ||
                memoryScene.sample(memorySceneTime()) == null)) clearMemoryScene()
        resolveHeadBounds(width, height)
        updateOrbTarget(width, height)
        if (headEnabled && cockpitHeadBounds != null) {
            val t = if (canAnimateFrame() && renderBudget().animate) avatarSeconds else 0f
            val stageWidth = width / density
            val stageHeight = height / density
            val flowTop = if (cockpit) stageHeight * .62f else 0f
            val flowCanvas = canvas.save()
            if (cockpit) {
                canvas.clipRect(0f, flowTop, stageWidth, stageHeight)
                canvas.translate(0f, flowTop)
            }
            at.heinz.deviceai.ui.JarvisEnergyFlows.draw(painter, stageWidth, stageHeight - flowTop, t, energy)
            canvas.restoreToCount(flowCanvas)
            updateAuraFraming()
            val auraCanvas = canvas.save()
            transformHeadEntrance(canvas, density)
            at.heinz.deviceai.ui.JarvisHeadAura.draw(painter, at.heinz.deviceai.ui.JarvisShellLayout.Box(
                headDrawBounds.left / density, headDrawBounds.top / density, headDrawBounds.right / density,
                headDrawBounds.bottom / density), auraAnchorEffective, t,
                headScale = auraScale, activityEnergy = energy, speechLevel = audioEnergy,
                referenceMode = cockpit)
            canvas.restoreToCount(auraCanvas)
        }
        if (headEnabled) {
            val pose = proof?.pose ?: synchronized(avatarPose) { avatarPose.pose(SystemClock.elapsedRealtime(), canAnimateFrame()) }
            receipt.stageFlags = receipt.stageFlags or AvatarFrameReceipt.POSE_SAMPLED
            receipt.jawOpen = pose.jawOpen
            receipt.speakEnergy = pose.speakEnergy
            receipt.gazeX = pose.gazeX
            receipt.gazeY = pose.gazeY
            receipt.visemeOrdinal = pose.viseme.ordinal
            if (canDisplay3d() && native3d != null) {
                runCatching {
                    native3d!!.setQuality(renderBudget().reducedBloom)
                    native3d!!.setFraming(headAnchorX, headCloseUp, headKeepOut)
                    native3d!!.render(
                        pose, avatarSeconds, canAnimateFrame(), nativeTexture?.width ?: 0,
                        nativeTexture?.height ?: 0,
                        framing = if (cockpitHeadBounds != null) cameraWindow else null,
                        diagnosticBlink = proof?.blinkOverride,
                        receipt = receipt,
                        coreEnergy = coreEnergy,
                        cinematicLighting = cinematicPrefs.getBoolean(at.heinz.deviceai.ui.JarvisCinematicSettings.KEY, true),
                        windowAccentArgb = workWindowAccentArgb,
                        windowOpen = workWindowActive,
                        windowLightEnabled =
                            at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
                                at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT),
                        gazeBlinkEnabled = at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
                            at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_09_FOCUS_BLINK),
                        eyeNeckCouplingEnabled = !at.heinz.deviceai.ui.JarvisCinematicSettings.cinemaOptIn(context) ||
                            at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
                                at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_07_GAZE),
                        sentenceGesturesEnabled = !at.heinz.deviceai.ui.JarvisCinematicSettings.cinemaOptIn(context) ||
                            at.heinz.deviceai.ui.JarvisCinematicSettings.experimentEnabled(context,
                                at.heinz.deviceai.ui.JarvisCinematicSettings.Experiment.EXP_10_SENTENCE)
                    )
                }
                    .onSuccess { rendered -> if (rendered) { native3dDrawn = true; nativeTexture?.alpha = if (layeredCockpit) at.heinz.deviceai.ui.CinematicWorkstationLayout.AVATAR_OPACITY else 1f } }
                    .onFailure { fail3d() }
            }
            renderDocumentStage()
            renderCockpitStage()
            if (!memoryScene.isActive && (native3dDrawn || cockpit || cockpitOrbBounds != null)) {
                // Same original orb, same actual app state and speech frame; no second animation loop.
                resolveOrbBounds(width, height)
                val orbWidth = orbDrawBounds.width() / density
                val orbHeight = orbDrawBounds.height() / density
                val orbCanvas = canvas.save()
                canvas.translate(orbDrawBounds.left / density, orbDrawBounds.top / density)
                canvas.clipRect(0f, 0f, orbWidth, orbHeight)
                if (cockpitOrbBounds != null) {
                    // Reuse the exact original animated sphere/rings with no opaque tile.
                    val originalEnergy = renderFrame.energy
                    renderFrame.energy = coreEnergy
                    renderer.layout(orbWidth, orbHeight)
                    renderer.draw(painter, renderFrame, transparentBackdrop = true)
                    renderFrame.energy = originalEnergy
                } else {
                    renderer.layout(orbWidth, orbHeight)
                    renderer.draw(painter, renderFrame)
                }
                canvas.restoreToCount(orbCanvas)
            }
            if (cockpitHeadBounds != null) {
                val overlayCanvas = canvas.save()
                transformHeadEntrance(canvas, density)
                at.heinz.deviceai.ui.AvatarPresentation.drawBack(painter,
                    at.heinz.deviceai.ui.JarvisShellLayout.Box(
                        headDrawBounds.left / density, headDrawBounds.top / density,
                        headDrawBounds.right / density, headDrawBounds.bottom / density),
                    avatarSeconds, renderFrame.energy, layeredCockpit)
                canvas.restoreToCount(overlayCanvas)
            }
            if (!native3dDrawn) {
                val headCanvas = if (layeredCockpit) canvas.saveLayerAlpha(
                    headDrawBounds.left / density, headDrawBounds.top / density,
                    headDrawBounds.right / density, headDrawBounds.bottom / density,
                    (at.heinz.deviceai.ui.CinematicWorkstationLayout.AVATAR_OPACITY * 255f).toInt())
                    else canvas.save()
                transformHeadEntrance(canvas, density)
                canvas.translate(headDrawBounds.left / density, headDrawBounds.top / density)
                avatarRenderer.draw(painter, headDrawBounds.width() / density, headDrawBounds.height() / density,
                    pose, avatarSeconds, canAnimateFrame())
                canvas.restoreToCount(headCanvas)
            }
            if (!native3dDrawn && head3dEnabled) {
                val status = if (native3dFailed) "3D derzeit nicht verfügbar" else "3D wird initialisiert …"
                painter.drawText(status, headDrawBounds.centerX() / density,
                    (headDrawBounds.top + headDrawBounds.height() * .94f) / density,
                    TextStyle(FontFamily.TEXT, 400, 11f), avatarBrandFill, TextAlign.CENTER)
            }
            if (cockpit && cockpitHeadBounds == null) painter.drawText("JARVIS", headDrawBounds.centerX() / density,
                if (cockpitHeadBounds != null) (headDrawBounds.top + headDrawBounds.height() * .96f) / density else height / density * .96f,
                avatarBrandStyle, avatarBrandFill, TextAlign.CENTER)
        } else if (!memoryScene.isActive) {
            renderer.layout(width / density, height / density)
            renderer.draw(painter, renderFrame)
        }
        drawnFrameCount++
        canvas.restoreToCount(saved)
    }

    /** Above child TextureView composition; driven by the same visible-view invalidation clock. */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (width <= 0 || height <= 0) return
        if (headFadeActive()) {
            val density = resources.displayMetrics.density
            val streams = canvas.save()
            transformHeadEntrance(canvas)
            canvas.translate(headDrawBounds.left, headDrawBounds.top)
            canvas.scale(density, density)
            painter.begin(canvas)
            at.heinz.deviceai.ui.AvatarPresentation.drawFront(
                painter,
                at.heinz.deviceai.ui.JarvisShellLayout.Box(
                    0f, 0f, headDrawBounds.width() / density, headDrawBounds.height() / density),
                avatarSeconds, renderFrame.energy, layeredCockpit)
            canvas.restoreToCount(streams)
        }
        if (!canAnimateFrame() || !renderBudget().animate) return
        val density=resources.displayMetrics.density
        val saved=canvas.save()
        canvas.scale(density,density)
        canvas.clipRect(0f,0f,width/density,height/density)
        painter.begin(canvas)
        taskEffects.draw(painter,width/density,height/density,SystemClock.uptimeMillis())
        avatarProofFrame(SystemClock.uptimeMillis())?.takeIf { it.cardProgress > 0f }?.let { proof ->
            val w = width / density
            val h = height / density
            val p = proof.cardProgress.coerceIn(0f, 1f)
            val cardW = minOf(220f, w * .38f)
            val cardH = minOf(110f, h * .18f)
            val l = w * .5f - cardW * .5f
            val t = h * .70f - cardH * .5f
            val frame = at.heinz.deviceai.ui.JarvisMotion.Frame(
                dx = 0f, dy = 0f, scale = .58f + .42f * p, alpha = p,
                glow = (1f - kotlin.math.abs(1f - 2f * p)).coerceIn(0f, 1f), done = p >= 1f
            )
            at.heinz.deviceai.ui.JarvisMotion.drawCard(painter, frame, l, t, l + cardW, t + cardH)
            painter.drawText("ANIMATION PROOF · ${proof.label}", l + cardW / 2f, t + cardH / 2f,
                TextStyle(FontFamily.DISPLAY, 700, 11f, letterSpacing = 1.2f),
                Fill.Solid(at.heinz.deviceai.ui.JarvisDesign.Colors.cyanSoft), TextAlign.CENTER)
        }
        canvas.restoreToCount(saved)
    }

    private val headFadePaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

    private fun headFadeActive() = headEnabled && native3dDrawn && cockpitHeadBounds != null && width > 0 && height > 0

    /** Only the native head surface is masked; full-view effect children keep their own unclipped drawing. */
    override fun drawChild(canvas: Canvas, child: android.view.View, drawingTime: Long): Boolean {
        if (child !== nativeTexture) return super.drawChild(canvas, child, drawingTime)
        val saved = canvas.save()
        transformHeadEntrance(canvas)
        val drawn = if (headFadeActive()) {
            val layer = canvas.saveLayer(headDrawBounds, null)
            val result = super.drawChild(canvas, child, drawingTime)
            applyHeadFade(canvas)
            canvas.restoreToCount(layer)
            result
        } else super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(saved)
        return drawn
    }

    /** DST_IN gradients matching [at.heinz.deviceai.ui.JarvisHeadAura.fadeAlpha] (bottom and both sides). */
    private fun applyHeadFade(canvas: Canvas) {
        val b = headDrawBounds
        val (bottom, left, right) = headFadeShaders(b)
        val bottomTop = b.bottom - b.height() * at.heinz.deviceai.ui.JarvisHeadAura.BOTTOM_FADE
        val side = b.width() * at.heinz.deviceai.ui.JarvisHeadAura.SIDE_FADE
        val sideTop = b.top + b.height() * at.heinz.deviceai.ui.JarvisHeadAura.SIDE_FADE_FROM
        headFadePaint.shader = bottom
        canvas.drawRect(b.left, bottomTop, b.right, b.bottom, headFadePaint)
        headFadePaint.shader = left
        canvas.drawRect(b.left, sideTop, b.left + side, b.bottom, headFadePaint)
        headFadePaint.shader = right
        canvas.drawRect(b.right - side, sideTop, b.right, b.bottom, headFadePaint)
        headFadePaint.shader = null
    }

    private var headFadeShaders: Array<android.graphics.Shader>? = null
    private val headFadeShaderBounds = RectF()

    /** The three mask gradients depend only on the head bounds; they are rebuilt only when those change. */
    private fun headFadeShaders(b: RectF): Array<android.graphics.Shader> {
        headFadeShaders?.takeIf { headFadeShaderBounds == b }?.let { return it }
        val opaque = android.graphics.Color.BLACK; val clear = android.graphics.Color.TRANSPARENT
        val clamp = android.graphics.Shader.TileMode.CLAMP
        val bottomTop = b.bottom - b.height() * at.heinz.deviceai.ui.JarvisHeadAura.BOTTOM_FADE
        val side = b.width() * at.heinz.deviceai.ui.JarvisHeadAura.SIDE_FADE
        return arrayOf<android.graphics.Shader>(
            android.graphics.LinearGradient(0f, bottomTop, 0f, b.bottom, opaque, clear, clamp),
            android.graphics.LinearGradient(b.left, 0f, b.left + side, 0f, clear, opaque, clamp),
            android.graphics.LinearGradient(b.right - side, 0f, b.right, 0f, opaque, clear, clamp)
        ).also { headFadeShaders = it; headFadeShaderBounds.set(b) }
    }

    private fun advanceFrame() {
        if (!canAnimateFrame()) return
        val now = SystemClock.uptimeMillis()
        entranceMotion.advance(now)
        val previousCameraProgress = cameraMotion.progress
        cameraMotion.advance(now, hasWindowFocus(), Settings.Global.getFloat(context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        // Keep the terminal frame too: progress reaches exactly zero before Canvas can draw,
        // and dropping that last frame leaves a near-rest (but not rest) head projection cached.
        if (head3dEnabled && cameraFraming != null && cockpitHeadBounds != null &&
            (cameraMotion.workActive || cameraMotion.progress > 0f || previousCameraProgress > 0f)) updateAuraFraming()
        val delta = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, .1f)
        lastFrameMs = now
        avatarSeconds = (avatarSeconds + delta) % 460f
        val blend = 1f - exp(-delta * 7f)
        for (i in stateWeights.indices) {
            stateWeights[i] += ((if (i == currentState.ordinal) 1f else 0f) - stateWeights[i]) * blend
        }
        val sampleTime = pendingAudioMs
        if (sampleTime > consumedAudioMs) {
            audio.submit(pendingAudioLevel, sampleTime)
            consumedAudioMs = sampleTime
        }
        audioEnergy = if (currentState == State.SPEAKING) sqrt(audio.valueAt(now)) else 0f
        val targetEnergy = if (currentState == State.SPEAKING) .12f + audioEnergy * .88f else stateEnergy(currentState)
        energy += (targetEnergy - energy) * (1f - exp(-delta * 9f))
        if (renderBudget().animate) phase = (phase + delta * (.28f + energy * .72f)) % 1000f
    }

    private fun stateEnergy(state: State) = when (state) {
        State.READY -> .18f
        State.LISTENING -> .42f
        State.THINKING -> .78f
        State.SPEAKING -> .12f
        State.RESEARCH -> .62f
        State.LEARNING -> .52f
    }

}
