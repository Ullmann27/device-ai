package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Native, independently scrollable conversation controls. The Activity owns every state change. */
internal class ContinuumConversationRailView(
    context: Context,
    private val onNew: () -> Unit,
    private val onSelect: (String) -> Unit,
    private val onNavigate: (String) -> Unit
) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val face = runCatching {
        Typeface.createFromAsset(context.assets, ContinuumFonts.assetPath(FontFamily.TEXT, 500))
    }.getOrDefault(Typeface.DEFAULT)
    private var rendered: List<ContinuumChatHistory.Entry>? = null
    private var selected: String? = null
    private var enabledState = true
    private var statusText: String? = null

    init {
        orientation = VERTICAL
        setPadding(dp(10), dp(14), dp(10), dp(16))
        setBackgroundColor(0xff07111b.toInt())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        tag = "continuum-conversation-rail"
        update(emptyList(), null)
    }

    fun update(entries: List<ContinuumChatHistory.Entry>, active: String?, enabled: Boolean = true,
               status: String? = null) {
        if (rendered == entries && selected == active && enabledState == enabled && statusText == status) return
        val focused = findFocus()?.tag
        rendered = entries.toList(); selected = active; enabledState = enabled; statusText = status
        removeAllViews()
        addView(label("GESPRÄCHE", 13f, 0xff9bb4c8.toInt()).apply {
            setPadding(dp(2), 0, 0, dp(8))
        }, LayoutParams(-1, -2))
        addView(action("+ Neuer Chat", "continuum-new-chat", false, enabled, onNew))
        if (status != null) addView(label(status, 13f, 0xffa4bdcf.toInt()).apply {
            setPadding(dp(3), dp(10), dp(3), dp(8))
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        })
        if (entries.isEmpty() && status == null) {
            addView(label("Noch kein gespeichertes Gespräch.", 13f, 0xff9bb4c8.toInt()).apply {
                setPadding(dp(3), dp(14), dp(3), dp(14))
            })
        } else entries.forEach { entry ->
            val button = action(entry.title, "continuum-chat:${entry.id}", entry.id == active, enabled) {
                onSelect(entry.id)
            }
            val date = entry.updatedAt.takeIf { it > 0L }?.let {
                android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(it))
            }
            button.contentDescription = entry.title + (date?.let { " · $it" } ?: "") +
                if (entry.id == active) " · ausgewähltes Gespräch" else " · Gespräch öffnen"
            addView(button)
        }
        addView(label("ARBEITSBEREICHE", 13f, 0xff9bb4c8.toInt()).apply {
            setPadding(dp(2), dp(20), 0, dp(8))
        })
        addView(action("Projekte und Aufgaben", "continuum-chat-projects", false, true) { onNavigate("tasks") })
        addView(action("Startseite", "continuum-chat-home", false, true) { onNavigate("home") })
        if (focused != null) findViewWithTag<View>(focused)?.requestFocus()
    }

    private fun label(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value; textSize = size; typeface = face; setTextColor(color)
    }

    private fun action(value: String, key: String, active: Boolean, enabled: Boolean,
                       onClick: () -> Unit) = label(value, 13.5f, 0xffe3f2ff.toInt()).apply {
        id = View.generateViewId(); tag = key
        minHeight = dp(48)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(10), dp(11), dp(8), dp(11))
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(if (active) 0xff153442.toInt() else 0xff0b1c29.toInt(), 0xff07131e.toInt())).apply {
            cornerRadius = dp(6).toFloat()
            setStroke(dp(1), if (active) 0xff51cde9.toInt() else 0xff294556.toInt())
        }
        isClickable = true; isFocusable = true; isEnabled = enabled; isSelected = active
        alpha = if (enabled) 1f else .55f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { onClick() }
        ContinuumControls.feedback(this, 6f)
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View,
                info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        }
    }

    private fun dp(value: Int) = (value * density + .5f).toInt()
}
