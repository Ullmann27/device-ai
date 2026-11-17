package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Actual compact badge and reply preview. */
class JarvisReferenceDialogReadabilityTest {
    @Test fun inputShowsLiveReplyAndRestoresIdlePromptWithReadableBounds() {
        val reply = "Reale Antwort."
        for (active in listOf(true, false)) for (font in listOf(1f, 1.3f, 1.5f, 2f)) {
            val state = ContinuumDashboardState(
                cockpit = CockpitStatus(dialog = listOf(CockpitStatus.DialogLine(false, reply))),
                presentation = ContinuumDashboardState.PresentationStatus(speaking = active))
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
            val expected = if (active) reply else "Was soll ich für dich erledigen?"
            assertTrue("font$font must show $expected: $drawn", drawn.joinToString(" ").contains(expected))
            if (!active) assertFalse("Idle input restores its prompt", drawn.joinToString(" ").contains(reply))
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
