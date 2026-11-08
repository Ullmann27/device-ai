package at.heinz.deviceai.ui

import at.heinz.deviceai.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The universal presentation adapter covers the real tool registry and the cockpit centres; it carries no data. */
class JarvisActionScenesTest {
    @Test fun everyRealToolHasAScene() {
        val unmapped = ToolRegistry.all().map { it.name }.filter { JarvisActionScenes.toolKind(it) == JarvisActionScenes.Kind.GENERIC }
        assertEquals("tools without a cockpit scene", emptyList<String>(), unmapped)
        assertEquals(JarvisActionScenes.Kind.GENERIC, JarvisActionScenes.toolKind("future_tool"))
        assertEquals(JarvisActionScenes.Origin.BesideFace, JarvisActionScenes.forTool("launch_app").origin)
    }

    @Test fun workspaceFootprintsMatchAirbusDeckIntent() {
        assertEquals(JarvisActionScenes.WorkspaceFootprint.MEDIUM_TERMINAL,
            JarvisActionScenes.forTool("launch_app").footprint)
        assertEquals(JarvisActionScenes.WorkspaceFootprint.MEDIUM_TERMINAL,
            JarvisActionScenes.forDocument().footprint)
        assertEquals(JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY,
            JarvisActionScenes.forCentre("Logik und Planung")!!.footprint)
        assertEquals(JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY,
            JarvisActionScenes.forCentre("Entwicklungszentrum")!!.footprint)
    }

    @Test fun centresEmergeFromTheTouchedTileOrBesideTheFace() {
        assertEquals(JarvisActionScenes.Origin.Tile("system"), JarvisActionScenes.forCentre("Systemstatus")!!.origin)
        assertEquals("Gerät opens Systemstatus from its own tile", JarvisActionScenes.Origin.Tile("device"),
            JarvisActionScenes.forCentre("Systemstatus", touchedTarget = "device")!!.origin)
        assertEquals(JarvisActionScenes.Origin.BesideFace, JarvisActionScenes.forCentre("Entwicklungszentrum")!!.origin)
        for (title in listOf("Gedächtniszentrum", "Logik und Planung", "Web und Quellen", "Lernzentrum"))
            assertTrue(title, JarvisActionScenes.forCentre(title)!!.origin is JarvisActionScenes.Origin.Tile)
        assertNull("unknown centres keep their dialog", JarvisActionScenes.forCentre("Modellzentrum"))
    }
}
