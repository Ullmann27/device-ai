package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AvatarPresentationTest {
    @Test fun cockpitFaceKeepsOverlayBehindAndChestStreamsInFront() {
        val head = Box(0f, 0f, 420f, 300f)
        val back = Recorder()
        val front = Recorder()
        AvatarPresentation.drawBack(back.painter, head, 1f, .18f, true)
        AvatarPresentation.drawFront(front.painter, head, 1f, .18f, true)
        assertTrue("Scanlines retained behind skin", back.lines > 50)
        assertEquals("Both overlay rings retained", 2, back.ovals)
        assertEquals("No scanlines across solid face", 0, front.lines)
        assertEquals("No overlay rings across face", 0, front.ovals)
        assertEquals("Both chest streams retained", 4, front.paths.size)
        for (path in front.paths) for (op in path.ops) {
            val y = when (op) {
                is VectorPath.Op.Move -> listOf(op.y)
                is VectorPath.Op.Cubic -> listOf(op.y1, op.y2, op.y)
                else -> emptyList()
            }
            assertTrue("Chest effects stay below chin", y.all { it >= head.height * .75f })
        }
    }

    @Test fun standaloneAppearanceKeepsOriginalOverlayAndBrand() {
        val recorder = Recorder()
        AvatarPresentation.drawFront(recorder.painter, Box(0f, 0f, 420f, 300f), 1f, .18f, false)
        assertTrue(recorder.lines > 50)
        assertEquals(2, recorder.ovals)
        assertTrue(AvatarPresentation.showOrbBrand(false, true))
        assertTrue(AvatarPresentation.showOrbBrand(true, false))
        assertFalse(AvatarPresentation.showOrbBrand(true, true))
    }

    private class Recorder {
        var lines = 0
        var ovals = 0
        val paths = mutableListOf<VectorPath>()
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "drawLine" -> lines++
                "strokeOval" -> ovals++
                "strokePath" -> paths.add(args!![0] as VectorPath)
            }
            when (method.returnType) {
                java.lang.Float.TYPE -> 0f
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        } as ContinuumPainter
    }
}
