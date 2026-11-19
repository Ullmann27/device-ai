package at.heinz.deviceai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ShellExecutionTest {
    @Test fun directoryListingCannotHideFailureBehindASuccessfulHeadPipeline() {
        val dir=java.nio.file.Files.createTempDirectory("checked-listing-").toFile()
        try {
            val path=java.io.File(dir,"missing-directory").absolutePath
            // Reproduce old command: ls fails but POSIX pipeline reports head's zero.
            val old=BoundedShellProcess.execute(ShellExecutionRequest("ls -lah -- '$path' 2>/dev/null | head -180",1000))
            assertEquals(0,old.exitCode)
            val fixed=BoundedShellProcess.execute(ShellExecutionRequest(checkedDirectoryListingCommand(path),1000))
            assertNotEquals(0,fixed.exitCode)
            assertThrows(ToolBackendFailure::class.java) { fixed.checkedOutput() }
            val file=java.io.File(dir,"public-synthetic.txt").also { it.writeText("fixture") }
            val good=BoundedShellProcess.execute(ShellExecutionRequest(checkedDirectoryListingCommand(dir.absolutePath),1000))
            assertTrue(good.checkedOutput().contains(file.name))
            val quoted=java.io.File(dir,"quote'; touch unexpected; '").also { it.mkdir() }
            val safe=BoundedShellProcess.execute(ShellExecutionRequest(checkedDirectoryListingCommand(quoted.absolutePath),1000))
            assertEquals(0,safe.exitCode)
            assertFalse(java.io.File(dir,"unexpected").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun actualExitStatusCannotBeForgedByPrintedSuccessText() {
        val r = BoundedShellProcess.execute(ShellExecutionRequest("printf 'SUCCEEDED'; exit 37", 1000))
        assertEquals(ShellOutcome.COMPLETED, r.outcome)
        assertEquals(37, r.exitCode)
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { r.checkedOutput() })
        assertEquals(ToolExecutionStatus.FAILED, gate.execute(ToolProposal("get_device_info", emptyMap()).invocation("nonzero")).record.status)
    }

    @Test fun successfulGermanOutputRoundTripsThroughStrictVersionedWire() {
        val request = ShellExecutionRequest("printf 'Grüße vom Gerät'", 1000)
        assertEquals(request, ShellExecutionWire.decodeRequest(ShellExecutionWire.encodeRequest(request)))
        val result = BoundedShellProcess.execute(request)
        assertEquals("Grüße vom Gerät", result.checkedOutput())
        assertEquals(result, ShellExecutionWire.decodeResult(ShellExecutionWire.encodeResult(result)))
        assertEquals(0, result.exitCode)
    }

    // The platform encodes process arguments (locale charset on a desktop JVM, UTF-8 on
    // Android). Forcing an ASCII-only argument charset reproduces the POSIX-locale host on
    // every machine and proves the command still reaches the shell byte-exact.
    @Test fun commandThePlatformCannotEncodeAsArgumentReachesTheShellUnchanged() {
        val ascii = Charsets.US_ASCII
        val german = BoundedShellProcess.execute(ShellExecutionRequest("printf 'Grüße vom Gerät'; exit 37", 1000), ascii)
        assertEquals(ShellOutcome.COMPLETED, german.outcome)
        assertEquals("Grüße vom Gerät", german.output)
        assertEquals(37, german.exitCode)
        // The evaluated command still sees end-of-input, exactly like the argument path.
        assertEquals("ä-eof", BoundedShellProcess.execute(
            ShellExecutionRequest("cat >/dev/null; printf 'ä-eof'", 1000), ascii).checkedOutput())
        // Trailing newlines of the command text are preserved (here-document terminator).
        assertEquals("Zeile ö\n", BoundedShellProcess.execute(
            ShellExecutionRequest("cat <<'EOF'\nZeile ö\nEOF\n", 1000), ascii).checkedOutput())
        // A syntax error is the shell's own failure, never a success.
        val broken = BoundedShellProcess.execute(ShellExecutionRequest("printf 'ü'; if then", 1000), ascii)
        assertNotEquals(0, broken.exitCode)
        // A representable command behaves identically under the same argument charset.
        assertEquals("plain", BoundedShellProcess.execute(ShellExecutionRequest("printf plain", 1000), ascii).checkedOutput())
    }

    @Test fun deadlineAndOutputLimitAlsoBoundTheStandardInputDelivery() {
        val start = System.nanoTime()
        val slow = BoundedShellProcess.execute(ShellExecutionRequest(": 'ü'; exec sleep 2", 100), Charsets.US_ASCII)
        assertEquals(ShellOutcome.TIMED_OUT, slow.outcome)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 900)
        val flood = BoundedShellProcess.execute(
            ShellExecutionRequest("while :; do printf 'üüüüüüüüüüüüüüüüüüüüüüüüüüüüüü'; done", 2000), Charsets.US_ASCII)
        assertEquals(ShellOutcome.OUTPUT_LIMIT, flood.outcome)
    }

    @Test fun malformedCommandTextIsRejectedInsteadOfRunningAReplacement() {
        val dir = java.nio.file.Files.createTempDirectory("shell-malformed-").toFile()
        try {
            val marker = java.io.File(dir, "executed")
            // An unpaired surrogate has no UTF-8 form; no charset may substitute a '?' for it.
            val request = ShellExecutionRequest("touch '${marker.absolutePath}' # \uD83D", 1000)
            for (charset in listOf(Charsets.UTF_8, Charsets.US_ASCII)) {
                assertEquals(ShellOutcome.FAILED, BoundedShellProcess.execute(request, charset).outcome)
                assertFalse(marker.exists())
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun silentAndOpenPipeCommandsRespectTheWholeDeadline() {
        for (command in listOf("exec sleep 2", "sleep 1 & wait")) {
            val start = System.nanoTime()
            val r = BoundedShellProcess.execute(ShellExecutionRequest(command, 100))
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            assertEquals(command, ShellOutcome.TIMED_OUT, r.outcome)
            assertTrue("deadline was bypassed: $elapsed ms", elapsed < 900)
            assertNull(r.exitCode)
            System.getProperty("continuum.action.evidenceDir")?.let {
                java.io.File(it, "shell-bounds.tsv").appendText("deadline\t100\t$elapsed\t${r.outcome}\n")
            }
        }
    }

    @Test fun outputFloodIsBoundedAndNeverCountedAsSuccessfulCompletion() {
        val start = System.nanoTime()
        val result = BoundedShellProcess.execute(ShellExecutionRequest("while :; do printf '012345678901234567890123456789'; done", 2000))
        assertEquals(ShellOutcome.OUTPUT_LIMIT, result.outcome)
        assertTrue(result.output.toByteArray().size <= ShellExecutionWire.MAX_OUTPUT_BYTES)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
        assertThrows(ToolBackendFailure::class.java) { result.checkedOutput() }
    }

    @Test fun malformedLegacyAndOversizedResponsesFailClosedWithoutRetry() {
        for (raw in listOf("Fertig.", "Exit-Code: 9", "DEVICE_AI_SHELL_RESULT_V2\nCOMPLETED\n0\n",
            "DEVICE_AI_SHELL_RESULT_V1\nCOMPLETED\n0\n!!!!", "x".repeat(200_000))) {
            assertThrows(IllegalArgumentException::class.java) { ShellExecutionWire.decodeResult(raw) }
        }
        val ok = ShellExecutionWire.encodeResult(ShellExecutionResult(ShellOutcome.COMPLETED, 0, "ok"))
        assertThrows(IllegalArgumentException::class.java) { ShellExecutionWire.decodeResult(ok + "\nforged") }
        assertThrows(IllegalArgumentException::class.java) { ShellExecutionRequest("x\u0000y", 100) }
        assertThrows(IllegalArgumentException::class.java) { ShellExecutionRequest("id", Long.MAX_VALUE) }
    }

    @Test fun stdinIsClosedAndInterruptedProcessDoesNotReportSuccess() {
        assertEquals("eof", BoundedShellProcess.execute(ShellExecutionRequest("cat >/dev/null; printf eof", 1000)).checkedOutput())
        val started = CountDownLatch(1)
        var outcome: ShellOutcome? = null
        val thread = Thread {
            started.countDown()
            outcome = BoundedShellProcess.execute(ShellExecutionRequest("exec sleep 2", 1000)).outcome
            assertTrue(Thread.currentThread().isInterrupted)
        }
        thread.start(); assertTrue(started.await(1, TimeUnit.SECONDS))
        thread.interrupt(); thread.join(1000)
        assertFalse(thread.isAlive)
        assertEquals(ShellOutcome.CANCELLED, outcome)
    }

    @Test fun backendTimeoutReachesGateAndCannotBeMisclassifiedAsGreen() {
        val result = ShellExecutionResult(ShellOutcome.TIMED_OUT, null, "")
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { result.checkedOutput() })
        assertEquals(ToolExecutionStatus.TIMED_OUT,
            gate.execute(ToolProposal("get_device_info", emptyMap()).invocation("timeout")).record.status)
    }

    @Test fun oversizedGateOutputIsNotCertifiedAsComplete() {
        val dir = java.nio.file.Files.createTempDirectory("shell-gate-output-limit").toFile()
        try {
            val receipts = FileToolExecutionReceiptStore(dir)
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true, maxOutputCharacters = 4),
                ToolRunner { "123456" }, receiptStore = receipts)
            val result = gate.execute(ToolProposal("get_device_info", emptyMap()).invocation("oversize"))
            assertEquals(ToolExecutionStatus.FAILED, result.record.status)
            assertEquals("1234", result.output)
            assertEquals(ToolReceiptState.UNKNOWN_AFTER_DISPATCH, receipts.get("oversize")!!.state)
            assertTrue(result.record.outcomeUncertain)
            assertNull(receipts.get("oversize")!!.outputSha256)
        } finally { dir.deleteRecursively() }
    }

    @Test fun versionedRequestIsANoopWhenAnOldServiceTreatsItAsShellText() {
        val wire = ShellExecutionWire.encodeRequest(ShellExecutionRequest("printf unintended-action", 1000))
        val oldService = BoundedShellProcess.execute(ShellExecutionRequest(wire, 1000))
        assertEquals(0, oldService.exitCode)
        assertEquals("", oldService.output)
        // No retry or raw-command downgrade when the peer cannot prove its result format.
        assertThrows(IllegalArgumentException::class.java) { ShellExecutionWire.decodeResult("Fertig.") }
    }

    @Test fun invalidUtf8AndEmptyRepliesRetainExplicitStatus() {
        val invalid = BoundedShellProcess.execute(ShellExecutionRequest("printf '\\377'", 1000))
        assertEquals(ShellOutcome.FAILED, invalid.outcome)
        for (result in listOf(ShellExecutionResult(ShellOutcome.COMPLETED, 0, ""),
            ShellExecutionResult(ShellOutcome.FAILED, null, ""))) {
            assertEquals(result, ShellExecutionWire.decodeResult(ShellExecutionWire.encodeResult(result)))
        }
    }
}
