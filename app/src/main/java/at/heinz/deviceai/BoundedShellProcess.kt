package at.heinz.deviceai

import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Executes the existing trusted shell command; never parses model plans or
 * grants authority. One deadline covers both pipe draining and process exit.
 * Killing the direct process cannot promise rollback/termination of descendants.
 */
object BoundedShellProcess {
    fun execute(request: ShellExecutionRequest): ShellExecutionResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(request.timeoutMillis)
        var process: Process? = null
        var drain: FutureTask<ByteArray>? = null
        try {
            val child = ProcessBuilder("sh", "-c", request.command).redirectErrorStream(true).start()
            process = child
            child.outputStream.close()
            val output = FutureTask {
                child.inputStream.use { input ->
                    val collected = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (collected.size() + count > ShellExecutionWire.MAX_OUTPUT_BYTES) throw OutputLimit()
                        collected.write(buffer, 0, count)
                    }
                    collected.toByteArray()
                }
            }
            drain = output
            Thread(output, "device-ai-shell-output").apply { isDaemon = true; start() }
            fun remaining(): Long = (deadline - System.nanoTime()).also { if (it <= 0) throw TimeoutException() }
            val bytes = output.get(remaining(), TimeUnit.NANOSECONDS)
            if (!child.waitFor(remaining(), TimeUnit.NANOSECONDS)) throw TimeoutException()
            return ShellExecutionResult(ShellOutcome.COMPLETED, child.exitValue(), ShellExecutionWire.utf8(bytes))
        } catch (_: TimeoutException) {
            return ShellExecutionResult(ShellOutcome.TIMED_OUT, null, "")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return ShellExecutionResult(ShellOutcome.CANCELLED, null, "")
        } catch (e: ExecutionException) {
            return ShellExecutionResult(if (e.cause is OutputLimit) ShellOutcome.OUTPUT_LIMIT else ShellOutcome.FAILED, null, "")
        } catch (_: Exception) {
            return ShellExecutionResult(ShellOutcome.FAILED, null, "")
        } finally {
            if (process?.isAlive == true) process.destroyForcibly()
            drain?.cancel(true)
        }
    }

    private class OutputLimit : RuntimeException()
}
