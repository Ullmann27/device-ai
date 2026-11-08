package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors

/**
 * Universal presentation adapter of the cockpit (owner brief 05.11.2026: one mechanism for every real action,
 * not one animation per app). A real action – a centre the owner opened, a tool the ToolGate runner really
 * started – maps to one reusable scene of [JarvisWorkWindow]: which kind of surface unfolds, where it emerges
 * and its accent. Presentation only: it never grants a tool, never decides whether an action may run and
 * carries no data. The window body shows only what the real source returned.
 */
object JarvisActionScenes {
    enum class WorkspaceFootprint { CONVERSATION_IDLE, MEDIUM_TERMINAL, FULL_MISSION_BAY }
    enum class Kind { STATUS, MEMORY, PLANNING, WEB, LEARNING, DEVELOPMENT, APP, FILES, SCREEN, GENERIC }

    /** Inner visual grammar only; real content still comes from the owning tool or source. */
    enum class FuiProfile {
        DOCUMENT_BLUEPRINT,
        APP_CONDUIT,
        CODE_FORGE,
        SWARM_GRID,
        EVIDENCE_CONSTELLATION,
        CHRONO_GAUGE,
        SECURITY_XRAY,
        GENERIC
    }

    /** Where the window emerges: the tile the owner touched, or the point beside the face Jarvis glances at first. */
    sealed class Origin {
        data class Tile(val target: String) : Origin()
        object BesideFace : Origin()
    }

    data class Scene(
        val kind: Kind,
        val origin: Origin,
        val accent: Int,
        val footprint: WorkspaceFootprint = footprintFor(kind),
        val fuiProfile: FuiProfile = profileFor(kind)
    )

    /** Centre title → kind and the START tile that opens it (null: no tile, the window emerges beside the face). */
    private val centres = mapOf(
        "Systemstatus" to (Kind.STATUS to "system"),
        "Gedächtniszentrum" to (Kind.MEMORY to "memory"),
        "Logik und Planung" to (Kind.PLANNING to "logic"),
        "Web und Quellen" to (Kind.WEB to "web"),
        "Lernzentrum" to (Kind.LEARNING to "learning"),
        "Entwicklungszentrum" to (Kind.DEVELOPMENT to null)
    )

    /**
     * Scene of a centre panel, or null when the centre has no cockpit scene and keeps its dialog. [touchedTarget] is
     * the control the owner really touched (e.g. „Gerät“ opens Systemstatus): the window then emerges from it.
     */
    fun forCentre(title: String, touchedTarget: String? = null): Scene? = centres[title]?.let { (kind, tile) ->
        val from = touchedTarget?.takeIf { it.isNotBlank() } ?: tile
        Scene(kind, from?.let { Origin.Tile(it) } ?: Origin.BesideFace, accent(kind))
    }

    /** Scene of a tool the runner actually started; unknown or future tools get the neutral scene. */
    fun forTool(toolName: String): Scene = toolKind(toolName).let { Scene(it, Origin.BesideFace, accent(it)) }

    /** A document the owner picked: it arrives beside the face (no tile), in the files colour. */
    fun forDocument(): Scene = Scene(Kind.FILES, Origin.BesideFace, accent(Kind.FILES))

    /** Every entry of the real ToolRegistry has a kind; GENERIC only for names outside it. */
    fun toolKind(name: String): Kind = when (name) {
        "launch_app", "find_app", "get_app_info", "list_installed_apps", "restart_app", "stop_app",
        "install_apk", "uninstall_app", "find_latest_apk", "read_app_screen" -> Kind.APP
        "list_files", "find_files", "get_large_files", "move_file", "delete_file" -> Kind.FILES
        "take_screenshot", "record_screen" -> Kind.SCREEN
        "get_battery_info", "get_device_info", "get_network_summary", "get_storage_info", "list_processes",
        "open_android_settings" -> Kind.STATUS
        "read_logcat" -> Kind.DEVELOPMENT
        "web_search", "web_read" -> Kind.WEB
        else -> Kind.GENERIC
    }

    fun footprintFor(kind: Kind): WorkspaceFootprint = when (kind) {
        Kind.PLANNING, Kind.LEARNING, Kind.DEVELOPMENT -> WorkspaceFootprint.FULL_MISSION_BAY
        else -> WorkspaceFootprint.MEDIUM_TERMINAL
    }

    fun profileFor(kind: Kind): FuiProfile = when (kind) {
        Kind.FILES -> FuiProfile.DOCUMENT_BLUEPRINT
        Kind.APP -> FuiProfile.APP_CONDUIT
        Kind.DEVELOPMENT -> FuiProfile.CODE_FORGE
        Kind.PLANNING, Kind.LEARNING -> FuiProfile.SWARM_GRID
        Kind.WEB, Kind.MEMORY -> FuiProfile.EVIDENCE_CONSTELLATION
        Kind.STATUS, Kind.SCREEN -> FuiProfile.SECURITY_XRAY
        Kind.GENERIC -> FuiProfile.GENERIC
    }

    /** Leading colour of the trail and window rim; the rest of the gradient stays the shared cockpit material. */
    fun accent(kind: Kind): Int = when (kind) {
        Kind.STATUS, Kind.APP, Kind.GENERIC -> Colors.cyan
        Kind.MEMORY, Kind.LEARNING -> Colors.violet
        Kind.PLANNING, Kind.DEVELOPMENT -> Colors.magenta
        Kind.WEB -> Colors.cardRimEnd
        Kind.FILES, Kind.SCREEN -> Colors.cyanSoft
    }
}
