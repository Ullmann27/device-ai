package at.heinz.deviceai.research.exp20.p2

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.random.Random

/** Gap distributions of one text bank (seconds, log-uniform). No gap ever falls between 15 and 35 minutes. */
data class TimeScale(
    val shortLo: Double, val shortHi: Double, val longLo: Double, val longHi: Double,
    val veryLongLo: Double, val veryLongHi: Double, val presenceOldLo: Double, val presenceOldHi: Double,
    val s5Short: ClosedFloatingPointRange<Double>, val s5Long: ClosedFloatingPointRange<Double>
)

/** Sentence templates of one author/bank. {ROOM}, {N}, {A}, {B} are placeholders. */
class Bank(
    val id: String, val scale: TimeScale,
    val maleNames: List<String>, val femaleNames: List<String>, val rooms: List<String>, val fillers: List<String>,
    val shortReferents: List<String>, val shortDecisions: List<String>,
    val longPairs: List<Pair<String, String>>, val longCorrectionTriples: List<Triple<String, String, String>>,
    val corrections: List<String>, val switches: List<String>, val presence: List<String>,
    val devicePairs: List<Pair<String, String>>, val selfContained: List<String>,
    val maleIntro: List<String>, val femaleIntro: List<String>, val maleTwo: List<String>, val femaleTwo: List<String>,
    val maleCorrection: List<String>, val femaleCorrection: List<String>,
    val maleDecisions: List<String>, val femaleDecisions: List<String>,
    val mechanicMale: List<String>, val repairStatements: List<String>
)

enum class RefType { NONE, SHORT, LONG, DEVICE_CMD, PERSON_M, PERSON_F }

/**
 * Builds one variant and labels its decision with ONE rule set for all scenarios (preregistered gold semantics):
 * NONE -> PROCEED. LONG -> PROCEED (EWM2 stays authoritative for durable facts).
 * SHORT -> CLARIFY if no situational referent (NO_REFERENT), if it is older than STALE_SEC (STALE_GAP) or if a
 *   situation switch happened after it (LOCATION_SWITCH); else PROCEED.
 * DEVICE_CMD -> CLARIFY if a presence cue is younger than PRESENCE_WINDOW_SEC or a switch happened after the device
 *   referent (PRESENCE_DRIFT), if the referent is missing (NO_REFERENT) or stale (STALE_GAP); else PROCEED.
 * PERSON_x -> live candidates = persons of that gender mentioned within PERSON_WINDOW_SEC and not corrected away;
 *   0 -> CLARIFY (NO_REFERENT), >= 2 -> CLARIFY (AMBIGUOUS_ENTITY), 1 -> PROCEED.
 * negControl marks PROCEED decisions that carry a distractor (switch, correction, old cue, long gap).
 */
class DialogBuilder(private val rng: Random, private val scale: TimeScale) {
    val turns = ArrayList<Turn>()
    private var t = 0.0
    private var lastShort: Double? = null
    private var lastDevice: Double? = null
    private val switches = ArrayList<Double>()
    private val presences = ArrayList<Double>()
    private var corrections = 0
    private var lastLong: Double? = null
    private val persons = LinkedHashMap<String, Pair<Char, Double>>()   // name -> gender, last mention
    private val excluded = HashSet<String>()

    private fun logU(lo: Double, hi: Double) = exp(rng.nextDouble(ln(lo), ln(hi)))
    fun wait(sec: Double) { t = ((t + sec) * 1000.0).roundToLong() / 1000.0 }
    fun short() = wait(logU(scale.shortLo, scale.shortHi))
    fun long() = wait(logU(scale.longLo, scale.longHi))
    fun veryLong() = wait(logU(scale.veryLongLo, scale.veryLongHi))
    fun presenceOld() = wait(logU(scale.presenceOldLo, scale.presenceOldHi))
    fun waitIn(r: ClosedFloatingPointRange<Double>) = wait(if (r.start == r.endInclusive) r.start else logU(r.start, r.endInclusive))

    private fun say(text: String) { turns += Turn(t, text) }
    fun filler(text: String) = say(text)
    fun shortRef(text: String) { say(text); lastShort = t }
    fun deviceRef(text: String) { say(text); lastShort = t; lastDevice = t }
    fun longRef(text: String) { say(text); lastLong = t }
    fun switch(text: String) { say(text); switches += t }
    fun presence(text: String) { say(text); presences += t }
    fun correction(text: String) { say(text); corrections++ }
    fun person(text: String, gender: Char, vararg names: String) { say(text); for (n in names) persons[n] = gender to t }
    fun personCorrection(text: String, gender: Char, keep: String, drop: String) {
        say(text); corrections++; excluded += drop; persons[keep] = gender to t
    }
    fun repair(text: String) { say(text); lastLong = t; lastShort = t }

    fun decide(text: String, ref: RefType) {
        val (gold, cause, neg) = label(ref)
        turns += Turn(t, text, true, gold, cause, neg)
    }

    private fun label(ref: RefType): Triple<Gold, Cause, Boolean> {
        val anySwitch = switches.isNotEmpty()
        return when (ref) {
            RefType.NONE -> Triple(Gold.PROCEED, Cause.NONE, anySwitch || (lastShort?.let { t - it > P2Config.STALE_SEC } ?: false))
            RefType.LONG -> Triple(Gold.PROCEED, Cause.NONE, anySwitch || corrections > 0 || (lastLong?.let { t - it > P2Config.STALE_SEC } ?: false))
            RefType.SHORT -> {
                val r = lastShort
                when {
                    r == null -> Triple(Gold.CLARIFY, Cause.NO_REFERENT, false)
                    t - r > P2Config.STALE_SEC -> Triple(Gold.CLARIFY, Cause.STALE_GAP, false)
                    switches.any { it > r } -> Triple(Gold.CLARIFY, Cause.LOCATION_SWITCH, false)
                    else -> Triple(Gold.PROCEED, Cause.NONE, anySwitch || corrections > 0)
                }
            }
            RefType.DEVICE_CMD -> {
                val r = lastDevice
                when {
                    presences.any { t - it <= P2Config.PRESENCE_WINDOW_SEC } -> Triple(Gold.CLARIFY, Cause.PRESENCE_DRIFT, false)
                    r != null && switches.any { it > r } -> Triple(Gold.CLARIFY, Cause.PRESENCE_DRIFT, false)
                    r == null -> Triple(Gold.CLARIFY, Cause.NO_REFERENT, false)
                    t - r > P2Config.STALE_SEC -> Triple(Gold.CLARIFY, Cause.STALE_GAP, false)
                    else -> Triple(Gold.PROCEED, Cause.NONE, presences.isNotEmpty() || anySwitch)
                }
            }
            RefType.PERSON_M, RefType.PERSON_F -> {
                val g = if (ref == RefType.PERSON_M) 'm' else 'f'
                val sameGender = persons.filter { it.value.first == g }
                val live = sameGender.filter { (n, v) -> n !in excluded && t - v.second <= P2Config.PERSON_WINDOW_SEC }
                when {
                    live.isEmpty() -> Triple(Gold.CLARIFY, Cause.NO_REFERENT, false)
                    live.size >= 2 -> Triple(Gold.CLARIFY, Cause.AMBIGUOUS_ENTITY, false)
                    else -> Triple(Gold.PROCEED, Cause.NONE, sameGender.size >= 2 || corrections > 0)
                }
            }
        }
    }
}

object P2Generator {
    private fun <T> List<T>.pick(r: Random) = this[r.nextInt(size)]
    private fun two(r: Random, names: List<String>): Pair<String, String> {
        val a = names.pick(r); var b = names.pick(r); while (b == a) b = names.pick(r); return a to b
    }

    /** Clusters per scenario are generated in index order; the split is a pure function of (bank, index). */
    fun generate(bank: Bank, seed: Long, splits: List<Pair<Split, Int>>): List<Dialog> {
        val out = ArrayList<Dialog>()
        for (s in Scenario.values()) {
            var index = 0
            for ((split, count) in splits) repeat(count) {
                val id = "%s-%s-%03d".format(bank.id, s.name.substringBefore('_'), index)
                val clusterSeed = seed * 1_000_003L + s.ordinal * 10_007L + index
                for (v in 0 until P2Config.VARIANTS) out += Dialog(id, v, s, split, variant(bank, s, index, v, clusterSeed))
                index++
            }
        }
        out.forEach { it.validate() }
        return out
    }

    fun devBank(): Bank = DevBank.bank

    private fun variant(bank: Bank, s: Scenario, index: Int, v: Int, clusterSeed: Long): List<Turn> {
        val stem = Random(clusterSeed)                       // fixed per cluster: latent structure and, for S5, all text
        val r = Random(clusterSeed * 31 + v + 1)              // per variant: names, sentences, gaps
        val b = DialogBuilder(r, bank.scale)
        fun room(x: String) = x.replace("{ROOM}", bank.rooms.pick(r))
        when (s) {
            Scenario.S1_LOCATION -> {
                val shortKind = index % 2 == 0
                val order = (index / 2) % 3                  // 0 switch after referent, 1 before, 2 none
                val longPair = bank.longPairs.pick(r)
                fun ref() = if (shortKind) b.shortRef(room(bank.shortReferents.pick(r))) else b.longRef(longPair.first)
                when (order) {
                    0 -> { ref(); b.short(); b.switch(bank.switches.pick(r)) }
                    1 -> { b.switch(bank.switches.pick(r)); b.short(); ref() }
                    else -> { b.filler(bank.fillers.pick(r)); b.short(); ref() }
                }
                b.short()
                if (shortKind) b.decide(bank.shortDecisions.pick(r), RefType.SHORT) else b.decide(longPair.second, RefType.LONG)
            }
            Scenario.S2_CORRECTION -> when (index % 4) {
                0 -> { val (ref, corr, q) = bank.longCorrectionTriples.pick(r)
                    b.longRef(ref); b.short(); b.correction(corr); b.short(); b.decide(q, RefType.LONG) }
                1 -> { val male = r.nextBoolean(); val (x, y) = two(r, if (male) bank.maleNames else bank.femaleNames)
                    b.person((if (male) bank.maleTwo else bank.femaleTwo).pick(r).replace("{A}", x).replace("{B}", y), if (male) 'm' else 'f', x, y)
                    b.short(); b.filler(bank.fillers.pick(r)); b.short()
                    b.decide((if (male) bank.maleDecisions else bank.femaleDecisions).pick(r), if (male) RefType.PERSON_M else RefType.PERSON_F) }
                2 -> { val male = r.nextBoolean(); val g = if (male) 'm' else 'f'; val (x, y) = two(r, if (male) bank.maleNames else bank.femaleNames)
                    b.person((if (male) bank.maleTwo else bank.femaleTwo).pick(r).replace("{A}", x).replace("{B}", y), g, x, y)
                    b.short(); b.personCorrection((if (male) bank.maleCorrection else bank.femaleCorrection).pick(r).replace("{A}", x).replace("{B}", y), g, x, y)
                    b.short(); b.decide((if (male) bank.maleDecisions else bank.femaleDecisions).pick(r), if (male) RefType.PERSON_M else RefType.PERSON_F) }
                else -> { val male = r.nextBoolean(); val n = (if (male) bank.maleNames else bank.femaleNames).pick(r)
                    b.person((if (male) bank.maleIntro else bank.femaleIntro).pick(r).replace("{N}", n), if (male) 'm' else 'f', n)
                    b.short(); b.correction(bank.corrections.pick(r)); b.short()
                    b.decide((if (male) bank.maleDecisions else bank.femaleDecisions).pick(r), if (male) RefType.PERSON_M else RefType.PERSON_F) }
            }
            Scenario.S3_INTERRUPTION -> {
                val shortKind = index % 2 == 0
                val withFiller = (index / 2) % 2 == 1
                val longGap = v >= 2                          // variants 0,1 short pause, 2,3 long pause
                val longPair = bank.longPairs.pick(r)
                if (shortKind) b.shortRef(room(bank.shortReferents.pick(r))) else b.longRef(longPair.first)
                if (longGap) b.long() else b.short()
                if (withFiller) { b.filler(bank.fillers.pick(r)); b.short() }
                if (shortKind) b.decide(bank.shortDecisions.pick(r), RefType.SHORT) else b.decide(longPair.second, RefType.LONG)
            }
            Scenario.S4_NESTED_ENTITIES -> {
                val male = r.nextBoolean(); val g = if (male) 'm' else 'f'
                val (x, y) = two(r, if (male) bank.maleNames else bank.femaleNames)
                val intro = if (male) bank.maleIntro else bank.femaleIntro
                val q = (if (male) bank.maleDecisions else bank.femaleDecisions).pick(r)
                val ref = if (male) RefType.PERSON_M else RefType.PERSON_F
                when (index % 4) {
                    0 -> { b.person(intro.pick(r).replace("{N}", x), g, x); b.short(); b.person(intro.pick(r).replace("{N}", y), g, y); b.short() }
                    1 -> { b.person(intro.pick(r).replace("{N}", x), g, x); b.veryLong(); b.person(intro.pick(r).replace("{N}", y), g, y); b.short() }
                    2 -> { b.filler(bank.fillers.pick(r)); b.short(); b.person(intro.pick(r).replace("{N}", x), g, x); b.short() }
                    else -> { val m = bank.maleNames.pick(r)
                        b.person(bank.mechanicMale.pick(r).replace("{N}", m), 'm', m); b.short(); b.repair(bank.repairStatements.pick(r)); b.short()
                        b.decide(bank.maleDecisions.pick(r), RefType.PERSON_M); return b.turns }
                }
                b.decide(q, ref)
            }
            Scenario.S5_SAME_SIGNALS_DT -> {
                // identical text in all four variants; only the pause differs (5 s class vs hours class)
                val text = bank.shortReferents.pick(stem).replace("{ROOM}", bank.rooms.pick(stem))
                val q = bank.shortDecisions.pick(stem)
                b.shortRef(text)
                b.waitIn(if (v < 2) bank.scale.s5Short else bank.scale.s5Long)
                b.decide(q, RefType.SHORT)
            }
            Scenario.S6_PRESENCE_DRIFT -> {
                val (ref, cmd) = bank.devicePairs.pick(r)
                when (index % 4) {
                    0 -> { b.deviceRef(ref); b.short(); b.presence(bank.presence.pick(r)); b.short() }
                    1 -> { b.deviceRef(ref); b.short(); b.switch(bank.switches.pick(r)); b.short() }
                    2 -> { b.filler(bank.fillers.pick(r)); b.short(); b.deviceRef(ref); b.short() }
                    else -> { b.presence(bank.presence.pick(r)); b.presenceOld(); b.deviceRef(ref); b.short() }
                }
                b.decide(cmd, RefType.DEVICE_CMD)
            }
            Scenario.S7_AMBIGUOUS -> when (index % 4) {
                0 -> { b.filler(bank.fillers.pick(r)); b.short(); b.filler(bank.fillers.pick(r)); b.short(); b.decide(bank.shortDecisions.pick(r), RefType.SHORT) }
                1 -> { b.filler(bank.fillers.pick(r)); b.short(); b.filler(bank.fillers.pick(r)); b.short()
                    val male = r.nextBoolean(); b.decide((if (male) bank.maleDecisions else bank.femaleDecisions).pick(r), if (male) RefType.PERSON_M else RefType.PERSON_F) }
                2 -> { b.filler(bank.fillers.pick(r)); b.short(); b.decide(bank.selfContained.pick(r), RefType.NONE) }
                else -> { b.shortRef(room(bank.shortReferents.pick(r))); b.long(); b.decide(bank.selfContained.pick(r), RefType.NONE) }
            }
        }
        return b.turns
    }
}

/** Development text bank (TRAIN + VALIDATION). Written together with, and before freezing, the encoder. */
private object DevBank {
    val bank = Bank(
        id = "dev",
        scale = TimeScale(3.0, 120.0, 2400.0, 10800.0, 9000.0, 14400.0, 720.0, 1500.0, 3.0..10.0, 7200.0..10800.0),
        maleNames = listOf("Huber", "Gruber", "Bauer", "Wagner", "Pichler", "Steiner", "Moser", "Hofer", "Leitner", "Berger"),
        femaleNames = listOf("Mayr", "Fuchs", "Eder", "Schmid", "Winkler", "Weber", "Schwarz", "Lang", "Brunner", "Wolf"),
        rooms = listOf("Wohnzimmer", "Schlafzimmer", "Keller", "Küche"),
        fillers = listOf("Wie wird das Wetter morgen?", "Erzähl mir einen kurzen Witz.", "Was bedeutet das Wort Muße?",
            "Spiel mir bitte etwas Ruhiges vor.", "Wie viele Tage hat der Februar?"),
        shortReferents = listOf("Im {ROOM} brennt noch das Licht.", "Im {ROOM} läuft noch der Fernseher.",
            "Auf dem Zettel in der Küche stehen drei Dinge: Milch, Brot und Kaffee.", "Die Heizung im {ROOM} steht auf Stufe drei."),
        shortDecisions = listOf("Ist das dort noch an?", "Und wie war das zweite davon?", "Kannst du mir das von vorhin nochmal sagen?",
            "Was war damit nochmal?"),
        longPairs = listOf(
            "Der Reparaturauftrag für die Waschmaschine läuft bis Freitag." to "Wie steht es um den Reparaturauftrag?",
            "Der Termin beim Zahnarzt ist am Mittwoch um neun." to "Wann ist der Termin nochmal?",
            "Das Projekt Gartenhaus soll bis Mai fertig sein." to "Wie weit ist das Projekt?",
            "Die Bestellung für das neue Fahrrad ist schon bezahlt." to "Was ist mit der Bestellung?"),
        longCorrectionTriples = listOf(
            Triple("Der Termin in der Werkstatt ist am Mittwoch.", "Nein, ich meinte nicht Mittwoch, sondern Donnerstag.", "Wann ist der Termin nochmal?"),
            Triple("Die Rechnung für den Kühlschrank beträgt 300 Euro.", "Falsch, es sind 350 Euro.", "Wie hoch war die Rechnung?"),
            Triple("Das Projekt Gartenhaus soll bis Mai fertig sein.", "Stimmt nicht, bis Juni.", "Bis wann läuft das Projekt?")),
        corrections = listOf("Nein, das war nicht gestern, sondern heute früh.", "Falsch, ich meinte die andere Farbe.",
            "Stimmt nicht ganz, es waren vier Stück."),
        switches = listOf("Ich sitz jetzt im Auto.", "Ich bin jetzt unterwegs zur Arbeit.", "Ich steig gerade in den Zug.",
            "So, ich bin wieder daheim."),
        presence = listOf("Da klingelt jemand an der Tür.", "Ich glaub, da ist noch wer im Haus.", "Wir haben gerade Besuch."),
        devicePairs = listOf("Die Haustür ist abgesperrt." to "Mach sie bitte auf.", "Der Herd in der Küche ist noch an." to "Schalt ihn bitte ab.",
            "Das Garagentor ist offen." to "Öffne die Garagen-App und mach das Tor zu."),
        selfContained = listOf("Wie viel sind 12 mal 14?", "Erklär mir kurz, was Photosynthese ist.", "Wie lange kocht ein weiches Ei?",
            "Wie heißt die Hauptstadt von Kanada?"),
        maleIntro = listOf("Ich hab vorhin mit Herrn {N} telefoniert.", "Der Kollege {N} hat mir geschrieben."),
        femaleIntro = listOf("Ich hab vorhin mit Frau {N} telefoniert.", "Die Nachbarin {N} hat mir eine Nachricht geschickt."),
        maleTwo = listOf("Ich war mit Herrn {A} und Herrn {B} beim Training."),
        femaleTwo = listOf("Frau {A} und Frau {B} haben mich angerufen."),
        maleCorrection = listOf("Nein, falsch: nur Herr {A} war dabei, Herr {B} war krank."),
        femaleCorrection = listOf("Nein, korrigiere: angerufen hat nur Frau {A}, nicht Frau {B}."),
        maleDecisions = listOf("Was hat er gesagt?", "Hat er sich schon gemeldet?"),
        femaleDecisions = listOf("Was hat sie gesagt?", "Hat sie sich schon gemeldet?"),
        mechanicMale = listOf("Der Mechaniker {N} kümmert sich um das Auto."),
        repairStatements = listOf("Die Reparatur der Heizung ist für Montag geplant.")
    )
}
