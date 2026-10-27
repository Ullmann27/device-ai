package at.heinz.deviceai

import android.content.Context
import java.util.Locale

class DeviceToolExecutor(
    private val context: Context,
    private val bridge: ShizukuBridge
) {
    private val sharedRoots = listOf(
        "/sdcard/Download",
        "/sdcard/Documents",
        "/sdcard/DCIM",
        "/sdcard/Pictures",
        "/sdcard/Movies"
    )

    fun execute(call: ToolCallRequest, callback: (Result<String>) -> Unit) {
        ToolRegistry.validate(call)?.let {
            callback(Result.failure(IllegalArgumentException(it)))
            return
        }
        // App-volume statistics need neither Shizuku nor access to personal files.
        // Exceptions propagate to the existing ToolGate as FAILED, never as a
        // successful string containing an error. The runner consumes Result explicitly.
        if (call.name == "get_storage_info") {
            callback(runCatching { DeviceStorageProbe.read(context.filesDir) })
            return
        }
        if (!bridge.isReady()) {
            callback(Result.failure(IllegalStateException("Shizuku ist nicht verbunden.")))
            return
        }

        val command = runCatching { buildCommand(call) }
            .getOrElse {
                callback(Result.failure(it))
                return
            }

        bridge.executeChecked(command, callback)
    }

    private fun buildCommand(call: ToolCallRequest): String =
        when (call.name) {
            "get_device_info" ->
                "echo '=== DEVICE AI ==='; " +
                    "echo Hersteller: $(getprop ro.product.manufacturer); " +
                    "echo Modell: $(getprop ro.product.model); " +
                    "echo Android: $(getprop ro.build.version.release); " +
                    "echo SDK: $(getprop ro.build.version.sdk); " +
                    "echo Build: $(getprop ro.build.display.id); " +
                    "echo ABI: $(getprop ro.product.cpu.abi)"

            "get_battery_info" -> "dumpsys battery | head -60"

            "list_installed_apps" -> {
                val userOnly = boolArg(call, "userOnly", true)
                if (userOnly) {
                    "pm list packages -3 | sed 's/^package://' | sort | head -400"
                } else {
                    "pm list packages | sed 's/^package://' | sort | head -700"
                }
            }

            "get_app_info" -> {
                val pkg = resolvePackage(requiredString(call, "packageOrName"))
                    ?: error("App nicht gefunden.")
                "dumpsys package ${safePackage(pkg)} 2>/dev/null | " +
                    "grep -E 'Package \\[|versionName=|versionCode=|firstInstallTime|lastUpdateTime|dataDir=|userId=' | head -100"
            }

            "list_processes" -> "top -b -n 1 -m 35 2>/dev/null | head -55"

            "read_logcat" -> {
                val lines = intArg(call, "lines", 220).coerceIn(40, 600)
                val target = stringArg(call, "packageOrName")
                if (target.isNullOrBlank()) {
                    "logcat -d -t 1400 2>/dev/null | grep -E 'AndroidRuntime|FATAL EXCEPTION| E/' | tail -$lines"
                } else {
                    val pkg = resolvePackage(target) ?: safePackage(target)
                    "logcat -d -t 1600 2>/dev/null | grep -i -E 'AndroidRuntime|FATAL EXCEPTION|${escapeGrep(pkg)}' | tail -$lines"
                }
            }

            "launch_app" -> {
                val name = requiredString(call, "packageOrName")
                val pkg = resolvePackage(name) ?: error("App nicht gefunden: $name")
                val safe = safePackage(pkg)
                "monkey -p $safe -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 && echo 'Gestartet: $safe'"
            }

            "stop_app" -> {
                val name = requiredString(call, "packageOrName")
                val pkg = resolvePackage(name) ?: error("App nicht gefunden: $name")
                val safe = safePackage(pkg)
                "am force-stop $safe && echo 'Gestoppt: $safe'"
            }

            "restart_app" -> {
                val name = requiredString(call, "packageOrName")
                val pkg = resolvePackage(name) ?: error("App nicht gefunden: $name")
                val safe = safePackage(pkg)
                "am force-stop $safe; sleep 1; " +
                    "monkey -p $safe -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                    "echo 'Neu gestartet: $safe'"
            }

            "install_apk" -> {
                val path = safeSharedPath(requiredString(call, "path"))
                require(path.endsWith(".apk", ignoreCase = true)) { "Nur APK-Dateien sind erlaubt." }
                "test -f ${shellQuote(path)} && pm install -r ${shellQuote(path)}"
            }

            "uninstall_app" -> {
                val name = requiredString(call, "packageOrName")
                val pkg = resolvePackage(name) ?: error("App nicht gefunden: $name")
                "pm uninstall ${safePackage(pkg)}"
            }

            "list_files" -> {
                val path = safeSharedPath(requiredString(call, "path"))
                checkedDirectoryListingCommand(path)
            }

            "find_files" -> {
                val query = sanitizeSearch(requiredString(call, "query"))
                val limit = intArg(call, "limit", 100).coerceIn(1, 250)
                val roots = rootsArg(call).ifEmpty { sharedRoots }
                val rootArgs = roots.map(::safeSharedPath).joinToString(" ") { shellQuote(it) }
                "find $rootArgs -type f -iname ${shellQuote("*$query*")} 2>/dev/null | head -$limit"
            }

            "find_latest_apk" -> {
                val filter = stringArg(call, "filter")
                    ?.replace(Regex("[^A-Za-z0-9._ -]"), "")
                    ?.trim()
                    .orEmpty()
                val pattern = if (filter.isBlank()) "*.apk" else "*$filter*.apk"
                "find /sdcard/Download -maxdepth 3 -type f -iname ${shellQuote(pattern)} -printf '%T@ %p\\n' 2>/dev/null | " +
                    "sort -nr | head -20 | cut -d' ' -f2-"
            }

            "get_large_files" -> {
                val limit = intArg(call, "limit", 40).coerceIn(1, 100)
                val roots = rootsArg(call).ifEmpty { sharedRoots }
                val rootArgs = roots.map(::safeSharedPath).joinToString(" ") { shellQuote(it) }
                "du -ak $rootArgs 2>/dev/null | sort -nr | head -$limit"
            }

            "move_file" -> {
                val source = safeSharedPath(requiredString(call, "source"))
                val destination = safeSharedPath(requiredString(call, "destination"))
                "mv -- ${shellQuote(source)} ${shellQuote(destination)} && echo 'Verschoben.'"
            }

            "delete_file" -> {
                val path = safeSharedPath(requiredString(call, "path"))
                require(path !in sharedRoots) { "Ordnerwurzeln dürfen nicht gelöscht werden." }
                "test -f ${shellQuote(path)} && rm -f -- ${shellQuote(path)} && echo 'Gelöscht.'"
            }

            "take_screenshot" ->
                "out=/sdcard/Download/device-ai-$(date +%Y%m%d-%H%M%S).png; " +
                    "screencap -p \"\$out\" && echo \"Gespeichert: \$out\""

            "record_screen" -> {
                val seconds = intArg(call, "seconds", 15).coerceIn(5, 60)
                "out=/sdcard/Download/device-ai-recording-$(date +%Y%m%d-%H%M%S).mp4; " +
                    "screenrecord --time-limit $seconds \"\$out\"; echo \"Gespeichert: \$out\""
            }

            "open_android_settings" -> {
                val page = requiredString(call, "page").lowercase(Locale.GERMAN)
                val action = when {
                    "wifi" in page || "wlan" in page -> "android.settings.WIFI_SETTINGS"
                    "bluetooth" in page -> "android.settings.BLUETOOTH_SETTINGS"
                    "akku" in page || "battery" in page -> "android.settings.BATTERY_SAVER_SETTINGS"
                    "speicher" in page || "storage" in page -> "android.settings.INTERNAL_STORAGE_SETTINGS"
                    "app" in page -> "android.settings.APPLICATION_SETTINGS"
                    else -> "android.settings.SETTINGS"
                }
                "am start -a $action >/dev/null 2>&1 && echo 'Einstellungen geöffnet.'"
            }

            "get_network_summary" ->
                "echo '=== Interfaces ==='; ip -brief addr 2>/dev/null | head -30; " +
                    "echo; echo '=== Connectivity ==='; dumpsys connectivity 2>/dev/null | " +
                    "grep -E 'NetworkAgentInfo|VALIDATED|WIFI|MOBILE' | head -80"

            else -> error("Tool nicht implementiert: ${call.name}")
        }

    private fun resolvePackage(query: String): String? {
        val q = query.trim().lowercase(Locale.GERMAN)
        if (q.matches(Regex("[A-Za-z0-9_.]+")) && q.contains(".")) return q

        val aliases = mapOf(
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "shizuku" to "moe.shizuku.privileged.api"
        )
        aliases[q]?.let { return it }

        return context.packageManager.getInstalledApplications(0)
            .mapNotNull { app ->
                val label = runCatching {
                    context.packageManager.getApplicationLabel(app).toString()
                }.getOrNull() ?: return@mapNotNull null
                Triple(app.packageName, label, label.lowercase(Locale.GERMAN))
            }
            .sortedBy { it.second.length }
            .firstOrNull { (pkg, _, label) ->
                label == q || label.contains(q) || q.contains(label) ||
                    pkg.lowercase(Locale.GERMAN).contains(q)
            }
            ?.first
    }

    private fun rootsArg(call: ToolCallRequest): List<String> {
        val raw = call.arguments["roots"] ?: return emptyList()
        return when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            is String -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun requiredString(call: ToolCallRequest, key: String): String =
        stringArg(call, key)?.takeIf { it.isNotBlank() }
            ?: error("Argument fehlt: $key")

    private fun stringArg(call: ToolCallRequest, key: String): String? =
        call.arguments[key]?.toString()

    private fun intArg(call: ToolCallRequest, key: String, fallback: Int): Int =
        when (val value = call.arguments[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }

    private fun boolArg(call: ToolCallRequest, key: String, fallback: Boolean): Boolean =
        when (val value = call.arguments[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> fallback
        }

    private fun safeSharedPath(raw: String): String {
        val normalized = raw.trim()
            .replace("/storage/emulated/0", "/sdcard")
            .replace(Regex("/+"), "/")
        require(normalized.startsWith("/sdcard/")) { "Nur freigegebener Gerätespeicher ist erlaubt." }
        require(!normalized.contains("..")) { "Pfadnavigation mit '..' ist nicht erlaubt." }
        require(sharedRoots.any { normalized == it || normalized.startsWith("$it/") }) {
            "Pfad liegt außerhalb der freigegebenen Ordner."
        }
        return normalized
    }

    private fun safePackage(value: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) }
            ?: error("Ungültiger Paketname.")

    private fun sanitizeSearch(value: String): String =
        value.replace(Regex("[\\r\\n\\t;&|<>$]"), " ")
            .trim()
            .take(80)

    private fun escapeGrep(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_.-]"), "")

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

}
