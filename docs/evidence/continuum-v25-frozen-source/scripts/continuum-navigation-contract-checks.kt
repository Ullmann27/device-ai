package at.heinz.deviceai

/**
 * Offline executable specification checks. No Android SDK, network or personal data required.
 * Run using scripts/continuum-navigation-jvm-check.sh in a complete source checkout.
 */
object ContinuumNavigationContractChecks {
    @JvmStatic
    fun main(args: Array<String>) {
        val spec = ContinuumWorkAreaNavigationSpec
        check(spec.destinations == enumValues<ContinuumWorkArea>().toList())
        check(spec.destinations.distinct().size == 5)
        var checked = 2
        for (fontScale in listOf(0.5f, 1f, 1.25f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            for (width in listOf(250, 319, 320, 400, 700)) {
                val expected = if (width >= 320 && fontScale.isFinite() &&
                    fontScale in 0.5f..1.25f) 2 else 1
                check(spec.quickActionColumns(width, fontScale) == expected)
                checked++
            }
        }
        for (active in spec.destinations) {
            for (tapped in spec.destinations) {
                val expected = if (tapped == ContinuumWorkArea.MEHR) active else tapped
                check(spec.selectionAfterTap(active, tapped) == expected)
                checked++
            }
        }
        for (wide in listOf(true, false)) for (keyboard in listOf(true, false)) {
            check(spec.showBottomBar(wide, keyboard) == (!wide && !keyboard))
            checked++
        }
        for (destination in spec.destinations) {
            check(spec.accessibilityLabel(destination).contains(destination.title))
            checked++
            check(destination.explanation.isNotBlank())
            checked++
        }
        check(checked == 71)
        println("NAVIGATION_CONTRACT_PASS: $checked checks")
    }
}
