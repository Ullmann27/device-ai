package sceneprobe

import at.heinz.deviceai.ContinuumWorkAreaNavigationSpec
import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import at.heinz.deviceai.ui.JarvisTiles
import kotlin.system.exitProcess

private var failures = 0
private var checks = 0

private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
private fun array(values: Collection<String>) = values.joinToString(",", "[", "]", transform = ::quote)
private fun box(value: Box?) = value?.let { "[${it.left},${it.top},${it.right},${it.bottom}]" } ?: "null"

private fun check(name: String, passed: Boolean, detail: String) {
    checks++
    if (!passed) failures++
    println("{\"kind\":\"check\",\"name\":${quote(name)},\"status\":${quote(if (passed) "PASS" else "FAIL")},\"detail\":${quote(detail)}}")
}

private fun overlap(a: Box, b: Box) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

fun main() {
    val missing = (600..703).filter { width ->
        "more" !in JarvisShellLayout.forContent(width.toFloat(), 760f, workstation = true).headerActionBoxes()
    }
    println("{\"kind\":\"observation\",\"name\":\"missing_direct_more\",\"tested_widths\":104,\"count\":${missing.size},\"first\":${missing.firstOrNull()},\"last\":${missing.lastOrNull()}}")
    for ((width, height, font) in listOf(
        Triple(412f, 915f, 1f), Triple(599f, 760f, 1f),
        Triple(600f, 760f, 1f), Triple(650f, 760f, 1f),
        Triple(703f, 760f, 1f), Triple(704f, 760f, 1f),
        Triple(884f, 760f, 1f), Triple(884f, 760f, 2f)
    )) {
        val shell = JarvisShellLayout.forContent(width, height, font, workstation = true)
        val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
        val columns = JarvisTiles.bottomNavColumns(dock.width)
        val rows = JarvisTiles.bottomNavRows(dock.width)
        println("{\"kind\":\"layout\",\"width\":$width,\"height\":$height,\"font\":$font,\"header_actions\":${array(shell.headerActionBoxes().keys)},\"dock_columns\":$columns,\"dock_rows\":$rows,\"status\":${box(shell[Slot.STATUS])},\"secondary\":${box(shell[Slot.COCKPIT_SECONDARY])},\"dialog\":${box(shell[Slot.DIALOG])},\"document\":${box(shell[Slot.WORK_DOCUMENT])}}")
    }
    val dockTargets = JarvisTiles.bottomNav.map { it.target }.toSet()
    val removed = JarvisTiles.railNav.map { it.target }.toSet() - dockTargets
    val more = ContinuumWorkAreaNavigationSpec.moreEntries.map { it.target }.toSet()
    println("{\"kind\":\"navigation\",\"dock\":${array(dockTargets)},\"removed_from_dock\":${array(removed)},\"more\":${array(more)}}")
    check("removed_targets_present_in_more", removed.all { it in more }, "Removed targets: ${removed.joinToString()}")
    check("eight_unique_dock_targets", dockTargets.size == 8 && JarvisTiles.bottomNav.size == 8, "Actual dock definitions")
    val phone = JarvisShellLayout.forContent(412f, 915f, workstation = true)
    val phoneDock = requireNotNull(phone[Slot.BOTTOM_NAV])
    check("phone_two_rows_four", JarvisTiles.bottomNavColumns(phoneDock.width) == 4 && JarvisTiles.bottomNavRows(phoneDock.width) == 2, "412 x 915 dp")
    val fold = JarvisShellLayout.forContent(884f, 760f, workstation = true)
    val foldDock = requireNotNull(fold[Slot.BOTTOM_NAV])
    check("wide_one_row_eight", JarvisTiles.bottomNavColumns(foldDock.width) == 8 && JarvisTiles.bottomNavRows(foldDock.width) == 1, "884 x 760 dp, synthetic host dimensions")
    check("status_geometry_survives_medium_width", (600..703).all {
        JarvisShellLayout.forContent(it.toFloat(), 760f, workstation = true)[Slot.STATUS] != null
    }, "STATUS source route to system inspected separately")
    val large = JarvisShellLayout.forContent(884f, 760f, 2f, workstation = true)
    check("large_font_secondary_panel_preserved", large[Slot.COCKPIT_SECONDARY] != null, "884 x 760 dp, fontScale 2.0")
    check("large_font_dialog_document_nonoverlap", !overlap(requireNotNull(large[Slot.DIALOG]), requireNotNull(large[Slot.WORK_DOCUMENT])), "884 x 760 dp, fontScale 2.0")
    for (width in listOf(320f, 360f, 600f, 640f, 700f, 720f, 884f)) {
        for (font in listOf(1f, 1.5f, 2f)) {
            val shell = JarvisShellLayout.forContent(width, 760f, font, workstation = true)
            val controls = shell.boxes.filterKeys { it != Slot.AVATAR }.entries.toList()
            val collisions = controls.flatMapIndexed { index, a ->
                controls.drop(index + 1).filter { b -> overlap(a.value, b.value) }
                    .map { b -> "${a.key}:${b.key}" }
            }
            val dock = requireNotNull(shell[Slot.BOTTOM_NAV])
            val cells = JarvisTiles.bottomNavItemBoxes(dock)
            val valid = shell.boxes.values.all {
                it.left >= 0f && it.top >= 0f && it.right <= width && it.bottom <= shell.height && it.width > 0f && it.height > 0f
            }
            val side = shell[Slot.COCKPIT_SECONDARY]
            val sideValid = width < 600f || side != null && side.height >= 48f * font
            val safe = valid && collisions.isEmpty() && sideValid && cells.all { it.width >= 48f && it.height >= 48f }
            check("geometry_${width.toInt()}_font${font}", safe,
                "collisions=${collisions.joinToString()}; secondary=${box(side)}; minimum48dp=${cells.all { it.width >= 48f && it.height >= 48f }}")
        }
    }
    println("{\"kind\":\"summary\",\"checks\":$checks,\"failures\":$failures,\"filament\":\"NOT_RUN\",\"device\":\"NOT_RUN\"}")
    exitProcess(if (failures == 0) 0 else 1)
}
