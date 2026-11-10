package at.heinz.deviceai

enum class ShizukuIdentity { ADB_SHELL, ROOT, UNSUPPORTED }

data class ShizukuCapabilities(
    val serverUid: Int,
    val userServiceUid: Int,
    val sdkInt: Int,
    val serverVersion: Int,
    val dumpsysExecutable: Boolean,
    val packageManagerExecutable: Boolean,
    val commandServiceExecutable: Boolean
) {
    val identity: ShizukuIdentity get() = when (userServiceUid) {
        2000 -> ShizukuIdentity.ADB_SHELL
        0 -> ShizukuIdentity.ROOT
        else -> ShizukuIdentity.UNSUPPORTED
    }
    val rootExpertEnabled: Boolean get() = false
    // Executable presence grants no permission.
    val standardShellEligible: Boolean get() = identity == ShizukuIdentity.ADB_SHELL
}

internal object ShizukuCapabilityProbe {
    const val COMMAND = "printf 'CONTINUUM_SHIZUKU_CAPABILITIES_V1\\n'; " +
        "id -u; getprop ro.build.version.sdk; " +
        "if test -x /system/bin/dumpsys; then echo DUMPSYS=1; else echo DUMPSYS=0; fi; " +
        "if test -x /system/bin/pm; then echo PM=1; else echo PM=0; fi; " +
        "if test -x /system/bin/cmd; then echo CMD=1; else echo CMD=0; fi"

    fun parse(output: String, serverUid: Int, expectedSdk: Int, serverVersion: Int): ShizukuCapabilities {
        require(output.length <= 256) { "SHIZUKU_PROBE_OUTPUT_LIMIT" }
        val lines = output.removeSuffix("\n").split('\n')
        require(lines.size == 6 && lines[0] == "CONTINUUM_SHIZUKU_CAPABILITIES_V1") {
            "SHIZUKU_PROBE_INVALID_FRAME"
        }
        fun number(value: String): Int {
            val parsed = value.toIntOrNull()
            require(parsed != null && parsed >= 0 && parsed.toString() == value) { "SHIZUKU_PROBE_INVALID_NUMBER" }
            return parsed
        }
        fun flag(value: String, key: String): Boolean = when (value) {
            "$key=1" -> true
            "$key=0" -> false
            else -> error("SHIZUKU_PROBE_INVALID_FLAG")
        }
        val actualUid = number(lines[1])
        val actualSdk = number(lines[2])
        require(actualUid == serverUid && serverUid >= 0) { "SHIZUKU_IDENTITY_MISMATCH" }
        require(actualSdk == expectedSdk && actualSdk >= 23) { "SHIZUKU_ANDROID_VERSION_MISMATCH" }
        require(serverVersion > 0) { "SHIZUKU_SERVER_VERSION_UNKNOWN" }
        return ShizukuCapabilities(serverUid, actualUid, actualSdk, serverVersion,
            flag(lines[3], "DUMPSYS"), flag(lines[4], "PM"), flag(lines[5], "CMD"))
    }
}
