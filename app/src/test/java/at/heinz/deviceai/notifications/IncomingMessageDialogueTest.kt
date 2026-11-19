package at.heinz.deviceai.notifications

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation about an incoming message (owner orders 18. and 19.11.2026) on a fake host with a fake clock:
 * the personal question, yes / no / later / silence, reading, the answer through a fake send lane, busy moments,
 * quiet hours, „Stör mich jetzt nicht“, tone and name. No Android, no model, no real app.
 */
class IncomingMessageDialogueTest {
    private fun IncomingRig.anna(text: String = "Kommst du heute um fünf?", time: Long = 1L) = arrive(chat("Anna", text to time))

    @Test fun theOwnersConversationFromTheQuestionToTheSentAnswer() {
        val rig = IncomingRig()
        rig.anna()
        assertEquals(listOf("Hey Heinz, darf ich dich kurz stören? Du hast eine Nachricht von Anna auf WhatsApp bekommen. " +
            "Soll ich sie dir vorlesen?"), rig.host.announced)
        assertEquals(listOf("Nachricht wartet · WhatsApp:WAITING"), rig.host.steps())
        assertTrue("a tap answer is offered as well", rig.host.cards.isNotEmpty())
        assertTrue(rig.dialogue.awaitingAnswer)

        assertTrue(rig.say("Ja"))
        assertEquals("Anna schreibt: „Kommst du heute um fünf?“ Möchtest du antworten?", rig.host.replies.last())
        assertTrue(rig.host.cards.isEmpty())
        assertEquals(listOf("Nachricht gemeldet · WhatsApp:DONE", "Nachricht vorgelesen:DONE", "Antwort offen:WAITING"), rig.host.steps())

        assertTrue(rig.say("ja bitte"))
        assertEquals("Gern. Was soll ich Anna antworten?", rig.host.replies.last())
        assertTrue(rig.say("ich komme um fünf, bis gleich"))
        assertEquals("Ich antworte Anna auf WhatsApp: „Ich komme um fünf, bis gleich“. Soll ich das so senden?", rig.host.replies.last())
        assertTrue("nothing is sent before his yes", rig.host.sent.isEmpty())

        assertTrue(rig.say("Ja, senden"))
        assertEquals(listOf(FakeIncomingHost.Sent("Ja, senden", IncomingReplyChannel.WHATSAPP, "Anna", "", "Ich komme um fünf, bis gleich")), rig.host.sent)
        assertEquals("Antwort an den Sendeweg übergeben:ACTIVE", rig.host.steps().last())
        // The send lane reports in its own words; its typed outcome ends the work-window steps.
        rig.dialogue.replySettled(sent = true, waitsForOwner = false)
        assertEquals("Antwort gesendet:DONE", rig.host.steps().last())
        assertTrue(rig.host.staged.last() is CockpitAnimationEvent.TurnEnded)
        assertTrue(rig.inbox.open().isEmpty())
        assertEquals(listOf("Ja", "ja bitte", "ich komme um fünf, bis gleich", "Ja, senden"), rig.host.turns)
        assertTrue("the log names the app, never sender or text", rig.host.activity.none { "Anna" in it || "fünf" in it })
    }

    @Test fun noLaterAndSilenceNeverSendAnythingAndLaterKeepsItInTheOpenList() {
        val no = IncomingRig()
        no.anna()
        assertTrue(no.say("nein"))
        assertEquals(IncomingMessageWording.NOT_READ, no.host.replies.last())
        assertTrue(no.inbox.open().isEmpty())

        val later = IncomingRig()
        later.anna()
        assertTrue(later.say("später"))
        assertEquals(IncomingMessageWording.KEPT, later.host.replies.last())
        assertEquals(IncomingState.OPEN, later.inbox.open().single().state)
        assertFalse(later.dialogue.awaitingAnswer)
        later.time.advance(10 * 60_000)
        assertEquals("„später“ is not asked again by itself", 1, later.host.announced.size)
        assertTrue(later.say("Jarvis, was ist noch offen?"))
        assertEquals("Offen ist eine Nachricht von Anna auf WhatsApp. Soll ich sie vorlesen?", later.host.replies.last())
        assertTrue(later.say("ja"))
        assertTrue(later.host.replies.last().startsWith("Anna schreibt: „Kommst du heute um fünf?“"))

        val silence = IncomingRig()
        silence.anna()
        silence.time.advance(IncomingMessageDialogue.OFFER_WINDOW_MS + 1_000)
        assertFalse(silence.dialogue.awaitingAnswer)
        assertEquals("unanswered: it stays in the list", IncomingState.OPEN, silence.inbox.open().single().state)
        assertFalse("a late yes belongs to nobody", silence.say("ja"))
        assertTrue(silence.host.replies.isEmpty() && silence.host.sent.isEmpty())

        // After reading: no, later, silence, a refused repetition – nothing is ever sent.
        listOf("nein", "später").forEach { answer ->
            val rig = IncomingRig()
            rig.anna(); rig.say("ja")
            assertTrue(rig.say(answer))
            assertTrue(rig.host.sent.isEmpty())
            assertEquals(answer, if (answer == "später") 1 else 0, rig.inbox.open().size)
        }
        val confirm = IncomingRig()
        confirm.anna(); confirm.say("ja"); confirm.say("ja"); confirm.say("Bin unterwegs")
        confirm.time.advance(IncomingMessageDialogue.ANSWER_WINDOW_MS + 1_000)
        assertFalse(confirm.say("ja"))
        assertTrue(confirm.host.sent.isEmpty())
        assertTrue(confirm.host.activity.contains("Nachricht · WhatsApp · keine Antwort gesendet"))
        val refused = IncomingRig()
        refused.anna(); refused.say("ja"); refused.say("ja"); refused.say("Bin unterwegs")
        assertTrue(refused.say("nein"))
        assertEquals(IncomingMessageWording.NOTHING_SENT, refused.host.replies.last())
        assertTrue(refused.host.sent.isEmpty() && refused.inbox.open().isEmpty())
    }

    @Test fun anAnswerCanBeCorrectedOrGivenInOneSentence() {
        val rig = IncomingRig()
        rig.anna(); rig.say("ja")
        assertTrue(rig.say("Ja, schreib ihr, ich bin in zehn Minuten da"))
        assertEquals("Ich antworte Anna auf WhatsApp: „Ich bin in zehn Minuten da“. Soll ich das so senden?", rig.host.replies.last())
        assertTrue(rig.say("anders"))
        assertEquals("In Ordnung. Wie soll die Antwort lauten?", rig.host.replies.last())
        assertTrue(rig.say("Antworte: wird später"))
        assertEquals("Ich antworte Anna auf WhatsApp: „Wird später“. Soll ich das so senden?", rig.host.replies.last())
        assertTrue(rig.say("passt"))
        assertEquals("Wird später", rig.host.sent.single().text)
        // The lane asks back (two contacts called Anna): the steps wait, and its final outcome ends them.
        rig.dialogue.replySettled(sent = false, waitsForOwner = true)
        assertEquals("Rückfrage des Sendewegs:WAITING", rig.host.steps().last())
        rig.dialogue.replySettled(sent = false, waitsForOwner = false)
        assertEquals("Antwort nicht gesendet:BLOCKED", rig.host.steps().last())
    }

    @Test fun aSendLaneThatDoesNotTakeTheAnswerIsSaidPlainly() {
        val rig = IncomingRig()
        rig.host.sendTakes = false
        rig.anna(); rig.say("ja"); rig.say("ja"); rig.say("Bin unterwegs")
        assertTrue(rig.say("ja"))
        assertEquals(IncomingMessageWording.NOT_HANDED, rig.host.replies.last())
        assertTrue(rig.host.sent.isEmpty() && rig.inbox.open().isEmpty())
        // A mail without the Gmail grant: the reason is named.
        val mail = IncomingRig()
        mail.host.sendTakes = false
        mail.arrive(mail("Anna Berger", "Fotos vom Wochenende", "Hallo, anbei die Bilder."))
        mail.say("ja"); mail.say("ja"); mail.say("Danke dir")
        assertTrue(mail.say("ja"))
        assertEquals(IncomingMessageWording.GMAIL_GRANT, mail.host.replies.last())
    }

    @Test fun itNeverInterruptsAndOffersAtTheNextQuietMoment() {
        val rig = IncomingRig()
        rig.host.quietNow = false // a job runs, an approval is open, or the owner is typing
        rig.anna()
        rig.time.advance(60_000)
        assertTrue("nothing is said while he is busy", rig.host.announced.isEmpty())
        assertEquals(1, rig.inbox.waiting().size)
        rig.host.quietNow = true
        rig.time.advance(IncomingMessageDialogue.RETRY_MS + 100)
        assertEquals(listOf("Heinz, während du beschäftigt warst, kam eine Nachricht von Anna auf WhatsApp – soll ich sie dir vorlesen?"),
            rig.host.announced)

        // He is speaking (the announcement cannot start): it is tried again, not dropped.
        val speaking = IncomingRig()
        speaking.host.canAnnounce = false
        speaking.anna()
        speaking.time.advance(5_000)
        assertTrue(speaking.host.announced.isEmpty())
        speaking.host.canAnnounce = true
        speaking.time.advance(IncomingMessageDialogue.RETRY_MS + 100)
        assertEquals("a short wait is not „beschäftigt“", "Hey Heinz, darf ich dich kurz stören? Du hast eine Nachricht von Anna auf " +
            "WhatsApp bekommen. Soll ich sie dir vorlesen?", speaking.host.announced.single())

        // While its own question is open no second one is asked; the next message follows after the first is done.
        val two = IncomingRig()
        two.anna()
        two.arrive(chat("Tom", "Servus" to 5L))
        assertEquals(1, two.host.announced.size)
        assertTrue(two.say("nein"))
        two.time.advance(IncomingMessageDialogue.OFFER_GAP_MS + IncomingMessageDialogue.RETRY_MS)
        assertEquals(2, two.host.announced.size)
        assertTrue(two.host.announced.last(), two.host.announced.last().contains("eine Nachricht von Tom auf WhatsApp"))
    }

    @Test fun messagesThatArrivedWhileTheAppWasNotInFrontAreOfferedOnReturn() {
        val rig = IncomingRig(attached = false)
        rig.anna()
        rig.arrive(mail("Bezirksgericht Linz", "Ladung als Zeuge, GZ 12 C 345/26"))
        assertTrue("no question without the screen: the background shows a card instead", rig.host.announced.isEmpty())
        assertEquals(2, rig.background.size)
        rig.inFront = true
        rig.dialogue.attach()
        rig.time.advance(IncomingMessageDialogue.ATTACH_DELAY_MS + 100)
        assertEquals("Heinz, während du weg warst, kam eine Nachricht von Anna auf WhatsApp – soll ich sie dir vorlesen? " +
            "Außerdem wartet noch eine weitere Nachricht.", rig.host.announced.single())
        // „Vorlesen“ on the card of the other one: it is read at once, the open question returns to the list.
        val court = rig.inbox.open().last()
        rig.dialogue.action(court.id, read = true)
        assertEquals("E-Mail von Bezirksgericht Linz. Betreff: Ladung als Zeuge, GZ 12 C 345/26. Mehr zeigt die Benachrichtigung nicht. " +
            "Die ganze E-Mail steht in Gmail. Wenn du antworten möchtest, öffne bitte Gmail.", rig.host.replies.last())
        assertEquals("Anna", rig.inbox.open().single().sender)
        assertEquals(IncomingState.OPEN, rig.inbox.open().single().state)
        // „Später“ on a card; an unknown card says so.
        rig.dialogue.action(rig.inbox.open().single().id, read = false)
        assertEquals(1, rig.inbox.open().size)
        rig.dialogue.action(9_999L, read = true)
        assertEquals(IncomingMessageWording.NOT_KEPT, rig.host.replies.last())
    }

    @Test fun quietHoursAndTheDoNotDisturbSentenceHoldMessagesBack() {
        val night = IncomingRig()
        night.host.current = night.host.current.copy(quietHours = true)
        night.host.minute = 23 * 60 + 30
        night.anna()
        night.time.advance(5 * 60_000)
        assertTrue(night.host.announced.isEmpty())
        night.host.minute = 7 * 60 + 5
        night.time.advance(IncomingMessageDialogue.LONG_RETRY_MS + 100)
        assertEquals("Heinz, während der Ruhezeit kam eine Nachricht von Anna auf WhatsApp – soll ich sie dir vorlesen?",
            night.host.announced.single())

        val rig = IncomingRig()
        assertTrue(rig.say("Stör mich jetzt nicht"))
        assertEquals(IncomingMessageWording.DND_ON, rig.host.replies.last())
        assertTrue(rig.dialogue.doNotDisturb)
        rig.anna()
        rig.time.advance(10 * 60_000)
        assertTrue(rig.host.announced.isEmpty())
        assertTrue(rig.say("Was ist noch offen?"))
        assertTrue("he can still ask himself", rig.host.replies.last().startsWith("Offen ist eine Nachricht von Anna auf WhatsApp."))
        assertTrue(rig.say("später"))
        rig.arrive(chat("Tom", "Servus" to 9L))
        assertTrue(rig.say("Du darfst wieder"))
        assertEquals(IncomingMessageWording.DND_OFF, rig.host.replies.last())
        rig.time.advance(IncomingMessageDialogue.OFFER_GAP_MS + IncomingMessageDialogue.RETRY_MS)
        assertEquals("Heinz, während ich dich nicht stören sollte, kam eine Nachricht von Tom auf WhatsApp – soll ich sie dir vorlesen?",
            rig.host.announced.single())
        // „Stör mich nicht“ ends by itself.
        val forgotten = IncomingRig()
        forgotten.say("nicht stören")
        forgotten.time.advance(IncomingMessageDialogue.DO_NOT_DISTURB_MAX_MS + 1)
        assertFalse(forgotten.dialogue.doNotDisturb)
    }

    @Test fun nameAndToneComeFromTheHostAndEveryKindOfMessageHasItsSentence() {
        fun offer(profile: IncomingOwnerProfile, post: IncomingNotification, change: (IncomingRig) -> Unit = {}): String {
            val rig = IncomingRig()
            rig.host.profile = profile
            change(rig)
            rig.arrive(post)
            return rig.host.announced.single()
        }
        val anna = chat("Anna", "Hallo" to 1L)
        assertEquals("Hey, darf ich dich kurz stören? Du hast eine Nachricht von Anna auf WhatsApp bekommen. Soll ich sie dir vorlesen?",
            offer(IncomingOwnerProfile.DEFAULT, anna))
        assertEquals("Du hast eine Nachricht von Anna auf WhatsApp bekommen. Soll ich sie vorlesen?", offer(NamedProfile("Heinz", personalTone = false), anna))
        assertTrue("a name is data: only its letters are spoken", offer(NamedProfile("Heinz <b>1\u0007"), anna).startsWith("Hey Heinz b, darf ich"))
        assertTrue("a blank name is no name", offer(NamedProfile("  "), anna).startsWith("Hey, darf ich"))
        assertEquals("Hey Heinz, darf ich dich kurz stören? Du hast zwei Nachrichten von Anna auf WhatsApp bekommen. Soll ich sie dir vorlesen?",
            offer(NamedProfile("Heinz"), chat("Anna", "Hallo" to 1L, "Bist du da?" to 2L)))
        assertEquals("Hey Heinz, darf ich dich kurz stören? Du hast eine Nachricht von einer unbekannten Nummer auf WhatsApp bekommen. " +
            "Soll ich sie dir vorlesen?", offer(NamedProfile("Heinz"), posted(title = "+43 660 0000001", text = "Guten Tag")))
        assertEquals("Hey Heinz, darf ich dich kurz stören? Du hast eine E-Mail von Bezirksgericht Linz in Gmail bekommen. Soll ich sie dir vorlesen?",
            offer(NamedProfile("Heinz"), mail("Bezirksgericht Linz", "Ladung als Zeuge")))
        assertEquals("Hey Heinz, darf ich dich kurz stören? Du hast eine E-Mail von Österreichische Post in Gmail bekommen. " +
            "Ich kann nicht einordnen, ob sie wichtig ist. Soll ich sie dir vorlesen?", offer(NamedProfile("Heinz"), mail("Österreichische Post", "Ihr Paket kommt morgen")))
        assertEquals("Hey Heinz, darf ich dich kurz stören? Du hast eine Nachricht von Lisa in der Gruppe Familie auf WhatsApp bekommen. " +
            "Soll ich sie dir vorlesen?", offer(NamedProfile("Heinz"), chat("Lisa", "Wer holt Oma ab?" to 1L, group = "Familie")) {
                it.host.current = it.host.current.copy(groups = true)
            })
        // Locked device: an honest hint, no question, nothing kept.
        val hidden = IncomingRig()
        hidden.arrive(posted(title = "", text = "", category = null, details = NotificationDetails(NotificationSource.ACCESSIBILITY, contentHidden = true)))
        assertEquals("Hey Heinz, auf WhatsApp ist etwas für dich eingegangen. Absender und Inhalt hat mir Android dazu nicht gezeigt; " +
            "bei gesperrtem Gerät ist das immer so. Sag „Öffne WhatsApp“, wenn du nachsehen möchtest.", hidden.host.announced.single())
        assertFalse(hidden.dialogue.awaitingAnswer)
        assertTrue(hidden.inbox.open().isEmpty())
        assertEquals("Hey Heinz, du hast eine Nachricht von Anna auf WhatsApp bekommen. Öffne Continuum, wenn ich sie vorlesen soll.",
            IncomingMessageWording.background((IncomingRig(attached = false).arrive(anna) as IncomingIntake.Collected).item, NamedProfile("Heinz")))
    }

    @Test fun readingNamesCodesPasswordsAndLinksButDoesNotSpeakThem() {
        fun read(vararg posts: IncomingNotification, change: (IncomingRig) -> Unit = {}): String {
            val rig = IncomingRig()
            change(rig)
            posts.dropLast(1).forEach { rig.inbox.onNotification(it) }
            rig.arrive(posts.last())
            assertTrue(rig.say("ja"))
            return rig.host.replies.last()
        }
        val code = read(chat("Anna", "Dein Bestätigungscode lautet 482913" to 1L))
        assertEquals("Anna schickt eine Nachricht mit einem Code oder Passwort. Codes und Passwörter lese ich nicht vor; " +
            "du findest sie in WhatsApp. Möchtest du antworten?", code)
        assertFalse(read(chat("Anna", "Das WLAN-Passwort ist Sommer2026" to 1L)).contains("Sommer2026"))
        val link = read(chat("Anna", "Schau mal: https://beispiel.example/fotos?id=7 und schreib an anna@beispiel.example" to 1L))
        assertEquals("Anna schreibt: „Schau mal: ein Link und schreib an eine E-Mail-Adresse“ Links und Adressen lese ich nicht vor. " +
            "Möchtest du antworten?", link)
        assertEquals("Anna schreibt: „Hallo“ Dann: „Bist du da?“ Möchtest du antworten?", read(chat("Anna", "Hallo" to 1L, "Bist du da?" to 2L)))
        assertEquals("Die unbekannte Nummer schreibt: „Guten Tag“ Der Absender steht nicht mit Namen in deinen Kontakten; antworte bitte direkt in WhatsApp.",
            read(posted(title = "+43 660 0000001", text = "Guten Tag")))
        assertEquals("In der Gruppe Familie: Lisa schreibt: „Wer holt Oma ab?“ Tom schreibt: „Ich“ In Gruppen antworte ich nicht selbst.",
            read(chat("Lisa", "Wer holt Oma ab?" to 1L, "Ich" to 2L, group = "Familie", senders = listOf("Lisa", "Tom"))) {
                it.host.current = it.host.current.copy(groups = true)
            })
        assertEquals("E-Mail von Anna Berger. Betreff: Fotos vom Wochenende. Der Anfang lautet: „Hallo, anbei die Bilder.“ " +
            "Die ganze E-Mail steht in Gmail. Möchtest du antworten?", read(mail("Anna Berger", "Fotos vom Wochenende", "Hallo, anbei die Bilder.")))
        assertEquals("E-Mail von Microsoft-Konto-Team. Eine E-Mail mit einem Code oder Passwort. Die ganze E-Mail steht in Gmail. " +
            "Codes und Passwörter lese ich nicht vor; du findest sie in Gmail. Wenn du antworten möchtest, öffne bitte Gmail.",
            read(mail("Microsoft-Konto-Team", "Sicherheitscode für Ihr Konto", "Ihr Sicherheitscode lautet 482913.")))
        assertEquals("Tom schreibt: „Servus“ Antworten kann ich bisher nur in WhatsApp und Gmail; öffne dafür bitte Signal.",
            read(chat("Tom", "Servus" to 1L, app = "org.thoughtcrime.securesms")) {
                it.host.current = it.host.current.copy(apps = listOf("org.thoughtcrime.securesms"))
            })
    }

    @Test fun aMailFromAPersonIsAnsweredAsANewMailWithItsSubject() {
        val rig = IncomingRig()
        rig.arrive(mail("Anna Berger", "Fotos vom Wochenende", "Hallo, anbei die Bilder."))
        rig.say("ja"); rig.say("ja")
        assertEquals("Gern. Was soll ich Anna Berger antworten?", rig.host.replies.last())
        rig.say("Danke, die sind schön geworden")
        assertEquals("Ich schreibe Anna Berger über Gmail eine neue E-Mail mit dem Betreff „Re: Fotos vom Wochenende“: " +
            "„Danke, die sind schön geworden“. Soll ich das so senden?", rig.host.replies.last())
        assertTrue(rig.say("ja"))
        assertEquals(FakeIncomingHost.Sent("ja", IncomingReplyChannel.GMAIL, "Anna Berger", "Re: Fotos vom Wochenende",
            "Danke, die sind schön geworden"), rig.host.sent.single())
    }

    @Test fun theOpenListReasonsAndTheOwnersListsAreAnsweredInPlainSentences() {
        val rig = IncomingRig()
        assertTrue(rig.say("Was ist noch offen?"))
        assertEquals(IncomingMessageWording.NOTHING_OPEN, rig.host.replies.last())
        rig.host.quietNow = false
        rig.anna()
        rig.arrive(mail("Bezirksgericht Linz", "Ladung als Zeuge"))
        rig.inbox.onNotification(mail("Zalando", "Black Week: bis zu 40 % Rabatt"))
        assertTrue(rig.say("Welche Nachrichten sind offen?"))
        assertEquals("Offen sind zwei Einträge: erstens eine Nachricht von Anna auf WhatsApp; zweitens eine E-Mail von Bezirksgericht Linz " +
            "in Gmail. Soll ich die erste vorlesen? Du kannst auch „die zweite“ sagen.", rig.host.replies.last())
        assertTrue(rig.say("die zweite"))
        assertTrue(rig.host.replies.last().startsWith("E-Mail von Bezirksgericht Linz."))
        assertEquals("Anna", rig.inbox.open().single().sender)

        assertTrue(rig.say("Warum hast du das nicht gemeldet?"))
        assertEquals("Die Nachricht von Zalando (Gmail) habe ich nicht gemeldet: Die Nachricht sieht nach Werbung aus („black week“).", rig.host.replies.last())
        assertTrue(rig.say("Warum hast du die Mail von Bezirksgericht gemeldet?"))
        assertEquals("Die Nachricht von Bezirksgericht Linz (Gmail) habe ich gemeldet: Absender oder Betreff nennen ein Gericht oder eine " +
            "Behörde („bezirksgericht“).", rig.host.replies.last())
        assertTrue(rig.say("Warum hast du die Nachricht von Niemand nicht gemeldet?"))
        assertEquals(IncomingMessageWording.NO_DECISION, rig.host.replies.last())

        assertTrue(rig.say("Melde Zalando immer"))
        assertEquals(listOf("Zalando"), rig.host.current.always)
        assertTrue(rig.inbox.onNotification(mail("Zalando", "Deine Bestellung ist unterwegs", whenMs = 9L)) is IncomingIntake.Collected)
        assertTrue(rig.say("Melde Nachrichten von Zalando nie"))
        assertEquals(listOf("Zalando") to emptyList<String>(), rig.host.current.never to rig.host.current.always)
        assertEquals("Gut, Nachrichten von „Zalando“ melde ich ab jetzt nicht mehr.", rig.host.replies.last())
        assertTrue(rig.say("Lies die Nachricht von Anna vor"))
        assertTrue(rig.host.replies.last().startsWith("Anna schreibt:"))
        assertTrue(rig.say("nein"))
        assertTrue(rig.say("So etwas nie mehr melden"))
        assertEquals("the sender that was read last", listOf("Zalando", "Anna"), rig.host.current.never)
        assertTrue(rig.say("Lösche die offenen Nachrichten"))
        assertEquals(IncomingMessageWording.CLEARED, rig.host.replies.last())
        assertTrue(rig.inbox.open().isEmpty())
    }

    @Test fun otherSentencesKeepTheirRouteAndASwitchedOffFeatureTakesNothing() {
        val rig = IncomingRig()
        listOf("Schreib Anna auf WhatsApp, ich komme später", "Fass mir die Nachrichten von heute zusammen", "Öffne WhatsApp",
            "Lies das Dokument vor", "ja", "nein", "später", "Wie spät ist es?", "Lies die Nachricht von Anna vor").forEach {
            assertFalse(it, rig.say(it))
        }
        rig.anna()
        // While the question is open another order is not an answer; it ends the question when a lane takes it.
        assertFalse(rig.say("Öffne WhatsApp"))
        rig.host.acceptOwnerTurn("Öffne WhatsApp")
        assertFalse("the yes now belongs to whatever the other lane asked", rig.say("ja"))
        assertEquals(IncomingState.OPEN, rig.inbox.open().single().state)

        rig.host.current = rig.host.current.copy(enabled = false)
        listOf("Was ist noch offen?", "Stör mich jetzt nicht", "ja").forEach { assertFalse(it, rig.say(it)) }
        assertFalse(rig.dialogue.poll())
    }

    @Test fun aRecreatedScreenReturnsAnUnansweredQuestionToTheListAndEndsAHalfFinishedAnswer() {
        val rig = IncomingRig()
        rig.anna()
        rig.arrive(chat("Tom", "Servus" to 3L))
        assertEquals(IncomingState.OFFERED, rig.inbox.open().first().state)
        val recreated = IncomingMessageDialogue(rig.host, rig.inbox)
        recreated.attach()
        assertEquals(listOf(IncomingState.OPEN, IncomingState.WAITING), rig.inbox.open().map { it.state })
        assertNull(rig.inbox.open().first().held)
    }
}
