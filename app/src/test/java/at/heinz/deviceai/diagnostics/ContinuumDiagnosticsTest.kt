package at.heinz.deviceai.diagnostics

import at.heinz.deviceai.diagnostics.ContinuumDiagnostics.Severity
import at.heinz.deviceai.diagnostics.ContinuumDiagnostics.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ContinuumDiagnosticsTest {
    private fun store() = ContinuumDiagnostics(File(Files.createTempDirectory("diag").toFile(), "issues.tsv"), "0.3.13-test")

    @Test fun repeatedProblemIsCountedNotDuplicatedAndStaysOpenUntilResolved() {
        val d = store()
        assertTrue(d.report("Lernkern", "LEARNING_STATUS", Severity.WARNING, "nicht lesbar", "IOException", "neu laden", 1000).isNew)
        assertFalse(d.report("Lernkern", "LEARNING_STATUS", Severity.ERROR, "nicht lesbar", "IOException", "neu laden", 2000).isNew)
        val open = d.open()
        assertEquals(1, open.size)
        assertEquals(2, open.single().count)
        assertEquals(Severity.ERROR, open.single().severity)
        assertEquals(1000L, open.single().firstSeen)
        assertTrue(d.resolve("Lernkern", "LEARNING_STATUS", "wieder lesbar", 3000))
        assertTrue(d.open().isEmpty())
        assertEquals(Status.RESOLVED, d.all().single().status)
    }

    @Test fun acknowledgedProblemReopensWhenItHappensAgainAndSurvivesRestart() {
        val file = File(Files.createTempDirectory("diag").toFile(), "issues.tsv")
        val d = ContinuumDiagnostics(file, "v")
        val issue = d.report("App", "CRASH", Severity.CRITICAL, "Absturz", "NullPointerException", "neu starten", 10).issue
        assertTrue(d.acknowledge(issue.fingerprint, 20))
        assertTrue(d.open().isEmpty())
        val again = ContinuumDiagnostics(file, "v").report("App", "CRASH", Severity.CRITICAL, "Absturz", "NPE", "neu starten", 30)
        assertTrue("an acknowledged problem that returns is announced again", again.isNew)
        assertEquals(Status.OPEN, ContinuumDiagnostics(file, "v").open().single().status)
    }

    @Test fun secretsAndPersonalDataNeverReachStorageOrExport() {
        val d = store()
        val detail = "Bearer abcdefghijkLMNOP1234 token=ghp_1234567890abcdefXYZ password=hunter2 mail heinz@example.com " +
            "url https://api.example.com/v1/run?key=SECRET123 /data/user/0/at.heinz.deviceai/files/x +43 664 1234567 gsk_ABCDEFGHIJKLMNOPQRST"
        d.report("Mentor", "REMOTE_FAILED", Severity.ERROR, "Fehler bei heinz@example.com", detail, "API-Schlüssel sk-ABCDEFGHIJKLMNOPQR prüfen")
        val export = d.export()
        val stored = d.all().single()
        for (secret in listOf("abcdefghijkLMNOP1234", "ghp_1234567890abcdef", "hunter2", "heinz@example.com", "SECRET123",
                "/data/user/0", "664 1234567", "gsk_ABCDEFGHIJ", "sk-ABCDEFGHIJ")) {
            assertFalse("leaked $secret", export.contains(secret))
            assertFalse("stored $secret", (stored.title + stored.detail + stored.recommendation).contains(secret))
        }
        assertTrue(export.contains("https://api.example.com/v1/run?[parameter entfernt]"))
    }

    @Test fun adversarialSecretsLeakNeitherIntoTheFileNorIntoTheExport() {
        val dir = Files.createTempDirectory("diag").toFile()
        val file = File(dir, "issues.tsv")
        val d = ContinuumDiagnostics(file, "0.3.13")
        val secrets = listOf(
            "Authorization: Basic dXNlcjpwYXNzd29yZA==",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
            "AKIAIOSFODNN7EXAMPLE",
            "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----",
            "hf_AbCdEfGhIjKlMnOpQrStUvWx", "glpat-AbCdEfGhIjKlMnOpQrSt", "sk-proj-AbCdEfGhIjKlMnOpQrStUv",
            "/home/heinz/geheim/plan.txt", "/Users/heinz/Desktop", "192.168.178.20",
            "Hallo Heinz", "Ullmann27", "heinz.ullmann@gmail.com", "passwort: Sommer2026!", "api_key=abc123def456ghi"
        )
        val text = secrets.joinToString(" | ")
        d.report("Mentor $text".take(60), "LEAK_PROBE", Severity.ERROR, "Titel $text", "Detail\n$text", "Empfehlung $text")
        val raw = file.readText()
        val export = d.export()
        val probes = listOf("dXNlcjpwYXNz", "eyJzdWIiOiIx", "dozjgNryP4J3", "AKIAIOSFODNN7", "MIIEvQIBADAN", "hf_AbCdEfGh", "glpat-AbCdEf",
            "sk-proj-AbCd", "/home/heinz", "/Users/heinz", "192.168.178.20", "Heinz", "Ullmann", "gmail.com", "Sommer2026", "abc123def456")
        for (probe in probes) {
            assertFalse("file leaks $probe", raw.contains(probe, ignoreCase = true))
            assertFalse("export leaks $probe", export.contains(probe, ignoreCase = true))
        }
        assertTrue("the package name stays readable for developers", Redaction.apply("at.heinz.deviceai.AgentActivity").contains("at.heinz.deviceai"))
    }

    @Test fun malformedHistoryCannotBeSilentlyOverwritten() {
        val directory = Files.createTempDirectory("diag-history").toFile()
        val destination = File(directory, "issues.tsv")
        destination.writeText("corrupt\\tentry", Charsets.UTF_8)
        val before = destination.readBytes()
        val diagnostics = ContinuumDiagnostics(destination, "v")
        var rejected = false
        try {
            diagnostics.report("Kern", "BROKEN_CYCLE", Severity.ERROR, "Fehler", "", "prüfen")
        } catch (_: IllegalStateException) {
            rejected = true
        }
        assertTrue("corrupt original must be rejected", rejected)
        assertTrue("original bytes must be preserved", before.contentEquals(destination.readBytes()))
    }

    @Test fun failedReplaceNeverDeletesAnExistingNonemptyDirectory() {
        val directory = Files.createTempDirectory("diag-existing").toFile()
        val destination = File(directory, "issues.tsv")
        assertTrue(destination.mkdir())
        val sentinel = File(destination, "preserve.txt")
        sentinel.writeText("original data", Charsets.UTF_8)
        val diagnostics = ContinuumDiagnostics(destination, "v")
        var failed = false
        try {
            diagnostics.report("Kern", "SAVE_FAILED", Severity.WARNING, "Fehler", "", "prüfen")
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("replacement of a directory must fail safely", failed)
        assertTrue("existing directory must remain", destination.isDirectory)
        assertEquals("original data", sentinel.readText(Charsets.UTF_8))
    }

    @Test fun openIssuesAreOrderedBySeverityThenRecency() {
        val d = store()
        d.report("A", "INFO_ONE", Severity.INFO, "i", "", "r", 5)
        d.report("B", "WARN_ONE", Severity.WARNING, "w", "", "r", 1)
        d.report("C", "ERR_ONE", Severity.ERROR, "e", "", "r", 2)
        assertEquals(listOf("ERR_ONE", "WARN_ONE", "INFO_ONE"), d.open().map { it.code })
    }
}
