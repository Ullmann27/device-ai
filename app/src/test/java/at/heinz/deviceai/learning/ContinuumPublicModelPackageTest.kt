package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ContinuumPublicModelPackageTest {
    private val model=ByteArray(32960) { (it % 251).toByte() }.also {\n        it[0]=83; it[1]=87; it[2]=66; it[3]=49\n    }
    private val hash=MessageDigest.getInstance("SHA-256").digest(model)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun manifest(sha:String=hash)=
        """{"schemaVersion":1,"format":"SWB1","privacy":"PUBLIC_AND_CURATED_TRAINING_ONLY","modelBytes":32960,"modelSha256":"$sha","codeSha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","updates":8}"""

    @Test fun matchingManifestAndBytesAreVerified() {
        val verified=ContinuumPublicModelPackage.verify(model,manifest())
        assertEquals(hash,verified.modelSha256)
        assertEquals("a".repeat(40),verified.codeSha)
        assertEquals(8,verified.updates)
    }
    @Test fun tamperedModelIsRejected() {
        val changed=model.copyOf()
        changed[999]= (changed[999].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicModelPackage.verify(changed,manifest())
        }
    }
    @Test fun unknownPrivacyAndOlderManifestAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicModelPackage.verify(model,manifest().replace(
                "PUBLIC_AND_CURATED_TRAINING_ONLY","PRIVATE"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicModelPackage.verify(model,manifest().replace(
                "\"schemaVersion\":1","\"schemaVersion\":9"))
        }
    }

    /** Same field layout zero-cost-core.mjs writes: nested audit, floats, null codeSha. */
    @Test fun githubManifestLayoutIsAccepted() {
        val manifest="""{
  "schemaVersion": 1, "format": "SWB1", "version": 1, "modelHash": "$hash", "updates": 36000,
  "rounds": 3, "accepted": 2, "paused": false, "replay": 0,
  "audit": {"intent": {"loss": 0.4123, "correct": 15, "total": 20},
            "semantic": {"loss": 0.5521, "correct": 14, "total": 20}},
  "suiteHash": "${"b".repeat(64)}", "lastDecision": "MULTITASK_GATE_PASSED",
  "privacy": "PUBLIC_AND_CURATED_TRAINING_ONLY", "codeSha": null,
  "modelSha256": "$hash", "modelBytes": 32960
}"""
        val verified=ContinuumPublicModelPackage.verify(model,manifest)
        assertNull(verified.codeSha)
        assertEquals(36000,verified.updates)
    }

    @Test fun duplicatePrivacyKeyCannotSmuggleAPrivatePackage() {
        val smuggled=manifest().replace("\"privacy\":","\"privacy\":\"PRIVATE\",\"privacy\":")
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicModelPackage.verify(model,smuggled)
        }
    }

    @Test fun contradictoryHashFieldsAreRejected() {
        val contradictory=manifest().replace("{","{\"modelHash\":\"${"c".repeat(64)}\",")
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumPublicModelPackage.verify(model,contradictory)
        }
    }
}
