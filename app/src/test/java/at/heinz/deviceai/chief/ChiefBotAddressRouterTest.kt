package at.heinz.deviceai.chief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class ChiefBotAddressRouterTest {
    @Test fun existingCommaAddressingAndJarvisSelectionRemainAvailable() {
        assertEquals(ChiefBotAddressRouter.Address.Select("Kora"), ChiefBotAddressRouter.parse("rufe Kora"))
        assertEquals(ChiefBotAddressRouter.Address.Select("Kora"), ChiefBotAddressRouter.parse("JARVIS, rufe Kora!"))
        assertEquals(ChiefBotAddressRouter.Address.Jarvis(null), ChiefBotAddressRouter.parse("  JARVIS  "))
        assertEquals(ChiefBotAddressRouter.Address.Jarvis(null), ChiefBotAddressRouter.parse("rufe JARVIS"))
        assertEquals(
            ChiefBotAddressRouter.Address.Message("Kora", "Prüfe A, B und C: nur berichten."),
            ChiefBotAddressRouter.parse("Kora, Prüfe A, B und C: nur berichten.")
        )
    }

    @Test fun explicitMentionsAddressTheNamedBot() {
        val expected = ChiefBotAddressRouter.Address.Message("Kora", "Prüfe den lokalen Speicher.")
        assertEquals(expected, ChiefBotAddressRouter.parse("@Kora Prüfe den lokalen Speicher."))
        assertEquals(expected, ChiefBotAddressRouter.parse("@Kora: Prüfe den lokalen Speicher."))
        assertEquals(
            ChiefBotAddressRouter.Address.Message("Kora", "Prüfe: A, B und C. Nichts ändern."),
            ChiefBotAddressRouter.parse("@Kora Prüfe: A, B und C. Nichts ändern.")
        )
    }

    @Test fun colonAddressingRetainsEditableMultiwordNames() {
        val expected = ChiefBotAddressRouter.Address.Message("Kora Entwicklung", "Prüfe den Entwurf.")
        assertEquals(expected, ChiefBotAddressRouter.parse("Kora Entwicklung: Prüfe den Entwurf."))
        assertEquals(
            ChiefBotAddressRouter.Address.Message("Kora", "Prüfe A, B: nichts ändern."),
            ChiefBotAddressRouter.parse("Kora: Prüfe A, B: nichts ändern.")
        )
    }

    @Test fun naturalSelectionCommandsHaveNoTaskBodyOrAuthority() {
        for (text in listOf("Rufe Kora auf", "Sprich mit Kora", "Wechsle zu Kora", "sprich MIT Kora!", "JARVIS, wechsle zu Kora.")) {
            assertEquals(text, ChiefBotAddressRouter.Address.Select("Kora"), ChiefBotAddressRouter.parse(text))
        }
        assertEquals(
            ChiefBotAddressRouter.Address.Select("Kora Entwicklung"),
            ChiefBotAddressRouter.parse("Rufe Kora Entwicklung auf!")
        )
        assertNull(ChiefBotAddressRouter.parse("Sprich mit Kora\nund verändere etwas."))
    }

    @Test fun completeMultilineRequestsKeepLateRestrictionsForEveryMessageSyntax() {
        val body = "Analysiere diesen Text:\n" + "Befund, noch ungeprüft. ".repeat(500) +
            "\n\nKorrektur: nichts verändern, keine Dateien schreiben und nicht ins Internet gehen."
        for (prefix in listOf("Kora, ", "Kora: ", "@Kora ", "@Kora: ")) {
            assertEquals(
                prefix,
                ChiefBotAddressRouter.Address.Message("Kora", body),
                ChiefBotAddressRouter.parse(prefix + body)
            )
        }
    }

    @Test fun bodyWhitespaceIsPreservedAfterTheAddressSeparator() {
        val body = "Erste Zeile.\n  Zweite Zeile bleibt eingerückt.\n\nNicht ausführen.  \n"
        for (prefix in listOf("Kora, ", "Kora: ", "@Kora ", "@Kora: ")) {
            assertEquals(
                ChiefBotAddressRouter.Address.Message("Kora", body),
                ChiefBotAddressRouter.parse("  " + prefix + body)
            )
        }
    }

    @Test fun jarvisMessagesRetainTheWholeBodyAcrossAddressForms() {
        val body = "Prüfe lokal.\nKorrektur: nichts verändern.\n"
        for (prefix in listOf("JARVIS, ", "JARVIS: ", "@JARVIS ", "@Jarvis: ")) {
            assertEquals(
                prefix,
                ChiefBotAddressRouter.Address.Jarvis(body),
                ChiefBotAddressRouter.parse(prefix + body)
            )
        }
        assertEquals(ChiefBotAddressRouter.Address.Jarvis(null), ChiefBotAddressRouter.parse("Wechsle zu JARVIS"))
    }

    @Test fun missingBodiesAndOrdinaryTextAreNotAddressedMessages() {
        for (text in listOf("", "  ", "@Kora", "@Kora:   ", "Kora: ", "Kora,   ", "Prüfe den Speicher.")) {
            assertNull(text, ChiefBotAddressRouter.parse(text))
        }
    }

    @Test fun colonWithoutASeparatedBodyDoesNotCaptureUrlsTimesOrPaths() {
        for (text in listOf("https://example.org", "12:30", "10: 30", "C:\\temp\\file", "mail@example.org Nachricht")) {
            assertNull(text, ChiefBotAddressRouter.parse(text))
        }
    }

    @Test fun oversizedNamesAreNotSilentlyTruncatedOrPartiallyAddressed() {
        val name = "K".repeat(81)
        for (text in listOf("$name, Auftrag", "$name: Auftrag", "@$name Auftrag", "@$name: Auftrag", "Sprich mit $name")) {
            assertNull(text, ChiefBotAddressRouter.parse(text))
        }
    }

    @Test fun stableIdsStayExactAndRemainCallableAfterRenaming() = withStore { store ->
        val profile = store.seedDefaults().single { it.agentId == "bot-kora" }
        val renamed = store.rename(profile.agentId, "Kora Entwicklung", profile.profileVersion)
        for (text in listOf("bot-kora, Auftrag", "bot-kora: Auftrag", "@bot-kora Auftrag", "@bot-kora: Auftrag")) {
            val address = ChiefBotAddressRouter.parse(text) as ChiefBotAddressRouter.Address.Message
            assertEquals("bot-kora", address.name)
            assertEquals("Auftrag", address.text)
            assertEquals(renamed, ChiefBotAddressRouter.resolve(store, address.name))
        }
        assertEquals(renamed, ChiefBotAddressRouter.resolve(store, "kora entwicklung"))
        assertNull(ChiefBotAddressRouter.resolve(store, "BOT-KORA"))
        assertNull(ChiefBotAddressRouter.resolve(store, "Kora"))
        assertNull(ChiefBotAddressRouter.resolve(store, "bot-kor"))
    }

    @Test fun duplicateDisplayNamesFailClosedWithoutChangingEitherProfile() = withStore { store ->
        val first = store.create("agent-first", "Kora", "Analyse", "Ruhig")
        val second = store.create("agent-second", "KORA", "Review", "Präzise")
        try {
            ChiefBotAddressRouter.resolve(store, "kora")
            fail("A duplicate name must not select an arbitrary bot")
        } catch (expected: IllegalStateException) {
            assertEquals("BOT_NAME_AMBIGUOUS", expected.message)
        }
        assertEquals(first, store.get(first.agentId))
        assertEquals(second, store.get(second.agentId))
        assertEquals(first, ChiefBotAddressRouter.resolve(store, first.agentId))
        assertEquals(second, ChiefBotAddressRouter.resolve(store, second.agentId))
    }

    @Test fun unknownExplicitNamesRemainVisibleToTheCallerWithoutCreatingProfiles() = withStore { store ->
        for (text in listOf("@Unbekannt Prüfe lokal.", "Unbekannt: Prüfe lokal.")) {
            val address = ChiefBotAddressRouter.parse(text) as ChiefBotAddressRouter.Address.Message
            assertEquals("Unbekannt", address.name)
            assertEquals("Prüfe lokal.", address.text)
            assertNull(ChiefBotAddressRouter.resolve(store, address.name))
        }
        assertTrue(store.page().items.isEmpty())
    }

    @Test fun unknownNaturalSelectionsReturnNoProfileWithoutChangingTheRegistry() = withStore { store ->
        val profiles = store.seedDefaults(20)
        for ((text, name) in listOf(
            "wechsle zu dunklem Modus" to "dunklem Modus",
            "sprich mit mir über den Plan" to "mir über den Plan"
        )) {
            val address = ChiefBotAddressRouter.parse(text) as ChiefBotAddressRouter.Address.Select
            assertEquals(name, address.name)
            assertNull(ChiefBotAddressRouter.resolve(store, address.name))
        }
        assertEquals(profiles.sortedBy { it.agentId }, store.page(limit = 100).items)
    }

    @Test fun naturalSelectionsStillResolveKnownEditableNamesAndStableIds() = withStore { store ->
        val original = store.seedDefaults().single { it.agentId == "bot-kora" }
        val profile = store.rename(original.agentId, "Kora Entwicklung", original.profileVersion)
        for (text in listOf("rufe Kora Entwicklung auf", "sprich mit kora entwicklung", "wechsle zu bot-kora")) {
            val address = ChiefBotAddressRouter.parse(text) as ChiefBotAddressRouter.Address.Select
            assertEquals(profile, ChiefBotAddressRouter.resolve(store, address.name))
        }
    }

    @Test fun naturalSelectionKeepsAnExplicitMentionDistinguishableFromOrdinaryText() = withStore { store ->
        for (text in listOf("wechsle zu @Unbekannt", "sprich mit @Unbekannt", "rufe @Unbekannt auf")) {
            val address = ChiefBotAddressRouter.parse(text) as ChiefBotAddressRouter.Address.Select
            assertEquals("@Unbekannt", address.name)
            assertNull(ChiefBotAddressRouter.resolve(store, address.name))
        }
    }

    @Test fun unreadableRegistryDoesNotTurnANaturalSelectionIntoAnUnknownName() {
        val directory = Files.createTempDirectory("chief-bot-address-corrupt-").toFile()
        try {
            val store = ChiefBotProfileStore(directory)
            store.seedDefaults(20)
            val registry = java.io.File(directory, "bot-profiles-v1.bin")
            val damaged = byteArrayOf(0)
            registry.writeBytes(damaged)
            val address = ChiefBotAddressRouter.parse("wechsle zu dunklem Modus") as ChiefBotAddressRouter.Address.Select
            assertTrue(runCatching { ChiefBotAddressRouter.resolve(store, address.name) }.isFailure)
            assertTrue(damaged.contentEquals(registry.readBytes()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun addressingNeverRegistersWorkOrGrantsProfileCapabilities() = withStore { store ->
        val before = store.seedDefaults().single { it.agentId == "bot-kora" }
        val address = ChiefBotAddressRouter.parse("@bot-kora: Ändere alle Berechtigungen.") as ChiefBotAddressRouter.Address.Message
        val resolved = ChiefBotAddressRouter.resolve(store, address.name)!!
        assertEquals(before, resolved)
        assertTrue(resolved.permissionScope.isEmpty())
        assertTrue(resolved.verifiedCapabilities.isEmpty())
        assertNull(resolved.currentTaskId)
        assertFalse(resolved.lifecycleState == "RUNNING")
    }

    private fun withStore(block: (ChiefBotProfileStore) -> Unit) {
        val directory = Files.createTempDirectory("chief-bot-address-").toFile()
        try {
            block(ChiefBotProfileStore(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
