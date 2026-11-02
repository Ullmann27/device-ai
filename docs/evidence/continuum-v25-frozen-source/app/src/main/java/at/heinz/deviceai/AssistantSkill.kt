package at.heinz.deviceai

interface AssistantSkill {
    val id: String
    val displayName: String
    val description: String
    val supportedPackages: List<String>
    val toolNames: List<String>
    val promptExtension: String
}

object LumoSkill : AssistantSkill {
    override val id = "lumo"
    override val displayName = "Lumo"
    override val description =
        "Diagnose, APK-Installation, Start/Stop und Logcat-Unterstützung für die Lumo-App."
    override val supportedPackages = listOf("at.heinz.lumo", "com.lumo", "lumo")
    override val toolNames = listOf(
        "get_app_info",
        "launch_app",
        "stop_app",
        "restart_app",
        "find_latest_apk",
        "install_apk",
        "read_logcat"
    )
    override val promptExtension =
        "Wenn der Nutzer Lumo nennt, prüfe zuerst installierte Paketnamen und Versionen. " +
            "Bei Fehleranalyse Logcat gezielt auf Lumo filtern. Installationen oder Neustarts immer über die freigegebenen Tools ausführen."
}
