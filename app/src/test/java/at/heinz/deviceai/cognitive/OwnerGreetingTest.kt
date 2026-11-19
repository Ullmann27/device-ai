package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The greeting when the owner gets in touch (19.11.2026): how long he was away is computed from two timestamps of
 * the device clock – tested here with fixed times – and every sentence is a fixed wording of the app.
 */
class OwnerGreetingTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(time: String): Long = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
    private fun absence(last: String?, now: String) = OwnerReturn.absence(last?.let(::at), at(now), zone)

    // ── the pause, from the clock ─────────────────────────────────────────────────────────────────────

    @Test fun aFewMinutesAreNoPauseAndUnderAnHourNoDurationIsSaid() {
        assertNull(absence("2026-11-19T09:58:00", "2026-11-19T10:00:00"))
        assertEquals(OwnerAbsence.Brief, absence("2026-11-19T09:50:00", "2026-11-19T10:00:00"))
        assertEquals(OwnerAbsence.Brief, absence("2026-11-19T09:01:00", "2026-11-19T10:00:00"))
        assertNull(OwnerReturn.spoken(OwnerAbsence.Brief))
    }

    @Test fun fromAnHourOnTheDurationIsRoundedTheWayPeopleSayIt() {
        fun said(last: String, now: String) = OwnerReturn.spoken(absence(last, now)!!)
        assertEquals("eine Stunde", said("2026-11-19T09:00:00", "2026-11-19T10:00:00"))
        // The owner's own example: „ich habe mich schon 2 Stunden nicht mehr gerührt“.
        assertEquals("zwei Stunden", said("2026-11-19T08:00:00", "2026-11-19T10:05:00"))
        assertEquals("gut zwei Stunden", said("2026-11-19T08:00:00", "2026-11-19T10:20:00"))
        assertEquals("fast drei Stunden", said("2026-11-19T08:00:00", "2026-11-19T10:50:00"))
        assertEquals("fünf Stunden", said("2026-11-19T05:00:00", "2026-11-19T10:00:00"))
    }

    @Test fun sinceYesterdayAndSeveralDaysAreSaidAsSuch() {
        assertEquals(OwnerAbsence.SinceYesterday, absence("2026-11-18T22:00:00", "2026-11-19T07:30:00"))
        assertEquals("seit gestern", OwnerReturn.spoken(OwnerAbsence.SinceYesterday))
        // Across midnight but only briefly: hours, not „seit gestern“.
        assertEquals(OwnerAbsence.Hours(90), absence("2026-11-18T23:30:00", "2026-11-19T01:00:00"))
        assertEquals(OwnerAbsence.Days(3), absence("2026-11-16T12:00:00", "2026-11-19T10:00:00"))
        assertEquals("seit drei Tagen", OwnerReturn.spoken(OwnerAbsence.Days(3)))
        assertEquals("seit 14 Tagen", OwnerReturn.spoken(OwnerAbsence.Days(14)))
    }

    @Test fun withoutAnEarlierSignOfLifeOrWithAnImplausibleClockNothingIsSaidAboutTime() {
        assertNull(absence(null, "2026-11-19T10:00:00"))
        assertNull(OwnerReturn.absence(0L, at("2026-11-19T10:00:00"), zone))
        // The clock was set back.
        assertNull(absence("2026-11-19T11:00:00", "2026-11-19T10:00:00"))
        // Far too long to be called a pause.
        assertNull(absence("2026-09-01T10:00:00", "2026-11-19T10:00:00"))
    }

    // ── what counts as a greeting ───────────────────────────────────────────────────────────────────────

    @Test fun aPlainGreetingIsRecognisedAndAGreetingWithAnOrderOrAQuestionIsNot() {
        for (greeting in listOf("Hallo", "Hallo Jarvis", "Guten Morgen!", "Servus", "Hi Continuum", "Ich bin wieder da",
            "Hallo, ich bin wieder da", "Grüß Gott", "Da bin ich wieder", "Hallo du"))
            assertTrue(greeting, OwnerGreeting.isGreeting(greeting, "Jarvis"))
        for (other in listOf("Hallo, wie spät ist es?", "Guten Morgen, fass mir die Nachrichten von heute zusammen",
            "Sag Hallo zu Anna", "Gute Nacht", "Danke", "Hallo, wie geht es dir?", "Hey, öffne WhatsApp", "Morgen um acht", ""))
            assertFalse(other, OwnerGreeting.isGreeting(other, "Jarvis"))
    }

    // ── the wording ───────────────────────────────────────────────────────────────────────────────────────

    @Test fun theFirstGreetingAfterTwoHoursIsTheOwnersOwnExample() {
        val said = OwnerGreeting.reply("Heinz", hour = 14, absence = OwnerAbsence.Hours(125), variation = 0)
        assertEquals("Hallo Heinz, wie geht's dir? Du hast dich ja zwei Stunden nicht gerührt.", said.text)
        assertEquals(0, said.index)
    }

    @Test fun everyBucketHasItsOwnWordingWithAndWithoutAName() {
        val hours = OwnerAbsence.Hours(140)
        assertEquals("Na Heinz, da bist du ja wieder. Gut zwei Stunden habe ich nichts von dir gehört. Was liegt an?",
            OwnerGreeting.reply("Heinz", 14, hours, 1).text)
        assertEquals("Guten Morgen, Heinz! Seit gestern war Funkstille. Alles gut bei dir?",
            OwnerGreeting.reply("Heinz", 7, OwnerAbsence.SinceYesterday, 2).text)
        assertEquals("Hallo, wie geht's dir? Du hast dich ja seit drei Tagen nicht gerührt.",
            OwnerGreeting.reply(null, 14, OwnerAbsence.Days(3), 0).text)
        assertEquals("Da bist du ja wieder, Heinz. Was gibt's?", OwnerGreeting.reply("Heinz", 14, OwnerAbsence.Brief, 0).text)
        assertEquals("Da bist du ja wieder. Was gibt's?", OwnerGreeting.reply(null, 14, OwnerAbsence.Brief, 0).text)
        // Nothing known about a pause: a greeting by name, and not a word about time.
        val plain = (0..4).map { OwnerGreeting.reply("Heinz", 20, null, it).text }
        assertEquals("Hallo Heinz! Schön, dass du da bist. Was liegt an?", plain[0])
        assertEquals("Guten Abend, Heinz! Womit fangen wir an?", plain[2])
        assertEquals(5, plain.toSet().size)
        plain.forEach { text ->
            assertTrue(text, text.contains("Heinz"))
            assertFalse(text, Regex("Stunde|gestern|Tagen|Minuten|gerührt|Funkstille").containsMatchIn(text))
        }
        (0..4).forEach { assertFalse(OwnerGreeting.reply(null, 12, null, it).text.contains("Heinz")) }
    }

    @Test fun theWordingVariesAndNeverRepeatsDirectly() {
        var previous = -1
        val seen = mutableListOf<String>()
        repeat(12) { count ->
            val said = OwnerGreeting.reply("Heinz", 14, OwnerAbsence.Hours(120), count, previous)
            assertNotEquals("same wording twice in a row", previous, said.index)
            previous = said.index
            seen += said.text
        }
        // The pool for a pause has grown from three to six wordings (owner wish 19.11.2026: never the same greeting twice).
        assertEquals(6, seen.toSet().size)
        // The same counter and the same previous wording always give the same sentence: nothing is random.
        assertEquals(OwnerGreeting.reply("Heinz", 14, OwnerAbsence.Hours(120), 5, 1).text,
            OwnerGreeting.reply("Heinz", 14, OwnerAbsence.Hours(120), 5, 1).text)
        // A counter that would repeat the last wording moves on by one.
        assertEquals(1, OwnerGreeting.reply("Heinz", 14, OwnerAbsence.Hours(120), 0, previous = 0).index)
    }

    @Test fun theTimeOfDayComesFromTheHour() {
        assertEquals("Guten Morgen", OwnerGreeting.timeOfDay(5))
        assertEquals("Guten Morgen", OwnerGreeting.timeOfDay(10))
        assertEquals("Hallo", OwnerGreeting.timeOfDay(11))
        assertEquals("Guten Abend", OwnerGreeting.timeOfDay(18))
        assertEquals("Hallo", OwnerGreeting.timeOfDay(23))
        assertEquals("Hallo", OwnerGreeting.timeOfDay(3))
    }

    // ── the clause in front of an order ─────────────────────────────────────────────────────────────────

    @Test fun anOrderAfterAPauseGetsOneShortClauseWithoutAQuestion() {
        assertEquals("Hallo Heinz – zwei Stunden nichts von dir gehört.", OwnerGreeting.returnClause("Heinz", OwnerAbsence.Hours(120)))
        assertEquals("Hallo – seit gestern nichts von dir gehört.", OwnerGreeting.returnClause(null, OwnerAbsence.SinceYesterday))
        assertEquals("Hallo Heinz, da bist du ja wieder.", OwnerGreeting.returnClause("Heinz", OwnerAbsence.Brief))
        assertEquals("Hallo Heinz!", OwnerGreeting.returnClause("Heinz", null))
        for (absence in listOf(OwnerAbsence.Hours(61), OwnerAbsence.Brief, OwnerAbsence.Days(2), null)) {
            val clause = OwnerGreeting.returnClause("Heinz", absence)
            assertFalse(clause, clause.contains("?"))
            assertTrue(clause, clause.length <= 60)
        }
    }

    @Test fun aQuestionAnApprovalOrAFailureGetsNoClause() {
        assertTrue(OwnerGreeting.clauseFits("Die Datei liegt jetzt in deinem Download-Ordner: Bericht.pdf."))
        assertTrue(OwnerGreeting.clauseFits("Ich mache das in zwei Schritten: 1. Als PDF speichern. 2. Die gespeicherte Datei öffnen."))
        // An approval or a question back stays exactly as it is.
        assertFalse(OwnerGreeting.clauseFits("Ich verstehe das so: per E-Mail an Anna schicken. Soll ich das so machen?"))
        assertFalse(OwnerGreeting.clauseFits("Welches PDF meinst du?"))
        // A failure too.
        for (failure in listOf("Das Speichern ist fehlgeschlagen: kein Platz.", "Ich habe kein PDF mit diesem Namen gefunden.",
            "Ein PDF namens Argon gibt es nicht, nur ähnliche Namen.", "Die Nachricht wurde nicht gesendet.",
            "Mein Cloud-Gehirn hat diese Anfrage abgelehnt.", "Das kann ich leider nicht öffnen.", "Fehler beim Lesen der Datei.",
            "Kein Zugriff auf den Download-Ordner.", ""))
            assertFalse(failure, OwnerGreeting.clauseFits(failure))
    }

    // ── the switch of its own ───────────────────────────────────────────────────────────────────────────

    @Test fun theReturnGreetingHasItsOwnSentenceToSwitchItOffAndOn() {
        assertEquals(false, OwnerGreetingSwitchCommand.parse("Begrüß mich nicht mehr", "Jarvis"))
        assertEquals(false, OwnerGreetingSwitchCommand.parse("Begrüßung aus", "Jarvis"))
        assertEquals(false, OwnerGreetingSwitchCommand.parse("Jarvis, begrüß mich nicht mehr", "Jarvis"))
        assertEquals(true, OwnerGreetingSwitchCommand.parse("Begrüß mich wieder", "Jarvis"))
        assertEquals(true, OwnerGreetingSwitchCommand.parse("Schalt die Begrüßung wieder ein", "Jarvis"))
        for (other in listOf("Begrüß Anna von mir", "Wie begrüßt man einen Bürgermeister?", "Begrüß mich nicht so laut",
            "Schalt das Licht aus", "Mach die Begrüßung kürzer", "Hallo", ""))
            assertNull(other, OwnerGreetingSwitchCommand.parse(other, "Jarvis"))
    }
}
