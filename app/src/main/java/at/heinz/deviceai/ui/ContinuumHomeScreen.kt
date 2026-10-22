package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import at.heinz.deviceai.ui.ContinuumIcons.Icon

/**
 * Which element fills which layout slot. The Android activity creates one view per slot from this map and the JVM
 * preview draws the same map, so both show the identical composition. The hero slot is the hologram view.
 */
object ContinuumHomeScreen {
    val phoneNavigation = listOf(Icon.HOME to "Start", Icon.CHAT to "Chat", Icon.BOOK to "Lernen", Icon.CODE to "Entwickeln",
        Icon.DOTS_VERTICAL to "Mehr")
    val railNavigation = listOf(Icon.HOME to "Start", Icon.CHAT to "Chat", Icon.BOOK to "Lernen", Icon.CODE to "Entwickeln",
        Icon.CHART_UP to "Analysieren", Icon.DATABASE to "Speicher", Icon.GEAR to "System")

    fun elements(layout: ContinuumHomeLayout.Layout, textScale: Float): Map<Slot, ContinuumElement> {
        val wide = layout.wide
        val map = linkedMapOf<Slot, ContinuumElement>(
            Slot.BRAND to HeaderBrandElement(textScale, large = wide),
            Slot.SEARCH to HeaderButtonElement(HeaderButtonElement.Kind.SEARCH, textScale),
            Slot.NOTIFICATIONS to HeaderButtonElement(HeaderButtonElement.Kind.NOTIFICATIONS, textScale),
            Slot.PROFILE to HeaderButtonElement(HeaderButtonElement.Kind.PROFILE, textScale),
            Slot.LABEL_CONNECTED to CoreLabelElement(Icon.GLOBE, "VERBUNDEN", { it.core.connected }, textScale, wide),
            Slot.LABEL_LEARNING to CoreLabelElement(Icon.BOOK, "LERNEN", { it.core.learning }, textScale, wide),
            Slot.LABEL_DEVELOPING to CoreLabelElement(Icon.CODE, "ENTWICKELN", { it.core.developing }, textScale, wide),
            Slot.LABEL_THINKING to CoreLabelElement(Icon.BRAIN, "DENKEN", { it.core.thinking }, textScale, wide),
            Slot.LABEL_ANALYSING to CoreLabelElement(Icon.CHART_UP, "ANALYSIEREN", { it.core.analysing }, textScale, wide),
            Slot.LABEL_EVOLVING to CoreLabelElement(Icon.ATOM, "EVOLVIEREN", { it.core.evolving }, textScale, wide)
        )
        if (wide) {
            map[Slot.MODEL] = WideModelCardElement(textScale)
            map[Slot.LEARNING] = WideLearningCardElement(textScale)
            map[Slot.STORAGE] = WideStorageCardElement(textScale)
            map[Slot.DEVELOPMENT] = WideDevelopmentCardElement(textScale)
            map[Slot.SYSTEM_STATUS] = SystemStatusElement(textScale)
            map[Slot.SYSTEM_ACTIVITY] = SystemActivityElement(textScale)
            map[Slot.MODEL_PILL] = ModelPillElement(textScale)
            map[Slot.RAIL] = RailElement(railNavigation, 0, textScale)
        } else {
            map[Slot.MODEL] = ModelStatusCardElement(textScale)
            map[Slot.LEARNING] = LearningCardElement(textScale)
            map[Slot.STORAGE] = StorageCardElement(textScale)
            map[Slot.DEVELOPMENT] = DevelopmentCardElement(textScale)
            map[Slot.NAV] = BottomNavElement(phoneNavigation, 0, textScale)
        }
        return map
    }

    /** Page background: deep navy with the blue haze behind the core and faint star dust, as in the references. */
    fun drawBackground(p: ContinuumPainter, width: Float, height: Float, layout: ContinuumHomeLayout.Layout) {
        p.fillRect(0f, 0f, width, height, Fill.Linear(0f, 0f, 0f, height,
            intArrayOf(Argb.rgb(3, 14, 36), ContinuumTheme.BACKGROUND, Argb.rgb(0, 8, 24))))
        val hero = layout[Slot.HERO] ?: return
        val cx = (hero.left + hero.right) / 2f
        val cy = hero.top + hero.height * .45f
        val radius = hero.width * .75f
        p.fillRect(0f, 0f, width, hero.bottom + 60f * layout.scale, Fill.Radial(cx, cy, radius,
            intArrayOf(Argb.of(110, 16, 54, 140), Argb.of(40, 10, 34, 96), 0), floatArrayOf(0f, .5f, 1f)))
        for (i in 0 until 90) {
            val x = ((i * 0.618034f + .21f) % 1f) * width
            val y = ((i * 0.754877f + .37f) % 1f) * height
            p.fillCircle(x, y, if (i % 7 == 0) .7f else .4f, Fill.Solid(Argb.rgb(120, 170, 255)), if (i % 3 == 0) .35f else .18f)
        }
    }
}
