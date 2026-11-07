package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.avatar.AvatarExpression
import at.heinz.deviceai.avatar.AvatarGestureIntent
import at.heinz.deviceai.avatar.AvatarPose
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.Viseme
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/** Bounded, allocation-free per-frame diagnostics for the real visible avatar path. */
internal class AvatarFrameReceipt {
    var frameNumber: Long = 0L
    var frameNanos: Long = 0L
    var stateOrdinal: Int = 0
    var stageFlags: Int = 0
    var jawOpen: Float = 0f
    var speakEnergy: Float = 0f
    var gazeX: Float = 0f
    var gazeY: Float = 0f
    var visemeOrdinal: Int = 0

    fun reset(frame: Long, nanos: Long) {
        frameNumber = frame
        frameNanos = nanos
        stateOrdinal = 0
        stageFlags = 0
        jawOpen = 0f
        speakEnergy = 0f
        gazeX = 0f
        gazeY = 0f
        visemeOrdinal = 0
    }

    fun compact(): String = String.format(
        Locale.ROOT,
        "frame=%d state=%d jaw=%.2f energy=%.2f gaze=%.2f/%.2f vis=%d flags=0x%03x",
        frameNumber, stateOrdinal, jawOpen, speakEnergy, gazeX, gazeY, visemeOrdinal, stageFlags
    )

    companion object {
        const val FRAME_DRAW = 1 shl 0
        const val AVATAR_EVENT = 1 shl 1
        const val POSE_SAMPLED = 1 shl 2
        const val HEAD_3D_SELECTED = 1 shl 3
        const val SURFACE_VALID = 1 shl 4
        const val TEXTURE_VISIBLE = 1 shl 5
        const val MORPH_UPLOADED = 1 shl 6
        const val JOINTS_UPLOADED = 1 shl 7
        const val FILAMENT_BEGIN = 1 shl 8
        const val FILAMENT_RENDERED = 1 shl 9
    }
}

internal class AvatarTraceRingBuffer(private val capacity: Int = 120) {
    init { require(capacity in 16..1024) }

    private val frames = Array(capacity) { AvatarFrameReceipt() }
    private val cursor = AtomicLong(0L)

    fun beginFrame(frameNanos: Long): AvatarFrameReceipt {
        val frame = cursor.incrementAndGet()
        return frames[(frame % capacity).toInt()].also { it.reset(frame, frameNanos) }
    }

    fun diagnose(sampleLimit: Int = 60): String {
        val current = cursor.get()
        if (current <= 0L) return "BROKEN_AT_ROOT: kein sichtbarer Avatar-Frame"
        val count = minOf(current, sampleLimit.coerceIn(1, capacity).toLong()).toInt()
        var selected = 0
        var surface = 0
        var texture = 0
        var pose = 0
        var morph = 0
        var begin = 0
        var rendered = 0
        for (i in 0 until count) {
            val r = frames[((current - i) % capacity).toInt()]
            if (r.stageFlags and AvatarFrameReceipt.HEAD_3D_SELECTED != 0) selected++
            if (r.stageFlags and AvatarFrameReceipt.SURFACE_VALID != 0) surface++
            if (r.stageFlags and AvatarFrameReceipt.TEXTURE_VISIBLE != 0) texture++
            if (r.stageFlags and AvatarFrameReceipt.POSE_SAMPLED != 0) pose++
            if (r.stageFlags and AvatarFrameReceipt.MORPH_UPLOADED != 0) morph++
            if (r.stageFlags and AvatarFrameReceipt.FILAMENT_BEGIN != 0) begin++
            if (r.stageFlags and AvatarFrameReceipt.FILAMENT_RENDERED != 0) rendered++
        }
        return when {
            selected == 0 -> "3D_NOT_SELECTED: sichtbarer Pfad benutzt keinen 3D-Kopf"
            surface == 0 -> "BROKEN_AT_SURFACE: keine gültige Filament-Surface"
            texture == 0 -> "BROKEN_AT_TEXTURE: TextureView nicht sichtbar"
            pose == 0 -> "BROKEN_AT_POSE: Renderer erhält keine Pose"
            morph == 0 -> "BROKEN_AT_MAPPER: Pose da, Morph-Upload fehlt"
            begin == 0 -> "BROKEN_AT_FILAMENT_BEGIN: beginFrame liefert keinen Frame"
            rendered == 0 -> "BROKEN_AT_FILAMENT_RENDER: Renderpass wird nicht abgeschlossen"
            else -> "AVATAR_PIPELINE_LIVE: " + frames[(current % capacity).toInt()].compact()
        }
    }
}

internal data class AvatarProofFrame(
    val pose: AvatarPose,
    val orbEnergy: Float,
    val blinkOverride: Float?,
    val cardProgress: Float,
    val label: String
)

/** 15-second deterministic renderer proof. No model, network, ASR or TTS. */
internal object DeterministicAvatarProofRunner {
    const val DURATION_MS = 15_000L

    fun frame(elapsedMillis: Long): AvatarProofFrame {
        val t = elapsedMillis.coerceIn(0L, DURATION_MS).toFloat() / 1000f
        val breath = sin(t * 2f * PI.toFloat() / 4.6f) * .04f
        return when {
            t < 2f -> AvatarProofFrame(
                pose = pose(AvatarState.IDLE, AvatarExpression.NEUTRAL,
                    sin(t * 1.5f) * .12f, breath, Viseme.SIL, 0f, 0f),
                orbEnergy = .24f, blinkOverride = null, cardProgress = 0f, label = "IDLE"
            )
            t < 3f -> AvatarProofFrame(
                pose = pose(AvatarState.IDLE, AvatarExpression.ATTENTIVE, 0f, breath,
                    Viseme.SIL, 0f, 0f),
                orbEnergy = .30f, blinkOverride = blink(t, 2.08f).coerceAtLeast(blink(t, 2.58f)),
                cardProgress = 0f, label = "BLINK"
            )
            t < 5f -> {
                val phase = (t - 3f) / 2f
                val gaze = when {
                    phase < .34f -> -.75f
                    phase < .67f -> 0f
                    else -> .75f
                }
                AvatarProofFrame(
                    pose = pose(AvatarState.IDLE, AvatarExpression.ATTENTIVE, gaze, 0f,
                        Viseme.SIL, 0f, 0f),
                    orbEnergy = .38f, blinkOverride = null, cardProgress = 0f, label = "GAZE"
                )
            }
            t < 9f -> {
                val step = (((t - 5f) / .8f).toInt()).coerceIn(0, 4)
                val viseme = arrayOf(Viseme.PP, Viseme.AA, Viseme.O, Viseme.U, Viseme.PP)[step]
                val opening = when (viseme) {
                    Viseme.PP -> 0f
                    Viseme.AA -> .80f
                    Viseme.O -> .58f
                    Viseme.U -> .38f
                    else -> .30f
                }
                AvatarProofFrame(
                    pose = pose(AvatarState.SPEAKING, AvatarExpression.NEUTRAL,
                        sin(t * 2.2f) * .10f, breath, viseme, 1f, opening),
                    orbEnergy = .86f, blinkOverride = null, cardProgress = 0f, label = "VISEME"
                )
            }
            t < 12f -> AvatarProofFrame(
                pose = pose(
                    AvatarState.THINKING, AvatarExpression.FOCUSED, .35f, .25f,
                    Viseme.SIL, 0f, 0f, AvatarGestureIntent.THINK_MICRO,
                    ((t - 9f) / 3f).coerceIn(0f, 1f)
                ),
                orbEnergy = 1f, blinkOverride = null, cardProgress = 0f, label = "THINKING"
            )
            else -> {
                val raw = ((t - 12f) / 1.5f).coerceIn(0f, 1f)
                val eased = 1f - (1f - raw) * (1f - raw) * (1f - raw)
                AvatarProofFrame(
                    pose = pose(
                        AvatarState.IDLE, AvatarExpression.ATTENTIVE, .55f, -.18f,
                        Viseme.SIL, 0f, 0f, AvatarGestureIntent.WINDOW_FOCUS, eased
                    ),
                    orbEnergy = .62f, blinkOverride = null, cardProgress = eased, label = "CARD"
                )
            }
        }
    }

    private fun pose(
        state: AvatarState,
        expression: AvatarExpression,
        gazeX: Float,
        gazeY: Float,
        viseme: Viseme,
        visemeWeight: Float,
        jawOpen: Float,
        gesture: AvatarGestureIntent? = null,
        gestureProgress: Float = 0f
    ) = AvatarPose(
        state = state,
        expression = expression,
        gazeX = gazeX.coerceIn(-1f, 1f),
        gazeY = gazeY.coerceIn(-1f, 1f),
        jawOpen = jawOpen.coerceIn(0f, 1f),
        viseme = viseme,
        visemeWeight = visemeWeight.coerceIn(0f, 1f),
        speakEnergy = jawOpen.coerceIn(0f, 1f),
        gesture = gesture,
        gestureProgress = gestureProgress.coerceIn(0f, 1f),
        gazeDirected = gesture == AvatarGestureIntent.WINDOW_FOCUS,
        fallbackGazeX = gazeX.coerceIn(-1f, 1f),
        fallbackGazeY = gazeY.coerceIn(-1f, 1f)
    )

    private fun blink(t: Float, onset: Float): Float {
        val dt = t - onset
        return when {
            dt < 0f || dt > .22f -> 0f
            dt < .055f -> dt / .055f
            dt < .075f -> 1f
            else -> (1f - (dt - .075f) / .145f).coerceIn(0f, 1f)
        }
    }
}
