package at.heinz.deviceai.intelligence.ewm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EXP-20 M6-B4: a later owner statement replaces exactly the earlier statement of the same holder and relation.
 * Base memory: my residence, my workplace (same city) and my sister's residence.
 */
class Ewm1OwnerSupersessionTest {
    private val base = listOf("wohnort" to "Ich wohne in Wien.", "arbeit" to "Ich arbeite in Wien.",
        "anna" to "Meine Schwester Anna wohnt in Linz.")

    private fun replaced(vararg later: String, facts: List<Pair<String, String>> = base): Map<String, Set<String>> =
        Ewm1Memory(facts + later.mapIndexed { i, t -> "neu$i" to t }).supersedes
            .mapValues { it.value.toSet() }.filterValues { it.isNotEmpty() }

    @Test fun aDatedRelocationReplacesOnlyMyOldResidence() =
        assertEquals(mapOf("neu0" to setOf("wohnort")), replaced("Seit Dienstag wohne ich in Graz."))

    @Test fun aSentenceInitialAdverbIsNotTheSubject() =
        assertEquals(mapOf("neu0" to setOf("wohnort")), replaced("Inzwischen wohne ich in Graz."))

    @Test fun mittlerweileWithInvertedPronounReplacesMyResidence() =
        assertEquals(mapOf("neu0" to setOf("wohnort")), replaced("Mittlerweile wohne ich in Graz."))

    @Test fun nichtMehrEndsMyResidenceButKeepsMyWorkplace() =
        assertEquals(mapOf("neu0" to setOf("wohnort")), replaced("Ich wohne nicht mehr in Wien."))

    @Test fun nichtMehrAtWorkEndsMyWorkplaceButKeepsMyResidence() =
        assertEquals(mapOf("neu0" to setOf("arbeit")), replaced("Ich arbeite nicht mehr in Wien."))

    @Test fun anExplicitCorrectionReplacesMyResidence() =
        assertEquals(mapOf("neu0" to setOf("wohnort")), replaced("Ich wohne nicht in Wien, sondern in Graz."))

    @Test fun anotherPersonsDatedMoveReplacesOnlyHerResidence() =
        assertEquals(mapOf("neu0" to setOf("anna")), replaced("Seit Dienstag wohnt meine Schwester Anna in Graz."))

    @Test fun aNamedPersonAfterAnAdverbKeepsHerOwnHistory() = assertEquals(mapOf("neu0" to setOf("f0")),
        replaced("Inzwischen wohnt Anna in Graz.", facts = listOf("f0" to "Anna wohnt in Linz.", "f1" to "Ich wohne in Wien.")))

    @Test fun ourHouseholdMoveReplacesOurResidenceButNotOurStove() = assertEquals(mapOf("neu0" to setOf("f0")),
        replaced("Inzwischen wohnen wir in Graz.", facts = listOf("f0" to "Wir wohnen in Wien.", "f1" to "Wir haben einen Kachelofen in Wien.")))

    // ---- negative controls: nothing confirmed may be lost

    @Test fun herOwnNameIsNoSharedDetailSoHerNewJobKeepsHerResidence() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Seit Dienstag arbeitet meine Schwester Anna in Graz.", facts = listOf("f0" to "Meine Schwester Anna wohnt in Wien.")))

    @Test fun anotherRelationInTheSameCityIsNoUpdate() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Max wohnt nicht mehr in Wien.", facts = listOf("f0" to "Max arbeitet in Wien.")))

    @Test fun aFutureMoveDoesNotEndMyPresentResidence() =
        assertEquals(emptyMap<String, Set<String>>(), replaced("Ab nächstem Monat wohne ich in Graz."))

    @Test fun aFutureJobOfAnotherPersonKeepsHerPresentOne() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Max arbeitet demnächst in Graz.", facts = listOf("f0" to "Max arbeitet in Wien.")))

    @Test fun aPostponedAppointmentToNextWeekStillReplacesTheOldDate() = assertEquals(mapOf("neu0" to setOf("f0")),
        replaced("Der Elternabend wurde auf nächsten Mittwoch verschoben.", facts = listOf("f0" to "Der Elternabend ist am Montag.")))

    // ---- review 2026-10-30: event vs state, time orientation, reported speech, coordination, chains, retrieval

    private val none = emptyMap<String, Set<String>>()
    private fun only(vararg facts: String) = facts.mapIndexed { i, t -> "f$i" to t }

    @Test fun aSharedCityAloneDoesNotLetAStateReplaceAPastVisit() =
        assertEquals(none, replaced("Max arbeitet nicht mehr in Wien.", facts = only("Max war gestern in Wien.")))

    @Test fun twoTimeAnchorsAloneRelateNothing() =
        assertEquals(none, replaced("Seit Dienstag arbeitet Anna in Wien.", facts = only("Anna besuchte am Montag Graz.")))

    @Test fun anAdverbInitialVisitIsAPastEventThatAStateDoesNotReplace() = assertEquals(none,
        replaced("Mittlerweile wohnt Anna in Wien.", "Seit Dienstag arbeitet Anna in Linz.", facts = only("Gestern besuchte Anna Graz.")))

    @Test fun twoVisitsAreTwoEvents() = assertEquals(none, replaced("Anna besuchte Linz.", facts = only("Anna besuchte Graz.")))

    @Test fun anExplicitCorrectionOfTheSamePastEventCorrectsIt() = assertEquals(mapOf("neu0" to setOf("f0")),
        replaced("Anna war gestern nicht in Graz, sondern in Linz.", facts = only("Anna war gestern in Graz.")))

    @Test fun aLaterStoredHistoricalStatementDoesNotEndThePresentOne() =
        assertEquals(none, replaced("Früher wohnte ich in Wien.", facts = only("Ich wohne in Graz.")))

    @Test fun aHistoricalStateStaysHistoryWhenANewStateFollows() =
        assertEquals(none, replaced("Anna wohnt inzwischen in Wien.", facts = only("Anna wohnte in Graz.")))

    @Test fun mySistersResidenceIsNoStatementAboutMe() =
        assertEquals(none, replaced("Ich wohne in Graz.", facts = only("Meine Schwester Anna wohnt in Linz.")))

    @Test fun aQuotedFirstPersonBelongsToTheQuotedSpeaker() = assertEquals(none, replaced("„Ich wohne jetzt in Graz“, sagt Paul."))

    @Test fun reportedSpeechIsNoOwnerStatement() = assertEquals(none, replaced("Paul sagt, ich wohne jetzt in Graz."))

    @Test fun aCoordinatedSubjectIsNotReplacedByOneOfItsMembers() =
        assertEquals(none, replaced("Inzwischen wohnt Anna in Graz.", facts = only("Anna und Ben wohnen in Wien.")))

    @Test fun mySpousesMoveKeepsMySonsResidence() = assertEquals(mapOf("neu0" to setOf("f0")),
        replaced("Inzwischen wohnt mein Mann Paul in Graz.", facts = only("Mein Mann Paul wohnt in Wien.", "Mein Sohn Leo wohnt in Linz.")))

    @Test fun aHolidayIsNoRelocation() = assertEquals(none, replaced("Ich mache gerade Urlaub in Graz."))

    @Test fun aSharedAdverbIsNoSharedActivity() =
        assertEquals(none, replaced("Inzwischen lese ich gerne Krimis.", facts = only("Ich spiele gerne Tennis.")))

    @Test fun successiveMovesFormAChain() = assertEquals(mapOf("neu0" to setOf("f0"), "neu1" to setOf("f0", "neu0")),
        replaced("Inzwischen wohne ich in Graz.", "Mittlerweile wohne ich in Linz.", facts = only("Ich wohne in Wien.")))

    @Test fun aKnownNewerStatementIsNeverHiddenByIncompleteRetrieval() {
        val facts = listOf("wohnort" to "Ich wohne in Wien.", "arbeit" to "Ich arbeite bei der Post.", "neu" to "Inzwischen wohne ich in Graz.")
        val pack = Ewm1Workspace(facts) { _, subset -> subset.filter { it != "neu" } }.decide("Wo wohne ich?")
        assertEquals(listOf("neu"), pack.cited)
        assertEquals(listOf("wohnort"), pack.outdatedFacts)
    }

    @Test fun mySpouseAndIAreNotMeAlone() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Inzwischen wohne ich in Graz.", facts = listOf("f0" to "Mein Mann und ich wohnen in Wien.")))

    @Test fun aVisitIsNoRelocation() = assertEquals(emptyMap<String, Set<String>>(), replaced("Ich war gestern in Graz."))

    @Test fun aVisitOnADayIsNoRelocation() = assertEquals(emptyMap<String, Set<String>>(), replaced("Am Montag war ich in Graz."))

    @Test fun mySistersStayChangesNothingOfMine() =
        assertEquals(emptyMap<String, Set<String>>(), replaced("Meine Schwester ist seit Dienstag in Graz."))

    @Test fun anUnrelatedDatedFactReplacesNothing() =
        assertEquals(emptyMap<String, Set<String>>(), replaced("Seit Dienstag spiele ich Tennis."))

    @Test fun aNewActivityInTheSameCityKeepsMyWorkplace() =
        assertEquals(emptyMap<String, Set<String>>(), replaced("Inzwischen spiele ich in Wien Tennis."))

    @Test fun twoWorkplacesWithoutUpdateMarkerCoexist() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Ich arbeite auch bei der Post.", facts = listOf("f0" to "Ich arbeite in Wien.")))

    @Test fun aMixedFactAboutTwoPeopleIsNotReplacedWholesale() = assertEquals(emptyMap<String, Set<String>>(),
        replaced("Inzwischen wohne ich in Graz.", facts = listOf("f0" to "Anna wohnt in Linz, ich wohne in Wien.")))
}
