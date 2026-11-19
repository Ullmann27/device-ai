package at.heinz.deviceai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevenLabsVoiceCatalogTest {
    /** Shape as documented for GET /v2/voices (synthetic values, no real account data). */
    private val response = """
        {"voices":[
          {"voice_id":"Zz000000000000000001","name":"Zeta","category":"premade","description":"Calm narrator",
           "labels":{"gender":"male","age":"middle_aged","accent":"american","descriptive":"deep","use_case":"narrative_story"},
           "preview_url":"https://storage.example.com/zeta/en.mp3",
           "verified_languages":[{"language":"en","model_id":"eleven_multilingual_v2","preview_url":"https://storage.example.com/zeta/en2.mp3"},
                                 {"language":"de","model_id":"eleven_multilingual_v2","accent":"standard","preview_url":"https://storage.example.com/zeta/de.mp3"}]},
          {"voice_id":"Aa000000000000000001","name":"Alpha","labels":{"gender":"female"},"preview_url":"http://insecure.example.com/a.mp3"},
          {"voice_id":"../../etc","name":"Kaputt"},
          {"voice_id":"Bb000000000000000001","name":""},
          {"voice_id":"Cc000000000000000001","name":"Beta\u0007","preview_url":"javascript:alert(1)"}
        ],"has_more":true}
    """.trimIndent()

    @Test fun parsesDocumentedFieldsAndPrefersTheGermanSample() {
        val page = ElevenLabsVoiceCatalog.parse(response)
        assertTrue(page.hasMore)
        assertEquals(listOf("Zeta", "Alpha", "Beta"), page.voices.map { it.name })
        val zeta = page.voices.first()
        assertTrue(zeta.germanVerified && zeta.germanPreview)
        assertEquals("https://storage.example.com/zeta/de.mp3", zeta.previewUrl)
        assertEquals("male · middle_aged · american · deep · narrative_story · Deutsch geprüft · premade", zeta.labelLine())
        assertEquals("Calm narrator", zeta.description)
    }

    @Test fun unsafeSamplesAndBrokenEntriesAreDroppedNotInvented() {
        val page = ElevenLabsVoiceCatalog.parse(response)
        assertNull("http is not played", page.voices.first { it.name == "Alpha" }.previewUrl)
        assertNull(page.voices.first { it.name == "Beta" }.previewUrl)
        assertFalse(page.voices.any { it.id == "../../etc" })
        assertTrue(ElevenLabsVoiceCatalog.parse("{}").voices.isEmpty())
    }

    @Test fun listingUsesOnlyTheVoicesEndpointWithTheKeyInTheFetchSeam() {
        val calls = ArrayList<Pair<String, String>>()
        val page = ElevenLabsVoiceCatalog.load("synthetic_test_key") { url, key -> calls += url to key; response }
        assertEquals(listOf(ElevenLabsVoiceCatalog.ENDPOINT to "synthetic_test_key"), calls)
        assertEquals("https://api.elevenlabs.io/v2/voices?page_size=100", ElevenLabsVoiceCatalog.ENDPOINT)
        assertEquals(3, page.voices.size)
    }
}
