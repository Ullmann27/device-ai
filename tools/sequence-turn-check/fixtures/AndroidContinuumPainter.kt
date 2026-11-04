package at.heinz.deviceai.ui

import android.graphics.Canvas
import java.lang.reflect.Proxy

/** Rendering is outside this fixture's scope; fail instead of inventing pixels or measurements. */
private fun unavailablePainter(): ContinuumPainter = Proxy.newProxyInstance(
    ContinuumPainter::class.java.classLoader,
    arrayOf(ContinuumPainter::class.java)
) { _, _, _ -> error("HOST_FIXTURE_RENDERING_UNAVAILABLE") } as ContinuumPainter

class AndroidContinuumPainter(assets: Any) : ContinuumPainter by unavailablePainter() {
    fun begin(canvas: Canvas): Unit = error("HOST_FIXTURE_RENDERING_UNAVAILABLE")
}
