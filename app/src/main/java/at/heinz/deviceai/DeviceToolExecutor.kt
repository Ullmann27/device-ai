package at.heinz.deviceai

import android.content.Context
import java.util.Locale
import at.heinz.deviceai.systemintelligence.*

class DeviceToolExecutor internal constructor(
    private val context: Context,
    private val bridgeReady: () -> Boolean,
    private val runBridge: (String, (Result<String>) -> Unit) -> Unit,
    private val appActions: DeviceAppActionPort?,
    private val probeBridge: (((Result<ShizukuCapabilities>) -> Unit) -> Unit)? = null
) {
    constructor(context: Context, bridge: ShizukuBridge) :
        this(context, bridge::isReady, bridge::executeChecked, AndroidDeviceAppActions(context), bridge::probeCapabilities)
    internal val systemStorage by lazy { SystemStorageInspector(context) }
    @Volatile internal var lastDiagnosis: SystemDiagnosis? = null
        private set
    @Volatile internal var lastCachePreview: OwnCachePreview? = null
        private set
    @Volatile internal var lastStorageAnalysis: StorageAnalysis? = null
        private set
    @Volatile internal var lastCleanupUndoId: String? = null
        private set
    @Volatile internal var lastCleanupResult: Pair<String, OwnCacheCleanupResult>? = null
        private set
    private data class SelectedStorage(val token: String, val uris: List<android.net.Uri>, val deadline: Long)
    private val selectedStorage = java.util.concurrent.atomic.AtomicReference<SelectedStorage?>(null)

    internal fun selectStorage(uris: List<android.net.Uri>): String {
        require(uris.size in 1..64 && uris.all { it.scheme == "content" })
        val token = java.util.UUID.randomUUID().toString()
        selectedStorage.set(SelectedStorage(token, uris.toList(), android.os.SystemClock.elapsedRealtime() + 60_000))
        return token
    }
    internal fun clearStorageSelection() { selectedStorage.set(null); lastStorageAnalysis = null }
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
        if (call.name == SystemIntelligenceCommands.CAPABILITIES) {
            val probe = probeBridge
            if (probe == null) callback(Result.failure(IllegalStateException("SHIZUKU_PROBE_UNAVAILABLE")))
            else probe { result -> callback(result.map { caps ->
                "Android-API: ${caps.sdkInt}\nServer-UID: ${caps.serverUid}\nDienst-UID: ${caps.userServiceUid}" +
                    "\nIdentität: ${caps.identity}\nRoot-Expertenmodus: deaktiviert" +
                    "\nShell geeignet: ${caps.standardShellEligible}" +
                    "\nDiagnoseprogramme vorhanden: dumpsys=${caps.dumpsysExecutable}, pm=${caps.packageManagerExecutable}, cmd=${caps.commandServiceExecutable}" +
                    "\nVorhandensein beweist keine einzelne Befehlsberechtigung."
            }) }
            return
        }
        if (call.name in SystemIntelligenceCommands.localTools) {
            callback(runCatching { executeSystemTool(call) })
            return
        }
        if (call.name == "get_device_info") {
            callback(Result.success("Hersteller: ${android.os.Build.MANUFACTURER}\nModell: ${android.os.Build.MODEL}" +
                "\nAndroid: ${android.os.Build.VERSION.RELEASE}\nSDK: ${android.os.Build.VERSION.SDK_INT}" +
                "\nBuild: ${android.os.Build.DISPLAY}"))
            return
        }
        if (call.name == "list_installed_apps") {
            callback(runCatching {
                val userOnly = boolArg(call, "userOnly", true)
                val packages = context.packageManager.getInstalledApplications(0)
                    .filter { !userOnly || it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map { it.packageName }.sorted().take(400)
                "Von Android sichtbare Pakete (${packages.size}, maximal 400; keine Vollständigkeitszusage):\n" +
                    packages.joinToString("\n")
            })
            return
        }
        if (call.name == "get_app_info") {
            callback(runCatching {
                val pkg = resolvePackage(requiredString(call, "packageOrName")) ?: error("APP_NOT_FOUND")
                @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(pkg, 0)
                val version = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                    @Suppress("DEPRECATION") info.versionCode.toLong()
                }
                "Paket: ${info.packageName}\nVersion: ${info.versionName ?: "unbekannt"}\nVersionscode: $version" +
                    "\nApp-Speicher und Energieverbrauch hier nicht gemessen."
            })
            return
        }
        if (call.name == "open_android_settings") {
            callback(runCatching {
                val page = requiredString(call, "page").lowercase(Locale.GERMAN)
                val action = when (page) {
                    "wifi", "wlan" -> android.provider.Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                    "akku", "battery" -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "speicher", "storage" -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    "apps" -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
                    else -> android.provider.Settings.ACTION_SETTINGS
                }
                context.startActivity(android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                "Android-Einstellungsseite angefordert; keine Einstellung geändert. Sichtbares Ergebnis nicht geprüft."
            })
            return
        }
        if (call.name in setOf("launch_app", "read_app_screen")) {
            val adapter=appActions
            if(adapter==null)callback(Result.failure(IllegalStateException("NO_VERIFIED_DEVICE_ACTION_ADAPTER")))
            else startNativeAction(call,adapter,callback)
            return
        }
        if (call.name == "find_app") {
            callback(runCatching { AndroidInstalledAppLookup.search(context, call.arguments["query"] as String) })
            return
        }
        // App-volume statistics need neither Shizuku nor access to personal files.
        // Exceptions propagate to the existing ToolGate as FAILED, never as a
        // successful string containing an error. The runner consumes Result explicitly.
        if (call.name == "get_storage_info") {
            callback(runCatching { DeviceStorageProbe.read(context.filesDir) })
            return
        }
        // Android owns connectivity state; it does not require a shell, Binder
        // bridge or private device data. Do not treat a failed shell as offline.
        if (call.name == "get_network_summary") {
            callback(runCatching { DeviceNetworkProbe.read(context) })
            return
        }
        // Battery data comes from Android's sticky system broadcast, not a shell dump.
        if (call.name == "get_battery_info") {
            callback(runCatching { DeviceBatteryProbe.read(context) })
            return
        }
        if (!bridgeReady()) {
            callback(Result.failure(IllegalStateException("Shizuku ist nicht verbunden.")))
            return
        }

        val command = runCatching { buildCommand(call) }
            .getOrElse {
                callback(Result.failure(it))
                return
            }

        runBridge(command, callback)
    }

    private fun executeSystemTool(call: ToolCallRequest): String {
        val worker = Thread.currentThread()
        fun cancelled() = worker.isInterrupted
        return when (call.name) {
            SystemIntelligenceCommands.SAVE -> {
                val snapshot = lastDiagnosis ?: error("NO_SYSTEM_SNAPSHOT")
                check(requiredString(call, "snapshotId") == snapshot.observedAtMillis.toString()) { "SYSTEM_SNAPSHOT_CHANGED" }
                val id = systemStorage.retainDiagnosis(DiagnosticCacheRecord(snapshot.observedAtMillis,
                    snapshot.storageAvailableBytes, snapshot.ownPssBytes, snapshot.batteryPercent, snapshot.thermalStatus), ::cancelled)
                "Technische Momentaufnahme lokal gespeichert: $id. Keine persönlichen Inhalte."
            }
            SystemIntelligenceCommands.DIAGNOSE -> {
                val sample = AndroidSystemDiagnosis(context).capture(::cancelled)
                check(!cancelled()) { "DIAGNOSIS_CANCELLED" }
                lastDiagnosis = sample
                sample.report()
            }
            SystemIntelligenceCommands.PREVIEW -> systemStorage.previewOwnCache(::cancelled).let {
                lastCachePreview = it
                "Bereinigungsvorschau: ${it.entries.size} technische Berichte; ${it.eligibleBytes} Byte." +
                    "\nGeschützte Dateien: ${it.protectedFileCount}. Vollständig: ${it.complete}. Noch nichts verändert."
            }
            SystemIntelligenceCommands.CLEANUP -> systemStorage.cleanupOwnCache(
                requiredString(call, "previewId"), requiredString(call, "planDigest"), ::cancelled).let {
                lastCachePreview = null
                if (it.undoId != null) lastCleanupUndoId = it.undoId
                lastCleanupResult = call.callId to it
                if (it.status != "COMPLETED") throw ToolBackendFailure(ToolExecutionStatus.FAILED, "CACHE_${it.status}")
                "Cache-Ergebnis: ${it.status}; ${it.removedFiles} Berichte; logisch ${it.logicalBytesRemoved} Byte." +
                    "\nGemessene Platzänderung: ${it.availableBytesDelta?.let { delta -> "$delta Byte" } ?: "nicht verfügbar"}. Rücknahme: ${it.undoId != null}. Kein Geschwindigkeitsnachweis."
            }
            SystemIntelligenceCommands.RESTORE -> systemStorage.restoreOwnCache(requiredString(call, "undoId"), ::cancelled).let {
                lastCleanupResult = call.callId to it
                if (it.status != "RESTORED") throw ToolBackendFailure(ToolExecutionStatus.FAILED, "CACHE_${it.status}")
                lastCleanupUndoId = null
                "Wiederherstellung: ${it.status}."
            }
            SystemIntelligenceCommands.FILES -> {
                val selected = selectedStorage.getAndSet(null) ?: error("NO_SELECTED_STORAGE_SCOPE")
                check(selected.token == requiredString(call, "scopeToken") &&
                    android.os.SystemClock.elapsedRealtime() <= selected.deadline) { "STORAGE_SCOPE_EXPIRED" }
                systemStorage.analyzeSelectedDocuments(selected.uris, ::cancelled).also {
                    check(!cancelled()) { "STORAGE_SCAN_CANCELLED" }
                    lastStorageAnalysis = it
                }.summaryForTool()
            }
            else -> error("UNSUPPORTED_SYSTEM_TOOL")
        }
    }

    private class PendingAction(val callId:String) {
        val cancelled=java.util.concurrent.atomic.AtomicBoolean()
        @Volatile var thread:Thread?=null
    }
    private val pendingAction=java.util.concurrent.atomic.AtomicReference<PendingAction?>(null)
    private fun startNativeAction(call:ToolCallRequest,adapter:DeviceAppActionPort,callback:(Result<String>)->Unit){
        val pending=PendingAction(call.callId)
        if(!pendingAction.compareAndSet(null,pending)){
            callback(Result.failure(ToolBackendFailure(ToolExecutionStatus.FAILED,"ANOTHER_DEVICE_ACTION_ACTIVE")));return
        }
        val thread=Thread({
            var outcome:Result<String>?=null
            try {
                if(!pending.cancelled.get())adapter.execute(call,{pending.cancelled.get()}){result->if(outcome==null)outcome=result}
            }catch(error:Exception){outcome=Result.failure(error)}
            finally{pendingAction.compareAndSet(pending,null)}
            callback(if(pending.cancelled.get())Result.failure(ToolBackendFailure(ToolExecutionStatus.CANCELLED,"DEVICE_ACTION_CANCELLED"))
                else outcome?:Result.failure(IllegalStateException("DEVICE_ACTION_NO_COMPLETION")))
        },"continuum-native-action").apply{isDaemon=true}
        pending.thread=thread
        try{thread.start()}catch(error:RuntimeException){pendingAction.compareAndSet(pending,null);callback(Result.failure(error))}
    }
    fun cancelNativeRequest(callId:String){
        pendingAction.get()?.takeIf{it.callId==callId}?.let{
            it.cancelled.set(true);appActions?.cancelPending();it.thread?.interrupt()
        }
    }
    fun cancelPendingActions(){pendingAction.get()?.let{cancelNativeRequest(it.callId)}}

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
