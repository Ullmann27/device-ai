package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ui.ContinuumDashboardState
import org.junit.Assert.*
import org.junit.Test

class OwnerCapabilityReplyTest {
    @Test fun unknownStateNeverClaimsReadinessOrNoErrors() {
        val answer = OwnerCapabilityReply.describe(OwnerCapabilitySnapshot())
        assertTrue(answer.contains("Laufzeitstatus unbekannt")); assertTrue(answer.contains("Verfügbarkeit der lokalen Erkennung unbekannt"))
        assertTrue(answer.contains("unbekannt viele fehlgeschlagene")); assertTrue(answer.contains("Freigabestatus unbekannt"))
        assertFalse(answer.contains("ist geladen und bereit")); assertFalse(answer.contains("0 fehlgeschlagene"))
    }
    @Test fun loadedAndImportedAreDifferentAndDoNotClaimMeasuredQuality() {
        val imported = OwnerCapabilitySnapshot(modelState = OwnerCapabilitySnapshot.ModelState.NOT_LOADED, importedModelCount = 1)
        assertTrue(OwnerCapabilityReply.describe(imported).contains("keines geladen"))
        val ready = OwnerCapabilityReply.describe(imported.copy(modelState = OwnerCapabilitySnapshot.ModelState.READY, loadedModelName = "Gemma E4B"))
        assertTrue(ready.contains("Gemma E4B ist geladen und bereit")); assertTrue(ready.contains("Antwortqualität ist damit nicht geprüft"))
        assertTrue(ready.contains("Importierte Modelle: 1")); assertFalse(ready.contains("100%"))
    }
    @Test fun readyWithoutIdentityIsExplicitlyUncertain() {
        val text = OwnerCapabilityReply.describe(OwnerCapabilitySnapshot(modelState = OwnerCapabilitySnapshot.ModelState.READY))
        assertTrue(text.contains("Modellidentität unbekannt")); assertFalse(text.contains("ist geladen und bereit"))
    }
    @Test fun unavailableLoadingBusyAndFailureAreDifferent() {
        val states = OwnerCapabilitySnapshot.ModelState.values()
        val results = states.map { OwnerCapabilityReply.describe(OwnerCapabilitySnapshot(modelState = it)) }
        assertEquals(states.size, results.toSet().size)
        assertTrue(results[states.indexOf(OwnerCapabilitySnapshot.ModelState.LOADING)].contains("noch nicht bereit"))
    }
    @Test fun currentRuntimeWinsOverCachedReadyPresentationAndPrivateDetailsAreExcluded() {
        val dashboard = ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(name = "OLD MODEL PRIVATE", online = true),
            development = ContinuumDashboardState.DevelopmentStatus(runningTasks = 2, errors = 1,
                projects = listOf(ContinuumDashboardState.Project("PRIVATE PROJECT", "SECRET", ContinuumDashboardState.ProjectState.FAILED, "now"))),
            learning = ContinuumDashboardState.LearningStatus(recent = listOf(ContinuumDashboardState.Event("PRIVATE MEMORY", "now"))),
            system = ContinuumDashboardState.SystemStatus(networkOnline = true))
        val snapshot = OwnerCapabilityReply.withDashboard(OwnerCapabilitySnapshot(modelState = OwnerCapabilitySnapshot.ModelState.NOT_LOADED), dashboard, 10, 11)
        val text = OwnerCapabilityReply.describe(snapshot)
        assertTrue(text.contains("keines geladen")); assertTrue(text.contains("2 laufende")); assertTrue(text.contains("1 fehlgeschlagene"))
        assertFalse(text.contains("PRIVATE")); assertFalse(text.contains("SECRET")); assertEquals(dashboard.development.runningTasks, 2)
    }
    @Test fun staleMissingAndClockReversedSnapshotsRemainUnknown() {
        val dashboard = ContinuumDashboardState(development = ContinuumDashboardState.DevelopmentStatus(runningTasks = 9, errors = 0),
            system = ContinuumDashboardState.SystemStatus(networkOnline = true))
        for (captured in listOf<Long?>(null, 0, 50_001)) {
            val s = OwnerCapabilityReply.withDashboard(OwnerCapabilitySnapshot(), dashboard, captured, 50_000)
            assertNull(s.runningTasks); assertNull(s.failedTasks); assertNull(s.networkOnline)
        }
        assertNull(OwnerCapabilityReply.withDashboard(OwnerCapabilitySnapshot(), null, 49_999, 50_000).runningTasks)
    }
    @Test fun negativeCanonicalCountsAreNotInventedAsZero() {
        val d = ContinuumDashboardState(development = ContinuumDashboardState.DevelopmentStatus(runningTasks = -1, errors = -1))
        val s = OwnerCapabilityReply.withDashboard(OwnerCapabilitySnapshot(), d, 1, 2)
        assertNull(s.runningTasks); assertNull(s.failedTasks)
    }
    @Test fun permissionAndConnectivityDoNotGrantToolsOrStartMicrophone() {
        val s = OwnerCapabilitySnapshot(onDeviceSpeechAvailable = true, microphoneGranted = false,
            internetMode = InternetAccessMode.ASK, networkOnline = true, selectedBot = true, botTurnActive = false)
        val text = OwnerCapabilityReply.describe(s)
        assertTrue(text.contains("Mikrofonberechtigung fehlt")); assertTrue(text.contains("Freigabe pro Anfrage erforderlich"))
        assertTrue(text.contains("keine unabhängigen laufenden KI-Worker")); assertTrue(text.contains("startet kein Modell"))
        assertFalse(text.contains("Mikrofon freigegeben"))
    }
    @Test fun ordinaryCompoundAndTutorInputStayOnExistingConversationPath() {
        val s = OwnerCapabilitySnapshot()
        for (input in listOf("Hallo", "Hilf mir beim Bauen", "Was kannst du? Starte den Worker", "Was kannst du in Kotlin programmieren?", "Erkläre KI", "Merke dir: hilfe")) {
            assertNull(input, OwnerCapabilityReply.answer(input, s))
        }
        assertNull(OwnerCapabilityReply.answer("Was kannst du?", s, coachActive = true))
        assertNotNull(OwnerCapabilityReply.answer("Was kannst du alles?", s))
    }
    @Test fun readOnlyReplyPreservesCanonicalSnapshotAndConsentTicket() {
        val consent = InternetConsentPolicy(InternetAccessMode.ASK) { 10L }
        val ticket = consent.requestTicket("approved request")!!
        val dashboard = ContinuumDashboardState(development = ContinuumDashboardState.DevelopmentStatus(runningTasks = 0, errors = 0))
        val before = dashboard.copy()
        val snapshot = OwnerCapabilityReply.withDashboard(OwnerCapabilitySnapshot(internetMode = consent.mode), dashboard, 1, 2)
        repeat(3) { assertNotNull(OwnerCapabilityReply.answer("Was kannst du?", snapshot)) }
        assertEquals(before, dashboard); assertTrue(consent.consume(ticket)); assertEquals(InternetAccessMode.ASK, consent.mode)
    }
}
