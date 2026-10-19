package at.heinz.deviceai

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Hardware accelerated local hologram. Shaders, paths and drawing objects are reused. */
class ContinuumHologramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class State { READY, LISTENING, THINKING, SPEAKING, RESEARCH, LEARNING }

    private val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val frame = RectF()
    private val path = Path()
    private val audio = HologramAudioEnvelope()
    private val stateWeights = FloatArray(State.entries.size).apply { this[0] = 1f }
    private val nodeX = FloatArray(11)
    private val nodeY = FloatArray(11)
    private val nodeDepth = FloatArray(11)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cyan = Color.rgb(78, 225, 255)
    private val violet = Color.rgb(155, 129, 255)

    // These shaders use the local 340 x 234 canvas and never change per frame.
    private val atmosphere = RadialGradient(170f, 111f, 155f,
        intArrayOf(0x4A153F96, 0x1A153A75, Color.TRANSPARENT),
        floatArrayOf(0f, .54f, 1f), Shader.TileMode.CLAMP)
    private val halo = RadialGradient(170f, 103f, 104f,
        intArrayOf(0x001648A7, 0x103A6DF1, 0x663C8DFF, 0x003B78FF),
        floatArrayOf(0f, .52f, .73f, 1f), Shader.TileMode.CLAMP)
    private val glass = RadialGradient(142f, 69f, 133f,
        intArrayOf(0xE52B689F.toInt(), 0xF009234A.toInt(), 0xF005122D.toInt(), 0xDD163D83.toInt()),
        floatArrayOf(0f, .35f, .73f, 1f), Shader.TileMode.CLAMP)
    private val coreGlow = RadialGradient(170f, 103f, 53f,
        intArrayOf(0xE1D3FAFF.toInt(), 0xA132D6FF.toInt(), 0x46335FE2, Color.TRANSPARENT),
        floatArrayOf(0f, .08f, .37f, 1f), Shader.TileMode.CLAMP)
    private val reflection = RadialGradient(140f, 61f, 45f,
        intArrayOf(0x91CAEDFF.toInt(), 0x2239AFFF, Color.TRANSPARENT),
        floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
    private val platformGlow = RadialGradient(170f, 205f, 123f,
        intArrayOf(0x8C207BEA.toInt(), 0x252756A3, Color.TRANSPARENT),
        floatArrayOf(0f, .57f, 1f), Shader.TileMode.CLAMP)

    @Volatile private var currentState = State.READY
    @Volatile private var pendingAudioLevel = 0f
    @Volatile private var pendingAudioMs = 0L
    private var consumedAudioMs = 0L
    private var animationsEnabled = true
    private var motionAllowed = false
    private var aggregatedVisible = false
    private var framePending = false
    private var observersRegistered = false
    private var lastFrameMs = 0L
    private var phase = .4f
    private var energy = .18f
    private var audioEnergy = 0f

    private val drawFrame = Runnable {
        framePending = false
        if (canAnimate()) invalidate()
    }
    private val motionObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = updateMotionPolicy()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateMotionPolicy()
    }

    init {
        // State has an accessible text label beside the hero; avoid a duplicate announcement.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
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
        if (!canAnimate()) settleState()
        invalidate()
    }

    /** Safe from a TTS callback thread. Supply actual PCM RMS in the range 0..1. */
    fun setAudioLevel(level: Float) {
        if (currentState != State.SPEAKING) return
        pendingAudioLevel = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        pendingAudioMs = SystemClock.uptimeMillis()
    }

    /** Explicit reduced motion preference; system animator scale is also respected. */
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        updateMotionPolicy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!observersRegistered) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            context.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            observersRegistered = true
        }
        aggregatedVisible = isShown
        updateMotionPolicy()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        audio.reset()
        if (observersRegistered) {
            context.contentResolver.unregisterContentObserver(motionObserver)
            context.unregisterReceiver(powerReceiver)
            observersRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        updateMotionPolicy()
    }

    private fun canAnimate() = motionAllowed && isAttachedToWindow && aggregatedVisible && windowVisibility == VISIBLE

    private fun updateMotionPolicy() {
        motionAllowed = animationsEnabled && ValueAnimator.areAnimatorsEnabled() && !powerManager.isPowerSaveMode
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        if (!canAnimate()) {
            settleState()
            audio.reset()
            audioEnergy = 0f
            pendingAudioMs = 0L
            consumedAudioMs = 0L
        }
        invalidate()
    }

    private fun settleState() {
        stateWeights.fill(0f)
        stateWeights[currentState.ordinal] = 1f
        energy = stateEnergy(currentState)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((340f * resources.displayMetrics.density).toInt(), widthMeasureSpec),
            resolveSize((234f * resources.displayMetrics.density).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 340f, height / 234f)
        if (scale <= 0f) return
        advanceFrame()
        val saved = canvas.save()
        canvas.translate((width - 340f * scale) / 2f, (height - 234f * scale) / 2f)
        canvas.scale(scale, scale)
        fillShader(atmosphere)
        canvas.drawOval(8f, 0f, 332f, 234f, brush)
        drawDistantParticles(canvas)
        drawPlatform(canvas)

        // Rear orbital halves sit behind the glass; front halves pass across it.
        drawOrbit(canvas, -24f, phase * 21f, false, cyan, 1f)
        drawOrbit(canvas, 28f, 158f - phase * 17f, false, violet, .8f)
        fillShader(halo, (155 + energy * 100).toInt())
        val haloScale = 1f + energy * .055f + sin(phase * 1.2f) * .012f
        val glowSave = canvas.save()
        canvas.scale(haloScale, haloScale, 170f, 103f)
        canvas.drawCircle(170f, 103f, 104f, brush)
        canvas.restoreToCount(glowSave)

        val orbSave = canvas.save()
        val breath = 1f + sin(phase * 1.5f) * (.008f + energy * .009f) + audioEnergy * .024f
        canvas.scale(breath, breath, 170f, 103f)
        fillShader(glass)
        canvas.drawCircle(170f, 103f, 72f, brush)
        drawSphereMesh(canvas)
        drawEnergyWaves(canvas)
        drawStateDetails(canvas)
        drawCore(canvas)
        stroke(cyan, .8f, 180)
        canvas.drawCircle(170f, 103f, 72f, brush)
        stroke(Color.rgb(169, 203, 255), 1.8f, 170)
        frame.set(99f, 32f, 241f, 174f)
        canvas.drawArc(frame, 196f, 116f, false, brush)
        stroke(violet, 1.4f, 190)
        canvas.drawArc(frame, 12f, 94f, false, brush)
        fillShader(reflection)
        canvas.drawOval(114f, 40f, 177f, 84f, brush)
        canvas.restoreToCount(orbSave)

        drawOrbit(canvas, -24f, phase * 21f, true, cyan, 1f)
        drawOrbit(canvas, 28f, 158f - phase * 17f, true, violet, .8f)
        drawSatellites(canvas)
        canvas.restoreToCount(saved)
        if (canAnimate() && !framePending) {
            framePending = true
            // Ready uses about 20fps, active states 30fps; paused views schedule nothing.
            postOnAnimationDelayed(drawFrame, if (currentState == State.READY) 40L else 24L)
        }
    }

    private fun advanceFrame() {
        if (!canAnimate()) return
        val now = SystemClock.uptimeMillis()
        val delta = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, .1f)
        lastFrameMs = now
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

    private fun drawDistantParticles(canvas: Canvas) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        for (i in 0 until 29) {
            val x = ((i * 83 + 19) % 310 + 15).toFloat()
            val y = ((i * 47 + 13) % 170 + 6).toFloat()
            val twinkle = .65f + .35f * sin(phase * .7f + i)
            brush.color = Color.argb((twinkle * if (i % 5 == 0) 135 else 60).toInt(), 126, 182, 255)
            canvas.drawCircle(x, y, if (i % 7 == 0) 1f else .55f, brush)
        }
    }

    private fun drawPlatform(canvas: Canvas) {
        fillShader(platformGlow, (160 + energy * 80).toInt())
        canvas.drawOval(43f, 178f, 297f, 234f, brush)
        // A light column anchors the floating sphere to its projection base.
        path.rewind()
        path.moveTo(151f, 170f)
        path.lineTo(132f, 204f)
        path.quadTo(170f, 214f, 208f, 204f)
        path.lineTo(189f, 170f)
        path.close()
        fill(Color.rgb(54, 132, 242), (8 + energy * 13).toInt())
        canvas.drawPath(path, brush)
        fill(Color.rgb(6, 15, 34), 235)
        canvas.drawOval(69f, 197f, 271f, 222f, brush)
        for (i in 0..3) {
            frame.set(57f + i * 14f, 197f + i * 2.1f, 283f - i * 14f, 225f - i * 2.2f)
            stroke(if (i % 2 == 0) cyan else violet, if (i == 0) 1.2f else .7f, 135 - i * 24)
            canvas.drawOval(frame, brush)
        }
        frame.set(59f, 195f, 281f, 223f)
        stroke(cyan, 2f, 190)
        canvas.drawArc(frame, 5f + phase * 12f, 105f, false, brush)
        stroke(Color.rgb(212, 247, 255), 1f, 220)
        canvas.drawArc(frame, 13f + phase * 12f, 38f, false, brush)
    }

    private fun drawSphereMesh(canvas: Canvas) {
        val turn = phase * .18f
        for (meridian in 0 until 7) {
            path.rewind()
            val longitude = meridian * TWO_PI / 7f + turn
            stroke(Color.rgb(102, 172, 246), .55f, if (cos(longitude) > 0f) 53 else 24)
            for (step in 0..24) {
                val latitude = -PI.toFloat() / 2f + step * PI.toFloat() / 24f
                val x = 170f + cos(latitude) * sin(longitude) * 71f
                val y = 103f + sin(latitude) * 71f
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, brush)
        }
        stroke(Color.rgb(102, 172, 246), .55f, 39)
        for (i in -2..2) {
            val y = i * 23f
            val radius = sqrt(71f * 71f - y * y)
            frame.set(170f - radius, 103f + y - radius * .18f, 170f + radius, 103f + y + radius * .18f)
            canvas.drawOval(frame, brush)
        }
        // Project a sparse constellation through the glass, with fainter rear nodes.
        for (i in nodeX.indices) {
            val y = 1f - i * 2f / (nodeX.size - 1)
            val latitudeRadius = sqrt(1f - y * y)
            val angle = i * 2.39996f + turn
            nodeX[i] = 170f + cos(angle) * latitudeRadius * 64f
            nodeY[i] = 103f + y * 64f
            nodeDepth[i] = (sin(angle) * latitudeRadius + 1f) * .5f
        }
        for (i in nodeX.indices) {
            if (i + 1 < nodeX.size) {
                stroke(cyan, .5f, (12 + nodeDepth[i] * 32).toInt())
                canvas.drawLine(nodeX[i], nodeY[i], nodeX[i + 1], nodeY[i + 1], brush)
            }
            fill(Color.rgb(163, 230, 255), (32 + nodeDepth[i] * 120).toInt())
            canvas.drawCircle(nodeX[i], nodeY[i], .65f + nodeDepth[i] * .65f, brush)
        }
    }

    private fun drawEnergyWaves(canvas: Canvas) {
        val speech = stateWeights[State.SPEAKING.ordinal]
        val amplitude = (1f - speech) * (.75f + energy * 3f) + speech * (.35f + audioEnergy * 9f)
        for (ring in 0..2) {
            path.rewind()
            for (i in 0..96) {
                val angle = i * TWO_PI / 96f
                val wave = sin(angle * (3 + ring) + phase * (1.5f + ring * .4f)) +
                    .45f * sin(angle * 7f - phase * 1.8f + ring)
                val radius = 61f + ring * 4.5f + wave * amplitude * (.55f + ring * .18f)
                val x = 170f + cos(angle) * radius
                val y = 103f + sin(angle) * radius
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            stroke(if (ring == 1) violet else cyan, 4.5f, (13 + energy * 13).toInt())
            canvas.drawPath(path, brush)
            stroke(if (ring == 1) violet else cyan, .65f, (70 + energy * 55).toInt())
            canvas.drawPath(path, brush)
        }
    }

    private fun drawStateDetails(canvas: Canvas) {
        val listening = stateWeights[State.LISTENING.ordinal]
        if (listening > .02f) {
            for (i in 0..2) {
                val progress = (phase * .5f + i / 3f) % 1f
                val radius = 36f + (1f - progress) * 29f
                stroke(cyan, .9f, (listening * progress * 120).toInt())
                canvas.drawCircle(170f, 103f, radius, brush)
            }
            frame.set(110f, 49f, 230f, 157f)
            stroke(cyan, 2f, (listening * 200).toInt())
            canvas.drawArc(frame, 151f, 58f, false, brush)
            canvas.drawArc(frame, -29f, 58f, false, brush)
        }
        val thinking = stateWeights[State.THINKING.ordinal]
        if (thinking > .02f) {
            val saved = canvas.save()
            canvas.rotate(phase * 44f, 170f, 103f)
            for (i in 0..2) {
                val radius = 23f + i * 12f
                frame.set(170f - radius, 103f - radius, 170f + radius, 103f + radius)
                stroke(if (i == 1) cyan else violet, 1.6f, (thinking * 185).toInt())
                canvas.drawArc(frame, i * 110f, 78f, false, brush)
            }
            canvas.restoreToCount(saved)
        }
        val speaking = stateWeights[State.SPEAKING.ordinal]
        if (speaking > .02f) {
            for (line in 0..2) {
                path.rewind()
                for (i in 0..64) {
                    val x = 116f + i * 108f / 64f
                    val taper = sin(i * PI.toFloat() / 64f)
                    val wave = sin(i * .27f - phase * 8f + line * .8f) * taper
                    val y = 103f + (line - 1) * 6f + wave * audioEnergy * (13f - line * 2f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                stroke(cyan, 3.8f, (speaking * 24).toInt())
                canvas.drawPath(path, brush)
                stroke(if (line == 1) Color.rgb(204, 248, 255) else cyan, .9f,
                    (speaking * (90 + audioEnergy * 150)).toInt())
                canvas.drawPath(path, brush)
            }
        }
        val research = stateWeights[State.RESEARCH.ordinal]
        if (research > .02f) {
            val saved = canvas.save()
            canvas.rotate(phase * 34f, 170f, 103f)
            frame.set(116f, 49f, 224f, 157f)
            stroke(cyan, 1.7f, (research * 200).toInt())
            canvas.drawArc(frame, -65f, 105f, false, brush)
            stroke(cyan, .7f, (research * 110).toInt())
            canvas.drawLine(170f, 103f, 208f, 65f, brush)
            fill(Color.rgb(195, 247, 255), (research * 240).toInt())
            canvas.drawCircle(208f, 65f, 2f, brush)
            canvas.restoreToCount(saved)
        }
        val learning = stateWeights[State.LEARNING.ordinal]
        if (learning > .02f) {
            for (i in 0 until 9) {
                val progress = (phase * .24f + i / 9f) % 1f
                val x = 170f + sin(i * 2.4f + progress * 3f) * (48f - progress * 32f)
                val y = 153f - progress * 87f
                fill(if (i % 2 == 0) violet else cyan, (learning * sin(progress * PI.toFloat()) * 210).toInt())
                canvas.drawCircle(x, y, 1.2f + progress * .6f, brush)
                if (i % 3 == 0) {
                    stroke(violet, .65f, (learning * 65).toInt())
                    canvas.drawLine(x, y, 170f, 103f, brush)
                }
            }
        }
    }

    private fun drawCore(canvas: Canvas) {
        val saved = canvas.save()
        val pulse = .78f + energy * .35f + audioEnergy * .32f
        canvas.scale(pulse, pulse, 170f, 103f)
        fillShader(coreGlow)
        canvas.drawCircle(170f, 103f, 53f, brush)
        fill(Color.rgb(211, 252, 255), (150 + energy * 100).toInt())
        canvas.drawCircle(170f, 103f, 1.8f + energy * .7f, brush)
        stroke(cyan, .65f, (75 + energy * 65).toInt())
        canvas.drawLine(159f, 103f, 181f, 103f, brush)
        canvas.drawLine(170f, 92f, 170f, 114f, brush)
        canvas.restoreToCount(saved)
    }

    private fun drawOrbit(canvas: Canvas, tilt: Float, turn: Float, front: Boolean, color: Int, strength: Float) {
        val saved = canvas.save()
        canvas.rotate(tilt, 170f, 111f)
        frame.set(43f, 77f, 297f, 145f)
        val start = if (front) 0f else 180f
        stroke(color, 6f, ((if (front) 28 else 13) * strength).toInt())
        canvas.drawArc(frame, start, 180f, false, brush)
        stroke(color, if (front) 1.65f else .85f, ((if (front) 195 else 85) * strength).toInt())
        canvas.drawArc(frame, start, 180f, false, brush)
        val highlight = ((turn % 180f) + 180f) % 180f
        stroke(Color.rgb(213, 244, 255), .85f, ((if (front) 225 else 90) * strength).toInt())
        canvas.drawArc(frame, start + highlight, min(32f, 180f - highlight), false, brush)
        canvas.restoreToCount(saved)
    }

    private fun drawSatellites(canvas: Canvas) {
        for (i in 0 until 12) {
            val angle = phase * (.3f + i % 3 * .08f) + i * 2.39996f
            val radius = 83f + i % 3 * 16f
            val x = 170f + cos(angle) * radius
            val y = 108f + sin(angle) * (40f + i % 4 * 8f)
            val alpha = (55 + (sin(angle) + 1f) * 65f).toInt()
            fill(if (i % 3 == 0) violet else cyan, alpha / 5)
            canvas.drawCircle(x, y, 3.2f, brush)
            fill(Color.rgb(173, 234, 255), alpha)
            canvas.drawCircle(x, y, if (i % 3 == 0) 1.25f else .8f, brush)
        }
    }

    private fun fillShader(shader: Shader, alpha: Int = 255) {
        brush.shader = shader
        brush.style = Paint.Style.FILL
        brush.color = Color.WHITE
        brush.alpha = alpha.coerceIn(0, 255)
    }

    private fun fill(color: Int, alpha: Int) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        brush.color = color
        brush.alpha = alpha.coerceIn(0, 255)
    }

    private fun stroke(color: Int, width: Float, alpha: Int) {
        brush.shader = null
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = width
        brush.strokeCap = Paint.Cap.ROUND
        brush.strokeJoin = Paint.Join.ROUND
        brush.color = color
        brush.alpha = alpha.coerceIn(0, 255)
    }

    private companion object {
        const val TWO_PI = 6.2831855f
    }
}
