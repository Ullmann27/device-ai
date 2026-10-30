package at.heinz.deviceai.research.exp20.p2

/**
 * EXP-20 P2 input encoder, FROZEN before the holdout text bank was written (its Git blob is preregistered).
 * It sees only the public text of one user turn: no EWM2 facts, no labels, no gold, no model output, no clock.
 * All arms receive exactly these 8 values in [0, 1]; the temporal arms additionally integrate them over time.
 * Lexical cues are deliberately coarse; an unknown phrasing is simply not seen, equally for every arm.
 */
object P2Encoder {
    const val VERSION = "exp20-p2-encoder-v1"
    val NAMES = listOf("SWITCH", "CORRECTION", "SHORT_REF", "LONG_REF", "PERSON_INTRO", "PERSON_PRONOUN", "DEVICE_OR_PLACE", "PRESENCE_UNCERTAIN")

    private fun rx(p: String) = Regex("(?iU)$p")
    private val switch = rx("""\b(bin jetzt (im|in der|unterwegs|da)|sitz\w* jetzt|steig\w* (jetzt |gerade )?(ins|in den|in die)|bin (gerade )?unterwegs|bin angekommen|fahr\w* (jetzt )?los|bin wieder (daheim|zu ?hause|im büro)|jetzt im (auto|zug|bus|büro))""")
    private val correction = rx("""(\bnein\b|\bfalsch\b|\bstimmt nicht\b|\bkorrigier\w*|\bich meinte\b|\bich meine nicht\b|\bnicht\b[^.?!]{1,40},\s*sondern\b)""")
    private val shortRef = rx("""\b(mach (es|das|sie|ihn)|schalt (es|das|sie|ihn)|dreh (es|das|sie|ihn)|das zweite|der zweite|die zweite|den zweiten|davon|von vorhin|wie eben|dasselbe|das gleiche|dort|da drüben|damit|nochmal das)\b""")
    private val longRef = rx("""\b(projekt\w*|reparatur\w*|termin\w*|auftrag|werkstatt|vertrag\w*|rechnung\w*|bestellung\w*)\b""")
    private val personIntro = rx("""\b(herr|herrn|frau|kollege|kollegin|nachbar|nachbarin|mechaniker\w*|arzt|ärztin|techniker\w*)\b""")
    private val pronoun = Regex("""(?U)\b(er|sie|ihm|ihn)\b|^(Er|Sie|Ihm|Ihn)\b""")
    private val deviceOrPlace = rx("""\b(tür|haustür|herd|licht|heizung|garage\w*|fenster|rollo\w*|alarm\w*|fernseher|tor|wohnzimmer|küche|schlafzimmer|keller)\b""")
    private val presence = rx("""\b(noch wer|jemand|wer ist da|besuch|nicht allein|klingelt|fremde\w*|gast)\b""")

    fun encode(text: String): DoubleArray = doubleArrayOf(
        flag(switch, text), flag(correction, text), flag(shortRef, text), count2(longRef, text),
        count2(personIntro, text), flag(pronoun, text), flag(deviceOrPlace, text), flag(presence, text)
    )

    private fun flag(r: Regex, t: String) = if (r.containsMatchIn(t)) 1.0 else 0.0
    /** 0, 0.5 or 1 for zero, one, two or more matches: "Herrn A und Herrn B" differs from "Herrn A". */
    private fun count2(r: Regex, t: String) = minOf(r.findAll(t).count(), 2) / 2.0
}
