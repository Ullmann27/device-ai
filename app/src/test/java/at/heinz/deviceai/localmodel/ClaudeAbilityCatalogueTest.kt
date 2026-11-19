package at.heinz.deviceai.localmodel

import at.heinz.deviceai.OwnerWorkGeneration
import at.heinz.deviceai.OwnerWorkTools
import at.heinz.deviceai.OwnerWorkToolsHarness
import at.heinz.deviceai.Risk
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolSpec
import at.heinz.deviceai.cognitive.NewsTestFeeds
import at.heinz.deviceai.cognitive.OwnerComputerIntentParser
import at.heinz.deviceai.cognitive.OwnerLaneCatalogue
import at.heinz.deviceai.cognitive.OwnerMissionModelPlan
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import at.heinz.deviceai.cognitive.OwnerMissionStep
import at.heinz.deviceai.cognitive.OwnerResearchCommand
import at.heinz.deviceai.sending.OwnerSendIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the cloud brain is told the app can do (field finding 19.11.2026: it answered „Eine PDF kann ich mit meinen
 * Werkzeugen nicht erstellen“ although the export flow exists, and proposed the Shizuku file search for a PDF the
 * document desk finds without Shizuku). The catalogue is generated – tools from the registry, flows from
 * [OwnerLaneCatalogue] – and every sentence it names is checked here against the lane's own parser, so it cannot
 * promise a sentence the app does not take.
 */
class ClaudeAbilityCatalogueTest {
    private val catalogue = ClaudeAbilityCatalogue.text()

    @Test fun everyRegisteredToolAppearsByItsLabelAndBridgeToolsAreMarked() {
        val declared = ClaudeToolCatalog.definitions()
        assertEquals(ToolRegistry.all().size - ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS.size, declared.size)
        for (spec in ToolRegistry.all().filterNot { it.name in ClaudeLocalOnlyGuard.LOCAL_ONLY_TOOLS }) {
            val marked = spec.label + (if (ClaudeToolCatalog.needsBridge(spec.name)) " [Shizuku]" else "") +
                (if (ClaudeToolCatalog.ownerOnly(spec.name)) " [Systemzentrum]" else "")
            assertTrue("${spec.name}: „$marked“ fehlt", Regex("(?:: |; )" + Regex.escape(marked) + "(?:;|\\.)").containsMatchIn(catalogue))
        }
        // The screen reader's result is device-local: it is not offered to the cloud brain at all.
        assertFalse(catalogue.contains(ToolRegistry.get("read_app_screen")!!.label))
        // What „needs the bridge“ means is in the tool's own description as well.
        val find = declared.single { it.name == "find_files" }
        assertTrue(find.description.endsWith(ClaudeToolCatalog.BRIDGE_NOTE))
        assertFalse(declared.single { it.name == "get_battery_info" }.description.contains("Shizuku-Verbindung"))
        for (native in listOf("get_device_info", "list_installed_apps", "get_app_info", "find_app", "get_storage_info",
            "get_network_summary", "get_battery_info", "open_android_settings", "launch_app", "web_search", "web_read"))
            assertFalse(native, ClaudeToolCatalog.needsBridge(native))
        for (bridge in listOf("find_files", "list_files", "delete_file", "move_file", "take_screenshot", "read_logcat", "install_apk"))
            assertTrue(bridge, ClaudeToolCatalog.needsBridge(bridge))
    }

    @Test fun theToolsOfTheSystemCentreAreMarkedAsTheOwnersOwn() {
        // The app refuses these as model proposals (SYSTEM_TOOLS_REQUIRE_EXPLICIT_LOCAL_ACTION); the brain is told so.
        assertEquals(SystemIntelligenceCommands.localTools, ToolRegistry.all().map { it.name }.filter(ClaudeToolCatalog::ownerOnly).toSet())
        val diagnose = ClaudeToolCatalog.definitions().single { it.name == SystemIntelligenceCommands.DIAGNOSE }
        assertTrue(diagnose.description.endsWith(ClaudeToolCatalog.OWNER_ONLY_NOTE))
        assertTrue(catalogue.contains("„[Systemzentrum]“ heißt"))
        assertTrue(catalogue.contains("Analysiere mein Handy"))
        assertTrue(SystemIntelligenceCommands.isDiagnosisRequest("Analysiere mein Handy"))
    }

    @Test fun aNewlyRegisteredToolAppearsWithoutAnyChangeToTheCatalogue() {
        val extended = ToolRegistry.all() + ToolSpec("create_pdf", "PDF erstellen", "Erstellt eine PDF-Datei aus Text.", Risk.CONFIRM,
            required = setOf("path"))
        val text = ClaudeAbilityCatalogue.text(ClaudeToolCatalog.definitions(extended))
        assertTrue(text.contains("; PDF erstellen;") || text.contains("; PDF erstellen."))
        // A tool the registry does not send to the bridge is never called a bridge tool.
        assertFalse(text.contains("PDF erstellen [Shizuku]"))
        assertEquals(ClaudeToolCatalog.definitions().size + 1, ClaudeToolCatalog.definitions(extended).size)
    }

    @Test fun theBridgeSetNamesOnlyRegisteredTools() {
        val names = ToolRegistry.all().map { it.name }.toSet()
        val bridge = names.filter(ClaudeToolCatalog::needsBridge)
        assertEquals(15, bridge.size)
        assertTrue(bridge.all { it in names })
    }

    @Test fun theCatalogueIsByteStableAndCarriesNothingOfATurn() {
        assertEquals(catalogue, ClaudeAbilityCatalogue.text())
        assertEquals(ClaudeAbilityCatalogue.text(ClaudeToolCatalog.definitions()), catalogue)
        assertFalse(Regex("\\d{1,2}\\.\\d{1,2}\\.20\\d{2}|\\d{1,2}:\\d{2}").containsMatchIn(catalogue))
        assertTrue(catalogue.length.toString(), catalogue.length in 1_500..6_000)
        // One constant line names the running model – per selected model, not per turn.
        assertEquals("AKTUELLES GEHIRN (nur für die direkte Frage danach): Continuum antwortet gerade über die Cloud mit dem " +
            "Modell Claude Opus 5.5 von Anthropic (claude-opus-5-5). Das Modell auf dem Gerät ist an dieser Antwort nicht beteiligt.",
            ClaudeAbilityCatalogue.runningOn(ClaudeModel.OPUS_5_5))
        assertEquals(ClaudeModel.entries.size, ClaudeModel.entries.map(ClaudeAbilityCatalogue::runningOn).toSet().size)
    }

    @Test fun theBrainIsToldNeverToDenyAnAbilityAndToPreferTheFlowsWithoutShizuku() {
        assertTrue(catalogue.contains("Behaupte nie, die App könne etwas davon nicht"))
        assertTrue(catalogue.contains("ist er jedem Werkzeug vorzuziehen, das Shizuku braucht"))
        assertTrue(catalogue.contains("Feste Abläufe der App. Sie sind keine Werkzeuge"))
        assertTrue(catalogue.contains("Erfinde keine weiteren Abläufe und keine Ergebnisse."))
        // The two field cases by name: creating a PDF, and finding a PDF by its name without Shizuku.
        assertTrue(catalogue.contains("als PDF oder Word-Datei speichern"))
        assertTrue(catalogue.contains("„Erstelle eine PDF davon“") || catalogue.contains("„Erstelle eine PDF“"))
        assertTrue(catalogue.contains("Such das PDF <Name>"))
        assertTrue(catalogue.contains("auch bei ungefährem Namen"))
        val search = OwnerLaneCatalogue.abilities.single { it.key == "pdf_search" }
        assertFalse(search.needsBridge)
        assertTrue(OwnerLaneCatalogue.lines().single { it.contains("Such das PDF <Name>") }.contains("ohne Shizuku"))
        assertTrue(OwnerLaneCatalogue.lines().single { it.contains("Such das neueste PDF") }.contains("braucht die Shizuku-Verbindung"))
    }

    /**
     * One source for what the app can do. Code71 appended a second, English ability text to the stable instruction
     * block; what it named beyond this catalogue (a WhatsApp draft without the standing grant, the scoped Android
     * apps) is in the catalogue, and the frame carries no further ability text of its own.
     */
    @Test fun theCatalogueIsTheOnlyAbilityTextOfTheFrame() {
        val send = OwnerLaneCatalogue.lines().single { it.contains("Schreib <Name> auf WhatsApp") }
        assertTrue(send, send.contains("mit der Dauerfreigabe des Besitzers für den Kanal wird nach der Prüfung am Bildschirm gesendet"))
        assertTrue(send, send.contains("ohne sie steht nur der Entwurf in WhatsApp bereit"))
        assertTrue(catalogue.contains("„Suche in Gmail nach <Begriff>“"))
        for (head in listOf(ClaudePromptFrame.HEAD_TEXT_ONLY, ClaudePromptFrame.HEAD_TOOLS)) {
            val stable = head + ClaudePromptFrame.OWNER_TURN_INSTRUCTIONS
            // Without the owner frame nothing about abilities is added; with it, exactly the catalogue.
            assertEquals(listOf(ClaudePromptFrame.RUNTIME_PREFACE, stable),
                ClaudePromptFrame.frame(stable + "Was kannst du?", isolatedWorker = false).systemBlocks)
            val framed = ClaudePromptFrame.frame(stable + "Was kannst du?", isolatedWorker = false) { listOf(catalogue) }
            assertEquals(listOf(ClaudePromptFrame.RUNTIME_PREFACE, stable, catalogue), framed.systemBlocks)
            assertEquals(1, framed.systemBlocks.count { it.contains("Feste Abläufe der App") })
            assertTrue(framed.systemBlocks.none { it.contains("app actions") || it.contains("direct chat command") })
        }
    }

    // ── every sentence the catalogue names is one the app takes ───────────────────────────────────────────

    private fun filled(sentence: String): String = sentence.replace("<Name>", "Argon4").replace("<Zahl>", "2")
        .replace("<Adresse>", "anna@example.org").replace("<Text>", "ich komme später").replace("<Begriff>", "Wetter Wien")
        .replace("<Thema>", "Wärmepumpen")

    @Test fun everySentenceOfTheCatalogueIsTakenByTheLaneItNames() {
        val name = "Jarvis"
        for (ability in OwnerLaneCatalogue.abilities) for (pattern in ability.say) {
            val sentence = filled(pattern)
            assertFalse("placeholder left in „$sentence“", sentence.contains('<'))
            val taken: Boolean = when (ability.key) {
                "pdf_search" -> (OwnerMissionPlanner.recognise(sentence, name) as? OwnerMissionStep.OpenPdf)?.open == false
                "pdf_open" -> (OwnerMissionPlanner.recognise(sentence, name) as? OwnerMissionStep.OpenPdf)?.open == true
                "page_summary" -> OwnerMissionPlanner.recognise(sentence, name) is OwnerMissionStep.PageSave
                "news" -> OwnerMissionPlanner.recognise(sentence, name) is OwnerMissionStep.News
                "export" -> OwnerMissionPlanner.recognise(sentence, name) is OwnerMissionStep.Export
                "mail" -> OwnerMissionPlanner.recognise(sentence, name) is OwnerMissionStep.Mail
                "newest_file" -> OwnerMissionPlanner.recognise(sentence, name) is OwnerMissionStep.FindFile
                "mission" -> (OwnerMissionPlanner.plan(sentence, name)?.steps?.size ?: 0) >= 2
                "send" -> OwnerSendIntentParser.parse(sentence, name) != null
                "computer" -> OwnerComputerIntentParser.parse(sentence) != OwnerComputerIntentParser.Result.NotComputerCommand
                "research" -> OwnerResearchCommand.parse(sentence, name) != null
                "system" -> SystemIntelligenceCommands.isDiagnosisRequest(sentence)
                "storage" -> at.heinz.deviceai.deepclean.DeepCleanCommands.parse(sentence, name) != null
                "open_saved" -> openSavedIsTaken(sentence)
                else -> error("no check for ${ability.key}")
            }
            assertTrue("${ability.key}: „$sentence“ is not taken by its lane", taken)
        }
        assertEquals(setOf("pdf_search", "pdf_open", "page_summary", "news", "export", "open_saved", "mail", "send", "mission",
            "newest_file", "computer", "research", "system", "storage"), OwnerLaneCatalogue.abilities.map { it.key }.toSet())
    }

    private fun openSavedIsTaken(sentence: String): Boolean {
        val host = OwnerWorkToolsHarness(model = { OwnerWorkGeneration("x", null, true) })
        val tools = OwnerWorkTools(host, now = { NewsTestFeeds.NOW }, zone = { NewsTestFeeds.ZONE })
        host.foreignTurn("Erklär mir Argon", "Argon ist ein Edelgas mit der Ordnungszahl 18 und kommt in der Luft vor.")
        assertTrue(tools.handle("Speichere das als PDF"))
        return tools.handle(sentence) && host.opened.size == 1
    }

    @Test fun whatMayBeMappedIsExactlyWhatTheMappingPromptOffersAndTheSendLaneIsNotAmongIt() {
        val prompt = OwnerMissionModelPlan.orderPrompt("Kannst du mir bitte mal das Argon-Dokument aufmachen")
        // The send lane, the live computer and the web research are never a mapped order: only the owner's own
        // wording starts them.
        for (key in listOf("send", "computer", "research", "system", "storage")) assertFalse(key, OwnerLaneCatalogue.abilities.single { it.key == key }.mapped)
        assertFalse(prompt.contains("WhatsApp") || prompt.contains("Schreib "))
        for (form in listOf("Such das PDF <Name>", "Öffne das PDF <Name>", "Fass Seite <Zahl> zusammen und speichere es als <Word oder PDF>",
            "Fass mir die Nachrichten von heute zusammen", "Speichere das als <PDF oder Word-Datei>", "Schick das per Mail an <Adresse oder Name>",
            "Such das neueste PDF in <Downloads oder Dokumente>")) assertTrue(form, prompt.contains(form))
        assertTrue(prompt.contains("KEIN PLAN"))
        assertTrue(prompt.endsWith("Auftrag: Kannst du mir bitte mal das Argon-Dokument aufmachen"))
        assertNotNull(OwnerLaneCatalogue.abilities.single { it.key == "export" }.takeIf { it.mapped })
    }
}
