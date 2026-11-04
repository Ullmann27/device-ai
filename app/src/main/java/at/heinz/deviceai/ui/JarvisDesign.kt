package at.heinz.deviceai.ui

/**
 * Jarvis design system: semantic façade over [ContinuumTheme] (PR #477).
 * Jarvis Kern (cockpit content) and Jarvis Avatar (head) use ONLY these names, so
 * the raw palette can be tuned in one place without touching their code.
 * See docs/DESIGN-SYSTEM.md.
 */
object JarvisDesign {
    /** Colour roles: deep navy base, neon cyan and violet accents (ref2/ref4/ref5). */
    object Colors {
        val background = ContinuumTheme.BACKGROUND          // Dunkelblau/Schwarz
        val backgroundHaze = ContinuumTheme.BACKGROUND_GLOW
        val surface = ContinuumTheme.CARD_FILL_TOP
        val surfaceLow = ContinuumTheme.CARD_FILL_BOTTOM
        val navSurface = ContinuumTheme.NAV_FILL
        val cyan = ContinuumTheme.CYAN                      // Neon-Cyan, aktive Elemente
        val cyanSoft = ContinuumTheme.CYAN_SOFT
        val violet = ContinuumTheme.VIOLET                  // Violett, zweiter Akzent
        val magenta = ContinuumTheme.MAGENTA
        val ok = ContinuumTheme.GREEN
        val warn = ContinuumTheme.AMBER
        val error = ContinuumTheme.RED
        val text = ContinuumTheme.TEXT
        val textSecondary = ContinuumTheme.TEXT_SECONDARY
        val textMuted = ContinuumTheme.TEXT_MUTED
        /** Cyan → violet edge light used by glass cards and neon lines. */
        val neonEdge = intArrayOf(ContinuumTheme.BORDER_LEFT, ContinuumTheme.BLUE, ContinuumTheme.VIOLET)
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
        const val railLabelled = 168f    // tablet/desktop: icon + label
    }

    /** Glow strength levels; [Level.OFF] for low-power / reduced-motion. */
    enum class Glow(val haloAlpha: Float, val haloWidth: Float) {
        OFF(0f, 0f), SUBTLE(.18f, 4f), NORMAL(.32f, 7f), STRONG(.5f, 11f)
    }
}
