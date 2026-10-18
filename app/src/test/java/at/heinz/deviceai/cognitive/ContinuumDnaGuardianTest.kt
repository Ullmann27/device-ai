package at.heinz.deviceai.cognitive

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumDnaGuardianTest {
    private class LocalSigner : IdentitySigner {
        private val key = "continuum-dna-guardian-fixture".toByteArray(Charsets.UTF_8)

        override fun sign(payload: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(key + payload)

        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            MessageDigest.isEqual(sign(payload), signature)
    }

    @Test
    fun noIdentityDoesNotCreateOneOrRequestAnAndroidKeystoreKey() {
        val dir = Files.createTempDirectory("dna-guardian-empty").toFile()
        try {
            val check = ContinuumDnaGuardian(dir) {
                error("The guardian must not initialize an identity")
            }.inspect()
            assertEquals(DnaIntegrityState.NOT_INITIALIZED, check.state)
            assertEquals(null, check.identityGeneration)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validSignedStateIsVerifiedAfterRestartWithoutMutatingTheSeed() {
        val dir = Files.createTempDirectory("dna-guardian-valid").toFile()
        try {
            val signer = LocalSigner()
            val runtime = ContinuumRuntimeStore(dir, signer)
            val created = runtime.openOrCreate()
            val committed = runtime.checkpoint(created)
            val seedFile = File(dir, "seed/continuum-seed.bin")
            val original = seedFile.readBytes()
            val check = ContinuumDnaGuardian(dir) { signer }.inspect()
            assertEquals(DnaIntegrityState.VERIFIED, check.state)
            assertEquals(committed.archive.identityGeneration, check.identityGeneration)
            assertTrue(MessageDigest.isEqual(original, seedFile.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aModifiedSeedWithNoValidFallbackIsRejectedAndPreserved() {
        val dir = Files.createTempDirectory("dna-guardian-seed-bad").toFile()
        try {
            val signer = LocalSigner()
            ContinuumRuntimeStore(dir, signer).openOrCreate()
            val seedFile = File(dir, "seed/continuum-seed.bin")
            val damaged = seedFile.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            seedFile.writeBytes(damaged)
            assertEquals(
                DnaIntegrityState.INVALID,
                ContinuumDnaGuardian(dir) { signer }.inspect().state
            )
            assertTrue(MessageDigest.isEqual(damaged, seedFile.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun orphanedArchiveDoesNotTriggerCreationOfAReplacementIdentity() {
        val dir = Files.createTempDirectory("dna-guardian-orphan").toFile()
        try {
            val orphan = File(dir, "cognitive/cognitive-state.bin")
            orphan.parentFile!!.mkdirs()
            orphan.writeText("orphaned state must be preserved")
            assertEquals(
                DnaIntegrityState.INVALID,
                ContinuumDnaGuardian(dir) {
                    error("No signer should be used without a signed seed")
                }.inspect().state
            )
            assertEquals("orphaned state must be preserved", orphan.readText())
            assertFalse(File(dir, "seed/continuum-seed.bin").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wrongSignerCannotSilentlyAuthorizeExistingDna() {
        val dir = Files.createTempDirectory("dna-guardian-wrong-key").toFile()
        try {
            val original = LocalSigner()
            ContinuumRuntimeStore(dir, original).openOrCreate()
            val wrongSigner = object : IdentitySigner {
                override fun sign(payload: ByteArray): ByteArray = ByteArray(32)
                override fun verify(payload: ByteArray, signature: ByteArray) = false
            }
            assertEquals(
                DnaIntegrityState.INVALID,
                ContinuumDnaGuardian(dir) { wrongSigner }.inspect().state
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
