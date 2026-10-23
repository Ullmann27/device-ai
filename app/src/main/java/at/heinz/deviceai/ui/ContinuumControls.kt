package at.heinz.deviceai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/** Shared native control states. No listeners, data access or perpetual animations: the owning screen keeps its actions. */
object ContinuumControls {
    const val CYAN = 0xFF62C8FF.toInt()
    const val VIOLET = 0xFFAB8BFF.toInt()
    const val WARNING = 0xFFFF8AAE.toInt()
    const val TEXT = 0xFFF1F5FF.toInt()
    const val MUTED = 0xFFB1BFDC.toInt()

    private fun dp(ctx: Context, n: Float) = n * ctx.resources.displayMetrics.density

    private fun shape(ctx: Context, radius: Float, top: Int, bottom: Int, edge: Int, stroke: Float = 1f) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
            cornerRadius = dp(ctx, radius)
            setStroke(dp(ctx, stroke).toInt().coerceAtLeast(1), edge)
        }

    fun surface(ctx: Context, radius: Float = 18f): Drawable =
        shape(ctx, radius, 0xFF132B48.toInt(), 0xFF081326.toInt(), 0xFF365A82.toInt())

    fun buttonBackground(ctx: Context, accent: Int = CYAN, primary: Boolean = false): Drawable {
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(ctx, 15f,
                0xFF182235.toInt(), 0xFF111A2B.toInt(), 0xFF384256.toInt()))
            addState(intArrayOf(android.R.attr.state_pressed), shape(ctx, 15f,
                0xFF254B6D.toInt(), 0xFF142B4A.toInt(), accent, 2f))
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, 15f,
                0xFF203F62.toInt(), 0xFF12243E.toInt(), accent, 2f))
            addState(intArrayOf(android.R.attr.state_selected), shape(ctx, 15f,
                0xFF184469.toInt(), 0xFF142D50.toInt(), accent, 2f))
            addState(intArrayOf(android.R.attr.state_hovered), shape(ctx, 15f,
                0xFF203B59.toInt(), 0xFF11233B.toInt(), accent))
            addState(intArrayOf(), shape(ctx, 15f,
                if (primary) 0xFF17517A.toInt() else 0xFF182E4B.toInt(),
                if (primary) 0xFF162D56.toInt() else 0xFF0E1D34.toInt(),
                if (primary) accent else 0xFF52698F.toInt(), if (primary) 1.5f else 1f))
        }
        return RippleDrawable(ColorStateList.valueOf(Argb.withAlpha(accent, .22f)), states,
            shape(ctx, 15f, Color.WHITE, Color.WHITE, Color.WHITE))
    }

    fun button(view: TextView, primary: Boolean = false, critical: Boolean = false) {
        val accent = if (critical) WARNING else if (primary) CYAN else VIOLET
        view.background = buttonBackground(view.context, accent, primary)
        view.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(0xFF91A0B6.toInt(), TEXT)))
        view.minimumHeight = dp(view.context, 48f).toInt()
        view.minimumWidth = dp(view.context, 48f).toInt()
        view.isFocusable = true
        view.isClickable = true
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Button"
            }
        }
    }

    /** Foreground feedback leaves the measured/custom-drawn card, its content and its hit testing intact. */
    fun feedback(view: View, radius: Float = 18f) {
        val ctx = view.context
        val outline = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(ctx, radius, 0, 0, 0))
            addState(intArrayOf(android.R.attr.state_focused), shape(ctx, radius, 0x103BC5FE, 0, CYAN, 2f))
            addState(intArrayOf(android.R.attr.state_hovered), shape(ctx, radius, 0x123BC5FE, 0, 0xAA62C8FF.toInt()))
            addState(intArrayOf(), shape(ctx, radius, 0, 0, 0))
        }
        view.foreground = RippleDrawable(ColorStateList.valueOf(0x285EDAFF), outline,
            shape(ctx, radius, Color.WHITE, Color.WHITE, Color.WHITE))
    }
}
