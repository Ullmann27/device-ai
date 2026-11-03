package at.heinz.deviceai.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime
import java.util.UUID

/**
 * M2 measurement, not a demo: a synthetic, German, private-free ten-year owner history (2016–2026)
 * written through the REAL ContinuumLearningCore write path, then queried through
 * (a) the existing memory answer path (ContinuumLearningCore.replyFromMemory, SWB1/EWM2 ranking) and
 * (b) the new temporal specialist over the same canonical ledger.
 * Ground truth is computed from the fixture timeline, never from either implementation.
 * Metrics are printed as `M2-METRIC key=value` lines (see the JUnit XML system-out).
 */
class ContinuumTemporalMemoryBenchmarkTest {
    private fun t(y: Int, m: Int = 1, d: Int = 1) =
        ZonedDateTime.of(y, m, d, 12, 0, 0, 0, ContinuumTemporalMemory.VIENNA).toInstant().toEpochMilli()
    private val now = t(2026, 11, 3)

    private data class Stay(val city: String, val from: Long)
    private data class Statement(val at: Long, val text: String)
    private data class Question(val text: String, val expected: Set<String>, val category: String)

    private val cities = listOf("Wien", "Graz", "Linz", "Salzburg", "Innsbruck", "Bregenz", "Klagenfurt", "Villach",
        "Wels", "Steyr", "Dornbirn", "Krems", "Leoben", "Baden", "Amstetten", "Feldkirch", "Bludenz", "Hallein",
        "Kufstein", "Lienz", "Melk", "Tulln", "Gmunden", "Zwettl")
    private val traps = listOf("Rom", "Paris", "Madrid", "Berlin", "Prag", "Oslo", "Lissabon")
    private val people = listOf("Anna", "Lukas", "Sophie", "Felix", "Marie", "Jonas", "Lena", "Paul", "Laura",
        "David", "Julia", "Elias", "Hannah", "Simon", "Sarah", "Tobias", "Emma", "Florian", "Nina", "Moritz")

    private fun fixture(): Triple<List<Statement>, Map<String, List<Stay>>, List<String>> {
        val statements = mutableListOf<Statement>()
        val stays = linkedMapOf<String, List<Stay>>()
        val owner = listOf(Stay("Wien", t(2016, 2, 1)), Stay("Graz", t(2018, 5, 1)), Stay("Linz", t(2020, 9, 1)),
            Stay("Salzburg", t(2022, 3, 1)), Stay("Innsbruck", t(2024, 7, 1)), Stay("Bregenz", t(2025, 10, 1)))
        stays["ich"] = owner
        statements += Statement(owner[0].from, "Ich wohne in Wien.")
        statements += Statement(owner[1].from, "Ich bin nach Graz gezogen.")
        statements += Statement(owner[2].from, "Ich wohne jetzt in Linz.")
        statements += Statement(owner[3].from, "Ich bin nach Salzburg gezogen.")
        statements += Statement(t(2024, 7, 20), "Ich wohne seit Juli 2024 in Innsbruck.")
        statements += Statement(owner[5].from, "Ich bin nach Bregenz gezogen.")
        val hypotheticals = mutableListOf(
            Statement(t(2017, 4, 1), "Vielleicht ziehe ich nach Rom."),
            Statement(t(2019, 8, 1), "Wenn ich in Paris wohnen würde, lerne ich Französisch."),
            Statement(t(2021, 1, 1), "Anna sagt, ich wohne in Madrid."),
            Statement(t(2023, 5, 1), "Ich überlege, nach Berlin zu ziehen."),
            Statement(t(2026, 6, 1), "Wohne ich in Prag?"))
        people.forEachIndexed { i, name ->
            val count = 1 + i % 3
            val list = (0 until count).map { k ->
                Stay(cities[(i * 3 + k * 5 + 1) % cities.size], t(2016 + (i + k * 3) % 10, 1 + (i * 5 + k) % 12, 3))
            }.sortedBy { it.from }.distinctBy { it.city }
            stays[name.lowercase()] = list
            list.forEachIndexed { k, s ->
                statements += Statement(s.from, when {
                    k == 0 -> "$name wohnt in ${s.city}."
                    k % 2 == 1 -> "$name ist nach ${s.city} gezogen."
                    else -> "$name wohnt jetzt in ${s.city}."
                })
            }
            hypotheticals += Statement(list.first().from + 86_400_000L,
                if (i % 2 == 0) "Vielleicht zieht $name nach ${traps[i % traps.size]}."
                else "Falls $name nach ${traps[i % traps.size]} zieht, besuche ich sie.")
        }
        val fillers = (1..150).map { k ->
            Statement(t(2016 + k % 10, 1 + k % 12, 1 + k % 27) + k, "Mein Notizbuch $k hat die Farbe ${listOf("rot", "blau", "grün")[k % 3]}.")
        }
        return Triple((statements + hypotheticals + fillers).sortedBy { it.at }, stays, hypotheticals.map { it.text })
    }

    private fun expectedDuring(list: List<Stay>, from: Long, to: Long): Set<String> =
        list.indices.filter { i ->
            val start = list[i].from
            val end = list.getOrNull(i + 1)?.from ?: Long.MAX_VALUE
            start < to && end > from
        }.map { list[it].city }.toSet()

    private fun questions(stays: Map<String, List<Stay>>): List<Question> {
        val q = mutableListOf<Question>()
        for ((who, list) in stays) {
            val owner = who == "ich"
            val name = who.replaceFirstChar { it.uppercase() }
            q += Question(if (owner) "Wo wohne ich?" else "Wo wohnt $name?", setOf(list.last().city), "current")
            q += Question(if (owner) "Wo habe ich ursprünglich gewohnt?" else "Wo hat $name ursprünglich gewohnt?",
                setOf(list.first().city), "original")
            for (year in 2016..2026)
                q += Question(if (owner) "Wo wohnte ich $year?" else "Wo wohnte $name $year?",
                    expectedDuring(list, t(year, 1, 1) - 43_200_000L, t(year + 1, 1, 1) - 43_200_000L), "year")
        }
        return q
    }

    private fun correct(answer: String, expected: Set<String>): Boolean {
        val body = answer.substringBefore("Beleg")
        val mentioned = (cities + traps).filter { Regex("\\b" + it + "\\b").containsMatchIn(body) }.toSet()
        return mentioned == expected
    }

    private fun pct(sorted: List<Long>, p: Int) = sorted[((sorted.size - 1) * p) / 100]

    @Test fun tenYearOwnerHistoryIsAnsweredCorrectlyAndHypotheticalsNeverLeak() {
        val dir = Files.createTempDirectory("continuum-m2-benchmark").toFile()
        try {
            var clock = 0L
            val core = ContinuumLearningCore(dir) { clock }
            val (statements, stays, hypotheticals) = fixture()
            val writeNanos = mutableListOf<Long>()
            for (s in statements) {
                clock = s.at
                val started = System.nanoTime()
                core.confirmOwnerFact(s.text)
                writeNanos += System.nanoTime() - started
            }
            clock = now
            val active = core.activeOwnerFacts().map { it.content }.toSet()
            val leaked = hypotheticals.count { it in active }
            val ledgerBytes = File(dir, "learning-core-v2/experiences-v2.bin").length()
            ContinuumBrainSettings(dir).set(ContinuumBrainMode.ON)
            val settings = ContinuumBrainSettings(dir)
            val baseline0 = ContinuumChatOutcome("—", "baseline", "trace", false, ContinuumBaselineSource.NONE)

            val qs = questions(stays)
            var specialistCorrect = 0; var baselineCorrect = 0; var trapHits = 0; var baselineTrapHits = 0
            val perCategory = linkedMapOf<String, IntArray>()
            val specNanos = mutableListOf<Long>(); val baseNanos = mutableListOf<Long>()
            for (question in qs) {
                val s0 = System.nanoTime()
                val r = ContinuumTemporalMemorySpecialist.decide(dir, core, question.text, baseline0,
                    settings = settings, nowMillis = now)
                specNanos += System.nanoTime() - s0
                val b0 = System.nanoTime()
                val b = core.replyFromMemory(question.text, UUID.randomUUID().toString())
                baseNanos += System.nanoTime() - b0
                val ok = correct(r.outcome.answer, question.expected)
                val bok = correct(b.answer, question.expected)
                if (ok) specialistCorrect++
                if (bok) baselineCorrect++
                if (traps.any { r.outcome.answer.substringBefore("Beleg").contains(it) }) trapHits++
                if (traps.any { b.answer.contains(it) }) baselineTrapHits++
                perCategory.getOrPut(question.category) { IntArray(3) }.let { it[0]++; if (ok) it[1]++; if (bok) it[2]++ }
            }
            specNanos.sort(); baseNanos.sort(); writeNanos.sort()
            fun ms(n: Long) = "%.2f".format(java.util.Locale.ROOT, n / 1e6)
            println("M2-METRIC statements_written=${statements.size}")
            println("M2-METRIC ledger_records=${core.status().experiences.total} ledger_bytes=$ledgerBytes")
            println("M2-METRIC hypothetical_statements=${hypotheticals.size} leaked_into_confirmed=$leaked")
            println("M2-METRIC questions=${qs.size} specialist_correct=$specialistCorrect baseline_correct=$baselineCorrect")
            perCategory.forEach { (c, v) -> println("M2-METRIC category=$c n=${v[0]} specialist=${v[1]} baseline=${v[2]}") }
            println("M2-METRIC trap_city_in_answer specialist=$trapHits baseline=$baselineTrapHits")
            println("M2-METRIC latency_ms specialist_p50=${ms(pct(specNanos, 50))} specialist_p95=${ms(pct(specNanos, 95))} " +
                "baseline_p50=${ms(pct(baseNanos, 50))} baseline_p95=${ms(pct(baseNanos, 95))}")
            println("M2-METRIC write_ms p50=${ms(pct(writeNanos, 50))} p95=${ms(pct(writeNanos, 95))}")

            assertEquals("hypothetical statements leaked into confirmed owner facts", 0, leaked)
            assertEquals("a trap city appeared as an answer", 0, trapHits)
            assertTrue("specialist accuracy $specialistCorrect/${qs.size}", specialistCorrect * 100 >= qs.size * 95)
        } finally { dir.deleteRecursively() }
    }

    @Test fun ledgerCapacityIsAMeasuredHardLimit() {
        val dir = Files.createTempDirectory("continuum-m2-capacity").toFile()
        try {
            var clock = 1L
            val core = ContinuumLearningCore(dir) { clock++ }
            var stored = 0
            val failure = runCatching {
                for (k in 1..300) { core.confirmOwnerFact("Mein Notizbuch $k ist grün."); stored++ }
            }.exceptionOrNull()
            println("M2-METRIC owner_fact_capacity=$stored failure=${failure?.javaClass?.simpleName}")
            assertEquals(FileLearningExperienceStore.MAX_RECORDS, stored)
        } finally { dir.deleteRecursively() }
    }
}
