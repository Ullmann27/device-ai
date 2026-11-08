package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileExperienceStoreTest {
    @get:Rule val dir = TemporaryFolder()

    private fun experience(
        id: String,
        request: String = "Prüfe die Surface",
        result: String = "Surface sichtbar",
        completedAt: Long = 2L,
        outcome: ExperienceOutcome = ExperienceOutcome.SUCCESS,
        tool: String = "get_device_info"
    ) = Experience(
        id = id,
        taskType = "agent_task",
        requestSummary = request,
        sourceIds = listOf("receipt:$id"),
        toolIds = listOf(tool),
        planSummary = "receipt first",
        resultSummary = result,
        confidenceBefore = .5,
        confidenceAfter = .9,
        outcome = outcome,
        startedAt = (completedAt - 1).coerceAtLeast(0),
        completedAt = completedAt,
        evaluation = Evaluation(outcome != ExperienceOutcome.FAILURE, if (outcome == ExperienceOutcome.SUCCESS) .9 else .2)
    )

    @Test fun verifiedExperienceSurvivesAStoreReopen() {
        val folder = dir.newFolder("experience")
        FileExperienceStore(folder).store(experience("e-1"))

        val reopened = FileExperienceStore(folder)
        val loaded = reopened.all().single()
        assertEquals("e-1", loaded.id)
        assertEquals(ExperienceOutcome.SUCCESS, loaded.outcome)
        assertEquals(listOf("get_device_info"), loaded.toolIds)
        assertEquals(listOf("receipt:e-1"), loaded.sourceIds)
    }

    @Test fun exactDuplicateIsIdempotentButConflictingIdentityFailsClosed() {
        val folder = dir.newFolder("experience-identity")
        val store = FileExperienceStore(folder)
        val original = experience("same")
        store.store(original)
        store.store(original)
        assertEquals(1, store.all().size)

        assertThrows(IllegalArgumentException::class.java) {
            store.store(experience("same", result = "anderes Ergebnis"))
        }
        assertEquals(original, store.all().single())
    }

    @Test fun sensitiveLiteralIsNeverPersistedAsAgentExperience() {
        val folder = dir.newFolder("experience-sensitive")
        val store = FileExperienceStore(folder)
        assertThrows(IllegalArgumentException::class.java) {
            store.store(experience("secret", request = "Mein API key ist ABC123"))
        }
        assertTrue(store.all().isEmpty())
        assertFalse(File(folder, "agent-experience-v1.bin").exists())
    }

    @Test fun checksumCorruptionFailsClosedInsteadOfReturningPartialHistory() {
        val folder = dir.newFolder("experience-corrupt")
        FileExperienceStore(folder).store(experience("good"))
        val file = File(folder, "agent-experience-v1.bin")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        file.writeBytes(bytes)

        assertThrows(IllegalArgumentException::class.java) {
            FileExperienceStore(folder).all()
        }
    }

    @Test fun retentionIsBoundedAndDropsOldestCertainHistory() {
        val folder = dir.newFolder("experience-bound")
        val store = FileExperienceStore(folder)
        repeat(257) { index ->
            store.store(experience("e-$index", completedAt = index.toLong() + 2L))
        }
        val all = FileExperienceStore(folder).all()
        assertEquals(256, all.size)
        assertTrue(all.none { it.id == "e-0" })
        assertTrue(all.any { it.id == "e-256" })
    }

    @Test fun similarLookupUsesPersistedTaskHistoryAfterRestart() {
        val folder = dir.newFolder("experience-similar")
        FileExperienceStore(folder).apply {
            store(experience("surface", request = "Prüfe schwarze Surface beim Fold"))
            store(experience("thermal", request = "Prüfe hohe Temperatur beim Prefill", tool = "get_storage_info"))
        }
        val reopened = FileExperienceStore(folder)
        val hits = reopened.similar("agent_task", "schwarze Surface prüfen", limit = 1)
        assertEquals(listOf("surface"), hits.map { it.id })
    }
}
