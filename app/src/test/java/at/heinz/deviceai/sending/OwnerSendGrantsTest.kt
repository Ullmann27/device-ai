package at.heinz.deviceai.sending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class OwnerSendGrantsTest {
    @Test fun bothChannelsAreOffUntilTheOwnerSwitchesThemOnAndStayOnAcrossRestarts() {
        val store = FakeSendStore()
        val grants = fakeGrants(store)
        SendChannel.entries.forEach { assertFalse(grants.enabled(it)); assertNull(grants.grantedOn(it)) }
        grants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())
        assertTrue(grants.enabled(SendChannel.WHATSAPP))
        assertFalse("one channel never grants the other", grants.enabled(SendChannel.GMAIL))
        // A new instance on the same store = the app after a restart: the permission is not asked for again.
        val restarted = fakeGrants(store)
        assertTrue(restarted.enabled(SendChannel.WHATSAPP))
        assertEquals("2026-11-18", restarted.grantedOn(SendChannel.WHATSAPP))
        restarted.change(SendChannel.WHATSAPP, false, OwnerSendOwnerTap())
        assertFalse(fakeGrants(store).enabled(SendChannel.WHATSAPP))
        assertNull(fakeGrants(store).grantedOn(SendChannel.WHATSAPP))
    }

    @Test fun theDailyLimitIsHardCountsBothChannelsAndStartsAgainTheNextDay() {
        var day = LocalDate.of(2026, 11, 18)
        val grants = fakeGrants(FakeSendStore(), { day }, SendChannel.WHATSAPP, SendChannel.GMAIL)
        repeat(OwnerSendGrants.DAILY_CAP) { index ->
            assertTrue(grants.reserveSend(if (index % 2 == 0) SendChannel.WHATSAPP else SendChannel.GMAIL))
        }
        assertEquals(OwnerSendGrants.DAILY_CAP, grants.sentToday())
        assertEquals(0, grants.remainingToday())
        assertFalse(grants.reserveSend(SendChannel.WHATSAPP))
        assertFalse(grants.reserveSend(SendChannel.GMAIL))
        assertEquals(OwnerSendGrants.DAILY_CAP, grants.sentToday())
        day = day.plusDays(1)
        assertEquals(0, grants.sentToday())
        assertTrue(grants.reserveSend(SendChannel.WHATSAPP))
        assertEquals(1, grants.sentToday())
    }

    @Test fun nothingIsReservedWithoutTheGrantAndABrokenLedgerCountsAsEmptyNotAsUnlimited() {
        val store = FakeSendStore()
        val grants = fakeGrants(store)
        assertFalse(grants.reserveSend(SendChannel.WHATSAPP))
        assertEquals(0, grants.sentToday())
        grants.change(SendChannel.WHATSAPP, true, OwnerSendOwnerTap())
        store.put("sent_ledger", "2026-11-18|-5")
        assertEquals(0, grants.sentToday())
        store.put("sent_ledger", "2026-11-18|29")
        assertTrue(grants.reserveSend(SendChannel.WHATSAPP))
        assertFalse(grants.reserveSend(SendChannel.WHATSAPP))
    }

    @Test fun theGrantDialogSaysWhatHappensWithoutAskingAgain() {
        SendChannel.entries.forEach { channel ->
            val text = OwnerSendGrants.grantDialogText(channel)
            listOf("ohne jedes Mal nachzufragen", "ausdrücklich beauftragst", "erst nach deinem Ja", "höchstens 30",
                "jederzeit wieder ausschalten").forEach { assertTrue("$channel: $it", text.contains(it)) }
        }
        assertTrue(OwnerSendGrants.grantDialogText(SendChannel.WHATSAPP).contains("Nie an Gruppen"))
        assertEquals("WhatsApp-Nachrichten selbst senden", SendChannel.WHATSAPP.settingLabel)
        assertEquals("E-Mails über Gmail selbst senden", SendChannel.GMAIL.settingLabel)
    }

    /** The flow itself never talks to a model, the network or the tool layer. */
    @Test fun theDeterministicFlowNeedsNoModelNoNetworkAndNoToolLayer() {
        val main = listOf(File("src/main/java/at/heinz/deviceai/sending"), File("app/src/main/java/at/heinz/deviceai/sending"))
            .first { it.isDirectory }
        listOf("OwnerSendFlow.kt", "OwnerSendScreens.kt", "OwnerSendRecipientResolver.kt", "OwnerSendGrants.kt").forEach { name ->
            val text = File(main, name).readText()
            assertFalse(name, Regex("localmodel|ToolGate|ToolRegistry|HttpURLConnection|java\\.net|generate\\(").containsMatchIn(text))
        }
    }
}
