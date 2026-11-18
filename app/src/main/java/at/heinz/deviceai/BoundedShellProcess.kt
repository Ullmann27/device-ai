package at.heinz.deviceai

import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Executes the existing trusted shell command; never parses model plans or
 * grants authority. One deadline covers both pipe draining and process exit.
 * Killing the direct process cannot promise rollback/termination of descendants.
 *
 * The command text must reach the shell byte-exact. Process arguments are encoded
 * by the platform, not by this class: Android always uses UTF-8, a desktop JVM uses
 * its locale charset and silently replaces every other character with '?'. A
 * command the platform cannot encode is therefore delivered as explicit UTF-8 over
 * standard input instead of being executed in a corrupted form.
 */
object BoundedShellProcess {
    fun execute(request: ShellExecutionRequest): ShellExecutionResult =
        execute(request, platformArgumentCharset())

    /** [argumentCharset] is the charset the platform applies to process arguments. */
    internal fun execute(request: ShellExecutionRequest, argumentCharset: Charset): ShellExecutionResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(request.timeoutMillis)
        var process: Process? = null
        var drain: FutureTask<ByteArray>? = null
        var feed: FutureTask<Unit>? = null
        try {
            val script = if (argumentCharset.newEncoder().canEncode(request.command)) null
                else strictUtf8(request.command)
            val child = ProcessBuilder("sh", "-c", if (script == null) request.command else STDIN_SCRIPT)
                .redirectErrorStream(true).start()
            process = child
            if (script == null) child.outputStream.close() else {
                val writer = FutureTask<Unit> {
                    try {
                        child.outputStream.write(script)
                        child.outputStream.flush()
                    } catch (failure: Exception) {
                        // A partially delivered script must never be evaluated.
                        child.destroyForcibly()
                        throw failure
                    } finally {
                        runCatching { child.outputStream.close() }
                    }
                }
                feed = writer
                Thread(writer, "device-ai-shell-script").apply { isDaemon = true; start() }
            }
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
            feed?.get(remaining(), TimeUnit.NANOSECONDS)
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
            feed?.cancel(true)
        }
    }

    /** OpenJDK derives the argument charset from the locale and publishes it as
     * `sun.jnu.encoding`; Android defines no such property and always uses UTF-8,
     * which is also its default charset. */
    private fun platformArgumentCharset(): Charset =
        System.getProperty("sun.jnu.encoding")?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?: Charset.defaultCharset()

    /** Malformed text (an unpaired surrogate) is rejected instead of being replaced. */
    @Throws(CharacterCodingException::class)
    private fun strictUtf8(command: String): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(command))
        return ByteArray(encoded.remaining()).also(encoded::get)
    }

    // Reads the whole script before anything runs, so the evaluated command sees the
    // same end-of-input on stdin as with a closed stream. The sentinel keeps trailing
    // newlines; a failed read exits without evaluating anything.
    private const val STDIN_SCRIPT = "s=\$(cat && printf x) || exit 126; eval \"\${s%x}\""

    private class OutputLimit : RuntimeException()
}
