package at.heinz.deviceai.research.exp20.p2

/**
 * EXPLORATORY, written after the holdout was opened (not preregistered, changes no decision):
 * share of each sentence class whose intended cue the frozen encoder actually sees, DEV bank vs holdout bank.
 */
object P2Explore {
    @JvmStatic fun main(args: Array<String>) {
        fun cov(ts: List<String>, name: String) = ts.count { P2Encoder.encode(it.replace("{N}", "X").replace("{A}", "X").replace("{B}", "Y"))[P2Encoder.NAMES.indexOf(name)] > 0.0 }
        // a {ROOM} template counts once per room of its own bank (holdout rooms are not in the frozen lexicon)
        fun expand(b: Bank, ts: List<String>) = ts.flatMap { t -> if ("{ROOM}" in t) b.rooms.map { t.replace("{ROOM}", it) } else listOf(t) }
        fun row(label: String, name: String, pick: (Bank) -> List<String>) {
            val d = expand(P2Generator.devBank(), pick(P2Generator.devBank())); val h = expand(P2HoldoutBank.bank, pick(P2HoldoutBank.bank))
            println("%-28s %-18s dev %d/%d  holdout %d/%d".format(label, name, cov(d, name), d.size, cov(h, name), h.size))
        }
        println("EXP-20 P2 exploratory encoder coverage (frozen encoder ${P2Encoder.VERSION})")
        row("short referents", "DEVICE_OR_PLACE") { it.shortReferents }
        row("short decisions", "SHORT_REF") { it.shortDecisions }
        row("long referents+decisions", "LONG_REF") { b -> b.longPairs.flatMap { listOf(it.first, it.second) } }
        row("long corrections", "CORRECTION") { b -> b.longCorrectionTriples.map { it.second } }
        row("long correction questions", "LONG_REF") { b -> b.longCorrectionTriples.map { it.third } }
        row("generic corrections", "CORRECTION") { it.corrections }
        row("person corrections", "CORRECTION") { it.maleCorrection + it.femaleCorrection }
        row("switches", "SWITCH") { it.switches }
        row("presence cues", "PRESENCE_UNCERTAIN") { it.presence }
        row("device referents", "DEVICE_OR_PLACE") { b -> b.devicePairs.map { it.first } }
        row("device commands", "SHORT_REF") { b -> b.devicePairs.map { it.second } }
        row("person intros", "PERSON_INTRO") { it.maleIntro + it.femaleIntro + it.maleTwo + it.femaleTwo + it.mechanicMale }
        row("pronoun questions", "PERSON_PRONOUN") { it.maleDecisions + it.femaleDecisions }
        for ((label, pick) in listOf<Pair<String, (Bank) -> List<String>>>("fillers" to { it.fillers }, "self-contained" to { it.selfContained })) {
            val d = pick(P2Generator.devBank()); val h = pick(P2HoldoutBank.bank)
            println("%-28s %-18s dev %d/%d  holdout %d/%d".format(label, "no cue at all", d.count { t -> P2Encoder.encode(t).all { it == 0.0 } }, d.size, h.count { t -> P2Encoder.encode(t).all { it == 0.0 } }, h.size))
        }
    }
}
