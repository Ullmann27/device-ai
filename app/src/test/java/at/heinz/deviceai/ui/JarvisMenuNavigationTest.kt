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

    @Test fun railHasTheEightSpecEntriesInOrder() {
        assertEquals(listOf("Übersicht", "Chat", "Agenten", "Gedächtnis", "Forschung", "Entwicklung", "System", "Live-Status"),
            JarvisMenu.railEntries.map { it.label })
    }

    @Test fun everyRailAndTileTargetOpensAnExistingScreen() {
        val expected = mapOf("home" to Slot.BRAND, "chat" to Slot.LABEL_THINKING, "agents" to Slot.ACTION_AGENTS,
            "memory" to Slot.ACTION_MEMORY, "research" to Slot.LABEL_ANALYSING, "development" to Slot.LABEL_DEVELOPING,
            "system" to Slot.SYSTEM_STATUS, "live" to Slot.SYSTEM_ACTIVITY)
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
        for ((w, h) in listOf(884f to 760f, 760f to 884f, 840f to 649f, 673f to 841f)) {
            assertEquals(JarvisMenuRail.Mode.SIDE_RAIL, JarvisMenuRail.mode(w, h))
            val rail = JarvisMenuRail.widthDp(w, h)
            assertEquals("$w×$h", (w * .20f).coerceIn(132f, 200f), rail, .001f)
            assertTrue(rail / w in .15f..0.21f)
            val rows = JarvisMenuRail.rows(h - 120f)
            assertEquals(8, rows.size); assertTrue(rows.all { it.second - it.first >= 48f })
        }
        for ((w, h) in listOf(412f to 915f, 360f to 780f, 915f to 412f)) { // phone, cover, phone landscape
            assertEquals(JarvisMenuRail.Mode.BOTTOM_BAR, JarvisMenuRail.mode(w, h)); assertEquals(0f, JarvisMenuRail.widthDp(w, h))
        }
        assertEquals(3, JarvisMenuRail.hit(10f + 3 * 64f + 1f, 2000f))
    }

    @Test fun railUsesDesignTokensOnly() {
        assertEquals(Argb.of(128, 0x0A, 0x11, 0x28), JarvisDesign.Colors.surface)
        assertEquals(Argb.rgb(0, 255, 255), JarvisDesign.Colors.cyan)
    }
}
