package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import java.util.Locale

/** Read-only instruments for the existing Android dashboard sampler. No synthetic history. */
object CommandWorkstationInstruments {
    data class Reading(val label: String, val percent: Int?, val note: String) {
        val value: String get() = percent?.let { "$it %" } ?: "—"
    }

    fun cpu(system: ContinuumDashboardState.SystemStatus) = Reading(
        "APP-CPU", system.appCpuPercent?.takeIf { it in 0..100 }, "Anteil dieser App")

    fun memory(system: ContinuumDashboardState.SystemStatus): Reading {
        val free = system.freeRamBytes?.takeIf { it >= 0L }
        return Reading("GERÄTE-RAM", system.memoryPercent?.takeIf { it in 0..100 },
            if (free == null) "Belegt im Gerät" else
                String.format(Locale.GERMAN, "%.1f GiB frei", free.toDouble() / 1_073_741_824.0))
    }

    /** Shared with native hit targets. Short panels keep their complete original content. */
    fun telemetryBox(box: Box): Box? = if (box.height >= 208f && box.width >= 96f)
        Box(box.left, box.bottom - 116f, box.right, box.bottom) else null

    fun contentBox(box: Box): Box = telemetryBox(box)?.let {
        Box(box.left, box.top, box.right, it.top - 8f)
    } ?: box

    fun drawCpu(p: ContinuumPainter, box: Box, system: ContinuumDashboardState.SystemStatus) {
        val reading = cpu(system)
        val graph = drawBase(p, box, reading)
        drawGrid(p, graph)
        val samples = system.activity.takeLast(24)
        var previousX = 0f
        var previousY = 0f
        var connected = false
        samples.forEachIndexed { index, sample ->
            if (!sample.isFinite() || sample !in 0f..100f) {
                connected = false
            } else {
                val x = if (samples.size == 1) graph.right else
                    graph.left + graph.width * index / (samples.size - 1)
                val y = graph.bottom - graph.height * sample / 100f
                if (connected) {
                    p.drawLine(previousX, previousY, x, y, 3.4f, Fill.Solid(Colors.cyan), .10f)
                    p.drawLine(previousX, previousY, x, y, 1.1f, Fill.Solid(Colors.cyanSoft))
                }
                previousX = x; previousY = y; connected = true
                if (index == samples.lastIndex)
                    p.fillCircle(x, y, 1.8f, Fill.Solid(Colors.activeWhite))
            }
        }
        if (samples.none { it.isFinite() && it in 0f..100f })
            caption(p, "Noch keine Messreihe", graph.left, graph.centerY + 4f, graph.width)
        caption(p, "0–100 % · letzte Messungen", box.left + 10f, box.bottom - 7f, box.width - 20f)
    }

    fun drawMemory(p: ContinuumPainter, box: Box, system: ContinuumDashboardState.SystemStatus) {
        val reading = memory(system)
        val graph = drawBase(p, box, reading)
        val gauge = Box(graph.left, graph.top + 10f, graph.right, graph.top + 22f)
        val gap = 2f
        val segments = 20
        val step = gauge.width / segments
        for (i in 0 until segments) {
            val left = gauge.left + i * step
            p.fillRect(left, gauge.top, left + maxOf(.5f, step - gap), gauge.bottom,
                Fill.Solid(Colors.rim), .25f)
            val amount = reading.percent?.let { (it / 5f - i).coerceIn(0f, 1f) } ?: 0f
            if (amount > 0f) p.fillRect(left, gauge.top,
                left + maxOf(.5f, step - gap) * amount, gauge.bottom,
                Fill.Solid(Colors.cyanSoft), .85f)
        }
        caption(p, if (reading.percent == null) "Messung nicht verfügbar" else "0–100 % · aktueller Wert",
            box.left + 10f, box.bottom - 7f, box.width - 20f)
    }

    private fun drawBase(p: ContinuumPainter, b: Box, reading: Reading): Box {
        p.drawLine(b.left + 9f, b.top, b.right - 9f, b.top, .7f, Fill.Solid(Colors.rim), .75f)
        val label = TextStyle(FontFamily.TEXT, 600, 10.5f, .5f)
        val figure = TextStyle(FontFamily.DISPLAY, 500, 25f)
        p.drawText(JarvisShellRenderer.fit(p, reading.label, label, b.width - 20f),
            b.left + 10f, b.top + 18f, label, Fill.Solid(Colors.textSecondary))
        p.drawText(reading.value, b.left + 10f, b.top + 46f, figure, Fill.Solid(Colors.text))
        caption(p, reading.note, b.left + 10f, b.top + 61f, b.width - 20f)
        return Box(b.left + 10f, b.top + 69f, b.right - 10f, b.bottom - 23f)
    }

    private fun drawGrid(p: ContinuumPainter, b: Box) {
        for (i in 0..2) {
            val y = b.top + b.height * i / 2f
            p.drawLine(b.left, y, b.right, y, .45f, Fill.Solid(Colors.rim), .55f)
        }
        for (i in 0..8) {
            val x = b.left + b.width * i / 8f
            p.drawLine(x, b.top, x, b.bottom, .45f, Fill.Solid(Colors.rim), .24f)
        }
    }

    private fun caption(p: ContinuumPainter, text: String, x: Float, y: Float, width: Float) {
        val style = TextStyle(FontFamily.TEXT, 400, 10.5f)
        p.drawText(JarvisShellRenderer.fit(p, text, style, width), x, y, style, Fill.Solid(Colors.textSecondary))
    }
}
