package at.heinz.deviceai

import android.content.Context
import androidx.annotation.Keep
import kotlin.system.exitProcess

class ShellUserService() : IDeviceShell.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun execute(command: String): String {
        val result = runCatching {
            val request = if (ShellExecutionWire.isFramedRequest(command)) {
                ShellExecutionWire.decodeRequest(command)
            } else ShellExecutionRequest(command, 90_000) // existing human console budget
            BoundedShellProcess.execute(request)
        }.getOrElse { ShellExecutionResult(ShellOutcome.FAILED, null, "") }
        return ShellExecutionWire.encodeResult(result)
    }

    override fun destroy() {
        exitProcess(0)
    }
}
