package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelImportPresetTest {
    @Test fun gemmaE4bPresetHasWellFormedImmutableImportIdentity() {
        assertTrue(LocalModelImportPreset.GEMMA4_E4B_ID.matches(
            Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        assertTrue(LocalModelImportPreset.GEMMA4_E4B_VERSION.matches(
            Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        assertTrue(LocalModelImportPreset.GEMMA4_E4B_SHA256.matches(Regex("[a-f0-9]{64}")))
        assertEquals("gemma4-e4b-it", LocalModelImportPreset.GEMMA4_E4B_ID)
    }
}
