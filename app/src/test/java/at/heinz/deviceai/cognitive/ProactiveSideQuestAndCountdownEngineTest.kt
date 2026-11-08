package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProactiveSideQuestAndCountdownEngineTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test
    fun missionCountdownFormatsGermanTenthsAndStopsCleanly() {
        val engine = ProactiveSideQuestAndCountdownEngine(zone)
        engine.startMissionCountdown("turn-42", targetBudgetMillis = 50_000L, nowNanos = 0L)

        val snap = engine.stopMissionCountdown("turn-42", nowNanos = 49_700_000_000L)
        assertNotNull(snap)
        assertEquals("49,7", snap!!.formattedSecondsGerman)
        assertEquals(49_700L, snap.elapsedMillis)
        assertEquals(300L, snap.remainingBudgetMillis)
        assertTrue(snap.finishedUnderBudget)
        assertNull(engine.sampleMissionTimer("turn-42", nowNanos = 49_800_000_000L))
    }

    @Test
    fun ownerProvidedTodayEventTriggersOnceThenCooldownBlocksIt() {
        val now = LocalDate.of(2026, 11, 8).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val engine = ProactiveSideQuestAndCountdownEngine(zone, ownerAddress = "Heinz")
        val dinner = record(
            id = "mem-dinner",
            content = "Heute Abend um 19 Uhr Abendessen mit meiner Frau.",
            updatedAt = now,
            provenance = ProvenanceType.USER_PROVIDED
        )

        val first = engine.findTimelySideQuestOrNull(listOf(dinner), nowMillis = now)
        assertNotNull(first)
        assertEquals("mem-dinner", first!!.memoryId)
        assertTrue(first.spokenInitiativeLine.contains("Abendessen"))
        assertTrue(first.spokenInitiativeLine.startsWith("Ach Heinz"))

        assertNull(engine.findTimelySideQuestOrNull(listOf(dinner), nowMillis = now + 60_000L))
    }

    @Test
    fun staleRelativeTodayAndUntrustedOrContradictedMemoryNeverSpeak() {
        val now = LocalDate.of(2026, 11, 8).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = now - 24L * 60L * 60L * 1000L

        val staleToday = record(
            id = "old",
            content = "Heute Abend Termin beim Zahnarzt.",
            updatedAt = yesterday,
            provenance = ProvenanceType.USER_PROVIDED
        )
        val generated = record(
            id = "generated",
            content = "Heute Abend Termin beim Zahnarzt.",
            updatedAt = now,
            provenance = ProvenanceType.MODEL_GENERATED
        )
        val contradicted = record(
            id = "contradicted",
            content = "Heute Abend Termin beim Zahnarzt.",
            updatedAt = now,
            provenance = ProvenanceType.USER_PROVIDED,
            state = ValidationState.CONTRADICTED
        )

        val engine = ProactiveSideQuestAndCountdownEngine(zone)
        assertNull(engine.findTimelySideQuestOrNull(listOf(staleToday, generated, contradicted), nowMillis = now))
    }

    @Test
    fun verifiedMemoryRequiresEvidenceAndCanTriggerForExplicitToday() {
        val now = LocalDate.of(2026, 11, 8).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val verified = record(
            id = "verified",
            content = "Heute Abend Reservierung um 18:30.",
            updatedAt = now,
            provenance = ProvenanceType.EXTERNAL_SOURCE,
            state = ValidationState.VERIFIED,
            evidence = listOf("calendar://event/verified")
        )

        val quest = ProactiveSideQuestAndCountdownEngine(zone)
            .findTimelySideQuestOrNull(listOf(verified), nowMillis = now)
        assertNotNull(quest)
    }

    private fun record(
        id: String,
        content: String,
        updatedAt: Long,
        provenance: ProvenanceType,
        state: ValidationState = ValidationState.UNVERIFIED,
        evidence: List<String> = emptyList()
    ) = MemoryRecord(
        id = id,
        type = MemoryType.EPISODIC,
        content = content,
        provenance = provenance,
        confidence = 0.95,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        tags = listOf("heute", "abend", "termin"),
        validationState = state,
        privacyClass = PrivacyClass.PRIVATE,
        validationEvidenceReferences = evidence
    )
}
