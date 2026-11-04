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
 * TODO(Kern #481): add `: at.heinz.deviceai.cockpit.JarvisAvatarRenderer` and `override` once the interface is on
 * origin; methods already match its exact signature (attach(ViewGroup, Rect px) / onAvatarState(AvatarState) /
 * onAnimationEvent / detach). Register via `JarvisAvatarSlot.register(JarvisHoloAvatar())`.
 * Speech/visemes/gaze/audio level need the full AvatarContract stream: feed [onAvatarEvent].
 */
class JarvisHoloAvatar {
    private var host: ViewGroup? = null
    private var stage: FrameLayout? = null
    var hologram: ContinuumHologramView? = null; private set
    var sequence: JarvisSequenceView? = null; private set

    fun attach(host: ViewGroup, slot: Rect) {
        detach()
        val s = FrameLayout(host.context)
        val holo = ContinuumHologramView(host.context).apply {
            setGeometry(ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)
            setNativeHeadEnabled(true)
        }
        val seq = JarvisSequenceView(host.context)
        s.addView(holo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        s.addView(seq, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.addView(s, ViewGroup.MarginLayoutParams(slot.width(), slot.height()))
        s.x = slot.left.toFloat(); s.y = slot.top.toFloat()
        this.host = host; stage = s; hologram = holo; sequence = seq
    }

    /** Kern's slot: coarse state → head (expression/gaze stay as last reported). */
    fun onAvatarState(state: AvatarState) {
        hologram?.onAvatarEvent(AvatarEvent.StateChanged(state, AvatarExpression.NEUTRAL, GazeTarget.OWNER, android.os.SystemClock.uptimeMillis()))
    }

    /** Full AvatarContract stream (speech, visemes, gaze, audio level) → the head. */
    fun onAvatarEvent(event: AvatarEvent) { hologram?.onAvatarEvent(event) }

    /** Cockpit bus events → sequence animations (Kern may also subscribe [sequence] to the bus directly). */
    fun onAnimationEvent(event: CockpitAnimationEvent) { sequence?.render(event) }

    fun detach() {
        hologram?.setNativeHeadEnabled(false)
        sequence?.clear()
        stage?.let { host?.removeView(it) }
        host = null; stage = null; hologram = null; sequence = null
    }

    val isAttached: Boolean get() = stage?.parent != null && stage?.visibility == View.VISIBLE

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
