package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Shared original-effect composition. */
object AvatarPresentation {
    fun showOrbBrand(layered: Boolean, headEnabled: Boolean) = !layered || !headEnabled

    fun drawBack(p: ContinuumPainter, head: Box, seconds: Float, energy: Float, layered: Boolean) {
        if (layered) JarvisCinemaEntrance.drawHologramOverlay(p, head, seconds, energy)
    }

    fun drawFront(p: ContinuumPainter, head: Box, seconds: Float, energy: Float, layered: Boolean) {
        p.save()
        p.translate(head.left, head.top)
        JarvisHeadAura.drawLightStreams(p, head.width, head.height)
        if (!layered) JarvisCinemaEntrance.drawHologramOverlay(p,
            Box(0f, 0f, head.width, head.height), seconds, energy)
        p.restore()
    }
}
