package at.heinz.deviceai.ui

/** Native cockpit geometry shared by the Activity, drawn elements and review captures. */
object ContinuumHomeLayout {
    enum class Slot {
        BRAND, SEARCH, NOTIFICATIONS, PROFILE, MODEL_PILL, HERO,
        LABEL_CONNECTED, LABEL_LEARNING, LABEL_DEVELOPING, LABEL_THINKING, LABEL_ANALYSING, LABEL_EVOLVING,
        MODEL, LEARNING, STORAGE, DEVELOPMENT, SYSTEM_STATUS, SYSTEM_ACTIVITY, WORKERS, NAV, RAIL,
        COMPOSER, ACTION_MEMORY, ACTION_AGENTS, ACTION_DEVICE, ACTION_BOTS, ACTION_TASK,
        VIEW_AVATAR, VIEW_ORB, VIEW_COMPACT,
        /** Landscape Jarvis cockpit (ContinuumLandscapeCockpitLayout). */
        PROCESSES, LIVE_DIALOG, ACTION_VERIFY
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun scaled(u: Float) = Box(left * u, top * u, right * u, bottom * u)
    }

    /** Coordinates are dp; [scale] is one for the current responsive cockpit. */
    data class Layout(
        val wide: Boolean,
        val width: Float,
        val scale: Float,
        val contentHeight: Float,
        val boxes: Map<Slot, Box>,
        val adaptive: Boolean = false,
        val referenceCockpit: Boolean = false,
        val portrait: Boolean = false,
        /** ref1/ref3 landscape cockpit: one screen, three columns, action bar. */
        val landscapeCockpit: Boolean = false
    ) {
        operator fun get(slot: Slot): Box? = boxes[slot]
    }

    const val PHONE_REFERENCE_WIDTH = 412f
    const val WIDE_REFERENCE_WIDTH = 840f
    const val WIDE_REFERENCE_HEIGHT = 649f
    const val PHONE_NAV_HEIGHT = 74f

    /**
     * Navigation reserves readable dp instead of shrinking every control with the window. Open Fold7 (both
     * orientations): ~20 % labelled rail after ref5 / JARVIS-DESIGN-BILDBESCHRIEB (was 10 %, 88–120 dp).
     */
    fun railWidthDp(windowWidthDp: Float): Float {
        require(windowWidthDp.isFinite() && windowWidthDp > 0f)
        return (windowWidthDp * JarvisMenuRail.WIDTH_FRACTION).coerceIn(JarvisMenuRail.MIN_WIDTH, JarvisMenuRail.MAX_WIDTH)
    }

    /** Height-only changes (IME and system bars) never reset the active work area. */
    fun needsWindowWidthReflow(configuredWidthDp: Float, measuredWidthDp: Float): Boolean {
        if (!configuredWidthDp.isFinite() || !measuredWidthDp.isFinite() ||
            configuredWidthDp <= 0f || measuredWidthDp <= 0f
        ) return false
        return kotlin.math.abs(configuredWidthDp - measuredWidthDp) >= 24f
    }

    fun isWide(widthDp: Float, heightDp: Float) = widthDp >= 600f && heightDp >= 480f

    fun isPortraitFold(widthDp: Float, heightDp: Float): Boolean =
        isWide(widthDp, heightDp) && heightDp >= widthDp * 1.02f

    @JvmOverloads
    fun phone(widthDp: Float, fontScale: Float = 1f): Layout =
        ContinuumReferenceLayout.create(widthDp, 0f, false, fontScale)

    @JvmOverloads
    fun portraitFold(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Layout {
        require(heightDp.isFinite() && heightDp > 0f)
        return ContinuumReferenceLayout.create(widthDp, heightDp, true, fontScale)
    }

    @JvmOverloads
    fun wide(widthDp: Float, heightDp: Float = 0f, fontScale: Float = 1f): Layout =
        ContinuumReferenceLayout.create(widthDp, heightDp, true, fontScale)
}
