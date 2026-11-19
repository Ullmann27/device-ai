package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's tone setting (19.11.2026): the spoken command, „Nenn mich …“ and the one style source both brains take
 * their manner from. Everything here is pure text logic; no model is involved.
 */
class OwnerConversationToneTest {
    private val jarvis = "Jarvis"

    // ── setting and command ─────────────────────────────────────────────────────────────────────────────

    @Test fun personalIsTheDefaultAndBothTonesHaveTheirLabel() {
        // He asked for it in his own words; „Sprich wieder sachlich“ is one sentence away.
        assertEquals(OwnerConversationTone.PERSONAL, OwnerConversationTone.DEFAULT)
        assertEquals(listOf("Persönlich", "Sachlich"), OwnerConversationTone.entries.map { it.label })
        assertEquals(OwnerConversationTone.FACTUAL, OwnerConversationTone.parse("FACTUAL"))
        assertNull(OwnerConversationTone.parse("FRIENDLY"))
        assertNull(OwnerConversationTone.parse(null))
    }

    @Test fun theOwnersTwoSentencesSwitchTheTone() {
        assertEquals(OwnerConversationTone.PERSONAL, OwnerToneCommand.parse("Sprich persönlich mit mir", jarvis))
        assertEquals(OwnerConversationTone.FACTUAL, OwnerToneCommand.parse("Sprich wieder sachlich", jarvis))
        assertEquals(OwnerConversationTone.PERSONAL, OwnerToneCommand.parse("Sei nicht so förmlich", jarvis))
        assertEquals(OwnerConversationTone.PERSONAL, OwnerToneCommand.parse("Jarvis, red mit mir wie ein guter Freund", jarvis))
        assertEquals(OwnerConversationTone.FACTUAL, OwnerToneCommand.parse("Gesprächston sachlich", jarvis))
    }

    @Test fun aSentenceAboutSomethingElseIsNoToneCommand() {
        for (other in listOf("Erklär mir das sachlich", "Schreib Anna eine persönliche Nachricht", "Ist das persönlich gemeint?",
            "Sprich nicht so laut", "Sprich englisch mit mir", "Sprich persönlich mit Anna darüber", "Hallo", "",
            "Sprich persönlich und sachlich mit mir", "Fass das sachlich zusammen und speichere es als PDF"))
            assertNull(other, OwnerToneCommand.parse(other, jarvis))
    }

    // ── name ────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun theNameIsOnlyWhatTheOwnerSaysHimself() {
        val said = OwnerNameCommand.parse("Nenn mich Heinz", jarvis) as OwnerNameCommand.Command.Set
        assertEquals("Heinz", said.name)
        // His sentence is kept as the provenance of the name.
        assertEquals("Nenn mich Heinz", said.statement)
        assertEquals("Heinz", (OwnerNameCommand.parse("nenn mich heinz", jarvis) as OwnerNameCommand.Command.Set).name)
        assertEquals("Heinz", (OwnerNameCommand.parse("Mein Name ist Heinz Ullmann", jarvis) as OwnerNameCommand.Command.Set).name)
        assertEquals("Chef", (OwnerNameCommand.parse("Du kannst mich Chef nennen", jarvis) as OwnerNameCommand.Command.Set).name)
        assertEquals(OwnerNameCommand.Command.Forget, OwnerNameCommand.parse("Vergiss meinen Namen", jarvis))
        assertEquals(OwnerNameCommand.Command.Forget, OwnerNameCommand.parse("Nenn mich nicht mehr Heinz", jarvis))
    }

    @Test fun aSentenceThatOnlyLooksLikeANameIsNotTaken() {
        for (other in listOf("Nenn mich nicht so", "Nenn mir drei Beispiele", "Ich heiße dich willkommen", "Wie heiße ich?",
            "Nenn mich", "Ich bin müde", "Mein Name steht im Vertrag", "Nenn mich bitte anders", "Sag Anna zu mir, dass es passt",
            "Nenn mich Heinz und öffne das PDF Argon4"))
            assertNull(other, OwnerNameCommand.parse(other, jarvis))
    }

    @Test fun aNameInTheConfirmedFactsIsReadAndNothingIsGuessed() {
        assertEquals("Heinz", OwnerNameCommand.fromFacts(listOf("Ich wohne in Wien", "Ich heiße Heinz")))
        assertEquals("Heinz", OwnerNameCommand.fromFacts(listOf("Mein Name ist Heinz Ullmann.")))
        // A fact about somebody else, or one that merely contains a name, is no statement of his name.
        assertNull(OwnerNameCommand.fromFacts(listOf("Meine Frau heißt Anna", "Heinz ist ein schöner Name", "Ich wohne in Wien")))
        assertNull(OwnerNameCommand.fromFacts(emptyList()))
        assertEquals("Heinz", OwnerNameCommand.typed("  heinz "))
        assertNull(OwnerNameCommand.typed("so"))
        assertNull(OwnerNameCommand.typed("Heinz 123"))
        assertNull(OwnerNameCommand.typed(""))
    }

    // ── one style source for both brains ────────────────────────────────────────────────────────────────

    @Test fun factualAddsNothingToEitherPrompt() {
        assertEquals("", OwnerToneStyle.localLine(OwnerConversationTone.FACTUAL, "Heinz", ConversationRegister.CASUAL))
        assertEquals("", OwnerToneStyle.localLine(OwnerConversationTone.FACTUAL, null, ConversationRegister.PLAYFUL))
        assertNull(OwnerToneStyle.claudeBlock(OwnerConversationTone.FACTUAL, "Heinz"))
        assertNull(OwnerToneStyle.claudeBlock(OwnerConversationTone.FACTUAL, null))
    }

    @Test fun theOnDeviceLineIsShortAndCarriesTheNameOnlyWhenKnown() {
        val named = OwnerToneStyle.localLine(OwnerConversationTone.PERSONAL, "Heinz", ConversationRegister.CASUAL)
        val nameless = OwnerToneStyle.localLine(OwnerConversationTone.PERSONAL, null, ConversationRegister.CASUAL)
        // Prompt tokens cost time and heat on the phone: one short paragraph, not a persona essay.
        assertTrue(named.length.toString(), named.length <= 460)
        assertTrue(nameless.length.toString(), nameless.length <= 460)
        assertTrue(named.startsWith("\n") && named.endsWith("\n"))
        assertTrue(named.contains("mit Heinz") && named.contains("„Heinz“"))
        assertTrue(named.contains("seit dreißig Jahren"))
        assertTrue(named.contains("trockenem Humor"))
        // No name is ever built into the style: without one the line says so and forbids guessing.
        assertFalse(nameless.contains("Heinz"))
        assertTrue(nameless.contains("rate keinen"))
        for (line in listOf(named, nameless)) {
            assertTrue(line.contains("erfinden"))
            assertTrue(line.contains("belegt"))
            assertTrue(line.contains("Nicht von dir aus begrüßen"))
            assertTrue(line.contains("Fehler und Rückfragen bleiben klar"))
        }
    }

    @Test fun aSeriousOrStrainedTurnGetsNoHumour() {
        for (register in listOf(ConversationRegister.SERIOUS, ConversationRegister.DISTRESSED)) {
            val line = OwnerToneStyle.localLine(OwnerConversationTone.PERSONAL, "Heinz", register)
            assertFalse(register.name, line.contains("trockenem Humor"))
            assertTrue(register.name, line.contains("ohne Humor"))
        }
    }

    @Test fun theCloudBlockIsConstantForATimeAndANameAndStatesTheLimits() {
        val block = OwnerToneStyle.claudeBlock(OwnerConversationTone.PERSONAL, "Heinz")!!
        // Byte-stable: the same input gives the same bytes, and nothing of a turn or a clock is in it.
        assertEquals(block, OwnerToneStyle.claudeBlock(OwnerConversationTone.PERSONAL, "Heinz"))
        assertFalse(Regex("\\d{1,2}[.:]\\d{2}").containsMatchIn(block))
        assertFalse(block.contains("2026"))
        assertTrue(block.contains("seit dreißig Jahren"))
        assertTrue(block.contains("„Heinz“"))
        // Familiarity is a manner, not a claim.
        assertTrue(block.contains("Erfinde keine gemeinsame Vergangenheit"))
        assertTrue(block.contains("Weißt du noch"))
        assertTrue(block.contains("bestätigte Erinnerung"))
        assertTrue(block.contains("Arbeitsjournal"))
        assertTrue(block.contains("Gesprächsverlauf"))
        assertTrue(block.contains("keine Schmeichelei"))
        assertTrue(block.contains("Du bist eine KI"))
        // Approvals, questions and errors stay unambiguous; the app greets, not the model.
        assertTrue(block.contains("Bestätigungsfragen"))
        assertTrue(block.contains("nicht in einen Scherz verpackt"))
        assertTrue(block.contains("Begrüße ihn nicht von dir aus"))
        assertTrue(block.contains("Standarddeutsch"))
        // A text he will pass on keeps its own form.
        assertTrue(block.contains("Texte, die er weitergeben oder speichern will"))
        // The reference manner: a cheeky remark is answered in kind, then the job is done.
        assertTrue(block.contains("im selben Ton"))

        val nameless = OwnerToneStyle.claudeBlock(OwnerConversationTone.PERSONAL, null)!!
        assertFalse(nameless.contains("Heinz"))
        assertTrue(nameless.contains("rate keinen"))
        assertNotNull(nameless)
    }

    @Test fun noNameIsHardcodedInTheStyleSource() {
        val source = listOf("src/main/java/at/heinz/deviceai/cognitive/OwnerConversationTone.kt",
            "app/src/main/java/at/heinz/deviceai/cognitive/OwnerConversationTone.kt").map { java.io.File(it) }.first { it.isFile }.readText()
        val code = source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("/**") || it.trim().startsWith("//") }
            .joinToString("\n")
        assertFalse("the style source must not contain the owner's name", code.contains("Heinz"))
        assertFalse(code.contains("Hans"))
    }
}
