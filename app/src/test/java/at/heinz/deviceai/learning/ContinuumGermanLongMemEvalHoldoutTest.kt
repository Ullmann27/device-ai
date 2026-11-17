package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * M2.2 — German LongMemEval-style BLIND holdout (written 2026-11-03 before any tuning against it; frozen).
 * Five abilities like LongMemEval: temporal reasoning, knowledge updates/corrections, hypotheticals (must not
 * become facts), multi-session synthesis, abstention. Natural phrasings were chosen WITHOUT consulting the grammar.
 * Do NOT change cases or the implementation to raise this score; add new failures to a separate dev set instead.
 * Only safety floors are asserted; the score is reported as M2-METRIC.
 */
class ContinuumGermanLongMemEvalHoldoutTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun t(y: Int, m: Int = 6, d: Int = 1) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = t(2026, 11, 3)

    private enum class Expect { VALUE, ABSTAIN, CLARIFY }
    private data class Case(val id: String, val ability: String, val sessions: List<Pair<Long, String>>,
                            val question: String, val expect: Expect, val must: List<String> = emptyList(),
                            val mustNot: List<String> = emptyList())

    private val cases = listOf(
        // ---- temporal reasoning
        Case("T1", "temporal", listOf(t(2015) to "Ich wohne in Feldkirch.", t(2019, 3) to "Seit März 2019 wohne ich in Wels."),
            "Wo habe ich 2017 gewohnt?", Expect.VALUE, listOf("Feldkirch"), listOf("Wels")),
        Case("T2", "temporal", listOf(t(2015) to "Ich wohne in Feldkirch.", t(2019, 3) to "Seit März 2019 wohne ich in Wels."),
            "Wo wohne ich?", Expect.VALUE, listOf("Wels"), listOf("Feldkirch")),
        Case("T3", "temporal", listOf(t(2022) to "Von 2010 bis 2014 wohnte ich in Leoben."),
            "Wo habe ich 2012 gewohnt?", Expect.VALUE, listOf("Leoben")),
        Case("T4", "temporal", listOf(t(2022) to "Von 2010 bis 2014 wohnte ich in Leoben."),
            "Wo habe ich 2016 gewohnt?", Expect.ABSTAIN, mustNot = listOf("Leoben")),
        Case("T5", "temporal", listOf(t(2018) to "Ich arbeite bei der Voestalpine.", t(2023) to "Ich arbeite jetzt bei Fronius."),
            "Wo habe ich 2020 gearbeitet?", Expect.VALUE, listOf("Voestalpine"), listOf("Fronius")),
        Case("T6", "temporal", listOf(t(2018) to "Ich wohne in Dornbirn.", t(2021) to "Ich bin nach Hallein gezogen.", t(2024) to "Ich bin nach Krems gezogen."),
            "Wo habe ich ursprünglich gewohnt?", Expect.VALUE, listOf("Dornbirn")),
        Case("T7", "temporal", listOf(t(2018) to "Ich wohne in Dornbirn.", t(2021) to "Ich bin nach Hallein gezogen.", t(2024) to "Ich bin nach Krems gezogen."),
            "Wo wohnte ich im Jahr 2022?", Expect.VALUE, listOf("Hallein")),
        Case("T8", "temporal", listOf(t(2020) to "Seit 2016 lebe ich in Mödling."),
            "Seit wann wohne ich in Mödling?", Expect.VALUE, listOf("2016")),
        // ---- knowledge updates / corrections
        Case("C1", "correction", listOf(t(2024) to "Ich wohne in Steyr.", t(2024, 7) to "Korrektur: Ich wohne in Enns."),
            "Wo wohne ich?", Expect.VALUE, listOf("Enns"), listOf("Steyr")),
        Case("C2", "correction", listOf(t(2024) to "Ich bin 41.", t(2024, 2) to "Korrektur: Ich bin 42."),
            "Wie alt bin ich?", Expect.VALUE, listOf("42"), listOf("41")),
        Case("C3", "correction", listOf(t(2023) to "Mein Hund heißt Bello.", t(2023, 5) to "Korrektur: Mein Hund heißt Balu."),
            "Wie heißt mein Hund?", Expect.VALUE, listOf("Balu"), listOf("Bello")),
        Case("C4", "correction", listOf(t(2024) to "Ich arbeite bei Spar.", t(2024, 9) to "Ich arbeite nicht bei Spar, sondern bei Billa."),
            "Wo arbeite ich?", Expect.VALUE, listOf("Billa"), listOf("Spar")),
        Case("C5", "correction", listOf(t(2024) to "Ich wohne in Tulln.", t(2024, 4) to "Ich wohne in Baden."),
            "Wo wohne ich?", Expect.CLARIFY, mustNot = listOf()),
        Case("C6", "correction", listOf(t(2020) to "Ich wohne in Zwettl.", t(2025) to "Inzwischen wohne ich in Horn."),
            "Wo wohne ich jetzt?", Expect.VALUE, listOf("Horn"), listOf("Zwettl")),
        Case("C7", "correction", listOf(t(2020) to "Meine Katze heißt Mimi.", t(2025) to "Korrektur: Meine Katze heißt Momo."),
            "Wie hieß meine Katze ursprünglich?", Expect.ABSTAIN, mustNot = listOf("Momo")),
        Case("C8", "correction", listOf(t(2023) to "Ich arbeite als Lehrer.", t(2025) to "Seit 2025 arbeite ich als Schulleiter."),
            "Was arbeite ich?", Expect.VALUE, listOf("Schulleiter")),
        // ---- hypotheticals (must never become facts)
        Case("H1", "hypothetical", listOf(t(2025) to "Wenn ich in Lissabon wohnen würde, wäre ich glücklicher."),
            "Wo wohne ich?", Expect.ABSTAIN, mustNot = listOf("Lissabon")),
        Case("H2", "hypothetical", listOf(t(2024) to "Ich wohne in Wien.", t(2025) to "Vielleicht ziehe ich nach Barcelona."),
            "Wo wohne ich?", Expect.VALUE, listOf("Wien"), listOf("Barcelona")),
        Case("H3", "hypothetical", listOf(t(2025) to "Ich überlege, bei Google zu arbeiten."),
            "Wo arbeite ich?", Expect.ABSTAIN, mustNot = listOf("Google")),
        Case("H4", "hypothetical", listOf(t(2025) to "Angenommen, ich wäre 30."),
            "Wie alt bin ich?", Expect.ABSTAIN, mustNot = listOf("30")),
        Case("H5", "hypothetical", listOf(t(2025) to "Meine Schwester sagt, ich wohne bald in Graz."),
            "Wo wohne ich?", Expect.ABSTAIN, mustNot = listOf("Graz")),
        Case("H6", "hypothetical", listOf(t(2024) to "Ich arbeite bei Kapsch.", t(2025) to "Nächstes Jahr werde ich bei Frequentis arbeiten."),
            "Wo arbeite ich?", Expect.VALUE, listOf("Kapsch"), listOf("Frequentis")),
        Case("H7", "hypothetical", listOf(t(2025) to "Wohne ich eigentlich in Linz?"),
            "Wo wohne ich?", Expect.ABSTAIN, mustNot = listOf("Linz")),
        Case("H8", "hypothetical", listOf(t(2025) to "Ich träume davon, in Kanada zu leben."),
            "Wo lebe ich?", Expect.ABSTAIN, mustNot = listOf("Kanada")),
        // ---- multi-session synthesis
        Case("M1", "multisession", listOf(t(2019) to "Meine Tochter Klara ist 7.", t(2021) to "Klara wohnt in Wien."),
            "Wie alt ist Klara?", Expect.VALUE, listOf("7")),
        Case("M2", "multisession", listOf(t(2019) to "Meine Tochter Klara ist 7.", t(2021) to "Klara wohnt in Wien."),
            "Wo wohnt Klara?", Expect.VALUE, listOf("Wien")),
        Case("M3", "multisession", listOf(t(2018) to "Mein Bruder heißt Stefan.", t(2022) to "Stefan arbeitet bei der ÖBB."),
            "Wo arbeitet Stefan?", Expect.VALUE, listOf("ÖBB")),
        Case("M4", "multisession", listOf(t(2017) to "Ich wohne in Ried.", t(2020) to "Ich arbeite bei KTM.", t(2023) to "Ich bin nach Braunau gezogen."),
            "Wo arbeite ich?", Expect.VALUE, listOf("KTM")),
        Case("M5", "multisession", listOf(t(2017) to "Ich wohne in Ried.", t(2020) to "Ich arbeite bei KTM.", t(2023) to "Ich bin nach Braunau gezogen."),
            "Wo habe ich 2021 gewohnt?", Expect.VALUE, listOf("Ried"), listOf("Braunau")),
        Case("M6", "multisession", listOf(t(2016) to "Paul wohnt in Bludenz.", t(2024) to "Paul ist nach Bregenz gezogen."),
            "Wo wohnt Paul?", Expect.VALUE, listOf("Bregenz"), listOf("Bludenz")),
        Case("M7", "multisession", listOf(t(2016) to "Paul wohnt in Bludenz.", t(2024) to "Paul ist nach Bregenz gezogen."),
            "Wo hat Paul 2020 gewohnt?", Expect.VALUE, listOf("Bludenz")),
        Case("M8", "multisession", listOf(t(2020) to "Mein Name ist Heinz.", t(2022) to "Ich wohne in Kufstein."),
            "Wie heiße ich?", Expect.VALUE, listOf("Heinz")),
        // ---- abstention
        Case("A1", "abstention", listOf(t(2024) to "Ich wohne in Villach."),
            "Wo arbeite ich?", Expect.ABSTAIN),
        Case("A2", "abstention", listOf(t(2024) to "Ich wohne in Villach."),
            "Wie alt bin ich?", Expect.ABSTAIN),
        Case("A3", "abstention", listOf(t(2024) to "Ich wohne in Villach."),
            "Wo habe ich 2015 gewohnt?", Expect.ABSTAIN, mustNot = listOf("Villach")),
        Case("A4", "abstention", listOf(t(2024) to "Meine Tochter heißt Lea."),
            "Wo wohnt Lea?", Expect.ABSTAIN),
        Case("A5", "abstention", emptyList(),
            "Wo wohne ich?", Expect.ABSTAIN),
        Case("A6", "abstention", listOf(t(2024) to "Ich wohne manchmal in Wien und manchmal in Graz."),
            "Wo wohne ich?", Expect.ABSTAIN, mustNot = listOf()),
        Case("A7", "abstention", listOf(t(2024) to "Mein Hund heißt Rex."),
            "Wie heißt meine Katze?", Expect.ABSTAIN, mustNot = listOf("Rex")),
        Case("A8", "abstention", listOf(t(2024) to "Ich arbeite bei Red Bull.", t(2025) to "Ich habe gekündigt."),
            "Wo arbeite ich?", Expect.ABSTAIN, mustNot = listOf()),
    )

    @Test fun blindGermanHoldoutIsScoredHonestly() {
        var correct = 0; var abstained = 0; var wrong = 0; var unsafe = 0
        val perAbility = linkedMapOf<String, IntArray>()
        for (c in cases) {
            val dir = Files.createTempDirectory("continuum-m2-lme").toFile()
            try {
                var clock = 0L
                val core = ContinuumLearningCore(dir) { clock }
                for ((at, text) in c.sessions) { clock = at; core.confirmOwnerFact(text) }
                clock = now
                val a = core.temporalMemoryView().answer(c.question, now)
                val kind = a?.kind; val text = a?.text.orEmpty()
                val leaked = c.mustNot.any { text.contains(it) } && kind == TemporalAnswerKind.ANSWER
                val verdict = when (c.expect) {
                    Expect.VALUE -> when {
                        kind == TemporalAnswerKind.ANSWER && c.must.all { text.contains(it) } && !leaked -> "CORRECT"
                        kind == TemporalAnswerKind.ANSWER -> "WRONG"
                        else -> "ABSTAINED"
                    }
                    Expect.ABSTAIN -> if (kind == TemporalAnswerKind.ANSWER) "WRONG" else "CORRECT"
                    Expect.CLARIFY -> when (kind) {
                        TemporalAnswerKind.CLARIFY -> "CORRECT"
                        TemporalAnswerKind.ANSWER -> "WRONG"
                        else -> "ABSTAINED"
                    }
                }
                when (verdict) { "CORRECT" -> correct++; "WRONG" -> wrong++; else -> abstained++ }
                if (leaked) unsafe++
                val row = perAbility.getOrPut(c.ability) { IntArray(3) }
                row[when (verdict) { "CORRECT" -> 0; "WRONG" -> 2; else -> 1 }]++
                println("M2-LME ${c.id} ${c.ability} $verdict kind=$kind q=\"${c.question}\" a=\"${text.replace('\n', ' ').take(140)}\"")
            } finally { dir.deleteRecursively() }
        }
        perAbility.forEach { (k, v) -> println("M2-METRIC lme_holdout ability=$k n=${v.sum()} correct=${v[0]} abstained=${v[1]} wrong=${v[2]}") }
        println("M2-METRIC lme_holdout n=${cases.size} correct=$correct abstained=$abstained wrong=$wrong forbidden_value_answered=$unsafe")
        // Safety floor only: a forbidden (hypothetical/stale/retracted) value must never be answered as fact.
        assertEquals("forbidden values answered as fact", 0, unsafe)
    }
}
