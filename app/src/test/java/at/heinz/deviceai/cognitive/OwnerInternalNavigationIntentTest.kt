package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ContinuumWorkAreaNavigationSpec
import at.heinz.deviceai.ui.JarvisMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #639: natural requests for internal cockpit screens are navigation, never an app or file lookup. */
class OwnerInternalNavigationIntentTest {
    private fun route(utterance: String, name: String? = "Jarvis"): String? =
        OwnerInternalNavigationIntent.resolve(utterance, name)?.target

    @Test fun ownerCommandFromIssue639OpensMemoryCentre() {
        assertEquals("memory", route("Jarvis, öffne das Gedächtniszentrum."))
        assertEquals("memory", route("öffne das Gedächtniszentrum."))
    }

    @Test fun freeFormAndAsrVariantsStillReachTheSameScreen() {
        for (u in listOf(
            "Jarvis öffne das Gedächtnis Zentrum",          // ASR splits the compound
            "Jarvis, öffne mir bitte das Gedächtnis-Zentrum", // hyphenated, courtesy words
            "öffne das gedaechtniszentrum",                 // no umlauts
            "Öffne das Gedächtniszenrum",                    // typo
            "Zeig mir das Gedächtnis",
            "Kannst du bitte das Gedächtniszentrum öffnen?",
            "Ich möchte ins Gedächtniszentrum",
            "Mach das Gedächtniszentrum auf",
            "Geh zum Gedächtnis",
            "Wechsle in die Gedächtnis-Ansicht",
            "Gedächtniszentrum",
            "Hey Jarvis öffne mal das Gedächtniszentrum bitte",
        )) assertEquals(u, "memory", route(u))
    }

    @Test fun logicAndPlanningRouteDespiteConjunction() {
        for (u in listOf(
            "Jarvis, öffne Logik und Planung",
            "Öffne Denken und Planen",
            "Zeig mir Logik & Planung",
            "öffne das Logikzentrum",
            "Jarvis öffne die Planung",
            "Öffne Logik und Blanung",   // ASR confusion
        )) assertEquals(u, "logic", route(u))
    }

    @Test fun everyRailAndCategoryDestinationIsReachableBySpeech() {
        val expected = mapOf(
            "öffne die Agenten" to "agents", "zeig mir die Assistenten" to "agents",
            "öffne die Aufgaben" to "tasks", "öffne die Entwicklungsaufgaben" to "tasks",
            "öffne die Recherche" to "research", "öffne die Forschung" to "research",
            "öffne das Lernzentrum" to "learning", "öffne Lernen" to "learning",
            "öffne die Gerätesteuerung" to "device",
            "öffne den Systemstatus" to "system",
            "öffne das Entwicklungszentrum" to "development", "öffne die Werkzeuge" to "development",
            "öffne die Modellverwaltung" to "model-management", "öffne das lokale Modell" to "model-management",
            "öffne die Personalisierung" to "settings", "öffne die Continuum Einstellungen" to "settings",
            "öffne den Datenschutz" to "privacy",
            "öffne die Diagnose" to "notifications",
            "öffne das Continuum Brain" to "brain", "zeig mir die Gehirnkugel" to "brain",
            "öffne den Handy-Builder" to "mobile-builder",
            "geh zur Startseite" to "home", "öffne das Cockpit" to "home",
            "öffne den Chat" to "chat", "wechsle zum Gespräch" to "chat",
            "öffne das Internet" to "web",
        )
        for ((u, target) in expected) assertEquals(u, target, route(u))
        // Every target the resolver can return exists as a real route in the cockpit.
        val known = JarvisMenu.routes.keys + (ContinuumWorkAreaNavigationSpec.systemEntries +
            ContinuumWorkAreaNavigationSpec.toolEntries + ContinuumWorkAreaNavigationSpec.moreEntries).map { it.target }
        for (target in OwnerInternalNavigationIntent.targets) assertTrue(target, target in known)
    }

    @Test fun fileOrPdfRequestsStayFileTasks() {
        for (u in listOf(
            "Öffne die Datei Gedächtniszentrum.pdf",
            "Jarvis, öffne Gedächtniszentrum.pdf",
            "öffne das Dokument Gedächtniszentrum",
            "Öffne den Ordner Gedächtnis",
            "Lies die PDF Logik und Planung",
            "öffne /sdcard/Download/Gedächtniszentrum.pdf",
        )) assertNull(u, route(u))
    }

    @Test fun externalAppsNegationsAndQuestionsAreNotInternalNavigation() {
        for (u in listOf(
            "Jarvis öffne mir Signal", "Öffne WhatsApp", "Mach bitte die App Kalender auf",
            "Öffne nicht das Gedächtniszentrum", "Öffne kein Gedächtnis",
            "Was steht im Gedächtnis?", "Merke dir: Gedächtniszentrum ist mein Lieblingswort",
            "Öffne das Gedächtnis und schreibe meinem Vater",
            "Erkläre mir Logik und Planung",
            "Öffne die Gedächtnis App",   // explicit external app
            "Öffne die Einstellungen",    // Android settings app, not the cockpit screen
            "Öffne Termux", "Öffne Chrome", "Öffne die Kamera", "Öffne Maps", "Öffne den Rechner",
            "Öffne Spotify", "Öffne Telegram", "Öffne die Galerie", "Öffne den Browser", "Öffne Netflix",
            "Öffne den Planer", "Öffne Systemd", "Öffne Memo",
            "öffne", "Gedächt",
        )) assertNull(u, route(u))
    }

    @Test fun matchedLabelIsTheOwnerVisibleName() {
        val hit = checkNotNull(OwnerInternalNavigationIntent.resolve("Jarvis, öffne das Gedächtniszentrum."))
        assertEquals("Gedächtnis", hit.label)
        assertEquals("Denken und Planen",
            checkNotNull(OwnerInternalNavigationIntent.resolve("öffne Logik und Planung")).label)
    }

    @Test fun explainsWhatHappenedWithoutModelOrWeb() {
        val hit = checkNotNull(OwnerInternalNavigationIntent.resolve("öffne das Gedächtniszentrum"))
        assertTrue(hit.activityLine.contains("NAVIGATION"))
        assertTrue(hit.activityLine.contains("Gedächtnis"))
        assertTrue(hit.activityLine.contains("kein Web oder Modellaufruf"))
    }
}
