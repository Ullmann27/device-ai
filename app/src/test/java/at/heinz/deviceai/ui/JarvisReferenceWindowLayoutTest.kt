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
        assertNull("Home has no fixed rail", shell[Slot.NAV_RAIL])
        assertNull(shell.rail)
        assertEquals(0f, shell.profile.railWidthDp, 0f)
        val tiles = shell.tileBoxes()
        if (shell.hasFeatureTiles) {
            val tileArea = requireNotNull(shell[Slot.TILES])
            assertEquals(10, tiles.size)
            for (tile in tiles) {
                assertTrue(tileArea.contains(tile))
                assertTrue("tile hit width $tile", tile.width >= 48f - .01f)
                assertTrue("tile hit height $tile", tile.height >= 158f * maxOf(1f, shell.profile.fontScale) - .01f)
            }
            assertTrue(tileArea.top > shell[Slot.INPUT]!!.bottom)
            assertTrue(shell[Slot.STATUS]!!.top > tileArea.bottom)
            for (i in tiles.indices) for (j in i + 1 until tiles.size) assertFalse(intersects(tiles[i], tiles[j]))
        } else {
            assertEquals(0, tiles.size)
            assertNotNull(shell[Slot.BOTTOM_NAV])
            listOf(Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT).forEach {
                assertNotNull("$it on packed cockpit", shell[it])
            }
        }
        for (slot in listOf(Slot.ROW_MEMORY, Slot.ROW_MODEL, Slot.ROW_AGENTS, Slot.ROW_TASKS)) {
            val box = shell[slot] ?: continue
            assertTrue("$slot follows the complete home", box.top > shell[Slot.STATUS]!!.bottom)
        }
        val avatar = requireNotNull(shell[Slot.AVATAR])
        assertTrue(avatar.contains(requireNotNull(shell.headBounds)))
        assertTrue(avatar.contains(requireNotNull(shell.orbBounds)))
        assertFalse("head and brain are independent viewports", intersects(shell.headBounds!!, shell.orbBounds!!))
    }

    @Test fun referenceHomePacksWorkPanelsIntoTheViewportWithoutScrolling() {
        val s = JarvisShellLayout.create(1536f, 1024f)
        val g = requireNotNull(s.grid)
        assertEquals(1536f, s.width, 0f)
        assertEquals(1024f, s.viewportHeight, 0f)
        assertEquals(g.height, s.height, 0f)
        assertEquals(s.viewportHeight, s.height, 0f)
        assertFalse(s.requiresVerticalScroll)
        assertNotNull("Fold/tablet home uses the holographic tool dock, not a left rail", s[Slot.BOTTOM_NAV])
        assertTrue(s[Slot.BOTTOM_NAV]!!.top >= s[Slot.WORK_DOCUMENT]!!.bottom - .01f)
        assertTrue(s[Slot.STATUS]!!.top >= s[Slot.BOTTOM_NAV]!!.bottom - .01f)
        assertEquals(g[Area.HEAD], s.headBounds)
        assertEquals(g[Area.HEAD], s.headStage)
        assertEquals(g[Area.ORB], s.orbBounds)
        assertEquals(g[Area.INPUT], s[Slot.INPUT])
        assertTrue(g[Area.DIALOG].height >= 140f)
        assertTrue(g[Area.INPUT].height >= 40f)
        assertTrue(g[Area.HEAD].height <= JarvisCockpitGrid.PACKED_HERO_MAX_DP + .01f)
        assertTrue(g[Area.WORK_DOCUMENT].top > g[Area.INPUT].bottom)
        assertTrue(g[Area.TOOL_DOCK].top > g[Area.WORK_DOCUMENT].bottom)
        assertTrue(g[Area.STATUS].top > g[Area.TOOL_DOCK].bottom)
        assertTrue(g[Area.CAMERA_STATUS].left > g[Area.ORB].right)
        assertFalse(Area.RAIL in g.areas)
        assertTrue(Area.DEVICE_STATUS in g.areas)
        assertEquals(1f, g.unit, 0f)
        sane(s)
    }

    @Test fun theTenTileDestinationsKeepTheReferenceRowMajorOrder() {
        val shell = JarvisShellLayout.create(884f, 760f)
        assertEquals(5, shell.tileColumns)
        assertEquals(2, shell.tileRows)
        assertEquals(listOf("chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system"),
            JarvisTiles.reference.map { it.target })
        assertEquals(listOf("Gespräch", "Gedächtnis", "Denken und Planen", "Forschung", "Mein KI-Team", "Aufgaben", "Internet", "Lernen", "Gerätesteuerung", "Systemstatus"),
            JarvisTiles.reference.map { it.label })
        val phone = JarvisShellLayout.create(412f, 915f)
        assertFalse(phone.hasFeatureTiles)
        assertEquals(0, phone.tileBoxes().size)
        assertNotNull(phone[Slot.BOTTOM_NAV])
        assertFalse(phone.requiresVerticalScroll)
        val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
        assertEquals(JarvisTiles.bottomNav.map { it.target },
            JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                JarvisShellContent.hitTarget(shell, JarvisTiles.reference, box.centerX, box.centerY)
            })
    }

    @Test fun widthBreakpointsAreExplicitAndNotGuessedFromTheDeviceModel() {
        assertEquals(WidthClass.COMPACT, JarvisWindowProfile.resolve(599f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(600f, 900f).widthClass)
        assertEquals(WidthClass.MEDIUM, JarvisWindowProfile.resolve(839f, 900f).widthClass)
        assertEquals(WidthClass.EXPANDED, JarvisWindowProfile.resolve(840f, 900f).widthClass)
        for (w in listOf(600f, 692f, 760f, 832f, 884f)) {
            val wide = JarvisShellLayout.create(w, 884f)
            assertEquals(5, wide.tileColumns)
            assertEquals(2, wide.tileRows)
            assertTrue(wide.ref5Grid)
            assertFalse(wide.requiresVerticalScroll)
            assertNotNull(wide[Slot.BOTTOM_NAV])
            assertNull(wide[Slot.NAV_RAIL])
        }
        assertEquals(2, JarvisShellLayout.create(760f, 884f).profile.informationColumns)
        assertFalse("compact width uses the cinema composition, not the 10-tile overflow",
            JarvisShellLayout.create(599f, 900f).hasFeatureTiles)
        assertNotNull(JarvisShellLayout.create(599f, 900f)[Slot.BOTTOM_NAV])
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

    @Test fun compactPhoneKeepsHeadAndSphereBesideEachOtherWithoutOverlappingTheTitle() {
        val shell = JarvisShellLayout.create(412f, 915f)
        assertNull(shell[Slot.NAV_RAIL])
        assertNotNull("cinema phone exposes the holographic dock", shell[Slot.BOTTOM_NAV])
        assertNull(shell[Slot.TILES])
        assertTrue(shell.headBounds!!.right < shell.orbBounds!!.left)
        assertTrue(shell[Slot.CONTINUUM_HEADER]!!.bottom <= shell.orbBounds!!.top + .01f)
        assertFalse(intersects(shell.headBounds!!, shell.orbBounds!!))
        assertFalse(intersects(shell[Slot.CONTINUUM_HEADER]!!, shell.orbBounds!!))
        assertTrue("mission stays below both identities",
            shell.headBounds!!.bottom <= shell[Slot.INPUT]!!.top &&
                shell.orbBounds!!.bottom <= shell[Slot.INPUT]!!.top)
        assertTrue(shell[Slot.WORK_DOCUMENT]!!.top > shell[Slot.INPUT]!!.bottom)
        assertFalse(shell.requiresVerticalScroll)
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
            assertTrue("font$font: head keeps a usable viewport", head.height >= 164f)
            assertTrue("font$font: original orb keeps a usable viewport", orb.height >= 96f)
            assertTrue("font$font: Continuum title does not sit on the orb",
                shell[Slot.CONTINUUM_HEADER]!!.bottom <= orb.top + .01f)
            assertNotNull("font$font: cinema work window", shell[Slot.WORK_DOCUMENT])
            assertNotNull("font$font: cinema dock", shell[Slot.BOTTOM_NAV])
            val protected = listOf(head, orb)
            assertTrue("font$font: both identities stay visible", protected.all { it.bottom < shell.viewportHeight })
            assertFalse(shell.hasFeatureTiles)
            assertEquals(0, shell.tileBoxes().size)
            sane(shell)
            assertTrue("font$font: live card is one of the three top boxes", dialog.height > 0f)
        }
    }

    @Test fun wideWindowsKeepWorkPanelsOnTheFirstScreen() {
        for (shell in listOf(JarvisShellLayout.create(1536f, 1024f), JarvisShellLayout.create(760f, 884f))) {
            assertEquals(2, shell.profile.informationColumns)
            assertFalse(shell.requiresVerticalScroll)
            assertEquals(shell[Slot.WORK_DOCUMENT]!!.top, shell[Slot.WORK_PROCESS]!!.top, .01f)
            assertTrue(shell[Slot.WORK_DOCUMENT]!!.right < shell[Slot.WORK_PROCESS]!!.left)
            assertTrue(shell[Slot.WORK_PROCESS]!!.right < shell[Slot.WORK_DRAFT]!!.left)
            assertNull(shell[Slot.ROW_MEMORY])
            assertNotNull(shell[Slot.BOTTOM_NAV])
        }
    }

    @Test fun viewportHeightDoesNotDistortTheReferenceProportions() {
        val original = JarvisShellLayout.create(1536f, 1024f)
        val taller = JarvisShellLayout.create(1536f, 1224f)
        assertFalse(original.requiresVerticalScroll)
        assertFalse(taller.requiresVerticalScroll)
        assertTrue(taller.headBounds!!.height <= JarvisCockpitGrid.PACKED_HERO_MAX_DP + .01f)
        assertTrue(original.headBounds!!.height <= JarvisCockpitGrid.PACKED_HERO_MAX_DP + .01f)
        assertEquals(original.grid!!.unit, taller.grid!!.unit, 0f)
        assertNotNull(taller[Slot.WORK_DOCUMENT])
        sane(taller)
    }

    @Test fun packedHeroKeepsHeadAndOrbBesideTheLiveAndStatusColumns() {
        for ((w, h) in listOf(692f to 700f, 840f to 900f, 884f to 1400f)) {
            val shell = JarvisShellLayout.create(w, h)
            val hero = requireNotNull(shell[Slot.AVATAR])
            assertTrue(shell[Slot.DIALOG]!!.right < hero.left)
            assertTrue(shell[Slot.COCKPIT_SECONDARY]!!.left > hero.right)
            assertTrue("packed hero stays inside the viewport", hero.height <= JarvisCockpitGrid.PACKED_HERO_MAX_DP + .01f)
            assertFalse(shell.requiresVerticalScroll)
            assertNull(shell[Slot.NAV_RAIL])
        }
    }

    @Test fun liveCardSitsBesideTheHeadAndMovesControlsBelowIt() {
        for ((w, h) in listOf(692f to 700f, 832f to 748f, 840f to 560f, 884f to 760f, 1536f to 1024f)) {
            for (font in listOf(1f, 1.3f)) {
                val shell = JarvisShellLayout.create(w, h, font)
                if (!shell.ref5Grid) continue
                val head = shell.headBounds!!; val dialog = shell[Slot.DIALOG]!!
                assertFalse("$w×$h font$font: live card is not painted over the face", intersects(head, dialog))
                assertTrue("live card stays left of the head", dialog.right < head.left)
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

    @Test fun availablePaneIsLaidOutWithoutClippingAHiddenRail() {
        val full = JarvisShellLayout.create(754f, 760f)
        val pane = JarvisShellLayout.forContent(754f, 760f, externalRailWidthDp = 130f)
        assertNull(pane[Slot.NAV_RAIL])
        assertFalse(Area.RAIL in pane.grid!!.areas)
        assertEquals(full, pane)
        assertFalse(pane.hasFeatureTiles)
        assertEquals(0, pane.tileBoxes().size)
        assertNotNull(pane[Slot.BOTTOM_NAV])
        val phone = JarvisShellLayout.forContent(412f, 915f, externalBottomBarHeightDp = 68f)
        assertEquals(847f, phone.viewportHeight, 0f)
        assertNotNull("cinema phone keeps the holographic dock inside the reserved viewport", phone[Slot.BOTTOM_NAV])
        assertFalse(phone.hasFeatureTiles)
    }

    @Test fun actualControlsHaveDisjointFortyEightDpHitBoxes() {
        for ((w, h) in listOf(240f to 320f, 412f to 915f, 692f to 700f, 760f to 884f, 832f to 748f, 884f to 760f, 1536f to 1024f)) {
            val shell = JarvisShellLayout.create(w, h)
            val controls = shell.dialogControlBoxes()
            assertEquals(2, controls.size)
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

    @Test fun nearSquareFoldKeepsThePackedStartCompositionWithoutScrolling() {
        for ((w, h) in listOf(692f to 700f, 832f to 748f, 840f to 560f, 884f to 760f, 960f to 640f)) {
            val shell = JarvisShellLayout.create(w, h)
            assertTrue(shell.ref5Grid)
            assertTrue(requireNotNull(shell[Slot.DIALOG]).height >= 140f)
            assertTrue(requireNotNull(shell[Slot.INPUT]).top > requireNotNull(shell[Slot.AVATAR]).bottom)
            assertFalse(shell.hasFeatureTiles)
            assertEquals(0, shell.tileBoxes().size)
            assertEquals(5, shell.tileColumns)
            assertEquals(2, shell.tileRows)
            assertNull(shell[Slot.NAV_RAIL])
            assertTrue(shell.headBounds!!.height <= JarvisCockpitGrid.PACKED_HERO_MAX_DP + .01f)
            assertTrue(shell[Slot.WORK_DOCUMENT]!!.top > shell[Slot.INPUT]!!.bottom)
            assertTrue(shell[Slot.STATUS]!!.top > shell[Slot.BOTTOM_NAV]!!.bottom)
            assertFalse("packed fold/tablet cockpit does not scroll", shell.requiresVerticalScroll)
            assertTrue(shell[Slot.COCKPIT_SECONDARY]!!.left > shell.orbBounds!!.right)
            assertEquals(h, shell.height, .01f)
            sane(shell)
        }
    }

    @Test fun compactModeKeepsTheSameDestinationsAndDisjointInputActions() {
        for ((w, h) in listOf(240f to 320f, 412f to 915f, 692f to 700f, 832f to 748f, 884f to 760f)) {
            val shell = JarvisShellLayout.create(w, h, compactPresentation = true)
            assertNull(shell.headBounds)
            assertEquals(112f, requireNotNull(shell.orbBounds).height, .01f)
            assertNull(shell[Slot.NAV_RAIL])
            assertEquals(2, shell.dialogControlBoxes().size)
            val conversation = requireNotNull(shell.inputConversationBox())
            shell.dialogControlBoxes().forEach {
                assertTrue(shell[Slot.INPUT]!!.contains(it))
                assertFalse(intersects(conversation, it))
            }
            if (shell.hasFeatureTiles) {
                assertEquals(10, shell.tileBoxes().size)
                assertTrue(shell[Slot.ROW_AGENTS]!!.top > shell[Slot.STATUS]!!.bottom)
            } else {
                assertEquals(0, shell.tileBoxes().size)
                assertNotNull(shell[Slot.BOTTOM_NAV])
                listOf(Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT).forEach {
                    assertNotNull("$it on compact packed cockpit", shell[it])
                }
                if (h >= 700f) {
                    assertTrue("compact cinema/reference shortens a tall page", shell.height < shell.viewportHeight + .01f)
                }
            }
        }
    }

    @Test fun cinemaPhoneDockAndMorePreserveEveryDestinationWithoutASecondBar() {
        val phone = JarvisShellLayout.create(412f, 915f)
        val dock = requireNotNull(phone[Slot.BOTTOM_NAV])
        val expected = listOf("home", "chat", "memory", "research", "tasks", "web", "learning", "device")
        assertEquals("The eight owner buttons have stable native routes", expected, JarvisTiles.bottomNav.map { it.target })
        val reachable = expected + at.heinz.deviceai.ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }
        assertTrue("Former rail actions remain available", reachable.containsAll(JarvisTiles.bottomNav.map { it.target }))
        assertEquals(expected,
            JarvisTiles.bottomNavItemBoxes(dock).map { box ->
                JarvisShellContent.hitTarget(phone, JarvisTiles.reference, box.centerX, box.centerY)
            })
        val withBar = JarvisShellLayout.create(412f, 915f, includeBottomNavigation = true)
        assertEquals("includeBottomNavigation must not add a second navigation row",
            phone[Slot.BOTTOM_NAV], withBar[Slot.BOTTOM_NAV])
        sane(phone)
        sane(withBar)
    }
}
