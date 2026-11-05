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
import at.heinz.deviceai.avatar3d.FilamentAvatarRenderer
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
        cockpitSequence?.let { old -> old.clear(); removeView(old) }
        cockpitSequence = sequence
        sequence?.let {
            it.setMotionAllowed(canAnimateFrame())
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
    private var auraScale = 1f
    private var auraAnchorEffective = .5f

    /**
     * Head size and effective anchor of the live 3D window (the calibrated values for the 2D head), so the aura
     * rings stay around the fitted head. Same [at.heinz.deviceai.avatar3d.AvatarFraming] window as the renderer;
     * recomputed only when the stage, the framing or the asset changes, never allocated per frame.
     */
    private fun updateAuraFraming() {
        val focus = if (head3dEnabled && native3dDrawn) assetMetadata?.focus else null
        val w = headDrawBounds.width(); val h = headDrawBounds.height()
        if (focus == null || w <= 0f || h <= 0f) { auraFocus = null; auraScale = 1f; auraAnchorEffective = headAnchorX; return }
        if (focus !== auraFocus || w != auraWidth || h != auraHeight || headAnchorX != auraAnchor ||
            headCloseUp != auraCloseUp || headKeepOut != auraKeepOut) {
            auraFocus = focus; auraWidth = w; auraHeight = h; auraAnchor = headAnchorX; auraCloseUp = headCloseUp
            auraKeepOut = headKeepOut
            val window = at.heinz.deviceai.avatar3d.AvatarFraming.window(focus, (w / h).toDouble(),
                headAnchorX.toDouble(), headCloseUp.toDouble(), headKeepOut)
            auraScale = window.headScale.toFloat(); auraAnchorEffective = window.anchorX.toFloat()
        }
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
        invalidate()
    }

    fun setCockpitRegions(head: Rect?, orb: Rect?) {
        require((head == null) == (orb == null)) { "Head and orb bounds must be supplied together" }
        if (head != null && orb != null) {
            require(!head.isEmpty && !orb.isEmpty && head.left >= 0 && head.top >= 0 && orb.left >= 0 && orb.top >= 0)
            require(!Rect.intersects(head, orb)) { "Head and orb drawing regions must not overlap" }
        }
        if (cockpitHeadBounds == head && cockpitOrbBounds == orb) return
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
                    bytes to AvatarGlbContract.validate(bytes)
                }
                mainHandler.post {
                    assetLoading = false
                    if (generation != assetGeneration) { if (canDisplay3d()) update3d(); return@post }
                    candidate.onSuccess { (bytes, metadata) -> assetBytes = bytes; assetMetadata = metadata; update3d(); invalidate() }
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

    private fun fail3d() {
        native3dFailed = true
        release3d(clearAsset = true)
        if (isAttachedToWindow && foregroundActive) Toast.makeText(context,
            "3D-Kopf derzeit nicht verfügbar", Toast.LENGTH_LONG).show()
        invalidate()
    }

    private fun release3d(clearAsset: Boolean) {
        native3d?.close(); native3d = null; native3dDrawn = false
        nativeTexture?.let { texture -> texture.surfaceTextureListener = null; removeView(texture) }
        nativeTexture = null
        nativeSurface?.release(); nativeSurface = null
        if (clearAsset) { assetGeneration++; assetBytes = null; assetMetadata = null }
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
            .setNeutralButton("Bewegung prüfen") { _, _ ->
                Toast.makeText(context, motionDiagnostics(), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Schließen", null).show()
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
        if (!active) synchronized(avatarPose) {
            avatarPose.clearSpeech()
            avatarPose.clearObservedGaze(System.currentTimeMillis())
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
        taskEffectsNotBeforeMs = SystemClock.uptimeMillis()
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
        cockpitSequence?.setMotionAllowed(false)
        clearTaskEffects()
        // A fold/navigation reparent destroys the surface, but retains validated immutable asset bytes.
        release3d(clearAsset = false)
        mainHandler.removeCallbacks(avatarInvalidation)
        avatarInvalidationPending.set(false)
        synchronized(avatarPose) {
            avatarPose.clearSpeech()
            avatarPose.clearObservedGaze(System.currentTimeMillis())
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
        updateMotionPolicy()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateMotionPolicy()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateMotionPolicy()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Re-evaluate scheduling after Samsung Fold window/cover transitions.
        if (isAttachedToWindow) updateMotionPolicy()
    }

    /** Actual View visibility, not a callback-cached flag that can go stale. */
    private fun canAnimateFrame() =
        foregroundActive && motionAllowed && isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun renderBudget() = at.heinz.deviceai.ui.JarvisRenderQuality.resolve(
        foregroundActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE,
        powerManager.isPowerSaveMode, thermalStatus,
        !animationsEnabled || !ValueAnimator.areAnimatorsEnabled(),
        currentState == State.THINKING || currentState == State.RESEARCH || currentState == State.LEARNING)

    /** No private content. Owner can inspect this by long-pressing the hologram. */
    /** Read only on the Android UI thread. Compares scheduled callbacks and real View draws. */
    data class MotionSample(val callbackCount: Long, val drawCount: Long, val phase: Float, val reason: String)

    fun sampleMotion(): MotionSample = MotionSample(visibleFrameCount, drawnFrameCount, phase, motionDiagnostics())

    internal fun motionDiagnostics(): String = when {
        !animationsEnabled -> "Avatar: In-App-Animation ausgeschaltet"
        !ValueAnimator.areAnimatorsEnabled() -> "Avatar: Android-Animationen ausgeschaltet. Bedienungshilfen/Entwickleroptionen prüfen."
        powerManager.isPowerSaveMode -> "Avatar: Energiesparmodus blockiert Bewegung."
        thermalStatus >= 3 -> "Avatar: Temperatur hoch · Bewegung pausiert."
        !foregroundActive -> "Avatar: Activity nicht im Vordergrund."
        !isAttachedToWindow -> "Avatar: View nicht am Fenster."
        !isShown || windowVisibility != VISIBLE -> "Avatar: View derzeit unsichtbar."
        else -> "Avatar: LIVE · Callbacks $visibleFrameCount · Canvas $drawnFrameCount · Phase ${"%.2f".format(java.util.Locale.ROOT, phase)}"
    }

    private fun updateMotionPolicy() {
        motionAllowed = animationsEnabled && ValueAnimator.areAnimatorsEnabled() && !powerManager.isPowerSaveMode && thermalStatus < 3
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        if (!canAnimateFrame()) {
            clearTaskEffects()
            synchronized(avatarPose) { avatarPose.clearObservedGaze(System.currentTimeMillis()) }
            settleState()
            audio.reset()
            audioEnergy = 0f
            pendingAudioMs = 0L
            consumedAudioMs = 0L
        }
        cockpitSequence?.setMotionAllowed(canAnimateFrame())
        update3d()
        native3d?.setQuality(renderBudget().reducedBloom)
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
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        frameWeights.indices.forEach { frameWeights[it] = stateWeights[it] }
        renderFrame.phase = phase
        renderFrame.energy = energy
        renderFrame.audio = audioEnergy
        val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
        resolveHeadBounds(width, height)
        updateOrbTarget(width, height)
        if (headEnabled && cockpitHeadBounds != null) {
            val t = if (canAnimateFrame()) avatarSeconds else 0f
            at.heinz.deviceai.ui.JarvisEnergyFlows.draw(painter, width / density, height / density, t, energy)
            updateAuraFraming()
            at.heinz.deviceai.ui.JarvisHeadAura.draw(painter, at.heinz.deviceai.ui.JarvisShellLayout.Box(
                headDrawBounds.left / density, headDrawBounds.top / density, headDrawBounds.right / density,
                headDrawBounds.bottom / density), auraAnchorEffective, t,
                headScale = auraScale, activityEnergy = energy, speechLevel = audioEnergy)
        }
        if (headEnabled) {
            val pose = synchronized(avatarPose) { avatarPose.pose(System.currentTimeMillis(), canAnimateFrame()) }
            if (canDisplay3d() && native3d != null) {
                runCatching {
                    native3d!!.setQuality(renderBudget().reducedBloom)
                    native3d!!.setFraming(headAnchorX, headCloseUp, headKeepOut)
                    native3d!!.render(pose, avatarSeconds, canAnimateFrame(), nativeTexture?.width ?: 0, nativeTexture?.height ?: 0)
                }
                    .onSuccess { rendered -> if (rendered) { native3dDrawn = true; nativeTexture?.alpha = 1f } }
                    .onFailure { fail3d() }
            }
            if (!native3dDrawn) {
                val headCanvas = canvas.save()
                canvas.translate(headDrawBounds.left / density, headDrawBounds.top / density)
                avatarRenderer.draw(painter, headDrawBounds.width() / density, headDrawBounds.height() / density,
                    pose, avatarSeconds, canAnimateFrame())
                canvas.restoreToCount(headCanvas)
            }
            if (native3dDrawn || cockpit || cockpitOrbBounds != null) {
                // Same original orb, same actual app state and speech frame; no second animation loop.
                resolveOrbBounds(width, height)
                val orbWidth = orbDrawBounds.width() / density
                val orbHeight = orbDrawBounds.height() / density
                val orbCanvas = canvas.save()
                canvas.translate(orbDrawBounds.left / density, orbDrawBounds.top / density)
                canvas.clipRect(0f, 0f, orbWidth, orbHeight)
                if (cockpitOrbBounds != null) {
                    // Reuse the exact original animated sphere/rings with no opaque tile.
                    val now = SystemClock.uptimeMillis()
                    val effects = cockpitSequence?.effects
                    val originalEnergy = renderFrame.energy
                    renderFrame.energy = maxOf(originalEnergy, effects?.orbPulseLevel(now) ?: 0f,
                        effects?.recallBrightness(now) ?: 0f)
                    renderer.layout(orbWidth, orbHeight)
                    renderer.draw(painter, renderFrame, transparentBackdrop = true)
                    renderFrame.energy = originalEnergy
                } else {
                    renderer.layout(orbWidth, orbHeight)
                    renderer.draw(painter, renderFrame)
                }
                canvas.restoreToCount(orbCanvas)
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
        } else {
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
            canvas.translate(headDrawBounds.left, headDrawBounds.top)
            canvas.scale(density, density)
            painter.begin(canvas)
            at.heinz.deviceai.ui.JarvisHeadAura.drawLightStreams(painter, headDrawBounds.width() / density,
                headDrawBounds.height() / density)
            canvas.restoreToCount(streams)
        }
        if (!canAnimateFrame()) return
        val density=resources.displayMetrics.density
        val saved=canvas.save()
        canvas.scale(density,density)
        canvas.clipRect(0f,0f,width/density,height/density)
        painter.begin(canvas)
        taskEffects.draw(painter,width/density,height/density,SystemClock.uptimeMillis())
        canvas.restoreToCount(saved)
    }

    private val headFadePaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

    private fun headFadeActive() = headEnabled && native3dDrawn && cockpitHeadBounds != null && width > 0 && height > 0

    /** Only the native head surface is masked; full-view effect children keep their own unclipped drawing. */
    override fun drawChild(canvas: Canvas, child: android.view.View, drawingTime: Long): Boolean {
        if (child !== nativeTexture || !headFadeActive()) return super.drawChild(canvas, child, drawingTime)
        val layer = canvas.saveLayer(headDrawBounds, null)
        val drawn = super.drawChild(canvas, child, drawingTime)
        applyHeadFade(canvas)
        canvas.restoreToCount(layer)
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
        phase = (phase + delta * (.28f + energy * .72f)) % 1000f
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
