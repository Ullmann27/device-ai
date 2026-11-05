package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/**
 * Regions measured from the owner's 1536 × 1024 reference (01-59477, 4 November 2026).
 *
 * Coordinates are layout constraints, never a transform applied to text or the 3D scene. Horizontal positions
 * follow the available window; extra height is shared by the hero and information row. Each renderer receives
 * its own bounds and preserves its aspect ratio. The background still paints the entire available window.
 */
object JarvisCockpitGrid {
    enum class Area {
        HEADER, DEVICE_STATUS, BRAND, MODE_PILL, PRIVACY_PILL, ICONS,
        RAIL, USER_CARD, HEAD, ORB, DIALOG, MODULES, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS,
        TAGLINE, CONTINUUM_HEADER, CAMERA_STATUS
    }

    /**
     * Intentional layers in the reference. The transparent head stage extends behind the header's lower edge;
     * its visible mesh, rather than its rectangular viewport, determines occlusion. USER_CARD remains an API
     * name for older compositions, but the new reference has no owner-card overlay on the face.
     */
    val overlays: Set<Pair<Area, Area>> = setOf(
        Area.HEAD to Area.DIALOG,
        Area.HEADER to Area.DEVICE_STATUS, Area.HEADER to Area.BRAND, Area.HEADER to Area.MODE_PILL,
        Area.HEADER to Area.PRIVACY_PILL, Area.HEADER to Area.ICONS, Area.HEADER to Area.TAGLINE,
        Area.HEADER to Area.HEAD, Area.HEAD to Area.BRAND, Area.HEAD to Area.TAGLINE,
        Area.HEADER to Area.CONTINUUM_HEADER, Area.HEADER to Area.CAMERA_STATUS
    )

    data class Grid(val width: Float, val height: Float, val unit: Float, val areas: Map<Area, Box>) {
        operator fun get(a: Area): Box = areas.getValue(a)
    }

    const val REF_W = 1536f
    const val REF_H = 1024f
    const val TILE_COLUMNS = 5
    const val TILE_ROWS = 2
    const val MIN_TOUCH_DP = 48f
    /** Heading, companion line, a real two-line reply and the unchanged 48dp action row. */
    const val MIN_DIALOG_DP = 144f
    /** Reference-x of the dialogue's right edge and of the free head stage's left edge (gap 8 reference px). */
    const val DIALOG_RIGHT = 540f
    const val HEAD_LEFT = 548f
    /**
     * Live head framing measured on renders of the packaged GLB against 59509: crown-to-chin ≈ 70 % of the hero
     * (before 47 %). The head centre sits at 70 % of the stage that reaches behind the dialogue; in the -22° three-
     * quarter view the whole head (ear to cheek) then spans ≈ 51–90 % of the stage width, right of the dialogue edge.
     */
    const val HEAD_ANCHOR_X = .70f
    const val HEAD_CLOSE_UP = .92f

    /** Both open-Fold orientations and wide tablets retain the same five-column composition. */
    fun applies(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
        widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp > 0f &&
            JarvisWindowProfile.resolve(widthDp, heightDp, fontScale).referenceComposition

    fun unit(widthDp: Float, heightDp: Float): Float = minOf(widthDp / REF_W, heightDp / REF_H)

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Grid {
        require(widthDp.isFinite() && widthDp >= 600f && heightDp.isFinite() && heightDp >= 480f)
        require(fontScale.isFinite() && fontScale > 0f)
        val u = unit(widthDp, heightDp)
        val extraHeight = heightDp - REF_H * u
        val gap = 12f * u
        // Small windows get real touch targets and readable panel rows before decorative space.
        val tileExtra = (2f * MIN_TOUCH_DP * maxOf(1f, fontScale) + gap - 182f * u).coerceAtLeast(0f)
        val rowExtra = (120f * maxOf(1f, fontScale) - (191f * u + extraHeight * .2f)).coerceAtLeast(0f)
        val heroExtension = extraHeight * .8f - tileExtra - rowExtra
        fun x(referenceX: Float) = referenceX / REF_W * widthDp
        fun b(left: Float, top: Float, right: Float, bottom: Float) = Box(x(left), top, x(right), bottom)
        val a = linkedMapOf<Area, Box>()

        a[Area.HEADER] = b(8f, 6f * u, 1522f, 94f * u)
        a[Area.DEVICE_STATUS] = b(16f, 10f * u, 172f, 90f * u)
        a[Area.BRAND] = b(190f, 5f * u, 466f, 83f * u)
        a[Area.TAGLINE] = b(494f, 13f * u, 793f, 78f * u)
        a[Area.MODE_PILL] = b(817f, 17f * u, 1001f, 61f * u)
        a[Area.PRIVACY_PILL] = b(1021f, 18f * u, 1280f, 59f * u)
        a[Area.ICONS] = b(1291f, 14f * u, 1517f, 62f * u)

        val heroBottom = 497f * u + heroExtension
        // Owner correction 2026-11-05: the head stays completely free. The dialogue keeps the hero's lower-left
        // corner; the head owns the right half of the hero next to the orb, so the two never overlap.
        a[Area.HEAD] = b(HEAD_LEFT, 70f * u, 800f, heroBottom)
        a[Area.ORB] = b(805f, 183f * u, 1212f, heroBottom)
        a[Area.CONTINUUM_HEADER] = b(805f, 80f * u, 1194f, 180f * u)
        val headerActionBottom = maxOf(48f, a.getValue(Area.ICONS).centerY + 24f)
        val cameraTop = maxOf(83f * u, headerActionBottom + 6f * u)
        val cameraBottom = cameraTop + maxOf(58f * u, MIN_TOUCH_DP)
        a[Area.CAMERA_STATUS] = b(1199f, cameraTop, 1520f, cameraBottom)
        a[Area.MODULES] = b(1219f, maxOf(156f * u, cameraBottom + 8f * u), 1520f,
            minOf(heroBottom - 8f * u, 405f * u + heroExtension * .6f))
        val dialogBottom = heroBottom - 5f * u
        val dialogHeight = maxOf(194f * u, MIN_DIALOG_DP * maxOf(1f, fontScale))
        a[Area.DIALOG] = b(256f, maxOf(a.getValue(Area.HEAD).top + 8f * u, dialogBottom - dialogHeight),
            DIALOG_RIGHT, dialogBottom)

        a[Area.TILES] = b(258f, 503f * u + heroExtension, 1520f,
            685f * u + extraHeight * .8f - rowExtra)
        val rowTop = 697f * u + extraHeight * .8f - rowExtra
        val rowBottom = 888f * u + extraHeight
        a[Area.ROW_AGENTS] = b(258f, rowTop, 548f, rowBottom)
        a[Area.ROW_TASKS] = b(560f, rowTop, 836f, rowBottom)
        a[Area.ROW_MEMORY] = b(848f, rowTop, 1152f, rowBottom)
        a[Area.ROW_MODEL] = b(1164f, rowTop, 1520f, rowBottom)
        a[Area.RAIL] = b(12f, 97f * u, 243f, 896f * u + extraHeight)
        a[Area.STATUS] = b(16f, 906f * u + extraHeight, 1520f, 978f * u + extraHeight)
        return Grid(widthDp, heightDp, u, a)
    }
}
