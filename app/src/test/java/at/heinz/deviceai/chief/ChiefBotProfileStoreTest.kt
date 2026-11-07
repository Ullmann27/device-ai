package at.heinz.deviceai.chief

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ChiefBotProfileStoreTest {
    private fun root() = Files.createTempDirectory("chief-bots-").toFile()
    private fun rejects(block: () -> Unit) { try { block(); fail("expected rejection") } catch (_: IllegalArgumentException) { } }

    @Test fun threeIdentitiesAndPrivateNotesSurviveRestartAndRename() {
        val dir = root()
        try {
            val store = ChiefBotProfileStore(dir)
            assertEquals(listOf("bot-atlas", "bot-kora", "bot-lint"), store.seedDefaults().map { it.agentId })
            val profile = store.get("bot-kora")!!
            val noted = store.saveWorkingContext(profile.agentId, profile.agentId, "Hypothese: Schleife prüfen", profile.profileVersion)
            val renamed = store.rename(profile.agentId, "Kora Entwicklung", noted.profileVersion)
            val next = ChiefBotProfileStore(dir)
            assertEquals(renamed, next.get(profile.agentId))
            assertEquals(profile.memoryNamespace, renamed.memoryNamespace)
            assertEquals(profile.workspaceId, renamed.workspaceId)
            assertEquals("Hypothese: Schleife prüfen", next.workingContext(profile.agentId, profile.agentId))
            assertNull(next.workingContext("bot-lint", "bot-lint"))
            assertTrue(next.get(profile.agentId)!!.permissionScope.isEmpty())
            assertTrue(next.get(profile.agentId)!!.verifiedCapabilities.isEmpty())
            assertEquals("REGISTERED", next.get(profile.agentId)!!.lifecycleState)
            assertNull(next.get(profile.agentId)!!.currentTaskId)
            assertEquals("Kora Entwicklung", next.seedDefaults().single { it.agentId == profile.agentId }.displayName)
            rejects { next.workingContext("bot-lint", profile.agentId) }
            rejects { next.saveWorkingContext("bot-lint", profile.agentId, "overwrite", renamed.profileVersion) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun optionalTwentySeedsStayMetadataAndPaginationIsDeterministic() {
        val dir = root()
        try {
            val store = ChiefBotProfileStore(dir)
            store.seedDefaults(3)
            store.seedDefaults(20)
            var cursor: String? = null
            val seen = mutableListOf<String>()
            do { val page = store.page(cursor, 3); seen += page.items.map { it.agentId }; cursor = page.nextAfterId } while (cursor != null)
            assertEquals(20, seen.size)
            assertEquals(seen.sorted().distinct(), seen)
            assertTrue(store.page().items.all { it.permissionScope.isEmpty() && it.verifiedCapabilities.isEmpty() && it.currentTaskId == null })
            assertEquals(seen, ChiefBotProfileStore(dir).page().items.map { it.agentId })
        } finally { dir.deleteRecursively() }
    }

    @Test fun systemParentAliasKeepsRosterDurable() {
        val base = root()
        try {
            val managed = File(base, "managed").apply { mkdir() }
            val files = File(managed, "app/files").apply { mkdirs() }
            val alias = File(base, "system-alias")
            Files.createSymbolicLink(alias.toPath(), managed.toPath())
            val aliasedFiles = File(alias, "app/files")
            val store = ChiefBotProfileStore(File(aliasedFiles, "chief-bots"), trustedParentDirectory = aliasedFiles)
            val original = store.seedDefaults(20).single { it.agentId == "bot-kora" }
            val renamed = store.rename(original.agentId, "Kora dauerhaft", original.profileVersion)
            assertEquals(20, ChiefBotProfileStore(File(files, "chief-bots")).page().items.size)
            assertEquals(renamed, ChiefBotProfileStore(File(aliasedFiles, "chief-bots"), trustedParentDirectory = aliasedFiles).get(original.agentId))
        } finally { base.deleteRecursively() }
    }

    @Test fun staleUpdatesAndDuplicateIdsDoNotOverwriteAcceptedData() {
        val dir = root()
        try {
            val a = ChiefBotProfileStore(dir); val b = ChiefBotProfileStore(dir)
            val p = a.create("agent-one", "One", "Tests", "Precise")
            b.rename(p.agentId, "New", p.profileVersion)
            rejects { a.rename(p.agentId, "Lost update", p.profileVersion) }
            rejects { a.create(p.agentId, "Duplicate", "Other", "Other") }
            assertEquals("New", a.get(p.agentId)!!.displayName)
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptAndUnknownSchemaStayUnmodified() {
        val dir = root()
        try {
            val store = ChiefBotProfileStore(dir); store.seedDefaults()
            val file = File(dir, "bot-profiles-v1.bin")
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            rejects { store.page() }; rejects { store.seedDefaults(20) }
            assertArrayEquals(bytes, file.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun loweredCapacityAndOversizedNotesCannotDamageCommittedData() {
        val dir = root()
        try {
            val store = ChiefBotProfileStore(dir, maxContextBytes = 64)
            val profile = store.seedDefaults().first()
            rejects { ChiefBotProfileStore(dir, maxProfiles = 2).seedDefaults() }
            rejects { store.saveWorkingContext(profile.agentId, profile.agentId, "x".repeat(65), 1) }
            assertEquals(3, store.page().items.size)
            assertNull(store.workingContext(profile.agentId, profile.agentId))
        } finally { dir.deleteRecursively() }
    }

    @Test fun unsafePathsAndSymlinksFailClosed() {
        val dir = root(); val outside = root()
        try {
            val store = ChiefBotProfileStore(dir)
            rejects { store.create("../other", "No", "No", "No") }
            Files.createSymbolicLink(File(dir, ".bot-profiles.lock").toPath(), File(outside, "lock").toPath())
            rejects { store.seedDefaults() }
            assertFalse(File(outside, "lock").exists())
        } finally { dir.deleteRecursively(); outside.deleteRecursively() }
    }

    @Test fun parallelInstancesSerializeAcceptedCreates() {
        val dir = root(); val workers = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 40).map { index -> Callable {
                ChiefBotProfileStore(dir).create("agent-$index", "Agent $index", "Tests", "Precise")
            } }
            workers.invokeAll(jobs).forEach { it.get() }
            assertEquals(40, ChiefBotProfileStore(dir).page().items.size)
        } finally { workers.shutdownNow(); dir.deleteRecursively() }
    }

    @Test fun existingObjectCorruptionBlocksReadAndIsNotSilentlyHealed() {
        val dir = root()
        try {
            val store = ChiefBotProfileStore(dir); val profile = store.seedDefaults().first()
            val noted = store.saveWorkingContext(profile.agentId, profile.agentId, "note", 1)
            val file = File(dir, "working-context/${profile.memoryNamespace}/${noted.contextObjectId}.obj")
            file.writeBytes(byteArrayOf(1, 2, 3))
            rejects { store.workingContext(profile.agentId, profile.agentId) }
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
            assertEquals(noted, store.get(profile.agentId))
        } finally { dir.deleteRecursively() }
    }

    @Test fun profileBackedCoordinatorStillRequiresTrustedRuntimeApproval() {
        val dir = root()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob { "No call" } }
        try {
            val store = ChiefBotProfileStore(dir); store.seedDefaults()
            val denied = ChiefDelegationCoordinator(pool, profileStore = store)
            assertFalse(denied.register(ChiefSpecialist("bot-kora", setOf("coding"), emptySet())))
            val accepted = ChiefDelegationCoordinator(pool, approveSpecialist = { true }, profileStore = store, maxSpecialists = 1000)
            assertFalse(accepted.register(ChiefSpecialist("unknown", setOf("coding"), emptySet())))
            assertTrue(accepted.register(ChiefSpecialist("bot-kora", setOf("coding"), emptySet())))
            assertTrue(accepted.activeAssignments().isEmpty())
            assertTrue(store.get("bot-kora")!!.verifiedCapabilities.isEmpty())
        } finally { pool.shutdown(); dir.deleteRecursively() }
    }
}
