package at.heinz.deviceai.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The intake of „Eingehende Nachrichten melden“: which posted notification is a new incoming message of a chosen app,
 * how a burst becomes one item, and what is never reported. Synthetic notifications only – the real formats of
 * WhatsApp and Gmail are NOT verified here.
 */
class IncomingMessageInboxTest {
    private fun rig(settings: IncomingMessageSettings = IncomingMessageSettings(enabled = true, apps = listOf(WA, GM), quietHours = false)) =
        IncomingRig(attached = false).also { it.host.current = settings }

    private fun ignored(intake: IncomingIntake) = (intake as IncomingIntake.Ignored).why

    @Test fun offByDefaultAndOnlyForTheOwnersApps() {
        val defaults = IncomingMessageSettings()
        assertFalse(defaults.enabled)
        assertTrue(defaults.apps.isEmpty())
        val rig = rig(defaults)
        assertEquals(IncomingIgnore.DISABLED, ignored(rig.inbox.onNotification(chat("Anna", "Hallo" to 1L))))
        rig.host.current = IncomingMessageSettings(enabled = true, apps = listOf(GM))
        assertEquals(IncomingIgnore.APP_NOT_CHOSEN, ignored(rig.inbox.onNotification(chat("Anna", "Hallo" to 1L))))
        assertFalse(rig.inbox.claims(WA))
        assertTrue(rig.inbox.claims(GM))
        assertTrue(rig.inbox.open().isEmpty())
    }

    @Test fun aBurstOfOneConversationBecomesOneItemAfterTheLastMessage() {
        val rig = rig()
        val first = rig.inbox.onNotification(chat("Anna", "Bist du da?" to 1L)) as IncomingIntake.Collected
        assertTrue(first.startedItem)
        rig.time.advance(2_000)
        // The app re-posts the whole unread history with every message: only the new one counts.
        val second = rig.inbox.onNotification(chat("Anna", "Bist du da?" to 1L, "Ich warte unten" to 2L)) as IncomingIntake.Collected
        assertFalse(second.startedItem)
        rig.time.advance(3_000)
        assertTrue("the burst is still open: 3 s after the last message", rig.background.isEmpty())
        rig.time.advance(1_100)
        assertEquals(1, rig.background.size)
        val item = rig.background.single()
        assertEquals(2, item.count)
        assertEquals(listOf("Bist du da?", "Ich warte unten"), item.parts.map { it.text })
        assertEquals(IncomingState.WAITING, item.state)
        assertEquals("Anna", item.sender)
        assertEquals(ImportanceReason.CONTACT, item.decision.reason)
    }

    @Test fun aBurstThatDoesNotEndIsClosedAfterFifteenSecondsAndStaysBounded() {
        val rig = rig()
        repeat(9) { index ->
            rig.inbox.onNotification(chat("Anna", *(0..index).map { "Nachricht $it" to it.toLong() }.toTypedArray()))
            rig.time.advance(2_000)
        }
        assertEquals("closed by the upper bound, not by a pause", 1, rig.background.size)
        val item = rig.background.single()
        assertEquals(IncomingMessageInbox.MAX_PARTS, item.parts.size)
        assertTrue(item.count >= 8)
        // What arrives after the burst closed joins the waiting item without a second announcement.
        assertEquals(1, rig.inbox.waiting().size)
    }

    @Test fun anUpdateOfTheSameNotificationIsNotCountedAgain() {
        val rig = rig()
        rig.arrive(chat("Anna", "Hallo" to 1L))
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rig.inbox.onNotification(chat("Anna", "Hallo" to 1L))))
        // Without a messaging style: same title, text and timestamp is an update, a new timestamp is a new message.
        val plain = NotificationDetails(NotificationSource.LISTENER, whenMs = 50L)
        rig.arrive(posted(title = "Tom", text = "Ok", details = plain))
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rig.inbox.onNotification(posted(title = "Tom", text = "Ok", details = plain))))
        assertTrue(rig.inbox.onNotification(posted(title = "Tom", text = "Ok", details = plain.copy(whenMs = 90L))) is IncomingIntake.Collected)
        assertEquals(2, rig.background.size)
        assertTrue("only hashes are remembered", rig.inbox.rememberedHashes() in 1..8)
    }

    @Test fun theSameMessageFromBothSourcesIsOneMessage() {
        val rig = rig()
        val fromListener = chat("Anna", "Kommst du um fünf?" to 77L, source = NotificationSource.LISTENER)
        val fromService = chat("Anna", "Kommst du um fünf?" to 77L, source = NotificationSource.ACCESSIBILITY, key = "a11y|$WA|1f")
        assertTrue(rig.inbox.onNotification(fromService) is IncomingIntake.Collected)
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rig.inbox.onNotification(fromListener)))
        rig.time.advance(5_000)
        assertEquals(1, rig.background.single().count)
        // And the other way round, for a mail.
        assertTrue(rig.inbox.onNotification(mail("Anna Berger", "Fotos", "Anbei", whenMs = 5L)) is IncomingIntake.Collected)
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rig.inbox.onNotification(
            mail("Anna Berger", "Fotos", "Anbei", whenMs = 5L, source = NotificationSource.ACCESSIBILITY))))
    }

    @Test fun ownMessagesCallsMediaBackupsAndSummariesAreNeverReported() {
        val rig = rig()
        assertEquals(IncomingIgnore.ONGOING, ignored(rig.inbox.onNotification(posted(title = "WhatsApp", text = "Backup läuft", ongoing = true))))
        assertEquals(IncomingIgnore.GROUP_SUMMARY, ignored(rig.inbox.onNotification(posted(title = "WhatsApp", text = "5 Nachrichten aus 2 Chats", summary = true))))
        rig.time.advance(10_000)
        assertTrue("with the listener every message has its own notification: a summary is nothing", rig.inbox.open().isEmpty())
        listOf("call", "missed_call", "transport", "progress", "service", "sys").forEach { category ->
            assertEquals(category, IncomingIgnore.CATEGORY, ignored(rig.inbox.onNotification(posted(title = "Anna", text = "Anruf", category = category))))
        }
        assertEquals(IncomingIgnore.OWN_PACKAGE, ignored(rig.inbox.onNotification(
            posted(title = "Continuum", text = "Eine Nachricht wartet").copy(isOwnPackage = true))))
        assertEquals(IncomingIgnore.EMPTY, ignored(rig.inbox.onNotification(posted(title = "", text = ""))))
        // The owner answered from the notification: the app adds his own line to the history.
        rig.arrive(chat("Anna", "Bist du da?" to 1L, self = "Du"))
        val ownReply = chat("Anna", "Bist du da?" to 1L, "Ja, gleich" to 2L, self = "Du", senders = listOf("Anna", "Du"))
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rig.inbox.onNotification(ownReply)))
        val onlyOwn = chat("Tom", "Bis später" to 3L, self = "Du", senders = listOf("Du"))
        assertEquals(IncomingIgnore.OWN_MESSAGE, ignored(rig.inbox.onNotification(onlyOwn)))
        assertEquals(1, rig.inbox.open().size)
    }

    @Test fun groupsAndSilentNotificationsAreOffByDefaultAndTheOwnersChoice() {
        val rig = rig()
        val group = chat("Lisa", "Wer holt Oma ab?" to 1L, group = "Familie")
        val notReported = rig.inbox.onNotification(group) as IncomingIntake.NotReported
        assertEquals(ImportanceReason.GROUP_CHAT, notReported.decision.reason)
        assertEquals(IncomingIgnore.SILENT_NOTIFICATION, ignored(rig.inbox.onNotification(chat("Tom", "psst" to 2L, silent = true))))
        assertTrue(rig.inbox.open().isEmpty())
        assertEquals("Die Benachrichtigung kam stumm, und stumme sind ausgeschaltet.", rig.inbox.lastDecision(reported = false, about = "Tom")!!.explanation)

        rig.host.current = rig.host.current.copy(groups = true, silentNotifications = true)
        val collected = rig.arrive(chat("Lisa", "Wer holt Oma ab?" to 1L, "Ich kann nicht" to 5L, group = "Familie",
            senders = listOf("Lisa", "Tom"))) as IncomingIntake.Collected
        assertTrue(collected.item.isGroup)
        assertEquals("Familie", collected.item.conversation)
        assertEquals("only the message that was not seen before", listOf("Tom"), collected.item.parts.map { it.sender })
        assertEquals(ImportanceReason.GROUP_MESSAGE, collected.item.decision.reason)
        assertTrue(rig.arrive(chat("Tom", "psst" to 9L, silent = true)) is IncomingIntake.Collected)
    }

    @Test fun advertisingIsNotReportedAndUnclearFollowsTheOwnersSetting() {
        val rig = rig()
        val promotion = rig.inbox.onNotification(mail("Zalando", "Black Week: bis zu 40 % Rabatt")) as IncomingIntake.NotReported
        assertEquals(ImportanceReason.PROMOTION, promotion.decision.reason)
        val unclear = rig.arrive(mail("Österreichische Post", "Ihr Paket kommt morgen")) as IncomingIntake.Collected
        assertTrue("default: reported briefly as unclear", unclear.item.unclear)
        val court = rig.arrive(mail("Bezirksgericht Linz", "Ladung als Zeuge, GZ 12 C 345/26")) as IncomingIntake.Collected
        assertEquals(ImportanceReason.AUTHORITY, court.item.decision.reason)

        rig.host.current = rig.host.current.copy(unclear = UnclearHandling.SILENT)
        assertTrue(rig.inbox.onNotification(mail("DHL Paket", "Ihre Sendung ist unterwegs")) is IncomingIntake.NotReported)
        assertEquals(2, rig.inbox.open().size)

        // „Warum hast du das (nicht) gemeldet?“ – sender and reason, never the text.
        val why = rig.inbox.lastDecision(reported = false, about = "Zalando")!!
        assertTrue(why.explanation, why.explanation.startsWith("Die Nachricht sieht nach Werbung aus"))
        assertEquals("Gmail", why.appLabel)
        assertTrue(rig.inbox.lastDecision(reported = false, about = "dhl")!!.explanation.endsWith("Unklare Nachrichten meldest du nicht."))
        assertTrue(rig.inbox.lastDecision(reported = true)!!.explanation.contains("Gericht oder eine Behörde"))
        assertNull(rig.inbox.lastDecision(reported = true, about = "Niemand"))
    }

    @Test fun aLockedDeviceGivesOnlyAHintPerAppWithoutSenderOrText() {
        val rig = rig()
        fun hidden() = posted(title = "", text = "", key = "a11y|$WA|hidden", category = null,
            details = NotificationDetails(NotificationSource.ACCESSIBILITY, contentHidden = true))
        val first = rig.arrive(hidden()) as IncomingIntake.Collected
        assertTrue(first.item.hidden && first.item.unclear)
        assertEquals("", first.item.sender)
        assertTrue(first.item.parts.isEmpty())
        // The next ones only count up: one hint per app until the owner has been told.
        val second = rig.inbox.onNotification(hidden()) as IncomingIntake.Collected
        assertFalse(second.startedItem)
        assertEquals(2, second.item.count)
        assertEquals(1, rig.background.size)
        assertTrue(rig.inbox.lastDecision(reported = true)!!.explanation.startsWith("Android hat mir nur gezeigt, dass in WhatsApp etwas eingegangen ist"))
        rig.host.current = rig.host.current.copy(unclear = UnclearHandling.SILENT)
        rig.inbox.clear()
        assertTrue("without content nothing can be classified: the owner's setting for unclear decides",
            rig.inbox.onNotification(hidden()) is IncomingIntake.NotReported)
    }

    @Test fun aSummaryWithoutItsMessageEventBecomesAHintAndNeverASecondAnnouncement() {
        fun summary(text: String = "1 neue Nachricht") = posted(title = "WhatsApp", text = text, summary = true, key = "a11y|$WA|sum",
            details = NotificationDetails(NotificationSource.ACCESSIBILITY, whenMs = 3L))
        // The message's own event arrives (before or after the summary): the summary adds nothing.
        val rig = rig()
        rig.inbox.onNotification(chat("Anna", "Hallo" to 1L, source = NotificationSource.ACCESSIBILITY))
        assertEquals(IncomingIgnore.GROUP_SUMMARY, ignored(rig.inbox.onNotification(summary())))
        rig.time.advance(10_000)
        assertEquals(listOf("Anna"), rig.inbox.open().map { it.sender })
        val other = rig()
        other.inbox.onNotification(summary())
        other.time.advance(300)
        other.inbox.onNotification(chat("Anna", "Hallo" to 1L, source = NotificationSource.ACCESSIBILITY))
        other.time.advance(10_000)
        assertEquals(listOf(false), other.inbox.open().map { it.hidden })

        // Only the summary came through (Android delivers one notification event per short interval): a hint, no sender.
        val lost = rig()
        lost.inbox.onNotification(summary())
        lost.inbox.onNotification(summary("2 neue Nachrichten"))
        lost.time.advance(IncomingMessageInbox.SUMMARY_GRACE_MS + IncomingMessageInbox.DEBOUNCE_MS + 100)
        val hint = lost.inbox.open().single()
        assertTrue(hint.hidden && hint.sender.isEmpty() && hint.parts.isEmpty())
        assertEquals("one hint for both summaries", 1, hint.count)
        // Once told, the next hints of that app wait for the cool-down instead of being announced one by one.
        lost.inbox.close(hint)
        lost.inbox.onNotification(summary("3 neue Nachrichten"))
        lost.time.advance(60_000)
        assertTrue(lost.inbox.waiting().isEmpty())
        lost.time.advance(IncomingMessageInbox.HINT_COOLDOWN_MS)
        assertEquals(1, lost.inbox.waiting().size)
        // A summary that repeats the chat's history is read like the message itself, and counted once.
        val rich = rig()
        val history = chat("Anna", "Kommst du?" to 5L, source = NotificationSource.ACCESSIBILITY)
        assertTrue(rich.inbox.onNotification(history.copy(isGroupSummary = true)) is IncomingIntake.Collected)
        assertEquals(IncomingIgnore.DUPLICATE, ignored(rich.inbox.onNotification(history)))
    }

    @Test fun contentIsBoundedExpiresAndIsReleased() {
        val rig = rig()
        val long = "Sehr langer Satz. ".repeat(400)
        val item = (rig.arrive(chat("Anna", long to 1L)) as IncomingIntake.Collected).item
        assertTrue(item.parts.single().text.length <= IncomingMessageInbox.MAX_READ)
        assertFalse("toString never carries content", item.toString().contains("Sehr langer"))
        repeat(30) { index -> rig.arrive(chat("Kontakt $index", "Hallo" to index.toLong(), key = "k$index")) }
        assertEquals(IncomingMessageInbox.MAX_ITEMS, rig.inbox.open().size)
        assertEquals("the oldest item was dropped and its content released", IncomingState.DONE, item.state)
        assertTrue(item.parts.isEmpty())

        val kept = rig.inbox.open().last()
        rig.time.advance(IncomingMessageInbox.RETENTION_MS + 1)
        assertTrue("everything expires after the retention time", rig.inbox.open().isEmpty())
        assertTrue(kept.parts.isEmpty())
        assertNull(rig.inbox.lastDecision())
        // After the duplicate memory expired the same text would be a new message again.
        assertTrue(rig.inbox.onNotification(chat("Kontakt 29", "Hallo" to 29L, key = "k29")) is IncomingIntake.Collected)

        rig.inbox.clear()
        assertEquals(0, rig.inbox.rememberedHashes())
        assertTrue(rig.inbox.open().isEmpty())
    }

    @Test fun aMessageReadInItsAppBeforeItWasOfferedIsDropped() {
        val rig = rig()
        val waiting = (rig.arrive(chat("Anna", "Hallo" to 1L, key = "wa-anna")) as IncomingIntake.Collected).item
        rig.inbox.onRemoved("wa-anna")
        assertEquals(IncomingState.DONE, waiting.state)
        // One the owner was already asked about stays in his list.
        val offered = (rig.arrive(chat("Tom", "Servus" to 2L, key = "wa-tom")) as IncomingIntake.Collected).item
        rig.inbox.mark(offered, IncomingState.OFFERED)
        rig.inbox.onRemoved("wa-tom")
        assertEquals(listOf(offered), rig.inbox.open())
        // The accessibility source reports no removal at all.
        rig.inbox.onRemoved("unknown")
    }

    @Test fun mailsOfOneSenderAreOneItemAndTheSubjectIsNotTheBody() {
        val rig = rig()
        rig.inbox.onNotification(mail("Anna Berger", "Fotos vom Wochenende", "Hallo, anbei die Bilder.", whenMs = 1L))
        val item = (rig.arrive(mail("Anna Berger", "Noch eins", "Das hatte ich vergessen.", whenMs = 2L)) as IncomingIntake.Collected).item
        assertEquals(IncomingKind.MAIL, item.kind)
        assertEquals(listOf("Fotos vom Wochenende", "Noch eins"), item.parts.map { it.subject })
        assertEquals(listOf("Hallo, anbei die Bilder.", "Das hatte ich vergessen."), item.parts.map { it.text })
        assertEquals(1, rig.background.size)
    }

    @Test fun olderWhatsAppGroupTitlesAndCountSuffixesAreUnderstoodAsData() {
        val rig = rig(IncomingMessageSettings(enabled = true, apps = listOf(WA), groups = true, quietHours = false))
        val legacy = (rig.arrive(posted(title = "Lisa @ Familie", text = "Wer holt Oma ab?")) as IncomingIntake.Collected).item
        assertTrue(legacy.isGroup)
        assertEquals("Lisa" to "Familie", legacy.sender to legacy.conversation)
        val counted = (rig.arrive(posted(title = "Tom (2 Nachrichten)", text = "Servus")) as IncomingIntake.Collected).item
        assertEquals("Tom", counted.sender)
        val number = (rig.arrive(posted(title = "+43 660 0000001", text = "Guten Tag")) as IncomingIntake.Collected).item
        assertTrue(number.unknownNumber)
        // A title that carries a code is not spoken as a sender name.
        val code = (rig.arrive(posted(title = "Ihr Code 482913 zur Bestätigung", text = "gültig 5 Minuten")) as IncomingIntake.Collected).item
        assertEquals("", code.sender)
    }
}
