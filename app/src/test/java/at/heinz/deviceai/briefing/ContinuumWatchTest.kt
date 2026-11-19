package at.heinz.deviceai.briefing

import at.heinz.deviceai.cognitive.InternetAccessMode
import at.heinz.deviceai.sending.OwnerContactPhone
import at.heinz.deviceai.sending.OwnerSendRecipientResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Truthful warnings: one calm sentence per episode of a real condition, quiet while something is open. */
class ContinuumWatchTest {
    private val minute = 60_000L
    private val fine = ContinuumBriefingHarness.fine()
    private fun watch() = ContinuumWatch()
    private fun ContinuumWatch.look(minutes: Long, facts: ContinuumSystemFacts, quiet: Boolean = false, loadReduced: Boolean = false,
                                  webJob: Boolean = false, name: String? = "Heinz") =
        observe(facts, name, minutes * minute, quiet, loadReduced, webJob)

    @Before fun fresh() = ContinuumBriefingBoard.clear()

    @Test fun nothingIsSaidWhileEverythingIsFine() {
        val watch = watch()
        repeat(120) { assertNull(watch.look(it.toLong(), fine)) }
        // A light warming is not a warning.
        assertNull(watch.look(121, fine.copy(thermalStatus = 1)))
    }

    @Test fun heatIsSaidOncePerEpisodeAndTheGearIsOnlyClaimedWhenLoadIsReallyReduced() {
        val watch = watch()
        val warm = fine.copy(thermalStatus = 2)
        assertEquals("Heinz, das Gerät wird warm – ich schalte einen Gang zurück.", watch.look(0, warm, loadReduced = true)!!.text)
        repeat(20) { assertNull("once per episode", watch.look(1L + it, warm, loadReduced = true)) }
        // Cool again, then warm again soon after: not repeated within half an hour …
        assertNull(watch.look(22, fine))
        assertNull(watch.look(23, warm))
        // … but said again when it is still warm after that time.
        assertEquals("Heinz, das Gerät wird warm.", watch.look(31, warm)!!.text)
        // Without a name, and first seen at a severe status.
        assertEquals("Das Gerät wird warm.", watch().look(0, warm, name = null)!!.text)
        val hot = watch().look(0, fine.copy(thermalStatus = 3), loadReduced = true)!!
        assertEquals(ContinuumWatch.Condition.HEAT, hot.condition)
        assertEquals("Heinz, das Gerät ist stark erwärmt. Gönn ihm bitte eine Pause.", hot.text)
        assertNull(hot.offer)
        // Rising from warm to severe inside one episode is not a second warning.
        val rising = watch()
        assertTrue(rising.look(0, warm) != null)
        assertNull(rising.look(1, fine.copy(thermalStatus = 3)))
    }

    @Test fun batteryStorageAndBridge() {
        assertEquals("Heinz, der Akku ist bei 18 Prozent und lädt nicht.", watch().look(0, fine.copy(batteryPercent = 18))!!.text)
        assertEquals("Heinz, der Akku ist fast leer – nur noch 4 Prozent.", watch().look(0, fine.copy(batteryPercent = 4))!!.text)
        assertNull("charging is no reason to warn", watch().look(0, fine.copy(batteryPercent = 4, charging = true)))
        assertNull("an unreadable battery is not a low battery", watch().look(0, fine.copy(batteryPercent = null)))
        // The storage is announced when it runs low while the app is in use …
        val filling = watch()
        assertNull(filling.look(0, fine))
        val storage = filling.look(1, fine.copy(storageFreeBytes = 7_500_000_000L, storageTotalBytes = 100_000_000_000L))!!
        assertEquals("Heinz, der Speicher wird knapp: noch 7,5 Gigabyte frei.", storage.text)
        assertEquals("the app can look into a full storage", ContinuumBriefingBoard.OfferKind.DIAGNOSIS, storage.offer)
        val full = watch()
        assertNull(full.look(0, fine))
        assertEquals("Heinz, der Speicher ist fast voll: noch 0,5 Gigabyte frei.", full.look(1, fine.copy(storageFreeBytes = 500_000_000L))!!.text)
        // … a storage that was already full when the app was opened is the system check's matter, not a warning at every start.
        val alreadyFull = watch()
        repeat(40) { assertNull(alreadyFull.look(it.toLong(), fine.copy(storageFreeBytes = 500_000_000L))) }
        assertNull("an unreadable storage is not a full storage", watch().look(0, fine.copy(storageFreeBytes = null)))
        // The bridge is only „lost“ when it was there before in this run.
        val never = watch()
        assertNull(never.look(0, fine.copy(shizukuReady = false)))
        val lost = watch()
        assertNull(lost.look(0, fine))
        assertEquals("Heinz, die Verbindung zu Shizuku ist abgebrochen.", lost.look(1, fine.copy(shizukuReady = false))!!.text)
        assertNull(lost.look(2, fine.copy(shizukuReady = false)))
        assertNull(lost.look(3, fine))
    }

    @Test fun aLostConnectionIsOnlyAWarningWhenAWebJobWasRunning() {
        val idle = watch()
        assertNull(idle.look(0, fine))
        assertNull("offline without a web job is no warning", idle.look(1, fine.copy(networkOnline = false)))
        val working = watch()
        assertNull(working.look(0, fine, webJob = true, quiet = true))
        // Lost while the job runs: nothing is said into the job …
        assertNull(working.look(1, fine.copy(networkOnline = false), webJob = true, quiet = true))
        // … and afterwards only if the connection is still gone.
        assertEquals("Heinz, während des Abrufs ist die Internetverbindung abgebrochen. Sie ist noch nicht wieder da.",
            working.look(2, fine.copy(networkOnline = false))!!.text)
        val recovered = watch()
        assertNull(recovered.look(0, fine, webJob = true, quiet = true))
        assertNull(recovered.look(1, fine.copy(networkOnline = false), webJob = true, quiet = true))
        assertNull("back before anything could be said: never said", recovered.look(2, fine))
        assertNull(recovered.look(3, fine.copy(networkOnline = false)))
    }

    @Test fun nothingIsSaidWhileSomethingIsOpenAndOnlyOneWarningAtATime() {
        val watch = watch()
        val bad = fine.copy(thermalStatus = 2, batteryPercent = 10)
        repeat(5) { assertNull(watch.look(it.toLong(), bad, quiet = true)) }
        // Afterwards the most important one first, the next one on the next look.
        assertEquals(ContinuumWatch.Condition.HEAT, watch.look(5, bad)!!.condition)
        assertEquals(ContinuumWatch.Condition.BATTERY, watch.look(6, bad)!!.condition)
        assertNull(watch.look(7, bad))
        // A condition that ended while something was open is never announced.
        val ended = watch()
        assertNull(ended.look(0, bad, quiet = true))
        assertNull(ended.look(1, fine))
    }

    @Test fun noInventedAlarmWording() {
        val watch = watch()
        val texts = listOf(fine.copy(thermalStatus = 2), fine.copy(thermalStatus = 4), fine.copy(batteryPercent = 3),
            fine.copy(storageFreeBytes = 100_000_000L)).mapNotNull { facts -> watch().also { it.look(0, fine) }.look(1, facts)?.text } +
            listOfNotNull(watch.look(0, fine.copy(batteryPercent = 15))?.text)
        assertEquals(5, texts.size)
        texts.forEach { text ->
            listOf("Achtung", "Datenüberlastung", "Bedrohung", "Alarm", "kritisch", "!").forEach {
                assertFalse("$text contains $it", text.contains(it, ignoreCase = true))
            }
        }
    }

    // ── Through the director ────────────────────────────────────────────────────────────────────────────────

    @Test fun theDirectorSaysAWarningOnceAndStaysQuietDuringJobsSpeechAndItsOwnQuestions() {
        val h = ContinuumBriefingHarness()
        h.director.watchTick()
        assertTrue(h.chat.isEmpty())
        h.facts = h.facts.copy(thermalStatus = 2)
        h.loadReduced = true
        h.busy = true
        h.director.watchTick()
        assertTrue("not into a running job", h.chat.isEmpty())
        h.busy = false
        h.speakingNow = true
        h.director.watchTick()
        assertTrue("not into speech", h.chat.isEmpty())
        h.speakingNow = false
        h.director.watchTick()
        assertEquals(listOf("Heinz, das Gerät wird warm – ich schalte einen Gang zurück."), h.chat)
        assertEquals(h.chat, h.spoken)
        assertTrue("a warning is not an owner turn", h.ownerTurn == 0L)
        repeat(10) { h.director.watchTick() }
        assertEquals(1, h.chat.size)
        assertEquals(ContinuumBriefingBoard.State.ATTENTION, ContinuumBriefingBoard.cards().single { it.kind.code == "system" }.state)
        // The watcher only reads: nothing is dispatched, dialled or requested.
        assertTrue(h.submitted.isEmpty() && h.weatherRequests.isEmpty() && h.calendarWindows.isEmpty() && h.dialed.isEmpty())
    }

    @Test fun aStorageWarningOffersTheDiagnosisAndNothingStartsWithoutAYes() {
        val h = ContinuumBriefingHarness()
        h.mode = InternetAccessMode.OFF
        h.director.watchTick()
        assertTrue(h.chat.isEmpty())
        h.facts = h.facts.copy(storageFreeBytes = 2_000_000_000L)
        h.director.watchTick()
        assertEquals("Heinz, der Speicher ist fast voll: noch 2 Gigabyte frei. Soll ich eine Systemdiagnose machen?", h.last)
        assertTrue(h.submitted.isEmpty())
        // While the question waits for his answer, no second warning is said.
        h.facts = h.facts.copy(batteryPercent = 9)
        h.director.watchTick()
        assertEquals(1, h.chat.size)
        assertTrue(h.say("ja"))
        assertEquals(listOf(ContinuumBriefingDirector.ORDER_DIAGNOSIS), h.submitted)
        h.director.watchTick()
        assertEquals("Heinz, der Akku ist bei 9 Prozent und lädt nicht.", h.last)
    }

    // ── The call target in the address book (resolver of the send lane, dial rule) ──────────────────────────

    @Test fun theDialRuleOfTheSendResolver() {
        val rows = listOf(
            OwnerContactPhone(1, "Anna Beispiel", "+43 000 555 0101", "+430005550101", true),
            OwnerContactPhone(1, "Anna Beispiel", "0000 555 0111", "+430005550111", false),
            OwnerContactPhone(2, "Büro", "0000 555 0120", null, false),
            OwnerContactPhone(3, "Leer", "-", null, true))
        val anna = OwnerSendRecipientResolver.dial("meine Anna", rows) as OwnerSendRecipientResolver.Resolution.Resolved
        assertEquals("the mobile number of the one contact", "+430005550101", anna.candidate.value)
        val office = OwnerSendRecipientResolver.dial("Büro", rows) as OwnerSendRecipientResolver.Resolution.Resolved
        assertEquals("a landline without country code is dialled as stored", "00005550120", office.candidate.value)
        assertTrue(OwnerSendRecipientResolver.dial("Berta", rows) is OwnerSendRecipientResolver.Resolution.NotFound)
        assertTrue(OwnerSendRecipientResolver.dial("", rows) is OwnerSendRecipientResolver.Resolution.NotFound)
        val empty = OwnerSendRecipientResolver.dial("Leer", rows) as OwnerSendRecipientResolver.Resolution.Unusable
        assertEquals("Bei Leer ist keine wählbare Nummer gespeichert. Angerufen habe ich niemanden.", empty.reason)
        // The WhatsApp rule of the send lane is untouched: it still refuses a number without country code.
        assertTrue(OwnerSendRecipientResolver.whatsapp("Büro", rows) is OwnerSendRecipientResolver.Resolution.Unusable)
    }
}
