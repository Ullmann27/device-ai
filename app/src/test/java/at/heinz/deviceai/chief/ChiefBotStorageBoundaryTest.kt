package at.heinz.deviceai.chief

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Real temporary filesystem boundaries, never an owner directory or a mocked store. */
class ChiefBotStorageBoundaryTest {
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected a closed storage boundary") } catch (_: IllegalArgumentException) { }
    }
    private fun withRoot(block: (File) -> Unit) {
        val parent = Files.createTempDirectory("bot-storage-boundary-").toFile()
        try { block(parent) } finally { parent.deleteRecursively() }
    }

    @Test fun trustedParentAliasCreatesTwentyProfilesAndReopensThroughBothPaths() = withRoot { parent ->
        val actual = File(parent, "managed").apply { mkdir() }
        File(actual, "files").mkdir()
        val alias = File(parent, "app-files")
        Files.createSymbolicLink(alias.toPath(), actual.toPath())
        val store = ChiefBotProfileStore(File(alias, "files/chief-bots"), trustedParentDirectory = File(alias, "files"))
        assertEquals(20, store.seedDefaults(20).size)
        var cursor: String? = null
        val ids = mutableListOf<String>()
        do {
            val page = store.page(cursor, 8)
            ids += page.items.map { it.agentId }
            cursor = page.nextAfterId
        } while (cursor != null)
        assertEquals(20, ids.distinct().size)
        assertEquals(ids.sorted(), ids)
        assertEquals(ids, ChiefBotProfileStore(File(actual, "files/chief-bots"), trustedParentDirectory = File(actual, "files")).page().items.map { it.agentId })
        assertEquals(ids, ChiefBotProfileStore(File(alias, "files/chief-bots"), trustedParentDirectory = File(alias, "files")).page().items.map { it.agentId })
    }

    @Test fun aliasesShareTheProcessLockForConcurrentCreates() = withRoot { parent ->
        val actual = File(parent, "actual").apply { mkdir() }
        File(actual, "files").mkdir()
        val alias = File(parent, "alias")
        Files.createSymbolicLink(alias.toPath(), actual.toPath())
        val pool = Executors.newFixedThreadPool(4)
        try {
            pool.invokeAll((0 until 24).map { index -> Callable {
                val base = if (index % 2 == 0) actual else alias
                ChiefBotProfileStore(File(base, "files/bots"), trustedParentDirectory = File(base, "files")).create("agent-$index", "Agent $index", "Role", "Style")
            } }).forEach { it.get() }
            assertEquals(24, ChiefBotProfileStore(File(actual, "files/bots"), trustedParentDirectory = File(actual, "files")).page().items.size)
        } finally { pool.shutdownNow() }
    }

    @Test fun ownedRootLinkIsRejectedWithoutWritingOutside() = withRoot { parent ->
        val outside = File(parent, "outside").apply { mkdir() }
        val owned = File(parent, "bots")
        Files.createSymbolicLink(owned.toPath(), outside.toPath())
        rejected { ChiefBotProfileStore(owned).seedDefaults(20) }
        assertTrue(outside.listFiles()!!.isEmpty())
    }

    @Test fun ownedContextAndConversationDirectoryLinksAreRejected() = withRoot { parent ->
        val owned = File(parent, "bots")
        val store = ChiefBotProfileStore(owned)
        val profile = store.seedDefaults().first()
        val outside = File(parent, "outside").apply { mkdir() }
        Files.createSymbolicLink(File(owned, "working-context").toPath(), outside.toPath())
        rejected { store.saveWorkingContext(profile.agentId, profile.agentId, "Private note", 1) }
        assertTrue(outside.listFiles()!!.isEmpty())
        Files.delete(File(owned, "working-context").toPath())
        File(owned, "conversations").mkdir()
        Files.createSymbolicLink(File(owned, "conversations/${profile.memoryNamespace}").toPath(), outside.toPath())
        val forged = profile.copy(conversationObjectId = "a".repeat(64), conversationVersion = 1)
        val directoryMethod = ChiefBotProfileStore::class.java.getDeclaredMethod("conversationDirectory", ChiefBotProfile::class.java)
        directoryMethod.isAccessible = true
        try { directoryMethod.invoke(store, forged); fail("Expected owned conversation link rejection") }
        catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalArgumentException) }
        assertTrue(outside.listFiles()!!.isEmpty())
        assertEquals(profile, store.get(profile.agentId))
    }

    @Test fun primaryAndLockLinksCannotReadOrModifyOutsideRecords() = withRoot { parent ->
        for (name in listOf("bot-profiles-v1.bin", ".bot-profiles.lock")) {
            val owned = File(parent, name.replace('.', '_')).apply { mkdir() }
            val outside = File(parent, "outside-$name").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            Files.createSymbolicLink(File(owned, name).toPath(), outside.toPath())
            rejected { ChiefBotProfileStore(owned).seedDefaults(20) }
            assertArrayEquals(byteArrayOf(1, 2, 3), outside.readBytes())
        }
    }

    @Test fun corruptAndUnsupportedRegistriesRemainByteExactAcrossFailedSeedAndRead() = withRoot { parent ->
        for (schema in listOf(2, 999)) {
            val owned = File(parent, "schema-$schema").apply { mkdir() }
            val file = File(owned, "bot-profiles-v1.bin")
            val bytes = registry(schema, emptyList()).also { if (schema == 2) it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(bytes)
            val store = ChiefBotProfileStore(owned)
            rejected { store.seedDefaults(20) }; rejected { store.page() }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun legacySchemaOneReadIsUnmodifiedAndExplicitWritePreservesOwnerProfile() = withRoot { parent ->
        val owned = File(parent, "bots").apply { mkdir() }
        val file = File(owned, "bot-profiles-v1.bin")
        val seed = ChiefBotProfileStore(File(parent, "seed")).seedDefaults().first().copy(displayName = "Owner legacy name", role = "Owner legacy role", profileVersion = 7)
        val legacy = registry(1, listOf(seed))
        file.writeBytes(legacy)
        assertEquals(seed, ChiefBotProfileStore(owned).page().items.single())
        assertArrayEquals(legacy, file.readBytes())
        ChiefBotProfileStore(owned).seedDefaults(20)
        assertEquals(seed, ChiefBotProfileStore(owned).get(seed.agentId))
        assertEquals(20, ChiefBotProfileStore(owned).page().items.size)
    }

    @Test fun failedSeedCapacityLeavesExistingSafeReadAndRegistryUnchanged() = withRoot { parent ->
        val owned = File(parent, "bots")
        ChiefBotProfileStore(owned).seedDefaults(3)
        val file = File(owned, "bot-profiles-v1.bin")
        val before = file.readBytes()
        val limited = ChiefBotProfileStore(owned, maxProfiles = 3)
        rejected { limited.seedDefaults(20) }
        assertEquals(3, limited.page().items.size)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun trustedParentIsExplicitAndDoesNotPermitStoreOrParentLinks() = withRoot { parent ->
        val trusted = File(parent, "files").apply { mkdir() }
        val outside = File(parent, "outside").apply { mkdir() }
        rejected { ChiefBotProfileStore(File(outside, "bots"), trustedParentDirectory = trusted) }
        rejected { ChiefBotProfileStore(trusted, trustedParentDirectory = trusted) }
        val linkedParent = File(parent, "linked-files")
        Files.createSymbolicLink(linkedParent.toPath(), trusted.toPath())
        rejected { ChiefBotProfileStore(File(linkedParent, "bots"), trustedParentDirectory = linkedParent) }
        Files.createSymbolicLink(File(trusted, "bots").toPath(), outside.toPath())
        rejected { ChiefBotProfileStore(File(trusted, "bots"), trustedParentDirectory = trusted).seedDefaults(20) }
        assertTrue(outside.listFiles()!!.isEmpty())
    }

    @Test fun failedFilesystemWriteRetainsRegistryAndSafeDiagnostic() = withRoot { parent ->
        val owned = File(parent, "bots")
        val store = ChiefBotProfileStore(owned)
        store.seedDefaults()
        val file = File(owned, "bot-profiles-v1.bin")
        val before = file.readBytes()
        // POSIX mode applies even to creation of the random temp file. Do not emulate IOException.
        val original = Files.getPosixFilePermissions(owned.toPath())
        try {
            Files.setPosixFilePermissions(owned.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"))
            try { store.seedDefaults(20); fail("Expected a real read-only-directory write failure") }
            catch (failure: java.nio.file.AccessDeniedException) {
                assertEquals("BOT_STORE_ACCESS_DENIED", ChiefBotProfileStore.diagnosticCode(failure))
            }
            assertArrayEquals(before, file.readBytes())
            assertEquals(3, store.page().items.size)
        } finally { Files.setPosixFilePermissions(owned.toPath(), original) }
    }

    @Test fun diagnosticsNeverEchoArbitraryOwnerPathsOrText() {
        val privateText = "/private/owner/path: private working note"
        assertEquals("BOT_STORE_OPERATION_FAILED", ChiefBotProfileStore.diagnosticCode(IllegalStateException(privateText)))
        assertEquals("BOT_REGISTRY_UNSUPPORTED_SCHEMA", ChiefBotProfileStore.diagnosticCode(
            IllegalStateException(privateText, IllegalArgumentException("BOT_REGISTRY_UNSUPPORTED_SCHEMA"))))
        assertEquals("BOT_STORE_ACCESS_DENIED", ChiefBotProfileStore.diagnosticCode(java.nio.file.AccessDeniedException(privateText)))
    }

    private fun registry(schema: Int, profiles: List<ChiefBotProfile>): ByteArray {
        val payload = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(profiles.size)
            for (p in profiles) {
                out.writeUTF(p.agentId); out.writeLong(p.profileVersion); out.writeUTF(p.displayName)
                out.writeUTF(p.role); out.writeUTF(p.communicationStyle); out.writeUTF(p.modelPolicy)
                out.writeUTF(p.memoryNamespace); out.writeUTF(p.workspaceId)
                out.writeInt(p.resourceBudget.maxModelCalls); out.writeInt(p.resourceBudget.maxToolCalls)
                out.writeLong(p.resourceBudget.deadlineMillis); out.writeLong(p.resourceBudget.workspaceBytes)
                out.writeUTF(p.provenance); out.writeBoolean(false)
                if (schema >= 2) { out.writeBoolean(false); out.writeLong(0) }
            }
        } }.toByteArray()
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeUTF("CONTINUUM_BOT_PROFILES"); out.writeInt(schema); out.writeInt(payload.size)
            out.write(payload); out.write(MessageDigest.getInstance("SHA-256").digest(payload))
        } }.toByteArray()
    }
}
