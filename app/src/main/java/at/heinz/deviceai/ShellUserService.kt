package at.heinz.deviceai

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class ShellUserService() : IDeviceShell.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun execute(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            val code = process.waitFor()
            buildString {
                if (stdout.isNotBlank()) append(stdout.trim())
                if (stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr.trim())
                }
                if (isEmpty()) append("Fertig (Exit-Code $code)")
            }
        } catch (t: Throwable) {
            "Fehler: ${t.javaClass.simpleName}: ${t.message ?: "unbekannt"}"
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
