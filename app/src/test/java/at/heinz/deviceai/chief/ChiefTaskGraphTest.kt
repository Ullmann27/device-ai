package at.heinz.deviceai.chief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fail-closed DAG composition; these tests do not execute tools or trust model-provided "passed" flags. */
class ChiefTaskGraphTest {
    private fun task(
        id: String, kind: ChiefTaskKind, capability: String = "code",
        reads: Set<String> = emptySet(), writes: Set<String> = emptySet(),
        verifier: String? = null, dependsOn: Set<String> = emptySet()
    ): ChiefPlannedTask = ChiefPlannedTask(
        delegation = ChiefDelegation(id, "Auftrag $id", capability),
        kind = kind, readPaths = reads, writePaths = writes,
        verifierTaskId = verifier, dependsOn = dependsOn
    )

    private fun rejects(block: () -> Unit) {
        assertTrue("expected fail-closed rejection", runCatching(block).isFailure)
    }

    @Test fun independentReadOnlyTasksCanBeScheduledTogether() {
        val a = task("read-a", ChiefTaskKind.READ_ONLY, reads = setOf("app/src/a.kt"))
        val b = task("read-b", ChiefTaskKind.READ_ONLY, reads = setOf("app/src/b.kt"))
        val graph = ChiefTaskGraph.compile(listOf(a, b))
        assertEquals(listOf("read-a", "read-b"), graph.ready(emptySet()).map { it.delegation.taskId })
        assertEquals(listOf("read-b"), graph.ready(setOf("read-a")).map { it.delegation.taskId })
        assertTrue(graph.releaseReady(setOf("read-a", "read-b")))
    }

    @Test fun sourceWriteRequiresSeparateVerificationBeforePlanCompletion() {
        val code = task("code", ChiefTaskKind.CODE_CHANGE,
            writes = setOf("app/src/Main.kt"), verifier = "qa")
        val qa = task("qa", ChiefTaskKind.VERIFICATION, "code.verify",
            reads = setOf("app/src/Main.kt"))
        val graph = ChiefTaskGraph.compile(listOf(code, qa))
        assertEquals(listOf("code"), graph.ready(emptySet()).map { it.delegation.taskId })
        assertFalse(graph.releaseReady(setOf("code")))
        assertEquals(listOf("qa"), graph.ready(setOf("code")).map { it.delegation.taskId })
        assertTrue(graph.releaseReady(setOf("code", "qa")))
    }

    @Test fun pathConflictsAreSerializedAndSimilarPrefixesAreNotConfused() {
        val code1 = task("code1", ChiefTaskKind.CODE_CHANGE,
            writes = setOf("app/src/A.kt"), verifier = "qa1")
        val qa1 = task("qa1", ChiefTaskKind.VERIFICATION, "code.verify",
            reads = setOf("app/src/A.kt"))
        val code2 = task("code2", ChiefTaskKind.CODE_CHANGE,
            writes = setOf("app/src/A.kt"), verifier = "qa2")
        val qa2 = task("qa2", ChiefTaskKind.VERIFICATION, "code.verify",
            reads = setOf("app/src/A.kt"))
        val independent = task("other", ChiefTaskKind.READ_ONLY,
            reads = setOf("app/src/A.kt.backup"))
        val graph = ChiefTaskGraph.compile(listOf(code1, qa1, code2, qa2, independent))
        assertEquals(listOf("code1", "other"), graph.ready(emptySet()).map { it.delegation.taskId })
        assertEquals(listOf("qa1", "other"), graph.ready(setOf("code1")).map { it.delegation.taskId })
        assertEquals(listOf("code2", "other"), graph.ready(setOf("code1", "qa1")).map { it.delegation.taskId })
    }

    @Test fun cyclesAndUnknownDependenciesAreRejectedBeforeAnyDispatch() {
        val a = task("a", ChiefTaskKind.READ_ONLY, dependsOn = setOf("b"))
        val b = task("b", ChiefTaskKind.READ_ONLY, dependsOn = setOf("a"))
        rejects { ChiefTaskGraph.compile(listOf(a, b)) }
        rejects { ChiefTaskGraph.compile(listOf(a)) }
        rejects { ChiefTaskGraph.compile(listOf(a.copy(dependsOn = setOf("a")))) }
        rejects { ChiefTaskGraph.compile(listOf(a, a)) }
        rejects { ChiefTaskGraph.compile(emptyList()) }
    }

    @Test fun unverifiedCodeAndUnrelatedOrWritingVerifierAreRejected() {
        val code = task("code", ChiefTaskKind.CODE_CHANGE,
            writes = setOf("app/src/Main.kt"), verifier = "qa")
        val unrelated = task("qa", ChiefTaskKind.VERIFICATION, "code.verify",
            reads = setOf("app/src/Other.kt"))
        rejects { ChiefTaskGraph.compile(listOf(code)) }
        rejects { ChiefTaskGraph.compile(listOf(code, unrelated)) }
        rejects { ChiefTaskGraph.compile(listOf(code, unrelated.copy(readPaths = setOf("app/src/Main.kt"),
            delegation = ChiefDelegation("qa", "qa", "code")))) }
        rejects { task("malicious-qa", ChiefTaskKind.VERIFICATION, writes = setOf("app/src/Main.kt")) }
    }

    @Test fun traversalAndAmbiguousRepositoryPathsAreRejected() {
        for (bad in listOf("../other", "/etc/passwd", "src/../test", "src//test",
            "C:/Windows", "src\\win", "src/.hidden/./test", "src/x/")) {
            rejects { task("bad", ChiefTaskKind.READ_ONLY, reads = setOf(bad)) }
        }
        val safe = task("safe", ChiefTaskKind.READ_ONLY, reads = setOf("app/src/main/Main.kt"))
        assertEquals(listOf(safe), ChiefTaskGraph.compile(listOf(safe)).ready(emptySet()))
    }

    @Test fun progressInputsMustOnlyContainKnownAndNonOverlappingJobs() {
        val read = task("read", ChiefTaskKind.READ_ONLY)
        val graph = ChiefTaskGraph.compile(listOf(read))
        rejects { graph.ready(setOf("unknown")) }
        rejects { graph.ready(setOf("read"), setOf("read")) }
        rejects { graph.releaseReady(setOf("unknown")) }
    }
}
