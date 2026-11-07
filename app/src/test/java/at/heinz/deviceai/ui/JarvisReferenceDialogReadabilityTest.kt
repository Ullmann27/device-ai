package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Actual compact badge and reply preview. */
class JarvisReferenceDialogReadabilityTest {
    @Test fun inputRetainsTheActualReplyPreviewWithReadableBounds() {
        val reply = "Reale Antwort."
        val state = ContinuumDashboardState(cockpit = CockpitStatus(dialog = listOf(CockpitStatus.DialogLine(false, reply))))
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) {
            val shell = JarvisShellLayout.create(840f, 760f, font)
            val b = requireNotNull(shell[JarvisShellLayout.Slot.INPUT])
            val drawn = mutableListOf<String>()
            val baselines = mutableListOf<Float>()
            val base = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
                arrayOf(ContinuumPainter::class.java)) { _, method, args ->
                when (method.name) {
                    "drawText" -> { drawn += args[0] as String; baselines += args[2] as Float; null }
                    "measureText" -> (args[0] as String).length * (args[1] as TextStyle).size * .52f
                    "capHeight" -> (args[0] as TextStyle).size * .75f
                    "drawRaster" -> false
                    else -> null
                }
            } as ContinuumPainter
            val p = JarvisTextScalePainter(base).apply { fontScale = font }
            assertTrue(JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.INPUT, b,
                shell.grid, state, 0L, shell))
            assertTrue("font$font must retain the actual answer: $drawn", drawn.joinToString(" ").contains(reply))
            assertTrue("text remains inside input", baselines.all { it > b.top && it < b.bottom })
            assertTrue(shell.dialogControlBoxes().all { it.height >= 48f - .01f })
        }
    }

    @Test fun compactBadgeNeverInventsReadyWhileTheModelIsUnavailable() {
        for ((online, expected) in listOf(false to "Offline", true to "Bereit")) {
            val state = ContinuumDashboardState(model = ContinuumDashboardState.ModelStatus(online = online))
            val shell = JarvisShellLayout.create(840f, 760f)
            val drawn = mutableListOf<String>()
            val p = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
                arrayOf(ContinuumPainter::class.java)) { _, method, args ->
                when (method.name) {
                    "drawText" -> { drawn += args[0] as String; null }
                    "measureText" -> (args[0] as String).length * (args[1] as TextStyle).size * .52f
                    "capHeight" -> (args[0] as TextStyle).size * .75f
                    "drawRaster" -> false
                    else -> null
                }
            } as ContinuumPainter
            assertTrue(JarvisCockpitGridContent.draw(p, JarvisShellLayout.Slot.DIALOG,
                requireNotNull(shell[JarvisShellLayout.Slot.DIALOG]), shell.grid, state, 0L, shell))
            assertTrue(drawn.contains(expected))
            if (!online) assertFalse(drawn.contains("Bereit"))
        }
    }
}
