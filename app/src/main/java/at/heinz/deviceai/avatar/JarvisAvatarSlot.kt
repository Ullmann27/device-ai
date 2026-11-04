package at.heinz.deviceai.avatar

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.JarvisSequenceView
import at.heinz.deviceai.ui.JarvisShellLayout

/**
 * The one Jarvis avatar for Kern's AVATAR slot (#481): holographic wireframe bust + brain orb
 * (existing [ContinuumHologramView] with [ContinuumAvatarRenderer] / ContinuumHologramRenderer) and the sequence
 * overlay ([JarvisSequenceView]: fly-in, data flight, envelope, pills, alarm).
 *
 * TODO(Kern): declare `: JarvisAvatarRenderer` once that interface is pushed. Written against the announced
 * signature attach(host, slot) / onAvatarState(state) / onAnimationEvent(event) / detach(); assumed types:
 * host = ViewGroup, slot = JarvisShellLayout.Box in dp, state = AvatarContract [AvatarEvent].
 */
class JarvisAvatarSlot {
    private var host: ViewGroup? = null
    private var stage: FrameLayout? = null
    var hologram: ContinuumHologramView? = null; private set
    var sequence: JarvisSequenceView? = null; private set

    fun attach(host: ViewGroup, slot: JarvisShellLayout.Box) {
        detach()
        val d = host.resources.displayMetrics.density
        val s = FrameLayout(host.context)
        val holo = ContinuumHologramView(host.context).apply {
            setGeometry(ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)
            setNativeHeadEnabled(true)
        }
        val seq = JarvisSequenceView(host.context)
        s.addView(holo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        s.addView(seq, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        host.addView(s, ViewGroup.MarginLayoutParams((slot.width * d).toInt(), (slot.height * d).toInt()))
        s.x = slot.left * d; s.y = slot.top * d
        this.host = host; stage = s; hologram = holo; sequence = seq
    }

    /** AvatarContract events (state, gaze, speech, visemes, audio level) → the head. */
    fun onAvatarState(event: AvatarEvent) { hologram?.onAvatarEvent(event) }

    /** Cockpit bus events → sequence animations (Kern may also subscribe [sequence] to the bus directly). */
    fun onAnimationEvent(event: CockpitAnimationEvent) { sequence?.render(event) }

    fun detach() {
        hologram?.setNativeHeadEnabled(false)
        sequence?.clear()
        stage?.let { host?.removeView(it) }
        host = null; stage = null; hologram = null; sequence = null
    }

    val isAttached: Boolean get() = stage?.parent != null && stage?.visibility == View.VISIBLE
}
