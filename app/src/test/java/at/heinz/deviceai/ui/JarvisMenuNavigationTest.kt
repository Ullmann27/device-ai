package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.cockpit.JarvisNavigator
import at.heinz.deviceai.cockpit.NAV_TURN
import at.heinz.deviceai.cockpit.TransitionPattern
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMenuNavigationTest {
    private fun setup(): Triple<JarvisMenuNavigator, MutableList<Slot>, MutableList<CockpitAnimationEvent>> {
        val bus = CockpitAnimationBus(); val events = mutableListOf<CockpitAnimationEvent>()
        bus.subscribe { events += it }
        val opened = mutableListOf<Slot>()
        return Triple(JarvisMenuNavigator(CockpitAnimationTriggers(bus) { 42L }, { opened += it }), opened, events)
    }

    @Test fun railHasTheElevenRef5EntriesInOrder() {
        // ref5: Home, Chat, Memory, Logic, Research, Agents, Tasks, Web, Learning, Device Control, System Status
        assertEquals(listOf("home", "chat", "memory", "logic", "research", "agents", "tasks", "web", "learning", "device", "system"),
            JarvisMenu.railEntries.map { it.target })
        assertEquals(JarvisTiles.railNav.map { it.label }, JarvisMenu.railEntries.map { it.label }) // one list (Kern)
        assertEquals(listOf("home", "chat"), JarvisMenu.railEntries.filter { it.kind == JarvisMenu.Kind.AREA }.map { it.target })
    }

    @Test fun everyRailAndTileTargetOpensAnExistingScreen() {
        val expected = mapOf("home" to Slot.BRAND, "chat" to Slot.LABEL_THINKING, "memory" to Slot.ACTION_MEMORY,
            "logic" to Slot.MODEL, "research" to Slot.LABEL_ANALYSING, "agents" to Slot.ACTION_AGENTS,
            "tasks" to Slot.ACTION_TASK, "web" to Slot.LABEL_CONNECTED, "learning" to Slot.LABEL_LEARNING,
            "device" to Slot.ACTION_DEVICE, "system" to Slot.SYSTEM_STATUS)
        JarvisMenu.railEntries.forEach { e ->
            val (nav, opened, _) = setup()
            nav.navigate(e.target)
            assertEquals(e.target, listOf(expected.getValue(e.target)), opened)
        }
        // all existing JarvisTile targets keep Kern's routes
        JarvisTiles.reference.forEach { t -> assertEquals(JarvisShellContent.routes[t.target], JarvisMenu.routes[t.target]) }
    }

    @Test fun unknownTargetDoesNothing() {
        val (nav, opened, events) = setup()
        nav.navigate("gibt-es-nicht")
        assertTrue(opened.isEmpty()); assertTrue(events.isEmpty()); assertEquals("home", nav.current)
    }

    @Test fun dedicatedDestinationsKeepSelectionAndEventsWithoutOpeningTheObsoleteSlot() {
        val bus = CockpitAnimationBus()
        val events = mutableListOf<CockpitAnimationEvent>()
        bus.subscribe { events += it }
        val selected = mutableListOf<String>()
        val semantic = mutableListOf<String>()
        val slots = mutableListOf<Slot>()
        val nav = JarvisMenuNavigator(CockpitAnimationTriggers(bus) { 42L }, { slots += it },
            onSelected = { selected += it }, openTarget = {
                if (it in setOf("logic", "tasks", "web")) { semantic += it; true } else false
            })
        listOf("logic", "tasks", "web", "memory", "unknown").forEach(nav::navigate)
        assertEquals(listOf("logic", "tasks", "web"), semantic)
        assertEquals(listOf(Slot.ACTION_MEMORY), slots)
        assertEquals(listOf("logic", "tasks", "web", "memory"), selected)
        assertEquals(selected, events.filterIsInstance<CockpitAnimationEvent.AreaChanged>().map { it.toTarget })
        assertEquals("memory", nav.current)
    }

    @Test fun areaToAreaIsPatternBSceneIsPatternA() {
        val (nav, _, events) = setup()
        nav.navigate("chat"); nav.navigate("memory"); nav.navigate("home"); nav.navigate("home")
        val changes = events.filterIsInstance<CockpitAnimationEvent.AreaChanged>()
        assertEquals(listOf(TransitionPattern.B_FLY_IN, TransitionPattern.A_DEPTH, TransitionPattern.A_DEPTH), changes.map { it.pattern })
        assertEquals(listOf("home" to "chat", "chat" to "memory", "memory" to "home"), changes.map { it.fromTarget to it.toTarget })
        assertTrue(changes.all { it.turnId == NAV_TURN && it.atMillis == 42L })
    }

    @Test fun barActionsLeadToRealPathsOtherwiseQuelleFehlt() {
        val (nav, opened, _) = setup()
        val actions = JarvisCockpitActions(nav)
        JarvisMenu.actionBar.forEach { (a, _) -> assertTrue(a.name, actions.availability(a).enabled); assertNull(actions.availability(a).reason) }
        assertEquals(listOf("Denken", "Recherchieren", "Lernen", "Entwickeln", "Ausführen", "Überprüfen"), JarvisMenu.actionBar.map { it.second })
        for (a in listOf(CockpitAction.TOOL, CockpitAction.WORKER)) {
            assertFalse(actions.availability(a).enabled); assertEquals("Quelle fehlt", actions.availability(a).reason)
        }
        actions.perform(CockpitAction.RESEARCH, "t1"); actions.perform(CockpitAction.VERIFY, "t1")
        assertEquals(listOf(Slot.LABEL_ANALYSING, Slot.ACTION_VERIFY), opened)
    }

    @Test fun missingPathIsDisabledAndPerformIsNoOp() {
        val calls = mutableListOf<String>()
        val nav = object : JarvisNavigator { override fun navigate(target: String) { calls += target } }
        val actions = JarvisCockpitActions(nav) { it != "learning" }
        assertEquals("Quelle fehlt", actions.availability(CockpitAction.LEARN).reason)
        actions.perform(CockpitAction.LEARN, "t"); actions.perform(CockpitAction.THINK, "t")
        assertEquals(listOf("chat"), calls)
    }

    @Test fun railSpecFold7BothOrientationsCoverAndPhoneBottomBar() {
        for ((w, h) in listOf(884f to 760f, 760f to 884f, 840f to 649f, 832f to 700f, 673f to 841f)) {
            assertEquals(JarvisMenuRail.Mode.SIDE_RAIL, JarvisMenuRail.mode(w, h))
            val rail = JarvisMenuRail.widthDp(w, h)
            // ref5: rail column 191 px of 1301 px screen width = 14.7 %; never narrower than margin + 108 dp button
            assertEquals("$w×$h", (w * .147f).coerceIn(122f, 168f), rail, .001f)
            assertTrue(rail - JarvisMenuRail.MARGIN >= JarvisDesign.Styles.button.let { 108f })
            val rows = JarvisMenuRail.rows(h)
            assertEquals(11, rows.size); assertTrue(rows.all { it.second - it.first >= 48f })
            assertTrue("all 11 entries visible without scrolling at $w×$h", JarvisMenuRail.minHeightDp() <= h)
        }
        assertEquals(.147f, 832f * JarvisMenuRail.WIDTH_FRACTION / 832f, 1e-6f)
        for ((w, h) in listOf(412f to 915f, 360f to 780f, 915f to 412f)) { // phone, cover, phone landscape
            assertEquals(JarvisMenuRail.Mode.BOTTOM_BAR, JarvisMenuRail.mode(w, h)); assertEquals(0f, JarvisMenuRail.widthDp(w, h))
        }
        // Owner-requested dense surfaces: first top retained, each visible button fills its independent hit row.
        val first = JarvisMenuRail.button(122f, JarvisMenuRail.rows(700f)[0])
        assertEquals(98f, first[1], .001f); assertEquals(48f, first[3] - first[1], .001f)
        assertEquals(3, JarvisMenuRail.hit(JarvisMenuRail.FIRST_ROW + 3 * 48f + 1f, 2000f))
        assertEquals(-1, JarvisMenuRail.hit(JarvisMenuRail.FIRST_ROW - 1f, 2000f)) // device status block is not a button
    }

    @Test fun deviceStatusShowsOnlyRealValuesOtherwiseDash() {
        val texts = mutableListOf<String>()
        val p = TextPainter(texts)
        JarvisMenuRailElement(device = JarvisDeviceStatus.EMPTY).draw(p, 122f, 700f, ContinuumDashboardState())
        assertEquals(listOf("—", "—", "—"), texts.take(3))
        assertTrue("no ref5 numbers", texts.none { it.contains("12:47") || it.contains("Jan 29") || it.contains("Fold7") })
        texts.clear()
        JarvisMenuRailElement(device = JarvisDeviceStatus("SM-F966B", "09:41", "Mi., 4. Nov.")).draw(p, 122f, 700f, ContinuumDashboardState())
        assertEquals(listOf("SM-F966B", "09:41", "Mi., 4. Nov."), texts.take(3))
        assertEquals(JarvisMenu.railEntries.size, texts.size - 3) // one label per button
    }

    @Test fun theExistingOtherPaneRailFitsTheSharedNavigationLabels() {
        val p = TextPainter(mutableListOf(), charWidth = 6f)
        JarvisMenu.railEntries.forEach { e ->
            val label = JarvisMenu.fitLabel(p, e, 108f)
            val st = JarvisDesign.Styles.button
            assertTrue("${e.label} → $label", p.measureText(label, JarvisDesign.Type.nav) <= 108f - st.padH - st.iconSize - st.iconGap - JarvisMenu.LABEL_END)
        }
        assertEquals("Gerät", JarvisMenu.fitLabel(p, JarvisMenu.railEntries.first { it.target == "device" }, 108f))
        assertEquals("Memory", JarvisMenu.fitLabel(p, JarvisMenu.railEntries.first { it.target == "memory" }, 108f)) // no "…"
    }

    @Test fun everyRailEntryHasItsRef5Icon() {
        JarvisMenu.railEntries.forEach { assertTrue(it.target, JarvisRailIcons.has(it.target)) }
        assertFalse(JarvisRailIcons.has("gibt-es-nicht"))
    }

    /** Records drawn text; fixed advance per char. */
    private class TextPainter(val texts: MutableList<String>, val charWidth: Float = 5f) : ContinuumPainter {
        override fun save() {}
        override fun restore() {}
        override fun translate(dx: Float, dy: Float) {}
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {}
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) {}
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) { texts += text }
        override fun measureText(text: String, style: TextStyle): Float = text.length * charWidth
        override fun capHeight(style: TextStyle): Float = style.size * .7f
    }

    @Test fun railUsesDesignTokensOnly() {
        // Welten-Optik's ref5 tokens (#479 @69b76b2f): button 108 x 32 dp, fill #041521, active #012C3C / rim #32D6ED, nav 11 sp
        val st = JarvisDesign.Styles.button
        assertEquals(32f, st.height, 0f); assertEquals(Argb.rgb(0x04, 0x15, 0x21), st.fill)
        assertEquals(Argb.rgb(0x01, 0x2C, 0x3C), JarvisDesign.Styles.buttonActive.fill)
        assertEquals(Argb.rgb(0x32, 0xD6, 0xED), JarvisDesign.Styles.buttonActive.rim)
        assertEquals(13f, JarvisDesign.Type.nav.size, 0f)
        assertEquals(JarvisMenuRail.MARGIN + 108f, JarvisMenuRail.MIN_WIDTH, 0f)
    }
}
