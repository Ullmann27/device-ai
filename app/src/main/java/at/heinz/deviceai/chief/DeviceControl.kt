package at.heinz.deviceai.chief

import at.heinz.deviceai.ToolBackendFailure
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionStatus
import org.json.JSONObject

/**
 * Device/screen control tools for the multi-step task loop (Lumo, 03.11.2026).
 *
 * These tools are deliberately NOT in the global [at.heinz.deviceai.ToolRegistry] (HAUPT,
 * 03.11.2026): the chat model never sees them. They exist only in the scope of a
 * [TaskLoopJob] (see [JobScopedTools]) and every risky call is approved per step by the
 * loop's [TaskApprovalGate]. This file contains no approval logic of its own.
 *
 * Opening apps, reading the screen and screenshots deliberately reuse the existing tools
 * `launch_app`, `read_app_screen` and `take_screenshot`; they are not duplicated here.
 */
object DeviceControlTools {
    const val CAPABILITIES = "device_capabilities"
    const val TAP = "device_tap"
    const val LONG_PRESS = "device_long_press"
    const val SWIPE = "device_swipe"
    const val TYPE_TEXT = "device_type_text"
    const val BACK = "device_back"
    const val HOME = "device_home"
    const val TAP_ELEMENT = "device_tap_element"

    val NAMES = setOf(CAPABILITIES, TAP, LONG_PRESS, SWIPE, TYPE_TEXT, BACK, HOME, TAP_ELEMENT)

    /** Integer arguments; ToolProposal accepts Number or integer strings for these keys. */
    val INTEGER_ARGUMENTS = setOf("x", "y", "toX", "toY", "durationMillis")

    const val MAX_COORDINATE = 10_000
    const val MAX_TEXT = 500
}

/** What the device can do right now. Detected live, never assumed. */
data class DeviceControlCapabilities(
    val shizukuReady: Boolean,
    val accessibilityConnected: Boolean,
    /** AccessibilityService was granted CAPABILITY_CAN_PERFORM_GESTURES (API 24+). */
    val accessibilityGestures: Boolean,
    /** Verified label click via the existing ScreenObservationBus/VerifiedNodeClick path. */
    val verifiedNodeClick: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", "CONTINUUM_DEVICE_CONTROL_CAPABILITIES_V1")
        .put("shizukuInput", shizukuReady)
        .put("accessibilityGlobalActions", accessibilityConnected)
        .put("accessibilityGestures", accessibilityGestures)
        .put("verifiedNodeClick", verifiedNodeClick)
        .put("textEntryAscii", shizukuReady || accessibilityConnected)
        .put("textEntryUnicode", accessibilityConnected)

    companion object {
        val NONE = DeviceControlCapabilities(false, false, false, false)
    }
}

/** The input may already have reached the screen; callers must not retry or fall back. */
class DeviceDispatchUncertain(message: String) : RuntimeException(message)

enum class ControlPath { SHIZUKU_INPUT, ACCESSIBILITY_GLOBAL_ACTION, ACCESSIBILITY_GESTURE, ACCESSIBILITY_SET_TEXT, VERIFIED_NODE_CLICK }

/** Android accessibility side of device control. Implemented by AndroidAccessibilityControl. */
interface AccessibilityControlPort {
    fun back(): Boolean
    fun home(): Boolean
    /** Dispatches a stroke and waits (bounded) for the system's completion callback. */
    fun gesture(x: Int, y: Int, toX: Int, toY: Int, durationMillis: Long): Boolean
    /** Sets text on the single focused, editable, non-password node only. */
    fun setFocusedText(text: String): Boolean
    /** Existing verified label click (VerifiedNodeClick); returns a JSON receipt. */
    fun tapElement(packageName: String, label: String, expectedLabel: String, cancelled: () -> Boolean): String
}

/** Pure validation of device-control arguments; no Android types. */
internal object DeviceControlArguments {
    fun int(call: ToolCallRequest, key: String, default: Int? = null, range: IntRange): Int {
        val raw = call.arguments[key]
        val value = when (raw) {
            null -> default ?: throw IllegalArgumentException("ARGUMENT_MISSING:$key")
            is Number -> raw.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        } ?: throw IllegalArgumentException("ARGUMENT_NOT_INTEGER:$key")
        require(value in range) { "ARGUMENT_OUT_OF_RANGE:$key" }
        return value
    }

    fun coordinate(call: ToolCallRequest, key: String) = int(call, key, range = 0..DeviceControlTools.MAX_COORDINATE)

    fun text(call: ToolCallRequest): String {
        val text = call.arguments["text"] as? String ?: throw IllegalArgumentException("ARGUMENT_MISSING:text")
        require(text.isNotEmpty() && text.length <= DeviceControlTools.MAX_TEXT) { "TEXT_LENGTH" }
        require(text.none { it.isISOControl() }) { "TEXT_CONTROL_CHARACTERS" }
        return text
    }

    fun label(call: ToolCallRequest, key: String): String {
        val value = call.arguments[key] as? String ?: throw IllegalArgumentException("ARGUMENT_MISSING:$key")
        require(value.length in 1..200 && value == value.trim() && value.none { it.isISOControl() }) { "INVALID_LABEL:$key" }
        return value
    }

    /** `input text` only handles a safe ASCII subset reliably; everything else uses accessibility. */
    fun isShellSafeAscii(text: String): Boolean = text.all { it in ' '..'~' && it !in SHELL_UNSAFE }

    private val SHELL_UNSAFE = setOf('\'', '"', '\\', '`', '$', ';', '&', '|', '<', '>', '(', ')', '*', '?', '!', '#', '~', '{', '}', '[', ']')
}

/** Fixed command templates for Shizuku `input`. Only validated integers / filtered ASCII. */
internal object ShizukuInputCommands {
    fun tap(x: Int, y: Int) = "input tap $x $y"
    fun swipe(x: Int, y: Int, toX: Int, toY: Int, durationMillis: Int) = "input swipe $x $y $toX $toY $durationMillis"
    fun longPress(x: Int, y: Int, durationMillis: Int) = "input swipe $x $y $x $y $durationMillis"
    fun back() = "input keyevent 4"
    fun home() = "input keyevent 3"
    fun text(text: String): String {
        require(DeviceControlArguments.isShellSafeAscii(text)) { "TEXT_NOT_SHELL_SAFE" }
        // `input text` treats %s as space; a literal space would split arguments.
        return "input text '" + text.replace("%", "\\%").replace(" ", "%s") + "'"
    }
}

/**
 * Routes one device-control call to Shizuku or accessibility.
 *
 * Fallback rule (deliberately strict): the secondary path is used only when the primary
 * path is *unavailable* according to live capability detection. A primary path that was
 * dispatched and then failed is never silently repeated via another path, because the
 * first input may already have reached the screen (double tap / double text).
 */
class DeviceControlRouter(
    private val capabilities: () -> DeviceControlCapabilities,
    private val runShizuku: (String, (Result<String>) -> Unit) -> Unit,
    private val accessibility: AccessibilityControlPort?
) {
    fun execute(call: ToolCallRequest, cancelled: () -> Boolean = { false }, callback: (Result<String>) -> Unit) {
        val outcome = runCatching { plan(call) }
        val route = outcome.getOrElse { callback(Result.failure(it)); return }
        if (cancelled()) {
            callback(Result.failure(ToolBackendFailure(ToolExecutionStatus.CANCELLED, "DEVICE_CONTROL_CANCELLED"))); return
        }
        when (route) {
            is Route.Report -> callback(Result.success(route.json.toString()))
            is Route.Shell -> runShizuku(route.command) { result ->
                // Once the command was handed to Shizuku, a failure no longer proves that
                // nothing happened on screen: report it as UNCERTAIN, never as retryable.
                callback(result.fold(
                    { Result.success(receipt(call.name, ControlPath.SHIZUKU_INPUT, it.trim().take(200))) },
                    { Result.failure(DeviceDispatchUncertain("SHIZUKU_INPUT_FAILED_AFTER_DISPATCH: ${it.message?.take(160)}")) }
                ))
            }
            is Route.Accessibility -> {
                val port = accessibility
                if (port == null) { callback(Result.failure(IllegalStateException("ACCESSIBILITY_CONTROL_UNAVAILABLE"))); return }
                val accepted = try {
                    if (route.path == ControlPath.VERIFIED_NODE_CLICK) {
                        callback(Result.success(port.tapElement(
                            DeviceControlArguments.label(call, "packageOrName"),
                            DeviceControlArguments.label(call, "label"),
                            DeviceControlArguments.label(call, "expectedLabel"), cancelled)))
                        return
                    }
                    route.action(port)
                } catch (e: Exception) {
                    callback(Result.failure(DeviceDispatchUncertain("ACCESSIBILITY_ACTION_FAILED: ${e.message?.take(160)}"))); return
                }
                // false = Android refused the action before dispatch; nothing reached the screen.
                callback(if (accepted) Result.success(receipt(call.name, route.path, ""))
                    else Result.failure(ToolBackendFailure(ToolExecutionStatus.FAILED, "ACCESSIBILITY_ACTION_NOT_ACCEPTED")))
            }
        }
    }

    internal sealed interface Route {
        data class Report(val json: JSONObject) : Route
        data class Shell(val command: String) : Route
        class Accessibility(val path: ControlPath, val action: (AccessibilityControlPort) -> Boolean) : Route
    }

    /** Pure routing decision; exposed for tests. */
    internal fun plan(call: ToolCallRequest): Route {
        require(call.name in DeviceControlTools.NAMES) { "NOT_A_DEVICE_CONTROL_TOOL" }
        val caps = capabilities()
        val a11y = accessibility != null && caps.accessibilityConnected
        val gestures = a11y && caps.accessibilityGestures
        fun unavailable(): Nothing = throw ToolBackendFailure(ToolExecutionStatus.FAILED,
            "NO_CONTROL_PATH_AVAILABLE:${call.name} (Shizuku=${caps.shizukuReady}, Bedienungshilfe=${caps.accessibilityConnected}, Gesten=${caps.accessibilityGestures})")
        return when (call.name) {
            DeviceControlTools.CAPABILITIES -> Route.Report(caps.toJson())
            DeviceControlTools.BACK -> when {
                a11y -> Route.Accessibility(ControlPath.ACCESSIBILITY_GLOBAL_ACTION) { it.back() }
                caps.shizukuReady -> Route.Shell(ShizukuInputCommands.back())
                else -> unavailable()
            }
            DeviceControlTools.HOME -> when {
                a11y -> Route.Accessibility(ControlPath.ACCESSIBILITY_GLOBAL_ACTION) { it.home() }
                caps.shizukuReady -> Route.Shell(ShizukuInputCommands.home())
                else -> unavailable()
            }
            DeviceControlTools.TAP, DeviceControlTools.LONG_PRESS -> {
                val x = DeviceControlArguments.coordinate(call, "x")
                val y = DeviceControlArguments.coordinate(call, "y")
                val long = call.name == DeviceControlTools.LONG_PRESS
                val duration = if (long) DeviceControlArguments.int(call, "durationMillis", 700, 400..3_000) else 60
                when {
                    caps.shizukuReady -> Route.Shell(if (long) ShizukuInputCommands.longPress(x, y, duration) else ShizukuInputCommands.tap(x, y))
                    gestures -> Route.Accessibility(ControlPath.ACCESSIBILITY_GESTURE) { it.gesture(x, y, x, y, duration.toLong()) }
                    else -> unavailable()
                }
            }
            DeviceControlTools.SWIPE -> {
                val x = DeviceControlArguments.coordinate(call, "x")
                val y = DeviceControlArguments.coordinate(call, "y")
                val toX = DeviceControlArguments.coordinate(call, "toX")
                val toY = DeviceControlArguments.coordinate(call, "toY")
                val duration = DeviceControlArguments.int(call, "durationMillis", 300, 50..3_000)
                when {
                    caps.shizukuReady -> Route.Shell(ShizukuInputCommands.swipe(x, y, toX, toY, duration))
                    gestures -> Route.Accessibility(ControlPath.ACCESSIBILITY_GESTURE) { it.gesture(x, y, toX, toY, duration.toLong()) }
                    else -> unavailable()
                }
            }
            DeviceControlTools.TYPE_TEXT -> {
                val text = DeviceControlArguments.text(call)
                when {
                    // Accessibility targets exactly the focused editable field and handles Unicode.
                    a11y -> Route.Accessibility(ControlPath.ACCESSIBILITY_SET_TEXT) { it.setFocusedText(text) }
                    caps.shizukuReady && DeviceControlArguments.isShellSafeAscii(text) -> Route.Shell(ShizukuInputCommands.text(text))
                    caps.shizukuReady -> throw ToolBackendFailure(ToolExecutionStatus.FAILED, "TEXT_NEEDS_ACCESSIBILITY (Umlaute/Sonderzeichen)")
                    else -> unavailable()
                }
            }
            DeviceControlTools.TAP_ELEMENT -> {
                DeviceControlArguments.label(call, "packageOrName")
                DeviceControlArguments.label(call, "label")
                DeviceControlArguments.label(call, "expectedLabel")
                if (a11y && caps.verifiedNodeClick) Route.Accessibility(ControlPath.VERIFIED_NODE_CLICK) { false }
                else unavailable()
            }
            else -> unavailable()
        }
    }

    private fun receipt(tool: String, path: ControlPath, detail: String): String = JSONObject()
        .put("schema", "CONTINUUM_DEVICE_CONTROL_V1")
        .put("tool", tool)
        .put("path", path.name)
        .put("status", "DISPATCHED")
        .put("claim", "Eingabe wurde an Android übergeben; die Wirkung auf dem Bildschirm ist damit NICHT geprüft.")
        .apply { if (detail.isNotBlank()) put("detail", detail) }
        .toString()
}
