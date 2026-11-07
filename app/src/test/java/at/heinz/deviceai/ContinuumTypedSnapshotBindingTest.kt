package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class ContinuumTypedSnapshotBindingTest {
    private val task = ContinuumCodingTask("snapshot-binding-job", "a".repeat(40),
        ContinuumCodingTask.Kind.BUGFIX, "Vollständiges Ziel mit später Einschränkung: keine Daten löschen",
        listOf("lib/distance.mjs", "lib/units.mjs"),
        listOf("Negativen Abstand erhalten", "Null ohne Rundung zurückgeben"),
        ContinuumCodingTask.Chain.NODE, ContinuumCodingTask.Limits(3, 24000))
    private val authorization get() = ContinuumCodingTask.Authorization(task, task.fingerprint())

    @Test fun completeSavedSnapshotMustMatchEveryBindingAndRejectsLegacyState() {
        fun parse(json:String?=task.canonicalJson(), hash:String?=task.fingerprint(),
                  id:String?=task.id, commit:String?=task.sourceCommit) =
            ContinuumTypedCodingBridge.parseBoundAuthorization(json, hash, id, commit)
        assertEquals(authorization, parse())
        assertNull(parse(json = null))
        assertNull(parse(hash = null))
        assertNull(parse(id = null))
        assertNull(parse(commit = null))
        assertNull(parse(id = "another-reviewed-job"))
        assertNull(parse(commit = "b".repeat(40)))
        assertNull(parse(hash = "b".repeat(64)))
        assertNull(parse(json = task.copy(goal = "Eine andere Änderung").canonicalJson()))
        assertNull(parse(json = task.canonicalJson() + " "))
    }

    @Test fun runReviewContainsTheCompleteScopeAndOnlyExecutionConsentLanguage() {
        val review = ContinuumTypedCodingBridge.executionReview(authorization)!!
        for (field in listOf(task.id, task.sourceCommit, task.fingerprint(), task.goal, task.kind.name,
                task.chain.wire, "Maximale Modellaufrufe: 3", "Maximale Kontextzeichen: 24000") +
                task.allowedPaths + task.acceptance) assertTrue("Missing $field", review.contains(field))
        assertTrue(review.contains("AUSFÜHRUNG:"))
        assertFalse(review.contains("VORBEREITUNG:"))
        assertTrue(review.contains("keine kryptografische Besitzeridentität"))
        assertTrue(ContinuumTypedCodingBridge.preparationReview(authorization)!!.contains("Kein KI-Modell ausführen"))
        assertNull(ContinuumTypedCodingBridge.executionReview(
            authorization.copy(taskSha256 = "b".repeat(64))))
    }
}
