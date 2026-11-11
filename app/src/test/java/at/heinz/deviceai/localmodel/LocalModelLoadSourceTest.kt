package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.MessageDigestSpi
import java.security.Provider
import java.security.Security

class LocalModelLoadSourceTest {
    private val bytes = ByteArray(262_144) { (it % 251).toByte() }
    private val capabilities = LocalModelCapabilities(
        true, false, false, false, true, 4_096, "0.18.0", setOf(LocalModelBackend.CPU)
    )

    @Test fun loadSourceHashesCompleteModelExactlyOnce() = fixture { catalog, model, file ->
        val digests = observeSha256 {
            val source = requireNotNull(catalog.sourceForLoad(model.id, model.version))
            assertEquals(model, source.first)
            assertEquals(file, source.second)
        }
        assertEquals("Complete model byte scans", listOf(bytes.size.toLong()),
            digests.filter { it >= bytes.size })
    }

    @Test fun laterLoadsStillVerifyEveryModelByte() = fixture { catalog, model, _ ->
        val digests = observeSha256 {
            repeat(2) { assertEquals(model, catalog.sourceForLoad(model.id, model.version)?.first) }
        }
        assertEquals(listOf(bytes.size.toLong(), bytes.size.toLong()),
            digests.filter { it >= bytes.size })
    }

    @Test fun sameLengthTamperingIsRejectedWithoutMetadataChanges() = fixture { catalog, model, file ->
        file.writeBytes(bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        assertNull(catalog.sourceForLoad(model.id, model.version))
        assertNull(catalog.get(model.id, model.version))
    }

    @Test fun missingOrTruncatedModelHasNoLoadSource() = fixture { catalog, model, file ->
        file.writeBytes(bytes.copyOf(bytes.size - 1))
        assertNull(catalog.sourceForLoad(model.id, model.version))
        assertTrue(file.delete())
        assertNull(catalog.sourceForLoad(model.id, model.version))
    }

    @Test fun wrongIdentityCannotResolveAnotherModel() = fixture { catalog, model, _ ->
        assertNull(catalog.sourceForLoad("different-model", model.version))
        assertNull(catalog.sourceForLoad(model.id, "different-version"))
        assertThrows(IllegalArgumentException::class.java) {
            catalog.sourceForLoad("../outside", model.version)
        }
    }

    private fun fixture(action: (LocalModelCatalog, ImportedLocalModel, File) -> Unit) {
        val directory = Files.createTempDirectory("model-load-source").toFile()
        try {
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val model = catalog.import("synthetic-load", "1", ByteArrayInputStream(bytes), hash)
            val file = catalog.modelFile(model)
            action(catalog, model, file)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun observeSha256(action: () -> Unit): List<Long> {
        val digests = mutableListOf<Long>()
        val thread = Thread.currentThread()
        val underlying = MessageDigest.getInstance("SHA-256").provider
        val provider = object : Provider("ContinuumLoadHashObserver", "1.0", "Test digest observer") {
            init {
                putService(object : Service(this, "MessageDigest", "SHA-256",
                    DigestObserver::class.java.name, emptyList(), emptyMap()) {
                    override fun newInstance(parameter: Any?): Any = DigestObserver(
                        MessageDigest.getInstance("SHA-256", underlying)
                    ) { count -> if (Thread.currentThread() === thread) digests += count }
                })
            }
        }
        check(Security.insertProviderAt(provider, 1) == 1)
        try { action() }
        finally { Security.removeProvider(provider.name) }
        return digests
    }

    private class DigestObserver(
        private val delegate: MessageDigest,
        private val completed: (Long) -> Unit
    ) : MessageDigestSpi() {
        private var bytes = 0L
        override fun engineUpdate(input: Byte) {
            bytes++
            delegate.update(input)
        }
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int) {
            bytes += length
            delegate.update(input, offset, length)
        }
        override fun engineDigest(): ByteArray {
            val result = delegate.digest()
            completed(bytes)
            bytes = 0L
            return result
        }
        override fun engineReset() {
            bytes = 0L
            delegate.reset()
        }
    }
}
