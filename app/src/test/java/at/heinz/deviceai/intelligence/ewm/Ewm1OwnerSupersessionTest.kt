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
