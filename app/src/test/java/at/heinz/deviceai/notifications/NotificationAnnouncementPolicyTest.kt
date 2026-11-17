package at.heinz.deviceai.notifications

import at.heinz.deviceai.cognitive.DriveMode
import at.heinz.deviceai.notifications.AnnouncementDecision.Announce
import at.heinz.deviceai.notifications.AnnouncementDecision.Defer
import at.heinz.deviceai.notifications.AnnouncementDecision.Suppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAnnouncementPolicyTest {
    private val whatsApp = "com.whatsapp"
    private val signal = "org.thoughtcrime.securesms"
    // Stand-in for an app the owner picked from the installed-app list; not a real bank package.
    private val ownerPicked = "com.example.ownerpicked"

    private fun notification(
        key: String = "k1",
        packageName: String = whatsApp,
        label: String = "WhatsApp",
        title: String = "Anna",
        text: String = "Bin gleich da",
        category: String? = "msg",
        ongoing: Boolean = false,
        summary: Boolean = false,
        own: Boolean = false,
    ) = IncomingNotification(key, packageName, label, title, text, category, ongoing, summary, 1_000L, own)

    private fun bankNotification(title: String, text: String, key: String = "bank") =
        notification(key, ownerPicked, "Meine Bank", title, text, category = null)

    private fun settings(
        vararg rules: Pair<String, AnnouncementMode>,
        enabled: Boolean = true,
        quiet: Boolean = false,
        maxPerMinute: Int = NotificationAnnouncementPolicy.DEFAULT_MAX_PER_MINUTE,
    ) = AnnouncementSettings(enabled, rules.associate { it.first to AppRule(it.second) }, quiet, maxPerMinute)

    private fun state(
        nowMs: Long = 1_000L,
        conversation: Boolean = true,
        drive: Boolean = false,
        speaking: Boolean = false,
        listening: Boolean = false,
    ) = AnnouncementState(conversation, drive, speaking, listening, nowMs)

    private fun spoken(decision: AnnouncementDecision): String = (decision as Announce).spokenText

    private fun reason(decision: AnnouncementDecision): SuppressReason = (decision as Suppress).reason

    private fun hasLoneSurrogate(text: String): Boolean = text.indices.any { i ->
        val c = text[i]
        (c.isHighSurrogate() && (i + 1 >= text.length || !text[i + 1].isLowSurrogate())) ||
            (c.isLowSurrogate() && (i == 0 || !text[i - 1].isHighSurrogate()))
    }

    @Test fun defaultsAnnounceNothing() {
        val defaults = AnnouncementSettings()
        assertFalse(defaults.enabled)
        assertTrue(defaults.apps.isEmpty())
        assertTrue(defaults.quietWhenNotInConversation)
        assertEquals(4, defaults.maxPerMinute)

        val policy = NotificationAnnouncementPolicy()
        assertEquals(SuppressReason.DISABLED, reason(policy.decide(notification(), defaults, state())))
        // Switched on, but the owner enabled no app: still nothing, also for a known messenger.
        assertEquals(SuppressReason.APP_NOT_ENABLED, reason(policy.decide(notification(), settings(), state())))
        assertEquals(
            SuppressReason.APP_NOT_ENABLED,
            reason(policy.decide(notification(), settings(whatsApp to AnnouncementMode.OFF), state())),
        )
        // A rule for another app does not open this one.
        assertEquals(
            SuppressReason.APP_NOT_ENABLED,
            reason(policy.decide(notification(), settings(signal to AnnouncementMode.FULL), state())),
        )
        // A stored rule is not enough while the master switch is off.
        assertEquals(
            SuppressReason.DISABLED,
            reason(policy.decide(notification(), settings(whatsApp to AnnouncementMode.FULL, enabled = false), state())),
        )
    }

    @Test fun messengerPhrasingPerMode() {
        val appOnly = NotificationAnnouncementPolicy().decide(notification(), settings(whatsApp to AnnouncementMode.APP_ONLY), state()) as Announce
        assertEquals("WhatsApp: neue Nachricht.", appOnly.spokenText)
        assertEquals("WhatsApp", appOnly.displayTitle)
        assertEquals("Neue Nachricht.", appOnly.displayDetail)

        val sender = NotificationAnnouncementPolicy().decide(notification(), settings(whatsApp to AnnouncementMode.SENDER), state()) as Announce
        assertEquals("WhatsApp: Nachricht von Anna.", sender.spokenText)
        assertEquals("Nachricht von Anna.", sender.displayDetail)

        val full = NotificationAnnouncementPolicy().decide(notification(), settings(whatsApp to AnnouncementMode.FULL), state()) as Announce
        assertEquals("WhatsApp: Nachricht von Anna: Bin gleich da.", full.spokenText)
        assertEquals("Nachricht von Anna: Bin gleich da.", full.displayDetail)
    }

    @Test fun lowerModesNeverContainTheText() {
        for (mode in listOf(AnnouncementMode.APP_ONLY, AnnouncementMode.SENDER)) {
            val result = NotificationAnnouncementPolicy().decide(notification(text = "Geheimes Treffen um acht"), settings(whatsApp to mode), state()) as Announce
            assertFalse(result.spokenText.contains("Geheim"))
            assertFalse(result.displayDetail.contains("Geheim"))
        }
        val appOnly = NotificationAnnouncementPolicy().decide(notification(), settings(whatsApp to AnnouncementMode.APP_ONLY), state()) as Announce
        assertFalse(appOnly.spokenText.contains("Anna"))
        assertFalse(appOnly.displayDetail.contains("Anna"))
    }

    @Test fun otherAppsAreReportedAndAmountsOnlyInFullMode() {
        val credit = bankNotification("Geldeingang", "Du hast € 1.250,00 erhalten")
        assertEquals(
            "Meine Bank: neue Benachrichtigung.",
            spoken(NotificationAnnouncementPolicy().decide(credit, settings(ownerPicked to AnnouncementMode.APP_ONLY), state())),
        )
        assertEquals(
            "Meine Bank meldet: Geldeingang.",
            spoken(NotificationAnnouncementPolicy().decide(credit, settings(ownerPicked to AnnouncementMode.SENDER), state())),
        )
        assertEquals(
            "Meine Bank meldet: Geldeingang. Du hast € 1.250,00 erhalten.",
            spoken(NotificationAnnouncementPolicy().decide(credit, settings(ownerPicked to AnnouncementMode.FULL), state())),
        )

        for (title in listOf("€ 1.250,00 erhalten", "1250 EUR erhalten", "12,50 € erhalten", "+300 Euro erhalten", "CHF 1'200.50 erhalten")) {
            val inTitle = bankNotification(title, "")
            val sender = NotificationAnnouncementPolicy().decide(inTitle, settings(ownerPicked to AnnouncementMode.SENDER), state()) as Announce
            assertEquals("Meine Bank meldet: ein Betrag erhalten.", sender.spokenText)
            assertFalse(sender.displayDetail.any(Char::isDigit))
            val full = spoken(NotificationAnnouncementPolicy().decide(inTitle, settings(ownerPicked to AnnouncementMode.FULL), state()))
            assertEquals("Meine Bank meldet: $title.", full)
        }
    }

    @Test fun emailAndRepeatedAppNameAndMissingLabel() {
        val mail = notification(packageName = ownerPicked, label = "Post", title = "Anna", text = "Betreff", category = "email")
        assertEquals("Post: E-Mail von Anna.", spoken(NotificationAnnouncementPolicy().decide(mail, settings(ownerPicked to AnnouncementMode.SENDER), state())))
        assertEquals("Post: neue E-Mail.", spoken(NotificationAnnouncementPolicy().decide(mail, settings(ownerPicked to AnnouncementMode.APP_ONLY), state())))

        val repeated = notification(title = "whatsapp", text = "3 neue Nachrichten")
        assertEquals("WhatsApp: neue Nachricht.", spoken(NotificationAnnouncementPolicy().decide(repeated, settings(whatsApp to AnnouncementMode.SENDER), state())))
        assertEquals(
            "WhatsApp: neue Nachricht: 3 neue Nachrichten.",
            spoken(NotificationAnnouncementPolicy().decide(repeated, settings(whatsApp to AnnouncementMode.FULL), state())),
        )

        // A messenger notification of another category (a missed call) is not called a message.
        val missedCall = notification(title = "Verpasster Sprachanruf", text = "Anna", category = "missed_call")
        assertEquals(
            "WhatsApp meldet: Verpasster Sprachanruf.",
            spoken(NotificationAnnouncementPolicy().decide(missedCall, settings(whatsApp to AnnouncementMode.SENDER), state())),
        )
        val uncategorised = notification(category = null)
        assertEquals(
            "WhatsApp: Nachricht von Anna.",
            spoken(NotificationAnnouncementPolicy().decide(uncategorised, settings(whatsApp to AnnouncementMode.SENDER), state())),
        )

        val unnamed = notification(label = " \n ")
        assertEquals("Eine App: Nachricht von Anna.", spoken(NotificationAnnouncementPolicy().decide(unnamed, settings(whatsApp to AnnouncementMode.SENDER), state())))
    }

    @Test fun oneTimeCodesAreNeverSpokenInAnyMode() {
        val variants = listOf(
            "Bank" to "Ihre TAN lautet 482913",
            "Bestätigungscode" to "482913",
            "482913 ist dein Code" to "",
            "WhatsApp" to "Dein WhatsApp-Code: 123-456. Teile ihn mit niemandem.",
            "Google" to "G-582931 is your Google verification code.",
            "Login" to "Your one-time password is 9041",
            "Konto" to "PIN: 12345678",
            "Bank" to "Einmalpasswort 739201 für deine Zahlung über € 49,90",
            "Sicherheit" to "OTP 4821",
            "mTAN" to "Freigabe mit 55 21 09",
            "Anmeldung" to "Dein Kennwort für die Anmeldung lautet 77441",
            "BESTÄTIGUNG" to "CODE 481516",
            "Info" to "Code:482913",
            "Anna" to "Der Türcode ist 4821, bis gleich",
            "Konto" to "Zugangscode 1234567890",
        )
        for ((title, text) in variants) {
            for (mode in listOf(AnnouncementMode.APP_ONLY, AnnouncementMode.SENDER, AnnouncementMode.FULL)) {
                for (packageName in listOf(whatsApp, ownerPicked)) {
                    val incoming = notification(packageName = packageName, label = "Dienst", title = title, text = text)
                    val result = NotificationAnnouncementPolicy().decide(incoming, settings(packageName to mode), state()) as Announce
                    val case = "$title / $text / $mode"
                    assertEquals(case, "Dienst: Ein Sicherheitscode ist eingegangen.", result.spokenText)
                    assertEquals(case, "Dienst", result.displayTitle)
                    assertEquals(case, "Ein Sicherheitscode ist eingegangen.", result.displayDetail)
                }
            }
        }
    }

    @Test fun oneTimeCodeSurvivesNeitherTruncationNorBatching() {
        // The code sits far behind the 240-character speech bound and must still be recognised.
        val late = notification(text = "Hallo. ".repeat(60) + "Dein Bestätigungscode lautet 918273")
        val result = NotificationAnnouncementPolicy().decide(late, settings(whatsApp to AnnouncementMode.FULL), state()) as Announce
        assertEquals("WhatsApp: Ein Sicherheitscode ist eingegangen.", result.spokenText)

        // In a batch a security code is not folded into a count and still never spoken.
        val batch = listOf(
            notification(key = "a", title = "Anna", text = "Hallo"),
            notification(key = "b", title = "Bank", text = "TAN 445566"),
        )
        val decisions = NotificationAnnouncementPolicy().decideAll(batch, settings(whatsApp to AnnouncementMode.SENDER), state())
        assertEquals("WhatsApp: Nachricht von Anna.", spoken(decisions[0]))
        assertEquals("WhatsApp: Ein Sicherheitscode ist eingegangen.", spoken(decisions[1]))
    }

    @Test fun ordinaryNumbersAreNotMistakenForCodes() {
        val full = settings(whatsApp to AnnouncementMode.FULL, ownerPicked to AnnouncementMode.FULL)
        assertEquals(
            "WhatsApp: Nachricht von Anna: Wir sehen uns um 18 Uhr, Hausnummer 12.",
            spoken(NotificationAnnouncementPolicy().decide(notification(text = "Wir sehen uns um 18 Uhr, Hausnummer 12"), full, state())),
        )
        // A code word without digits, digits without a code word.
        assertEquals(
            "WhatsApp: Nachricht von Anna: Code Review morgen?",
            spoken(NotificationAnnouncementPolicy().decide(notification(text = "Code Review morgen?"), full, state())),
        )
        assertEquals(
            "WhatsApp: Nachricht von Anna: Ruf bitte 4821 an.",
            spoken(NotificationAnnouncementPolicy().decide(notification(text = "Ruf bitte 4821 an"), full, state())),
        )
        // An amount or a date next to "Bestätigung" is not a one-time code.
        assertEquals(
            "Meine Bank meldet: Bestätigung. Zahlung über € 1250,00 erhalten.",
            spoken(NotificationAnnouncementPolicy().decide(bankNotification("Bestätigung", "Zahlung über € 1250,00 erhalten"), full, state())),
        )
        assertEquals(
            "Meine Bank meldet: Bestätigung. Termin am 17.11.2026 gebucht.",
            spoken(NotificationAnnouncementPolicy().decide(bankNotification("Bestätigung", "Termin am 17.11.2026 gebucht"), full, state())),
        )
    }

    @Test fun linksAndMailAddressesAreNotReadAloud() {
        val full = settings(whatsApp to AnnouncementMode.FULL)
        assertEquals(
            "WhatsApp: Nachricht von Anna: Schau mal ein Link und ein Link.",
            spoken(NotificationAnnouncementPolicy().decide(notification(text = "Schau mal https://example.com/a?b=1 und www.test.de/x."), full, state())),
        )
        assertEquals(
            "WhatsApp: Nachricht von Anna: Hier ein Link, schreib an eine E-Mail-Adresse!",
            spoken(NotificationAnnouncementPolicy().decide(notification(text = "Hier bit.ly/3xYz, schreib an anna.muster@example.org!"), full, state())),
        )
        // Also when the link is in the title and only the sender is announced.
        val inTitle = NotificationAnnouncementPolicy().decide(
            notification(title = "HTTP://Evil.example/login"), settings(whatsApp to AnnouncementMode.SENDER), state(),
        ) as Announce
        assertEquals("WhatsApp: Nachricht von ein Link.", inTitle.spokenText)
        // A link longer than the speech bound is replaced before the cut, not half of it spoken.
        val longLink = "Link: https://example.com/" + "a".repeat(400)
        val cut = spoken(NotificationAnnouncementPolicy().decide(notification(text = longLink), full, state()))
        assertEquals("WhatsApp: Nachricht von Anna: Link: ein Link.", cut)
    }

    @Test fun controlAndFormatCharactersAreStripped() {
        val hostile = notification(
            title = "An\u0007na‮‏",
            text = "Zeile eins\nZeile\tzwei​  drei\u0000 vier fünf\u001B[31m",
        )
        val result = NotificationAnnouncementPolicy().decide(hostile, settings(whatsApp to AnnouncementMode.FULL), state()) as Announce
        assertEquals("WhatsApp: Nachricht von An na: Zeile eins Zeile zwei drei vier fünf [31m.", result.spokenText)
        for (value in listOf(result.spokenText, result.displayTitle, result.displayDetail)) {
            assertTrue(value.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it == ' ' })
            assertFalse(value.contains("  "))
        }
        // Only control characters is the same as empty.
        val blank = notification(title = "\u0000​\n", text = " \t‮")
        assertEquals(SuppressReason.EMPTY, reason(NotificationAnnouncementPolicy().decide(blank, settings(whatsApp to AnnouncementMode.FULL), state())))
    }

    @Test fun lengthsAreBoundedWithoutSplittingSurrogatePairs() {
        val emoji = "😀" // one code point, two UTF-16 units
        for (shift in 0..3) {
            val title = "x".repeat(shift) + emoji.repeat(100)
            val text = "y".repeat(shift) + emoji.repeat(400)
            val boundedTitle = UntrustedNotificationText.bound(UntrustedNotificationText.clean(title), UntrustedNotificationText.MAX_TITLE)
            val boundedText = UntrustedNotificationText.bound(UntrustedNotificationText.clean(text), UntrustedNotificationText.MAX_TEXT)
            assertTrue(boundedTitle.length in 70..80)
            assertTrue(boundedText.length in 230..240)
            assertTrue(boundedTitle.endsWith("…") && boundedText.endsWith("…"))
            val result = NotificationAnnouncementPolicy().decide(
                notification(label = emoji.repeat(60), title = title, text = text), settings(whatsApp to AnnouncementMode.FULL), state(),
            ) as Announce
            for (value in listOf(result.spokenText, result.displayTitle, result.displayDetail)) assertFalse(hasLoneSurrogate(value))
            assertTrue(result.displayTitle.length <= UntrustedNotificationText.MAX_LABEL)
            assertTrue(result.spokenText.length <= 40 + 80 + 240 + 24)
        }
        // A pair that straddles the inspection bound, and unpaired halves, are dropped cleanly.
        val straddling = "a".repeat(UntrustedNotificationText.MAX_RAW - 1) + emoji + "tail"
        assertEquals("a".repeat(UntrustedNotificationText.MAX_RAW - 1), UntrustedNotificationText.clean(straddling))
        assertEquals("ab", UntrustedNotificationText.clean("a\uD83Db\uDE00"))
    }

    @Test fun longTextIsCutAtASentenceOrWordBoundary() {
        val sentences = "Das ist der erste Satz. ".repeat(30).trim()
        val cutAtSentence = UntrustedNotificationText.bound(sentences, UntrustedNotificationText.MAX_TEXT)
        assertTrue(cutAtSentence.length <= 240)
        assertTrue(cutAtSentence.endsWith("Das ist der erste Satz. …"))

        val words = "wort ".repeat(100).trim()
        val cutAtWord = UntrustedNotificationText.bound(words, UntrustedNotificationText.MAX_TEXT)
        assertTrue(cutAtWord.length <= 240)
        assertTrue(cutAtWord.endsWith("wort…"))
        assertTrue(cutAtWord.removeSuffix("…").split(' ').all { it == "wort" })

        // A decimal point is not a sentence end.
        val number = "Betrag 1.250 ".repeat(40).trim()
        assertFalse(UntrustedNotificationText.bound(number, UntrustedNotificationText.MAX_TEXT).endsWith("1. …"))

        val unbroken = "z".repeat(1_000)
        assertEquals("z".repeat(79) + "…", UntrustedNotificationText.bound(unbroken, UntrustedNotificationText.MAX_TITLE))
        assertEquals("kurz", UntrustedNotificationText.bound("kurz", UntrustedNotificationText.MAX_TITLE))

        val huge = notification(title = "T".repeat(50_000), text = "Satz. ".repeat(50_000))
        val result = NotificationAnnouncementPolicy().decide(huge, settings(whatsApp to AnnouncementMode.FULL), state()) as Announce
        assertTrue(result.spokenText.length <= 40 + 80 + 240 + 24)
    }

    @Test fun duplicatesAreSuppressedUntilContentChangesOrTheNotificationIsRemoved() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.FULL)
        assertTrue(policy.decide(notification(), rules, state(1_000)) is Announce)
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(notification(), rules, state(2_000))))
        // Same key, new content: a new message in the same chat.
        assertEquals(
            "WhatsApp: Nachricht von Anna: Doch später.",
            spoken(policy.decide(notification(text = "Doch später"), rules, state(3_000))),
        )
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(notification(text = "Doch später"), rules, state(4_000))))
        // Same content under another key is another notification.
        assertTrue(policy.decide(notification(key = "k2", text = "Doch später"), rules, state(5_000)) is Announce)
        // Removed (read on the phone), then the same words arrive again.
        policy.forget("k1")
        assertTrue(policy.decide(notification(text = "Doch später"), rules, state(70_000)) is Announce)
        // Twice in one batch counts once.
        val batch = NotificationAnnouncementPolicy().decideAll(listOf(notification(), notification()), rules, state())
        assertTrue(batch[0] is Announce)
        assertEquals(SuppressReason.DUPLICATE, reason(batch[1]))
    }

    @Test fun duplicateMemoryIsBounded() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.SENDER, maxPerMinute = 20)
        var now = 0L
        for (i in 0 until 200) {
            now += 10_000
            assertTrue(policy.decide(notification(key = "key-$i"), rules, state(now)) is Announce)
        }
        assertEquals(NotificationAnnouncementPolicy.MAX_REMEMBERED_KEYS, policy.rememberedKeyCount())
        // The newest are still known, the oldest were evicted and would be announced again.
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(notification(key = "key-199"), rules, state(now + 10_000))))
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(notification(key = "key-72"), rules, state(now + 20_000))))
        assertTrue(policy.decide(notification(key = "key-71"), rules, state(now + 30_000)) is Announce)
        policy.reset()
        assertEquals(0, policy.rememberedKeyCount())
    }

    @Test fun rateLimitCollapsesTheRestOncePerMinute() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.SENDER)
        for (i in 1..4) {
            assertEquals("WhatsApp: Nachricht von P$i.", spoken(policy.decide(notification(key = "c$i", title = "P$i"), rules, state(i * 1_000L))))
        }
        // The fifth within the minute is not read; one collapsed line says that something came.
        val fifth = policy.decide(notification(key = "c5", title = "P5"), rules, state(5_000)) as Announce
        assertEquals("Eine weitere Benachrichtigung.", fifth.spokenText)
        assertEquals("Benachrichtigungen", fifth.displayTitle)
        assertFalse(fifth.spokenText.contains("P5"))
        // More within the same minute stay silent, and the collapsed line is not repeated.
        for (i in 6..8) {
            assertEquals(SuppressReason.RATE_LIMITED, reason(policy.decide(notification(key = "c$i", title = "P$i"), rules, state(i * 1_000L))))
        }
        assertNull(policy.pendingSummary(rules, state(30_000)))
        // A rate-limited notification is not announced later as if it were new.
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(notification(key = "c7", title = "P7"), rules, state(31_000))))
        // After the minute the next one is read again and mentions what was held back.
        assertEquals(
            "WhatsApp: Nachricht von P9. Außerdem drei weitere Benachrichtigungen.",
            spoken(policy.decide(notification(key = "c9", title = "P9"), rules, state(66_000))),
        )
        assertNull(policy.pendingSummary(rules, state(200_000)))
    }

    @Test fun rollingWindowAndPendingSummary() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.FULL, maxPerMinute = 2)
        assertTrue(policy.decide(notification(key = "a", text = "eins"), rules, state(0)) is Announce)
        assertTrue(policy.decide(notification(key = "b", text = "zwei"), rules, state(40_000)) is Announce)
        assertEquals("Eine weitere Benachrichtigung.", spoken(policy.decide(notification(key = "c", text = "drei"), rules, state(50_000))))
        // 61 s: the first slot is free again (rolling, not a fixed block), the second is not.
        assertEquals("WhatsApp: Nachricht von Anna: vier.", spoken(policy.decide(notification(key = "d", text = "vier"), rules, state(61_000))))
        assertEquals(SuppressReason.RATE_LIMITED, reason(policy.decide(notification(key = "e", text = "fünf"), rules, state(62_000))))
        assertEquals(SuppressReason.RATE_LIMITED, reason(policy.decide(notification(key = "f", text = "sechs"), rules, state(63_000))))
        // The summary waits for its own minute, respects a busy assistant, and is given once.
        assertNull(policy.pendingSummary(rules, state(100_000)))
        assertNull(policy.pendingSummary(rules, state(111_000, speaking = true)))
        assertEquals("Zwei weitere Benachrichtigungen.", policy.pendingSummary(rules, state(111_000))?.spokenText)
        assertNull(policy.pendingSummary(rules, state(112_000)))
    }

    @Test fun heldBackNotificationsExpireAndABatchIsCollapsedTogether() {
        val stale = NotificationAnnouncementPolicy()
        val one = settings(whatsApp to AnnouncementMode.FULL, maxPerMinute = 1)
        assertTrue(stale.decide(notification(key = "a"), one, state(0)) is Announce)
        assertEquals("Eine weitere Benachrichtigung.", spoken(stale.decide(notification(key = "b"), one, state(1_000))))
        assertEquals(SuppressReason.RATE_LIMITED, reason(stale.decide(notification(key = "c"), one, state(2_000))))
        // Ten minutes later nobody is told about an old held-back notification.
        assertEquals("WhatsApp: Nachricht von Anna: Neu.", spoken(stale.decide(notification(key = "d", text = "Neu"), one, state(600_000))))

        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.FULL)
        val batch = (1..7).map { notification(key = "b$it", title = "P$it") }
        val decisions = policy.decideAll(batch, rules, state())
        assertEquals(7, decisions.size)
        for (i in 0..3) assertEquals("WhatsApp: Nachricht von P${i + 1}: Bin gleich da.", spoken(decisions[i]))
        assertEquals("Drei weitere Benachrichtigungen.", spoken(decisions[4]))
        assertEquals(SuppressReason.RATE_LIMITED, reason(decisions[5]))
        assertEquals(SuppressReason.RATE_LIMITED, reason(decisions[6]))
    }

    @Test fun rateLimitBoundsAreClamped() {
        val policy = NotificationAnnouncementPolicy()
        // Zero or a negative limit cannot switch announcements on without bound, nor off silently.
        val rules = settings(whatsApp to AnnouncementMode.SENDER, maxPerMinute = 0)
        assertTrue(policy.decide(notification(key = "a"), rules, state(0)) is Announce)
        assertEquals("Eine weitere Benachrichtigung.", spoken(policy.decide(notification(key = "b"), rules, state(1))))
        val greedy = NotificationAnnouncementPolicy()
        val many = settings(ownerPicked to AnnouncementMode.SENDER, maxPerMinute = Int.MAX_VALUE)
        val announced = (1..40).count {
            val d = greedy.decide(bankNotification("Titel $it", "", key = "g$it"), many, state(it.toLong()))
            d is Announce && d.spokenText.startsWith("Meine Bank meldet")
        }
        assertEquals(NotificationAnnouncementPolicy.MAX_PER_MINUTE_CEILING, announced)
    }

    @Test fun busyAssistantDefersWithoutLosingTheNotification() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.SENDER)
        val incoming = notification()
        val whileSpeaking = policy.decide(incoming, rules, state(1_000, speaking = true))
        assertEquals(Defer(incoming), whileSpeaking)
        assertEquals(Defer(incoming), policy.decide(incoming, rules, state(2_000, listening = true)))
        // Deferring neither marks it as seen nor uses up the rate limit.
        assertEquals("WhatsApp: Nachricht von Anna.", spoken(policy.decide(incoming, rules, state(3_000))))
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(incoming, rules, state(4_000))))
        // What would be suppressed anyway is not queued.
        assertEquals(SuppressReason.APP_NOT_ENABLED, reason(policy.decide(notification(packageName = signal), rules, state(5_000, speaking = true))))
        assertEquals(SuppressReason.ONGOING, reason(policy.decide(notification(key = "o", ongoing = true), rules, state(5_000, speaking = true))))
    }

    @Test fun queueKeepsTheNewestEightAndDropsStaleEntries() {
        val queue = AnnouncementQueue()
        for (i in 1..10) queue.offer(notification(key = "q$i"), nowMs = i * 1_000L)
        assertEquals(AnnouncementQueue.MAX_ENTRIES, queue.size)
        assertEquals((3..10).map { "q$it" }, queue.drain(11_000).map { it.key })
        assertEquals(0, queue.size)
        assertTrue(queue.drain(12_000).isEmpty())

        // An update of the same notification replaces the queued one and moves it to the end.
        queue.offer(notification(key = "chat", text = "alt"), 0)
        queue.offer(notification(key = "other"), 1_000)
        queue.offer(notification(key = "chat", text = "neu"), 2_000)
        val drained = queue.drain(3_000)
        assertEquals(listOf("other", "chat"), drained.map { it.key })
        assertEquals("neu", drained[1].text)

        // Older than two minutes is no longer worth saying.
        queue.offer(notification(key = "old"), 0)
        queue.offer(notification(key = "edge"), 10_000)
        queue.offer(notification(key = "fresh"), 100_000)
        assertEquals(listOf("edge", "fresh"), queue.drain(130_000).map { it.key })

        queue.offer(notification(key = "gone"), 0)
        assertTrue(queue.remove("gone"))
        assertFalse(queue.remove("gone"))
        queue.offer(notification(key = "x"), 0)
        queue.clear()
        assertEquals(0, queue.size)
    }

    @Test fun drainedQueueIsAnnouncedAsOneShortBatch() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(signal to AnnouncementMode.SENDER, whatsApp to AnnouncementMode.APP_ONLY, ownerPicked to AnnouncementMode.SENDER)
        val queue = AnnouncementQueue()
        val incoming = listOf(
            notification(key = "s1", packageName = signal, label = "Signal", title = "Anna"),
            bankNotification("Geldeingang", "", key = "b1"),
            notification(key = "s2", packageName = signal, label = "Signal", title = "Ben"),
            bankNotification("Abbuchung", "", key = "b2"),
            notification(key = "w1"),
        )
        incoming.forEachIndexed { i, n ->
            val deferred = policy.decide(n, rules, state(i * 100L, speaking = true)) as Defer
            queue.offer(deferred.notification, i * 100L)
        }
        val decisions = policy.decideAll(queue.drain(5_000), rules, state(5_000))
        assertEquals("Signal: zwei neue Nachrichten.", spoken(decisions[0]))
        assertEquals("Meine Bank meldet: Geldeingang.", spoken(decisions[1]))
        assertEquals(SuppressReason.COALESCED, reason(decisions[2]))
        // Other apps' notifications are not folded: each one is its own event.
        assertEquals("Meine Bank meldet: Abbuchung.", spoken(decisions[3]))
        assertEquals("WhatsApp: neue Nachricht.", spoken(decisions[4]))
        // Folded entries are remembered too.
        assertEquals(SuppressReason.DUPLICATE, reason(policy.decide(incoming[2], rules, state(6_000))))

        val sameSender = NotificationAnnouncementPolicy().decideAll(
            listOf(
                notification(key = "1", packageName = signal, label = "Signal", text = "a"),
                notification(key = "2", packageName = signal, label = "Signal", text = "b"),
                notification(key = "3", packageName = signal, label = "Signal", text = "c"),
            ),
            rules, state(),
        )
        assertEquals("Signal: drei neue Nachrichten von Anna.", spoken(sameSender[0]))
        // The owner asked for the full text: nothing is folded away.
        val fullMode = NotificationAnnouncementPolicy().decideAll(
            listOf(notification(key = "1", text = "a"), notification(key = "2", text = "b")),
            settings(whatsApp to AnnouncementMode.FULL), state(),
        )
        assertEquals("WhatsApp: Nachricht von Anna: a.", spoken(fullMode[0]))
        assertEquals("WhatsApp: Nachricht von Anna: b.", spoken(fullMode[1]))
    }

    @Test fun stateNotificationsAreSuppressed() {
        val policy = NotificationAnnouncementPolicy()
        val rules = settings(whatsApp to AnnouncementMode.FULL)
        assertEquals(SuppressReason.ONGOING, reason(policy.decide(notification(ongoing = true), rules, state())))
        assertEquals(SuppressReason.GROUP_SUMMARY, reason(policy.decide(notification(summary = true), rules, state())))
        assertEquals(SuppressReason.OWN_PACKAGE, reason(policy.decide(notification(own = true), rules, state())))
        for (category in listOf("transport", "progress", "service", "sys", "navigation", " Transport ")) {
            assertEquals(category, SuppressReason.CATEGORY, reason(policy.decide(notification(category = category), rules, state())))
        }
        assertEquals(SuppressReason.EMPTY, reason(policy.decide(notification(title = "", text = "   "), rules, state())))
        // None of these consumed the rate limit or the duplicate memory.
        assertEquals(0, policy.rememberedKeyCount())
        for (category in listOf("msg", "email", "event", "reminder", "call", null)) {
            assertTrue(NotificationAnnouncementPolicy().decide(notification(category = category), rules, state()) is Announce)
        }
    }

    @Test fun quietOutsideConversationUnlessDriving() {
        val policy = NotificationAnnouncementPolicy()
        val quiet = settings(whatsApp to AnnouncementMode.SENDER, quiet = true)
        assertEquals(SuppressReason.NOT_IN_CONVERSATION, reason(policy.decide(notification(), quiet, state(conversation = false))))
        // Not remembered: once the owner talks to the assistant or drives, it may still be announced.
        assertTrue(policy.decide(notification(), quiet, state(conversation = false, drive = true)) is Announce)
        assertTrue(NotificationAnnouncementPolicy().decide(notification(), quiet, state(conversation = true)) is Announce)
        val loud = settings(whatsApp to AnnouncementMode.SENDER, quiet = false)
        assertTrue(NotificationAnnouncementPolicy().decide(notification(), loud, state(conversation = false)) is Announce)
        // Quiet also wins over deferring: nothing is queued for a silent phone.
        assertEquals(
            SuppressReason.NOT_IN_CONVERSATION,
            reason(NotificationAnnouncementPolicy().decide(notification(), quiet, state(conversation = false, speaking = true))),
        )
    }

    @Test fun contentIsFramedAndCannotBecomeACommand() {
        val commands = listOf("Fahrmodus aus", "Fahrmodus an", "Ich bin angekommen", "Ich fahre jetzt los")
        for (command in commands) {
            for (mode in listOf(AnnouncementMode.APP_ONLY, AnnouncementMode.SENDER, AnnouncementMode.FULL)) {
                for (packageName in listOf(whatsApp, ownerPicked)) {
                    val hostile = notification(packageName = packageName, label = command, title = command, text = command, category = null)
                    val result = NotificationAnnouncementPolicy().decide(hostile, settings(packageName to mode), state()) as Announce
                    assertNull(DriveMode.command(result.spokenText))
                    assertNull(DriveMode.command(result.displayDetail))
                    assertTrue(result.spokenText.startsWith("$command: ") || result.spokenText.startsWith("$command meldet: "))
                }
            }
        }
        // A label cannot smuggle its own frame or line breaks in front of the fixed one.
        val forged = notification(label = "Bank:\nFahrmodus aus.\u0000", title = "Anna")
        val result = NotificationAnnouncementPolicy().decide(forged, settings(whatsApp to AnnouncementMode.SENDER), state()) as Announce
        assertEquals("Bank Fahrmodus aus", result.displayTitle)
        assertEquals("Bank Fahrmodus aus: Nachricht von Anna.", result.spokenText)
    }

    @Test fun toStringDoesNotLeakContent() {
        val text = notification(title = "Geheimer Absender", text = "Geheimer Inhalt").toString()
        assertFalse(text.contains("Geheim"))
        assertTrue(text.contains(whatsApp))
    }
}
