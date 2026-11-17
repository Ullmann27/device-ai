package at.heinz.deviceai.ui

import java.lang.management.ManagementFactory
import java.util.Locale

/** CPU geometry/allocation probe only. No Android Canvas, GPU, text layout or device frame-time claim. */
object HollywoodFuiPainterBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        println("budget,frames,p50_geometry_us,p95_geometry_us,allocated_bytes_per_frame,draw_calls_per_frame")
        for ((name, budget) in listOf(
            "high" to JarvisRenderQuality.resolve(true, false, 0, false, false),
            "warm-inference" to JarvisRenderQuality.resolve(true, false, 2, false, true),
            "low" to JarvisRenderQuality.resolve(true, false, 3, false, true))) {
            val b = HollywoodFuiCinematics.Budget.from(budget)
            val painter = CountingPainter()
            val draw = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
            val box = HollywoodFuiCinematics.Box(452f, 96f, 852f, 540f)
            fun sample(i: Int) {
                val time = 900L + i * 16L
                val frame = HollywoodFuiConstruct.frame(time)
                draw.drawAnchor(painter, 330f, 230f, box, frame, b.bloom)
                draw.drawGlassLayers(painter, box, frame, 6f)
                draw.drawGridAndScan(painter, box, frame, b.bloom)
                draw.drawMotesAndEnergy(painter, box, frame, time, b.motes, 7, b.animate, b.bloom, 6f)
                draw.drawBrackets(painter, box, frame.brackets, b.bloom)
            }
            repeat(2_000, ::sample)
            val times = LongArray(4_000)
            painter.calls = 0
            val allocated = bean.getThreadAllocatedBytes(thread)
            repeat(times.size) { i ->
                val start = System.nanoTime()
                sample(i)
                times[i] = System.nanoTime() - start
            }
            val bytes = bean.getThreadAllocatedBytes(thread) - allocated
            times.sort()
            println(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.1f,%.1f",
                name, times.size, times[times.size / 2] / 1000.0, times[times.size * 95 / 100] / 1000.0,
                bytes.toDouble() / times.size, painter.calls.toDouble() / times.size))
        }
    }

    private class CountingPainter : ContinuumPainter {
        var calls = 0L
        override fun save() {}
        override fun restore() {}
        override fun translate(dx: Float, dy: Float) {}
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { calls++ }
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) { calls++ }
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) { calls++ }
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) { calls++ }
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { calls++ }
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) { calls++ }
        override fun measureText(text: String, style: TextStyle) = 0f
        override fun capHeight(style: TextStyle) = 0f
    }
}
