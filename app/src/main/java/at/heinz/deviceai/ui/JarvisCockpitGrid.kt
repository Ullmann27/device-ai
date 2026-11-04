package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Jarvis Kern: the Fold7-open cockpit grid measured from ref5 (§11, "pixelgenau wie ref5"). Pure dp geometry.
 * Horizontal positions are fractions of the window width read from ref5 (screen area 735 × 538 px in the
 * reference image); the fixed bands (header, tiles, bottom panels, status bar) use the same ref5 heights scaled by
 * [unit], and the head stage takes the remaining height so that EVERYTHING of ref5 is visible at once.
 * The user card uses Lückenfüller's measured fractions (left .157, top .123, w .149, h .194).
 * The navigation rail ([Area.RAIL]) and the header device status ([Area.DEVICE_STATUS]) are slots for
 * Integration (#486); the head ([Area.HEAD]) belongs to Avatar (#482), the orb ([Area.ORB]) to Gedächtnis (#484).
 */
object JarvisCockpitGrid {
    enum class Area {
        HEADER, DEVICE_STATUS, BRAND, MODE_PILL, PRIVACY_PILL, ICONS,
        RAIL, USER_CARD, HEAD, ORB, DIALOG, MODULES, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS
    }

    /** Areas drawn on top of a larger one (container to inner); every other pair must not overlap. */
    val overlays: Set<Pair<Area, Area>> = setOf(
        Area.HEAD to Area.USER_CARD, Area.HEAD to Area.DIALOG,
        Area.HEADER to Area.DEVICE_STATUS, Area.HEADER to Area.BRAND, Area.HEADER to Area.MODE_PILL,
        Area.HEADER to Area.PRIVACY_PILL, Area.HEADER to Area.ICONS,
    )

    data class Grid(val width: Float, val height: Float, val unit: Float, val areas: Map<Area, Box>) {
        operator fun get(a: Area): Box = areas.getValue(a)
    }

    /** ref5 screen area in reference pixels. */
    const val REF_W = 735f
    const val REF_H = 538f

    // ref5 fractions of the width (x - 48) / 735
    private const val CONTENT_LEFT = .157f      // = user card left (Lückenfüller)
    private const val HEAD_RIGHT = .547f
    private const val ORB_RIGHT = .819f
    private const val MODULES_LEFT = .833f
    private const val BRAND_LEFT = .125f
    private const val BRAND_RIGHT = .40f
    private const val MODE_LEFT = .522f
    private const val MODE_RIGHT = .642f
    private const val PRIVACY_LEFT = .662f
    private const val PRIVACY_RIGHT = .874f
    private const val ICONS_LEFT = .888f
    private const val DIALOG_RIGHT = .370f
    private const val CARD_TOP = .123f
    private const val CARD_W = .149f
    private const val CARD_H = .194f
    /** Bottom panels Active Agents / Task Flow / Memory Activity / Local Model (ref5 widths 158:132:139:159). */
    private val ROW_WEIGHTS = floatArrayOf(158f, 132f, 139f, 159f)

    /** Applies on the open Fold7 (FOLD_OPEN) with normal font sizes; larger fonts keep the scrolling-free legacy grid. */
    fun applies(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
        JarvisShellLayout.formFactor(widthDp, heightDp) == JarvisShellLayout.FormFactor.FOLD_OPEN && fontScale <= 1.3f

    fun unit(widthDp: Float, heightDp: Float): Float = minOf(widthDp / REF_W, heightDp / REF_H)

    fun create(widthDp: Float, heightDp: Float): Grid {
        require(widthDp.isFinite() && widthDp > 0f && heightDp.isFinite() && heightDp > 0f)
        val W = widthDp; val H = heightDp
        val u = unit(W, H)
        val m = 6f * u; val g = 6f * u
        val a = linkedMapOf<Area, Box>()
        val right = W - m
        val cl = W * CONTENT_LEFT
        // Header: ref5 56 px, never below the user card top.
        val cardTop = H * CARD_TOP
        val headerBottom = minOf(56f * u, cardTop - g)
        a[Area.HEADER] = Box(m, 0f, right, headerBottom)
        val pillH = minOf(20f * u, headerBottom - 8f)
        val pillTop = (headerBottom - pillH) / 2f
        a[Area.DEVICE_STATUS] = Box(m, 4f, W * BRAND_LEFT - g, headerBottom - 4f)
        a[Area.BRAND] = Box(W * BRAND_LEFT, 4f, W * BRAND_RIGHT, headerBottom - 4f)
        a[Area.MODE_PILL] = Box(W * MODE_LEFT, pillTop, W * MODE_RIGHT, pillTop + pillH)
        a[Area.PRIVACY_PILL] = Box(W * PRIVACY_LEFT, pillTop, W * PRIVACY_RIGHT, pillTop + pillH)
        a[Area.ICONS] = Box(W * ICONS_LEFT, pillTop, right, pillTop + pillH)

        // Bands from the bottom: status 26 px, bottom panels 95 px, tiles 2 × 45 px + 5 px (ref5).
        val statusH = 26f * u
        val statusTop = H - m - statusH
        a[Area.STATUS] = Box(m, statusTop, right, H - m)
        val rowBottom = statusTop - g
        val rowTop = rowBottom - 95f * u
        val tilesBottom = rowTop - g
        val tilesTop = tilesBottom - 95f * u
        a[Area.TILES] = Box(cl, tilesTop, right, tilesBottom)
        val rows = listOf(Area.ROW_AGENTS, Area.ROW_TASKS, Area.ROW_MEMORY, Area.ROW_MODEL)
        val rowRoom = right - cl - (rows.size - 1) * g
        var x = cl
        rows.forEachIndexed { i, area ->
            val w = rowRoom * ROW_WEIGHTS[i] / ROW_WEIGHTS.sum()
            val r = if (i == rows.lastIndex) right else x + w
            a[area] = Box(x, rowTop, r, rowBottom); x = r + g
        }
        a[Area.RAIL] = Box(m, headerBottom + g, cl - g, rowBottom)

        // Head stage between header and tiles: head (Avatar), orb (Gedächtnis), modules column.
        val stageTop = headerBottom + g
        val stageBottom = tilesTop - g
        val stageH = stageBottom - stageTop
        a[Area.HEAD] = Box(cl, stageTop, W * HEAD_RIGHT - g / 2f, stageBottom)
        a[Area.ORB] = Box(W * HEAD_RIGHT + g / 2f, stageTop, W * ORB_RIGHT, stageBottom)
        a[Area.MODULES] = Box(W * MODULES_LEFT, stageTop + stageH * .15f, right, stageTop + stageH * .74f)
        val cardBottom = minOf(cardTop + H * CARD_H, stageTop + stageH * .52f)
        a[Area.USER_CARD] = Box(cl, maxOf(cardTop, stageTop), cl + W * CARD_W, cardBottom)
        val dialogTop = maxOf(cardBottom + g, stageBottom - stageH * .42f)
        a[Area.DIALOG] = Box(cl, dialogTop, W * DIALOG_RIGHT, stageBottom)
        return Grid(W, H, u, a)
    }

    /**
     * Compact type for the ref5 grid. Interim scale until Welten-Optik's measured typography table (#479) is pushed;
     * built only from the ContinuumTheme token functions, switched in this ONE place.
     */
    object Type {
        val brand = ContinuumTheme.display(600, 15f, .3f)
        val brandSub = ContinuumTheme.text(400, 8.5f)
        val pill = ContinuumTheme.text(500, 9.5f)
        val tileTitle = ContinuumTheme.text(500, 11f)
        val tileSub = ContinuumTheme.text(400, 8.5f)
        val panelTitle = ContinuumTheme.text(500, 10f)
        val body = ContinuumTheme.text(400, 8.5f)
        val status = ContinuumTheme.text(400, 9f)
        val placeholder = ContinuumTheme.text(400, 10f)
    }
}
