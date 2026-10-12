package at.heinz.deviceai

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class ShellUserService() : IDeviceShell.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun execute(command: String): String {
        if (command.isBlank()) return "Leerer Befehl."

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            val buffer = CharArray(2048)
            var total = 0
            val maxChars = 120_000

            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break

                val remaining = maxChars - total
                if (remaining <= 0) {
                    output.append("\n… Ausgabe gekürzt …")
                    break
                }

                val copy = minOf(read, remaining)
                output.append(buffer, 0, copy)
                total += copy
            }

            val finished = process.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "Zeitlimit überschritten. Der Befehl wurde nach 90 Sekunden beendet."
            }

            val code = process.exitValue()
            val text = output.toString().trim()

            when {
                text.isNotBlank() && code == 0 -> text
                text.isNotBlank() -> "$text\n\nExit-Code: $code"
                code == 0 -> "Fertig."
                else -> "Befehl beendet mit Exit-Code $code."
            }
        } catch (t: Throwable) {
            "Fehler: ${t.javaClass.simpleName}: ${t.message ?: "unbekannt"}"
        }
    }

    override fun destroy() {
        exitProcess(0)
    }
}
