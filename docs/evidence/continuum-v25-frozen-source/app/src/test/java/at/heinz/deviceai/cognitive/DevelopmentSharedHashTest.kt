package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentSharedHashTest {
    @Test fun sharedHashMatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test fun developmentSnapshotUsesSharedHashAndRejectsTampering() {
        val snapshot = SourceSnapshot.of("app/Counter.kt", "abc")
        assertEquals(sha256Hex("abc"), snapshot.sha256)
        assertThrows(IllegalArgumentException::class.java) {
            SourceSnapshot(snapshot.path, "changed", snapshot.sha256)
        }
    }

    @Test fun identityIntegrityStillUsesTheUnchangedSharedHash() {
        val genome = CognitiveGenome("shared-hash-regression")
        assertEquals(sha256Hex(genome.canonical()), genome.hash())
        val identity = IdentityState.initial(genome)
        assertTrue(identity.verify())
        assertEquals(sha256Hex(identity.canonical()), identity.integrityHash)
    }
}
