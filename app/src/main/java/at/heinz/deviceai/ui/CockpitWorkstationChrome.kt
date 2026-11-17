package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Shared workstation surfaces. */
object CockpitWorkstationChrome {
    private val wall = Fill.Linear(0f, 0f, 0f, 1f,
        intArrayOf(0xff030712.toInt(), 0xff0a1023.toInt(), 0xff02040b.toInt()))
    private val floor = Fill.Linear(0f, .55f, 0f, 1f,
        intArrayOf(0xff10172c.toInt(), 0xff030710.toInt(), 0xff080c1a.toInt()))
    private val floorShape = VectorPath().moveTo(0f, .55f)
        .quadTo(.5f, .79f, 1f, .55f).lineTo(1f, 1f).lineTo(0f, 1f).close()
    private val horizon = VectorPath().moveTo(0f, .55f).quadTo(.5f, .79f, 1f, .55f)
    private val horizonFill = Fill.Linear(0f, .55f, 1f, .55f,
        intArrayOf(Colors.cyan, Colors.cyanDeep, Colors.violet, Colors.violetRim))
    private val columns = List(22) { i ->
        val x = .07f + i * .04f
        val bottom = .58f + .11f * (1f - kotlin.math.abs(x - .5f) * 2f)
        VectorPath().moveTo(x, 0f).lineTo(x + .025f, 0f)
            .lineTo(x + .025f, bottom).lineTo(x, bottom).close() to
            Fill.Linear(x, 0f, x + .025f, 0f,
                intArrayOf(0xff040816.toInt(), 0xff121b32.toInt(), 0xff060a18.toInt()))
    }
    private val leftWall = VectorPath().moveTo(0f, 0f).lineTo(.05f, 0f)
        .lineTo(.14f, .59f).lineTo(.04f, .70f).lineTo(0f, .69f).close()
    private val rightWall = VectorPath().moveTo(1f, 0f).lineTo(.95f, 0f)
        .lineTo(.86f, .59f).lineTo(.96f, .70f).lineTo(1f, .69f).close()
    private val leftStone = Fill.Linear(0f, 0f, .14f, .6f,
        intArrayOf(0xff182237.toInt(), 0xff080d1a.toInt(), 0xff20283b.toInt()))
    private val rightStone = Fill.Linear(1f, 0f, .86f, .6f,
        intArrayOf(0xff182035.toInt(), 0xff080b18.toInt(), 0xff272139.toInt()))
    private val leftLight = Fill.Radial(.09f, .61f, .19f,
        intArrayOf(0x883ad9ed.toInt(), 0x153acbea, 0))
    private val rightLight = Fill.Radial(.91f, .61f, .19f,
        intArrayOf(0x995b2bda.toInt(), 0x224b20bd, 0))
    private val panelFill = Fill.Solid(0xff060c17.toInt())
    private val send = VectorPath().moveTo(1f, 1f).lineTo(23f, -10f)
        .lineTo(16f, 12f).lineTo(10f, 5f).lineTo(1f, 1f)
        .moveTo(10f, 5f).lineTo(23f, -10f)

    fun backdrop(p: ContinuumPainter, b: Box) {
        if (b.width <= 0f || b.height <= 0f) return
        p.save()
        p.clipRect(b.left, b.top, b.right, b.bottom)
        p.translate(b.left, b.top)
        p.scale(b.width, b.height)
        p.fillRect(0f, 0f, 1f, 1f, wall)
        columns.forEach { (path, fill) -> p.fillPath(path, fill) }
        p.fillPath(floorShape, floor)
        p.fillPath(leftWall, leftStone)
        p.fillPath(rightWall, rightStone)
        p.setAdditive(true)
        p.fillOval(-.10f, .36f, .28f, .86f, leftLight)
        p.fillOval(.72f, .36f, 1.10f, .86f, rightLight)
        p.strokePath(horizon, .012f, horizonFill, .10f)
        p.strokePath(horizon, .0025f, horizonFill, .70f)
        p.setAdditive(false)
        p.restore()
    }

    fun header(p: ContinuumPainter, b: Box, actions: Map<String, Box>,
               notifications: Int, fontScale: Float) {
        JarvisReferenceHud.surface(p, b, 5f, Colors.cyan, Colors.cyanDeep)
        val x = b.left + 14f
        val end = actions.values.minOfOrNull { it.left } ?: (b.right - 12f)
        // JarvisTextScalePainter already applies the user's accessibility text scale.
        val base = TextStyle(FontFamily.DISPLAY, 500, 22f, 1.1f)
        val style = JarvisShellRenderer.shrinkToFit(p, "DEVICE AI", base,
            (end - x - 8f).coerceAtLeast(1f), minScale = .55f)
        val caption = TextStyle(FontFamily.DISPLAY, 400, 10f, 1.8f)
        val line = JarvisShellRenderer.shrinkToFit(p, "CONTINUUM", caption,
            (end - x - 8f).coerceAtLeast(1f), minScale = .7f)
        val titleCap = p.capHeight(style)
        val captionCap = p.capHeight(line)
        val top = b.centerY - (titleCap + captionCap + 5f) * .5f
        if (end - x >= 64f) {
            p.drawText("DEVICE AI", x, top + titleCap, style, Fill.Solid(Colors.text))
            p.drawText("CONTINUUM", x, top + titleCap + 5f + captionCap,
                line, Fill.Solid(Colors.textSecondary))
        }
        actions.forEach { (target, box) ->
            val r = minOf(18f, box.width * .39f)
            p.fillRoundRect(box.centerX - r, box.centerY - r,
                box.centerX + r, box.centerY + r, 5f, Fill.Solid(Colors.navSurface))
            p.strokeRoundRect(box.centerX - r, box.centerY - r,
                box.centerX + r, box.centerY + r, 5f, .8f, Fill.Solid(Colors.rim))
            ContinuumIcons.draw(p, when (target) {
                "chats" -> Icon.CHAT
                "notifications" -> Icon.BELL
                "settings" -> Icon.GEAR
                else -> Icon.DOTS_HORIZONTAL
            }, box.centerX, box.centerY, r * 1.15f, Fill.Solid(Colors.text))
            if (target == "notifications" && notifications > 0)
                p.fillCircle(box.centerX + r * .62f, box.centerY - r * .62f,
                    2.8f, Fill.Solid(Colors.magenta))
        }
    }

    fun sidePanel(p: ContinuumPainter, b: Box) {
        p.fillRoundRect(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f, 5f, panelFill)
        p.strokeRoundRect(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f, 5f,
            .65f, Fill.Solid(Colors.rim), .75f)
        p.strokeRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 3f,
            .45f, Fill.Solid(Colors.textSecondary), .18f)
        p.drawLine(b.left + 9f, b.top + 2f, b.left + minOf(37f, b.width * .25f),
            b.top + 2f, 1.2f, Fill.Solid(Colors.cyanSoft), .65f)
        p.drawLine(b.right - 2f, b.bottom - 25f, b.right - 2f,
            b.bottom - 9f, 1.2f, Fill.Solid(Colors.violetSoft), .6f)
    }

    fun badgeSurface(p: ContinuumPainter, b: Box) {
        p.fillRoundRect(b.left, b.top, b.right, b.bottom, 5f, panelFill, .94f)
        val edge = Fill.Linear(b.left, b.top, b.right, b.bottom,
            intArrayOf(Colors.cyan, Colors.cyanDeep, Colors.violetRim))
        p.strokeRoundRect(b.left, b.top, b.right, b.bottom, 5f, 1.1f, edge, .78f)
        p.strokeRoundRect(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f,
            3f, .6f, Fill.Solid(Colors.text), .12f)
    }

    fun companionBadge(p: ContinuumPainter, b: Box, status: String, active: Boolean) {
        val stacked = b.width < 260f
        val top = if (stacked) b.top + 9f else b.centerY - 14f
        val textX = b.left + 12f
        val style = JarvisDesign.Type.body.copy(size = 13f)
        val cap = p.capHeight(style)
        p.drawText(JarvisShellRenderer.fit(p, "Device AI", style, b.right - textX - 8f),
            textX, top + cap, style, Fill.Solid(Colors.text))
        p.drawText(JarvisShellRenderer.fit(p, "Continuum", style, b.right - textX - 8f),
            textX, top + cap * 2f + 2f, style, Fill.Solid(Colors.text))
        val caption = JarvisDesign.Type.caption.copy(size = 11f)
        val statusX = if (stacked) b.left + 21f else maxOf(textX + 82f, b.centerX + 12f)
        val y = if (stacked) b.bottom - 9f else b.centerY + p.capHeight(caption) * .5f
        p.fillCircle(statusX - 9f, y - p.capHeight(caption) * .5f, 2.8f,
            Fill.Solid(if (active) Colors.ok else Colors.textMuted))
        p.drawText(JarvisShellRenderer.fit(p, status, caption, b.right - statusX - 8f),
            statusX, y, caption, Fill.Solid(Colors.textSecondary))
    }

    fun cameraBadge(p: ContinuumPainter, b: Box, status: String, active: Boolean) {
        val fill = Fill.Solid(if (active) Colors.ok else Colors.cyanSoft)
        p.strokeRoundRect(b.left + 10f, b.centerY - 6f, b.left + 26f, b.centerY + 6f, 3f, 1.1f, fill)
        p.strokeCircle(b.left + 18f, b.centerY, 3.4f, 1.1f, fill)
        p.fillCircle(b.left + 18f, b.centerY, 1.4f, fill)
        val style = JarvisDesign.Type.caption.copy(size = 12f)
        p.drawText(JarvisShellRenderer.fit(p, status, style, b.width - 44f),
            b.left + 34f, b.centerY + p.capHeight(style) * .5f, style, Fill.Solid(Colors.textSecondary))
    }

    fun systemBadge(p: ContinuumPainter, b: Box, cpuPercent: Int?, memoryPercent: Int?) {
        ContinuumIcons.draw(p, Icon.CPU, b.left + 18f, b.centerY, 17f, Fill.Solid(Colors.cyanSoft))
        val style = JarvisDesign.Type.caption.copy(size = 12f)
        p.drawText("System", b.left + 34f, b.centerY - 2f, style, Fill.Solid(Colors.textSecondary))
        val values = "CPU ${cpuPercent?.let { "$it%" } ?: "—"} · " +
            "RAM ${memoryPercent?.let { "$it%" } ?: "—"}"
        val caption = style.copy(size = 10f)
        p.drawText(JarvisShellRenderer.fit(p, values, caption, b.width - 44f),
            b.left + 34f, b.centerY + 12f, caption, Fill.Solid(Colors.textSecondary))
    }

    fun panelHeader(p: ContinuumPainter, b: Box, title: String, icon: Icon): Float {
        val style = JarvisDesign.Type.heading
        val y = b.top + 10f + p.capHeight(style)
        ContinuumIcons.draw(p, icon, b.left + 15f, y - p.capHeight(style) * .5f,
            13f, Fill.Solid(Colors.cyanSoft), 1.2f)
        p.drawText(JarvisShellRenderer.fit(p, title, style, b.width - 46f),
            b.left + 29f, y, style, Fill.Solid(Colors.text))
        ContinuumIcons.draw(p, Icon.DOTS_HORIZONTAL, b.right - 12f,
            y - p.capHeight(style) * .5f, 10f, Fill.Solid(Colors.textSecondary))
        return y
    }

    fun idleInput(p: ContinuumPainter, b: Box, textBox: Box, controls: List<Box>, listening: Boolean) {
        controls.forEachIndexed { i, box ->
            val color = if (i == 0 && listening) Colors.ok else Colors.cyan
            val fill = Fill.Solid(color)
            if (i == 0) {
                p.strokeCircle(box.centerX, box.centerY, 16f, .9f, fill)
                p.strokeRoundRect(box.centerX - 3f, box.centerY - 9f,
                    box.centerX + 3f, box.centerY + 3f, 3f, 1.3f, fill)
                p.strokeArc(box.centerX - 6f, box.centerY - 4f,
                    box.centerX + 6f, box.centerY + 8f, 0f, 180f, 1.3f, fill)
                p.drawLine(box.centerX, box.centerY + 8f, box.centerX, box.centerY + 11f, 1.3f, fill)
            } else {
                p.save(); p.translate(box.centerX - 12f, box.centerY)
                p.strokePath(send, 1.5f, fill); p.restore()
            }
        }
        val text = "Was soll ich für dich erledigen?"
        val base = JarvisDesign.Type.body.copy(size = (b.width * .027f).coerceIn(15f, 23f))
        val style = JarvisShellRenderer.shrinkToFit(p, text, base, textBox.width - 16f, minScale = .78f)
        p.drawText(JarvisShellRenderer.fit(p, text, style, textBox.width - 16f),
            textBox.left + 8f, b.centerY + p.capHeight(style) * .5f,
            style, Fill.Solid(Colors.text))
    }
}
