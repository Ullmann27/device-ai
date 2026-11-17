package at.heinz.deviceai

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * The installed app a tool call names, for its cockpit scene (owner V1 06.11.2026: „echtes Icon fliegt auf einer
 * Lichtbahn vor JARVIS“). Resolved exactly like the executor resolves a launch (AndroidDeviceAppActions
 * .resolveExactLauncher): only an explicit package name that has an installed launcher. A label the model wrote,
 * an unknown or uninstalled package yields null – the scene then stays neutral; no icon or name is ever invented.
 * Presentation only: it grants nothing and does not decide whether the tool may run.
 */
data class ToolAppIdentity(val packageName: String, val label: String, val icon: Drawable?) {
    companion object {
        /** Tools whose single target is one installed app (argument „packageOrName“). */
        val APP_TOOLS = setOf("launch_app", "get_app_info", "restart_app", "stop_app", "uninstall_app")

        fun resolve(pm: PackageManager, toolName: String, arguments: Map<String, Any?>): ToolAppIdentity? {
            if (toolName !in APP_TOOLS) return null
            val target = arguments["packageOrName"]
            if (!ToolRegistry.isExplicitAppTarget(target)) return null
            val pkg = target as String
            if (runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() == null) return null
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
            val label = runCatching { pm.getApplicationLabel(info).toString().trim() }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: return null
            return ToolAppIdentity(pkg, label.take(48), runCatching { pm.getApplicationIcon(info) }.getOrNull())
        }
    }
}
