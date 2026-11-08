package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

internal object ContinuumLandscapeTargetPrecisionChecks {
    fun strictRegression() {
        val layout = ContinuumHomeLayout.wide(832f, 700f, 1f)
        for (slot in listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT)) {
            val box = requireNotNull(layout[slot])
            check(box.width >= 48f && box.height >= 48f) { "$slot: $box" }
        }
    }

    fun legacyMatrix() {
        val windows = listOf(840f to 649f, 832f to 700f, 960f to 600f, 1280f to 800f, 1440f to 900f)
        for ((width, height) in windows) for (font in listOf(1f, 1.3f)) {
            val layout = ContinuumHomeLayout.wide(width, height, font)
            check(layout.landscapeCockpit && layout.contentHeight == height)
            val rail = requireNotNull(layout[Slot.RAIL])
            val named = ContinuumLandscapeCockpitLayout.topTabs + ContinuumLandscapeCockpitLayout.actionBar +
                ContinuumLandscapeCockpitLayout.cards + listOf(Slot.BRAND, Slot.SEARCH, Slot.NOTIFICATIONS,
                Slot.PROFILE, Slot.COMPOSER, Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT, Slot.HERO)
            val boxes = named.map { it to requireNotNull(layout[it]) }
            for ((slot, box) in boxes) {
                check(box.width >= 48f && box.height >= 48f) { "$width/$height/$font $slot: $box" }
                check(box.left >= rail.right - .01f && box.right <= width + .01f &&
                    box.top >= 0f && box.bottom <= height + .01f)
            }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i].second; val b = boxes[j].second
                check(!(a.left < b.right - .01f && b.left < a.right - .01f &&
                    a.top < b.bottom - .01f && b.top < a.bottom - .01f))
            }
        }
    }

    fun unchangedHeroBounds() {
        val windows = listOf(
            floatArrayOf(840f, 649f, 1f), floatArrayOf(840f, 649f, 1.3f),
            floatArrayOf(832f, 700f, 1f), floatArrayOf(832f, 700f, 1.3f),
            floatArrayOf(960f, 600f, 1f), floatArrayOf(960f, 600f, 1.3f),
            floatArrayOf(1280f, 800f, 1f), floatArrayOf(1280f, 800f, 1.3f),
            floatArrayOf(1440f, 900f, 1f), floatArrayOf(1440f, 900f, 1.3f)
        )
        for ((width, height, font) in windows) {
            val layout = ContinuumHomeLayout.wide(width, height, font)
            val hero = requireNotNull(layout[Slot.HERO])
            val model = requireNotNull(layout[Slot.MODEL])
            val system = requireNotNull(layout[Slot.SYSTEM_STATUS])
            val pill = requireNotNull(layout[Slot.VIEW_AVATAR])
            val composer = requireNotNull(layout[Slot.COMPOSER])
            val rail = requireNotNull(layout[Slot.RAIL])
            val gap = JarvisDesign.Metrics.gap
            // Independent neighbours catch misplaced or overlapping avatar bounds.
            val actualGaps = floatArrayOf(
                hero.left - model.right, system.left - hero.right,
                hero.top - pill.bottom, composer.top - hero.bottom
            )
            val requiredGaps = floatArrayOf(gap, gap, 6f, gap)
            check(actualGaps.indices.all {
                kotlin.math.abs(actualGaps[it] - requiredGaps[it]) <= .02f
            }) { "$width/$height/$font: displaced HERO $hero; gaps=${actualGaps.toList()}" }
            check(hero.left >= rail.right && hero.right <= width &&
                hero.top >= 0f && hero.bottom <= height &&
                hero.width >= 48f && hero.height >= 48f) {
                "$width/$height/$font: avatar not fully reachable"
            }
            val replay = requireNotNull(ContinuumHomeLayout.wide(width, height, font)[Slot.HERO])
            val bits = floatArrayOf(hero.left, hero.top, hero.right, hero.bottom)
            val replayBits = floatArrayOf(replay.left, replay.top, replay.right, replay.bottom)
            check(bits.indices.all { bits[it].toRawBits() == replayBits[it].toRawBits() }) {
                "Repeated layout must produce identical avatar pixels"
            }
        }
    }
}
