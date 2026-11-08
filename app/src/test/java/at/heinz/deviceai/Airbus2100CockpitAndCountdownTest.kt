package at.heinz.deviceai

import at.heinz.deviceai.avatar3d.AvatarCameraFraming
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.PrivacyClass
import at.heinz.deviceai.cognitive.ProactiveSideQuestAndCountdownEngine
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.ValidationState
import at.heinz.deviceai.ui.JarvisActionScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Airbus2100CockpitAndCountdownTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test
    fun missionProfilesCreateMediumAndFullAvionicsFraming() {
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, .2, .5)

        val mediumPath = AvatarCameraFraming(
            focus = focus,
            aspect = 2.0,
            anchorX = .7,
            closeUp = 1.0,
            profile = AvatarCameraFraming.Profile.MEDIUM_TERMINAL
        )
        val medium = mediumPath.sample(1f, 1f)
        assertEquals(.34, medium.anchorX, 1e-6)
        assertEquals(mediumPath.rest.headScale * .76, medium.headScale, 1e-6)

        val fullPath = AvatarCameraFraming(
            focus = focus,
            aspect = 2.0,
            anchorX = .7,
            closeUp = 1.0,
            profile = AvatarCameraFraming.Profile.FULL_MISSION_BAY
        )
        val full = fullPath.sample(1f, 1f)
        assertEquals(.28, full.anchorX, 1e-6)
        assertEquals(fullPath.rest.headScale * .64, full.headScale, 1e-6)
        assertTrue(full.anchorX < medium.anchorX)
        assertTrue(full.headScale < medium.headScale)
        assertEquals(
            JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY,
            JarvisActionScenes.footprintFor(JarvisActionScenes.Kind.DEVELOPMENT)
        )
    }

    @Test
    fun missionCountdownAndTrustedTodaySideQuestEnforceCooldown() {
        val engine = ProactiveSideQuestAndCountdownEngine(zone, ownerAddress = "Heinz")
        engine.startMissionCountdown("turn-42", targetBudgetMillis = 50_000L, nowNanos = 0L)

        val snap = engine.stopMissionCountdown("turn-42", nowNanos = 49_700_000_000L)
        assertNotNull(snap)
        assertEquals("49,7", snap!!.formattedSecondsGerman)
        assertEquals(49_700L, snap.elapsedMillis)
        assertEquals(300L, snap.remainingBudgetMillis)
        assertTrue(snap.finishedUnderBudget)
        assertNull(engine.sampleMissionTimer("turn-42", nowNanos = 49_800_000_000L))

        val now = LocalDate.of(2026, 11, 8)
            .atTime(17, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val dinner = MemoryRecord(
            id = "mem-dinner-1",
            type = MemoryType.EPISODIC,
            content = "Heute Abend um 19 Uhr Abendessen mit meiner Frau.",
            provenance = ProvenanceType.USER_PROVIDED,
            confidence = .95,
            createdAt = now,
            updatedAt = now,
            tags = listOf("heute", "abend", "termin", "abendessen", "treffen", "reservierung"),
            validationState = ValidationState.UNVERIFIED,
            privacyClass = PrivacyClass.PRIVATE
        )

        val first = engine.findTimelySideQuestOrNull(listOf(dinner), nowMillis = now)
        assertNotNull(first)
        assertEquals("mem-dinner-1", first!!.memoryId)
        assertTrue(first.spokenInitiativeLine.startsWith("Ach Heinz"))
        assertTrue(first.spokenInitiativeLine.contains("Abendessen"))

        assertNull(engine.findTimelySideQuestOrNull(listOf(dinner), nowMillis = now + 60_000L))
    }

    @Test
    fun oldRelativeTodayMemoryCannotFireOnALaterDay() {
        val now = LocalDate.of(2026, 11, 8)
            .atTime(17, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val yesterday = now - 24L * 60L * 60L * 1000L
        val old = MemoryRecord(
            id = "old-today",
            type = MemoryType.EPISODIC,
            content = "Heute Abend Termin und Abendessen.",
            provenance = ProvenanceType.USER_PROVIDED,
            confidence = .95,
            createdAt = yesterday,
            updatedAt = yesterday,
            tags = listOf("heute", "abend", "termin", "abendessen"),
            validationState = ValidationState.UNVERIFIED,
            privacyClass = PrivacyClass.PRIVATE
        )

        assertNull(
            ProactiveSideQuestAndCountdownEngine(zone)
                .findTimelySideQuestOrNull(listOf(old), nowMillis = now)
        )
    }
}
