package at.heinz.deviceai.ui

/**
 * Jarvis design system: semantic façade over [ContinuumTheme] (PR #477).
 * Jarvis Kern (cockpit content) and Jarvis Avatar (head) use ONLY these names, so
 * the raw palette can be tuned in one place without touching their code.
 * See docs/DESIGN-SYSTEM.md.
 */
object JarvisDesign {
    /**
     * Preserved cyan and violet accents.
     * Dark surfaces follow current reference.
     * Device calibration remains separate.
     */
    object Colors {
        val background = Argb.rgb(0x02, 0x05, 0x10)
        val backgroundBottom = Argb.rgb(0x03, 0x07, 0x15)
        val backgroundHaze = Argb.rgb(0x08, 0x10, 0x29)
        val surface = Argb.of(250, 0x04, 0x0B, 0x1C)
        val surfaceLow = Argb.of(250, 0x03, 0x08, 0x16)
        val rim = Argb.rgb(0x17, 0x68, 0x7F)                // tile.rim #17687F (1 px)
        val panelRim = Argb.rgb(0x32, 0x9E, 0xC0)           // glass.rim #329EC0
        val cyanDeep = Argb.rgb(0x01, 0x75, 0xAF)           // pill.rim #0175AF
        val navSurface = Argb.rgb(0x03, 0x0A, 0x18)
        val navSurfaceActive = Argb.rgb(0x02, 0x20, 0x2D)
        val navRimActive = Argb.rgb(0x32, 0xD6, 0xED)       // button.rim.active #32D6ED
        val tileFillStart = Argb.rgb(0x05, 0x18, 0x30)
        val tileFillMiddle = Argb.rgb(0x01, 0x13, 0x27)
        val tileFillEnd = Argb.rgb(0x06, 0x17, 0x31)
        val tileRimStart = Argb.rgb(0x01, 0xDA, 0xE8)       // tile.rim.left #01DAE8
        val tileRimEnd = Argb.rgb(0xC7, 0x4D, 0xFF)
        // Decorative destination colors only.
        val memoryTileStart = Argb.rgb(0x0C, 0x0D, 0x24)
        val memoryTileEnd = Argb.rgb(0x04, 0x0A, 0x1B)
        val agentsTileStart = Argb.rgb(0x0D, 0x0C, 0x25)
        val agentsTileEnd = Argb.rgb(0x05, 0x09, 0x1C)
        val chevron = Argb.rgb(0x51, 0xAD, 0xC5)            // tile.chevron #51ADC5 (antialiased 1 px stroke, true colour brighter)
        val pillRimStart = Argb.rgb(0x2A, 0xEA, 0xFC)       // pill.rim.left #2AEAFC
        val pillRimEnd = Argb.rgb(0xCC, 0x94, 0xEF)         // pill.rim.right #CC94EF
        val cardRimStart = Argb.rgb(0x21, 0xD9, 0xF5)
        val cardRimEnd = Argb.rgb(0x67, 0xBA, 0xFF)
        val cardRimEndViolet = Argb.rgb(0xC9, 0x57, 0xFF)
        val chipLocalSurface = Argb.rgb(0x02, 0x12, 0x1A)
        val pillSurface = Argb.rgb(0x05, 0x0F, 0x25)
        val cyan = Argb.rgb(0x00, 0xE5, 0xFF)
        val cyanSoft = Argb.rgb(0x78, 0xF6, 0xFB)           // cyan.icon #78F6FB
        val iconCore = Argb.rgb(0xC9, 0xFF, 0xFF)           // tile.icon #C9FFFF
        val glowCore = Argb.rgb(0xAB, 0xF8, 0xFF)           // glow.core #ABF8FF
        val glowHalo = Argb.rgb(0x01, 0x25, 0x39)           // glow.halo #012539
        val bloomHalo = Argb.rgb(0x08, 0xAF, 0xFF)          // bloom halo hue: #034666 at 4 px off the active rim, normalised to full brightness
        val dust = Argb.rgb(0x0F, 0x93, 0xC4)               // dust mote core #0F93C4 (only 2 motes in the clean sample – low confidence)
        val violet = Argb.rgb(0xA4, 0x3B, 0xFF)
        val violetSoft = Argb.rgb(0x91, 0x6D, 0xC9)         // violet.icon #916DC9
        val violetRim = Argb.rgb(0xD8, 0x81, 0xFF)
        val activeWhite = Argb.rgb(255, 255, 255)
        val magenta = Argb.rgb(0xF0, 0x4D, 0xFF)
        val ok = Argb.rgb(0x01, 0xFC, 0x7B)                 // status.ok #01FC7B
        val okSoft = Argb.rgb(0x86, 0xE9, 0xCD)             // status.okCheck #86E9CD
        val warn = ContinuumTheme.AMBER                     // not present in ref5
        val error = ContinuumTheme.RED                      // not present in ref5
        val text = Argb.rgb(0xFF, 0xFF, 0xFF)               // text.primary #FFFFFF
        val textSecondary = Argb.rgb(0xD0, 0xE1, 0xEA)      // text.secondary #D0E1EA
        val textMuted = ContinuumTheme.TEXT_MUTED
        /** Cyan → violet edge light (cyan.text → pill.rim → violet.hair). */
        val neonEdge = intArrayOf(cyan, cyanDeep, violet)
        val brand = ContinuumTheme.brandGradientColors
    }

    /**
     * Type scale measured in ref5: sp = ink px x 832 dp / 1296 px (Fold7 open 2184 px @ 420 dpi = 832 dp over the
     * 1296 px ref5 screen width), font size = cap height / 0.711 (Roboto cap ratio). See docs/JARVIS-REF5-FARBEN.md.
     */
    object Type {
        val brand = ContinuumTheme.display(600, 18f)          // "Device AI Continuum": cap 18 px
        val title = ContinuumTheme.display(500, 24f)          // "Continuum" header: cap 23 px (incl. glow)
        val heading = ContinuumTheme.text(500, 15f)
        val tileLabel = ContinuumTheme.text(500, 15f)
        val nav = ContinuumTheme.text(400, 13f)
        val body = ContinuumTheme.text(400, 14f)              // dialog "Good afternoon.": cap 15 px
        val subtitle = ContinuumTheme.text(400, 10f)
        val pill = ContinuumTheme.text(500, 10f)              // "JARVIS Mode": cap 11 px
        val caption = ContinuumTheme.text(400, 10f)
        val status = ContinuumTheme.text(400, 9f)
        val label = heading
        val figure = ContinuumTheme.text(500, 13f)            // live values, same size as body
        const val subtitleLineHeight = 13f
    }

    /** Component styles measured in ref5 (px x 0.642 = dp, rounded). */
    data class ComponentStyle(
        val height: Float, val radius: Float, val rimWidth: Float, val fill: Int, val rim: Int,
        val padH: Float, val iconSize: Float, val iconGap: Float, val glow: Glow,
        /** Optional horizontal gradient end colours (left = [fill]/[rim], right = these), measured in ref5. */
        val fillEnd: Int? = null, val rimEnd: Int? = null,
    )

    object Styles {
        /** Tall reference cards, rounded glass. */
        val tile = ComponentStyle(51f, 14f, 1f, Colors.tileFillStart, Colors.tileRimStart, 16f, 24f, 16f, Glow.SUBTLE,
            fillEnd = Colors.tileFillEnd, rimEnd = Colors.tileRimEnd)
        val memoryTile = tile.copy(fill = Colors.memoryTileStart, fillEnd = Colors.memoryTileEnd,
            rim = Colors.cyan, rimEnd = Colors.magenta)
        val agentsTile = tile.copy(fill = Colors.agentsTileStart, fillEnd = Colors.agentsTileEnd,
            rim = Colors.violetRim, rimEnd = Colors.magenta)
        val systemTile = tile.copy(rimEnd = Colors.violetRim)
        /** Tile chevron: glyph 8 px high, 22 px from the right edge → box 10 dp, inset 14 dp. */
        const val chevronSize = 10f
        const val chevronInset = 14f
        /** Nav button 168 x 50 px → 108 x 32 dp, corner 12 px, gap 9 px. Active: brighter fill + rim + glow. */
        val button = ComponentStyle(32f, 8f, 1f, Colors.navSurface, Colors.rim, 12f, 16f, 12f, Glow.OFF)
        val buttonActive = button.copy(fill = Colors.navSurfaceActive, rim = Colors.navRimActive, glow = Glow.NORMAL)
        /** Pill "JARVIS Mode" 151 x 35 px → 97 x 22 dp, fully round, icon 14 px. */
        val pill = ComponentStyle(22f, 11f, 1f, Colors.pillSurface, Colors.pillRimStart, 10f, 9f, 6f, Glow.SUBTLE,
            rimEnd = Colors.pillRimEnd)
        const val buttonGap = 0f
    }

    /** Spacing / shape tokens (dp). */
    object Metrics {
        const val radiusCard = 14f       // 12–16 dp (VIDEO-ANALYSE)
        const val backdropBlur = 24f     // dp, RenderEffect blur radius. GESCHÄTZT: in ref5 nicht messbar (Blur hinter Panels nicht trennbar)
        const val radiusControl = 12f
        const val radiusPill = 999f
        const val gap = ContinuumTheme.CARD_GAP
        const val margin = ContinuumTheme.SCREEN_MARGIN
        const val inputBarHeight = 52f
        const val bottomNavHeight = 74f // = ContinuumHomeLayout.PHONE_NAV_HEIGHT
        const val railCompact = 56f      // Fold7: icon-only rail
        const val railLabelled = 168f    // tablet: icon + label
        const val railFold = 124f        // Fold7 open: icon + label (ref5, Heinz-Entscheid)
    }

    /** Glow strength levels; [Level.OFF] for low-power / reduced-motion. */
    /** Glow strength levels; SUBTLE and STRONG are GESCHÄTZT (ref5 shows only the NORMAL case). */
    enum class Glow(val haloAlpha: Float, val haloWidth: Float) {
        OFF(0f, 0f), SUBTLE(.05f, 4f), // SUBTLE kept below the measured NORMAL
        /** ref5 active Home button: halo reaches 12 px = 8 dp; 1 px off the rim it adds ~10 % of the core (0.9 x .08 x 1.4). */
        NORMAL(.08f, 8f),
        STRONG(.5f, 11f)
    }
}
