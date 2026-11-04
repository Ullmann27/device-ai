package at.heinz.deviceai.ui

/**
 * Jarvis design system: semantic façade over [ContinuumTheme] (PR #477).
 * Jarvis Kern (cockpit content) and Jarvis Avatar (head) use ONLY these names, so
 * the raw palette can be tuned in one place without touching their code.
 * See docs/DESIGN-SYSTEM.md.
 */
object JarvisDesign {
    /** Colour roles: deep navy base, neon cyan and violet accents (ref2/ref4/ref5). */
    /**
     * Jarvis values from VIDEO-SPEC.md (Heinz' videos). Deliberately differ from ContinuumTheme (#477):
     * background #050914→#0A1128 (#477: #000A1D), cyan #00FFFF (#477: #3EC8FF), violet #8A2BE2 (#477: #925CFF),
     * glass rgba(10,15,26,.6) (#477: opaque navy). ContinuumTheme is left untouched.
     */
    object Colors {
        val background = Argb.rgb(0x05, 0x09, 0x14)        // #050914 top of page gradient
        val backgroundBottom = Argb.rgb(0x0A, 0x11, 0x28)  // #0A1128
        val backgroundHaze = ContinuumTheme.BACKGROUND_GLOW
        val surface = Argb.of(153, 10, 15, 26)              // rgba(10,15,26,0.6) glass
        val surfaceLow = Argb.of(153, 6, 10, 20)
        val navSurface = Argb.of(170, 8, 12, 24)
        val cyan = Argb.rgb(0x00, 0xFF, 0xFF)               // #00FFFF Neon-Cyan
        val cyanSoft = Argb.rgb(150, 255, 255)
        val violet = Argb.rgb(0x8A, 0x2B, 0xE2)             // #8A2BE2 Violett
        val activeWhite = Argb.rgb(255, 255, 255)
        val magenta = ContinuumTheme.MAGENTA
        val ok = ContinuumTheme.GREEN
        val warn = ContinuumTheme.AMBER
        val error = ContinuumTheme.RED
        val text = ContinuumTheme.TEXT
        val textSecondary = ContinuumTheme.TEXT_SECONDARY
        val textMuted = ContinuumTheme.TEXT_MUTED
        /** Cyan → violet edge light used by glass cards and neon lines. */
        val neonEdge = intArrayOf(Argb.rgb(0, 255, 255), Argb.rgb(40, 140, 255), Argb.rgb(0x8A, 0x2B, 0xE2))
        val brand = ContinuumTheme.brandGradientColors
    }

    /** Type scale (sp). Display = Montserrat, text = Roboto (bundled, see ContinuumFonts). */
    object Type {
        val brand = ContinuumTheme.display(600, 22f, 2.4f)    // "CONTINUUM"
        val title = ContinuumTheme.display(500, 17f, .6f)     // card titles, "JARVIS – Aktiv"
        val label = ContinuumTheme.text(500, 12f, .8f)        // small caps labels
        val body = ContinuumTheme.text(400, 14f)
        val caption = ContinuumTheme.text(400, 11f)
        val figure = ContinuumTheme.text(500, 26f)             // large live values (real data only)
        val nav = ContinuumTheme.text(500, 13f)
    }

    /** Spacing / shape tokens (dp). */
    object Metrics {
        const val radiusCard = ContinuumTheme.CARD_RADIUS
        const val radiusControl = 12f
        const val radiusPill = 999f
        const val gap = ContinuumTheme.CARD_GAP
        const val margin = ContinuumTheme.SCREEN_MARGIN
        const val inputBarHeight = 52f
        const val bottomNavHeight = ContinuumHomeLayout.PHONE_NAV_HEIGHT
        const val railCompact = 56f      // Fold7: icon-only rail
        const val railLabelled = 168f    // tablet: icon + label
        const val railFold = 124f        // Fold7 open: icon + label (ref5, Heinz-Entscheid)
    }

    /** Glow strength levels; [Level.OFF] for low-power / reduced-motion. */
    enum class Glow(val haloAlpha: Float, val haloWidth: Float) {
        OFF(0f, 0f), SUBTLE(.18f, 4f), NORMAL(.32f, 7f), STRONG(.5f, 11f)
    }
}
