package at.heinz.deviceai.avatar

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.JarvisSequenceView
import android.graphics.Rect

/**
 * The one Jarvis avatar for Kern's AVATAR slot (#481): holographic wireframe bust + brain orb
 * (existing [ContinuumHologramView] with [ContinuumAvatarRenderer] / ContinuumHologramRenderer) and the sequence
 * overlay ([JarvisSequenceView]: fly-in, data flight, envelope, pills, alarm).
 *
 * The Activity supplies its existing signal-bearing [liveSurface]. The adapter only installs the cockpit
 * overlay; it never replaces the Director's surface, changes the saved appearance or starts another avatar clock.
 * A standalone host can omit [liveSurface] and owns the resulting stage itself.
 */
class JarvisHoloAvatar(private val liveSurface: ContinuumHologramView? = null) : at.heinz.deviceai.cockpit.JarvisAvatarRenderer {
    private var host: ViewGroup? = null
    private var stage: FrameLayout? = null
    var hologram: ContinuumHologramView? = null; private set
    var sequence: JarvisSequenceView? = null; private set

    /** Preserve the Activity's surface ownership. */
    internal fun canAttachTo(host: ViewGroup): Boolean = liveSurface == null || liveSurface.parent === host

    override fun attach(host: ViewGroup, slot: Rect) {
        if (this.host === host && isAttached) { onSlotChanged(slot); return }
        detach()
        val holo = liveSurface ?: ContinuumHologramView(host.context)
        if (liveSurface != null) require(holo.parent === host) { "The Activity owns the live avatar's parent" }
        holo.setGeometry(ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)
        val seq = JarvisSequenceView(host.context)
        holo.setCockpitSequence(seq)
        if (liveSurface == null) {
            val s = FrameLayout(host.context)
            s.addView(holo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            host.addView(s, ViewGroup.MarginLayoutParams(slot.width(), slot.height()))
            stage = s
        }
        this.host = host; hologram = holo; sequence = seq
        onSlotChanged(slot)
    }

    /** Bounds changes preserve the surface, pose, speech stream and overlay. */
    override fun onSlotChanged(slot: Rect) {
        stage?.let { s ->
            s.layoutParams = s.layoutParams.apply { width = slot.width(); height = slot.height() }
            s.x = slot.left.toFloat(); s.y = slot.top.toFloat()
        }
    }

    /** Kern's slot: coarse state → head (expression/gaze stay as last reported). */
    override fun onAvatarState(state: AvatarState) {
        hologram?.onAvatarEvent(AvatarEvent.StateChanged(state, AvatarExpression.NEUTRAL, GazeTarget.OWNER, android.os.SystemClock.elapsedRealtime()))
    }

    /** Full AvatarContract stream (speech, visemes, gaze, audio level) → the head. */
    override fun onAvatarEvent(event: AvatarEvent) { hologram?.onAvatarEvent(event) }

    /** Cockpit bus events → sequence animations (Kern may also subscribe [sequence] to the bus directly). */
    override fun onAnimationEvent(event: CockpitAnimationEvent) { sequence?.render(event) }

    override fun onCockpitCue(step: at.heinz.deviceai.cockpit.TimelineStep): Boolean =
        if (hologram?.playAvatarEntrance(step) == true) true else sequence?.playCue(step) ?: false

    override val cueGeneration: Long get() = sequence?.cueGeneration ?: -1L

    override fun detach() {
        hologram?.resetAvatarEntrance()
        hologram?.setCockpitSequence(null)
        if (liveSurface == null) hologram?.setForegroundActive(false)
        stage?.let { host?.removeView(it) }
        host = null; stage = null; hologram = null; sequence = null
    }

    val isAttached: Boolean get() = stage?.let { it.parent === host && it.visibility == View.VISIBLE }
        ?: (host != null && hologram?.parent === host)

    companion object {
        /**
         * Painter-only content for JarvisShellRenderer's AVATAR slot (previews, JVM/host renders): the same head and
         * orb renderers as the live views, head left ~62 %, brain orb on its pedestal right. [pose]/[seconds] from caller.
         */
        fun slotContent(pose: () -> AvatarPose, seconds: () -> Float): at.heinz.deviceai.ui.JarvisShellRenderer.SlotContent {
            val head = ContinuumAvatarRenderer()
            val orb = ContinuumHologramRenderer().apply { geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT; showBrand = false }
            return at.heinz.deviceai.ui.JarvisShellRenderer.SlotContent { p, box ->
                val portrait = box.height > box.width * .8f
                val hw = if (portrait) box.width * .66f else box.width * .62f
                p.save(); p.translate(box.left, box.top)
                head.draw(p, hw, box.height, pose(), seconds(), true)
                p.translate(hw - box.width * .04f, box.height * .12f)
                val ow = box.width - hw + box.width * .04f; val oh = box.height * .7f
                p.save(); p.clipRect(0f, 0f, ow, oh)
                orb.layout(ow, oh)
                orb.draw(p, ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.READY, seconds()))
                p.restore(); p.restore()
            }
        }
    }
}
