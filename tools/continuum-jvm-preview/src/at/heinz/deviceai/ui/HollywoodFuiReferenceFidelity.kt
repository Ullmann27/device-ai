package at.heinz.deviceai.ui

import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reference-fidelity measurement of the work-window glass construct (VIS-REF-FIDELITY-1).
 *
 * Measures the same pixel statistics on (a) glass panels of the owner's reference designs
 * (`docs/design-screenshots/ref5-fold7-offen.png`, `jarvis-master-20261104.jpg`) and (b) the construct rendered
 * through the production painter stack ([HollywoodFuiCinematicsPreview.paintConstruct]) at reference pixel scale.
 * The reference envelope is derived from the reference panels only, before any candidate is measured; negative
 * controls (hue-rotated candidate, blank pane) must fail the envelope, otherwise the metric is not trusted.
 *
 * Measured per panel rectangle (edges snapped to the brightest line within ±8 px):
 *  - rimL        mean luminance of the ±3 px rim band (neon edge),
 *  - haloL       mean luminance 4–10 px outside the rim (glow spill),
 *  - interiorL   median luminance of the interior inset by 10 % (dark glass; median ignores text),
 *  - rimContrast rimL / interiorL,
 *  - haloSpread  haloL / rimL,
 *  - cyan/blue/violet/neutral/offPalette shares of lit rim pixels (HSV hue buckets).
 *
 * This is a Java2D stand-in: no Android/Filament pixels, no device luminance, no frame times. Reference values are
 * design targets, not measured device output.
 */
object HollywoodFuiReferenceFidelity {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    data class Metrics(val rimL: Double, val haloL: Double, val interiorL: Double, val rimContrast: Double,
                       val haloSpread: Double, val cyan: Double, val blue: Double, val violet: Double,
                       val neutral: Double, val offPalette: Double, val litRimShare: Double) {
        fun json(): String = """{"rimL":${f(rimL)},"haloL":${f(haloL)},"interiorL":${f(interiorL)},"rimContrast":${f(rimContrast)},"haloSpread":${f(haloSpread)},"cyan":${f(cyan)},"blue":${f(blue)},"violet":${f(violet)},"neutral":${f(neutral)},"offPalette":${f(offPalette)},"litRimShare":${f(litRimShare)}}"""
    }

    /** Reference envelope: derived from reference panels only, with the preregistered tolerances below. */
    data class Envelope(val rimLMin: Double, val interiorLMax: Double, val rimContrastMin: Double,
                        val haloSpreadMin: Double, val offPaletteMax: Double) {
        fun failures(m: Metrics): List<String> = buildList {
            if (m.rimL < rimLMin) add("rimL %.3f < %.3f".format(m.rimL, rimLMin))
            if (m.interiorL > interiorLMax) add("interiorL %.3f > %.3f".format(m.interiorL, interiorLMax))
            if (m.rimContrast < rimContrastMin) add("rimContrast %.2f < %.2f".format(m.rimContrast, rimContrastMin))
            if (m.haloSpread < haloSpreadMin) add("haloSpread %.3f < %.3f".format(m.haloSpread, haloSpreadMin))
            if (m.offPalette > offPaletteMax) add("offPalette %.3f > %.3f".format(m.offPalette, offPaletteMax))
        }

        fun json(): String = """{"rimLMin":${f(rimLMin)},"interiorLMax":${f(interiorLMax)},"rimContrastMin":${f(rimContrastMin)},"haloSpreadMin":${f(haloSpreadMin)},"offPaletteMax":${f(offPaletteMax)}}"""

        companion object {
            /** Tolerances fixed before candidate measurement: 60 % of the weakest reference rim/halo/contrast,
             *  150 % of the brightest reference interior, reference off-palette maximum plus 5 points. */
            fun from(reference: Collection<Metrics>): Envelope = Envelope(
                rimLMin = reference.minOf { it.rimL } * .60,
                interiorLMax = reference.maxOf { it.interiorL } * 1.50,
                rimContrastMin = reference.minOf { it.rimContrast } * .60,
                haloSpreadMin = reference.minOf { it.haloSpread } * .60,
                offPaletteMax = reference.maxOf { it.offPalette } + .05)
        }
    }

    private class ReferencePanel(val image: String, val name: String, val rect: Rect)

    // Panel rectangles read off the reference designs (approximate; edges are snapped before measuring).
    private val referencePanels = listOf(
        ReferencePanel("ref5-fold7-offen.png", "ref5-chat-bubble", Rect(290, 446, 566, 552)),
        ReferencePanel("ref5-fold7-offen.png", "ref5-chat-tile", Rect(290, 568, 548, 646)),
        ReferencePanel("ref5-fold7-offen.png", "ref5-active-agents", Rect(290, 752, 566, 912)),
        ReferencePanel("ref5-fold7-offen.png", "ref5-status-right", Rect(1172, 230, 1364, 466)),
        ReferencePanel("ref5-fold7-offen.png", "ref5-local-model", Rect(1082, 752, 1364, 912)),
        ReferencePanel("jarvis-master-20261104.jpg", "master-chat-bubble", Rect(270, 368, 640, 440)),
        ReferencePanel("jarvis-master-20261104.jpg", "master-chat-tile", Rect(262, 506, 508, 590)),
        ReferencePanel("jarvis-master-20261104.jpg", "master-active-agents", Rect(262, 700, 546, 886)),
        ReferencePanel("jarvis-master-20261104.jpg", "master-status-right", Rect(1218, 158, 1518, 400)),
    )

    /** ref5 shows the 884 dp Fold 7 at 1448 px: 1.64 px/dp. Rendering at that scale keeps the ±3 px bands comparable. */
    private const val REFERENCE_SCALE = 1.64f

    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val out = File(args[1]).also { it.mkdirs() }
        val label = args.getOrElse(2) { "candidate" }
        val sb = StringBuilder("{\n")
        sb.append("  \"tool\": \"HollywoodFuiReferenceFidelity\",\n  \"java2dStandIn\": true, \"deviceVerified\": false,\n")
        sb.append("  \"referenceScalePxPerDp\": $REFERENCE_SCALE,\n")

        // 1. References (snapped) and the envelope derived from them.
        val refMetrics = LinkedHashMap<String, Pair<Metrics, Rect>>()
        val refCrops = ArrayList<Pair<String, BufferedImage>>()
        for (panel in referencePanels) {
            val image = ImageIO.read(File(root, "docs/design-screenshots/${panel.image}"))
            val snapped = snapEdges(image, panel.rect, 8)
            refMetrics[panel.name] = measure(image, snapped) to snapped
            refCrops += panel.name to crop(image, snapped, 14)
        }
        val envelope = Envelope.from(refMetrics.values.map { it.first })
        sb.append("  \"reference\": {\n")
        sb.append(refMetrics.entries.joinToString(",\n") { (name, pair) ->
            "    \"$name\": {\"rect\": ${rectJson(pair.second)}, \"metrics\": ${pair.first.json()}}" })
        sb.append("\n  },\n  \"envelope\": ${envelope.json()},\n")
        val referenceSelfCheck = refMetrics.values.all { envelope.failures(it.first).isEmpty() }

        // 2. Candidate: production painter stack, settled working frame and glass-end frame, fold and phone geometry.
        data class Scene(val name: String, val widthDp: Int, val heightDp: Int, val box: HollywoodFuiCinematics.Box,
                         val originX: Float, val originY: Float)
        val scenes = listOf(
            Scene("fold", 884, 760, HollywoodFuiCinematics.Box(452f, 96f, 852f, 540f), 330f, 230f),
            Scene("phone", 412, 915, HollywoodFuiCinematics.Box(20f, 380f, 392f, 860f), 206f, 200f))
        val high = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, false, 0, false, false))
        val low = HollywoodFuiCinematics.Budget.from(JarvisRenderQuality.resolve(true, true, 3, false, false))
        data class Probe(val name: String, val scene: Scene, val timeMs: Long, val budget: HollywoodFuiCinematics.Budget,
                         val working: Boolean)
        val probes = listOf(
            Probe("fold-settled-high", scenes[0], 2400L, high, true),
            Probe("fold-glassend-high", scenes[0], HollywoodFuiConstruct.GLASS_END_MS, high, false),
            Probe("phone-settled-high", scenes[1], 2400L, high, true),
            Probe("fold-settled-low", scenes[0], 2400L, low, true))
        val candMetrics = LinkedHashMap<String, Pair<Metrics, List<String>>>()
        val candCrops = ArrayList<Pair<String, BufferedImage>>()
        var candidateImage: BufferedImage? = null
        var candidateRect: Rect? = null
        for (probe in probes) {
            val image = render(root, probe.scene.widthDp, probe.scene.heightDp, probe.scene.box, probe.scene.originX,
                probe.scene.originY, HollywoodFuiConstruct.frame(probe.timeMs), probe.timeMs, probe.budget, probe.working)
            val rect = Rect((probe.scene.box.left * REFERENCE_SCALE).roundToInt(), (probe.scene.box.top * REFERENCE_SCALE).roundToInt(),
                (probe.scene.box.right * REFERENCE_SCALE).roundToInt(), (probe.scene.box.bottom * REFERENCE_SCALE).roundToInt())
            val snapped = snapEdges(image, rect, 8)
            val metrics = measure(image, snapped)
            candMetrics[probe.name] = metrics to envelope.failures(metrics)
            candCrops += probe.name to crop(image, snapped, 14)
            ImageIO.write(image, "png", File(out, "$label-${probe.name}.png"))
            if (probe.name == "fold-settled-high") { candidateImage = image; candidateRect = snapped }
        }
        sb.append("  \"candidate\": \"$label\",\n  \"probes\": {\n")
        sb.append(candMetrics.entries.joinToString(",\n") { (name, pair) ->
            "    \"$name\": {\"metrics\": ${pair.first.json()}, \"failures\": [${pair.second.joinToString(",") { "\"$it\"" }}]}" })
        sb.append("\n  },\n")

        // 3. Negative controls on the fold-settled-high frame: a 180° hue rotation (cyan → red) must break the palette
        //    (120° would map cyan onto in-palette violet and is vacuous), a blank pane must lose rim contrast. If either
        //    passes, the metric does not discriminate and nothing above may be trusted.
        val base = candidateImage!!; val baseRect = candidateRect!!
        val rotated = hueRotate(base, 180f)
        val blank = blankPane(base.width, base.height)
        val rotatedM = measure(rotated, baseRect); val blankM = measure(blank, snapEdges(blank, baseRect, 8))
        val rotatedFails = envelope.failures(rotatedM); val blankFails = envelope.failures(blankM)
        ImageIO.write(rotated, "png", File(out, "$label-control-hue-rotated-180.png"))
        sb.append("  \"controls\": {\n")
        sb.append("    \"hue-rotated-120\": {\"metrics\": ${rotatedM.json()}, \"failures\": [${rotatedFails.joinToString(",") { "\"$it\"" }}], \"expected\": \"FAIL\"},\n")
        sb.append("    \"blank-pane\": {\"metrics\": ${blankM.json()}, \"failures\": [${blankFails.joinToString(",") { "\"$it\"" }}], \"expected\": \"FAIL\"}\n  },\n")
        val controlsDiscriminate = rotatedFails.isNotEmpty() && blankFails.isNotEmpty()

        // 4. Verdicts. Gate = the two HIGH-budget settled probes (fold, phone); glass-end and LOW are reported only.
        val gateProbes = listOf("fold-settled-high", "phone-settled-high")
        val gatePass = gateProbes.all { candMetrics.getValue(it).second.isEmpty() }
        sb.append("  \"referenceSelfCheck\": $referenceSelfCheck,\n  \"controlsDiscriminate\": $controlsDiscriminate,\n")
        sb.append("  \"gateProbes\": [${gateProbes.joinToString(",") { "\"$it\"" }}],\n  \"gate\": \"${if (gatePass) "PASS" else "FAIL"}\",\n")
        sb.append("  \"metricTrusted\": ${referenceSelfCheck && controlsDiscriminate}\n}\n")
        File(out, "$label-fidelity.json").writeText(sb.toString())
        ImageIO.write(contactSheet(refCrops, candCrops, refMetrics.mapValues { it.value.first }, candMetrics.mapValues { it.value.first }),
            "png", File(out, "$label-contact-sheet.png"))

        println("reference panels: ${refMetrics.size}; envelope ${envelope.json()}")
        for ((name, pair) in refMetrics) println("  REF  %-22s %s".format(name, pair.first.json()))
        for ((name, pair) in candMetrics) println("  %s %-22s %s %s".format(if (pair.second.isEmpty()) "PASS" else "FAIL", name, pair.first.json(), pair.second))
        println("  control hue-rotated: ${if (rotatedFails.isNotEmpty()) "FAIL as expected" else "PASSED (metric broken)"} $rotatedFails")
        println("  control blank-pane : ${if (blankFails.isNotEmpty()) "FAIL as expected" else "PASSED (metric broken)"} $blankFails")
        println("metricTrusted=${referenceSelfCheck && controlsDiscriminate} gate=${if (gatePass) "PASS" else "FAIL"} ($label; Java2D stand-in, not device)")
        if (!(referenceSelfCheck && controlsDiscriminate)) System.exit(3)
        if (!gatePass) System.exit(1)
    }

    // ---- rendering -------------------------------------------------------------------------------------------------

    fun render(root: File, widthDp: Int, heightDp: Int, box: HollywoodFuiCinematics.Box, originX: Float, originY: Float,
               frame: HollywoodFuiConstruct.Frame, timeMs: Long, budget: HollywoodFuiCinematics.Budget,
               working: Boolean): BufferedImage {
        val image = BufferedImage((widthDp * REFERENCE_SCALE).toInt(), (heightDp * REFERENCE_SCALE).toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, Color(0x03, 0x07, 0x16), 0f, image.height.toFloat(), Color(0x05, 0x0B, 0x22))
        g.fillRect(0, 0, image.width, image.height)
        g.scale(REFERENCE_SCALE.toDouble(), REFERENCE_SCALE.toDouble())
        HollywoodFuiCinematicsPreview.paintConstruct(g, root, box, originX, originY, frame, timeMs, budget, working)
        g.dispose()
        return image
    }

    // ---- measurement -----------------------------------------------------------------------------------------------

    fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / 255.0; val g = (argb shr 8 and 0xFF) / 255.0; val b = (argb and 0xFF) / 255.0
        return .2126 * r + .7152 * g + .0722 * b
    }

    /** Hue bucket of a pixel: 0 dark, 1 neutral (low saturation), 2 cyan, 3 blue, 4 violet/magenta, 5 off-palette. */
    fun hueBucket(argb: Int): Int {
        val hsb = Color.RGBtoHSB(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF, null)
        val hue = hsb[0] * 360f; val sat = hsb[1]; val value = hsb[2]
        if (value < .25f) return 0
        if (sat < .20f) return 1
        return when {
            hue in 165f..205f -> 2
            hue > 205f && hue <= 255f -> 3
            hue > 255f && hue <= 335f -> 4
            else -> 5
        }
    }

    /** Moves each edge to the brightest parallel line within ±search px (corners excluded), so approximate
     *  reference rectangles and anti-aliased renders are measured on their actual rim. */
    fun snapEdges(image: BufferedImage, rect: Rect, search: Int): Rect {
        fun lineMean(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            var sum = 0.0; var n = 0
            for (y in y0..y1) for (x in x0..x1) {
                if (x in 0 until image.width && y in 0 until image.height) { sum += luminance(image.getRGB(x, y)); n++ }
            }
            return if (n == 0) 0.0 else sum / n
        }
        val cx0 = rect.left + rect.width / 8; val cx1 = rect.right - rect.width / 8
        val cy0 = rect.top + rect.height / 8; val cy1 = rect.bottom - rect.height / 8
        fun best(candidates: IntRange, mean: (Int) -> Double): Int = candidates.maxByOrNull { mean(it) } ?: candidates.first
        val top = best(rect.top - search..rect.top + search) { lineMean(cx0, it, cx1, it) }
        val bottom = best(rect.bottom - search..rect.bottom + search) { lineMean(cx0, it, cx1, it) }
        val left = best(rect.left - search..rect.left + search) { lineMean(it, cy0, it, cy1) }
        val right = best(rect.right - search..rect.right + search) { lineMean(it, cy0, it, cy1) }
        return Rect(left, top, right, bottom)
    }

    /** Signed distance of a pixel centre to the rectangle border (negative inside), in px. */
    private fun borderDistance(rect: Rect, x: Int, y: Int): Double {
        val dx = max(rect.left - x, x - rect.right).toDouble()
        val dy = max(rect.top - y, y - rect.bottom).toDouble()
        return if (dx <= 0 && dy <= 0) max(dx, dy) else Math.hypot(max(dx, 0.0), max(dy, 0.0))
    }

    fun measure(image: BufferedImage, rect: Rect): Metrics {
        var rimSum = 0.0; var rimN = 0; var haloSum = 0.0; var haloN = 0
        val buckets = IntArray(6); var lit = 0
        val interior = ArrayList<Double>()
        val inset = (min(rect.width, rect.height) * .10).roundToInt()
        val x0 = max(0, rect.left - 10); val x1 = min(image.width - 1, rect.right + 10)
        val y0 = max(0, rect.top - 10); val y1 = min(image.height - 1, rect.bottom + 10)
        for (y in y0..y1) for (x in x0..x1) {
            val argb = image.getRGB(x, y)
            val d = borderDistance(rect, x, y)
            val l = luminance(argb)
            when {
                abs(d) <= 3.0 -> {
                    rimSum += l; rimN++
                    val bucket = hueBucket(argb)
                    if (bucket != 0) { lit++; buckets[bucket]++ }
                }
                d > 3.0 && d <= 10.0 -> { haloSum += l; haloN++ }
                d <= -inset -> interior += l
            }
        }
        val rimL = if (rimN == 0) 0.0 else rimSum / rimN
        val haloL = if (haloN == 0) 0.0 else haloSum / haloN
        val interiorL = if (interior.isEmpty()) 0.0 else interior.sorted()[interior.size / 2]
        fun share(b: Int) = if (lit == 0) 0.0 else buckets[b].toDouble() / lit
        return Metrics(rimL, haloL, interiorL, rimL / max(interiorL, .01), haloL / max(rimL, 1e-3),
            share(2), share(3), share(4), share(1), share(5), if (rimN == 0) 0.0 else lit.toDouble() / rimN)
    }

    // ---- controls and sheets ---------------------------------------------------------------------------------------

    fun hueRotate(image: BufferedImage, degrees: Float): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            val hsb = Color.RGBtoHSB(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF, null)
            val rgb = Color.HSBtoRGB(((hsb[0] * 360f + degrees) % 360f) / 360f, hsb[1], hsb[2])
            out.setRGB(x, y, (argb and 0xFF000000.toInt()) or (rgb and 0xFFFFFF))
        }
        return out
    }

    fun blankPane(width: Int, height: Int): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(0x03, 0x07, 0x16), 0f, height.toFloat(), Color(0x05, 0x0B, 0x22))
        g.fillRect(0, 0, width, height)
        g.dispose()
        return out
    }

    private fun crop(image: BufferedImage, rect: Rect, margin: Int): BufferedImage {
        val x0 = max(0, rect.left - margin); val y0 = max(0, rect.top - margin)
        val x1 = min(image.width, rect.right + margin); val y1 = min(image.height, rect.bottom + margin)
        return image.getSubimage(x0, y0, x1 - x0, y1 - y0)
    }

    private fun contactSheet(references: List<Pair<String, BufferedImage>>, candidates: List<Pair<String, BufferedImage>>,
                             refMetrics: Map<String, Metrics>, candMetrics: Map<String, Metrics>): BufferedImage {
        val rowHeight = 180; val gap = 10; val labelHeight = 26
        fun rowWidth(items: List<Pair<String, BufferedImage>>) =
            items.sumOf { (it.second.width * rowHeight / it.second.height) + gap } + gap
        val width = max(rowWidth(references), rowWidth(candidates))
        val sheet = BufferedImage(width, 2 * (rowHeight + labelHeight + gap) + gap, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = Color(0x10, 0x10, 0x14); g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = g.font.deriveFont(10f)
        fun drawRow(items: List<Pair<String, BufferedImage>>, metrics: Map<String, Metrics>, y: Int) {
            var x = gap
            for ((name, img) in items) {
                val w = img.width * rowHeight / img.height
                g.drawImage(img, x, y, w, rowHeight, null)
                val m = metrics.getValue(name)
                g.color = Color(0xDD, 0xDD, 0xDD)
                g.drawString(name, x, y + rowHeight + 11)
                g.drawString("rim %.3f halo %.3f in %.3f off %.2f".format(m.rimL, m.haloL, m.interiorL, m.offPalette), x, y + rowHeight + 22)
                x += w + gap
            }
        }
        drawRow(references, refMetrics, gap)
        drawRow(candidates, candMetrics, gap + rowHeight + labelHeight + gap)
        g.dispose()
        return sheet
    }

    private fun rectJson(r: Rect) = "[${r.left},${r.top},${r.right},${r.bottom}]"
    private fun f(v: Double) = "%.4f".format(java.util.Locale.ROOT, v)
}
