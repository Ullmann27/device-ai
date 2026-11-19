package at.heinz.deviceai.sending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerSendIntentParserTest {
    private fun order(utterance: String): OwnerSendOrder =
        (OwnerSendIntentParser.parse(utterance) as OwnerSendIntentParser.Result.Order).order

    private fun refused(utterance: String): String =
        (OwnerSendIntentParser.parse(utterance) as OwnerSendIntentParser.Result.Refused).message

    @Test fun theOwnersExamplesAreUnderstoodWithHisOwnWording() {
        order("schreib Anna auf WhatsApp, ich komme später").let {
            assertEquals("Anna", it.recipient); assertEquals("Ich komme später", it.text)
            assertEquals(TextOrigin.DICTATED, it.origin); assertFalse(it.draftOnly)
        }
        order("Schick Anna eine WhatsApp: Bin in 10 Minuten da").let {
            assertEquals("Anna", it.recipient); assertEquals("Bin in 10 Minuten da", it.text)
        }
        order("sag dem Thomas per WhatsApp, dass ich später komme").let {
            assertEquals("Thomas", it.recipient); assertEquals("Ich komme später", it.text)
            assertEquals(TextOrigin.MINIMAL_TRANSFORM, it.origin)
        }
    }

    @Test fun spokenFormsWithoutPunctuationKeepRecipientAndTextApart() {
        order("schreib Anna auf WhatsApp ich komme später").let {
            assertEquals("Anna", it.recipient); assertEquals("Ich komme später", it.text)
        }
        order("schick dem Thomas eine WhatsApp Nachricht bin gleich da").let {
            assertEquals("Thomas", it.recipient); assertEquals("Bin gleich da", it.text)
        }
        order("Kannst du bitte Anna Beispiel per WhatsApp schreiben, dass ich später komme").let {
            assertEquals("Anna Beispiel", it.recipient); assertEquals("Ich komme später", it.text)
        }
        order("Könntest du dem Thomas auf WhatsApp schreiben").let { assertEquals("Thomas", it.recipient); assertNull(it.text) }
        order("schreib Anna eine Nachricht auf WhatsApp wir treffen uns um acht").let {
            assertEquals("Anna", it.recipient); assertEquals("Wir treffen uns um acht", it.text)
        }
    }

    @Test fun recipientBehindTheAppIsCutAtASeparatorOrLeftToTheContacts() {
        order("Schick eine WhatsApp an Anna Beispiel: Bin unterwegs").let {
            assertEquals("Anna Beispiel", it.recipient); assertEquals("Bin unterwegs", it.text)
        }
        order("schick eine WhatsApp an Thomas dass ich später komme").let {
            assertEquals("Thomas", it.recipient); assertEquals("Ich komme später", it.text)
        }
        order("schick eine WhatsApp an Anna Beispiel ich komme später").let {
            assertNull(it.recipient); assertNull(it.text); assertEquals("Anna Beispiel ich komme später", it.unsplit)
        }
    }

    @Test fun aSpokenFamilyRecipientAfterWhatsAppIsNotTreatedAsTheMessage() {
        val request = order("schreib bitte in WhatsApp meinen Papa guten Morgen")
        assertEquals("meinen Papa guten Morgen", request.unsplit)
        assertNull(request.text)
        assertEquals("Papa", OwnerSendIntentParser.recipientAnswer(
            "ich habe dir zum dreimal gesagt der Name heißt Papa"))
        assertNull(OwnerSendIntentParser.recipientAnswer("WhatsApp"))
        assertEquals(OwnerSendIntentParser.REFUSED_SEVERAL,
            refused("Schreib in WhatsApp meinem Papa und Mama guten Morgen"))
        assertEquals(OwnerSendIntentParser.REFUSED_NEGATED,
            refused("Schreib in WhatsApp meinem Papa nicht guten Morgen"))
    }

    @Test fun austrianColloquialFormsAreUnderstoodAndStayVerbatim() {
        order("schreib da Anna auf WhatsApp, i kumm a bissl später").let {
            assertEquals("Anna", it.recipient); assertEquals("I kumm a bissl später", it.text)
            assertEquals(TextOrigin.DICTATED, it.origin)
        }
        order("schick dem Thomas a WhatsApp, dass i heit später kumm").let {
            assertEquals("Thomas", it.recipient); assertEquals("I kumm heit später", it.text)
        }
        order("geh schreib der Mama gschwind auf WhatsApp i bin glei do").let {
            assertEquals("Mama", it.recipient); assertEquals("I bin glei do", it.text)
        }
        order("sag der Anna per WhatsApp Bescheid, dass wir schon unterwegs sind").let {
            assertEquals("Wir sind schon unterwegs", it.text)
        }
    }

    @Test fun indirectSpeechIsOnlyTransformedWhenTheWordsStayTheOwners() {
        assertEquals("Ich kann nicht kommen", IndirectSpeech.toDirect("ich nicht kommen kann"))
        assertEquals("Ich bin gestern beim Arzt gewesen", IndirectSpeech.toDirect("ich gestern beim Arzt gewesen bin"))
        assertEquals("Ich komme", IndirectSpeech.toDirect("ich komme"))
        // Separable verb, third-person pronoun (change of perspective), second clause, other subject: asked for instead.
        assertNull(IndirectSpeech.toDirect("ich dich später anrufe"))
        assertNull(IndirectSpeech.toDirect("ich sie morgen sehe"))
        assertNull(IndirectSpeech.toDirect("ich später komme, weil der Zug steht"))
        assertNull(IndirectSpeech.toDirect("ich später komme und Brot bringe"))
        assertNull(IndirectSpeech.toDirect("er mich anrufen soll"))
        assertNull(IndirectSpeech.toDirect("das Essen fertig ist"))
        order("sag Thomas per WhatsApp, dass er mich anrufen soll").let {
            assertEquals("Thomas", it.recipient); assertNull(it.text); assertNull(it.modelBrief)
        }
    }

    @Test fun aWishForOwnWordingIsMarkedAsModelTextAndNeverAsDictated() {
        order("schreib Anna auf WhatsApp was Nettes").let {
            assertNull(it.text); assertEquals("was Nettes", it.modelBrief); assertEquals(TextOrigin.DERIVED, it.origin)
        }
        order("schick Thomas per WhatsApp einen lieben Geburtstagsgruß").let { assertEquals("einen lieben Geburtstagsgruß", it.modelBrief) }
        // Without an article these are simply the owner's words.
        order("sag der Anna per WhatsApp liebe Grüße").let { assertEquals("Liebe Grüße", it.text); assertNull(it.modelBrief) }
    }

    @Test fun missingPartsAreAskedForInsteadOfGuessed() {
        order("schreib Anna auf WhatsApp").let { assertEquals("Anna", it.recipient); assertNull(it.text) }
        order("Schick eine WhatsApp: Bin gleich da").let { assertNull(it.recipient); assertEquals("Bin gleich da", it.text) }
        order("schreib ihr auf WhatsApp, ich komme später").let { assertNull(it.recipient); assertEquals("Ich komme später", it.text) }
    }

    @Test fun explicitDraftWishesStayDrafts() {
        assertTrue(order("Bereite eine Nachricht an Anna in WhatsApp vor: Bin gleich da").draftOnly)
        assertTrue(order("Schreib einen Entwurf an Anna auf WhatsApp: Bin gleich da").draftOnly)
        order("Bereite eine Nachricht an Anna in WhatsApp vor: Bin gleich da").let {
            assertEquals("Anna", it.recipient); assertEquals("Bin gleich da", it.text)
        }
        assertFalse(order("Schreibe Anna in WhatsApp: Bin unterwegs").draftOnly)
    }

    @Test fun negatedSeveralRecipientsAndGroupsAreRefused() {
        assertEquals(OwnerSendIntentParser.REFUSED_NEGATED, refused("Schreib Anna nicht auf WhatsApp, dass ich komme"))
        assertEquals(OwnerSendIntentParser.REFUSED_NEGATED, refused("schick keine WhatsApp an Anna: Hallo"))
        assertEquals(OwnerSendIntentParser.REFUSED_SEVERAL, refused("schreib Anna und Thomas auf WhatsApp, ich komme später"))
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, refused("schreib in die Familiengruppe auf WhatsApp, ich komme später"))
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, refused("schick eine WhatsApp an alle: Party um acht"))
        assertEquals(OwnerSendIntentParser.REFUSED_GROUP, refused("schreib allen auf WhatsApp, Party um acht"))
        // A negation inside the dictated text is text.
        assertEquals("Ich komme heute nicht", order("schreib Anna auf WhatsApp, ich komme heute nicht").text)
    }

    @Test fun readingQuestionsNewsAndOtherAppOrdersNeverFire() {
        listOf(
            "Nachricht von Anna vorlesen",
            "Lies mir die WhatsApp von Anna vor",
            "Was hat Anna auf WhatsApp geschrieben?",
            "Hat Thomas mir auf WhatsApp geschrieben?",
            "Soll ich Anna auf WhatsApp schreiben?",
            "Wie schreibe ich Anna auf WhatsApp?",
            "Fass mir die Nachrichten von heute zusammen",
            "Schick mir die Nachrichten von heute",
            "Öffne WhatsApp",
            "Such in WhatsApp nach Anna",
            "Schreib mir auf, was Anna auf WhatsApp geschrieben hat",
            "Sag mir was auf WhatsApp los ist",
            "Schreib Anna eine Mail: schau bitte auf WhatsApp",
            "Schreib Anna, sie soll auf WhatsApp schauen",
            "Schick das per Mail an anna@example.org",
            "Schreib eine Zusammenfassung",
            "Kannst du mir sagen, wer auf WhatsApp geschrieben hat",
            "Kannst du WhatsApp öffnen"
        ).forEach { assertNull(it, OwnerSendIntentParser.parse(it)) }
    }

    @Test fun textLimitsAndControlCharactersAreNotSendable() {
        assertEquals(OwnerSendIntentParser.REFUSED_TEXT, refused("schreib Anna auf WhatsApp: " + "a".repeat(2_050)))
        assertNull(OwnerSendIntentParser.parse("schreib Anna auf WhatsApp: Hallo\u0007"))
    }

    @Test fun answersToOpenQuestionsAreClassifiedDeterministically() {
        listOf("ja", "Ja bitte", "passt so", "senden", "ja, schick es ab", "abschicken").forEach { assertTrue(it, OwnerSendIntentParser.isYes(it)) }
        listOf("vielleicht", "ja aber später", "nein", "schick es an Thomas", "ja und schreib dazu Bussi").forEach {
            assertFalse(it, OwnerSendIntentParser.isYes(it)) }
        listOf("nein", "abbrechen", "doch nicht", "vergiss es").forEach { assertTrue(it, OwnerSendIntentParser.isCancel(it)) }
        listOf("Wie spät ist es?", "Öffne Chrome", "Nachricht von Anna vorlesen", "Was gibt es Neues").forEach {
            assertTrue(it, OwnerSendIntentParser.addressesContinuum(it)) }
        assertFalse(OwnerSendIntentParser.addressesContinuum("Bin in zehn Minuten da"))
        assertEquals("Anna Beispiel", OwnerSendIntentParser.recipientAnswer("der Anna Beispiel"))
        assertNull(OwnerSendIntentParser.recipientAnswer("Wie spät ist es"))
    }
}
