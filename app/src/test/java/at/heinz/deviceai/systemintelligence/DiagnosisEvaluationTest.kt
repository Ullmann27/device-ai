package at.heinz.deviceai.systemintelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisEvaluationTest {
    private fun sample() = SystemDiagnosis(1_000, 37, 1_000L shl 20, 700L shl 20, 8L shl 30, 3L shl 30, false,
        460L shl 20, 80, 33.0, false, 0, 10.0, 250)
    private fun preview(complete: Boolean = true) = OwnCachePreview("p1", "d1",
        listOf(OwnCacheEntry("a".repeat(32), 120, 10), OwnCacheEntry("b".repeat(32), 130, 20)), 250, 3, complete)

    private fun text(evaluation: DiagnosisEvaluation) = evaluation.render()

    @Test fun ownerTargetSentencePathMeasuresAndPreviewsButChangesNothing() {
        val owner = "Continuum, untersuche bitte mein Handy. Sag mir, was Speicher verbraucht, warum es heiß wird " +
            "und was wir bereinigen könnten. Aber lösch noch nichts."
        assertTrue(SystemIntelligenceCommands.isDiagnosisRequest(owner))
        assertTrue(SystemIntelligenceCommands.diagnosisIncludesOwnCachePreview(owner))
        val evaluation = DiagnosisEvaluation.evaluate(sample(), preview())
        assertTrue(evaluation.performedChanges.isEmpty())
        assertTrue(evaluation.measuredResults.isEmpty())
        assertTrue(evaluation.approvedActions.isEmpty())
        assertEquals(listOf("a".repeat(32) to 120L, "b".repeat(32) to 130L),
            evaluation.cleanupCandidates.map { it.entryId to it.bytes })
        // The preview's cleanup is offered for approval, never executed.
        assertTrue(evaluation.requiresApproval.any { it.contains("250 Byte") && it.contains("Freigabe") })
        val rendered = text(evaluation)
        listOf("1 · Messwerte", "2 · Auffälligkeiten", "3 · Hypothesen", "4 · Bereinigungskandidaten",
            "5 · Erst nach Freigabe", "6 · Durchgeführte Änderungen").forEach { assertTrue(it, rendered.contains(it)) }
        assertTrue(rendered.contains("Keine Änderung durchgeführt"))
        assertTrue(rendered.contains("Fremde App-Caches"))
    }

    @Test fun thermalStatusThreeIsAnAnomalyNeverAFoundCause() {
        for (status in 3..6) {
            val evaluation = DiagnosisEvaluation.evaluate(sample().copy(thermalStatus = status))
            assertTrue("$status", evaluation.anomalies.any { it.contains("Thermalstatus $status") })
            assertTrue("$status", evaluation.hypotheses.isNotEmpty())
            assertTrue("$status", evaluation.hypotheses.all { it.missingCrossCheck.isNotBlank() })
            val rendered = text(evaluation).lowercase()
            assertFalse(rendered, rendered.contains("ursache gefunden"))
            assertFalse(rendered, rendered.contains("ursache ist"))
            assertTrue(rendered, rendered.contains("ursache nicht bestimmt"))
        }
        val calm = DiagnosisEvaluation.evaluate(sample().copy(thermalStatus = 2))
        assertTrue(calm.anomalies.none { it.contains("Thermalstatus") })
    }

    @Test fun missingValuesAreNotAvailableAndNeverZeroOrHealthy() {
        val unknown = sample().copy(storageAvailableBytes = null, storageTotalBytes = null, ramAvailableBytes = null,
            ownPssBytes = null, batteryPercent = null, batteryTemperatureC = null, charging = null,
            thermalStatus = null, ownCpuCorePercent = null, lowMemory = null,
            unavailable = setOf("Datenträger", "Akkustand"))
        val evaluation = DiagnosisEvaluation.evaluate(unknown)
        listOf("Datenträger belegt", "Datenträger verfügbar", "RAM verfügbar", "Continuum-PSS", "Akku",
            "Akkutemperatur", "Android-Thermalstatus", "App-CPU", "CPU-Temperatur", "Fremde App-Energie")
            .forEach { label ->
                assertTrue(label, evaluation.measured.any { it.startsWith("$label: nicht verfügbar") })
            }
        assertTrue(evaluation.anomalies.isEmpty())
        assertTrue(evaluation.hypotheses.any { it.statement.contains("Wärmeursache unbekannt") })
        assertFalse(text(evaluation).contains("Stufe 0"))
        assertFalse(text(evaluation).contains(" 0.0 MiB"))
    }

    @Test fun measuredDeviationsAreSeparatedFromHypotheses() {
        val stressed = sample().copy(storageAvailableBytes = 50L shl 20, lowMemory = true, ownCpuCorePercent = 220.0,
            charging = true, thermalStatus = 4)
        val evaluation = DiagnosisEvaluation.evaluate(stressed)
        assertTrue(evaluation.anomalies.any { it.contains("10 %") })
        assertTrue(evaluation.anomalies.any { it.contains("Speicherdruck") })
        assertTrue(evaluation.anomalies.any { it.contains("220.0 %") })
        // A hypothesis is never repeated as a measured anomaly and always names its missing cross-check.
        assertTrue(evaluation.hypotheses.any { it.statement.contains("Laden") && it.missingCrossCheck.contains("ohne Ladekabel") })
        assertTrue(evaluation.hypotheses.all { h -> evaluation.anomalies.none { it == h.statement } })
        assertTrue(evaluation.requiresApproval.any { it.contains("Lokale Inferenz pausieren") })
        assertTrue(evaluation.performedChanges.isEmpty())
        assertTrue(text(evaluation).contains("Kein Geschwindigkeitsgewinn belegt"))
    }

    @Test fun usedStorageIsMeasuredWhileForeignAppShareStaysUnavailable() {
        val evaluation = DiagnosisEvaluation.evaluate(sample())
        assertTrue(evaluation.measured.contains("Datenträger belegt: 300.0 MiB von 1000.0 MiB (30 %)"))
        assertTrue(evaluation.measured.any { it.startsWith("Speicher je fremder App: nicht verfügbar") })
        assertTrue(evaluation.hypotheses.any { it.missingCrossCheck.contains("Android-Speicheraufschlüsselung") })
    }

    @Test fun cleanupCandidatesComeOnlyFromACompletePreviewOfOwnEntries() {
        val none = DiagnosisEvaluation.evaluate(sample())
        assertTrue(none.cleanupCandidates.isEmpty())
        assertTrue(none.cleanupNotes.any { it.contains("Keine Cache-Vorschau") })
        assertTrue(none.cleanupNotes.any { it.contains("Fremde App-Caches") && it.contains("nicht zugänglich") })
        assertTrue(none.requiresApproval.none { it.contains("Diagnoseberichte entfernen") })

        val partial = DiagnosisEvaluation.evaluate(sample(), preview(complete = false))
        assertTrue(partial.cleanupNotes.any { it.contains("unvollständig") })
        assertTrue(partial.requiresApproval.none { it.contains("Diagnoseberichte entfernen") })

        val empty = DiagnosisEvaluation.evaluate(sample(), OwnCachePreview("p", "d", emptyList(), 0, 2, true))
        assertTrue(empty.cleanupCandidates.isEmpty())
        assertTrue(empty.cleanupNotes.any { it.contains("2 geschützte Einträge") })
        assertTrue(empty.requiresApproval.none { it.contains("Diagnoseberichte entfernen") })
    }

    @Test fun previewFlagPassesTheExistingToolGateSchemaAndStaysReadOnly() {
        val flag = mapOf(SystemIntelligenceCommands.INCLUDE_CACHE_PREVIEW to "true")
        assertEquals(null, at.heinz.deviceai.ToolProposal(SystemIntelligenceCommands.DIAGNOSE, flag).validate())
        assertEquals(at.heinz.deviceai.Risk.READ_ONLY, at.heinz.deviceai.ToolRegistry.risk(SystemIntelligenceCommands.DIAGNOSE))
        assertTrue(at.heinz.deviceai.ToolProposal(SystemIntelligenceCommands.DIAGNOSE,
            mapOf(SystemIntelligenceCommands.INCLUDE_CACHE_PREVIEW to "ja")).validate() != null)
        assertTrue(at.heinz.deviceai.ToolProposal(SystemIntelligenceCommands.DIAGNOSE,
            mapOf("cleanup" to "true")).validate() != null)
    }

    @Test fun onlyAnApprovedCleanupResultFillsPerformedChangesWithMeasuredResults() {
        val done = DiagnosisEvaluation.evaluate(sample(), approvedCleanup =
            OwnCacheCleanupResult("COMPLETED", removedFiles = 2, logicalBytesRemoved = 250, availableBytesDelta = 4096,
                undoId = "u"))
        assertEquals(1, done.approvedActions.size)
        assertTrue(done.performedChanges.single().contains("2 eigene Diagnoseberichte"))
        assertTrue(done.measuredResults.any { it.contains("250 Byte") })
        assertTrue(done.measuredResults.any { it.contains("4096 Byte") })
        assertTrue(done.measuredResults.any { it.contains("Rücknahme") })

        val unknownDelta = DiagnosisEvaluation.evaluate(sample(), approvedCleanup =
            OwnCacheCleanupResult("COMPLETED", removedFiles = 1, logicalBytesRemoved = 120))
        assertTrue(unknownDelta.measuredResults.any { it.contains("Platzänderung: nicht verfügbar") })

        val failed = DiagnosisEvaluation.evaluate(sample(), approvedCleanup = OwnCacheCleanupResult("PREVIEW_EXPIRED"))
        assertTrue(failed.performedChanges.isEmpty())
        assertTrue(failed.measuredResults.any { it.contains("PREVIEW_EXPIRED") })
        assertTrue(text(failed).contains("Keine Änderung durchgeführt"))
    }
}
