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
        val tiles = shell.tileBoxes()
        if (shell.profile.widthClass == WidthClass.COMPACT) {
            assertTrue(shell.hasFeatureTiles)
            val tileArea = requireNotNull(shell[Slot.TILES])
            assertEquals(10, tiles.size)
            for (tile in tiles) {
                assertTrue(tileArea.contains(tile))
                assertTrue("tile hit width $tile", tile.width >= 48f - .01f)
                assertTrue("tile hit height $tile", tile.height >= 48f - .01f)
            }
        } else {
            assertFalse("wide navigation has one home surface", shell.hasFeatureTiles)
            assertNull(shell[Slot.TILES])
            assertTrue(tiles.isEmpty())
            assertEquals(0, shell.tileColumns)
            assertEquals(0, shell.tileRows)
        }
        for (i in tiles.indices) for (j in i + 1 until tiles.size) assertFalse(intersects(tiles[i], tiles[j]))
        val avatar = requireNotNull(shell[Slot.AVATAR])
        assertTrue(avatar.contains(requireNotNull(shell.headBounds)))
        assertTrue(avatar.contains(requireNotNull(shell.orbBounds)))
        assertFalse("head and brain are independent viewports", intersects(shell.headBounds!!, shell.orbBounds!!))
    }

    @Test fun mainDiagnosticsCloseThePrimaryReferenceBeforeSecondaryDetails() {
        val s = JarvisShellLayout.create(1536f, 1024f)
        val g = requireNotNull(s.grid)
        assertEquals(1536f, s.width, 0f)
        assertEquals(1024f, s.viewportHeight, 0f)
        assertEquals(g.height, s.height, 0f)
        assertEquals(g[Area.HEAD], s.headBounds)
        assertEquals(g[Area.HEAD], s.headStage)
        assertEquals(g[Area.ORB], s.orbBounds)
        assertEquals(g[Area.INPUT], s[Slot.INPUT])
        assertEquals(56f, g[Area.DIALOG].height, .01f)
        assertEquals(56f, g[Area.INPUT].height, .01f)
        assertTrue(g[Area.STATUS].bottom < 1024f)
        assertTrue(g[Area.ROW_MEMORY].top > g[Area.INPUT].bottom)
        assertEquals(g[Area.ROW_MEMORY].top, g[Area.ROW_MODEL].top, .01f)
        assertTrue(g[Area.ROW_AGENTS].top > g[Area.ROW_MEMORY].bottom)
        assertTrue(g[Area.STATUS].top > g[Area.ROW_AGENTS].bottom)
        assertTrue(g[Area.CAMERA_STATUS].top > g[Area.STATUS].bottom)
        assertFalse(Area.TILES in g.areas)
        assertFalse(Area.DEVICE_STATUS in g.areas)
        assertEquals(1f, g.unit, 0f)
        sane(s)
    }

    @Test fun theTenTileDestinationsKeepTheReferenceRowMajorOrder() {
        val shell = JarvisShellLayout.create(412f, 915f)
        assertEquals(2, shell.tileColumns)
        assertEquals(5, shell.tileRows)
        assertEquals(listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system"),
            JarvisTiles.reference.map { it.target })
        assertEquals(listOf("Gespräch", "Gedächtnis", "Denken und Planen", "Forschung", "Assistenten", "Aufgaben", "Internet", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        assertTrue(shell.tileBoxes()[2].top > shell.tileBoxes()[1].bottom)
        assertEquals(shell.tileBoxes()[0].left, shell.tileBoxes()[2].left, .001f)
    }

    @Test fun widthBreakpointsAreExplicitAndNotGuessedFromTheDeviceModel() {
        assertEquals(WidthClass.COMPACT, JarvisWindowProfile.resolve(599f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(600f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(839f, 900f).widthClass)
        assertEquals(WidthClass.EXPANDED, JarvisWindowProfile.resolve(840f, 900f).widthClass)
        for (w in listOf(600f, 692f, 760f, 832f, 884f)) {
            val wide = JarvisShellLayout.create(w, 884f)
            assertEquals(0, wide.tileColumns)
            assertFalse(wide.hasFeatureTiles)
            assertNotNull(wide[Slot.NAV_RAIL])
        }
        assertEquals(2, JarvisShellLayout.create(760f, 884f).profile.informationColumns)
        assertTrue(JarvisShellLayout.create(599f, 900f).hasFeatureTiles)
    }

    @Test fun shortWideWindowAndLargeFontsReflowWithoutNegativeHeroBounds() {
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) for ((w, h) in listOf(
            240f to 320f, 360f to 780f, 412f to 915f, 600f to 480f, 692f to 700f, 760f to 884f, 832f to 748f,
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

    @Test fun wideWindowsGiveChartsTheFirstTwoColumnRow() {
        for (shell in listOf(JarvisShellLayout.create(1536f, 1024f), JarvisShellLayout.create(760f, 884f))) {
            assertEquals(2, shell.profile.informationColumns)
            assertEquals(shell[Slot.ROW_MEMORY]!!.top, shell[Slot.ROW_MODEL]!!.top, .01f)
            assertTrue(shell[Slot.ROW_MEMORY]!!.right < shell[Slot.ROW_MODEL]!!.left)
            assertEquals(shell[Slot.ROW_AGENTS]!!.top, shell[Slot.ROW_TASKS]!!.top, .01f)
            assertTrue(shell[Slot.ROW_AGENTS]!!.top > shell[Slot.ROW_MEMORY]!!.bottom)
            assertTrue(shell[Slot.ROW_MEMORY]!!.height > shell[Slot.ROW_AGENTS]!!.height)
        }
    }

    @Test fun extraHeightGrowsTheHeroWithoutStretchingCards() {
        val original = JarvisShellLayout.create(1536f, 1024f)
        val taller = JarvisShellLayout.create(1536f, 1224f)
        val heroGrowth = taller.headBounds!!.height - original.headBounds!!.height
        assertTrue(heroGrowth > 0f)
        assertTrue(taller.headBounds!!.height <= 480f)
        assertEquals(original[Slot.ROW_MEMORY]!!.height, taller[Slot.ROW_MEMORY]!!.height, .01f)
        assertEquals(original[Slot.ROW_AGENTS]!!.height, taller[Slot.ROW_AGENTS]!!.height, .01f)
        assertEquals(heroGrowth, taller[Slot.STATUS]!!.top - original[Slot.STATUS]!!.top, .01f)
        assertEquals(original.grid!!.unit, taller.grid!!.unit, 0f)
        sane(taller)
    }

    @Test fun compactBadgeLeavesTheUpperHeadClearAndMovesControlsBelowIt() {
        for ((w, h) in listOf(692f to 700f, 832f to 748f, 840f to 560f, 884f to 760f, 1536f to 1024f)) {
            for (font in listOf(1f, 1.3f)) {
                val shell = JarvisShellLayout.create(w, h, font)
                if (!shell.ref5Grid) continue
                val head = shell.headBounds!!; val dialog = shell[Slot.DIALOG]!!
                assertTrue("$w×$h font$font: badge belongs to stage", head.contains(dialog))
                assertTrue("badge leaves upper face free", dialog.top >= head.top + head.height * .6f)
                shell.dialogControlBoxes().forEach {
                    assertTrue(it.width >= 48f - .01f && it.height >= 48f - .01f)
                    assertTrue(it.top > head.bottom)
                }
            }
        }
    }

    @Test fun missingOwnerCardNoLongerShrinksThePortraitViewport() {
        val shell = JarvisShellLayout.create(884f, 760f)
        assertNull(shell[Slot.USER_CARD])
        assertFalse(Area.USER_CARD in shell.grid!!.areas)
        assertTrue(shell.headBounds!!.width > shell[Slot.AVATAR]!!.width * .48f)
    }

    @Test fun reservationsAreSubtractedOnceAndDoNotCreateASecondNavigationRail() {
        val full = JarvisShellLayout.create(884f, 760f)
        val pane = JarvisShellLayout.forContent(754f, 760f, externalRailWidthDp = 130f)
        assertNull(pane[Slot.NAV_RAIL])
        assertFalse(Area.RAIL in pane.grid!!.areas)
        assertEquals(full.headBounds!!.left, pane.headBounds!!.left + 130f, .001f)
        assertEquals(full.orbBounds!!.left, pane.orbBounds!!.left + 130f, .001f)
        assertFalse(pane.hasFeatureTiles)
        assertTrue(pane.tileBoxes().isEmpty())
        val phone = JarvisShellLayout.forContent(412f, 915f, externalBottomBarHeightDp = 68f)
        assertEquals(847f, phone.viewportHeight, 0f)
        assertNull(phone[Slot.BOTTOM_NAV])
    }

    @Test fun actualControlsHaveDisjointFortyEightDpHitBoxes() {
        for ((w, h) in listOf(240f to 320f, 412f to 915f, 692f to 700f, 760f to 884f, 832f to 748f, 884f to 760f, 1536f to 1024f)) {
            val shell = JarvisShellLayout.create(w, h)
            val controls = shell.dialogControlBoxes()
            assertEquals(3, controls.size)
            val conversation = requireNotNull(shell.inputConversationBox())
            assertTrue(conversation.width >= 48f && conversation.height >= 48f)
            for (control in controls) {
                assertTrue(shell[Slot.INPUT]!!.contains(control))
                assertFalse(intersects(requireNotNull(shell.inputConversationBox()), control))
                assertTrue(control.width >= 48f - .01f && control.height >= 48f - .01f)
            }
            controls.zipWithNext().forEach { (a, b) -> assertFalse(intersects(a, b)) }
            shell.headerActionBoxes().values.zipWithNext().forEach { (a, b) -> assertFalse(intersects(a, b)) }
        }
    }

    @Test fun nearSquareFoldUsesReleasedTileSpaceForAccessibleDiagnostics() {
        for ((w, h) in listOf(692f to 700f, 832f to 748f, 840f to 560f, 884f to 760f, 960f to 640f)) {
            val shell = JarvisShellLayout.create(w, h)
            assertTrue(shell.ref5Grid)
            assertEquals(56f, requireNotNull(shell[Slot.DIALOG]).height, .01f)
            assertTrue(requireNotNull(shell[Slot.INPUT]).top > requireNotNull(shell[Slot.AVATAR]).bottom)
            assertFalse(shell.hasFeatureTiles)
            assertTrue(shell.tileBoxes().isEmpty())
            assertTrue(shell[Slot.ROW_MEMORY]!!.top > shell[Slot.INPUT]!!.bottom)
            for (slot in listOf(Slot.ROW_MEMORY, Slot.ROW_MODEL)) assertTrue(shell[slot]!!.height >= 190f - .01f)
            for (slot in listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS)) assertTrue(shell[slot]!!.height >= 132f - .01f)
            assertTrue("readable controls scroll", shell.requiresVerticalScroll)
            assertTrue(shell[Slot.STATUS]!!.top > shell[Slot.ROW_TASKS]!!.bottom)
            assertTrue(shell[Slot.COCKPIT_SECONDARY]!!.top > shell[Slot.STATUS]!!.bottom)
            sane(shell)
        }
    }

    @Test fun compactModeKeepsTheSameDestinationsAndDisjointInputActions() {
        for ((w, h) in listOf(240f to 320f, 412f to 915f, 692f to 700f, 832f to 748f, 884f to 760f)) {
            val shell = JarvisShellLayout.create(w, h, compactPresentation = true)
            assertNull(shell.headBounds)
            assertEquals(112f, requireNotNull(shell.orbBounds).height, .01f)
            assertEquals(w < 600f, shell.hasFeatureTiles)
            assertEquals(if (w < 600f) 10 else 0, shell.tileBoxes().size)
            assertEquals(3, shell.dialogControlBoxes().size)
            val conversation = requireNotNull(shell.inputConversationBox())
            shell.dialogControlBoxes().forEach {
                assertTrue(shell[Slot.INPUT]!!.contains(it))
                assertFalse(intersects(conversation, it))
            }
            if (shell.hasFeatureTiles) assertTrue(shell[Slot.ROW_AGENTS]!!.top > shell[Slot.STATUS]!!.bottom)
            else assertTrue(shell[Slot.STATUS]!!.top > shell[Slot.ROW_TASKS]!!.bottom)
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
