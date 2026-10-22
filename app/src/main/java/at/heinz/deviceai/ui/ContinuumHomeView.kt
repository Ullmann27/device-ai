package at.heinz.deviceai.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * The Continuum start screen as real Android views: one [ElementView] per layout slot plus the living core
 * ([ContinuumHologramView]) in the hero slot. Positions come from [ContinuumHomeLayout] (measured from the reference
 * images); every element is a focusable, clickable view with a TalkBack description of its real values.
 * The phone navigation is not part of this view: the activity pins it to the bottom edge.
 */
@SuppressLint("ViewConstructor")
class ContinuumHomeView(context: Context, val hologram: ContinuumHologramView, private val onSlot: (Slot, Float, Float) -> Unit) :
    ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val textScale = resources.configuration.fontScale.coerceIn(.85f, 1.3f)
    private var layoutSpec: ContinuumHomeLayout.Layout? = null
    private var offset = 0f
    private val views = linkedMapOf<Slot, ElementView>()
    private val backgroundPainter = AndroidContinuumPainter(context.assets)
    private val sciFiEffects = ContinuumSciFiEffects(density)
    var state: ContinuumDashboardState = ContinuumDashboardState()
        set(value) {
            field = value
            views.values.forEach { it.refresh() }
        }

    init {
        setWillNotDraw(false)
        addView(hologram)
    }

    /**
     * Builds the element views for a window [windowWidthDp] wide. In the wide composition the navigation rail is a
     * separate view on the left; [railOffset] shifts every slot so the remaining slots keep their reference positions.
     */
    fun configure(windowWidthDp: Float, wide: Boolean, railOffset: Float = 0f, includeNavigation: Boolean = false) {
        val spec = if (wide) ContinuumHomeLayout.wide(windowWidthDp) else ContinuumHomeLayout.phone(windowWidthDp)
        if (spec == layoutSpec && offset == railOffset && views.isNotEmpty()) return
        layoutSpec = spec
        offset = railOffset
        views.values.forEach { removeView(it) }
        views.clear()
        hologram.setGeometry(if (spec.wide) ContinuumHologramRenderer.Geometry.FOLD else ContinuumHologramRenderer.Geometry.PHONE)
        for ((slot, element) in ContinuumHomeScreen.elements(spec, textScale)) {
            if (slot == Slot.NAV && !includeNavigation) continue
            if (slot == Slot.RAIL) continue
            val view = ElementView(context, element, slot)
            views[slot] = view
            addView(view)
        }
        requestLayout()
    }

    val wide: Boolean get() = layoutSpec?.wide == true

    /** Height of a slot in dp (for tap zones inside an element). */
    fun slotHeightDp(slot: Slot): Float = layoutSpec?.get(slot)?.height ?: 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val spec = layoutSpec
        val contentHeight = ((spec?.contentHeight ?: 0f) * density).toInt()
        for (child in listOf<View>(hologram) + views.values) {
            val box = boxOf(child) ?: continue
            child.measure(MeasureSpec.makeMeasureSpec((box.width * density).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((box.height * density).toInt(), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(width, maxOf(contentHeight, suggestedMinimumHeight))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (child in listOf<View>(hologram) + views.values) {
            val box = boxOf(child) ?: continue
            child.layout((box.left * density).toInt(), (box.top * density).toInt(),
                (box.right * density).toInt(), (box.bottom * density).toInt())
        }
    }

    private fun boxOf(child: View): ContinuumHomeLayout.Box? {
        val spec = layoutSpec ?: return null
        val box = if (child === hologram) spec[Slot.HERO] else (child as? ElementView)?.slot?.let { spec[it] }
        return box?.let { ContinuumHomeLayout.Box(it.left - offset, it.top, it.right - offset, it.bottom) }
    }

    override fun onDraw(canvas: Canvas) {
        val spec = layoutSpec ?: return
        backgroundPainter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        canvas.translate(-offset, 0f)
        ContinuumHomeScreen.drawBackground(backgroundPainter, width / density + offset, height / density, spec)
        canvas.restoreToCount(saved)
        // Ambient radiance sits behind child cards and their real measurements.
        sciFiEffects.drawAmbient(canvas, width.toFloat(), height.toFloat())
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // No intercepting overlay and no full-screen blur over foreground text.
        sciFiEffects.drawScanlines(canvas, width.toFloat(), height.toFloat())
    }

    /** Selected navigation index (phone bar / rail); redraws the navigation element. */
    fun selectNavigation(index: Int) {
        (views[Slot.NAV]?.element as? BottomNavElement)?.selected = index
        views[Slot.NAV]?.refresh()
    }

    /** A single drawn element; reports taps with the touch position in element dp coordinates. */
    inner class ElementView(context: Context, val element: ContinuumElement, val slot: Slot) : View(context) {
        private val painter = AndroidContinuumPainter(context.assets)
        private var downX = 0f
        private var downY = 0f

        init {
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            refresh()
            setOnClickListener { onSlot(slot, downX / density, downY / density) }
        }

        fun refresh() {
            contentDescription = element.describe(state)
            invalidate()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                downX = event.x; downY = event.y
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            painter.begin(canvas)
            val saved = canvas.save()
            canvas.scale(density, density)
            element.draw(painter, width / density, height / density, state)
            canvas.restoreToCount(saved)
        }
    }
}

/** Standalone drawn element (phone navigation bar, Fold rail) used outside the scrolling start screen. */
@SuppressLint("ViewConstructor")
class ContinuumElementView(context: Context, val element: ContinuumElement, private val onTap: (Float, Float) -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private val painter = AndroidContinuumPainter(context.assets)
    private var tapX = 0f
    private var tapY = 0f
    var state: ContinuumDashboardState = ContinuumDashboardState()
        set(value) { field = value; contentDescription = element.describe(value); invalidate() }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = element.describe(state)
        setOnClickListener { onTap(tapX / density, tapY / density) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) { tapX = event.x; tapY = event.y }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        element.draw(painter, width / density, height / density, state)
        canvas.restoreToCount(saved)
    }
}
