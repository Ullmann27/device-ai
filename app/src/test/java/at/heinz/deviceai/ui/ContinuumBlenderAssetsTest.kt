package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/** Blender assets: packaged and decodable-sized, raster path used when available, vector fallback otherwise. */
class ContinuumBlenderAssetsTest {
    private val assetRoot = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    @After fun reset() { ContinuumBlenderAssets.enabled = true }

    /** Width/height from the RIFF WebP header (VP8 lossy, VP8L lossless or VP8X extended). */
    private fun webpSize(bytes: ByteArray): Pair<Int, Int> {
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        assertEquals("RIFF", String(bytes, 0, 4)); assertEquals("WEBP", String(bytes, 8, 4))
        return when (String(bytes, 12, 4)) {
            "VP8X" -> (1 + (u(24) or (u(25) shl 8) or (u(26) shl 16))) to (1 + (u(27) or (u(28) shl 8) or (u(29) shl 16)))
            "VP8 " -> ((u(26) or (u(27) shl 8)) and 0x3FFF) to ((u(28) or (u(29) shl 8)) and 0x3FFF)
            "VP8L" -> {
                val b = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
                (1 + (b and 0x3FFF)) to (1 + ((b ushr 14) and 0x3FFF))
            }
            else -> error("unknown WebP chunk")
        }
    }

    @Test fun everyDeclaredAssetIsPackagedWithItsDeclaredSizeAndTheSetStaysSmall() {
        var total = 0L
        for (asset in ContinuumBlenderAssets.all) {
            val file = File(assetRoot, asset.path)
            assertTrue("missing ${asset.path}", file.isFile)
            assertTrue(asset.path.startsWith("jarvis-reference/"))  // painters only load this decorative root
            val bytes = file.readBytes()
            assertEquals(asset.path, asset.width to asset.height, webpSize(bytes))
            total += bytes.size
        }
        assertTrue("Blender set must stay below 1.5 MB in the APK, was $total", total < 1_500_000L)
        assertTrue(ContinuumBlenderAssets.all.size + 4 <= MAX_RASTERS)
    }

    @Test fun manifestRecordsProvenanceAndTheOrbMetricsUsedInCode() {
        val manifest = File(assetRoot, ContinuumBlenderAssets.DIR + "manifest.json").readText()
        assertTrue(manifest.contains("\"reference_images_used\": false"))
        assertTrue(manifest.contains("\"contains_runtime_status\": false"))
        fun metric(name: String) = Regex("\"$name\": ([0-9.]+)").find(manifest)!!.groupValues[1].toFloat()
        assertEquals(metric("centerX"), ContinuumBlenderAssets.ORB_CENTER_X, 1e-4f)
        assertEquals(metric("centerY"), ContinuumBlenderAssets.ORB_CENTER_Y, 1e-4f)
        assertEquals(metric("radius"), ContinuumBlenderAssets.ORB_RADIUS, 1e-4f)
        for (asset in ContinuumBlenderAssets.all) assertTrue(asset.path, manifest.contains("\"${asset.path}\""))
    }

    private class Recorder(val rastersWork: Boolean) {
        val rasters = mutableListOf<Pair<String, FloatArray>>()
        var primitives = 0
        var depth = 0
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "save", "saveAlpha" -> depth++
                "restore" -> { assertTrue("unbalanced restore", depth > 0); depth-- }
                "drawRaster" -> rasters += (args!![0] as String) to floatArrayOf(
                    args[1] as Float, args[2] as Float, args[3] as Float, args[4] as Float)
            }
            if (method.name.startsWith("fill") || method.name.startsWith("stroke") || method.name == "drawLine") primitives++
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> rastersWork
                else -> null
            }
        } as ContinuumPainter
    }

    @Test fun painterWithoutRastersFallsBackToTheUnchangedVectorOrb() {
        val vector = Recorder(false)
        ContinuumBrainOrbRenderer().draw(vector.painter, 200f, 180f, 120f)
        val hooked = Recorder(false)
        ContinuumBrainOrbRenderer().apply { rasterDensity = 2.625f }.draw(hooked.painter, 200f, 180f, 120f)
        assertEquals(1, hooked.rasters.size)                     // tried the Blender orb once
        assertEquals(vector.primitives, hooked.primitives)       // then exactly the vector orb
        assertEquals(0, hooked.depth)
        assertFalse(ContinuumBlenderAssets.drawBackground(hooked.painter, 400f, 800f))
        assertFalse(ContinuumBlenderAssets.drawTile(hooked.painter, "memory", 0f, 0f, 90f, 70f, 12f))
        assertEquals(0, hooked.depth)
    }

    @Test fun rasterOrbKeepsLiveSignalsOnTopAndIsPlacedFromTheMeasuredMetrics() {
        val rec = Recorder(true)
        val orb = ContinuumBrainOrbRenderer().apply { rasterDensity = 2.625f }
        orb.draw(rec.painter, 200f, 180f, 120f, ContinuumBrainOrbRenderer.Signals(pulse = 1f, recall = 1f, hypothesis = true))
        assertEquals(ContinuumBlenderAssets.ORB_LARGE.path, rec.rasters.single().first)
        val (l, t, r, b) = rec.rasters.single().second.toList()
        val size = 120f / ContinuumBlenderAssets.ORB_RADIUS
        assertEquals(size, r - l, .01f); assertEquals(size, b - t, .01f)
        assertEquals(200f, l + ContinuumBlenderAssets.ORB_CENTER_X * size, .01f)
        assertEquals(180f, t + ContinuumBlenderAssets.ORB_CENTER_Y * size, .01f)
        val vector = Recorder(false).also { ContinuumBrainOrbRenderer().draw(it.painter, 200f, 180f, 120f) }
        assertTrue("signals/orbits still drawn", rec.primitives > 20)
        assertTrue("static body comes from the raster", rec.primitives < vector.primitives)
        assertEquals(0, rec.depth)
    }

    @Test fun backgroundCoversTheScreenAndPicksTheVariantByAspect() {
        val rec = Recorder(true)
        assertTrue(ContinuumBlenderAssets.drawBackground(rec.painter, 411f, 891f))   // S26 portrait
        assertTrue(ContinuumBlenderAssets.drawBackground(rec.painter, 904f, 832f))   // Fold7 open
        assertEquals(listOf(ContinuumBlenderAssets.BACKGROUND_TALL.path, ContinuumBlenderAssets.BACKGROUND_WIDE.path),
            rec.rasters.map { it.first })
        for ((i, size) in listOf(411f to 891f, 904f to 832f).withIndex()) {
            val (l, t, r, b) = rec.rasters[i].second.toList()
            assertTrue(l <= 0f && t <= 0f && r >= size.first && b >= size.second)
        }
        assertEquals(0, rec.depth)
    }

    @Test fun shellBackgroundAndDockTilesUseTheRastersAndFallBackWithoutThem() {
        val shell = JarvisShellLayout.create(884f, 760f)
        val on = Recorder(true)
        JarvisShellRenderer.draw(on.painter, shell)
        val paths = on.rasters.map { it.first }.toSet()
        assertTrue(paths.toString(), ContinuumBlenderAssets.BACKGROUND_WIDE.path in paths)
        val off = Recorder(false)
        JarvisShellRenderer.draw(off.painter, shell)
        assertTrue(off.primitives > 0)
        assertEquals(0, on.depth); assertEquals(0, off.depth)
        val tile = Recorder(true)
        CockpitGlassButtons.draw(tile.painter, JarvisShellLayout.Box(0f, 0f, 96f, 72f),
            CockpitGlassButtons.Spec("memory", "Gedächtnis", Icon.DATABASE, 0xFFA05CFF.toInt(), "Violett"), active = false)
        assertTrue(tile.rasters.any { it.first == ContinuumBlenderAssets.TILES.getValue("memory").path })
        val disabled = Recorder(true)
        CockpitGlassButtons.draw(disabled.painter, JarvisShellLayout.Box(0f, 0f, 96f, 72f),
            CockpitGlassButtons.Spec("memory", "Gedächtnis", Icon.DATABASE, 0xFFA05CFF.toInt(), "Violett"),
            active = false, disabled = true)
        assertTrue(disabled.rasters.none { it.first.startsWith(ContinuumBlenderAssets.DIR) })
    }

    @Test fun switchOffSkipsEveryRaster() {
        ContinuumBlenderAssets.enabled = false
        val rec = Recorder(true)
        assertFalse(ContinuumBlenderAssets.drawOrb(rec.painter, 1f, 1f, 50f, 2f))
        assertFalse(ContinuumBlenderAssets.drawBackground(rec.painter, 400f, 800f))
        assertFalse(ContinuumBlenderAssets.drawTile(rec.painter, "chat", 0f, 0f, 80f, 60f, 10f))
        assertTrue(rec.rasters.isEmpty())
    }

    @Test fun orbVariantFollowsPixelSize() {
        assertEquals(ContinuumBlenderAssets.ORB_SMALL, ContinuumBlenderAssets.orbAsset(40f, 2.625f))
        assertEquals(ContinuumBlenderAssets.ORB_LARGE, ContinuumBlenderAssets.orbAsset(140f, 2.625f))
    }
}
