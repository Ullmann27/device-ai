package at.heinz.deviceai.learning.interactive

import at.heinz.deviceai.learning.LearningExperience
import at.heinz.deviceai.learning.LearningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Real separate JVM processes, not just eight threads in one JVM. */
class ResearchFeedbackMultiprocessTest {
    @Test fun concurrentProcessReviewsAcceptOnlyOneDigest() {
        val root = Files.createTempDirectory("continuum-feedback-multiprocess-").toFile()
        val processes = mutableListOf<Process>()
        try {
            val feedback = ResearchFeedback(root)
            val candidate = feedback.propose("Nein, gemeint war der Luftdruck.", "os-process")
            val javaExecutable = File(System.getProperty("java.home"), "bin/java").absolutePath
            // Gradle workers often expose only the worker launcher in java.class.path.
            // Include code origins for the actual loaded Kotlin app/test classes.
            val codeOrigins = listOf(
                ResearchFeedbackReviewProbe::class.java,
                ResearchFeedback::class.java,
                LearningExperience::class.java,
                kotlin.jvm.internal.Intrinsics::class.java
            ).map { type ->
                val url = requireNotNull(type.protectionDomain?.codeSource?.location) {
                    "Missing JVM code source for ${type.name}"
                }
                File(url.toURI()).canonicalPath
            }
            val parentClasspath = System.getProperty("java.class.path").orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
            val classpath = (codeOrigins + parentClasspath).distinct()
                .joinToString(File.pathSeparator)
            val probe = ResearchFeedbackReviewProbe::class.java.name

            for (id in listOf("one", "two")) {
                processes += ProcessBuilder(
                    javaExecutable, "-cp", classpath, probe,
                    root.absolutePath, id, candidate.experience.id, candidate.reviewDigest
                ).start()
            }

            val limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!listOf("one.ready", "two.ready").all { File(root, it).exists() } &&
                   System.nanoTime() < limit) Thread.sleep(10)
            assertTrue("Both JVMs must reach the start barrier", listOf("one.ready", "two.ready")
                .all { File(root, it).exists() })
            File(root, "go").writeText("go")

            val outcomes = processes.map { process ->
                assertTrue("Child process timed out", process.waitFor(20, TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText().trim()
                val diagnostic = process.errorStream.bufferedReader().readText().trim()
                assertEquals("Child failed: $output; stderr: $diagnostic", 0, process.exitValue())
                output
            }
            assertEquals(1, outcomes.count { it == "CONFIRMED" })
            assertEquals(1, outcomes.count { it == "DENIED" })
            assertEquals(LearningStatus.CONFIRMED, ResearchFeedback(root).all().single().experience.status)
            assertEquals(1, ResearchFeedback(root).trainingCandidates().size)
        } finally {
            processes.forEach { if (it.isAlive) it.destroyForcibly() }
            root.deleteRecursively()
        }
    }

    @Test fun crashesAndSymlinksNeverDowngradeToUnprotectedStorage() {
        val root = Files.createTempDirectory("continuum-feedback-path-").toFile()
        val external = Files.createTempDirectory("continuum-feedback-external-").toFile()
        try {
            val lock = ResearchFeedbackFileLock(root)
            assertThrows(IllegalStateException::class.java) {
                lock.guard { throw IllegalStateException("deliberate interruption") }
            }
            assertEquals("recovered", lock.guard { "recovered" })

            // An attacker-controlled lock-file symlink must not silently bypass
            // the kernel lock or write to the target outside the ledger.
            val externalLockTarget = File(root, "external-lock-target")
            externalLockTarget.writeText("untouched")
            val lockPath = File(File(root, "interactive-feedback-v1"), ".review.lock").toPath()
            Files.delete(lockPath)
            Files.createSymbolicLink(lockPath, externalLockTarget.toPath())
            assertThrows(IOException::class.java) { ResearchFeedback(root).all() }
            assertEquals("untouched", externalLockTarget.readText())
            Files.deleteIfExists(lockPath)

            // Both ledger generations must be ordinary files; an injected link
            // must not permit reading or training on an outside file.
            for (ledgerName in listOf("experiences-v2.bin", "experiences-v2.lkg")) {
                val ledgerLink = File(File(root, "interactive-feedback-v1"), ledgerName).toPath()
                Files.createSymbolicLink(ledgerLink, externalLockTarget.toPath())
                assertThrows(IOException::class.java) { ResearchFeedback(root).all() }
                assertEquals("untouched", externalLockTarget.readText())
                Files.deleteIfExists(ledgerLink)
            }

            val link = File(root, "linked-root")
            Files.createSymbolicLink(link.toPath(), external.toPath())
            assertThrows(IOException::class.java) { ResearchFeedback(link).all() }
            val dir = File(external, "interactive-feedback-v1")
            Files.createSymbolicLink(dir.toPath(), root.toPath())
            assertThrows(IOException::class.java) { ResearchFeedback(external).all() }
        } finally {
            Files.deleteIfExists(File(root, "linked-root").toPath())
            Files.deleteIfExists(File(external, "interactive-feedback-v1").toPath())
            root.deleteRecursively()
            external.deleteRecursively()
        }
    }
}

/** Invoked only by the test suite, never as an Android or production entrypoint. */
object ResearchFeedbackReviewProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4)
        val root = File(args[0])
        File(root, args[1] + ".ready").writeText("ready")
        val start = File(root, "go")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!start.exists() && System.nanoTime() < deadline) Thread.sleep(5)
        require(start.exists())
        try {
            ResearchFeedback(root).review(args[2], args[3], ResearchFeedback.Review.CONFIRM, 2)
            println("CONFIRMED")
        } catch (error: IllegalArgumentException) {
            println("DENIED")
        }
    }
}
