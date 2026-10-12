package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.contentHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class LocalModelCatalogTest {
    private val capabilities = LocalModelCapabilities(
        text = true,
        vision = false,
        audio = false,
        video = false,
        functionCalling = true,
        maxContextTokens = 1_024,
        minimumRuntimeVersion = "0.18.0",
        supportedBackends = setOf(LocalModelBackend.CPU)
    )

    @Test
    fun importCatalogAndIntegrityChecksAreBoundedAndVersioned() {
        val directory = Files.createTempDirectory("device-ai-models").toFile()
        try {
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities }, maxModelBytes = 1_024)
            val bytes = "validated local model".toByteArray()
            val model = catalog.import(
                "functiongemma",
                "1.0.0",
                ByteArrayInputStream(bytes),
                contentHash(bytes.decodeToString())
            )
            assertEquals(bytes.size.toLong(), model.byteCount)
            assertEquals(capabilities, model.capabilities)
            assertEquals(model, catalog.get("functiongemma", "1.0.0"))
            assertEquals(listOf(model), LocalModelCatalog(directory, LocalModelInspector { capabilities }).all())
            assertThrows(IllegalArgumentException::class.java) {
                catalog.import("functiongemma", "1.0.0", ByteArrayInputStream(bytes), model.sha256)
            }

            catalog.modelFile(model).writeText("tampered local model")
            assertNull(catalog.get("functiongemma", "1.0.0"))
            assertTrue(catalog.delete("functiongemma", "1.0.0"))
            assertFalse(catalog.delete("functiongemma", "1.0.0"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun importRejectsBadHashesOversizedAndUnsupportedModelsWithoutCataloging() {
        val directory = Files.createTempDirectory("device-ai-models-invalid").toFile()
        try {
            val bytes = "not model bytes".toByteArray()
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities }, maxModelBytes = 8)
            assertThrows(IllegalArgumentException::class.java) {
                catalog.import("model", "1.0.0", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            }
            assertTrue(catalog.all().isEmpty())

            val wrongHashCatalog = LocalModelCatalog(
                directory,
                LocalModelInspector { capabilities },
                maxModelBytes = 1_024
            )
            assertThrows(IllegalArgumentException::class.java) {
                wrongHashCatalog.import("model", "1.0.0", ByteArrayInputStream(bytes), "0".repeat(64))
            }
            assertThrows(IllegalArgumentException::class.java) {
                wrongHashCatalog.import("../outside", "1.0.0", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            }

            val unsupported = LocalModelCatalog(
                directory,
                LocalModelInspector {
                    capabilities.copy(supportedBackends = setOf(LocalModelBackend.GPU))
                },
                maxModelBytes = 1_024
            )
            assertThrows(IllegalArgumentException::class.java) {
                unsupported.import("gpu-only", "1.0.0", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            }
            assertTrue(wrongHashCatalog.all().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
