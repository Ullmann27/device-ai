package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ContinuumCodingPreparationReviewTest {
    private fun task() = ContinuumCodingTask(
        id = "review-entire-coding-task",
        sourceCommit = "a".repeat(40),
        kind = ContinuumCodingTask.Kind.BUGFIX,
        goal = "Untersuche die vollständige Aufgabenbindung: " + "Einzelheit ".repeat(40),
        allowedPaths = listOf("lib/first.mjs", "backend/src/second.js", "src/third.mjs"),
        acceptance = listOf(
            "Originale Funktion bleibt erhalten",
            "Unbekannte Grenzfälle sind unabhängig geprüft",
            "Ein falscher Patch kann keine Freigabe erzeugen"
        ),
        chain = ContinuumCodingTask.Chain.NODE,
        limits = ContinuumCodingTask.Limits(maxModelCalls = 3, maxContextChars = 24000)
    )

    @Test fun fullReviewContainsCompleteTaskScopeAndUnshortenedFingerprint() {
        val task = task()
        assertTrue(task.problems().isEmpty())
        val hash = task.fingerprint()
        val review = ContinuumTypedCodingBridge.preparationReview(
            ContinuumCodingTask.Authorization(task, hash)
        )
        assertNotNull(review)
        val content = review!!
        assertTrue(content.contains(task.id))
        assertTrue(content.contains(task.kind.name))
        assertTrue(content.contains(task.goal))
        assertTrue(content.contains(task.sourceCommit))
        task.allowedPaths.forEach { assertTrue("Missing path: $it", content.contains(it)) }
        task.acceptance.forEach { assertTrue("Missing criterion: $it", content.contains(it)) }
        assertTrue(content.contains(task.chain.wire))
        assertTrue(content.contains("Maximale Modellaufrufe: 3"))
        assertTrue(content.contains("Maximale Kontextzeichen: 24000"))
        assertTrue(content.contains(hash))
        assertTrue(content.contains("keine kryptografische Besitzeridentität"))
    }

    @Test fun changedTaskOrOldFingerprintCannotProduceReview() {
        val task = task()
        val validHash = task.fingerprint()
        assertNull(ContinuumTypedCodingBridge.preparationReview(
            ContinuumCodingTask.Authorization(task.copy(goal = "Verändertes Ziel"), validHash)
        ))
        assertNull(ContinuumTypedCodingBridge.preparationReview(
            ContinuumCodingTask.Authorization(task.copy(sourceCommit = "main"), validHash)
        ))
        assertNull(ContinuumTypedCodingBridge.preparationReview(
            ContinuumCodingTask.Authorization(task, "0".repeat(64))
        ))
    }

    @Test fun reviewingDoesNotModifyTaskFingerprintOrFixedPrepareCommand() {
        val task = task()
        val hash = task.fingerprint()
        val originalJson = task.canonicalJson()
        val authorization = ContinuumCodingTask.Authorization(task, hash)
        val before = ContinuumTypedCodingBridge.commandPrepare(authorization)

        val review = ContinuumTypedCodingBridge.preparationReview(authorization)

        assertNotNull(review)
        assertEquals(review, ContinuumTypedCodingBridge.preparationReview(authorization))
        assertEquals(originalJson, task.canonicalJson())
        assertEquals(hash, task.fingerprint())
        val after = ContinuumTypedCodingBridge.commandPrepare(authorization)
        assertEquals(before.executable, after.executable)
        assertEquals(before.workDir, after.workDir)
        assertEquals(before.args, after.args)
        assertEquals("prepare", after.args[1])
        assertEquals(hash, after.args[3])
        val decoded = String(Base64.getUrlDecoder().decode(after.args[2]), Charsets.UTF_8)
        assertEquals(originalJson, decoded)
    }
}
