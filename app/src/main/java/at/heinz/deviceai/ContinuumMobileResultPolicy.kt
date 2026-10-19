package at.heinz.deviceai

/**
 * EXP-08: classify a Termux RUN_COMMAND callback; exit 0 alone is not a valid build proof.
 * Source of the result keys: official termux-shared TermuxConstants.java (MIT).
 */
internal object ContinuumMobileResultPolicy {
    enum class Outcome {
        TOOLCHAIN_READY,
        TOOLCHAIN_UNAVAILABLE,
        BUILD_REPORTED_UNVERIFIED_ON_DEVICE,
        BUILD_FAILED,
        STATUS_REPORTED,
        EXECUTION_FAILED,
        INVALID_RESULT
    }

    fun evaluate(
        operation: ContinuumMobileBuildPlan.Operation,
        exitCode: Int?,
        termuxError: Int?,
        reportedStatus: String?
    ): Outcome {
        if (exitCode == null || termuxError != -1) return Outcome.INVALID_RESULT
        if (exitCode != 0) {
            return if (operation == ContinuumMobileBuildPlan.Operation.DOCTOR &&
                reportedStatus == "UNAVAILABLE"
            ) Outcome.TOOLCHAIN_UNAVAILABLE else Outcome.EXECUTION_FAILED
        }
        return when (operation) {
            ContinuumMobileBuildPlan.Operation.DOCTOR ->
                if (reportedStatus == "READY_FOR_EXPERIMENTAL_BUILD") Outcome.TOOLCHAIN_READY
                else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.BUILD ->
                if (reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED") {
                    Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE
                } else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.STATUS ->
                if (reportedStatus == "NO_BUILD_REPORT" ||
                    reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" ||
                    reportedStatus == "BLOCKED_MISSING_TOOLCHAIN" ||
                    reportedStatus == "BLOCKED_UNCLEAN_SOURCE" ||
                    reportedStatus == "FAILED_BUILD" ||
                    reportedStatus == "FAILED_NO_APK" ||
                    reportedStatus == "FAILED_APK_SIGNATURE"
                ) Outcome.STATUS_REPORTED else Outcome.INVALID_RESULT
        }
    }

    fun explanation(outcome: Outcome): String = when (outcome) {
        Outcome.TOOLCHAIN_READY -> "Lokale Termux-Werkzeuge haben den Doctor-Test bestanden. Noch kein APK-Bau nachgewiesen."
        Outcome.TOOLCHAIN_UNAVAILABLE -> "Mindestens ein Werkzeug fehlt oder ist nicht bereit. Termux-Toolchain prüfen."
        Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE ->
            "Termux meldet eine signaturgeprüfte APK. Das ist noch keine unabhängige Geräte- oder Installationsabnahme."
        Outcome.BUILD_FAILED -> "Der mobile Build ist fehlgeschlagen."
        Outcome.STATUS_REPORTED -> "Ein lokaler Buildbericht wurde von Termux zurückgemeldet."
        Outcome.EXECUTION_FAILED -> "Der Termux-Befehl ist fehlgeschlagen oder wurde abgebrochen."
        Outcome.INVALID_RESULT -> "Keine verlässliche Termux-Antwort; kein erfolgreicher Build nachgewiesen."
    }
}
