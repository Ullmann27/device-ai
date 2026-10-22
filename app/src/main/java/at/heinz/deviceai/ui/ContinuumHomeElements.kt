package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumDashboardState.Companion.UNKNOWN
import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.ContinuumTheme.text
import kotlin.math.max
import kotlin.math.min

/**
 * One drawable piece of the Continuum start screen. [draw] works in a box of w x h dp; all positions are measured
 * from the reference images at their reference size, so the elements reproduce the reference composition exactly.
 * [textScale] is the system font scale (clamped), applied to every text size.
 */
abstract class ContinuumElement(protected val textScale: Float = 1f) {
    abstract fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState)
    /** Spoken by TalkBack; contains the same real values that are drawn. */
    abstract fun describe(s: ContinuumDashboardState): String
    protected fun t(weight: Int, size: Float, spacing: Float = 0f) = text(weight, size * textScale, spacing * textScale)
    protected fun d(weight: Int, size: Float, spacing: Float = 0f) = ContinuumTheme.display(weight, size * textScale, spacing * textScale)
    protected fun solid(color: Int) = Fill.Solid(color)
}

/** Logo, "Continuum" and the subtitle (phone reference: 64 dp tall header). */
class HeaderBrandElement(textScale: Float = 1f, private val large: Boolean = false) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        if (large) {
            // Fold reference: logo 66 x 34 dp, title 20.7 sp, subtitle 8.5 sp (box origin 80/30 dp)
            ContinuumIcons.logo(p, 45.3f, 23.2f, 66f, 34f)
            p.drawText("Continuum", 87f, 31.7f, d(500, 20.7f), solid(ContinuumTheme.TEXT))
            p.drawText("Dein KI-Universum. Ohne Grenzen.", 87.5f, 48f, t(400, 8.5f), solid(Argb.rgb(166, 190, 226)))
            return
        }
        val cy = h * .52f
        ContinuumIcons.logo(p, 43.3f, cy, 48.6f, 25f)
        p.drawText("Continuum", 79f, cy + 7.5f, d(500, 21.66f), solid(ContinuumTheme.TEXT))
        p.drawText("Dein KI-Universum. Ohne Grenzen.", 79.5f, cy + 17.9f, t(400, 8.74f), solid(Argb.rgb(166, 190, 226)))
    }
    override fun describe(s: ContinuumDashboardState) = "Continuum. Dein KI-Universum. Ohne Grenzen."
}

/** Round header button: search, notifications (red badge only for real pending items) and the profile orb. */
class HeaderButtonElement(val kind: Kind, textScale: Float = 1f) : ContinuumElement(textScale) {
    enum class Kind { SEARCH, NOTIFICATIONS, PROFILE }

    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val cx = w / 2f; val cy = h / 2f; val rr = min(w, h) / 2f - .5f
        if (kind == Kind.PROFILE) {
            p.fillCircle(cx, cy, rr * 1.15f, Fill.Radial(cx, cy, rr * 1.15f, intArrayOf(Argb.of(120, 120, 80, 255), 0)))
            p.fillCircle(cx, cy, rr, Fill.Radial(cx, cy, rr, intArrayOf(Argb.rgb(250, 220, 255), Argb.rgb(200, 90, 255),
                Argb.rgb(70, 40, 170), Argb.rgb(20, 40, 120), Argb.rgb(60, 140, 255)), floatArrayOf(0f, .12f, .4f, .8f, 1f)))
            for (i in 0 until 8) {
                val a = i * 45f
                p.save(); p.rotate(a, cx, cy)
                p.drawLine(cx + rr * .18f, cy, cx + rr * .82f, cy, .6f, solid(Argb.rgb(200, 170, 255)), .5f)
                p.restore()
            }
            p.strokeCircle(cx, cy, rr * .62f, .6f, solid(Argb.rgb(120, 200, 255)), .55f)
            p.strokeCircle(cx, cy, rr, 1.2f, Fill.Sweep(cx, cy, intArrayOf(Argb.rgb(90, 220, 255), Argb.rgb(190, 110, 255),
                Argb.rgb(90, 220, 255))), .95f)
            p.fillCircle(cx, cy, rr * .16f, solid(Argb.rgb(255, 240, 255)))
            return
        }
        p.fillCircle(cx, cy, rr, Fill.Radial(cx, cy - rr * .4f, rr * 1.4f, intArrayOf(Argb.rgb(20, 36, 70), Argb.rgb(8, 16, 36))))
        p.strokeCircle(cx, cy, rr, 1f, Fill.Linear(cx, cy - rr, cx, cy + rr, intArrayOf(Argb.rgb(60, 86, 140), Argb.rgb(28, 44, 86))), .9f)
        val icon = if (kind == Kind.SEARCH) Icon.SEARCH else Icon.BELL
        ContinuumIcons.draw(p, icon, cx, cy, rr * .98f, solid(Argb.rgb(222, 230, 245)), rr * .085f)
        if (kind == Kind.NOTIFICATIONS && s.pendingNotifications > 0) {
            val bx = cx + rr * .66f; val by = cy - rr * .68f
            p.fillCircle(bx, by, 5.4f, Fill.Radial(bx, by, 5.4f, intArrayOf(Argb.of(170, 255, 50, 80), 0)))
            p.fillCircle(bx, by, 3.4f, solid(Argb.rgb(255, 46, 76)))
        }
    }

    override fun describe(s: ContinuumDashboardState) = when (kind) {
        Kind.SEARCH -> "Wissensarchiv durchsuchen"
        Kind.NOTIFICATIONS -> if (s.pendingNotifications > 0) "${s.pendingNotifications} offene Hinweise" else "Keine offenen Hinweise"
        Kind.PROFILE -> "Persönliche Einstellungen"
    }
}

/** One of the six labels around the core: icon disc, cyan title and a real status line. */
class CoreLabelElement(val icon: Icon, val title: String, private val status: (ContinuumDashboardState) -> String,
                       textScale: Float = 1f, private val boxed: Boolean = false) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val cy = h / 2f
        if (boxed) {
            ContinuumSurfaces.glassCard(p, 0f, 0f, w, h, 12f)
            ContinuumSurfaces.iconDisc(p, icon, 26f, cy, 30f, 16f)
            p.drawText(title, 51f, cy - 1.5f, t(700, 8.6f), solid(ContinuumTheme.LABEL_CYAN))
            p.drawText(ContinuumSurfaces.fit(p, status(s), t(400, 7.8f), w - 56f), 51f, cy + 10.5f, t(400, 7.8f),
                solid(ContinuumTheme.TEXT_SECONDARY))
            return
        }
        ContinuumSurfaces.iconDisc(p, icon, 13.5f, cy, 27f, 14.5f)
        p.drawText(title, 32.5f, cy - 1.2f, t(700, 7.95f), solid(ContinuumTheme.LABEL_CYAN))
        p.drawText(ContinuumSurfaces.fit(p, status(s), t(400, 6.63f), w - 33f), 32.5f, cy + 8.6f, t(400, 6.63f),
            solid(ContinuumTheme.TEXT_SECONDARY))
    }
    override fun describe(s: ContinuumDashboardState) = "$title: ${status(s)}"
}

/** Phone "Modellstatus" card (reference 385.5 x 89 dp). */
class ModelStatusCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val m = s.model
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.BRAIN, 31.8f, 30f, 36f, 21f)
        p.drawText("Modellstatus", 63f, 29.5f, t(500, 13.43f), solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, m.name, t(400, 10.11f), 126f), 63f, 44.5f, t(400, 10.11f), solid(ContinuumTheme.TEXT_SECONDARY))
        val pillColor = if (m.online) ContinuumTheme.GREEN else ContinuumTheme.AMBER
        ContinuumSurfaces.pill(p, 63f, 55.5f, 20.5f, m.stateLabel, t(500, 9.73f), pillColor, minWidth = 58f)
        val divider = w * .5f
        p.drawLine(divider, 16f, divider, 76f, .8f, solid(ContinuumTheme.CARD_DIVIDER))
        val x0 = divider + 16f
        // Holdout check questions of the learning core: a narrow test score, deliberately not labelled as performance.
        p.drawText(QUALITY_LABEL, x0, 24f, t(400, 8.27f), solid(ContinuumTheme.TEXT_SECONDARY))
        // 24-hour change of the measured quality (owner request); declines are shown just as clearly as gains.
        val delta = m.qualityDelta24h
        val deltaX = x0 + p.measureText(QUALITY_LABEL, t(400, 8.27f)) + 5f
        p.drawText(ContinuumDashboardState.delta24h(delta), deltaX, 24f, t(500, 7.4f), solid(when {
            delta == null || delta == 0 -> ContinuumTheme.TEXT_MUTED; delta > 0 -> ContinuumTheme.GREEN; else -> ContinuumTheme.RED }))
        drawSegmentBar(p, x0, 34.7f, x0 + 103f, 43.9f, m.qualityPercent)
        p.drawText(ContinuumDashboardState.percent(m.qualityPercent), x0 + 113f, 43.9f, t(500, 12.36f), solid(ContinuumTheme.CYAN_SOFT))
        p.drawText("Reaktionszeit", x0, 61f, t(400, 8.27f), solid(ContinuumTheme.TEXT_SECONDARY))
        p.drawText(ContinuumDashboardState.millis(m.latencyMs), x0, 76.5f, t(500, 11f), solid(ContinuumTheme.CYAN_SOFT))
        val x1 = x0 + 88f
        p.drawText("Kontextfenster", x1, 61f, t(400, 8.27f), solid(ContinuumTheme.TEXT_SECONDARY))
        p.drawText(ContinuumSurfaces.fit(p, m.context ?: UNKNOWN, t(500, 11f), w - x1 - 10f), x1, 76.5f, t(500, 11f),
            solid(ContinuumTheme.CYAN_SOFT))
        ContinuumSurfaces.chevron(p, w - 17.5f, 22.5f, 13f)
    }

    override fun describe(s: ContinuumDashboardState) = with(s.model) {
        "Modellstatus: $name, $stateLabel. Prüffragen des Lernkerns (Intent und Semantik, kein allgemeiner Intelligenzwert) " +
            "${ContinuumDashboardState.percent(qualityPercent)}, " +
            "${ContinuumDashboardState.delta24h(qualityDelta24h)}, " +
            "Reaktionszeit ${ContinuumDashboardState.millis(latencyMs)}, Kontextfenster ${context ?: "unbekannt"}."
    }

    companion object {
        const val QUALITY_LABEL = "Prüffragen"

        /** Segmented level bar; without a measurement all segments stay dark (no invented level). */
        fun drawSegmentBar(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, percent: Int?, segments: Int = 20) {
            val gap = (r - l) * .012f * 2.2f
            val width = ((r - l) - gap * (segments - 1)) / segments
            val lit = percent?.let { (it.coerceIn(0, 100) * segments + 50) / 100 } ?: 0
            for (i in 0 until segments) {
                val x = l + i * (width + gap)
                val color = when {
                    i >= lit -> Argb.rgb(26, 40, 72)
                    i < segments * .55f -> Argb.mix(Argb.rgb(40, 110, 255), Argb.rgb(60, 200, 255), i / (segments * .55f))
                    i < segments * .85f -> Argb.mix(Argb.rgb(60, 200, 255), Argb.rgb(160, 160, 255), (i - segments * .55f) / (segments * .3f))
                    else -> Argb.mix(Argb.rgb(170, 150, 255), Argb.rgb(255, 160, 230), (i - segments * .85f) / (segments * .15f))
                }
                p.fillRoundRect(x, t, x + width, b, width * .3f, Fill.Solid(color))
                if (i < lit) p.fillRoundRect(x - .6f, t - .6f, x + width + .6f, b + .6f, width * .4f, Fill.Solid(color), .18f)
            }
        }
    }
}

/** Phone "Lernen & Forschung" card (reference 202 x 182 dp). */
class LearningCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val l = s.learning
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.BOOK, 26f, 23f, 26.6f, 15f)
        p.drawText("Lernen & Forschung", 47.4f, 27.7f, t(500, 11.24f), solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.chevron(p, w - 17f, 23f, 12.5f)
        val figure = ContinuumDashboardState.count(l.today)
        val figureStyle = t(500, 18.98f)
        p.drawText(figure, 13.9f, 68f, figureStyle, ContinuumSurfaces.figureFill(52f, 68f))
        val labelX = 13.9f + p.measureText(figure, figureStyle) + 6.5f
        p.drawText("Lernereignisse", labelX, 57.8f, t(400, 8.32f), solid(Argb.rgb(196, 208, 228)))
        p.drawText("heute", labelX, 68.4f, t(400, 8.32f), solid(Argb.rgb(196, 208, 228)))
        l.changePercent?.let { change ->
            val up = change >= 0
            val color = if (up) ContinuumTheme.RED else ContinuumTheme.GREEN
            val label = (if (up) "+" else "") + change + "%"
            val style = t(500, 8.34f)
            val chipW = 19f + p.measureText(label, style)
            val cl = w - 12f - chipW
            p.fillRoundRect(cl, 47f, w - 12f, 61f, 5f, solid(Argb.withAlpha(color, .18f)))
            p.strokeRoundRect(cl, 47f, w - 12f, 61f, 5f, .8f, solid(Argb.withAlpha(color, .5f)))
            ContinuumIcons.draw(p, if (up) Icon.ARROW_UP else Icon.CHEVRON_DOWN, cl + 9f, 54f, 8.5f, solid(color), 1.2f)
            p.drawText(label, cl + 14.5f, 57.3f, style, solid(color))
        }
        WaveChart.draw(p, 14f, 72f, w - 11f, 112f, l.series)
        val rows = l.recent.take(3)
        if (rows.isEmpty()) {
            p.drawText("Heute noch keine Lernereignisse.", 14f, 136f, t(400, 7.02f), solid(ContinuumTheme.TEXT_MUTED))
        }
        for ((i, e) in rows.withIndex()) {
            val y = 131f + i * 17.5f
            EventDot.draw(p, 18.5f, y - 3.3f, e.kind)
            val time = e.ago
            val timeStyle = t(400, 6.75f)
            val timeW = p.measureText(time, timeStyle)
            p.drawText(time, w - 12f, y, timeStyle, solid(ContinuumTheme.TEXT_MUTED), TextAlign.RIGHT)
            p.drawText(ContinuumSurfaces.fit(p, e.title, t(400, 7.02f), w - 28f - timeW - 14f), 28f, y, t(400, 7.02f),
                solid(Argb.rgb(205, 214, 232)))
        }
    }

    override fun describe(s: ContinuumDashboardState) = with(s.learning) {
        "Lernen und Forschung: ${ContinuumDashboardState.count(today)} Lernereignisse heute" +
            (changePercent?.let { ", $it Prozent gegenüber gestern" } ?: "") +
            recent.take(3).joinToString("") { ". ${it.title}, ${it.ago}" }
    }
}

/** Phone "Speicher & Quellen" card (reference 174 x 182 dp). */
class StorageCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val st = s.storage
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.DATABASE, 23.7f, 23f, 26.6f, 15f)
        p.drawText("Speicher & Quellen", 45f, 27.7f, t(500, 11.01f), solid(ContinuumTheme.TEXT))
        ContinuumSurfaces.chevron(p, w - 16.2f, 23f, 12.5f)
        Donut.draw(p, 49.7f, 83.8f, 36.4f, 7.2f, st.usedPercent, st.segments)
        p.drawText(ContinuumDashboardState.percent(st.usedPercent), 49.7f, 88.6f, t(500, 16.38f), solid(Argb.rgb(226, 238, 255)), TextAlign.CENTER)
        p.drawText("belegt", 49.7f, 99.5f, t(400, 7.57f), solid(ContinuumTheme.TEXT_SECONDARY), TextAlign.CENTER)
        for ((i, seg) in st.segments.take(4).withIndex()) {
            val y = 56.6f + i * 23.8f
            p.fillCircle(102f, y - 3.4f, 3.2f, solid(seg.color))
            p.drawText(ContinuumSurfaces.fit(p, seg.label, t(400, 7.62f), w - 115f), 111f, y, t(400, 7.62f), solid(Argb.rgb(200, 210, 228)))
            p.drawText(ContinuumDashboardState.bytes(seg.bytes), 111f, y + 10.6f, t(500, 7.97f), solid(Argb.rgb(126, 210, 255)))
        }
        p.drawLine(14f, 143f, w - 14f, 143f, .8f, solid(ContinuumTheme.CARD_DIVIDER))
        ContinuumSurfaces.iconDisc(p, Icon.GLOBE, 21.4f, 161.8f, 22f, 13f, glow = false)
        val sources = st.activeSources?.let { "$it gespeicherte Quellen" } ?: "Quellen: $UNKNOWN"
        p.drawText(ContinuumSurfaces.fit(p, sources, t(500, 8.11f), w - 62f), 39.9f, 159.5f, t(500, 8.11f), solid(ContinuumTheme.TEXT))
        p.drawText(ContinuumSurfaces.fit(p, st.syncLabel, t(400, 6.91f), w - 62f), 39.9f, 170.5f, t(400, 6.91f), solid(ContinuumTheme.TEXT_MUTED))
        ContinuumSurfaces.chevron(p, w - 16.2f, 162f, 12.5f)
    }

    override fun describe(s: ContinuumDashboardState) = with(s.storage) {
        "Speicher und Quellen: ${ContinuumDashboardState.percent(usedPercent)} belegt. " +
            segments.joinToString(". ") { it.label + " " + ContinuumDashboardState.bytes(it.bytes) } +
            ". ${activeSources ?: "unbekannt viele"} gespeicherte Quellen, $syncLabel."
    }
}

/** Phone "Entwicklung & Code" card (reference 385.5 x 100 dp). */
class DevelopmentCardElement(textScale: Float = 1f) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val dv = s.development
        ContinuumSurfaces.glassCard(p, 0f, 0f, w, h)
        ContinuumSurfaces.iconDisc(p, Icon.CODE, 25.4f, 23f, 26.6f, 15f)
        p.drawText("Entwicklung & Code", 47.4f, 27.7f, t(500, 11.5f), solid(ContinuumTheme.TEXT))
        val pillColor = when (dv.healthy) { true -> ContinuumTheme.GREEN; false -> ContinuumTheme.RED; null -> ContinuumTheme.AMBER }
        ContinuumSurfaces.pill(p, w - 11.5f, 14.5f, 16.1f, dv.statusLabel, t(500, 7.22f), pillColor, alignRight = true)
        val metrics = listOf(ContinuumDashboardState.count(dv.activeProjects) to "offene Aufgaben",
            ContinuumDashboardState.count(dv.runningTasks) to "laufend", ContinuumDashboardState.count(dv.errors) to "fehlgeschlagen")
        val xs = floatArrayOf(24f, 94.8f, 166.5f)
        for ((i, m) in metrics.withIndex()) {
            p.drawText(m.first, xs[i], 69f, t(500, 18.5f), solid(ContinuumTheme.CYAN_SOFT))
            p.drawText(m.second, xs[i], 82.4f, t(400, 7.34f), solid(Argb.rgb(196, 208, 228)))
        }
        p.drawLine(83.8f, 50f, 83.8f, 85f, .8f, solid(ContinuumTheme.CARD_DIVIDER))
        p.drawLine(154.3f, 50f, 154.3f, 85f, .8f, solid(ContinuumTheme.CARD_DIVIDER))
        p.drawLine(225f, 40f, 225f, 86f, .8f, solid(ContinuumTheme.CARD_DIVIDER))
        BarChart.draw(p, 232f, 43f, w - 20f, 84f, dv.series)
    }

    override fun describe(s: ContinuumDashboardState) = with(s.development) {
        "Entwicklung und Code: $statusLabel. ${ContinuumDashboardState.count(activeProjects)} offene Aufgaben im Backlog, " +
            "${ContinuumDashboardState.count(runningTasks)} laufend, ${ContinuumDashboardState.count(errors)} fehlgeschlagen."
    }
}

/** Smooth layered waves of a real series; an empty series draws a flat baseline only. */
object WaveChart {
    private val path = VectorPath()
    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, series: List<Float>, layers: Int = 3) {
        p.drawLine(l, b, r, b, .6f, Fill.Solid(Argb.rgb(40, 60, 110)), .8f)
        if (series.size < 2 || series.maxOrNull()?.let { it <= 0f } != false) return
        val maxV = series.maxOrNull()!!.coerceAtLeast(1e-6f)
        val colors = intArrayOf(Argb.rgb(40, 120, 255), Argb.rgb(150, 90, 255), Argb.rgb(255, 80, 180))
        for (layer in layers - 1 downTo 0) {
            // later layers are damped copies, giving the reference's multi-colour depth from the same data
            val damp = 1f - layer * .22f
            val shift = layer * .9f
            path.reset()
            val n = series.size
            fun px(i: Float) = l + (r - l) * (i / (n - 1))
            fun py(i: Int) = b - (b - t) * .95f * (series[(i).coerceIn(0, n - 1)] / maxV) * damp
            path.moveTo(px(0f), b)
            path.lineTo(px(0f), py(0))
            for (i in 1 until n) {
                val x0 = px(i - 1f + shift / n); val x1 = px(i.toFloat())
                val mid = (x0 + x1) / 2f
                path.cubicTo(mid, py(i - 1), mid, py(i), x1, py(i))
            }
            path.lineTo(px(n - 1f), b).close()
            val color = colors[layer % colors.size]
            p.fillPath(path, Fill.Linear(0f, t, 0f, b, intArrayOf(Argb.withAlpha(color, .55f), Argb.withAlpha(color, 0f))))
            path.reset()
            path.moveTo(px(0f), py(0))
            for (i in 1 until n) {
                val x0 = px(i - 1f); val x1 = px(i.toFloat()); val mid = (x0 + x1) / 2f
                path.cubicTo(mid, py(i - 1), mid, py(i), x1, py(i))
            }
            p.strokePath(path, 2.6f, Fill.Linear(l, 0f, r, 0f, intArrayOf(Argb.rgb(60, 160, 255), color, Argb.rgb(255, 90, 190))), .18f)
            p.strokePath(path, 1.1f, Fill.Linear(l, 0f, r, 0f, intArrayOf(Argb.rgb(80, 180, 255), color, Argb.rgb(255, 110, 200))), .95f)
        }
    }
}

/** Gradient bar chart of a real series; empty series draws only the axis. */
object BarChart {
    fun draw(p: ContinuumPainter, l: Float, t: Float, r: Float, b: Float, series: List<Float>, grid: Boolean = true) {
        if (grid) for (i in 0..3) {
            val y = t + (b - t) * i / 3f
            p.drawLine(l, y, r, y, .4f, Fill.Solid(Argb.rgb(36, 54, 98)), .7f)
        }
        p.drawLine(l, b, r, b, .6f, Fill.Solid(Argb.rgb(60, 80, 130)))
        if (series.isEmpty()) return
        val maxV = max(series.maxOrNull() ?: 0f, 1e-6f)
        val n = series.size
        val slot = (r - l) / n
        val bar = slot * .55f
        for ((i, v) in series.withIndex()) {
            val x = l + i * slot + (slot - bar) / 2f
            val top = b - (b - t) * .95f * (v / maxV)
            if (v <= 0f) continue
            val k = i / max(1f, n - 1f)
            val color = when {
                k < .5f -> Argb.mix(Argb.rgb(40, 110, 255), Argb.rgb(70, 190, 255), k / .5f)
                else -> Argb.mix(Argb.rgb(150, 100, 255), Argb.rgb(255, 90, 200), (k - .5f) / .5f)
            }
            p.fillRect(x - .8f, top - .8f, x + bar + .8f, b, Fill.Solid(color), .18f)
            p.fillRect(x, top, x + bar, b, Fill.Linear(0f, top, 0f, b, intArrayOf(Argb.mix(color, Argb.rgb(255, 255, 255), .25f), Argb.withAlpha(color, .55f))))
        }
    }
}

/** Ring chart. Segments carry real byte sizes; the visible arc is the measured used share. */
object Donut {
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, thickness: Float, usedPercent: Int?,
             segments: List<ContinuumDashboardState.Segment>) {
        val rr = radius - thickness / 2f
        p.strokeCircle(cx, cy, rr, thickness, Fill.Solid(Argb.rgb(24, 38, 72)))
        if (usedPercent == null) return
        val total = segments.sumOf { it.bytes ?: 0L }.coerceAtLeast(1L)
        var start = -90f
        val sweepTotal = 360f * usedPercent.coerceIn(0, 100) / 100f
        val parts = segments.filter { (it.bytes ?: 0L) > 0L }
        if (parts.isEmpty()) {
            p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, start, sweepTotal, thickness, Fill.Solid(ContinuumTheme.CYAN), roundCap = false)
            return
        }
        for (seg in parts) {
            val sweep = sweepTotal * ((seg.bytes ?: 0L).toFloat() / total)
            p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, start, sweep, thickness * 1.9f, Fill.Solid(seg.color), .14f, roundCap = false)
            p.strokeArc(cx - rr, cy - rr, cx + rr, cy + rr, start, max(.5f, sweep - 1.2f), thickness, Fill.Solid(seg.color), roundCap = false)
            start += sweep
        }
    }
}

/** Small ring dots in front of event rows (colour by event kind). */
object EventDot {
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, kind: Int) {
        val color = when (kind % 4) { 0 -> Argb.rgb(130, 110, 255); 1 -> Argb.rgb(40, 230, 200); 2 -> Argb.rgb(90, 160, 255); else -> Argb.rgb(170, 140, 255) }
        p.fillCircle(cx, cy, 4.2f, Fill.Solid(Argb.withAlpha(color, .25f)))
        p.strokeCircle(cx, cy, 4.2f, .9f, Fill.Solid(color))
        p.fillCircle(cx, cy, 1.7f, Fill.Solid(Argb.mix(color, Argb.rgb(255, 255, 255), .4f)))
    }
}

/** Phone bottom navigation panel with five destinations; [selected] index is highlighted. */
class BottomNavElement(private val labels: List<Pair<Icon, String>>, var selected: Int = 0, textScale: Float = 1f) :
    ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        p.fillRoundRect(0f, 0f, w, h + 30f, 22f, Fill.Linear(0f, 0f, 0f, h, intArrayOf(Argb.rgb(6, 16, 40), Argb.rgb(2, 9, 26))))
        p.strokeRoundRect(0f, 0f, w, h + 30f, 22f, 1f, Fill.Linear(0f, 0f, w, 0f,
            intArrayOf(Argb.rgb(40, 90, 170), Argb.rgb(24, 46, 96), Argb.rgb(60, 60, 140))), .9f)
        val centers = floatArrayOf(49.7f, 127f, 205f, 285f, 363f).map { it * w / 412f }
        for ((i, item) in labels.withIndex()) {
            val cx = centers.getOrElse(i) { w * (i + .5f) / labels.size }
            val active = i == selected
            if (active) {
                p.fillCircle(cx, 20f, 18f, Fill.Radial(cx, 20f, 18f, intArrayOf(Argb.of(110, 40, 150, 255), 0)))
                ContinuumIcons.draw(p, item.first, cx, 20.2f, 22f, Fill.Linear(cx, 9f, cx, 31f,
                    intArrayOf(Argb.rgb(120, 225, 255), Argb.rgb(40, 110, 255))), 2.1f)
                p.drawText(item.second, cx, 44f, t(500, 9.5f), Fill.Solid(Argb.rgb(70, 170, 255)), TextAlign.CENTER)
                p.fillRoundRect(cx - 16f, 47.2f, cx + 16f, 48.8f, .8f, Fill.Solid(Argb.rgb(70, 170, 255)))
                p.fillRoundRect(cx - 18f, 46f, cx + 18f, 50f, 2f, Fill.Solid(Argb.rgb(70, 170, 255)), .18f)
            } else {
                ContinuumIcons.draw(p, item.first, cx, 20.2f, 22f, Fill.Solid(Argb.rgb(168, 190, 225)), 1.6f)
                p.drawText(item.second, cx, 44f, t(500, 8.95f), Fill.Solid(Argb.rgb(178, 190, 212)), TextAlign.CENTER)
            }
        }
    }
    override fun describe(s: ContinuumDashboardState) = "Navigation"
}
