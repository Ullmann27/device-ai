package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import at.heinz.deviceai.ui.JarvisWindowProfile.WidthClass
import org.junit.Assert.*
import org.junit.Test

/** New owner-reference contracts. These verify geometry / routing, not pixel or physical Fold7 acceptance. */
class JarvisReferenceWindowLayoutTest {
    private fun intersects(a: Box, b: Box) =
        a.left < b.right - .01f && b.left < a.right - .01f && a.top < b.bottom - .01f && b.top < a.bottom - .01f

    private fun sane(shell: JarvisShellLayout.Shell) {
        for ((name, box) in shell.boxes) {
            assertTrue("$name inside ${shell.width} × ${shell.height}: $box",
                box.left >= 0f && box.top >= 0f && box.right <= shell.width + .01f && box.bottom <= shell.height + .01f)
            assertTrue("$name has positive size", box.width > 0f && box.height > 0f)
        }
        val entries = shell.boxes.entries.toList()
        for (i in entries.indices) for (j in i + 1 until entries.size) {
            val pair = entries[i].key to entries[j].key
            if (pair !in shell.overlays && pair.second to pair.first !in shell.overlays) {
                assertFalse("unexpected visual-region overlap $pair", intersects(entries[i].value, entries[j].value))
            }
        }
        val tileArea = requireNotNull(shell[Slot.TILES])
        val tiles = shell.tileBoxes()
        assertEquals(10, tiles.size)
        for (tile in tiles) {
            assertTrue(tileArea.contains(tile))
            assertTrue("tile hit width $tile", tile.width >= 48f - .01f)
            assertTrue("tile hit height $tile", tile.height >= 48f - .01f)
        }
        for (i in tiles.indices) for (j in i + 1 until tiles.size) assertFalse(intersects(tiles[i], tiles[j]))
        val avatar = requireNotNull(shell[Slot.AVATAR])
        assertTrue(avatar.contains(requireNotNull(shell.headBounds)))
        assertTrue(avatar.contains(requireNotNull(shell.orbBounds)))
        assertFalse("head and brain are independent viewports", intersects(shell.headBounds!!, shell.orbBounds!!))
    }

    @Test fun canonicalRegionsComeFromTheNewImageWithoutLetterboxTransform() {
        val s = JarvisShellLayout.create(1536f, 1024f)
        val g = requireNotNull(s.grid)
        assertEquals(1536f, s.width, 0f)
        assertEquals(1024f, s.height, 0f)
        assertEquals(Box(548f, 70f, 800f, 497f), g[Area.HEAD])
        assertEquals(Box(805f, 183f, 1212f, 497f), g[Area.ORB])
        assertEquals(Box(256f, 298f, 540f, 492f), g[Area.DIALOG])
        assertEquals(Box(258f, 503f, 1520f, 685f), g[Area.TILES])
        assertEquals(Box(12f, 97f, 243f, 896f), g[Area.RAIL])
        assertEquals(Box(16f, 906f, 1520f, 978f), g[Area.STATUS])
        assertEquals(Box(805f, 80f, 1194f, 180f), g[Area.CONTINUUM_HEADER])
        assertEquals(Box(1199f, 83f, 1520f, 141f), g[Area.CAMERA_STATUS])
        assertEquals(1f, g.unit, 0f)
        sane(s)
    }

    @Test fun theTenTileDestinationsKeepTheReferenceRowMajorOrder() {
        val shell = JarvisShellLayout.create(884f, 760f)
        assertEquals(5, shell.tileColumns)
        assertEquals(2, shell.tileRows)
        assertEquals(listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system"),
            JarvisTiles.reference.map { it.target })
        assertEquals(listOf("Gespräch", "Gedächtnis", "Denken und Planen", "Forschung", "Assistenten", "Aufgaben", "Internet", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        assertTrue(shell.tileBoxes()[5].top > shell.tileBoxes()[4].bottom)
        assertEquals(shell.tileBoxes()[0].left, shell.tileBoxes()[5].left, .001f)
    }

    @Test fun widthBreakpointsAreExplicitAndNotGuessedFromTheDeviceModel() {
        assertEquals(WidthClass.COMPACT, JarvisWindowProfile.resolve(599f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(600f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(839f, 900f).widthClass)
        assertEquals(WidthClass.EXPANDED, JarvisWindowProfile.resolve(840f, 900f).widthClass)
        assertEquals(3, JarvisShellLayout.create(760f, 884f).tileColumns)
        assertEquals(2, JarvisShellLayout.create(760f, 884f).profile.informationColumns)
        assertEquals(5, JarvisShellLayout.create(884f, 760f).tileColumns)
    }

    @Test fun shortWideWindowAndLargeFontsReflowWithoutNegativeHeroBounds() {
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) for ((w, h) in listOf(
            240f to 320f, 360f to 780f, 412f to 915f, 600f to 480f, 760f to 884f,
            840f to 560f, 884f to 760f, 915f to 412f, 1280f to 400f, 1536f to 1024f)) {
            val shell = JarvisShellLayout.create(w, h, font)
            assertEquals(h, shell.viewportHeight, 0f)
            sane(shell)
        }
        assertFalse(JarvisShellLayout.create(884f, 760f, 2f).ref5Grid)
        assertTrue(JarvisShellLayout.create(884f, 760f, 2f).requiresVerticalScroll)
        assertFalse(JarvisShellLayout.create(915f, 412f).ref5Grid)
    }

    @Test fun compactPhoneKeepsHeadAndSphereBesideEachOtherAboveDialogue() {
        val shell = JarvisShellLayout.create(412f, 915f)
        assertEquals(2, shell.tileColumns)
        assertEquals(5, shell.tileRows)
        assertNull(shell[Slot.NAV_RAIL])
        assertNull("Activity owns its five-category phone bar", shell[Slot.BOTTOM_NAV])
        assertTrue(shell.headBounds!!.bottom <= shell[Slot.DIALOG]!!.top)
        assertTrue(shell.headBounds!!.right < shell.orbBounds!!.left)
        assertTrue(shell.orbBounds!!.bottom <= shell[Slot.DIALOG]!!.top)
        assertTrue(shell.orbBounds!!.bottom < shell[Slot.TILES]!!.top)
        assertTrue(shell.requiresVerticalScroll)
        sane(shell)
    }

    @Test fun coverKeepsBothIdentitiesAndARealWorkAreaAtLargeFonts() {
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) {
            val shell = JarvisShellLayout.forContent(412f, 915f, font,
                externalBottomBarHeightDp = ContinuumHomeLayout.PHONE_NAV_HEIGHT)
            val head = requireNotNull(shell.headBounds)
            val orb = requireNotNull(shell.orbBounds)
            val dialog = requireNotNull(shell[Slot.DIALOG])
            assertTrue("font$font: head and original orb share the hero", head.right < orb.left &&
                maxOf(head.top, orb.top) < minOf(head.bottom, orb.bottom))
            assertTrue("font$font: head keeps a usable viewport", head.height >= 220f)
            assertTrue("font$font: original orb keeps a usable viewport", orb.height >= 112f)
            assertTrue("font$font: dialogue stays below both identities",
                head.bottom <= dialog.top && orb.bottom <= dialog.top)
            val protected = listOf(head, orb, dialog)
            val workspace = Box(8f, protected.maxOf { it.bottom } + 8f,
                shell.width - 8f, shell.viewportHeight - 8f)
            assertTrue("font$font: actual free width supports the work area", workspace.width >= 200f)
            assertTrue("font$font: actual free height supports the work area: $workspace", workspace.height >= 160f - .01f)
            protected.forEach { assertFalse("font$font: workspace cannot cover $it", intersects(workspace, it)) }
            assertTrue("font$font: both identities stay visible", protected.all { it.bottom < shell.viewportHeight })
            assertEquals(10, shell.tileBoxes().size)
            sane(shell)
        }
    }

    @Test fun expandedWindowsUseFourInformationColumnsMediumWindowsUseTwoRows() {
        val expanded = JarvisShellLayout.create(884f, 760f)
        val medium = JarvisShellLayout.create(760f, 884f)
        assertEquals(expanded[Slot.ROW_AGENTS]!!.top, expanded[Slot.ROW_MODEL]!!.top, 0f)
        assertTrue(expanded[Slot.ROW_AGENTS]!!.right < expanded[Slot.ROW_TASKS]!!.left)
        assertEquals(medium[Slot.ROW_AGENTS]!!.top, medium[Slot.ROW_TASKS]!!.top, 0f)
        assertTrue(medium[Slot.ROW_MEMORY]!!.top > medium[Slot.ROW_AGENTS]!!.bottom)
        assertEquals(medium[Slot.ROW_MEMORY]!!.top, medium[Slot.ROW_MODEL]!!.top, 0f)
    }

    @Test fun extraHeightEnlargesHeroAndInformationWithoutStretchingItsText() {
        val original = JarvisShellLayout.create(1536f, 1024f)
        val taller = JarvisShellLayout.create(1536f, 1224f)
        assertEquals(160f, taller.headBounds!!.height - original.headBounds!!.height, .01f)
        assertEquals(40f, taller[Slot.ROW_MEMORY]!!.height - original[Slot.ROW_MEMORY]!!.height, .01f)
        assertEquals(original.tileBoxes().first().height, taller.tileBoxes().first().height, .01f)
        assertEquals(200f, taller[Slot.STATUS]!!.top - original[Slot.STATUS]!!.top, .01f)
        assertEquals(original.grid!!.unit, taller.grid!!.unit, 0f)
        sane(taller)
    }

    @Test fun theDialogueNeverCoversTheHeadInAnyReferenceWindow() {
        for ((w, h) in listOf(840f to 560f, 840f to 900f, 884f to 760f, 960f to 640f, 1536f to 1024f, 1536f to 1224f)) {
            for (font in listOf(1f, 1.3f)) {
                val shell = JarvisShellLayout.create(w, h, font)
                if (!shell.ref5Grid) continue
                val head = shell.headBounds!!; val dialog = shell[Slot.DIALOG]!!
                assertTrue("$w×$h font$font: dialogue beside the head", dialog.right <= head.left)
                shell.dialogControlBoxes().forEach { assertTrue(it.width >= 48f - .01f && it.height >= 48f - .01f) }
            }
        }
    }

    @Test fun missingOwnerCardNoLongerShrinksThePortraitViewport() {
        val shell = JarvisShellLayout.create(884f, 760f)
        assertNull(shell[Slot.USER_CARD])
        assertFalse(Area.USER_CARD in shell.grid!!.areas)
        assertEquals(252f / 1536f * 884f, shell.headBounds!!.width, .01f)
    }

    @Test fun reservationsAreSubtractedOnceAndDoNotCreateASecondNavigationRail() {
        val full = JarvisShellLayout.create(884f, 760f)
        val pane = JarvisShellLayout.forContent(754f, 760f, externalRailWidthDp = 130f)
        assertNull(pane[Slot.NAV_RAIL])
        assertFalse(Area.RAIL in pane.grid!!.areas)
        assertEquals(full.headBounds!!.left, pane.headBounds!!.left + 130f, .001f)
        assertEquals(full.orbBounds!!.left, pane.orbBounds!!.left + 130f, .001f)
        assertEquals(10, pane.tileBoxes().size)
        val phone = JarvisShellLayout.forContent(412f, 915f, externalBottomBarHeightDp = 68f)
        assertEquals(847f, phone.viewportHeight, 0f)
        assertNull(phone[Slot.BOTTOM_NAV])
    }

    @Test fun actualControlsHaveDisjointFortyEightDpHitBoxes() {
        for ((w, h) in listOf(412f to 915f, 760f to 884f, 884f to 760f, 1536f to 1024f)) {
            val shell = JarvisShellLayout.create(w, h)
            val controls = shell.dialogControlBoxes()
            assertEquals(3, controls.size)
            for (control in controls) {
                assertTrue(shell[Slot.DIALOG]!!.contains(control))
                assertTrue(control.width >= 48f - .01f && control.height >= 48f - .01f)
            }
            controls.zipWithNext().forEach { (a, b) -> assertFalse(intersects(a, b)) }
            shell.headerActionBoxes().values.zipWithNext().forEach { (a, b) -> assertFalse(intersects(a, b)) }
        }
    }

    @Test fun narrowReferenceDialogReservesReplySpaceAboveUnchangedControls() {
        for ((w, h) in listOf(840f to 560f, 840f to 900f, 884f to 760f, 960f to 640f)) {
            val shell = JarvisShellLayout.create(w, h)
            assertTrue(shell.ref5Grid)
            val dialog = requireNotNull(shell[Slot.DIALOG])
            assertTrue("$w×$h real reply space", dialog.height >= JarvisCockpitGrid.MIN_DIALOG_DP)
            assertTrue("heading and reply stay above controls", shell.dialogControlBoxes().first().top - dialog.top >= 90f)
            shell.dialogControlBoxes().forEach { assertEquals(48f, it.height, .001f) }
            sane(shell)
        }
    }

    @Test fun optionalStandalonePhoneBarIsOnlyAddedWhenRequested() {
        assertNull(JarvisShellLayout.create(412f, 915f)[Slot.BOTTOM_NAV])
        val withBar = JarvisShellLayout.create(412f, 915f, includeBottomNavigation = true)
        assertNotNull(withBar[Slot.BOTTOM_NAV])
        assertEquals(5, JarvisTiles.bottomNav.size)
        sane(withBar)
    }
}
