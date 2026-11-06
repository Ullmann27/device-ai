package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.JarvisVoiceSelection.Candidate
import at.heinz.deviceai.cognitive.JarvisVoiceSelection.Language
import at.heinz.deviceai.cognitive.JarvisVoiceSelection.Origin
import org.junit.Assert.*
import org.junit.Test

class JarvisVoiceSelectionTest {
    private fun v(name: String, lang: String = "de", country: String = "DE", quality: Int = 400, latency: Int = 200,
                  network: Boolean = false, notInstalled: Boolean = false) =
        Candidate(name, lang, country, quality, latency, network, notInstalled)

    // What the old rule (maxBy quality over all German voices) got wrong: a de-DE voice of equal quality won over de-AT
    // depending on engine list order.
    @Test fun austrianVariantWinsOverGermanAtEqualQualityRegardlessOfListOrder() {
        val de = v("de-de-x-a-local", country = "DE"); val at = v("de-at-x-b-local", country = "AT")
        assertEquals(at, JarvisVoiceSelection.choose(listOf(de, at), Language.GERMAN, null)!!.voice)
        assertEquals(at, JarvisVoiceSelection.choose(listOf(at, de), Language.GERMAN, null)!!.voice)
    }

    @Test fun higherQualityBeatsRegionOnlyWithinTheSameRegionRank() {
        val atLow = v("at-low", country = "AT", quality = 300); val deHigh = v("de-high", country = "DE", quality = 500)
        // Region first: the owner asked for Austrian pronunciation; quality decides only among equals.
        assertEquals(atLow, JarvisVoiceSelection.choose(listOf(deHigh, atLow), Language.GERMAN, null)!!.voice)
        val atHigh = v("at-high", country = "AT", quality = 500)
        assertEquals(atHigh, JarvisVoiceSelection.choose(listOf(atLow, atHigh), Language.GERMAN, null)!!.voice)
    }

    @Test fun networkOrNotInstalledVoicesAreNeverChosenEvenIfTheOwnerPickedThem() {
        val net = v("de-net", network = true); val missing = v("de-missing", notInstalled = true); val ok = v("de-ok", quality = 200)
        val c = JarvisVoiceSelection.choose(listOf(net, missing, ok), Language.GERMAN, "de-net")!!
        assertEquals(ok, c.voice); assertEquals(Origin.AUTOMATIC, c.origin); assertTrue(c.ownerChoiceDropped)
        assertNull(JarvisVoiceSelection.choose(listOf(net, missing), Language.GERMAN, null))
    }

    @Test fun ownerChoiceWinsOverTheAutomaticRankingAndEvenOverTheLanguageSwitch() {
        val at = v("de-at", country = "AT"); val female = v("de-de-x-chosen", quality = 300)
        val c = JarvisVoiceSelection.choose(listOf(at, female), Language.GERMAN, "de-de-x-chosen")!!
        assertEquals(female, c.voice); assertEquals(Origin.OWNER_CHOICE, c.origin); assertFalse(c.ownerChoiceDropped)
        // An explicitly chosen English voice stays even while German is the language setting.
        val en = v("en-gb-x", lang = "en", country = "GB")
        assertEquals(en, JarvisVoiceSelection.choose(listOf(at, en), Language.GERMAN, "en-gb-x")!!.voice)
    }

    @Test fun englishIsSelectableAndPrefersBritishThenUs() {
        val us = v("en-us", lang = "en", country = "US"); val gb = v("en-gb", lang = "en", country = "GB")
        val de = v("de-at", country = "AT")
        val list = JarvisVoiceSelection.selectable(listOf(us, de, gb), Language.ENGLISH)
        assertEquals(listOf(gb, us), list)
        assertEquals(gb, JarvisVoiceSelection.choose(listOf(us, de, gb), Language.ENGLISH, null)!!.voice)
    }

    @Test fun tiesAreBrokenByLatencyThenNameSoTheChoiceIsStableAcrossStarts() {
        val slow = v("b-slow", latency = 300); val fast = v("c-fast", latency = 100); val fast2 = v("a-fast", latency = 100)
        assertEquals(listOf(fast2, fast, slow), JarvisVoiceSelection.selectable(listOf(slow, fast, fast2), Language.GERMAN))
        assertEquals(listOf(fast2, fast, slow), JarvisVoiceSelection.selectable(listOf(fast2, slow, fast), Language.GERMAN))
    }

    @Test fun languageTagRoundTripAndDescriptionAreReadable() {
        assertEquals(Language.ENGLISH, Language.fromTag("en")); assertEquals(Language.GERMAN, Language.fromTag(null))
        val d = JarvisVoiceSelection.describe(v("de-at-x-dea-local", country = "AT", quality = 400, latency = 200))
        assertTrue(d, d.startsWith("Österreich · sehr hohe Qualität · schnell · de-at-x-dea-local"))
    }
}
