package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Small drawers with real state. */
internal class JarvisLiveTabs(
    context: Context,
    private val clock: JarvisShellView,
    private val navigate: (String) -> Unit
) : ViewGroup(context) {
    private val d = resources.displayMetrics.density
    private val keys = listOf("system", "memory", "agents")
    private val labels = listOf("System", "Gedächtnis", "Assistenten")
    private val tabs = keys.mapIndexed { i, key -> Tab(context, key, labels[i]) }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(14f), px(8f), px(14f), px(12f))
    }
    private val heading = TextView(context).apply {
        setTextColor(JarvisDesign.Colors.text)
        textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        maxLines = 2
    }
    private val body = TextView(context).apply {
        tag = "jarvis-live-panel:values"
        textSize = 12f
        setTextColor(JarvisDesign.Colors.textSecondary)
        setLineSpacing(4f * d, 1f)
        setPadding(0, px(10f), 0, px(12f))
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val close = button("Schließen", "close") { dismiss() }
    private val details = button("Details öffnen", "open") {
        val key = shownKey ?: return@button
        collapseImmediately()
        navigate(key)
    }
    private val appearance = button("Darstellung", "appearance") {
        collapseImmediately()
        navigate("avatar-appearance")
    }
    private val panel = ScrollView(context).apply {
        tag = "jarvis-live-panel"
        isFillViewport = false
        isVerticalScrollBarEnabled = false
        clipToOutline = true
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xfa01172f.toInt(), 0xfa080a24.toInt())).apply {
            cornerRadius = 14f * d
            setStroke(px(1f).coerceAtLeast(1), JarvisDesign.Colors.cyan)
        }
        elevation = 8f * d
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private var state = ContinuumDashboardState()
    private var shell: JarvisShellLayout.Shell? = null
    private var shownKey: String? = null
    private var expanded = false
    private var progress = 0f
    private var from = 0f
    private var transitionAt = 0L
    private var pulseUntil = 0L
    private var subscription: AutoCloseable? = null

    init {
        tag = "jarvis-live-tabs"
        clipChildren = true
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(heading, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(px(84f), px(48f)))
        }
        content.addView(titleRow)
        content.addView(body, LinearLayout.LayoutParams(-1, -2))
        content.addView(details, LinearLayout.LayoutParams(-1, px(48f)))
        content.addView(appearance, LinearLayout.LayoutParams(-1, px(48f)))
        tabs.forEach(::addView)
        addView(panel)
    }

    fun setGeometry(value: JarvisShellLayout.Shell) {
        // Measurement may probe several widths.
        shell = value
    }

    fun updateState(value: ContinuumDashboardState) {
        val previous = shownKey?.let { values(it, state) }
        val stopped = state.presentation.processing && !value.presentation.processing
        state = value
        renderValues()
        if (stopped) pulseUntil = 0L
        else if (shownKey != null && previous != values(shownKey!!, value)) {
            pulseUntil = SystemClock.uptimeMillis() + 700L
            startClock()
        }
        tabs.forEach { it.invalidate() }
    }

    private fun renderValues() {
        val key = shownKey ?: return
        heading.text = labels[keys.indexOf(key)]
        body.text = values(key, state).joinToString("\n") { "${it.first}  ${it.second}" }
        appearance.visibility = if (key == "system") VISIBLE else GONE
        panel.contentDescription = "Live-${heading.text}"
    }

    private fun toggle(key: String) {
        if (expanded && shownKey == key) { dismiss(); return }
        shownKey = key
        expanded = true
        from = progress
        transitionAt = SystemClock.uptimeMillis()
        panel.visibility = VISIBLE
        panel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        panel.scrollTo(0, 0)
        renderValues()
        tabs.forEach { it.isSelected = it.key == key; it.invalidate() }
        requestLayout()
        startClock()
    }

    private fun dismiss() {
        expanded = false
        from = progress
        transitionAt = SystemClock.uptimeMillis()
        panel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        panel.isEnabled = false
        content.isEnabled = false
        listOf(close, details, appearance).forEach { it.isEnabled = false }
        startClock()
    }

    fun collapseImmediately() {
        expanded = false
        progress = 0f
        shownKey = null
        pulseUntil = 0L
        panel.visibility = GONE
        panel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        subscription?.close()
        subscription = null
        tabs.forEach { it.isSelected = false; it.invalidate() }
    }

    private fun startClock() {
        if (subscription == null) subscription = clock.observeMotionFrames(::frame)
        clock.invalidate()
    }

    private fun frame(now: Long, scale: Float): Boolean {
        val target = if (expanded) 1f else 0f
        val fraction = if (scale <= 0f) 1f else
            ((now - transitionAt) / (280f * scale)).coerceIn(0f, 1f)
        progress = from + (target - from) * JarvisMotion.Easing.easeOutCubic(fraction)
        if (scale <= 0f) pulseUntil = 0L
        panel.translationX = -(panel.width + px(8f)) * (1f - progress)
        panel.alpha = progress
        if (expanded) {
            panel.isEnabled = true
            content.isEnabled = true
            listOf(close, details, appearance).forEach { it.isEnabled = true }
        }
        tabs.forEach { it.invalidate() }
        if (!expanded && fraction >= 1f) collapseImmediately()
        val moving = fraction < 1f || now < pulseUntil
        if (!moving) { subscription?.close(); subscription = null }
        return moving
    }

    override fun onDetachedFromWindow() {
        collapseImmediately()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        tabs.forEach { it.measure(exact(48f), exact(56f)) }
        val panelWidth = minOf(336f * d, width - 48f * d).toInt().coerceAtLeast(px(176f))
        val stage = shell?.get(JarvisShellLayout.Slot.AVATAR)
        val panelHeight = minOf(((stage?.height ?: 360f) - 16f).coerceAtLeast(48f),
            400f * resources.configuration.fontScale)
        panel.measure(MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(px(panelHeight), MeasureSpec.AT_MOST))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val stage = shell?.get(JarvisShellLayout.Slot.AVATAR)
        val top = px((stage?.top ?: 72f) + 8f)
        tabs.forEachIndexed { i, view ->
            val y = top + px(i * 60f)
            view.layout(0, y, px(48f), y + px(56f))
        }
        val panelTop = minOf(top + px(keys.indexOf(shownKey).coerceAtLeast(0) * 60f),
            px(stage?.bottom ?: (height / d)) - panel.measuredHeight).coerceAtLeast(top)
        panel.layout(px(36f), panelTop, px(36f) + panel.measuredWidth, panelTop + panel.measuredHeight)
        panel.translationX = -(panel.width + px(8f)) * (1f - progress)
        panel.alpha = progress
    }

    private fun button(label: String, id: String, action: () -> Unit) = Button(context).apply {
        tag = "jarvis-live-panel:$id"
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(JarvisDesign.Colors.cyanSoft)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        minWidth = 0
        minimumWidth = 0
        setPadding(px(4f), 0, px(4f), 0)
        setOnClickListener { action() }
    }

    private inner class Tab(context: Context, val key: String, label: String) : View(context) {
        private val painter = AndroidContinuumPainter(context.assets)
        init {
            tag = "jarvis-live-tab:$key"
            contentDescription = "$label: Live-Werte anzeigen"
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { toggle(key) }
        }
        override fun onDraw(canvas: Canvas) {
            painter.begin(canvas)
            val save = canvas.save()
            canvas.scale(d, d)
            val color = if (key == "memory") JarvisDesign.Colors.violetRim else JarvisDesign.Colors.cyan
            val box = JarvisShellLayout.Box(3f, 7f, 25f, 49f)
            JarvisReferenceHud.surface(painter, box, 8f, color, JarvisDesign.Colors.cyanDeep)
            if (isSelected || SystemClock.uptimeMillis() < pulseUntil)
                JarvisNeon.halo(painter, 14f, 28f, 18f, color, .18f)
            JarvisRailIcons.draw(painter, key, 6.5f, 20.5f, 15f, color)
            painter.drawLine(24f, 22f, 27f, 28f, .9f, Fill.Solid(color))
            painter.drawLine(27f, 28f, 24f, 34f, .9f, Fill.Solid(color))
            canvas.restoreToCount(save)
        }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = Button::class.java.name
            info.isCheckable = true
            info.isChecked = isSelected
        }
    }

    private fun px(dp: Float) = (dp * d + .5f).toInt()
    private fun exact(dp: Float) = MeasureSpec.makeMeasureSpec(px(dp), MeasureSpec.EXACTLY)

    internal companion object {
        fun values(key: String, s: ContinuumDashboardState): List<Pair<String, String>> {
            fun percent(v: Int?) = v?.takeIf { it in 0..100 }?.let { "$it %" } ?: "—"
            return when (key) {
                "system" -> listOf("App-CPU" to percent(s.system.appCpuPercent),
                    "Geräte-RAM" to percent(s.system.memoryPercent),
                    "Akku-Temperatur" to (s.system.temperatureC?.takeIf(Float::isFinite)
                        ?.let { String.format(Locale.GERMANY, "%.1f °C", it) } ?: "—"),
                    "Modell" to s.model.stateLabel)
                "memory" -> (s.memoryActivity ?: MemoryActivitySnapshot.from(s.memory, null))
                    .rows().map { it.second to it.first }
                "agents" -> listOf("Aktive Assistenten" to (s.cockpit.workers?.count { it.running }?.toString() ?: "—"),
                    "Kapazität" to (s.cockpit.workerCapacity?.toString() ?: "—"),
                    "Ablauf" to ContinuumCockpitPresentation.phaseLabel(s.cockpit.turnPhase))
                else -> emptyList()
            }
        }
    }
}
