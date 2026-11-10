package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.abs
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
    enum class Outcome { RUNNING, DONE, FAILED, UNVERIFIED }

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

    private class Envelope(val label: String, val targets: List<String>, val at: Long)
    private class Pills(val items: List<String>, val at: Long)
    private var envelope: Envelope? = null
    private var pills: Pills? = null
    private var alarmAt = -1L
    private var alarmMs = 0L

    /** Avatar centre for the question pills, as fractions of the overlay. */
    var avatarX = .5f
    var avatarY = .40f
    /** Glow level; set to [JarvisDesign.Glow.OFF] for power save / reduced effects. */
    var glow: JarvisDesign.Glow = JarvisDesign.Glow.NORMAL

    /**
     * Muster F: 3-D mail envelope out of depth (0.5 s), flap opens (0.3 s), the document slides up and laser lines
     * (Muster D) draw to one label per real follow-up action in [targets] (max 4), e.g. "Gmail analysieren".
     */
    fun envelope(label: String, targets: List<String>, nowMillis: Long) {
        envelope = Envelope(clip(label, 40), targets.take(4).map { clip(it, 28) }, nowMillis); lastNow = nowMillis
    }
    fun closeEnvelope() { envelope = null }

    /** Muster G: answer options fly from the screen edges to the avatar, no fade, hard deceleration, then float. */
    fun questionPills(options: List<String>, nowMillis: Long) {
        pills = Pills(options.take(6).map { clip(it, 32) }, nowMillis); lastNow = nowMillis
    }
    fun clearPills() { pills = null; answerPills = emptyList() }

    /** Muster G with Kern's [at.heinz.deviceai.cockpit.AnswerPill]s: labels from the safe gate, disabled ones marked. */
    private var answerPills: List<at.heinz.deviceai.cockpit.AnswerPill> = emptyList()
    fun answerPills(pills: List<at.heinz.deviceai.cockpit.AnswerPill>, nowMillis: Long) {
        answerPills = pills.take(6); questionPills(pillLabels(answerPills), nowMillis)
    }

    data class PillBox(val first: Float, val second: Float, val third: Float, val fourth: Float)

    /** Landed bounds per pill index (left, top, right, bottom) in overlay pixels. */
    fun pillBounds(w: Float, h: Float, nowMillis: Long): Map<Int, PillBox> {
        val pl = pills ?: return emptyMap()
        return pl.items.mapIndexedNotNull { i, text ->
            val g = pillGeometry(i, text, pillWidths[text] ?: estimateWidth(text, w), w, h, nowMillis - pl.at)
            if (g == null) null else i to PillBox(g.x - g.pw / 2f, g.y - PILL_HALF_H, g.x + g.pw / 2f, g.y + PILL_HALF_H)
        }.toMap()
    }

    /** Tap → pill, only when landed and enabled; the caller forwards it to PillActionGate.request (never executes). */
    fun pillAt(x: Float, y: Float, w: Float, h: Float, nowMillis: Long): at.heinz.deviceai.cockpit.AnswerPill? {
        val pl = pills ?: return null
        for ((i, b) in pillBounds(w, h, nowMillis)) {
            val landed = nowMillis - pl.at - JarvisMotion.staggerStart(i) >= JarvisMotion.Presets.pill(JarvisMotion.Direction.LEFT).durationMs
            val pill = answerPills.getOrNull(i) ?: continue
            if (landed && pill.enabled && x in b.first..b.third && y in b.second..b.fourth) return pill
        }
        return null
    }

    private val pillWidths = HashMap<String, Float>()
    private fun estimateWidth(text: String, w: Float) = min(text.length * 7.5f + 28f, w * .44f)

    private class PillGeo(val x: Float, val y: Float, val pw: Float, val t: Float)
    private fun pillGeometry(i: Int, text: String, pw: Float, w: Float, h: Float, sinceStart: Long): PillGeo? {
        val left = i % 2 == 0; val row = i / 2
        val ax = w * avatarX; val ay = h * avatarY
        val fx = if (left) ax - w * .2f - pw / 2f + row * 6f else ax + w * .2f + pw / 2f - row * 6f
        val fy = ay - 40f + row * 46f + (if (left) 0f else 20f)
        val fxc = fx.coerceIn(pw / 2f + 4f, w - pw / 2f - 4f)
        val spec = JarvisMotion.Presets.pill(if (left) JarvisMotion.Direction.LEFT else JarvisMotion.Direction.RIGHT)
        val el = sinceStart - JarvisMotion.staggerStart(i)
        if (el < 0) return null
        val t = (el.toFloat() / spec.durationMs).coerceIn(0f, 1f)
        val startX = if (left) -pw else w + pw
        val float = if (t >= 1f) sin(el / 700f + i * 1.7f) * 1f else 0f   // offset floating ~1 px
        return PillGeo(startX + (fxc - startX) * spec.easing(t), fy + float, pw, t)
    }

    /** Muster H: alarm (overload/threat) – 2 px shake, glow x3, binary cascade. Host may apply [shakeOffsetX]/[shakeOffsetY]. */
    fun alarm(nowMillis: Long, durationMs: Long = 2400L) { alarmAt = nowMillis; alarmMs = durationMs.coerceIn(300L, 10_000L) }
    fun alarmActive(nowMillis: Long) = alarmAt >= 0 && nowMillis - alarmAt < alarmMs
    /** Glow multiplier for other renderers (avatar/orb) while the alarm runs: up to 3. */
    fun glowBoost(nowMillis: Long) = if (alarmActive(nowMillis)) 3f else 1f
    fun shakeOffsetX(nowMillis: Long) = if (alarmActive(nowMillis)) sin(nowMillis * .09f) * 2f else 0f
    fun shakeOffsetY(nowMillis: Long) = if (alarmActive(nowMillis)) cos(nowMillis * .113f) * 2f else 0f

    /** Brain-orb target of the data flight, as fractions of the overlay (default: hub). Set to the real orb centre. */
    var orbTargetX = .5f
    var orbTargetY = .30f

    /** Muster E: [count] document icons fly from the left on a glowing path INTO the brain orb (real reads only). */
    fun dataFlight(count: Int, nowMillis: Long) {
        flightCount = count.coerceIn(1, MAX_FLIGHT); flightAt = nowMillis; lastNow = nowMillis
        arrivalPulseAt = nowMillis + FLIGHT_MS // neurons flash + orb pulses when the first icon arrives
    }
    /** Number of document icons of the running flight (0 when none). */
    val flightDocuments: Int get() = if (flightAt >= 0) flightCount else 0

    // ---- Orb inputs (owned by Jarvis Gedächtnis). Driven only by real memory events via BrainOrbAnimationRenderer. ----
    private var arrivalPulseAt = -1L
    private var pulseAt = -1L
    private var pulseStrength = 0f
    private var recallAt = -1L
    private var recallScore = 0f
    /** Hits of the last real recall (0 = none). */
    var recallCount = 0
        private set
    private var hypothesisAt = -1L

    /** Orb pulse / neuron flash, e.g. a confirmed memory write. [strength] 0..1. */
    fun orbPulse(strength: Float, nowMillis: Long) { pulseAt = nowMillis; pulseStrength = strength.coerceIn(0f, 1f) }
    /** Light point(s) for a real recall: brightness = rank-1 score; nothing without hits/score. */
    fun recallLight(count: Int, topScore: Float?, nowMillis: Long) {
        if (count <= 0 || topScore == null) { recallAt = -1L; recallCount = 0; recallScore = 0f; return }
        recallAt = nowMillis; recallCount = count; recallScore = topScore.coerceIn(0f, 1f)
    }
    /** Marks that an UNCONFIRMED hypothesis was stored (dashed violet ring, never a confirmed flash). */
    fun hypothesisMarker(nowMillis: Long) { hypothesisAt = nowMillis; pulseAt = -1L }

    fun orbPulseLevel(nowMillis: Long): Float {
        fun decay(at: Long, strength: Float): Float {
            if (at < 0 || nowMillis < at) return 0f
            val t = (nowMillis - at).toFloat() / PULSE_MS
            return if (t >= 1f) 0f else strength * (1f - t)
        }
        return maxOf(decay(arrivalPulseAt, 1f), decay(pulseAt, pulseStrength))
    }
    fun recallBrightness(nowMillis: Long): Float =
        if (recallAt >= 0 && nowMillis >= recallAt && nowMillis - recallAt < RECALL_MS) recallScore else 0f
    fun hypothesisVisible(nowMillis: Long) = hypothesisAt >= 0 && nowMillis >= hypothesisAt && nowMillis - hypothesisAt < HYPOTHESIS_MS

    private fun drawOrbSignals(p: ContinuumPainter, w: Float, h: Float, now: Long) {
        val cx = w * orbTargetX; val cy = h * orbTargetY
        val pulse = orbPulseLevel(now)
        if (pulse > 0f) {
            p.setAdditive(true)
            JarvisNeon.halo(p, cx, cy, 30f + 18f * pulse, JarvisDesign.Colors.cyan, .55f * pulse)
            for (k in 0 until 6) { // neuron flashes: fixed radial directions, length/alpha follow the real pulse
                val a = (k * 60f + 15f) * (Math.PI.toFloat() / 180f)
                JarvisNeon.line(p, cx + kotlin.math.cos(a) * 10f, cy + kotlin.math.sin(a) * 10f,
                    cx + kotlin.math.cos(a) * (18f + 22f * pulse), cy + kotlin.math.sin(a) * (18f + 22f * pulse),
                    JarvisDesign.Colors.cyan, .9f, JarvisDesign.Glow.SUBTLE)
            }
            p.setAdditive(false)
        }
        val b = recallBrightness(now)
        if (b > 0f) {
            p.setAdditive(true)
            JarvisNeon.halo(p, cx + 14f, cy - 10f, 6f + 6f * b, JarvisDesign.Colors.cyan, b)
            p.setAdditive(false)
        }
        if (hypothesisVisible(now)) for (k in 0 until 12) if (k % 2 == 0) { // dashed violet ring = unconfirmed
            val a0 = k * 30f * (Math.PI.toFloat() / 180f); val a1 = (k * 30f + 22f) * (Math.PI.toFloat() / 180f)
            JarvisNeon.line(p, cx + kotlin.math.cos(a0) * 34f, cy + kotlin.math.sin(a0) * 34f,
                cx + kotlin.math.cos(a1) * 34f, cy + kotlin.math.sin(a1) * 34f, JarvisDesign.Colors.violet, 1f, JarvisDesign.Glow.SUBTLE)
        }
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
        if (active.size >= MAX_CARDS) {
            // Keep the canonical turn scene while bounded details rotate out.
            (active.firstOrNull { !it.id.endsWith(".main_scene") } ?: active.first()).leavingAt = nowMillis
        }
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

    fun stepUnverified(id: String, nowMillis: Long): Boolean {
        val c = cards.firstOrNull { it.id == id && it.leavingAt < 0 } ?: return false
        if (c.outcome == Outcome.UNVERIFIED) return false
        c.outcome = Outcome.UNVERIFIED
        c.outcomeAt = nowMillis
        return true
    }

    fun dismiss(id: String, nowMillis: Long): Boolean {
        val c = cards.firstOrNull { it.id == id && it.leavingAt < 0 } ?: return false
        c.leavingAt = nowMillis; return true
    }

    fun dismissAll(nowMillis: Long) { for (c in cards) if (c.leavingAt < 0) c.leavingAt = nowMillis }

    fun clear() { cards.clear(); command = null; flightAt = -1L; arrivalPulseAt = -1L; pulseAt = -1L; recallAt = -1L; recallCount = 0; hypothesisAt = -1L; envelope = null; pills = null; alarmAt = -1L }

    /** True while something moves; the host may stop invalidating when false. */
    fun isAnimating(nowMillis: Long): Boolean {
        if (flightAt >= 0 && nowMillis - flightAt < flightDuration()) return true
        if (orbPulseLevel(nowMillis) > 0f || recallBrightness(nowMillis) > 0f || hypothesisVisible(nowMillis)) return true
        if (envelope != null || pills != null || alarmActive(nowMillis)) return true
        command?.let { if (nowMillis - it.at < COMMAND_MS) return true }
        return cards.any { nowMillis - it.at < FLY_MS + 200 || it.leavingAt >= 0 || it.live ||
            nowMillis - it.outcomeAt < 500 }
    }

    val cardCount: Int get() = cards.count { it.leavingAt < 0 }

    /** Cards and command chip docked into an open work window (not drawn here; orb signals stay). */
    var docked = false
    /** Protected regions (dp: l, t, r, b) – the original head and the orb. No card or command chip is drawn over them. */
    private val protectedBoxes = FloatArray(8)
    private var protectedCount = 0

    /** The host's real head and orb regions in dp (null clears them, e.g. the legacy composition without regions). */
    fun setProtectedAreas(head: FloatArray?, orb: FloatArray?) {
        protectedCount = 0
        for (box in listOfNotNull(head, orb)) {
            if (box.size < 4) continue
            System.arraycopy(box, 0, protectedBoxes, 4 * protectedCount++, 4)
        }
    }

    private fun overlapsProtected(l: Float, t: Float, r: Float, b: Float): Boolean {
        for (i in 0 until protectedCount) {
            val o = 4 * i
            if (l < protectedBoxes[o + 2] && r > protectedBoxes[o] && t < protectedBoxes[o + 3] && b > protectedBoxes[o + 1]) return true
        }
        return false
    }

    private fun circleOverlapsProtected(cx: Float, cy: Float, outer: Float, inner: Float = 0f): Boolean {
        for (i in 0 until protectedCount) {
            val o = 4 * i
            val l = protectedBoxes[o]; val t = protectedBoxes[o + 1]
            val r = protectedBoxes[o + 2]; val b = protectedBoxes[o + 3]
            val nearX = maxOf(l - cx, 0f, cx - r)
            val nearY = maxOf(t - cy, 0f, cy - b)
            val farX = maxOf(abs(l - cx), abs(r - cx))
            val farY = maxOf(abs(t - cy), abs(b - cy))
            if (nearX * nearX + nearY * nearY <= outer * outer &&
                farX * farX + farY * farY >= inner * inner) return true
        }
        return false
    }

    private fun lineOverlapsProtected(x0: Float, y0: Float, x1: Float, y1: Float, pad: Float): Boolean {
        val dx = x1 - x0; val dy = y1 - y0
        for (i in 0 until protectedCount) {
            val o = 4 * i
            val l = protectedBoxes[o] - pad; val t = protectedBoxes[o + 1] - pad
            val r = protectedBoxes[o + 2] + pad; val b = protectedBoxes[o + 3] + pad
            var start = 0f; var end = 1f
            if (dx == 0f) {
                if (x0 < l || x0 > r) continue
            } else {
                val a = (l - x0) / dx; val z = (r - x0) / dx
                start = maxOf(start, minOf(a, z)); end = minOf(end, maxOf(a, z))
                if (start > end) continue
            }
            if (dy == 0f) {
                if (y0 < t || y0 > b) continue
            } else {
                val a = (t - y0) / dy; val z = (b - y0) / dy
                start = maxOf(start, minOf(a, z)); end = minOf(end, maxOf(a, z))
                if (start > end) continue
            }
            return true
        }
        return false
    }
    /** Boxes (dp: l, t, r, b) of the cards drawn in the last frame – reused buffer, no per-frame allocation. */
    private val drawnBoxes = FloatArray(4 * (MAX_CARDS + 4))
    var drawnCardCount = 0; private set
    fun drawnCardBox(i: Int): FloatArray = drawnBoxes.copyOfRange(4 * i, 4 * i + 4)

    fun draw(p: ContinuumPainter, width: Float, height: Float, nowMillis: Long) {
        if (width <= 0f || height <= 0f) return
        val now = maxOf(nowMillis, 0L)
        cards.removeAll { it.leavingAt >= 0 && now - it.leavingAt > LEAVE_MS }
        if (command != null && now - command!!.at > COMMAND_MS) command = null
        val hubX = width / 2f; val hubY = height * .30f
        drawnCardCount = 0
        // The command chip ends at the hub: never over the head or the orb.
        if (!docked && !overlapsProtected(hubX - 1f, hubY - 1f, hubX + 1f, hubY + 1f)) drawCommand(p, width, height, hubX, hubY, now)
        drawFlight(p, width, height, now)
        drawOrbSignals(p, width, height, now)
        drawAlarm(p, width, height, now)
        p.save(); p.translate(shakeOffsetX(now), shakeOffsetY(now))
        drawEnvelope(p, width, height, now)
        drawPills(p, width, height, now)
        p.restore()
        val cw = min(width - 32f, 340f); val ch = 58f; val gap = 10f
        if (docked) return
        // Geometric check of the whole stack: where any card would cover the head or the orb, the cards stay off this
        // surface – their steps are shown in the cockpit's process panel or the work window's status strip instead.
        var protectedSlot = 0
        for (c in cards) {
            val top = height * .44f + protectedSlot * (ch + gap)
            if (overlapsProtected(width / 2f - cw / 2f, top, width / 2f + cw / 2f, top + ch)) return
            if (c.leavingAt < 0) protectedSlot++
        }
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
                val pad = ((7 - k) * .5f + JarvisDesign.Glow.SUBTLE.haloWidth) / 2f
                if (lineOverlapsProtected(tx, ty, x, y, pad)) continue
                JarvisNeon.line(p, tx, ty, x, y, if (k % 2 == 0) JarvisDesign.Colors.violet else JarvisDesign.Colors.cyan,
                    (7 - k) * .5f, JarvisDesign.Glow.SUBTLE)
            }
            val style = JarvisDesign.Type.caption
            val tw = min(p.measureText(c.text, style) + 28f, w - 24f)
            val s = .6f + .4f * (1f - e)
            // Include the material's outer rim.
            val halfWidth = (tw / 2f + 14f) * s
            val halfHeight = (16f + 14f) * s
            if (overlapsProtected(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)) return
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
            val haloRadius = 30f + k * 60f
            if (!circleOverlapsProtected(hubX, hubY, haloRadius)) {
                JarvisNeon.halo(p, hubX, hubY, haloRadius, JarvisDesign.Colors.cyan, .45f * fade)
            }
            for (ring in 0..1) {
                val rk = (k - ring * .18f).coerceIn(0f, 1f)
                val radius = 12f + rk * min(w, h) * .32f
                val stroke = 2.2f * (1f - rk) + .4f
                if (circleOverlapsProtected(hubX, hubY, radius + stroke / 2f,
                        (radius - stroke / 2f).coerceAtLeast(0f))) continue
                p.strokeCircle(hubX, hubY, radius, stroke,
                    Fill.Solid(if (ring == 0) JarvisDesign.Colors.cyan else JarvisDesign.Colors.violet), (1f - rk) * .9f)
            }
            for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val r0 = 10f + k * 40f; val r1 = r0 + 14f * fade
                val x0 = hubX + cos(a) * r0; val y0 = hubY + sin(a) * r0
                val x1 = hubX + cos(a) * r1; val y1 = hubY + sin(a) * r1
                if (lineOverlapsProtected(x0, y0, x1, y1, .6f)) continue
                p.drawLine(x0, y0, x1, y1, 1.2f,
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
        val halfWidth = maxOf(cw / 2f, (cw / 2f + 14f) * sx)
        if (overlapsProtected(cx - halfWidth, t - 14f, cx + halfWidth, b + 14f)) return
        if (drawnCardCount < drawnBoxes.size / 4) {
            val o = 4 * drawnCardCount++
            drawnBoxes[o] = l; drawnBoxes[o + 1] = t; drawnBoxes[o + 2] = r; drawnBoxes[o + 3] = b
        }
        if (inT < 1f) {
            val x0 = cx - side * cw * .5f
            val x1 = cx - side * (cw * .5f + 90f * (1f - inT))
            val stroke = 3f * (1f - inT)
            val pad = (stroke + JarvisDesign.Glow.STRONG.haloWidth) / 2f
            if (!lineOverlapsProtected(x0, cy, x1, cy, pad)) {
                p.setAdditive(true)
                JarvisNeon.line(p, x0, cy, x1, cy, JarvisDesign.Colors.cyan, stroke, JarvisDesign.Glow.STRONG)
                p.setAdditive(false)
            }
        }
        // link to the hub while the step runs (live process line)
        if (c.live && c.leavingAt < 0 && inT >= 1f) {
            val dash = ((now - c.at) % 900L) / 900f
            p.setAdditive(true)
            if (!lineOverlapsProtected(hubX, hubY, cx, t, .4f)) {
                p.drawLine(hubX, hubY, cx, t, .8f, Fill.Solid(JarvisDesign.Colors.violet), .25f)
            }
            val px = hubX + (cx - hubX) * dash; val py = hubY + (t - hubY) * dash
            if (!circleOverlapsProtected(px, py, 2.2f)) {
                p.fillCircle(px, py, 2.2f, Fill.Solid(JarvisDesign.Colors.cyan), .9f)
            }
            p.setAdditive(false)
        }
        p.save(); p.scale(sx, 1f, cx, cy)
        val flash = if (inT in .75f..1f) JarvisDesign.Glow.STRONG else JarvisDesign.Glow.NORMAL
        if (c.live || inT in .75f..1f) JarvisNeon.bloomRoundRect(p, l, t, r, b, 14f, intensity = if (c.live) .6f else 1f)
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
            Outcome.UNVERIFIED -> {
                val neutral = Fill.Solid(JarvisDesign.Colors.textSecondary)
                p.strokeCircle(x, y, 11f, 1.4f, neutral)
                p.drawLine(x - 4f, y, x + 4f, y, 1.8f, neutral)
                return
            }
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
            val e = ORB_EASE_IN(t)
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
            if (t < 1f) { p.setAdditive(true); JarvisNeon.halo(p, px, py, 4.5f, JarvisDesign.Colors.cyan, .85f); p.setAdditive(false) } // spark at the head of the track
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

    // F envelope object colours: NO JarvisDesign token yet (Gmail red / paper are not in the ref5 table) –
    // requested from Welten-Optik (#479); kept as the only literals here until the measured table lands.
    private val mailRed = Fill.Solid(MAIL_RED)
    private val paper = Fill.Solid(0xfff2f6fb.toInt())
    private val paperShade = Fill.Solid(0xffcfd8e6.toInt())
    private val flapPath = VectorPath()

    private fun drawEnvelope(p: ContinuumPainter, w: Float, h: Float, now: Long) {
        val env = envelope ?: return
        val el = now - env.at
        val pose = envelopePose(el, 1f)
        val scale = pose.depthScale
        val cx = w / 2f; val bob = sin(el / 650f) * 3f                       // floats after arrival
        val cy = h * .42f + bob
        val ew = min(w * ENVELOPE_W_FRAC, ENVELOPE_MAX_W); val eh = ew * .68f   // ref5 scale: small object beside the head
        p.setAdditive(true); JarvisNeon.halo(p, cx, cy, ew * 1.1f * scale, JarvisDesign.Colors.cyan, .3f * bloomStrength(glow, scale)); p.setAdditive(false)
        // spatial entrance from depth: converging depth rings + tilt that straightens as it arrives
        val e = scale.coerceIn(0f, 1f)
        if (e < 1f) {
            p.setAdditive(true)
            for (k in 0..2) {
                val rr = ew * (1.6f - e * 1.1f) * (1f + k * .35f)
                JarvisNeon.ring(p, cx, cy, rr, rr * .32f, 1f, glow = JarvisDesign.Glow.SUBTLE)
            }
            p.setAdditive(false)
        }
        p.save(); p.scale(scale, scale * (.55f + (REST_FORESHORTEN - .55f) * e), cx, cy); p.rotate(-(pose.tiltDeg + REST_TILT_DEG), cx, cy)
        val l = cx - ew / 2f; val r = cx + ew / 2f; val t = cy - eh / 2f; val b = cy + eh / 2f
        // document slides up out of the open envelope (after the flap)
        val docT = pose.docRise
        val docY = t + eh * .15f - docT * eh * .85f
        if (docT > 0f) {
            p.fillRoundRect(l + ew * .14f, docY, r - ew * .14f, docY + eh * .8f, 3f, paper)
            for (k in 0..3) p.drawLine(l + ew * .22f, docY + 10f + k * 8f, r - ew * (.22f + (k % 2) * .12f), docY + 10f + k * 8f, 1.4f, paperShade)
        }
        // cinema look (#479 bloom tokens): depth shadow, cyan bloom behind, glass body over a paper core
        val bs = bloomStrength(glow, e)
        p.fillRoundRect(l + 4f, t + 8f, r + 4f, b + 10f, 8f, Fill.Solid(JarvisDesign.Colors.background), .55f)
        JarvisNeon.bloomRoundRect(p, l, t, r, b, 8f, JarvisDesign.Colors.cyan, .8f * bs)
        p.fillRoundRect(l, t, r, b, 6f, paper, .92f)
        p.fillRoundRect(l, t + eh * .55f, r, b, 6f, paperShade, .35f)                    // lower body in shade = depth
        JarvisNeon.glassCard(p, l, t, r, b, 6f, if (bs > 0f) JarvisDesign.Glow.NORMAL else JarvisDesign.Glow.OFF, glowBoost = bs.coerceAtLeast(.01f))
        p.setAdditive(true); JarvisNeon.bloom(p, cx, cy, ew * .35f, MAIL_RED, .25f * bs); p.setAdditive(false)
        // red M (two diagonals meeting at the centre) and red side bars
        p.drawLine(l + 6f, t + 8f, cx, cy + 2f, 5f, mailRed); p.drawLine(r - 6f, t + 8f, cx, cy + 2f, 5f, mailRed)
        p.drawLine(l + 6f, t + 8f, l + 6f, b - 6f, 6f, mailRed); p.drawLine(r - 6f, t + 8f, r - 6f, b - 6f, 6f, mailRed)
        // flap: X-rotation as vertical scale through the top edge (cos), flips above the envelope when open
        // flap: perspective X-rotation (foreshortened, widens toward the camera), flips above when open
        val fy = pose.flapProjected * eh * .55f
        val spread = (pose.flapWidth - 1f) * ew * .5f
        flapPath.reset().moveTo(l - spread, t).lineTo(cx, t + fy).lineTo(r + spread, t).close()
        p.fillPath(flapPath, if (fy >= 0f) paper else paperShade)
        p.strokePath(flapPath, 1f, paperShade)
        p.restore()
        // Muster D: lasers from the document to the target labels
        if (docT >= 1f) {
            val lt = el - ENVELOPE_LASER_START
            env.targets.forEachIndexed { i, label ->
                val left = i % 2 == 0
                val tx = if (left) w * .14f else w * .86f
                val ty = h * (.2f + .16f * (i / 2)) + (if (left) 0f else h * .08f)
                val prog = JarvisMotion.laser(lt - i * JarvisMotion.Durations.STAGGER)
                JarvisNeon.laserSparks(p, cx, docY + 6f, tx, ty, prog, now)
                if (prog >= 1f) {
                    val style = JarvisDesign.Type.pill; val st = JarvisDesign.Styles.pill
                    val pw = min(p.measureText(label, style) + 2f * st.padH, w * .4f)
                    val pl = if (left) (tx - pw / 2f).coerceAtLeast(4f) else (tx - pw / 2f).coerceAtMost(w - pw - 4f)
                    val top = ty - st.height / 2f
                    JarvisNeon.component(p, pl, top, pl + pw, top + st.height, st)
                    p.drawText(label, pl + pw / 2f, top + (st.height + p.capHeight(style)) / 2f, style, Fill.Solid(JarvisDesign.Colors.text), TextAlign.CENTER)
                }
            }
        }
    }

    private fun drawPills(p: ContinuumPainter, w: Float, h: Float, now: Long) {
        val pl = pills ?: return
        val style = JarvisDesign.Type.pill    // §11 / REF5-FARBEN: pill type 10 sp
        val st0 = JarvisDesign.Styles.pill   // #07204D fill, #0175AF rim, 22 dp, round
        pl.items.forEachIndexed { i, text ->
            val pw = pillWidths.getOrPut(text) { min(p.measureText(text, style) + 2f * st0.padH, w * .44f) }
            val g = pillGeometry(i, text, pw, w, h, now - pl.at) ?: return@forEachIndexed
            val enabled = answerPills.getOrNull(i)?.enabled ?: true
            // No alpha fade (Muster G): full opacity from the first frame; landing flash = bloom/active rim, not alpha.
            val st = st0.copy(rim = if (enabled) st0.rim else JarvisDesign.Colors.textMuted,
                glow = if (!enabled || glow == JarvisDesign.Glow.OFF) JarvisDesign.Glow.OFF else if (g.t in .85f..1f) JarvisDesign.Glow.STRONG else st0.glow)
            val top = g.y - st.height / 2f
            JarvisNeon.component(p, g.x - pw / 2f, top, g.x + pw / 2f, top + st.height, st, active = enabled && g.t in .85f..1f)
            p.drawText(text, g.x, top + (st.height + p.capHeight(style)) / 2f, style,
                Fill.Solid(if (enabled) JarvisDesign.Colors.text else JarvisDesign.Colors.textMuted), TextAlign.CENTER)
        }
    }

    private val cascadeStyle = ContinuumTheme.text(500, 11f)
    private val alarmRed = 0xffff3b5c.toInt()

    private fun drawAlarm(p: ContinuumPainter, w: Float, h: Float, now: Long) {
        if (!alarmActive(now)) return
        val el = now - alarmAt
        val fade = (1f - el.toFloat() / alarmMs).coerceIn(0f, 1f).let { if (el < 200) el / 200f else it }
        // glow x3: red/cyan edge vignette pulses
        val pulse = .6f + .4f * sin(el / 90f)
        p.setAdditive(true)
        JarvisNeon.halo(p, 0f, h / 2f, h * .7f, alarmRed, .45f * fade * pulse)
        JarvisNeon.halo(p, w, h / 2f, h * .7f, alarmRed, .45f * fade * pulse)
        JarvisNeon.halo(p, w / 2f, h * .4f, w * .6f, JarvisDesign.Colors.cyan, .25f * fade)
        p.setAdditive(false)
        // binary cascade, falling in Y (decorative glyphs only, never values)
        val cols = (w / 18f).toInt()
        for (c in 0 until cols) {
            val speed = 120f + (c * 37 % 90)
            val head = ((el / 1000f) * speed + (c * 7919 % 997)) % (h + 120f)
            for (k in 0 until 7) {
                val y = head - k * 15f
                if (y < 0f || y > h) continue
                val glyph = if (((c * 7 + k * 3 + (el / 120).toInt()) and 1) == 0) "0" else "1"
                p.drawText(glyph, c * 18f + 6f, y, cascadeStyle, Fill.Solid(if (k == 0) 0xffe6fdff.toInt() else JarvisDesign.Colors.cyan),
                    TextAlign.LEFT)
            }
        }
        if (fade > 0f) p.fillRect(0f, 0f, w, h, Fill.Solid(alarmRed), .05f * fade * pulse)
    }

    private fun backOut(t: Float): Float { val s = 1.7f; val u = t - 1f; return 1f + u * u * ((s + 1f) * u + s) }
    private fun clip(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
    private fun validId(id: String) = id.length in 1..96 && id.all { it.isLetterOrDigit() && it.code < 128 || it in "._:-" }

    companion object {
        const val MAX_CARDS = 4
        const val MAX_FLIGHT = 8
        /** Ease-in into the orb: Jarvis Mathematik #489 (motion-math @e600add5) Easings.easeInCubic. */
        val ORB_EASE_IN: (Float) -> Float = at.heinz.deviceai.motion.Easings.easeInCubic::at
        /** Muster E: 0.6–0.8 s per icon (spec). */
        const val FLIGHT_MS = 700L
        const val PULSE_MS = 600L
        const val RECALL_MS = 1200L
        const val HYPOTHESIS_MS = 2500L
        const val FLY_MS = JarvisMotion.Durations.PANEL_FLY_IN
        const val LEAVE_MS = 380L
        const val COMMAND_MS = 1100L

        /** Muster F: document rise after the flap (GESCHÄTZT), then Muster D lasers. */
        const val DOC_RISE_MS = 450L
        const val ENVELOPE_LASER_START = JarvisMotion.Durations.ENVELOPE_IN + JarvisMotion.Durations.ENVELOPE_FLAP + DOC_RISE_MS
        /** Bounded per frame: ≤ 4 lasers × 3 sparks (JarvisNeon.laserSparks draws 3). */
        /** Touch half height (dp) of a pill; drawn pill is JarvisDesign.Styles.pill (22 dp), hit box kept larger for touch. */
        const val PILL_HALF_H = 14f
        const val MAX_LASERS = 4
        const val SPARKS_PER_LASER = 3
        /** F at rest (ref5): ~26 % width, max 96 dp, kept slightly foreshortened/tilted so it reads 3-D, not flat. */
        /** Gmail red: no REF5 token (object colour), flagged for Welten-Optik. */
        const val MAIL_RED = 0xffea4335.toInt()
        const val ENVELOPE_W_FRAC = .26f
        const val ENVELOPE_MAX_W = 96f
        const val REST_FORESHORTEN = .9f
        const val REST_TILT_DEG = 6f
        private const val CAMERA_DISTANCE = 8f
        private const val START_DEPTH = 24f

        data class EnvelopePose(val depthScale: Float, val tiltDeg: Float, val flapAngleDeg: Float,
                                val flapProjected: Float, val flapWidth: Float, val docRise: Float, val laser: Float)

        /**
         * Muster F as a pure pose: perspective depth entrance 0→100 % in 0,5 s (z = START_DEPTH → 0 at
         * CAMERA_DISTANCE), tilt straightens on arrival; flap X-rotation 0→180° in 0,3 s, projected with
         * foreshortening; document rise; laser draw-on 0,3 s. animatorScale ≤ 0 (reduced motion) = final pose.
         */
        fun envelopePose(elapsedMs: Long, animatorScale: Float): EnvelopePose {
            if (!(animatorScale > 0f) || !animatorScale.isFinite()) return EnvelopePose(1f, 0f, 180f, -1f, 1f, 1f, 1f)
            val s = animatorScale
            val e = JarvisMotion.Easing.easeOutCubic((elapsedMs / (JarvisMotion.Durations.ENVELOPE_IN * s)).coerceIn(0f, 1f))
            val z = (1f - e) * START_DEPTH
            val depth = e * CAMERA_DISTANCE / (CAMERA_DISTANCE + z) * ((CAMERA_DISTANCE + 0f) / CAMERA_DISTANCE)
            val angle = 180f * JarvisMotion.envelopeFlap(elapsedMs, s)
            val rad = Math.toRadians(angle.toDouble())
            val lift = kotlin.math.sin(rad).toFloat()
            val projected = kotlin.math.cos(rad).toFloat() / (1f + lift * .35f)
            val docT = ((elapsedMs - (JarvisMotion.Durations.ENVELOPE_IN + JarvisMotion.Durations.ENVELOPE_FLAP) * s) / (DOC_RISE_MS * s)).coerceIn(0f, 1f)
            return EnvelopePose(depth, (1f - e) * 18f, angle, projected, 1f + lift * .25f,
                JarvisMotion.Easing.easeOutCubic(docT), JarvisMotion.laser(elapsedMs - (ENVELOPE_LASER_START * s).toLong(), s))
        }

        /** Layered-bloom strength; [JarvisDesign.Glow.OFF] (power save / below-API fallback) = 0, flat look only. */
        fun bloomStrength(glow: JarvisDesign.Glow, boost: Float): Float =
            if (glow == JarvisDesign.Glow.OFF) 0f else (glow.haloAlpha / JarvisDesign.Glow.NORMAL.haloAlpha * boost).coerceIn(0f, 2f)

        fun pillLabels(pills: List<at.heinz.deviceai.cockpit.AnswerPill>): List<String> = pills.map { pill ->
            val label = at.heinz.deviceai.cockpit.ToolGatePillActionGate.LABELS[pill.kind] ?: pill.kind.name
            if (pill.enabled) label else "$label · ${pill.disabledReason ?: at.heinz.deviceai.cockpit.ToolGatePillActionGate.SOURCE_MISSING}"
        }
    }
}
