package at.heinz.deviceai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProfileStoreTest {
    private class Memory : VoiceProfileStore.Values {
        val strings = HashMap<String, String>()
        val ints = HashMap<String, Int>()
        val booleans = HashMap<String, Boolean>()
        override fun string(key: String) = strings[key]
        override fun int(key: String, default: Int) = ints[key] ?: default
        override fun boolean(key: String, default: Boolean) = booleans[key] ?: default
        override fun write(strings: Map<String, String?>, ints: Map<String, Int>, booleans: Map<String, Boolean>): Boolean {
            strings.forEach { (k, v) -> if (v == null) this.strings.remove(k) else this.strings[k] = v }
            this.ints.putAll(ints); this.booleans.putAll(booleans)
            return true
        }
    }

    @Test fun anInstallationWithoutProfileKeysReadsAsTheFormerVoice() {
        val memory = Memory()
        assertEquals(VoiceProfileSelection(), VoiceProfileStore(memory).load())
        // An owner who had Maya enabled keeps Maya with the former settings.
        memory.booleans["maya_enabled"] = true
        val loaded = VoiceProfileStore(memory).load()
        assertEquals(VoiceEngine.ELEVENLABS, loaded.engine)
        assertTrue(loaded.isFormerVoice)
    }

    @Test fun roundTripAndBounds() {
        val memory = Memory()
        val store = VoiceProfileStore(memory)
        val chosen = VoiceProfileSelection(VoiceEngine.ELEVENLABS, "AbCdEf1234567890", "Tiefe Stimme",
            ProsodyPreset.SOUVERAEN, -2, 1)
        assertTrue(store.save(chosen))
        assertEquals(chosen, store.load())
        assertEquals(true, memory.booleans["maya_enabled"])
        memory.ints[VoiceProfileStore.KEY_RATE_STEP] = 99
        memory.strings[VoiceProfileStore.KEY_PRESET] = "UNBEKANNT"
        memory.strings[VoiceProfileStore.KEY_VOICE_ID] = "bad id"
        val clamped = store.load()
        assertEquals(VoiceProfiles.MAX_RATE_STEP, clamped.rateStep)
        assertEquals(ProsodyPreset.BISHERIG, clamped.preset)
        assertNull(clamped.elevenVoiceId)
        assertNull(clamped.elevenVoiceName)
        assertTrue(store.save(VoiceProfiles.former(chosen)))
        assertNull(memory.strings[VoiceProfileStore.KEY_VOICE_ID])
    }
}
