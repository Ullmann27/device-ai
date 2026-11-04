package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Jarvis sequence animations ("Ablauf-Animationen") on the platform-neutral [ContinuumPainter]:
 *
 *  - [command]: the owner's command chip shoots up from the composer into the hub and releases a shock ring, so it
 *    is visible at once that something has started ("sofortiges sichtbares Handeln").
 *  - [card] / [step] / [result]: glass cards fly in from the side with perspective, overshoot and a light trail and
 *    stack up as a live process view; [stepFinished] marks a step only with the outcome the caller reports.
 *  - [dismiss] flies a card out; [clear] drops everything immediately (cancel, session change, background).
 *
 * Pure presentation: every label is supplied by the caller from real app events (Jarvis Kern), nothing is invented,
 * no timers or threads; the host view advances time by calling [draw] with its monotonic clock.
 * Halo, lines, cards and text use [JarvisNeon] / [JarvisDesign] (design system, PR #479).
 */
class JarvisSequenceEffects {
    enum class Kind { STEP, ACCOUNT, DOCUMENT, RESULT, INFO }
    enum class Outcome { RUNNING, DONE, FAILED }

    private class Card(val id: String, val kind: Kind, var title: String, var subtitle: String?, val at: Long, val fromLeft: Boolean) {
        var outcome = Outcome.RUNNING
        var outcomeAt = 0L
        val live get() = outcome == Outcome.RUNNING && (kind == Kind.STEP || kind == Kind.INFO)
        var leavingAt = -1L
    }
    private class Command(val text: String, val at: Long)

    private val cards = ArrayList<Card>(MAX_CARDS + 2)
    private var flightAt = -1L
    private var flightCount = 0

    /** Brain-orb target of the data flight, as fractions of the overlay (default: hub). Set to the real orb centre. */
    var orbTargetX = .5f
    var orbTargetY = .30f

    /** Muster E: [count] document icons fly from the left on a glowing path INTO the brain orb (real reads only). */
    fun dataFlight(count: Int, nowMillis: Long) {
        flightCount = count.coerceIn(1, MAX_FLIGHT); flightAt = nowMillis; lastNow = nowMillis
    }
    private var command: Command? = null
    private var lastNow = 0L
    private var serial = 0

    /** A new owner command arrived. Starts the fly-to-hub chip and shock ring. */
    fun command(text: String, nowMillis: Long) {
        command = Command(clip(text, 80), nowMillis); lastNow = nowMillis
    }

    /** Any card flying in (e.g. Gmail account card = [Kind.ACCOUNT], PDF = [Kind.DOCUMENT]). Same id updates text. */
    fun card(id: String, kind: Kind, title: String, subtitle: String?, nowMillis: Long): Boolean {
        if (!validId(id)) return false
        cards.firstOrNull { it.id == id && it.leavingAt < 0 }?.let { it.title = clip(title, 60); it.subtitle = subtitle?.let { s -> clip(s, 80) }; return true }
        val active = cards.filter { it.leavingAt < 0 }
        if (active.size >= MAX_CARDS) active.first().leavingAt = nowMillis
        cards += Card(id, kind, clip(title, 60), subtitle?.let { clip(it, 80) }, nowMillis, serial++ % 2 == 1)
        lastNow = nowMillis
        return true
    }

    fun step(id: String, title: String, nowMillis: Long) = card(id, Kind.STEP, title, null, nowMillis)
    fun result(id: String, title: String, subtitle: String?, nowMillis: Long) = card(id, Kind.RESULT, title, subtitle, nowMillis)

    /** Reports the real outcome of a step; [ok] false shows a failure mark, never a success. */
    fun stepFinished(id: String, ok: Boolean, nowMillis: Long): Boolean {
        val c = cards.firstOrNull { it.id == id && it.leavingAt < 0 } ?: return false
        if (c.outcome != Outcome.RUNNING) return false
        c.outcome = if (ok) Outcome.DONE else Outcome.FAILED; c.outcomeAt = nowMillis
        return true
    }

    fun dismiss(id: String, nowMillis: Long): Boolean {
        val c = cards.firstOrNull { it.id == id && it.leavingAt < 0 } ?: return false
        c.leavingAt = nowMillis; return true
    }

    fun dismissAll(nowMillis: Long) { for (c in cards) if (c.leavingAt < 0) c.leavingAt = nowMillis }

    fun clear() { cards.clear(); command = null; flightAt = -1L }

    /** True while something moves; the host may stop invalidating when false. */
    fun isAnimating(nowMillis: Long): Boolean {
        if (flightAt >= 0 && nowMillis - flightAt < flightDuration()) return true
        command?.let { if (nowMillis - it.at < COMMAND_MS) return true }
        return cards.any { nowMillis - it.at < FLY_MS + 200 || it.leavingAt >= 0 || it.live ||
            nowMillis - it.outcomeAt < 500 }
    }

    val cardCount: Int get() = cards.count { it.leavingAt < 0 }

    fun draw(p: ContinuumPainter, width: Float, height: Float, nowMillis: Long) {
        if (width <= 0f || height <= 0f) return
        val now = maxOf(nowMillis, 0L)
        cards.removeAll { it.leavingAt >= 0 && now - it.leavingAt > LEAVE_MS }
        if (command != null && now - command!!.at > COMMAND_MS) command = null
        val hubX = width / 2f; val hubY = height * .30f
        drawCommand(p, width, height, hubX, hubY, now)
        drawFlight(p, width, height, now)
        val cw = min(width - 32f, 340f); val ch = 58f; val gap = 10f
        var slot = 0
        for (c in cards) {
            val targetY = height * .44f + slot * (ch + gap)
            if (c.leavingAt < 0) slot++
            drawCard(p, c, width, cw, ch, targetY, hubX, hubY, now)
        }
    }

    private fun drawCommand(p: ContinuumPainter, w: Float, h: Float, hubX: Float, hubY: Float, now: Long) {
        val c = command ?: return
        val t = ((now - c.at).toFloat() / COMMAND_MS).coerceIn(0f, 1f)
        val fly = (t / .45f).coerceIn(0f, 1f)
        val e = 1f - (1f - fly) * (1f - fly) * (1f - fly)
        val sx = w / 2f; val sy = h - 40f
        // curved path from the composer up into the hub
        val x = sx + (hubX - sx) * e + sin(e * PI.toFloat()) * w * .08f
        val y = sy + (hubY - sy) * e
        if (fly < 1f) {
            for (k in 1..6) {   // light trail
                val te = (e - k * .05f).coerceAtLeast(0f)
                val tx = sx + (hubX - sx) * te + sin(te * PI.toFloat()) * w * .08f
                val ty = sy + (hubY - sy) * te
                JarvisNeon.line(p, tx, ty, x, y, if (k % 2 == 0) JarvisDesign.Colors.violet else JarvisDesign.Colors.cyan,
                    (7 - k) * .5f, JarvisDesign.Glow.SUBTLE)
            }
            val style = JarvisDesign.Type.caption
            val tw = min(p.measureText(c.text, style) + 28f, w - 24f)
            val s = .6f + .4f * (1f - e)
            p.save(); p.scale(s, s, x, y)
            JarvisNeon.glassCard(p, x - tw / 2f, y - 16f, x + tw / 2f, y + 16f, 16f, JarvisDesign.Glow.STRONG, active = true)
            p.save(); p.clipRect(x - tw / 2f + 8f, y - 16f, x + tw / 2f - 8f, y + 16f)
            p.drawText(c.text, x, y + 4f, style, Fill.Solid(JarvisDesign.Colors.text), TextAlign.CENTER)
            p.restore(); p.restore()
        } else {
            // arrival: shock rings and radial sparks at the hub
            val k = ((t - .45f) / .55f).coerceIn(0f, 1f)
            val fade = 1f - k
            p.setAdditive(true)
            JarvisNeon.halo(p, hubX, hubY, 30f + k * 60f, JarvisDesign.Colors.cyan, .45f * fade)
            for (ring in 0..1) {
                val rk = (k - ring * .18f).coerceIn(0f, 1f)
                p.strokeCircle(hubX, hubY, 12f + rk * min(w, h) * .32f, 2.2f * (1f - rk) + .4f,
                    Fill.Solid(if (ring == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet), (1f - rk) * .9f)
            }
            for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val r0 = 10f + k * 40f; val r1 = r0 + 14f * fade
                p.drawLine(hubX + cos(a) * r0, hubY + sin(a) * r0, hubX + cos(a) * r1, hubY + sin(a) * r1, 1.2f,
                    Fill.Solid(if (i % 2 == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet), fade)
            }
            p.setAdditive(false)
        }
    }

    private fun drawCard(p: ContinuumPainter, c: Card, w: Float, cw: Float, ch: Float, targetY: Float,
                         hubX: Float, hubY: Float, now: Long) {
        val inT = ((now - c.at).toFloat() / FLY_MS).coerceIn(0f, 1f)
        val ein = backOut(inT)
        val outT = if (c.leavingAt >= 0) ((now - c.leavingAt).toFloat() / LEAVE_MS).coerceIn(0f, 1f) else 0f
        val side = if (c.fromLeft) -1f else 1f
        val startX = w / 2f + side * (w / 2f + cw)
        val cx = startX + (w / 2f - startX) * ein - side * outT * outT * (w / 2f + cw)
        val cy = targetY + ch / 2f - (1f - ein) * 40f
        val alpha = (inT * 3f).coerceIn(0f, 1f) * (1f - outT)
        if (alpha <= 0f) return
        // perspective: card turns in edge-first, then faces the viewer
        val sx = (.25f + .75f * ein.coerceIn(0f, 1.1f)) * (1f - outT * .5f)
        val l = cx - cw / 2f; val r = cx + cw / 2f; val t = cy - ch / 2f; val b = cy + ch / 2f
        if (inT < 1f) {
            p.setAdditive(true)
            JarvisNeon.line(p, cx - side * cw * .5f, cy, cx - side * (cw * .5f + 90f * (1f - inT)), cy,
                JarvisDesign.Colors.cyan, 3f * (1f - inT), JarvisDesign.Glow.STRONG)
            p.setAdditive(false)
        }
        // link to the hub while the step runs (live process line)
        if (c.live && c.leavingAt < 0 && inT >= 1f) {
            val dash = ((now - c.at) % 900L) / 900f
            p.setAdditive(true)
            p.drawLine(hubX, hubY, cx, t, .8f, Fill.Solid(JarvisDesign.Colors.violet), .25f)
            val px = hubX + (cx - hubX) * dash; val py = hubY + (t - hubY) * dash
            p.fillCircle(px, py, 2.2f, Fill.Solid(JarvisDesign.Colors.cyan), .9f)
            p.setAdditive(false)
        }
        p.save(); p.scale(sx, 1f, cx, cy)
        val flash = if (inT in .75f..1f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL
        JarvisNeon.glassCard(p, l, t, r, b, 14f, flash, active = c.live && inT >= 1f)
        p.restore()
        if (sx < .7f || alpha < .3f) return
        p.save(); p.clipRect(l + 4f, t, r - 4f, b)
        drawIcon(p, c, l + 28f, cy, now)
        val tx = l + 52f
        p.drawText(c.title, tx, if (c.subtitle == null) cy + 5f else cy - 3f, JarvisDesign.Type.title, Fill.Solid(JarvisDesign.Colors.text), TextAlign.LEFT)
        c.subtitle?.let { p.drawText(it, tx, cy + 15f, JarvisDesign.Type.caption, Fill.Solid(JarvisDesign.Colors.textSecondary), TextAlign.LEFT) }
        p.restore()
    }

    private fun drawIcon(p: ContinuumPainter, c: Card, x: Float, y: Float, now: Long) {
        val cyan = Fill.Solid(JarvisDesign.Colors.cyan); val violet = Fill.Solid(JarvisDesign.Colors.violet)
        when (c.outcome) {
            Outcome.DONE -> { p.strokeCircle(x, y, 11f, 1.4f, Fill.Solid(JarvisDesign.Colors.ok))
                p.drawLine(x - 5f, y, x - 1.5f, y + 4f, 1.8f, Fill.Solid(JarvisDesign.Colors.ok)); p.drawLine(x - 1.5f, y + 4f, x + 5.5f, y - 4f, 1.8f, Fill.Solid(JarvisDesign.Colors.ok)); return }
            Outcome.FAILED -> { p.strokeCircle(x, y, 11f, 1.4f, Fill.Solid(JarvisDesign.Colors.error))
                p.drawLine(x - 4f, y - 4f, x + 4f, y + 4f, 1.8f, Fill.Solid(JarvisDesign.Colors.error)); p.drawLine(x + 4f, y - 4f, x - 4f, y + 4f, 1.8f, Fill.Solid(JarvisDesign.Colors.error)); return }
            Outcome.RUNNING -> Unit
        }
        when (c.kind) {
            Kind.ACCOUNT -> { p.strokeRoundRect(x - 10f, y - 7f, x + 10f, y + 7f, 2f, 1.4f, cyan)
                p.drawLine(x - 10f, y - 6f, x, y + 1f, 1.3f, cyan); p.drawLine(x, y + 1f, x + 10f, y - 6f, 1.3f, cyan) }
            Kind.DOCUMENT -> { p.strokeRoundRect(x - 8f, y - 10f, x + 8f, y + 10f, 2f, 1.4f, violet)
                for (i in 0..2) p.drawLine(x - 4.5f, y - 4f + i * 4.5f, x + 4.5f, y - 4f + i * 4.5f, 1.1f, violet) }
            Kind.RESULT -> { p.strokeCircle(x, y, 10f, 1.4f, cyan); p.fillCircle(x, y, 3.2f, cyan) }
            Kind.STEP, Kind.INFO -> {
                // spinner arc: something is actually running (caller has not reported an outcome yet)
                val a = (now % 1200L) / 1200f * 360f
                p.strokeArc(x - 10f, y - 10f, x + 10f, y + 10f, a, 250f, 1.8f, cyan)
                p.fillCircle(x, y, 2.4f, violet)
            }
        }
    }

    private fun flightDuration() = FLIGHT_MS + (flightCount - 1) * JarvisMotion.Durations.STAGGER + 300L

    private fun drawFlight(p: ContinuumPainter, w: Float, h: Float, now: Long) {
        if (flightAt < 0) return
        val elapsed = now - flightAt
        if (elapsed >= flightDuration()) { flightAt = -1L; return }
        val tx = w * orbTargetX; val ty = h * orbTargetY
        for (i in 0 until flightCount) {
            val sx = -12f; val sy = h * (.18f + .5f * ((i * .618034f) % 1f))
            val cx = w * .25f; val cy = sy - h * .12f            // control point: arc upwards
            val t = ((elapsed - i * JarvisMotion.Durations.STAGGER).toFloat() / FLIGHT_MS).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val e = JarvisMotion.Easing.easeInOutCubic(t)
            fun bx(u: Float) = (1 - u) * (1 - u) * sx + 2 * (1 - u) * u * cx + u * u * tx
            fun by(u: Float) = (1 - u) * (1 - u) * sy + 2 * (1 - u) * u * cy + u * u * ty
            // glowing track (drawn behind the icon, fades once the icon is absorbed)
            var px = bx(0f); var py = by(0f)
            if (t < 1f) for (k in 1..16) {
                val u = e * k / 16f
                val nx = bx(u); val ny = by(u)
                JarvisNeon.line(p, px, py, nx, ny, if (i % 2 == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet,
                    .8f, JarvisDesign.Glow.SUBTLE)
                px = nx; py = ny
            }
            if (t < 1f) {
                val x = bx(e); val y = by(e); val s = 1f - .65f * e    // shrinks as it enters the orb
                p.save(); p.scale(s, s, x, y)
                p.fillRoundRect(x - 8f, y - 10f, x + 8f, y + 10f, 2f, Fill.Solid(JarvisDesign.Colors.surface), .9f)
                p.strokeRoundRect(x - 8f, y - 10f, x + 8f, y + 10f, 2f, 1.2f, Fill.Solid(JarvisDesign.Colors.cyan))
                for (l in 0..2) p.drawLine(x - 4.5f, y - 4f + l * 4f, x + 4.5f, y - 4f + l * 4f, .9f, Fill.Solid(JarvisDesign.Colors.cyan))
                p.restore()
            } else {
                val k = ((elapsed - i * JarvisMotion.Durations.STAGGER - FLIGHT_MS) / 300f).coerceIn(0f, 1f)
                p.setAdditive(true)
                JarvisNeon.halo(p, tx, ty, 18f + 22f * k, JarvisDesign.Colors.cyan, .5f * (1f - k))
                p.setAdditive(false)
            }
        }
    }

    private fun backOut(t: Float): Float { val s = 1.7f; val u = t - 1f; return 1f + u * u * ((s + 1f) * u + s) }
    private fun clip(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
    private fun validId(id: String) = id.length in 1..96 && id.all { it.isLetterOrDigit() && it.code < 128 || it in "._:-" }

    companion object {
        const val MAX_CARDS = 4
        const val MAX_FLIGHT = 8
        const val FLIGHT_MS = 900L
        const val FLY_MS = JarvisMotion.Durations.PANEL_FLY_IN
        const val LEAVE_MS = 380L
        const val COMMAND_MS = 1100L
    }
}
