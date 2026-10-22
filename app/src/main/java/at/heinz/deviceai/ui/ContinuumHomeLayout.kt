package at.heinz.deviceai.ui

/**
 * Positions of every start-screen element, measured from the owner's reference images.
 *
 * Phone reference: 412 dp wide (reference-phone.png, 713 px screen, 1.7306 px/dp), origin below the status bar.
 * Wide/Fold reference: 840 dp wide (reference-fold.png, 1294 px screen, 1.54 px/dp), origin at the screen top.
 * Other window sizes scale the whole composition uniformly with the width, so proportions never change.
 */
object ContinuumHomeLayout {
    enum class Slot {
        BRAND, SEARCH, NOTIFICATIONS, PROFILE, MODEL_PILL, HERO,
        LABEL_CONNECTED, LABEL_LEARNING, LABEL_DEVELOPING, LABEL_THINKING, LABEL_ANALYSING, LABEL_EVOLVING,
        MODEL, LEARNING, STORAGE, DEVELOPMENT, SYSTEM_STATUS, SYSTEM_ACTIVITY, NAV, RAIL
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun scaled(u: Float) = Box(left * u, top * u, right * u, bottom * u)
    }

    /** [scale] maps reference dp to window dp; [contentHeight] is the scrolling part above a phone navigation bar. */
    data class Layout(val wide: Boolean, val width: Float, val scale: Float, val contentHeight: Float, val boxes: Map<Slot, Box>) {
        operator fun get(slot: Slot): Box? = boxes[slot]
    }

    const val PHONE_REFERENCE_WIDTH = 412f
    const val WIDE_REFERENCE_WIDTH = 840f
    const val PHONE_NAV_HEIGHT = 74f

    /** Wide layout from 700 dp width and 480 dp height (unfolded Fold, tablets, landscape); phone otherwise. */
    fun isWide(widthDp: Float, heightDp: Float) = widthDp >= 700f && heightDp >= 480f

    fun forWindow(widthDp: Float, heightDp: Float): Layout =
        if (isWide(widthDp, heightDp)) wide(widthDp) else phone(widthDp)

    private val phoneReference = mapOf(
        Slot.BRAND to Box(0f, 0f, 276f, 68.8f),
        Slot.SEARCH to Box(279f, 22f, 311f, 54f),
        Slot.NOTIFICATIONS to Box(322f, 22f, 354f, 54f),
        Slot.PROFILE to Box(364f, 22f, 396f, 54f),
        Slot.HERO to Box(0f, 68.8f, 412f, 375.6f),
        Slot.LABEL_CONNECTED to Box(10.8f, 96f, 150f, 126f),
        Slot.LABEL_LEARNING to Box(10.8f, 165.4f, 150f, 195.4f),
        Slot.LABEL_DEVELOPING to Box(10.8f, 242.2f, 150f, 272.2f),
        Slot.LABEL_THINKING to Box(321.1f, 96f, 412f, 126f),
        Slot.LABEL_ANALYSING to Box(321.1f, 165.4f, 412f, 195.4f),
        Slot.LABEL_EVOLVING to Box(321.1f, 242.2f, 412f, 272.2f),
        Slot.MODEL to Box(13.3f, 380.3f, 398.8f, 469.4f),
        Slot.LEARNING to Box(13.3f, 479.6f, 215.5f, 661.6f),
        Slot.STORAGE to Box(224.8f, 479.6f, 398.8f, 661.6f),
        Slot.DEVELOPMENT to Box(13.3f, 673.2f, 398.8f, 773.2f),
        Slot.NAV to Box(0f, 787f, 412f, 787f + PHONE_NAV_HEIGHT)
    )

    private val wideReference = mapOf(
        Slot.RAIL to Box(0f, 0f, 76f, 649f),
        Slot.BRAND to Box(80f, 30f, 420f, 86f),
        Slot.SEARCH to Box(579.9f, 42.2f, 608.5f, 70.8f),
        Slot.NOTIFICATIONS to Box(616.9f, 42.2f, 645.5f, 70.8f),
        Slot.PROFILE to Box(657.7f, 42.2f, 686.3f, 70.8f),
        Slot.MODEL_PILL to Box(709f, 30.5f, 826f, 72.7f),
        Slot.HERO to Box(212f, 0f, 622f, 291f),
        Slot.LABEL_CONNECTED to Box(90f, 97f, 212f, 138f),
        Slot.LABEL_LEARNING to Box(90f, 148.5f, 212f, 192f),
        Slot.LABEL_DEVELOPING to Box(90f, 201.3f, 212f, 247f),
        Slot.LABEL_THINKING to Box(550f, 97.4f, 647.4f, 137.7f),
        Slot.LABEL_ANALYSING to Box(550f, 151.3f, 647.4f, 191.6f),
        Slot.LABEL_EVOLVING to Box(550f, 204.5f, 647.4f, 243.5f),
        Slot.SYSTEM_STATUS to Box(657.1f, 84.4f, 829.2f, 276f),
        Slot.MODEL to Box(87.7f, 290.9f, 335.7f, 475.3f),
        Slot.LEARNING to Box(345.5f, 290.9f, 613f, 475.3f),
        Slot.STORAGE to Box(624.7f, 290.9f, 829.2f, 475.3f),
        Slot.DEVELOPMENT to Box(87.7f, 485.7f, 492.9f, 642.9f),
        Slot.SYSTEM_ACTIVITY to Box(504.5f, 485.7f, 829.2f, 642.9f)
    )

    fun phone(widthDp: Float): Layout {
        require(widthDp > 0f)
        val u = widthDp / PHONE_REFERENCE_WIDTH
        return Layout(false, widthDp, u, 787f * u, phoneReference.mapValues { it.value.scaled(u) })
    }

    fun wide(widthDp: Float): Layout {
        require(widthDp > 0f)
        val u = widthDp / WIDE_REFERENCE_WIDTH
        return Layout(true, widthDp, u, 649f * u, wideReference.mapValues { it.value.scaled(u) })
    }
}
