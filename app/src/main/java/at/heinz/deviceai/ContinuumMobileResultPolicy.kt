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
        reportedStatus: String?,
        apkSha256: String? = null,
        signerSha256: String? = null
    ): Outcome {
        if (exitCode == null || termuxError != -1) return Outcome.INVALID_RESULT
        if (exitCode != 0) {
            return if (operation == ContinuumMobileBuildPlan.Operation.DOCTOR &&
                reportedStatus == "UNAVAILABLE"
            ) Outcome.TOOLCHAIN_UNAVAILABLE else if (operation == ContinuumMobileBuildPlan.Operation.BUILD) Outcome.BUILD_FAILED
            else Outcome.EXECUTION_FAILED
        }
        return when (operation) {
            ContinuumMobileBuildPlan.Operation.DOCTOR ->
                if (reportedStatus == "READY_FOR_EXPERIMENTAL_BUILD") Outcome.TOOLCHAIN_READY
                else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.BUILD ->
                if (reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" &&
                    apkSha256 != null && Regex("[0-9a-f]{64}").matches(apkSha256) &&
                    signerSha256 != null && Regex("[0-9a-f]{64}").matches(signerSha256)
                ) {
                    Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE
                } else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.STATUS ->
                if (reportedStatus == "NO_BUILD_REPORT" ||
                    reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" ||
                    reportedStatus == "BLOCKED_MISSING_TOOLCHAIN" ||
                    reportedStatus == "BLOCKED_UNCLEAN_SOURCE" ||
                    reportedStatus == "BLOCKED_SOURCE_CHANGED_DURING_BUILD" ||
                    reportedStatus == "FAILED_BUILD" ||
                    reportedStatus == "FAILED_NO_APK" ||
                    reportedStatus == "FAILED_APK_SIGNATURE"
                ) Outcome.STATUS_REPORTED else Outcome.INVALID_RESULT
        }
    }

    /**
     * STATUS is a read-only query. It must display persisted historical
     * failures honestly, not silently reclassify them as a fresh build success.
     * Never echo arbitrary callback text into the UI.
     */
    fun statusExplanation(reportedStatus: String?): String = when (reportedStatus) {
        "NO_BUILD_REPORT" -> "In Termux liegt noch kein gespeicherter Buildbericht vor."
        "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" ->
            "Gespeicherter Buildbericht: Eine experimentelle APK wurde gemeldet. " +
                "Dieser Bericht ersetzt weder Installation noch einen Gerätetest."
        "BLOCKED_MISSING_TOOLCHAIN" ->
            "Gespeicherter Buildbericht: Der damalige Build wurde wegen fehlender " +
                "Werkzeuge blockiert. Der aktuelle Werkzeugzustand ist getrennt zu prüfen."
        "BLOCKED_UNCLEAN_SOURCE" ->
            "Gespeicherter Buildbericht: Der Build wurde wegen ungesicherter " +
                "Quellcodeänderungen blockiert. Es erfolgte keine APK-Freigabe."
        "BLOCKED_SOURCE_CHANGED_DURING_BUILD" ->
            "Gespeicherter Buildbericht: Dateien oder Git-HEAD haben sich während " +
                "des Builds verändert. Die APK-Freigabe wurde aus Sicherheitsgründen gestoppt."
        "FAILED_BUILD" ->
            "Gespeicherter Buildbericht: Gradle oder ein Test ist fehlgeschlagen. " +
                "Es wurde kein neuer Build-Erfolg bestätigt."
        "FAILED_NO_APK" ->
            "Gespeicherter Buildbericht: Keine neue APK nachgewiesen."
        "FAILED_APK_SIGNATURE" ->
            "Gespeicherter Buildbericht: Die APK-Signaturprüfung ist fehlgeschlagen. " +
                "Das Artefakt wurde nicht freigegeben."
        else -> "Buildbericht unbekannt oder ungültig. Kein Erfolg nachgewiesen."
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
