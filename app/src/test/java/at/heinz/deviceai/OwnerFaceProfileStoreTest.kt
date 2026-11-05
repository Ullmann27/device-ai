package at.heinz.deviceai

import java.nio.file.Files
import javax.crypto.KeyGenerator
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real AES-GCM, synthetic template data. */
class OwnerFaceProfileStoreTest {
    private val directory = Files.createTempDirectory("owner-face-test").toFile()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store(onDelete: () -> Unit = {}) = OwnerFaceProfileStore(directory, { key }, onDelete)
    private fun profile(name: String = "SyntheticFixtureOnly") = OwnerFaceProfile(name, 1_000,
        OwnerFaceModelContract.RECOGNIZER_SHA256, List(OwnerFacePolicy.ENROLLMENT_SAMPLES) {
            FloatArray(OwnerFaceModelContract.FEATURE_SIZE) { index -> if (index == 0) 1f else 0f }
        })

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun profileRoundTripsWithoutPlaintextAndUsesFreshNonce() {
        val store = store()
        assertNull(store.load())
        store.save(profile())
        val first = directory.resolve("profile.aes").readBytes()
        assertFalse(String(first, Charsets.ISO_8859_1).contains("SyntheticFixtureOnly"))
        val loaded = store.load()!!
        assertTrue(loaded.valid())
        assertEquals("SyntheticFixtureOnly", loaded.displayName)
        assertArrayEquals(profile().features.first(), loaded.features.first(), 0f)
        store.save(profile())
        assertFalse(first.contentEquals(directory.resolve("profile.aes").readBytes()))
        assertFalse(directory.resolve("profile.pending").exists())
    }

    @Test fun tamperingAndWrongKeyCannotProduceProfile() {
        store().save(profile())
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertThrows(Exception::class.java) { OwnerFaceProfileStore(directory, { otherKey }).load() }
        val file = directory.resolve("profile.aes")
        file.writeBytes(file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        assertThrows(Exception::class.java) { store().load() }
    }

    @Test fun invalidReplacementPreservesPreviousProfile() {
        val store = store()
        store.save(profile("Previous"))
        val before = directory.resolve("profile.aes").readBytes()
        val invalid = profile("New").also { it.features[0][0] = Float.NaN }
        assertThrows(IllegalArgumentException::class.java) { store.save(invalid) }
        assertTrue(before.contentEquals(directory.resolve("profile.aes").readBytes()))
        assertEquals("Previous", store.load()!!.displayName)
    }

    @Test fun deleteRemovesProfilePendingAndKey() {
        var deleted = 0
        val store = store { deleted++ }
        store.save(profile())
        directory.resolve("profile.pending").writeBytes(byteArrayOf(1, 2, 3))
        store.delete()
        assertNull(store.load())
        assertEquals(1, deleted)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun truncatedOrOversizedFilesFailClosed() {
        val file = directory.resolve("profile.aes")
        file.writeBytes(byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) { store().load() }
        file.writeBytes(ByteArray(65_537))
        assertThrows(IllegalArgumentException::class.java) { store().load() }
    }
}
