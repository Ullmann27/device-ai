package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryContextPlannerTest {
    private val all = PrivacyClass.values().toSet()

    private fun record(id: String, content: String, at: Long = 1_000L, privacy: PrivacyClass = PrivacyClass.DEVICE_LOCAL_ONLY) =
        MemoryRecord(id, MemoryType.SEMANTIC, content, ProvenanceType.USER_PROVIDED, 0.9, at, at, privacyClass = privacy)

    private fun plan(query: String, records: List<MemoryRecord>, limit: Int = 8, allowed: Set<PrivacyClass> = all) =
        MemoryContextPlanner.plan(query, records, limit, nowMillis = 10_000L, allowedPrivacy = allowed)

    @Test fun repeatedStatementsUseOneSlotAndKeepTheirIdsAsCorroboration() {
        val records = listOf(
            record("a", "Ute wohnt in Enns."), record("b", "Ute wohnt in Enns"), record("c", "ute wohnt in enns!"),
            record("d", "Ute arbeitet in Tulln."))
        val result = plan("Wo wohnt und arbeitet Ute?", records, limit = 2)
        val entries = result.groups.flatMap { it.entries }
        assertEquals(2, entries.size)
        val enns = entries.single { it.memory.content.contains("Enns", ignoreCase = true) }
        assertEquals(2, enns.corroboratingIds.size)
        assertTrue(entries.any { it.memory.id == "d" })
    }

    @Test fun aNegationIsNeverMergedIntoThePositiveStatement() {
        val result = plan("Mag Kurt Kaffee?", listOf(record("p", "Kurt mag Kaffee."), record("n", "Kurt mag keinen Kaffee.")))
        val ids = result.groups.flatMap { it.ids }.toSet()
        assertEquals(setOf("p", "n"), ids)
        assertTrue(result.groups.flatMap { it.entries }.all { it.corroboratingIds.isEmpty() })
    }

    @Test fun anOutdatedRecordBringsItsCurrentSuccessorEvenWhenRankingMissedIt() {
        val old = record("old", "Ute wohnte bis 2022 in Enns.", at = 1_000L)
        val new = record("new", "Ute wohnt seit 2023 in Tulln.", at = 2_000L)
        // the query only matches the old record lexically
        val lexical = HybridMemoryRetriever.retrieve("Enns", listOf(old, new), nowMillis = 10_000L)
        assertEquals(listOf("old"), lexical.map { it.memory.id })
        val group = plan("Enns", listOf(old, new)).groups.single()
        assertEquals(listOf("new", "old"), group.ids)
        val (current, outdated) = group.entries
        assertEquals(MemoryContextPlanner.Role.CURRENT_SUCCESSOR, current.role)
        assertEquals(listOf("old"), current.supersedes)
        assertTrue(current.isCurrent)
        assertEquals(MemoryContextPlanner.Role.SUPERSEDED, outdated.role)
        assertEquals(listOf("new"), outdated.supersededBy)
        assertFalse(outdated.isCurrent)
    }

    @Test fun aLaterNowStatementSupersedesAnUnmarkedOneButNotTheOtherWayRound() {
        val earlier = record("wien", "Ich wohne in Wien.", at = 1_000L)
        val later = record("graz", "Ich wohne jetzt in Graz.", at = 2_000L)
        val group = plan("Wo wohne ich?", listOf(earlier, later)).groups.single()
        assertEquals("graz", group.entries.first().memory.id)
        assertEquals(listOf("graz"), group.entries.single { it.memory.id == "wien" }.supersededBy)

        // the "jetzt" statement is OLDER than the unmarked one: no supersession is invented, both are a conflict
        val reversed = plan("Wo wohne ich?", listOf(earlier.copy(createdAt = 3_000L, updatedAt = 3_000L), later)).groups.single()
        assertTrue(reversed.entries.all { it.isCurrent })
        assertTrue(reversed.entries.all { it.conflictsWith.isNotEmpty() })
    }

    @Test fun contradictingRecordsStayTogetherAndAreMarkedForAskingBack() {
        val result = plan("Welchen Bus fährt Kurt?",
            listOf(record("g", "Kurt fährt einen grünen Bus."), record("y", "Kurt fährt einen gelben Bus.")))
        val group = result.groups.single()
        assertEquals(setOf("g", "y"), group.ids.toSet())
        assertEquals(listOf("y"), group.entries.single { it.memory.id == "g" }.conflictsWith)
        assertEquals(listOf("g"), group.entries.single { it.memory.id == "y" }.conflictsWith)
    }

    @Test fun aSiblingQuestionAlsoRetrievesTheOnePersonTheFactsDetermine() {
        val records = listOf(record("rel", "Ute ist die Schwester von Kurt."), record("job", "Kurt arbeitet als Tischler."))
        val baseline = HybridMemoryRetriever.retrieve("Welchen Beruf hat der Bruder von Ute?", records, nowMillis = 10_000L)
        assertFalse(baseline.any { it.memory.id == "job" })
        val result = plan("Welchen Beruf hat der Bruder von Ute?", records)
        assertEquals("Welchen Beruf hat Kurt?", result.relationalQuery)
        assertTrue(result.groups.flatMap { it.ids }.contains("job"))

        val ambiguous = records + record("rel2", "Ute ist die Schwester von Hans.")
        val noGuess = plan("Welchen Beruf hat der Bruder von Ute?", ambiguous)
        assertNull(noGuess.relationalQuery)
        assertFalse(noGuess.groups.flatMap { it.ids }.contains("job"))
    }

    @Test fun aForbiddenRecordIsNeverPulledInAsSuccessorOrPartner() {
        val old = record("old", "Ute wohnte bis 2022 in Enns.", privacy = PrivacyClass.PUBLIC)
        val secret = record("secret", "Ute wohnt seit 2023 in Tulln.", at = 2_000L, privacy = PrivacyClass.SENSITIVE)
        val result = plan("Enns", listOf(old, secret), allowed = setOf(PrivacyClass.PUBLIC))
        assertEquals(listOf("old"), result.groups.flatMap { it.ids })
        assertTrue(result.groups.single().entries.single().isCurrent)
    }

    @Test fun conflictingAliasesAreDroppedAndLimitZeroPlansNothing() {
        val a = record("dup", "Ute wohnt in Enns.")
        val b = record("dup", "Ute wohnt in Linz.")
        assertTrue(plan("Wo wohnt Ute?", listOf(a, b)).groups.isEmpty())
        assertTrue(plan("Wo wohnt Ute?", listOf(record("x", "Ute wohnt in Enns.")), limit = 0).groups.isEmpty())
    }

    @Test fun groupsAreOrderedByRankAndLinkedGrowthIsBounded() {
        val records = (0 until 10).map { record("v$it", "Kurt fährt einen Bus mit Nummer ${100 + it}.") }
        val result = plan("Welchen Bus fährt Kurt?", records, limit = 1)
        val group = result.groups.single()
        assertTrue(group.entries.size <= 1 + MemoryContextPlanner.MAX_LINKED_PER_GROUP)
        assertNotNull(group.entries.firstOrNull { it.role == MemoryContextPlanner.Role.SELECTED })
    }
}
