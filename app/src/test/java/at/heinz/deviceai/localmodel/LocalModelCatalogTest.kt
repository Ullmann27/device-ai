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

    @Test fun explicitRetryRecoversMatchingOrphanBytesAfterMetadataCommitWasInterrupted() {
        val directory = Files.createTempDirectory("model-orphan-recovery").toFile()
        try {
            var inspections = 0
            val catalog = LocalModelCatalog(directory, LocalModelInspector {
                inspections++
                capabilities
            })
            val bytes = "validated interrupted model".toByteArray()
            val expected = contentHash(bytes.decodeToString())
            val original = catalog.import("gemma-fixture", "v1", ByteArrayInputStream(bytes), expected)
            val originalFile = catalog.modelFile(original)
            assertTrue(directory.listFiles()!!.single { it.extension == "meta" }.delete())
            assertTrue(originalFile.isFile)
            assertNull(catalog.get(original.id, original.version))
            assertTrue(catalog.all().isEmpty())

            val recovered = catalog.import("gemma-fixture", "v1", ByteArrayInputStream(bytes), expected)
            assertEquals(original, recovered)
            assertEquals(recovered, catalog.get(recovered.id, recovered.version))
            assertTrue(originalFile.readBytes().contentEquals(bytes))
            assertEquals(2, inspections)
            assertEquals(1, directory.listFiles()!!.count { it.extension == "litertlm" })
            assertThrows(IllegalArgumentException::class.java) {
                catalog.import("gemma-fixture", "v1", ByteArrayInputStream(bytes), expected)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun mismatchingOrphanIsPreservedAndCannotAcquireVerifiedMetadata() {
        val directory = Files.createTempDirectory("model-orphan-mismatch").toFile()
        try {
            val catalog = LocalModelCatalog(directory, LocalModelInspector { capabilities })
            val bytes = "verified selected model".toByteArray()
            val expected = contentHash(bytes.decodeToString())
            val original = catalog.import("fixture", "v1", ByteArrayInputStream(bytes), expected)
            val originalFile = catalog.modelFile(original)
            assertTrue(directory.listFiles()!!.single { it.extension == "meta" }.delete())
            originalFile.writeText("different interrupted bytes")
            val before = originalFile.readBytes()
            val failure = assertThrows(IllegalArgumentException::class.java) {
                catalog.import("fixture", "v1", ByteArrayInputStream(bytes), expected)
            }
            assertTrue(failure.message!!.contains("existing bytes preserved"))
            assertTrue(originalFile.readBytes().contentEquals(before))
            assertNull(catalog.get("fixture", "v1"))
            assertFalse(directory.listFiles()!!.any { it.extension == "meta" })
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
