package at.heinz.deviceai.avatar

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.VectorPath
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Original procedural 2D head. No downloaded portrait, mesh, camera, audio engine or runtime state. */
class ContinuumAvatarRenderer {
    private val cyan = Fill.Solid(0xff77efff.toInt())
    private val violet = Fill.Solid(0xffb293ff.toInt())
    private val white = Fill.Solid(0xffd9fcff.toInt())
    private val dark = Fill.Solid(0xff061523.toInt())
    private val skin = Fill.Linear(-68f, -95f, 70f, 95f,
        intArrayOf(0xff12374b.toInt(), 0xff0a2136.toInt(), 0xff302050.toInt()))
    private val edge = Fill.Linear(-70f, 0f, 70f, 0f,
        intArrayOf(0xff55d9f5.toInt(), 0xffa78bff.toInt()))
    private val glow = Fill.Radial(0f, -8f, 130f,
        intArrayOf(0x2548c9ff, 0x153f4f99, 0x00000000))
    // All static paths and gradients are allocated once, never once per frame.
    private val face = VectorPath().moveTo(0f, -100f)
        .cubicTo(-49f, -103f, -70f, -75f, -67f, -32f)
        .lineTo(-59f, 35f).quadTo(-51f, 63f, -26f, 82f)
        .quadTo(0f, 99f, 26f, 82f).quadTo(51f, 63f, 59f, 35f)
        .lineTo(67f, -32f).cubicTo(70f, -75f, 49f, -103f, 0f, -100f).close()
    private val neck = VectorPath().moveTo(-26f, 77f).lineTo(-29f, 103f)
        .lineTo(-68f, 118f).quadTo(0f, 139f, 68f, 118f)
        .lineTo(29f, 103f).lineTo(26f, 77f).close()
    private val nose = VectorPath().moveTo(-9f, -19f).lineTo(-13f, 23f)
        .quadTo(0f, 32f, 13f, 23f).lineTo(9f, -19f)
    private val leftTemple = VectorPath().moveTo(-58f, -64f).lineTo(-43f, -48f)
        .lineTo(-23f, -52f).lineTo(-12f, -68f)
    private val rightTemple = VectorPath().moveTo(58f, -64f).lineTo(43f, -48f)
        .lineTo(23f, -52f).lineTo(12f, -68f)
    private val cheekLeft = VectorPath().moveTo(-57f, 1f).lineTo(-42f, 22f).lineTo(-23f, 30f)
    private val cheekRight = VectorPath().moveTo(57f, 1f).lineTo(42f, 22f).lineTo(23f, 30f)

    /** Cosmetic eyelid only. The animation never changes the app's work state. */
    internal fun eyeOpening(seconds: Float): Float {
        val cycle = ((seconds % 4.6f) + 4.6f) % 4.6f
        return if (cycle < 4.42f) 1f else abs(cycle - 4.51f).div(.09f).coerceIn(.08f, 1f)
    }

    fun draw(p: ContinuumPainter, width: Float, height: Float, pose: AvatarPose, seconds: Float, motion: Boolean) {
        if (width <= 0f || height <= 0f) return
        val scale = min(width / 255f, height / 272f)
        p.save()
        p.translate(width / 2f, height * .46f)
        p.scale(scale, scale)
        p.fillCircle(0f, -8f, 130f, glow)
        p.strokeOval(-106f, 112f, 106f, 137f, .8f, edge, .32f)
        p.fillPath(neck, skin)
        p.strokePath(neck, 1f, edge, .7f)
        // Small head sway is decoration, not an indication of thinking or learning.
        p.rotate(if (motion) sin(seconds * .7f) * .65f else 0f, 0f, 80f)
        p.translate(pose.gazeX * 3f, -pose.gazeY * 2f)
        p.fillOval(-77f, -20f, -61f, 23f, skin)
        p.fillOval(61f, -20f, 77f, 23f, skin)
        p.strokeOval(-77f, -20f, -61f, 23f, 1f, cyan, .55f)
        p.strokeOval(61f, -20f, 77f, 23f, 1f, violet, .55f)
        p.fillPath(face, skin)
        p.strokePath(face, 4f, edge, .10f)
        p.strokePath(face, 1.25f, edge, .92f)
        p.strokePath(leftTemple, .7f, cyan, .4f)
        p.strokePath(rightTemple, .7f, violet, .4f)
        p.strokePath(cheekLeft, .9f, cyan, .48f)
        p.strokePath(cheekRight, .9f, violet, .48f)
        p.strokePath(nose, .85f, cyan, .54f)
        p.drawLine(0f, -87f, 0f, -53f, .55f, edge, .4f)
        p.strokeArc(-41f, -89f, 41f, -43f, 205f, 130f, .7f, edge, .48f)
        p.fillCircle(0f, -75f, 2.1f, cyan, .85f)
        val eyelid = if (motion) eyeOpening(seconds) else 1f
        drawEye(p, -31f, eyelid, pose)
        drawEye(p, 31f, eyelid, pose)
        val brow = when (pose.expression) {
            AvatarExpression.ATTENTIVE -> -3f
            AvatarExpression.CONCERNED -> 3f
            AvatarExpression.FOCUSED -> 1.5f
            AvatarExpression.CURIOUS -> -2f
            AvatarExpression.NEUTRAL -> 0f
        }
        p.drawLine(-48f, -36f, -16f, -38f + brow, 1.3f, cyan, .7f)
        p.drawLine(16f, -38f + brow, 48f, -36f, 1.3f, violet, .7f)
        // Jaw energy comes only from the existing TTS envelope; cues change shape, never invent speech.
        val rounded = (pose.viseme == Viseme.O || pose.viseme == Viseme.U || pose.viseme == Viseme.CH)
        val mouthWidth = if (rounded && pose.visemeWeight > 0f) 10f else 21f
        val jaw = if (motion) pose.jawOpen else 0f
        val mouthHeight = 1f + jaw * 12f
        p.fillOval(-mouthWidth, 52f - mouthHeight, mouthWidth, 52f + mouthHeight, dark)
        p.strokeOval(-mouthWidth, 52f - mouthHeight, mouthWidth, 52f + mouthHeight, 1f, edge, .86f)
        p.drawLine(-16f, 73f, 16f, 73f, .65f, edge, .45f)
        p.restore()
    }

    private fun drawEye(p: ContinuumPainter, x: Float, opening: Float, pose: AvatarPose) {
        val eyeHeight = 6.5f * opening
        p.fillOval(x - 18f, -20f - eyeHeight, x + 18f, -20f + eyeHeight, dark)
        p.strokeOval(x - 18f, -20f - eyeHeight, x + 18f, -20f + eyeHeight, .8f, edge, .8f)
        val pupilX = x + pose.gazeX * 6f
        val pupilY = -20f - pose.gazeY * 2f
        p.fillOval(pupilX - 4.2f, pupilY - 4.2f * opening, pupilX + 4.2f, pupilY + 4.2f * opening, cyan, .9f)
        p.fillOval(pupilX - 1.7f, pupilY - 2f * opening, pupilX + 1.7f, pupilY + 2f * opening, white)
    }
}
