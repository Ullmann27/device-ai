package at.heinz.deviceai

import at.heinz.deviceai.chief.ChiefBotAddressRouter
import at.heinz.deviceai.chief.ChiefBotProfileStore
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Actual paginated registry lookup. Names are editable metadata, never authority. */
class IndependentBotAddressRouterTest {
    @Test fun renamedProfileBeyondFirstHundredIsResolvedWithoutMutatingRightsOrRegistry() {
        val root = Files.createTempDirectory("independent-bot-router-page-").toFile()
        try {
            val store = ChiefBotProfileStore(root)
            repeat(105) { index -> store.create("custom-${index.toString().padStart(3, '0')}", "Person $index", "Prüfung", "Nennt Quellen") }
            val old = requireNotNull(store.get("custom-104"))
            val renamed = store.rename(old.agentId, "Dynamischer Name", old.profileVersion)
            val before = root.resolve("bot-profiles-v1.bin").readBytes()
            val parsed = ChiefBotAddressRouter.parse("Dynamischer Name, Prüfe nur diesen Zusammenhang") as ChiefBotAddressRouter.Address.Message
            val resolved = requireNotNull(ChiefBotAddressRouter.resolve(store, parsed.name))
            assertEquals(renamed.agentId, resolved.agentId)
            assertEquals(old.memoryNamespace, resolved.memoryNamespace)
            assertEquals(old.workspaceId, resolved.workspaceId)
            assertEquals(old.permissionScope, resolved.permissionScope)
            assertEquals("Prüfe nur diesen Zusammenhang", parsed.text)
            assertArrayEquals(before, root.resolve("bot-profiles-v1.bin").readBytes())
            assertNull(ChiefBotAddressRouter.resolve(store, old.displayName))
        } finally { root.deleteRecursively() }
    }

    @Test fun duplicateNamesAreRejectedAcrossPageBoundary() {
        val root = Files.createTempDirectory("independent-bot-router-ambiguity-").toFile()
        try {
            val store = ChiefBotProfileStore(root)
            repeat(101) { index -> store.create("review-${index.toString().padStart(3, '0')}",
                if (index == 0 || index == 100) "Gleicher Name" else "Review $index", "Prüfung", "Sorgfältig") }
            try {
                ChiefBotAddressRouter.resolve(store, "gleicher name")
                fail("A duplicate name cannot choose whichever page was read first")
            } catch (expected: IllegalStateException) {
                assertEquals("BOT_NAME_AMBIGUOUS", expected.message)
            }
        } finally { root.deleteRecursively() }
    }

    @Test funDisplayNameCannotSilentlyImpersonateAnotherStableId() {
        val root = Files.createTempDirectory("independent-bot-router-id-").toFile()
        try {
            val store = ChiefBotProfileStore(root)
            store.create("actual-bot", "Echte Identität", "Prüfung", "Sorgfältig")
            store.create("other-bot", "actual-bot", "Andere Identität", "Sorgfältig")
            try {
                ChiefBotAddressRouter.resolve(store, "actual-bot")
                fail("Ambiguous visible-name/stable-ID collision must fail closed")
            } catch (expected: IllegalStateException) {
                assertEquals("BOT_NAME_AMBIGUOUS", expected.message)
            }
            assertNull(ChiefBotAddressRouter.resolve(store, "nicht-vorhanden"))
        } finally { root.deleteRecursively() }
    }
}
