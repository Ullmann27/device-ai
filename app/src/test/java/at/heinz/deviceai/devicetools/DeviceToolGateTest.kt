package at.heinz.deviceai.devicetools

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolExecutionStatus
import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolInvocation
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolPrecheck
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRefusal
import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.ToolSurface
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.localmodel.ClaudeToolCatalog
import at.heinz.deviceai.localmodel.LiteRtToolDeclaration
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The registry, the gate's precheck and the device tool policy: what is declared, what starts, and what is refused before. */
class DeviceToolGateTest {
    private companion object {
        val NEW_TOOLS = setOf("search_device", "read_file", "create_document", "open_file", "list_recent_files",
            "get_folder_overview", "search_file_text", "read_clipboard", "write_clipboard", "share_file")
    }

    private fun request(tool: String, vararg arguments: Pair<String, Any?>) = ToolCallRequest("call-$tool", tool, mapOf(*arguments))

    private fun access(settings: DeviceToolSettings = MemoryDeviceToolSettings(), cloud: Boolean = false, bridge: Boolean = false,
                       native: NativeDeviceTools? = null) = DeviceToolAccess(settings, { cloud }, { bridge }, { native })

    // ── registry and schema ─────────────────────────────────────────────────────────────────────────────────

    @Test fun everyRegisteredToolIsClassifiedAndTheNewToolsHaveTheirRiskClass() {
        assertEquals(emptyList<String>(), ToolRegistry.all().map { it.name }.filterNot(DeviceToolPolicy::classified))
        assertEquals(43, ToolRegistry.all().size)
        mapOf("search_device" to Risk.READ_ONLY, "read_file" to Risk.READ_ONLY, "open_file" to Risk.READ_ONLY,
            "list_recent_files" to Risk.READ_ONLY, "get_folder_overview" to Risk.READ_ONLY, "search_file_text" to Risk.READ_ONLY,
            "create_document" to Risk.CONFIRM, "read_clipboard" to Risk.CONFIRM, "write_clipboard" to Risk.CONFIRM,
            "share_file" to Risk.CONFIRM).forEach { (tool, risk) -> assertEquals(tool, risk, ToolRegistry.risk(tool)) }
        // The system-centre surface is exactly the set the Activity only dispatches for its own calls.
        assertEquals(SystemIntelligenceCommands.localTools, ToolRegistry.all().filter { it.surface == ToolSurface.SYSTEM_CENTRE }.map { it.name }.toSet())
        assertTrue(ToolRegistry.all().all { it.label.isNotBlank() && it.description.length >= 20 })
        // The compiled context quotes at most 160 characters of a description; the new ones fit whole.
        assertTrue(ToolRegistry.all().filter { it.name in NEW_TOOLS }.all { it.description.length <= 160 })
        assertEquals(10, NEW_TOOLS.count { ToolRegistry.get(it) != null })
    }

    @Test fun argumentTypesAreCompleteAndEqualForBothRuntimes() {
        ToolRegistry.all().forEach { spec ->
            spec.schema.forEach { (argument, schema) ->
                val local = LiteRtToolDeclaration.jsonType(argument)
                assertEquals("${spec.name}.$argument", local, ClaudeToolCatalog.jsonType(argument))
                // What the validator checks as a number or a list must be declared as one; text stays text.
                val expected = when (schema) {
                    is at.heinz.deviceai.ArgSchema.Integer -> "integer"
                    at.heinz.deviceai.ArgSchema.PathList -> "array"
                    at.heinz.deviceai.ArgSchema.Flag -> if (argument == "includeOwnCachePreview") "string" else "boolean"
                    else -> "string"
                }
                assertEquals("${spec.name}.$argument", expected, local)
            }
            val declared = org.json.JSONObject(LiteRtToolDeclaration.json(spec))
            assertEquals(spec.name, declared.getString("name"))
            assertEquals((spec.required + spec.optional), declared.getJSONObject("parameters").getJSONObject("properties").keys().asSequence().toSet())
        }
        // A value of every declared type passes the proposal validation of every tool.
        ToolRegistry.all().forEach { spec ->
            val arguments = (spec.required + spec.optional).associateWith { argument ->
                when (LiteRtToolDeclaration.jsonType(argument)) {
                    "integer" -> 5
                    "boolean" -> true
                    "array" -> listOf("Download")
                    else -> when (argument) {
                        "url" -> "https://example.org/seite"
                        "packageOrName" -> "com.example.app"
                        "includeOwnCachePreview" -> "true"
                        "path", "source", "destination" -> "Download/datei.pdf"
                        else -> "wert"
                    }
                }
            }
            assertNull(spec.name, ToolProposal(spec.name, arguments).validate())
        }
    }

    @Test fun aDocumentMayBeLongAndMultiLineButNothingElseGrows() {
        val content = "# Titel\n\n" + "Zeile mit Text.\n".repeat(1_400)
        assertTrue(content.length in 20_000..24_000)
        assertNull(ToolProposal("create_document", mapOf("title" to "Bericht", "content" to content)).validate())
        assertNotNull(ToolProposal("create_document", mapOf("title" to "Bericht", "content" to "x".repeat(24_001))).validate())
        assertNotNull("a control character is never part of a document", ToolProposal("create_document", mapOf("title" to "B", "content" to "a\u0007b")).validate())
        assertNotNull("a title is one line", ToolProposal("create_document", mapOf("title" to "A\nB", "content" to "Text")).validate())
        assertNotNull(ToolProposal("find_files", mapOf("query" to "x".repeat(4_097))).validate())
        assertNotNull(ToolProposal("read_file", mapOf("path" to "/sdcard/../data/x")).validate())
        assertNull(ToolProposal("read_file", mapOf("path" to "/sdcard/Download/a.pdf", "pages" to "2-4", "offset" to 4000)).validate())
        assertNotNull(ToolProposal("read_file", mapOf("path" to "/sdcard/Download/a.pdf", "offset" to -1)).validate())
    }

    @Test fun theOnDeviceModelIsToldLessThanBeforeAlthoughItGotNewTools() {
        val declared = ToolRegistry.onDeviceModel()
        val bytes = declared.sumOf { LiteRtToolDeclaration.json(it).toByteArray(Charsets.UTF_8).size }
        val everything = ToolRegistry.all().sumOf { LiteRtToolDeclaration.json(it).toByteArray(Charsets.UTF_8).size }
        // What the on-device model was told before this change: the 33 tools that existed, all of them.
        val before = ToolRegistry.all().filterNot { it.name in NEW_TOOLS }.sumOf { LiteRtToolDeclaration.json(it).toByteArray(Charsets.UTF_8).size }
        println("TOOL-DECLARATION on-device: ${declared.size} tools, $bytes bytes (before: 33 tools, $before bytes); " +
            "all ${ToolRegistry.all().size} tools would be $everything bytes")
        // Every declaration takes room in the 4 096-token window (LocalNativePromptByteBudgetTest: ≈ 7 043 bytes ≈ 2 348
        // tokens for the 33 tools). With four new tools the declaration must not be larger than it was.
        assertTrue("on-device declarations grew from $before to $bytes bytes", bytes <= before)
        assertEquals(30, declared.size)
        assertTrue(declared.map { it.name }.containsAll(listOf("search_device", "read_file", "create_document", "open_file", "find_files", "list_files")))
        assertTrue(declared.none { it.name in SystemIntelligenceCommands.localTools || it.surface != ToolSurface.EVERY_MODEL })
        // The cloud brain's catalogue is generated from the whole registry.
        assertTrue(ClaudeToolCatalog.definitions().map { it.name }.containsAll(listOf("list_recent_files", "search_file_text", "share_file")))
    }

    // ── the gate's precheck ─────────────────────────────────────────────────────────────────────────────────

    @Test fun aBridgeOnlyToolWithoutTheBridgeIsRefusedBeforeDispatchAndNeverUnknown() {
        val dispatched = AtomicInteger()
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), ToolRunner { dispatched.incrementAndGet(); "ran" },
            precheck = access(bridge = false).turn("Stoppe WhatsApp", modelTurn = true))
        for ((tool, arguments) in listOf("stop_app" to mapOf("packageOrName" to "com.whatsapp"), "take_screenshot" to emptyMap(),
            "list_processes" to emptyMap<String, Any?>(), "uninstall_app" to mapOf("packageOrName" to "com.whatsapp"))) {
            val result = gate.execute(ToolInvocation("no-bridge-$tool", ToolCallRequest("no-bridge-$tool", tool, arguments)), userConfirmed = true, destructiveConfirmed = true)
            assertEquals(tool, ToolExecutionStatus.REJECTED, result.record.status)
            assertFalse(tool, result.record.outcomeUncertain)
            assertEquals(tool, "BRIDGE_NOT_RUNNING", result.refusal!!.code)
            assertTrue(result.output, result.output!!.contains("muss Shizuku laufen") && result.output!!.contains("nichts ausgeführt"))
        }
        assertEquals(0, dispatched.get())
        // With the bridge the same gate dispatches; a tool that needs no bridge never asked for it.
        val ready = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "ran" }, precheck = access(bridge = true).turn("", modelTurn = true))
        assertEquals("ran", ready.execute(ToolInvocation("b1", request("list_processes").copy(callId = "b1"))).output)
        val none = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "akku" }, precheck = access(bridge = false).turn("", modelTurn = true))
        assertEquals("akku", none.execute(ToolInvocation("b2", request("get_battery_info").copy(callId = "b2"))).output)
    }

    @Test fun aPrecheckThatBreaksRefusesAndARefusedCallIdStaysRefused() {
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "ran" }, precheck = ToolPrecheck { error("kaputt") })
        val call = ToolCallRequest("p1", "get_battery_info", emptyMap())
        val result = gate.execute(ToolInvocation("p1", call))
        assertEquals(ToolExecutionStatus.REJECTED, result.record.status)
        assertEquals("PRECHECK_FAILED", result.refusal!!.code)
        // An unconfirmed changing tool is still rejected by the gate's own rule when the precheck has no objection.
        val plain = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { "ran" }, precheck = ToolPrecheck.NONE)
        val unconfirmed = plain.execute(ToolInvocation("p2", ToolCallRequest("p2", "create_document", mapOf("title" to "A", "content" to "B"))))
        assertEquals(ToolExecutionStatus.REJECTED, unconfirmed.record.status)
        assertNull(unconfirmed.refusal)
        assertNull(unconfirmed.output)
    }

    @Test fun theBrainTurnEndsWithThePlainReasonAndNobodyIsAskedToApproveWhatCannotRun() {
        val approvals = AtomicInteger()
        val dispatched = AtomicInteger()
        fun run(tool: String, arguments: Map<String, Any?>, access: DeviceToolAccess, owner: String) = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations ->
                if (observations.isEmpty()) AgentModelOutput("test-model", "", ToolProposal(tool, arguments)) else AgentModelOutput("test-model", "Fertig, alles erledigt.")
            },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), ToolRunner { dispatched.incrementAndGet(); "ran" },
                precheck = access.turn(owner, modelTurn = true)),
            evaluator = AgentEvaluator { _, _ -> Evaluation(true, 1.0) },
            experienceStore = InMemoryExperienceStore(),
            approvalProvider = AgentToolApprovalProvider { _, _ -> approvals.incrementAndGet(); AgentToolApproval(true, true) }
        ).run(owner)

        val stop = run("stop_app", mapOf("packageOrName" to "com.whatsapp"), access(bridge = false), "Stoppe WhatsApp")
        assertTrue(stop.response, stop.response.startsWith("Für „App stoppen“ muss Shizuku laufen"))
        assertEquals(stop.response, stop.blocker)
        assertFalse(stop.response.contains("UNKNOWN") || stop.response.contains("Besitzerprüfung") || stop.response.contains("Fertig"))
        assertEquals("no approval for a call that cannot run", 0, approvals.get())
        // Cloud brain without the names consent: nothing is searched, the owner learns both ways out.
        val search = run("search_device", mapOf("query" to "Experimente"), access(cloud = true), "ich suche eine PDF die heißt Experimente")
        assertTrue(search.response, search.response.contains("Claude (Cloud)") && search.response.contains("„Dateinamen an Claude erlauben“") &&
            search.response.contains("lokale Modell"))
        // … and with the consent, or with the on-device model, the same call is dispatched.
        assertEquals("Fertig, alles erledigt.", run("search_device", mapOf("query" to "Experimente"),
            access(MemoryDeviceToolSettings(cloudNames = true), cloud = true), "such Experimente").response)
        assertEquals("Fertig, alles erledigt.", run("search_device", mapOf("query" to "Experimente"), access(cloud = false), "such Experimente").response)
        assertEquals(2, dispatched.get())
        assertEquals(0, approvals.get())
    }

    @Test fun bothBrainsAndBothConsentsDecideSeparatelyForNamesAndContent() {
        val storage = TestStorage()
        try {
            val native = testTools(storage)
            fun refusal(tool: String, settings: DeviceToolSettings, cloud: Boolean, modelTurn: Boolean = true, vararg arguments: Pair<String, Any?>) =
                access(settings, cloud, native = native).turn("Zeig mir die Datei", modelTurn).refusal(request(tool, *arguments))?.code
            val nothing = MemoryDeviceToolSettings()
            val names = MemoryDeviceToolSettings(cloudNames = true)
            val content = MemoryDeviceToolSettings(cloudContent = true)
            // On-device model: no consent is needed for anything.
            for (tool in listOf("search_device", "list_installed_apps", "find_files", "read_clipboard"))
                assertNull(tool, refusal(tool, nothing, cloud = false, arguments = arrayOf("query" to "x")))
            assertNull(refusal("read_file", nothing, cloud = false, arguments = arrayOf("path" to "/sdcard/Download/a.txt")))
            // Cloud brain: names and content are two switches.
            assertEquals("CLOUD_NAMES_CONSENT_MISSING", refusal("search_device", nothing, cloud = true, arguments = arrayOf("query" to "x")))
            assertEquals("CLOUD_NAMES_CONSENT_MISSING", refusal("search_device", content, cloud = true, arguments = arrayOf("query" to "x")))
            assertNull(refusal("search_device", names, cloud = true, arguments = arrayOf("query" to "x")))
            assertEquals("CLOUD_CONTENT_CONSENT_MISSING", refusal("read_file", names, cloud = true, arguments = arrayOf("path" to "/sdcard/Download/a.txt")))
            assertNull(refusal("read_file", content, cloud = true, arguments = arrayOf("path" to "/sdcard/Download/a.txt")))
            // A fixed in-app sequence does not feed the brain; nothing is refused for privacy there.
            assertNull(refusal("search_device", nothing, cloud = true, modelTurn = false, arguments = arrayOf("query" to "x")))
            // What reveals neither names nor content runs with the cloud brain as before.
            for (tool in listOf("get_battery_info", "get_storage_info", "get_device_info", "get_network_summary"))
                assertNull(tool, refusal(tool, nothing, cloud = true))
            // The owner's switch flips the decision at once.
            val settings = MemoryDeviceToolSettings()
            val turn = access(settings, cloud = true, native = native).turn("such", modelTurn = true)
            assertNotNull(turn.refusal(request("find_files", "query" to "x")))
            assertTrue(DeviceToolCommand.apply(DeviceToolCommand.parse("Dateinamen an Claude erlauben")!!, settings).startsWith("Erlaubt:"))
            assertNull(turn.refusal(request("find_files", "query" to "x")))
            DeviceToolCommand.apply(DeviceToolCommand.Command.NAMES_OFF, settings)
            assertNotNull(turn.refusal(request("find_files", "query" to "x")))
            // Missing grant: the desk's wording and the way to give it; with the bridge the shell tools still run.
            storage.granted = at.heinz.deviceai.OwnerLibraryAccess.MISSING
            val missing = access(native = native).turn("", modelTurn = true).refusal(request("find_files", "query" to "x"))!!
            assertEquals("ALL_FILES_ACCESS_MISSING", missing.code)
            assertTrue(missing.message, missing.message.contains("„Zugriff auf alle Dateien“") && missing.message.contains("Dateizugriff einrichten") &&
                missing.message.contains("nichts durchsucht"))
            assertNull(access(bridge = true, native = native).turn("", modelTurn = true).refusal(request("find_files", "query" to "x")))
            assertEquals("ALL_FILES_ACCESS_MISSING", access(bridge = true, native = native).turn("", modelTurn = true)
                .refusal(request("read_file", "path" to "/sdcard/Download/a.txt"))!!.code)
        } finally { storage.delete() }
    }

    @Test fun systemCentreToolsRunOnlyForTheSystemCentresOwnCall() {
        val turn = access().turn("Analysiere", modelTurn = false, systemCallId = "system-1")
        assertNull(turn.refusal(ToolCallRequest("system-1", SystemIntelligenceCommands.DIAGNOSE, emptyMap())))
        assertEquals("SYSTEM_CENTRE_ONLY", turn.refusal(ToolCallRequest("model-7", SystemIntelligenceCommands.DIAGNOSE, emptyMap()))!!.code)
        assertEquals("SYSTEM_CENTRE_ONLY", access().turn("", modelTurn = true).refusal(request(SystemIntelligenceCommands.PREVIEW))!!.code)
    }

    @Test fun aTurnCreatesAtMostTwoDocumentsShowsOneFileAndAsksOnlyWhenTheOwnerDidNotOrderIt() {
        val settings = MemoryDeviceToolSettings()
        val access = access(settings)
        val turn = access.turn("Erstelle bitte eine PDF und zeig sie mir dann", modelTurn = true)
        val create = request("create_document", "title" to "A", "content" to "B")
        assertNull(turn.refusal(create))
        assertEquals("Gespeichert: /sdcard/Download/A.pdf", turn.run(create) { "Gespeichert: /sdcard/Download/A.pdf" })
        turn.run(create) { "Nicht angelegt: leer" } // a document that was not written does not count
        assertNull(turn.refusal(create))
        turn.run(create) { "Gespeichert: /sdcard/Download/B.pdf" }
        assertEquals("DOCUMENT_LIMIT", turn.refusal(create)!!.code)
        assertEquals("DOCUMENT_FORMAT_UNKNOWN", access.turn("Erstelle eine PDF", true).refusal(request("create_document", "title" to "A", "content" to "B", "format" to "wert"))!!.code)

        // The switch is off: every document goes to the dialog. On: only a document the owner ordered himself skips it.
        val asked = AtomicInteger()
        val dialog = AgentToolApprovalProvider { _, _ -> asked.incrementAndGet(); AgentToolApproval(false) }
        val proposal = ToolProposal("create_document", mapOf("title" to "A", "content" to "B"))
        assertFalse(access.approvals("Erstelle bitte eine PDF", dialog).requestApproval(proposal, Risk.CONFIRM).userConfirmed)
        assertEquals(1, asked.get())
        assertTrue(DeviceToolCommand.apply(DeviceToolCommand.Command.DOCS_ON, settings).startsWith("An:"))
        assertTrue(access.approvals("Erstelle bitte eine PDF", dialog).requestApproval(proposal, Risk.CONFIRM).userConfirmed)
        assertEquals("ordered by the owner: no dialog", 1, asked.get())
        assertFalse(access.approvals("Welche PDFs habe ich?", dialog).requestApproval(proposal, Risk.CONFIRM).userConfirmed)
        assertEquals(2, asked.get())
        // The switch covers documents only – never another changing tool.
        assertFalse(access.approvals("Erstelle eine PDF und lösch die alte Datei", dialog)
            .requestApproval(ToolProposal("delete_file", mapOf("path" to "/sdcard/Download/a.txt")), Risk.DESTRUCTIVE).userConfirmed)
        assertEquals(3, asked.get())
        val text = access.confirmationText(ToolProposal("create_document", mapOf("title" to "Einkauf", "content" to "- Milch\n- Brot", "format" to "docx")))!!
        assertTrue(text, text.contains("Titel: Einkauf") && text.contains("Format: Word-Datei") && text.contains("nichts wird überschrieben") &&
            text.contains("- Milch"))
        assertNull(access.confirmationText(ToolProposal("stop_app", mapOf("packageOrName" to "com.whatsapp"))))
    }

    @Test fun switchCommandsReportTheStateAndAllStartSwitchedOff() {
        val settings = MemoryDeviceToolSettings()
        assertFalse(settings.cloudNames || settings.cloudContent || settings.documentsWithoutAsking)
        val access = access(settings)
        val status = access.command("Werkzeugfreigaben zeigen", null)!!
        assertTrue(status.text, status.text.contains("Namen von Dateien und Apps an Claude: gesperrt") &&
            status.text.contains("Inhalte von deinem Gerät an Claude: gesperrt") && status.text.contains("Dokumente ohne Rückfrage anlegen: aus"))
        assertFalse(status.openFileAccessSettings)
        assertTrue(access.command("Dateiinhalte an Claude erlauben", null)!!.text.startsWith("Erlaubt:"))
        assertTrue(settings.cloudContent && !settings.cloudNames)
        assertTrue(access.command("Dateizugriff einrichten", "Jarvis")!!.openFileAccessSettings)
        assertNull(access.command("Wie spät ist es?", null))
        assertTrue(access.command("Welche Werkzeugfreigaben sind aktiv?", null)!!.text.contains("Inhalte von deinem Gerät an Claude: erlaubt"))
    }

    @Test fun onlyTheOwnersOwnWordsOpenAFile() {
        val storage = TestStorage()
        try {
            val native = testTools(storage)
            val open = request("open_file", "path" to "/sdcard/Download/a.pdf")
            assertEquals("OPEN_NOT_ORDERED", access(native = native).turn("Welche PDFs habe ich?", true).refusal(open)!!.code)
            val turn = access(native = native).turn("Zeig mir die PDF", true)
            assertNull(turn.refusal(open))
            turn.run(open) { "Geöffnet: /sdcard/Download/a.pdf" }
            assertEquals("OPEN_LIMIT", turn.refusal(open)!!.code)
            assertTrue(ToolRefusal("OPEN_LIMIT", "x").code.isNotEmpty())
        } finally { storage.delete() }
    }
}
