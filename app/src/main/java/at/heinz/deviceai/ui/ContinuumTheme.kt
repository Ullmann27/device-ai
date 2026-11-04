package at.heinz.deviceai.ui

/**
 * Continuum native cockpit tokens, following the owner's cyan/violet JARVIS references of 2026-11-04.
 * All sizes are dp; text sizes are sp before font scaling.
 */
object ContinuumTheme {
    // Surfaces (median colours of empty regions in the reference)
    val BACKGROUND = Argb.rgb(2, 7, 22)
    val BACKGROUND_GLOW = Argb.rgb(8, 38, 83)
    val CARD_FILL_TOP = Argb.of(238, 6, 32, 56)
    val CARD_FILL_BOTTOM = Argb.of(240, 3, 12, 31)
    val CARD_DIVIDER = Argb.of(130, 41, 99, 132)
    val NAV_FILL = Argb.of(245, 2, 11, 30)
    val NAV_EDGE = Argb.rgb(18, 40, 78)
    val ICON_DISC = Argb.of(220, 8, 27, 62)
    val ICON_DISC_EDGE = Argb.of(200, 44, 92, 170)

    // Accents
    val CYAN = Argb.rgb(54, 225, 255)
    val CYAN_SOFT = Argb.rgb(125, 219, 255)
    val BLUE = Argb.rgb(36, 112, 255)
    val VIOLET = Argb.rgb(167, 104, 255)
    val MAGENTA = Argb.rgb(228, 76, 214)
    val PINK = Argb.rgb(255, 92, 150)
    val GREEN = Argb.rgb(30, 226, 190)              // "Aktiv", "Keine Fehlschläge", "Keine offenen Fehler"
    val GREEN_FILL = Argb.of(70, 12, 120, 110)
    val RED = Argb.rgb(255, 77, 109)
    val RED_FILL = Argb.of(70, 150, 20, 60)
    val AMBER = Argb.rgb(255, 196, 92)

    // Text
    val TEXT = Argb.rgb(242, 246, 252)
    val TEXT_SECONDARY = Argb.rgb(174, 198, 218)
    val TEXT_MUTED = Argb.rgb(134, 160, 187)
    val LABEL_CYAN = Argb.rgb(105, 230, 255)

    // Card border: cyan-blue on the left, violet on the right, as in the reference
    val BORDER_LEFT = Argb.rgb(48, 201, 234)
    val BORDER_TOP = Argb.rgb(37, 110, 154)
    val BORDER_RIGHT = Argb.rgb(142, 88, 224)
    val BORDER_GLOW = Argb.of(90, 40, 203, 255)

    const val CARD_RADIUS = 16f
    const val SCREEN_MARGIN = 13f
    const val CARD_GAP = 10f

    /** Phone reference: the screen of reference-phone.png is 713 px wide, i.e. 412 dp at 1.7306 px/dp. */
    const val REFERENCE_WIDTH_DP = 412f
    const val REFERENCE_DENSITY = 713f / 412f

    fun display(weight: Int, sizeSp: Float, letterSpacing: Float = 0f) = TextStyle(FontFamily.DISPLAY, weight, sizeSp, letterSpacing)
    fun text(weight: Int, sizeSp: Float, letterSpacing: Float = 0f) = TextStyle(FontFamily.TEXT, weight, sizeSp, letterSpacing)

    val brandGradientColors = intArrayOf(Argb.rgb(48, 210, 255), Argb.rgb(52, 110, 255), Argb.rgb(150, 86, 255), Argb.rgb(226, 82, 230))
}
