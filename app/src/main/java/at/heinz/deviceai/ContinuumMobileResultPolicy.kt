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
        INVALID_RESULT,
        PROJECT_CREATED,
        PROJECT_SOURCES_VERIFIED,
        PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE,
        PROJECT_STATUS_REPORTED
    }

    fun evaluate(
        operation: ContinuumMobileBuildPlan.Operation,
        exitCode: Int?,
        termuxError: Int?,
        reportedStatus: String?,
        apkSha256: String? = null,
        signerSha256: String? = null,
        sourceCommit: String? = null,
        packageName: String? = null,
        versionCode: Int? = null,
        sourceSha256: String? = null,
        recipeId: String? = null,
        expectedProject: ContinuumOwnedProject = ContinuumOwnedProject.NOTES,
        planSha256: String? = null,
        toolchainProfile: String? = null,
        verification: String? = null,
        buildStatus: String? = null
    ): Outcome {
        if (exitCode == null || termuxError != -1) return Outcome.INVALID_RESULT
        if (exitCode != 0) {
            return if (operation == ContinuumMobileBuildPlan.Operation.DOCTOR &&
                reportedStatus == "UNAVAILABLE"
            ) Outcome.TOOLCHAIN_UNAVAILABLE else if (operation == ContinuumMobileBuildPlan.Operation.BUILD) Outcome.BUILD_FAILED
            else if (operation == ContinuumMobileBuildPlan.Operation.PROJECT_BUILD) Outcome.BUILD_FAILED
            else Outcome.EXECUTION_FAILED
        }
        val projectBound = recipeId == expectedProject.id && packageName == expectedProject.packageName &&
            planSha256 == expectedProject.planSha256 && sourceSha256 != null &&
            Regex("[0-9a-f]{64}").matches(sourceSha256)
        val apkBound = apkSha256 != null && Regex("[0-9a-f]{64}").matches(apkSha256) &&
            signerSha256 != null && Regex("[0-9a-f]{64}").matches(signerSha256) && versionCode == 1
        return when (operation) {
            ContinuumMobileBuildPlan.Operation.DOCTOR ->
                if (reportedStatus == "READY_FOR_EXPERIMENTAL_BUILD") Outcome.TOOLCHAIN_READY
                else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.BUILD ->
                if (reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" &&
                    apkSha256 != null && Regex("[0-9a-f]{64}").matches(apkSha256) &&
                    signerSha256 != null && Regex("[0-9a-f]{64}").matches(signerSha256) &&
                    sourceCommit != null && Regex("[0-9a-f]{40}").matches(sourceCommit) &&
                    packageName == "at.heinz.deviceai.preview" &&
                    versionCode != null && versionCode in 1..2_100_000_000
                ) {
                    Outcome.BUILD_REPORTED_UNVERIFIED_ON_DEVICE
                } else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.PROJECT_CREATE ->
                if (reportedStatus == "PROJECT_CREATED_NOT_BUILT" &&
                    projectBound
                ) Outcome.PROJECT_CREATED else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.PROJECT_VERIFY ->
                if (reportedStatus == "SOURCE_VERIFIED" &&
                    projectBound
                ) Outcome.PROJECT_SOURCES_VERIFIED else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.PROJECT_BUILD ->
                if (reportedStatus == "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED" &&
                    projectBound && apkBound && toolchainProfile == "termux-arm64" && verification == "CONFIRMED"
                ) Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE else Outcome.INVALID_RESULT
            ContinuumMobileBuildPlan.Operation.PROJECT_STATUS -> when {
                reportedStatus == "NO_PROJECT_BUILD_REPORT" && recipeId == expectedProject.id -> Outcome.PROJECT_STATUS_REPORTED
                reportedStatus == "PROJECT_BUILD_STATUS" && projectBound && buildStatus in projectBuildStatuses &&
                    verification in setOf("CONFIRMED", "INCONCLUSIVE") &&
                    toolchainProfile in setOf("termux-arm64", "host-sdk") &&
                    (verification != "CONFIRMED" ||
                        buildStatus == "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED" && apkBound) ->
                    Outcome.PROJECT_STATUS_REPORTED
                else -> Outcome.INVALID_RESULT
            }
            ContinuumMobileBuildPlan.Operation.STATUS ->
                if (reportedStatus == "NO_BUILD_REPORT" ||
                    reportedStatus == "EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED" ||
                    reportedStatus == "BLOCKED_MISSING_TOOLCHAIN" ||
                    reportedStatus == "BLOCKED_UNCLEAN_SOURCE" ||
                    reportedStatus == "BLOCKED_SOURCE_CHANGED_DURING_BUILD" ||
                    reportedStatus == "FAILED_BUILD" ||
                    reportedStatus == "FAILED_NO_APK" ||
                    reportedStatus == "FAILED_APK_SIGNATURE" ||
                    reportedStatus == "FAILED_APK_IDENTITY"
                ) Outcome.STATUS_REPORTED else Outcome.INVALID_RESULT
        }
    }

    private val projectBuildStatuses = setOf(
        "EXPERIMENTAL_PROJECT_APK_BUILT_NOT_DEVICE_TESTED", "BUILD_RUNNING", "BUILD_CANCELLED", "BUILD_TIMED_OUT",
        "BUILD_TERMINATION_UNCONFIRMED", "BLOCKED_UNSAFE_BUILD_OUTPUT", "BLOCKED_SOURCE_CHANGED_DURING_BUILD",
        "FAILED_NO_APK", "FAILED_BUILD_EVIDENCE", "FAILED_APK_SIGNATURE", "FAILED_ARTIFACT_COPY",
        "FAILED_ARTIFACT_CHANGED", "FAILED_APK_IDENTITY", "FAILED_BUILD_IO", "FAILED_BUILD_STATE", "FAILED_BUILD"
    )

    fun projectStatusExplanation(status: String?, verification: String?, profile: String?): String = when {
        status == "NO_PROJECT_BUILD_REPORT" -> "Für dieses Projekt liegt noch kein gespeicherter Buildbeleg vor."
        verification == "CONFIRMED" && profile == "host-sdk" ->
            "Historischer Host-SDK-Build: Quellen, Tests, Lint und APK-Datei stimmen noch mit dem Beleg überein. " +
                "Das ist kein Termux- oder Fold7-Nachweis. Keine Installation bestätigt."
        verification == "CONFIRMED" ->
            "Historischer M4-Buildbeleg: Quellen, Tests, Lint und APK-Datei wurden erneut abgeglichen. " +
                "Installation und Geräteabnahme stehen weiterhin aus."
        status == "BUILD_RUNNING" ->
            "Gespeicherter M4-Auftrag läuft oder wurde unterbrochen. Vor einer Wiederholung Termux prüfen; kein Abschluss bestätigt."
        else -> "Der gespeicherte M4-Auftrag hat keinen aktuell bestätigten APK-Abschlussbeleg. Kein neuer Erfolg nachgewiesen."
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
        "FAILED_APK_IDENTITY" ->
            "Gespeicherter Buildbericht: Paketkennung oder APK-Version nicht bestätigt. " +
                "Das Artefakt wurde nicht freigegeben."
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
        Outcome.PROJECT_CREATED ->
            "Das gewählte M4-Projekt wurde als eigenständige Android-App-Quelle erzeugt. Noch keine APK gebaut."
        Outcome.PROJECT_SOURCES_VERIFIED ->
            "Die fest vorgegebenen M4-Projektquellen stimmen mit der Vorlage überein. Noch kein APK-Build bestätigt."
        Outcome.PROJECT_BUILD_REPORTED_UNVERIFIED_ON_DEVICE ->
            "Termux meldet eine signatur- und paketgeprüfte M4-Projekt-APK. Weder Installation noch Gerätetest bestätigt."
        Outcome.PROJECT_STATUS_REPORTED ->
            "Ein gespeicherter M4-Projektbeleg wurde erneut gelesen; das ist kein neuer Build."
    }
}
