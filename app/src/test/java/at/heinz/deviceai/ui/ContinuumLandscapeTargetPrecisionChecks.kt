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
        // Frozen from the C1 baseline.
        val baselines = listOf(
            floatArrayOf(840.0f, 649.0f, 1.0f, 326.4396f, 105.680534f, 637.29816f, 559.0f),
            floatArrayOf(840.0f, 649.0f, 1.3f, 326.4145f, 137.2f, 637.23035f, 559.0f),
            floatArrayOf(832.0f, 700.0f, 1.0f, 323.35333f, 105.48709f, 631.80774f, 610.0f),
            floatArrayOf(832.0f, 700.0f, 1.3f, 323.33148f, 137.2f, 631.74866f, 610.0f),
            floatArrayOf(960.0f, 600.0f, 1.0f, 372.72235f, 109.078125f, 719.86426f, 510.0f),
            floatArrayOf(960.0f, 600.0f, 1.3f, 372.65543f, 137.2f, 719.6834f, 510.0f),
            floatArrayOf(1280.0f, 800.0f, 1.0f, 476.04144f, 127.4375f, 963.7417f, 710.0f),
            floatArrayOf(1280.0f, 800.0f, 1.3f, 476.04144f, 127.4375f, 963.7417f, 710.0f),
            floatArrayOf(1440.0f, 900.0f, 1.0f, 518.4459f, 136.61719f, 1078.3915f, 810.0f),
            floatArrayOf(1440.0f, 900.0f, 1.3f, 518.4459f, 136.61719f, 1078.3915f, 810.0f),
        )
        for (row in baselines) {
            val hero = requireNotNull(ContinuumHomeLayout.wide(row[0], row[1], row[2])[Slot.HERO])
            val actual = floatArrayOf(hero.left, hero.top, hero.right, hero.bottom)
            check(actual.indices.all { actual[it].toRawBits() == row[it + 3].toRawBits() })
        }
    }
}
