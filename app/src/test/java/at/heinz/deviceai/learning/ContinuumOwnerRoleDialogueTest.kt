package at.heinz.deviceai.learning

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ContinuumOwnerRoleDialogueTest {
    private fun fact(text: String, at: Long = 1) = LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, text, now=at)

    @Test fun namedPossessorAndEllipsisUseOnlyThatPersonsConfirmedEvidenceAcrossRestart() {
        val dir = Files.createTempDirectory("continuum-owner-role").toFile()
        try {
            val bytes = TrainableSubwordEncoder().save()
            val anna = fact("Anna besitzt einen roten Schlüssel.")
            val ben = fact("Ben besitzt einen blauen Schlüssel.")
            val memories = listOf(anna, ben)
            val first = ContinuumMemoryDialogue(dir).reply(bytes, memories, "Welche Farbe hat Bens Schlüssel?", 2)
            assertEquals(listOf(ben.id), first.memoryIds)
            assertFalse(first.answer.contains(anna.content))
            val second = ContinuumMemoryDialogue(dir).reply(bytes, memories, "Und Annas?", 3)
            assertEquals(listOf(anna.id), second.memoryIds)
            assertTrue(second.resolvedQuery.contains("Annas Schlüssel"))
            assertFalse(second.answer.contains(ben.content))
            assertArrayEquals(TrainableSubwordEncoder().save(), bytes)
        } finally { dir.deleteRecursively() }
    }

    @Test fun laterExplicitCurrentResidenceDoesNotCiteSupersededResidence() {
        val old = fact("Mira wohnt in Wien.", 1)
        val current = fact("Mira wohnt jetzt in Graz.", 2)
        val hits = ContinuumMemoryDialogue.rank(TrainableSubwordEncoder().save(),
            "Wo wohnt Mira?", listOf(current, old))
        assertEquals(listOf(current.id), hits.map { it.id })
    }

    @Test fun missingOrRevokedPersonCannotBorrowAnotherOwnersProperty() {
        val anna = fact("Anna besitzt einen roten Schlüssel.")
        val ben = fact("Ben besitzt einen blauen Schlüssel.")
        val bytes = TrainableSubwordEncoder().save()
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Welche Farbe hat Claras Schlüssel?", listOf(anna, ben)).isEmpty())
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Welche Farbe hat Bens Schlüssel?",
            listOf(anna, ben.copy(status=LearningStatus.REVOKED))).isEmpty())
    }

    @Test fun mentionIsNotPossessionAndUnconfirmedSourceIsNotOwnerEvidence() {
        val misleading = fact("Anna besitzt einen roten Schlüssel und kennt Ben.")
        val ben = fact("Ben besitzt einen blauen Schlüssel.")
        val bytes = TrainableSubwordEncoder().save()
        assertEquals(listOf(ben.id), ContinuumMemoryDialogue.rank(bytes,
            "Welche Farbe hat Bens Schlüssel?", listOf(misleading, ben)).map { it.id })
        val source = LearningExperienceFactory.create(LearningExperienceType.SOURCE_TEXT,
            LearningProvenance.PUBLIC_WEB_SOURCE, LearningTrustClass.SOURCE_OBSERVATION,
            LearningStatus.OBSERVED, "Ben besitzt einen grünen Schlüssel.", sourceRef="https://example.org/beleg", now=3)
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Welche Farbe hat Bens Schlüssel?", listOf(source)).isEmpty())
    }

    @Test fun negativeAndRevokedCorrectionsInvalidateWithoutResurrectingOldHome() {
        val bytes = TrainableSubwordEncoder().save()
        val old = fact("Mira wohnt in Wien.", 1)
        val negative = fact("Korrektur: Mira wohnt nicht in Wien.", 2)
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Wo wohnt Mira?", listOf(old, negative)).isEmpty())
        val current = fact("Mira wohnt jetzt in Graz.", 2)
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Wo wohnt Mira?",
            listOf(old, current.copy(status=LearningStatus.REVOKED))).isEmpty())
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Wo wohnt Mira?",
            listOf(fact("Mira wohnt in Wien, falls sie die Zusage bekommt.", 3))).isEmpty())
    }

    @Test fun equalTimesAndAmbiguousFirstNamesCannotBeResolvedByHashOrder() {
        val bytes = TrainableSubwordEncoder().save()
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Wo wohnt Mira?", listOf(
            fact("Mira wohnt in Wien."), fact("Mira wohnt in Graz."))).isEmpty())
        val full = fact("Mira Berger wohnt in Wien.")
        val namesake = fact("Mira Kurz wohnt in Graz.")
        assertTrue(ContinuumMemoryDialogue.rank(bytes, "Wo wohnt Mira?", listOf(full, namesake)).isEmpty())
        assertEquals(listOf(full.id), ContinuumMemoryDialogue.rank(bytes,
            "Wo wohnt Mira Berger?", listOf(full, namesake)).map { it.id })
    }

    @Test fun unsupportedEllipsisAndOtherConversationDoNotSupplyOldOwners() {
        val dir = Files.createTempDirectory("continuum-role-session").toFile()
        try {
            val bytes = TrainableSubwordEncoder().save()
            val facts = listOf(fact("Anna besitzt einen roten Schlüssel."), fact("Ben besitzt einen blauen Schlüssel."))
            val dialogue = ContinuumMemoryDialogue(dir)
            dialogue.replyInConversation(bytes, facts, "Welche Farbe hat Bens Schlüssel?", 2, "first")
            assertFalse(dialogue.replyInConversation(bytes, facts, "Und Annas Jacke?", 3, "first").grounded)
            assertFalse(dialogue.replyInConversation(bytes, facts, "Und Annas?", 4, "second").grounded)
        } finally { dir.deleteRecursively() }
    }

    @Test fun aLaterClauseCannotSupplyTheHeadersOwnersAttribute() {
        val bytes = TrainableSubwordEncoder().save()
        val cases = listOf("Mara wohnt in Wien. Tina besitzt einen roten Schlüssel.",
            "Mara besitzt einen blauen Helm. Tina besitzt einen roten Schlüssel.",
            "Mara besitzt einen blauen Helm und Tina besitzt einen roten Schlüssel.")
        cases.forEach { text -> assertTrue(text, ContinuumMemoryDialogue.rank(bytes,
            "Welche Farbe hat Maras Schlüssel?", listOf(fact(text))).isEmpty()) }
    }

    @Test fun explicitOwnerReactivationHasItsOwnAssertionTime() {
        val old = fact("Mira wohnt in Wien.", 1).copy(updatedAt=10)
        val interim = fact("Mira wohnt jetzt in Graz.", 3)
        assertEquals(listOf(old.id), ContinuumMemoryDialogue.rank(TrainableSubwordEncoder().save(),
            "Wo wohnt Mira?", listOf(old, interim)).map { it.id })
    }

    @Test fun earlierResidenceStaysHistoricalAndCurrentQuestionCitesGraz() {
        val before = fact("Mira wohnte früher in Wien.", 1)
        val current = fact("Mira wohnt jetzt in Graz.", 2)
        val memories = listOf(before,current)
        val hits = ContinuumMemoryDialogue.rank(TrainableSubwordEncoder().save(), "Wo wohnt Mira jetzt?",memories)
        assertEquals(listOf(current.id),hits.map { it.id })
        assertTrue(hits.single().text.contains("Graz"))
        assertEquals("Mira wohnte früher in Wien.",memories.first().content)
    }

    @Test fun explicitKinshipAgeKeepsTheNamedChildWithoutBorrowingOtherOwners() {
        val bytes = TrainableSubwordEncoder().save()
        val klara = fact("Meine Tochter Klara ist 29.", 1)
        val ben = fact("Ben ist 31 Jahre alt.", 2)
        assertEquals(listOf(klara.id), ContinuumMemoryDialogue.rank(bytes,
            "Wie alt ist Klara?", listOf(klara, ben)).map { it.id })
        assertTrue(ContinuumMemoryDialogue.rank(bytes,
            "Wie alt ist Clara?", listOf(klara, ben)).isEmpty())
        assertEquals(listOf(ben.id), ContinuumMemoryDialogue.rank(bytes,
            "Wie alt ist Ben?", listOf(klara, ben)).map { it.id })
    }

    @Test fun kinshipAgeDoesNotAcceptSecondClauseOrReportedAgeAsOwnersFact() {
        val bytes = TrainableSubwordEncoder().save()
        val misleading = listOf(
            "Meine Tochter Klara ist 29. Ben ist 31.",
            "Meine Tochter Klara ist 29 und Ben ist 31.",
            "Meine Tochter Klara ist 29, laut Ben.",
            "Meine Tochter Klara ist angeblich 29."
        )
        misleading.forEach { text ->
            assertTrue(text, ContinuumMemoryDialogue.rank(bytes,
                "Wie alt ist Klara?", listOf(fact(text))).isEmpty())
        }
    }

    @Test fun ownerCodeIsDisplayedAsDeinWhileLiteralEvidenceIsUnchanged() {
        val dir=Files.createTempDirectory("continuum-owner-perspective").toFile()
        try {
            val original=fact("Mein Testkürzel lautet NEBEL-731.")
            val turn=ContinuumMemoryDialogue(dir).reply(TrainableSubwordEncoder().save(),listOf(original),
                "Wie lautet mein Testkürzel?",2)
            assertEquals(listOf(original.id),turn.memoryIds)
            assertTrue(turn.answer.contains("Dein Testkürzel lautet NEBEL-731."))
            assertTrue(turn.answer.contains("gespeichert: „Mein Testkürzel lautet NEBEL-731.“"))
            assertEquals("Mein Testkürzel lautet NEBEL-731.",original.content)
        } finally { dir.deleteRecursively() }
    }
}
