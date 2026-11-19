package at.heinz.deviceai.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings, the owner's sentences and single classifier rules of „Eingehende Nachrichten melden“. Pure JVM. */
class IncomingMessageSettingsTest {
    @Test fun thePrivateDefaultIsOffWithoutAppsAndUnreadableInputFallsBackToIt() {
        val defaults = IncomingMessageSettings()
        assertFalse(defaults.enabled || defaults.groups || defaults.silentNotifications || defaults.backgroundVoice)
        assertTrue(defaults.apps.isEmpty() && defaults.always.isEmpty() && defaults.never.isEmpty())
        assertEquals(UnclearHandling.BRIEF, defaults.unclear)
        assertTrue(defaults.quietHours && defaults.backgroundCard)
        listOf(null, "", "kein json", "{\"v\":99,\"enabled\":true}", "[]").forEach {
            assertEquals(it.toString(), defaults, IncomingMessageSettingsCodec.decode(it))
        }
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b", "com.google.android.gm"), IncomingMessagePreferences.SUGGESTED_APPS)
        assertTrue(IncomingMessagePreferences.EXPLANATION.contains("Benachrichtigungen der Apps, die du hier auswählst"))
        assertTrue(IncomingMessagePreferences.EXPLANATION.contains("Alles bleibt auf diesem Gerät"))
    }

    @Test fun settingsSurviveARoundTripAndAreBounded() {
        val chosen = IncomingMessageSettings(enabled = true, apps = listOf("com.whatsapp", "com.google.android.gm", "org.telegram.messenger"),
            groups = true, silentNotifications = true, unclear = UnclearHandling.SILENT, always = listOf("Bezirksgericht", "Ordination Dr. Steiner"),
            never = listOf("Newsletter"), quietHours = false, quietFromMinute = 21 * 60, quietUntilMinute = 6 * 60 + 30,
            backgroundCard = false, backgroundVoice = true)
        assertEquals(chosen, IncomingMessageSettingsCodec.decode(IncomingMessageSettingsCodec.encode(chosen)))
        val wild = IncomingMessageSettingsCodec.normalize(IncomingMessageSettings(enabled = true,
            apps = listOf("com.whatsapp", "com.whatsapp", "kein paket", "x".repeat(300) + ".y") + (1..40).map { "app.nr$it" },
            always = listOf("  Anna  ", "anna", "A", "x".repeat(200)) + (1..80).map { "Eintrag $it" },
            quietFromMinute = -5, quietUntilMinute = 99_999))
        assertEquals(IncomingMessageSettingsCodec.MAX_APPS, wild.apps.size)
        assertEquals("com.whatsapp", wild.apps.first())
        assertFalse("kein paket" in wild.apps)
        assertEquals(IncomingMessageSettingsCodec.MAX_LIST, wild.always.size)
        assertEquals("Anna", wild.always.first())
        assertTrue(wild.always.all { it.length in 2..IncomingMessageSettingsCodec.MAX_ENTRY })
        assertEquals(0 to 24 * 60 - 1, wild.quietFromMinute to wild.quietUntilMinute)
    }

    @Test fun quietHoursCrossMidnight() {
        val night = IncomingMessageSettings(quietHours = true)
        assertTrue(night.quietAt(22 * 60) && night.quietAt(23 * 60 + 59) && night.quietAt(0) && night.quietAt(6 * 60 + 59))
        assertFalse(night.quietAt(7 * 60) || night.quietAt(12 * 60) || night.quietAt(21 * 60 + 59))
        val noon = night.copy(quietFromMinute = 12 * 60, quietUntilMinute = 14 * 60)
        assertTrue(noon.quietAt(13 * 60))
        assertFalse(noon.quietAt(14 * 60) || noon.quietAt(11 * 60))
        assertFalse(night.copy(quietHours = false).quietAt(23 * 60))
        assertFalse("equal ends mean never", night.copy(quietFromMinute = 600, quietUntilMinute = 600).quietAt(600))
    }

    @Test fun theOwnersSentencesAreUnderstoodNarrowly() {
        fun parsed(text: String) = IncomingMessageCommands.parse(text)
        listOf("Was ist noch offen?", "was ist offen", "Welche Nachrichten sind noch offen?", "Offene Nachrichten", "Zeig mir die offenen Nachrichten",
            "Gibt es offene Nachrichten?").forEach { assertEquals(it, IncomingMessageCommands.Command.OpenList, parsed(it)) }
        assertEquals(IncomingMessageCommands.Command.Read("Anna"), parsed("Lies die Nachricht von Anna vor"))
        assertEquals(IncomingMessageCommands.Command.Read(null), parsed("Lies mir bitte die Nachricht vor"))
        assertEquals(IncomingMessageCommands.Command.Read("Bezirksgericht Linz"), parsed("Lies die Mail von Bezirksgericht Linz bitte vor."))
        listOf("Stör mich jetzt nicht", "Bitte störe mich gerade nicht", "Nicht stören", "Lass mich jetzt in Ruhe").forEach {
            assertEquals(it, IncomingMessageCommands.Command.DoNotDisturb, parsed(it))
        }
        listOf("Du darfst wieder", "Du darfst mich wieder stören", "Du kannst wieder").forEach { assertEquals(it, IncomingMessageCommands.Command.MayDisturb, parsed(it)) }
        assertEquals(IncomingMessageCommands.Command.Why(reported = true, who = null), parsed("Warum hast du das gemeldet?"))
        assertEquals(IncomingMessageCommands.Command.Why(reported = false, who = null), parsed("Wieso hast du mir das nicht gemeldet?"))
        assertEquals(IncomingMessageCommands.Command.Why(reported = false, who = "Zalando"), parsed("Warum hast du die Mail von Zalando nicht gemeldet?"))
        assertEquals(IncomingMessageCommands.Command.Always("Bezirksgericht"), parsed("Melde Nachrichten von Bezirksgericht immer"))
        assertEquals(IncomingMessageCommands.Command.Never("LinkedIn"), parsed("Melde LinkedIn nie"))
        assertEquals(IncomingMessageCommands.Command.Never("Hofer"), parsed("Bitte melde mir Mails von Hofer nicht mehr"))
        assertEquals(IncomingMessageCommands.Command.NeverLast, parsed("So etwas nie mehr melden"))
        assertEquals(IncomingMessageCommands.Command.NeverLast, parsed("Das nicht wieder melden."))
        assertEquals(IncomingMessageCommands.Command.Clear, parsed("Lösche die offenen Nachrichten"))
        // Everything else keeps its present route: news, documents, send orders, questions, plain small talk.
        listOf("Fass mir die Nachrichten von heute zusammen", "Was gibt es Neues in den Nachrichten?", "Lies das Dokument vor",
            "Lies Seite 2 vor", "Schreib Anna auf WhatsApp, ich komme später", "Öffne WhatsApp", "Was ist das?", "Melde dich bei Anna",
            "Melde das immer", "Warum ist der Himmel blau?", "ja", "nein", "Stör mich nicht mit Details zur Steuer, fass es kurz",
            "Was ist noch offen bei der Steuererklärung?", "Lies die Nachrichten von heute vor und fass sie zusammen", "").forEach {
            assertNull(it, parsed(it))
        }
    }

    @Test fun answersAndTheDictatedTextAreTakenLiterally() {
        listOf("ja", "Ja.", "ja bitte", "gerne", "ok", "Ja, vorlesen", "lies sie vor", "klar").forEach { assertTrue(it, IncomingMessageCommands.isYes(it)) }
        listOf("nein", "Nein danke", "lass es", "nicht vorlesen", "abbrechen", "stopp").forEach { assertTrue(it, IncomingMessageCommands.isNo(it)) }
        listOf("später", "Später bitte", "nicht jetzt", "ja, später", "nachher").forEach { assertTrue(it, IncomingMessageCommands.isLater(it)) }
        listOf("ja", "passt", "Ja, senden", "schick es ab", "abschicken").forEach { assertTrue(it, IncomingMessageCommands.isSend(it)) }
        listOf("vielleicht", "ja aber", "Ja, wer ist das?", "später vielleicht doch", "").forEach {
            assertFalse(it, IncomingMessageCommands.isYes(it) || IncomingMessageCommands.isNo(it) || IncomingMessageCommands.isLater(it))
        }
        assertFalse("a hesitant answer is not a yes to sending", IncomingMessageCommands.isSend("ja gut, oder doch nicht"))
        assertFalse(IncomingMessageCommands.isSend("vorlesen"))
        assertTrue(IncomingMessageCommands.isRedo("anders") && IncomingMessageCommands.isRedo("Nein, neu"))
        assertEquals(1, IncomingMessageCommands.ordinal("die zweite"))
        assertEquals(0, IncomingMessageCommands.ordinal("Ja, die erste bitte"))
        assertNull(IncomingMessageCommands.ordinal("die nächste"))
        assertEquals("Ich komme um fünf", IncomingMessageCommands.dictation("ich komme um fünf"))
        assertEquals("Wird später", IncomingMessageCommands.dictation("Antworte: wird später"))
        assertEquals("Schreib mir bitte die Adresse", IncomingMessageCommands.dictation("schreib mir bitte die Adresse"))
        assertEquals("Ich bin gleich da", IncomingMessageCommands.yesWithText("Ja, schreib ihr, ich bin gleich da")?.let(IncomingMessageCommands::dictation))
        assertNull(IncomingMessageCommands.dictation("   "))
        assertNull(IncomingMessageCommands.dictation("x".repeat(IncomingMessageCommands.MAX_REPLY + 1)))
        assertNull(IncomingMessageCommands.dictation("Zeile\u0000Null"))
    }

    @Test fun singleRulesOfTheClassifierAndItsPersonCheck() {
        fun mail(sender: String, subject: String, text: String = "", contact: Boolean = false, rules: ImportanceRules = ImportanceRules()) =
            IncomingMessageClassifier.classify(ImportanceInput(IncomingKind.MAIL, sender, subject, text, inContacts = contact), rules)
        assertEquals(IncomingKind.MAIL, IncomingMessageClassifier.kindOf("com.google.android.gm", null))
        assertEquals(IncomingKind.MAIL, IncomingMessageClassifier.kindOf("irgendeine.mail.app", "email"))
        assertEquals(IncomingKind.MESSENGER, IncomingMessageClassifier.kindOf("com.whatsapp", "msg"))
        // Abbreviations only as capital words; everyday words that merely contain one decide nothing.
        assertEquals(ImportanceReason.AUTHORITY, mail("AMS Wels", "Termin").reason)
        assertEquals(ImportanceReason.UNCLEAR, mail("Reiseblog Amsterdam", "Unsere Route durch die Stadt").reason)
        assertEquals(ImportanceReason.UNCLEAR, mail("Datenbank-Dienst", "Wartungsfenster am Sonntag").reason)
        assertEquals(ImportanceReason.PERSONAL_NAME, mail("Franz Gruber", "Gib mir bitte Bescheid").reason)
        assertEquals(ImportanceReason.OFFICIAL_MATTER, mail("Portal", "Ihr Bescheid liegt vor").reason)
        // A contact stays a contact whatever he writes; the owner's lists go before everything.
        assertEquals(ImportanceReason.CONTACT, mail("Zalando", "50 % Rabatt nur heute", contact = true).reason)
        assertEquals(ImportanceVerdict.SILENT, mail("Anna Berger", "Hallo", contact = true, rules = ImportanceRules(never = listOf("Anna Berger"))).verdict)
        assertEquals("a list entry is a whole word", ImportanceReason.PERSONAL_NAME,
            mail("Annemarie Huber", "Hallo", rules = ImportanceRules(never = listOf("Anne"))).reason)
        // Untrusted text is only matched: control characters and markup change nothing.
        assertEquals(ImportanceReason.AUTHORITY, mail("Bezirks​gericht Linz", "Ladung\u0007").reason)
        listOf("Anna Berger", "Mag. Petra Hofer", "Dr. Wimmer", "Gruber Franz", "Julia Moser-Lang", "Frau Maier").forEach {
            assertTrue(it, IncomingMessageClassifier.personalName(it))
        }
        listOf("Lukas", "Zalando", "Wiener Linien", "Kundenservice Team", "Peter Wagner | Firma Muster GmbH", "anna berger", "A1 Telekom Austria",
            "Anna Berger Shop", "", "Eins Zwei Drei Vier Fünf Sechs Sieben Acht").forEach {
            assertFalse(it, IncomingMessageClassifier.personalName(it))
        }
    }
}
