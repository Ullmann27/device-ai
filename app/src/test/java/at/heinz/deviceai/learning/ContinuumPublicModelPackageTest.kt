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
}
