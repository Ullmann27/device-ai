package at.heinz.deviceai

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

enum class ShellOutcome { COMPLETED, TIMED_OUT, OUTPUT_LIMIT, CANCELLED, FAILED }

data class ShellExecutionRequest(val command: String, val timeoutMillis: Long = 15_000) {
    init {
        require(command.isNotBlank() && '\u0000' !in command && command.toByteArray().size <= 16_384)
        require(timeoutMillis in 50..90_000)
    }
}

data class ShellExecutionResult(val outcome: ShellOutcome, val exitCode: Int?, val output: String) {
    init {
        require((outcome == ShellOutcome.COMPLETED) == (exitCode != null))
        require(exitCode == null || exitCode in 0..255)
        require(output.toByteArray().size <= ShellExecutionWire.MAX_OUTPUT_BYTES)
    }

    fun checkedOutput(): String {
        if (outcome == ShellOutcome.COMPLETED && exitCode == 0) return output
        val status = when (outcome) {
            ShellOutcome.TIMED_OUT -> ToolExecutionStatus.TIMED_OUT
            ShellOutcome.CANCELLED -> ToolExecutionStatus.CANCELLED
            else -> ToolExecutionStatus.FAILED
        }
        throw ToolBackendFailure(status, "Shell outcome: $outcome, exit: ${exitCode ?: "unknown"}.")
    }

    fun display(): String = when {
        outcome != ShellOutcome.COMPLETED -> "Befehl nicht abgeschlossen: $outcome."
        exitCode != 0 -> output + "\nExit-Code: $exitCode"
        output.isBlank() -> "Fertig."
        else -> output
    }
}

/** Versioned framing over the unchanged AIDL String channel. No permission or
 * attestation is created by this codec; only the trusted bound service may reply.
 */
object ShellExecutionWire {
    const val MAX_OUTPUT_BYTES = 120_000
    // An old service executes this as the POSIX no-op ':' followed by a comment.
    // It never executes the embedded command; checked clients reject its plain reply.
    private const val REQUEST = ": #DEVICE_AI_SHELL_REQUEST_V1 "
    private const val RESULT = "DEVICE_AI_SHELL_RESULT_V1"

    fun encodeRequest(value: ShellExecutionRequest): String =
        "$REQUEST${value.timeoutMillis} ${encode(value.command)}"

    fun decodeRequest(raw: String): ShellExecutionRequest {
        require(raw.length <= 24_000)
        require(raw.startsWith(REQUEST))
        val fields = raw.removePrefix(REQUEST).split(' ')
        require(fields.size == 2)
        val timeout = fields[0].toLongOrNull()
        require(timeout != null && timeout.toString() == fields[0])
        return ShellExecutionRequest(decode(fields[1], 16_384), timeout)
    }

    fun isFramedRequest(raw: String): Boolean = raw.startsWith(": #DEVICE_AI_SHELL_REQUEST_")

    fun encodeResult(value: ShellExecutionResult): String =
        "$RESULT\n${value.outcome.name}\n${value.exitCode ?: "-"}\n${encode(value.output)}"

    fun decodeResult(raw: String): ShellExecutionResult {
        require(raw.length <= 161_000)
        val fields = raw.split('\n')
        require(fields.size == 4 && fields[0] == RESULT)
        val outcome = ShellOutcome.entries.firstOrNull { it.name == fields[1] }
        require(outcome != null)
        val code = if (fields[2] == "-") null else fields[2].toIntOrNull().also {
            require(it != null && it.toString() == fields[2])
        }
        return ShellExecutionResult(outcome, code, decode(fields[3], MAX_OUTPUT_BYTES))
    }

    internal fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun decode(value: String, limit: Int): String {
        val bytes = try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid shell transport encoding.")
        }
        require(bytes.size <= limit && Base64.getEncoder().encodeToString(bytes) == value)
        return try { utf8(bytes) } catch (_: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Invalid shell transport UTF-8.")
        }
    }
}
