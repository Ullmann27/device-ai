package at.heinz.deviceai.research.m12

import java.security.MessageDigest

/**
 * M12 · labelled cases for the claim audit. Labels come from construction (a known injected error or a known
 * faithful paraphrase), never from a model's judgement. DEV and HOLDOUT use different sentence templates AND
 * different names/places/numbers, so an audit tuned on DEV meets unseen wording on HOLDOUT.
 */
data class ClaimCase(val id: String, val split: String, val category: String, val evidence: String,
                     val answer: String, val isError: Boolean)

object ClaimAuditCases {
    val ERROR_CATEGORIES = listOf("E_NUM", "E_NUMWORD", "E_DATE", "E_WEEKDAY", "E_TIME", "E_NEG", "E_COMPOUND",
        "E_OUTDATED", "E_UNSUPPORTED", "E_CONFLICT")
    val CORRECT_CATEGORIES = listOf("C_RESTATE", "C_REORDER", "C_PARAPHRASE", "C_PARTIAL", "C_NEG_KEPT", "C_PRONOUN")

    private data class Fill(val p: String, val p2: String, val c: String, val c2: String, val n: Int, val d: Int,
                            val m: String, val w: String, val w2: String, val h: Int, val obj: String)

    private val DEV_FILLS = listOf(
        Fill("Anna", "Markus", "Graz", "Linz", 12, 3, "März", "Montag", "Dienstag", 8, "Fahrrad"),
        Fill("Petra", "Jonas", "Salzburg", "Wels", 7, 14, "April", "Mittwoch", "Freitag", 10, "Auto"),
        Fill("Clara", "Felix", "Villach", "Steyr", 25, 21, "Mai", "Donnerstag", "Samstag", 15, "Boot"),
        Fill("Sophie", "Lukas", "Bregenz", "Krems", 4, 9, "Juni", "Freitag", "Sonntag", 17, "Zelt"),
        Fill("Hanna", "David", "Leoben", "Baden", 31, 28, "Juli", "Samstag", "Montag", 9, "Klavier"))
    private val HOLDOUT_FILLS = listOf(
        Fill("Ida", "Tobias", "Dornbirn", "Amstetten", 18, 6, "Oktober", "Dienstag", "Donnerstag", 11, "Kajak"),
        Fill("Lea", "Simon", "Feldkirch", "Mödling", 9, 17, "November", "Sonntag", "Mittwoch", 13, "Motorrad"),
        Fill("Emma", "Paul", "Kufstein", "Tulln", 43, 2, "Dezember", "Montag", "Freitag", 7, "Cello"),
        Fill("Mia", "Elias", "Lienz", "Gmünd", 6, 23, "Jänner", "Mittwoch", "Samstag", 19, "Gewächshaus"),
        Fill("Nora", "Jakob", "Zwettl", "Melk", 27, 11, "Februar", "Donnerstag", "Dienstag", 14, "Wohnmobil"))
    private val WORDS = mapOf(3 to "drei", 4 to "vier", 5 to "fünf", 6 to "sechs", 7 to "sieben", 8 to "acht", 9 to "neun")

    private fun dev(f: Fill): List<Triple<String, String, Pair<String, String>>> {
        val wordN = WORDS[(f.n % 6) + 3]!!; val wordWrong = WORDS[((f.n + 1) % 6) + 3]!!
        return listOf(
            Triple("E_NUM", "${f.p} hat ${f.n} Bücher über ${f.c} gekauft.", "${f.p} hat ${f.n + 2} Bücher über ${f.c} gekauft." to ""),
            Triple("E_NUMWORD", "${f.p} hat $wordN Katzen.", "${f.p} hat $wordWrong Katzen." to ""),
            Triple("E_DATE", "Der Termin mit ${f.p2} ist am ${f.d}. ${f.m}.", "Der Termin mit ${f.p2} ist am ${f.d + 1}. ${f.m}." to ""),
            Triple("E_WEEKDAY", "${f.p} trainiert jeden ${f.w} in ${f.c}.", "${f.p} trainiert jeden ${f.w2} in ${f.c}." to ""),
            Triple("E_TIME", "Das Treffen mit ${f.p2} beginnt um ${f.h} Uhr.", "Das Treffen mit ${f.p2} beginnt um ${f.h + 1} Uhr." to ""),
            Triple("E_NEG", "${f.p} hat das ${f.obj} nicht verkauft.", "${f.p} hat das ${f.obj} verkauft." to ""),
            Triple("E_COMPOUND", "${f.p} wohnt in ${f.c} und ${f.p2} arbeitet in ${f.c2}.", "${f.p} wohnt in ${f.c} und ${f.p2} arbeitet in ${f.c}." to ""),
            Triple("E_OUTDATED", "Bis 2023 wohnte ${f.p} in ${f.c2}, seit 2024 wohnt sie in ${f.c}.", "${f.p} wohnt in ${f.c2}." to ""),
            Triple("E_UNSUPPORTED", "${f.p} hat am ${f.w} mit ${f.p2} telefoniert.", "${f.p} ist mit ${f.p2} verheiratet und plant eine Hochzeitsreise." to ""),
            Triple("E_CONFLICT", "${f.p} hat ${f.n} Euro bezahlt. Laut Beleg hat ${f.p} ${f.n + 5} Euro bezahlt.", "${f.p} hat ${f.n} Euro bezahlt." to ""),
            Triple("C_RESTATE", "${f.p} hat ${f.n} Bücher über ${f.c} gekauft.", "${f.p} hat ${f.n} Bücher über ${f.c} gekauft." to ""),
            Triple("C_REORDER", "Der Termin mit ${f.p2} ist am ${f.d}. ${f.m} um ${f.h} Uhr.", "Am ${f.d}. ${f.m} um ${f.h} Uhr ist der Termin mit ${f.p2}." to ""),
            Triple("C_PARAPHRASE", "${f.p} trainiert jeden ${f.w} in ${f.c}.", "Jeden ${f.w} geht ${f.p} in ${f.c} trainieren." to ""),
            Triple("C_PARTIAL", "${f.p} wohnt in ${f.c} und ${f.p2} arbeitet in ${f.c2}.", "${f.p2} arbeitet in ${f.c2}." to ""),
            Triple("C_NEG_KEPT", "${f.p} hat das ${f.obj} nicht verkauft.", "${f.p} hat das ${f.obj} bisher nicht verkauft." to ""),
            Triple("C_PRONOUN", "${f.p} hat am ${f.w} mit ${f.p2} telefoniert.", "Sie hat am ${f.w} mit ${f.p2} telefoniert." to ""))
    }

    private fun holdout(f: Fill): List<Triple<String, String, Pair<String, String>>> {
        val wordN = WORDS[(f.n % 6) + 3]!!; val wordWrong = WORDS[((f.n + 2) % 6) + 3]!!
        return listOf(
            Triple("E_NUM", "Für das ${f.obj} zahlte ${f.p} ${f.n * 10} Euro.", "${f.p} zahlte ${f.n * 10 + 30} Euro für das ${f.obj}." to ""),
            Triple("E_NUMWORD", "Im Garten von ${f.p} stehen $wordN Bäume.", "Bei ${f.p} stehen $wordWrong Bäume im Garten." to ""),
            Triple("E_DATE", "${f.p2} fliegt am ${f.d}. ${f.m} nach ${f.c}.", "Am ${f.d + 2}. ${f.m} fliegt ${f.p2} nach ${f.c}." to ""),
            Triple("E_WEEKDAY", "Der Chor mit ${f.p} probt immer am ${f.w}.", "Am ${f.w2} probt ${f.p} mit dem Chor." to ""),
            Triple("E_TIME", "Die Werkstatt ruft ${f.p} um ${f.h} Uhr an.", "Um ${f.h + 2} Uhr ruft die Werkstatt bei ${f.p} an." to ""),
            Triple("E_NEG", "${f.p2} hat keine Einladung nach ${f.c} bekommen.", "${f.p2} hat eine Einladung nach ${f.c} bekommen." to ""),
            Triple("E_COMPOUND", "${f.p} fährt nach ${f.c}, während ${f.p2} nach ${f.c2} fährt.", "${f.p} fährt nach ${f.c}, während ${f.p2} nach ${f.c} fährt." to ""),
            Triple("E_OUTDATED", "Früher arbeitete ${f.p2} in ${f.c}, inzwischen arbeitet er in ${f.c2}.", "${f.p2} arbeitet in ${f.c}." to ""),
            Triple("E_UNSUPPORTED", "${f.p} hat das ${f.obj} in ${f.c} abgeholt.", "${f.p} hat das ${f.obj} gestohlen und versteckt." to ""),
            Triple("E_CONFLICT", "Die Feier von ${f.p} ist am ${f.w}. Die Einladung nennt den ${f.w2} als Termin der Feier.", "Die Feier von ${f.p} ist am ${f.w}." to ""),
            Triple("C_RESTATE", "Für das ${f.obj} zahlte ${f.p} ${f.n * 10} Euro.", "Für das ${f.obj} zahlte ${f.p} ${f.n * 10} Euro." to ""),
            Triple("C_REORDER", "${f.p2} fliegt am ${f.d}. ${f.m} nach ${f.c}.", "Nach ${f.c} fliegt ${f.p2} am ${f.d}. ${f.m}." to ""),
            Triple("C_PARAPHRASE", "Der Chor mit ${f.p} probt immer am ${f.w}.", "${f.p} singt jeden ${f.w} bei der Chorprobe." to ""),
            Triple("C_PARTIAL", "${f.p} fährt nach ${f.c}, während ${f.p2} nach ${f.c2} fährt.", "${f.p} fährt nach ${f.c}." to ""),
            Triple("C_NEG_KEPT", "${f.p2} hat keine Einladung nach ${f.c} bekommen.", "${f.p2} wurde nicht nach ${f.c} eingeladen." to ""),
            Triple("C_PRONOUN", "Die Werkstatt ruft ${f.p} um ${f.h} Uhr an.", "Um ${f.h} Uhr ruft die Werkstatt sie an." to ""))
    }

    fun cases(split: String): List<ClaimCase> {
        require(split == "dev" || split == "holdout")
        val fills = if (split == "dev") DEV_FILLS else HOLDOUT_FILLS
        return fills.flatMapIndexed { i, f ->
            (if (split == "dev") dev(f) else holdout(f)).map { (cat, ev, ans) ->
                ClaimCase("$split-$cat-$i", split, cat, ev, ans.first, cat.startsWith("E_"))
            }
        }
    }

    fun seal(split: String): String = MessageDigest.getInstance("SHA-256")
        .digest(cases(split).joinToString("\n") { "${it.id}\t${it.category}\t${it.evidence}\t${it.answer}\t${it.isError}" }
            .toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
