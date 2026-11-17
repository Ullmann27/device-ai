package at.heinz.deviceai.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumLanguageCoreSelectionTest {
    private val suite = "german-grounded-holdout-v1"
    private fun core(
        backend: ContinuumLanguageBackend,
        score: Double = 0.75,
        ready: Boolean = true,
        passed: Boolean = true,
        consent: Boolean = false,
        suiteId: String = suite
    ) = ContinuumBenchmarkedCore(
        backend = backend,
        modelSha256 = "a".repeat(64),
        ready = ready,
        suiteId = suiteId,
        score = score,
        independentHoldoutPassed = passed,
        ownerNetworkConsent = consent
    )

    @Test fun swb1CannotBePromotedToGenerativeLanguageCore() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.DIALOGUE, suite,
            listOf(core(ContinuumLanguageBackend.SWB1_AUXILIARY, 1.0))
        )
        assertSame(ContinuumLanguageSelection.NoQualifiedCore, result)
    }

    @Test fun swb1MayRemainAnAuxiliaryClassifier() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.INTENT_CLASSIFICATION, suite,
            listOf(core(ContinuumLanguageBackend.SWB1_AUXILIARY, 0.7))
        ) as ContinuumLanguageSelection.Selected
        assertEquals(ContinuumLanguageBackend.SWB1_AUXILIARY, result.candidate.backend)
    }

    @Test fun unreadyOrRegressedModelIsNotAdmitted() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.CODING, suite,
            listOf(
                core(ContinuumLanguageBackend.LITERT_LM, 0.9, ready = false),
                core(ContinuumLanguageBackend.GGUF_LOCAL, 0.95, passed = false)
            )
        )
        assertSame(ContinuumLanguageSelection.NoQualifiedCore, result)
    }

    @Test fun benchmarkSuitesCannotBeMixed() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.DIALOGUE, suite,
            listOf(core(ContinuumLanguageBackend.LITERT_LM, 0.99, suiteId = "different"))
        )
        assertSame(ContinuumLanguageSelection.NoQualifiedCore, result)
    }

    @Test fun ownerLanRequiresExplicitCurrentConsent() {
        val lan = core(ContinuumLanguageBackend.OWNER_LAN, 0.98, consent = true)
        val phone = core(ContinuumLanguageBackend.LITERT_LM, 0.74)
        val without = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.GROUNDED_QUESTION, suite, listOf(lan, phone)
        ) as ContinuumLanguageSelection.Selected
        assertEquals(ContinuumLanguageBackend.LITERT_LM, without.candidate.backend)
        val with = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.GROUNDED_QUESTION, suite, listOf(lan, phone),
            allowOwnerLan = true
        ) as ContinuumLanguageSelection.Selected
        assertEquals(ContinuumLanguageBackend.OWNER_LAN, with.candidate.backend)
    }

    @Test fun ownerLanStillRequiresConsentWithinTheCandidate() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.DIALOGUE, suite,
            listOf(core(ContinuumLanguageBackend.OWNER_LAN, consent = false)),
            allowOwnerLan = true
        )
        assertSame(ContinuumLanguageSelection.NoQualifiedCore, result)
    }

    @Test fun tieKeepsPromptOnDevice() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.CODING, suite,
            listOf(
                core(ContinuumLanguageBackend.OWNER_LAN, 0.8, consent = true),
                core(ContinuumLanguageBackend.LITERT_LM, 0.8)
            ), allowOwnerLan = true
        ) as ContinuumLanguageSelection.Selected
        assertEquals(ContinuumLanguageBackend.LITERT_LM, result.candidate.backend)
    }

    @Test fun strongestQualifiedModelWinsBySameSuiteScore() {
        val result = ContinuumLanguageCoreSelection.choose(
            ContinuumLanguageWorkload.DIALOGUE, suite,
            listOf(
                core(ContinuumLanguageBackend.LITERT_LM, 0.6),
                core(ContinuumLanguageBackend.GGUF_LOCAL, 0.9)
            )
        ) as ContinuumLanguageSelection.Selected
        assertEquals(ContinuumLanguageBackend.GGUF_LOCAL, result.candidate.backend)
        assertTrue(result.candidate.independentHoldoutPassed)
    }
}
