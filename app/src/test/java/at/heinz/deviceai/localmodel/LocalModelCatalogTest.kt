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
import java.io.InputStream
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

    @Test fun retryRecoversVerifiedModelAfterProcessDeathBetweenDataAndMetadataCommit() {
        val directory = Files.createTempDirectory("model-import-commit-recovery").toFile()
        try {
            val bytes = "complete model bytes before interrupted metadata commit".toByteArray()
            val first = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val imported = first.import("gemma", "v1", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            val file = first.modelFile(imported)
            // Exact persistent state after the data rename but before metadata publication.
            assertTrue(directory.listFiles()!!.single { it.name.endsWith(".meta") }.delete())
            val reopened = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            assertTrue(reopened.all().isEmpty())
            var closed = false
            val unread = object : InputStream() {
                override fun read(): Int = error("Recovery must not copy the multi-GB model again")
                override fun close() { closed = true }
            }
            val recovered = reopened.import("gemma", "v1", unread, imported.sha256)
            assertTrue(closed)
            assertEquals(imported, recovered)
            assertEquals(listOf(imported), reopened.all())
            assertEquals(imported, reopened.get("gemma", "v1"))
            assertTrue(bytes.contentEquals(file.readBytes()))
        } finally { directory.deleteRecursively() }
    }

    @Test fun orphanRecoveryRejectsDifferentExpectedBytesWithoutOverwritingExistingFile() {
        val directory = Files.createTempDirectory("model-import-orphan-mismatch").toFile()
        try {
            val bytes = "existing complete model".toByteArray()
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val original = catalog.import("gemma", "v1", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            val file = catalog.modelFile(original)
            assertTrue(directory.listFiles()!!.single { it.name.endsWith(".meta") }.delete())
            val failure = assertThrows(IllegalArgumentException::class.java) {
                catalog.import("gemma", "v1", ByteArrayInputStream("different".toByteArray()), "0".repeat(64))
            }
            assertTrue(failure.message!!.contains("SHA-256"))
            assertTrue(bytes.contentEquals(file.readBytes()))
            assertTrue(catalog.all().isEmpty())
            assertFalse(directory.listFiles()!!.any { it.name.endsWith(".meta") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun orphanRecoveryRechecksCpuCompatibilityBeforePublishingMetadata() {
        val directory = Files.createTempDirectory("model-import-orphan-capability").toFile()
        try {
            val bytes = "existing model".toByteArray()
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val original = catalog.import("gemma", "v1", ByteArrayInputStream(bytes), contentHash(bytes.decodeToString()))
            val file = catalog.modelFile(original)
            assertTrue(directory.listFiles()!!.single { it.name.endsWith(".meta") }.delete())
            val unsupported = LocalModelCatalog(directory, LocalModelInspector {
                capabilities.copy(supportedBackends = setOf(LocalModelBackend.GPU))
            })
            val failure = assertThrows(IllegalArgumentException::class.java) {
                unsupported.import("gemma", "v1", ByteArrayInputStream(bytes), original.sha256)
            }
            assertTrue(failure.message!!.contains("CPU"))
            assertTrue(bytes.contentEquals(file.readBytes()))
            assertTrue(unsupported.all().isEmpty())
        } finally { directory.deleteRecursively() }
    }

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

    @Test fun failedNewImportReportsBothHashesAndPreservesPreviouslyVerifiedModel() {
        val directory = Files.createTempDirectory("model-import-identity").toFile()
        try {
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val oldBytes = "previous valid model".toByteArray()
            val old = catalog.import("owner-name", "v1", ByteArrayInputStream(oldBytes), contentHash(oldBytes.decodeToString()))
            val candidate = "different downloaded bytes".toByteArray()
            val failure = assertThrows(IllegalArgumentException::class.java) {
                catalog.import("owner-name", "v2", ByteArrayInputStream(candidate), "0".repeat(64))
            }
            assertTrue("Mismatch must identify the expected digest", failure.message!!.contains("0".repeat(64)))
            assertTrue("Mismatch must identify the actually read bytes", failure.message!!.contains(contentHash(candidate.decodeToString())))
            assertEquals(old, catalog.get(old.id, old.version))
            assertEquals(listOf(old), catalog.all())
            assertNull(catalog.get("owner-name", "v2"))
            assertFalse(directory.listFiles()!!.any { it.name.startsWith("model-import-") })
        } finally { directory.deleteRecursively() }
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
