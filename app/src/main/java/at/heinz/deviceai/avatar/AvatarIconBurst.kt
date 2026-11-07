package at.heinz.deviceai.avatar

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.JarvisDesign
import at.heinz.deviceai.ui.JarvisNeon
import at.heinz.deviceai.ui.VectorPath
import kotlin.math.cos
import kotlin.math.sin

/** Shared native/fallback icon flight. */
class AvatarIconBurst {
    private var startedAt = -1L
    private val file = VectorPath().moveTo(-.38f, -.5f).lineTo(.18f, -.5f)
        .lineTo(.38f, -.3f).lineTo(.38f, .5f).lineTo(-.38f, .5f).close()
    private val fold = VectorPath().moveTo(.18f, -.5f).lineTo(.18f, -.3f).lineTo(.38f, -.3f)
    private val folder = VectorPath().moveTo(-.5f, -.32f).lineTo(-.12f, -.32f)
        .lineTo(-.02f, -.2f).lineTo(.5f, -.2f).lineTo(.5f, .36f).lineTo(-.5f, .36f).close()
    private val glass = Fill.Solid(0xff0a2a5c.toInt())
    private val back = Fill.Solid(0xff0088ff.toInt())
    private val cyan = Fill.Solid(JarvisDesign.Colors.cyan)
    private val violet = Fill.Solid(JarvisDesign.Colors.violet)
    private val core = Fill.Solid(0xffeafcff.toInt())

    fun start(nowMillis: Long) { startedAt = nowMillis }
    fun clear() { startedAt = -1L }
    fun active(nowMillis: Long) = startedAt >= 0L && nowMillis - startedAt in 0L..1_950L

    fun draw(p: ContinuumPainter, nowMillis: Long, bloom: Float = .6f) {
        if (!active(nowMillis)) return
        for (i in 0 until 9) {
            val t = (nowMillis - startedAt - i * 50L) / 1500f
            if (t <= 0f || t >= 1f) continue
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            val angle = -.95f + 1.9f * ((i * 7) % 9) / 8f
            val distance = 150f + (i * 37 % 70)
            val x = 6f + cos(angle) * distance * e
            val y = -40f + sin(angle) * distance * .85f * e
            val size = 8f + 42f * e * e
            val alpha = (if (t < .12f) t / .12f else 1f) *
                (1f - ((t - .7f) / .3f).coerceIn(0f, 1f))
            val isFolder = i % 3 == 0
            val color = if (i % 2 == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet
            JarvisNeon.bloom(p, x, y, size * .22f, color, alpha * (.25f + .2f * bloom))
            p.save(); p.translate(x, y); p.rotate(angle * 18f + (1f - e) * 25f); p.scale(size, size)
            val path = if (isFolder) folder else file
            p.save(); p.translate(-.07f, -.05f); p.fillPath(path, back, .25f * alpha); p.restore()
            p.fillPath(path, glass, .45f * alpha)
            p.setAdditive(true)
            p.fillPath(path, back, .18f * alpha)
            p.strokePath(path, .1f, if (i % 2 == 0) cyan else violet, .25f * alpha)
            p.strokePath(path, .035f, core, .95f * alpha)
            if (!isFolder) p.strokePath(fold, .035f, core, .8f * alpha)
            p.setAdditive(false); p.restore()
        }
    }
}
