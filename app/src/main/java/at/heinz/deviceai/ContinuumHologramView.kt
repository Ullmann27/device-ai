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
    // New installations use the requested 3D cockpit. An existing explicit orb
    // or 2D choice is preserved; this never requests camera/audio permissions.
    private var head3dEnabled = appearancePrefs.getBoolean("native-head-3d", !hasSavedAppearance)
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
            postOnAnimationDelayed(drawFrame, if (head3dEnabled) { if (thermalStatus >= 2) 65L else 25L } else if (thermalStatus >= 2) 32L else 8L)
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
        windowVisibility == VISIBLE && !powerManager.isPowerSaveMode && thermalStatus < 3

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
                alpha = 1f
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
            addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
            .onSuccess { native3d = it }.onFailure { fail3d() }
    }

    private fun fail3d() {
        native3dFailed = true
        release3d(clearAsset = true)
        if (isAttachedToWindow && foregroundActive) Toast.makeText(context,
            "3D-Kopf derzeit nicht verfügbar · native 2D-Vorschau aktiv", Toast.LENGTH_LONG).show()
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
        AlertDialog.Builder(context).setTitle("Continuum · Darstellung")
            .setSingleChoiceItems(arrayOf("Kugel · Original", "Kopf · Native 2D-Vorschau", "Kopf · Native 3D-Vorschau"), if (head3dEnabled) 2 else if (headEnabled) 1 else 0) { dialog, item ->
                if (item == 2) setNative3dEnabled(true) else setNativeHeadEnabled(item == 1)
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
        clearTaskEffects()
        release3d(clearAsset = true)
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
        update3d()
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
        val cockpit = renderer.geometry == ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
        nativeTexture?.layoutParams?.let {
            it.width = (measuredWidth * if (cockpit) .66f else .72f).toInt()
            it.height = if (cockpit) (measuredHeight * .92f).toInt() else LayoutParams.MATCH_PARENT
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
    }

    /** Phone or Fold composition of the reference images (see ContinuumHologramRenderer.Geometry). */
    fun setGeometry(geometry: ContinuumHologramRenderer.Geometry) {
        val changed = renderer.geometry != geometry
        renderer.geometry = geometry
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
        if (headEnabled) {
            val pose = synchronized(avatarPose) { avatarPose.pose(System.currentTimeMillis()) }
            if (canDisplay3d() && native3d != null) {
                runCatching { native3d!!.render(pose, avatarSeconds, canAnimateFrame(), nativeTexture?.width ?: 0, nativeTexture?.height ?: 0) }
                    .onSuccess { rendered -> if (rendered) { native3dDrawn = true; nativeTexture?.alpha = 1f } }
                    .onFailure { fail3d() }
            }
            if (!native3dDrawn) avatarRenderer.draw(painter,
                width / density * if (cockpit) .66f else 1f,
                height / density * if (cockpit) .92f else 1f, pose, avatarSeconds, canAnimateFrame())
            if (native3dDrawn || cockpit) {
                // Same original orb, same actual app state and speech frame; no second animation loop.
                val orbWidth = width / density * if (cockpit) .43f else .28f
                val orbHeight = height / density * if (cockpit) .76f else .40f
                val orbCanvas = canvas.save()
                canvas.translate(width / density * if (cockpit) .57f else .72f,
                    height / density * if (cockpit) .09f else .30f)
                canvas.clipRect(0f, 0f, orbWidth, orbHeight)
                renderer.layout(orbWidth, orbHeight)
                renderer.draw(painter, renderFrame)
                canvas.restoreToCount(orbCanvas)
            }
            if (cockpit) painter.drawText("JARVIS", width / density * .33f, height / density * .96f,
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
        if (!canAnimateFrame() || width<=0 || height<=0) return
        val density=resources.displayMetrics.density
        val saved=canvas.save()
        canvas.scale(density,density)
        canvas.clipRect(0f,0f,width/density,height/density)
        painter.begin(canvas)
        taskEffects.draw(painter,width/density,height/density,SystemClock.uptimeMillis())
        canvas.restoreToCount(saved)
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
