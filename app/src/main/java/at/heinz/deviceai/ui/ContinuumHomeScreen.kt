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
        if (layout.landscapeCockpit) return landscapeCockpitElements(layout, textScale)
        if (layout.referenceCockpit) return referenceElements(layout, textScale)
        if (layout.adaptive) return adaptiveElements(layout, textScale)
        val wide = layout.wide
        val portrait = wide && layout[Slot.WORKERS] != null
        val map = linkedMapOf<Slot, ContinuumElement>(
            Slot.BRAND to HeaderBrandElement(textScale, large = wide),
            Slot.SEARCH to HeaderButtonElement(HeaderButtonElement.Kind.SEARCH, textScale),
            Slot.NOTIFICATIONS to HeaderButtonElement(HeaderButtonElement.Kind.NOTIFICATIONS, textScale),
            Slot.PROFILE to HeaderButtonElement(HeaderButtonElement.Kind.PROFILE, textScale),
            Slot.LABEL_CONNECTED to CoreLabelElement(Icon.GLOBE, "VERBUNDEN", { it.core.connected }, textScale, wide, portrait = portrait),
            Slot.LABEL_LEARNING to CoreLabelElement(Icon.BOOK, "LERNEN", { it.core.learning }, textScale, wide, portrait = portrait),
            Slot.LABEL_DEVELOPING to CoreLabelElement(Icon.CODE, "ENTWICKELN", { it.core.developing }, textScale, wide, portrait = portrait),
            Slot.LABEL_THINKING to CoreLabelElement(Icon.BRAIN, "DENKEN", { it.core.thinking }, textScale, wide, mirrored = true, portrait = portrait),
            Slot.LABEL_ANALYSING to CoreLabelElement(Icon.CHART_UP, "ANALYSIEREN", { it.core.analysing }, textScale, wide, mirrored = true, portrait = portrait),
            Slot.LABEL_EVOLVING to CoreLabelElement(Icon.ATOM, "EVOLVIEREN", { it.core.evolving }, textScale, wide, mirrored = true, portrait = portrait)
        )
        if (portrait) {
            map[Slot.MODEL] = PortraitModelCard(textScale)
            map[Slot.LEARNING] = PortraitLearningCard(textScale)
            map[Slot.STORAGE] = PortraitStorageCard(textScale)
            map[Slot.DEVELOPMENT] = PortraitDevelopmentCard(textScale)
            map[Slot.SYSTEM_STATUS] = PortraitSystemStatusStrip(textScale)
            map[Slot.SYSTEM_ACTIVITY] = PortraitActivityCard(textScale)
            map[Slot.WORKERS] = PortraitWorkersCard(textScale)
            // The portrait reference has NO legacy left rail, top model pill or phone bar.
        } else if (wide) {
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

    private fun referenceElements(layout: ContinuumHomeLayout.Layout, textScale: Float): Map<Slot, ContinuumElement> {
        val map = linkedMapOf<Slot, ContinuumElement>(
            Slot.BRAND to ReferenceBrandElement(textScale),
            Slot.SEARCH to HeaderButtonElement(HeaderButtonElement.Kind.SEARCH, textScale),
            Slot.NOTIFICATIONS to HeaderButtonElement(HeaderButtonElement.Kind.NOTIFICATIONS, textScale),
            Slot.PROFILE to HeaderButtonElement(HeaderButtonElement.Kind.PROFILE, textScale),
            Slot.COMPOSER to ReferenceComposerElement(textScale)
        )
        for (slot in listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT))
            map[slot] = ReferenceViewModeElement(slot, textScale)
        for (slot in ContinuumReferenceLayout.actions) map[slot] = ReferenceActionElement(slot, textScale)
        for (slot in listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT,
            Slot.WORKERS, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)) {
            if (layout[slot] != null) map[slot] = ReferenceMetricCard(slot, textScale)
        }
        map[Slot.LABEL_EVOLVING] = ReferenceLearningStatusElement(textScale)
        if (layout[Slot.RAIL] != null) map[Slot.RAIL] = RailElement(railNavigation, 0, textScale)
        if (layout[Slot.NAV] != null) map[Slot.NAV] = BottomNavElement(phoneNavigation, 0, textScale)
        return map
    }

    /** ref1/ref3 landscape cockpit; HERO stays the avatar slot (live hologram / Jarvis Avatar via avatar contract). */
    private fun landscapeCockpitElements(layout: ContinuumHomeLayout.Layout, textScale: Float): Map<Slot, ContinuumElement> {
        val map = linkedMapOf<Slot, ContinuumElement>(
            Slot.BRAND to CockpitBrandElement(textScale),
            Slot.SEARCH to HeaderButtonElement(HeaderButtonElement.Kind.SEARCH, textScale),
            Slot.NOTIFICATIONS to HeaderButtonElement(HeaderButtonElement.Kind.NOTIFICATIONS, textScale),
            Slot.PROFILE to HeaderButtonElement(HeaderButtonElement.Kind.PROFILE, textScale),
            Slot.MODEL to CockpitBrainCard(textScale),
            Slot.PROCESSES to CockpitProcessesCard(textScale),
            Slot.LIVE_DIALOG to CockpitDialogCard(textScale),
            Slot.SYSTEM_STATUS to CockpitSystemCard(textScale),
            Slot.WORKERS to CockpitAgentsCard(textScale),
            Slot.COMPOSER to CockpitComposerElement(textScale)
        )
        for (slot in ContinuumLandscapeCockpitLayout.topTabs) map[slot] = CockpitTabElement(slot, textScale)
        for (slot in ContinuumLandscapeCockpitLayout.actionBar) map[slot] = CockpitActionElement(slot, textScale)
        for (slot in listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT)) map[slot] = ReferenceViewModeElement(slot, textScale)
        if (layout[Slot.RAIL] != null) map[Slot.RAIL] = RailElement(railNavigation, 0, textScale)
        return map
    }

    private fun adaptiveElements(layout: ContinuumHomeLayout.Layout, textScale: Float): Map<Slot, ContinuumElement> {
        val map = linkedMapOf<Slot, ContinuumElement>(
            Slot.BRAND to AdaptiveHeaderBrandElement(textScale),
            Slot.SEARCH to HeaderButtonElement(HeaderButtonElement.Kind.SEARCH, textScale),
            Slot.NOTIFICATIONS to HeaderButtonElement(HeaderButtonElement.Kind.NOTIFICATIONS, textScale),
            Slot.PROFILE to HeaderButtonElement(HeaderButtonElement.Kind.PROFILE, textScale),
            Slot.LABEL_THINKING to CockpitActionTile(Icon.CHAT, "Chat", { it.core.thinking }, textScale),
            Slot.LABEL_ANALYSING to CockpitActionTile(Icon.GLOBE, "Forschung", { it.core.analysing }, textScale),
            Slot.LABEL_LEARNING to CockpitActionTile(Icon.BOOK, "Lernen", { it.core.learning }, textScale),
            Slot.LABEL_DEVELOPING to CockpitActionTile(Icon.CODE, "Entwicklung", { it.core.developing }, textScale),
            Slot.LABEL_CONNECTED to CockpitActionTile(Icon.WIFI, "Verbindung", { it.core.connected }, textScale),
            Slot.LABEL_EVOLVING to CockpitActionTile(Icon.ATOM, "Lernstatus", { it.core.evolving }, textScale)
        )
        for (slot in listOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT,
            Slot.WORKERS, Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)) {
            if (layout[slot] != null) map[slot] = CockpitMetricCard(slot, textScale, layout.wide)
        }
        if (layout[Slot.RAIL] != null) map[Slot.RAIL] = RailElement(railNavigation, 0, textScale)
        if (layout[Slot.NAV] != null) map[Slot.NAV] = BottomNavElement(phoneNavigation, 0, textScale)
        return map
    }

    /** Page background: deep navy with the blue haze behind the core and faint star dust, as in the references. */
    fun drawBackground(p: ContinuumPainter, width: Float, height: Float, layout: ContinuumHomeLayout.Layout) {
        p.fillRect(0f, 0f, width, height, Fill.Linear(0f, 0f, 0f, height,
            intArrayOf(Argb.rgb(2, 7, 20), ContinuumTheme.BACKGROUND, Argb.rgb(1, 5, 17))))
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
