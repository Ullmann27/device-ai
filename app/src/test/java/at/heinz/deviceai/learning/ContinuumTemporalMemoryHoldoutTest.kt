package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/**
 * Honest holdout: phrasings written AFTER the grammar, not used to build it. Measures coverage (answered correctly)
 * versus abstention (null / no evidence, the caller keeps its existing path) versus WRONG answers.
 * Only wrong answers are a failure; low coverage is a reported limitation, not hidden.
 */
class ContinuumTemporalMemoryHoldoutTest {
    private fun t(y: Int, m: Int = 1, d: Int = 1) =
        ZonedDateTime.of(y, m, d, 12, 0, 0, 0, ContinuumTemporalMemory.VIENNA).toInstant().toEpochMilli()
    private val now = t(2026, 11, 3)
    private fun fact(text: String, at: Long) = LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
        LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, text, now = at)

    private data class Case(val facts: List<Pair<String, Long>>, val question: String, val expected: String?)

    private val cases = listOf(
        Case(listOf("Ich lebe in Wels." to t(2020)), "Wo wohne ich?", "Wels"),
        Case(listOf("Seit 2021 wohne ich in Steyr." to t(2022)), "Wo wohne ich?", "Steyr"),
        Case(listOf("Mein Wohnort ist Linz." to t(2022)), "Wo wohne ich?", "Linz"),
        Case(listOf("Ich bin 2019 nach Wien umgezogen." to t(2020)), "Wo wohnte ich 2020?", "Wien"),
        Case(listOf("Ich wohne in Graz." to t(2018)), "In welcher Stadt wohne ich?", "Graz"),
        Case(listOf("Ich wohne in Graz." to t(2018)), "Wo bin ich 2019 gewohnt?", "Graz"),
        Case(listOf("Ich wohne in Graz." to t(2018), "Inzwischen lebe ich in Leoben." to t(2023)), "Wo lebe ich?", "Leoben"),
        Case(listOf("Ich wohne in Graz." to t(2018), "Ich bin umgezogen, jetzt wohne ich in Krems." to t(2023)), "Wo wohne ich?", "Krems"),
        Case(listOf("Ich arbeite als Lehrer." to t(2019)), "Was arbeite ich?", "Lehrer"),
        Case(listOf("Ich arbeite bei der Post." to t(2019), "Ich arbeite jetzt bei der Bahn." to t(2022)), "Wo arbeite ich?", "Bahn"),
        Case(listOf("Ich arbeite bei der Post." to t(2019), "Ich arbeite jetzt bei der Bahn." to t(2022)), "Wo arbeitete ich 2020?", "Post"),
        Case(listOf("Mein Sohn Max ist 7 Jahre alt." to t(2025)), "Wie alt ist Max?", "7"),
        Case(listOf("Meine Katze heißt Mimi." to t(2024)), "Wie heißt meine Katze?", "Mimi"),
        Case(listOf("Meine Schwester Eva wohnt in Villach." to t(2024)), "Wo wohnt Eva?", "Villach"),
        Case(listOf("Eva wohnte von 2015 bis 2018 in Tulln." to t(2024)), "Wo wohnte Eva 2016?", "Tulln"),
        Case(listOf("Ich wohne nicht in Graz, sondern in Melk." to t(2024)), "Wo wohne ich?", "Melk"),
        Case(listOf("Ich wohne in Graz, aber nur unter der Woche." to t(2024)), "Wo wohne ich?", null),
        Case(listOf("Ich wohne in Graz und in Wien." to t(2024)), "Wo wohne ich?", null),
        Case(listOf("Wenn es regnet, wohne ich bei Oma in Lienz." to t(2024)), "Wo wohne ich?", null),
        Case(listOf("Mein Bruder sagt, ich wohne in Kufstein." to t(2024)), "Wo wohne ich?", null)
    )

    @Test fun holdoutNeverAnswersWrongAndReportsCoverage() {
        var correct = 0; var abstained = 0; val wrong = mutableListOf<String>()
        for (c in cases) {
            val m = ContinuumTemporalMemory.fromLedger(c.facts.map { (text, at) -> fact(text, at) })
            val a = m.answer(c.question, now)
            val answered = a != null && a.kind == TemporalAnswerKind.ANSWER
            val body = a?.text?.substringBefore("Beleg").orEmpty()
            when {
                !answered -> if (c.expected == null) correct++ else abstained++
                c.expected != null && body.contains(c.expected) -> correct++
                else -> wrong += "${c.question} <- ${c.facts.map { it.first }} => $body"
            }
        }
        println("M2-METRIC holdout_cases=${cases.size} correct=$correct abstained=$abstained wrong=${wrong.size}")
        wrong.forEach { println("M2-WRONG $it") }
        assertEquals("wrong answers on holdout: $wrong", 0, wrong.size)
    }
}
