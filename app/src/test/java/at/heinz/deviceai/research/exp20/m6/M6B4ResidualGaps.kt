package at.heinz.deviceai.intelligence.ewm

/** EXP-20 M6-B4: residual supersession gaps after the holder fix (expected outcome noted in the evidence file). */
object M6B4ResidualGaps {
    @JvmStatic fun main(args: Array<String>) {
        val cases = listOf(
            "alias" to listOf("Meine Schwester Anna wohnt in Linz.", "Inzwischen wohnt Anna in Graz."),
            "pronoun" to listOf("Meine Schwester Anna wohnt in Linz.", "Sie wohnt jetzt in Graz."),
            "subclause" to listOf("Ich wohne in Wien.", "Weil es billiger ist, wohne ich jetzt in Graz."),
            "datedvalidity" to listOf("Ich wohne in Wien.", "Ab nächstem Monat wohne ich in Graz."))
        for ((n, f) in cases) println("$n supersedes=${Ewm1Memory(f.mapIndexed { i, t -> "f$i" to t }).supersedes}")
    }
}
