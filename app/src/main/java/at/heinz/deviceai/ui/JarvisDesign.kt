package at.heinz.deviceai.ui

/**
 * Jarvis design system: semantic façade over [ContinuumTheme] (PR #477).
 * Jarvis Kern (cockpit content) and Jarvis Avatar (head) use ONLY these names, so
 * the raw palette can be tuned in one place without touching their code.
 * See docs/DESIGN-SYSTEM.md.
 */
object JarvisDesign {
    /**
     * Colour roles measured pixel by pixel in ref5 (Fold7 open, 1448 x 1086), see docs/JARVIS-REF5-FARBEN.md
     * and tools/measure_ref5_colors.py. Translucent roles: colour solved so that, at the stated alpha over the
     * measured background, the composite equals the measured ref5 pixel.
     */
    object Colors {
        val background = Argb.rgb(0x00, 0x0C, 0x16)        // bg.top   #000C16
        val backgroundBottom = Argb.rgb(0x01, 0x0C, 0x17)  // bg.low   #010C17 (ref5 has no visible vertical gradient)
        val backgroundHaze = Argb.rgb(0x0D, 0x22, 0x50)    // bg.violetAmbient #0D2250 (lower edge light)
        val surface = Argb.of(204, 0x01, 0x1D, 0x2F)        // glass.fill composite #011A2A @80 %
        val surfaceLow = Argb.of(204, 0x06, 0x1F, 0x36)     // tile.fill composite #051B30 @80 %
        val rim = Argb.rgb(0x17, 0x68, 0x7F)                // tile.rim #17687F (1 px)
        val panelRim = Argb.rgb(0x32, 0x9E, 0xC0)           // glass.rim #329EC0
        val cyanDeep = Argb.rgb(0x01, 0x75, 0xAF)           // pill.rim #0175AF
        val navSurface = Argb.rgb(0x04, 0x15, 0x21)         // button.fill #041521
        val navSurfaceActive = Argb.rgb(0x01, 0x2C, 0x3C)   // button.fill.active #012C3C
        val navRimActive = Argb.rgb(0x32, 0xD6, 0xED)       // button.rim.active #32D6ED
        val tileFillStart = Argb.of(204, 0x01, 0x4C, 0x60) // tile.fill.left composite #013F51 @80 %
        val tileFillEnd = Argb.of(204, 0x0C, 0x23, 0x41)   // tile.fill.right composite #0A1E38 @80 %
        val tileRimStart = Argb.rgb(0x01, 0xDA, 0xE8)       // tile.rim.left #01DAE8
        val tileRimEnd = Argb.rgb(0x2E, 0x52, 0x89)         // tile.rim.right #2E5289
        // Dark fill samples from the left/right of the two violet ref5 tiles; no activity/state is implied.
        val memoryTileStart = Argb.rgb(0x19, 0x21, 0x4E)
        val memoryTileEnd = Argb.rgb(0x08, 0x1E, 0x36)
        val agentsTileStart = Argb.rgb(0x21, 0x20, 0x58)
        val agentsTileEnd = Argb.rgb(0x0D, 0x1E, 0x3E)
        val chevron = Argb.rgb(0x51, 0xAD, 0xC5)            // tile.chevron #51ADC5 (antialiased 1 px stroke, true colour brighter)
        val pillRimStart = Argb.rgb(0x2A, 0xEA, 0xFC)       // pill.rim.left #2AEAFC
        val pillRimEnd = Argb.rgb(0xCC, 0x94, 0xEF)         // pill.rim.right #CC94EF
        val cardRimStart = Argb.rgb(0x08, 0x80, 0x9A)       // card.rim.left median of 4 ref5 cards (#0588A1 #0C7792 #0F6A83 #0DB1BD)
        val cardRimEnd = Argb.rgb(0x14, 0x92, 0xBA)         // card.rim.right median (#05B0E0 #16DAF6 #237394; violet only on right capability card)
        val cardRimEndViolet = Argb.rgb(0x83, 0x5F, 0xBE)   // card.rim.right of right capability list #835FBE
        val chipLocalSurface = Argb.rgb(0x02, 0x1E, 0x24)   // chip "Local · Private" fill #021E24
        val pillSurface = Argb.rgb(0x07, 0x20, 0x4D)        // pill.fill #07204D
        val cyan = Argb.rgb(0x2F, 0xF9, 0xFE)               // cyan.text #2FF9FE
        val cyanSoft = Argb.rgb(0x78, 0xF6, 0xFB)           // cyan.icon #78F6FB
        val iconCore = Argb.rgb(0xC9, 0xFF, 0xFF)           // tile.icon #C9FFFF
        val glowCore = Argb.rgb(0xAB, 0xF8, 0xFF)           // glow.core #ABF8FF
        val glowHalo = Argb.rgb(0x01, 0x25, 0x39)           // glow.halo #012539
        val bloomHalo = Argb.rgb(0x08, 0xAF, 0xFF)          // bloom halo hue: #034666 at 4 px off the active rim, normalised to full brightness
        val dust = Argb.rgb(0x0F, 0x93, 0xC4)               // dust mote core #0F93C4 (only 2 motes in the clean sample – low confidence)
        val violet = Argb.rgb(0x91, 0x4C, 0xEF)             // violet.hair #914CEF
        val violetSoft = Argb.rgb(0x91, 0x6D, 0xC9)         // violet.icon #916DC9
        val violetRim = Argb.rgb(0x9E, 0x5B, 0xD5)          // violet.pillRim #9E5BD5
        val activeWhite = Argb.rgb(255, 255, 255)
        val magenta = ContinuumTheme.MAGENTA
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
        val brand = ContinuumTheme.display(600, 16f)          // "Device AI Continuum": cap 18 px
        val title = ContinuumTheme.display(500, 20f)          // "Continuum" header: cap 23 px (incl. glow)
        val heading = ContinuumTheme.text(500, 11f)           // panel title "Active Agents": 14 px incl. ( )
        val tileLabel = ContinuumTheme.text(500, 11f)         // tile "Chat": cap 12 px
        val nav = ContinuumTheme.text(400, 11f)               // nav "Home": cap 12 px
        val body = ContinuumTheme.text(400, 13f)              // dialog "Good afternoon.": cap 15 px
        val subtitle = ContinuumTheme.text(400, 9f)           // tile subtitle: cap 10 px, line pitch 20 px = 13 dp
        val pill = ContinuumTheme.text(500, 10f)              // "JARVIS Mode": cap 11 px
        val caption = ContinuumTheme.text(400, 9f)            // "Android AI Control Center": cap 10 px
        val status = ContinuumTheme.text(400, 8f)             // status bar: cap 9 px
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
        /** Tile 258 x 79 px → 166 x 51 dp, corner 12 px, icon 39 px, inset 25 px, icon→text 26 px. */
        val tile = ComponentStyle(51f, 8f, 1f, Colors.tileFillStart, Colors.tileRimStart, 16f, 24f, 16f, Glow.SUBTLE,
            fillEnd = Colors.tileFillEnd, rimEnd = Colors.tileRimEnd)
        val memoryTile = tile.copy(fill = Colors.memoryTileStart, fillEnd = Colors.memoryTileEnd,
            rim = Colors.violetRim, rimEnd = Colors.tileRimEnd)
        val agentsTile = tile.copy(fill = Colors.agentsTileStart, fillEnd = Colors.agentsTileEnd,
            rim = Colors.violetRim, rimEnd = Colors.violetSoft)
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
        const val buttonGap = 6f
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
