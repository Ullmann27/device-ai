package at.heinz.deviceai.devicetools

import android.content.Context
import at.heinz.deviceai.DeviceToolExecutor
import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolCallRequest
import at.heinz.deviceai.ToolPrecheck
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRefusal
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one door between the app and the device tool layer. The Activity builds it once and asks it for
 *  - [turn]: the precheck of one owner turn's ToolGate (bridge, grant, cloud consent, per-turn bounds),
 *  - [approvals]: the owner switch „Dokumente ohne Rückfrage“ in front of the existing confirmation dialog,
 *  - [command]: the typed or spoken switch commands,
 *  - [confirmationText]: what the confirmation dialog says for a document, in the owner's words.
 * Nothing here executes a tool; execution stays with DeviceToolExecutor behind the ToolGate.
 */
internal class DeviceToolAccess(
    private val settings: DeviceToolSettings,
    private val cloudBrain: () -> Boolean,
    private val bridgeReady: () -> Boolean,
    private val nativeTools: () -> NativeDeviceTools?
) {
    /**
     * [executor] is asked every time: bridge state and in-process tools are those of the executor that will really
     * run the call (the Activity's field), never a copy from start-up.
     */
    constructor(context: Context, executor: () -> DeviceToolExecutor, cloudBrain: () -> Boolean) :
        this(StoredDeviceToolSettings(context), cloudBrain, { executor().bridgeIsReady() }, { executor().native }) {
        // The on-device model's window is small; only a large-context brain gets the long results.
        runCatching { executor().native?.largeContext = cloudBrain }
    }

    private val native: NativeDeviceTools? get() = runCatching(nativeTools).getOrNull()

    /** A bridge-only tool right now, without the bridge: the caller may say so before it builds a whole turn. */
    fun blockedByMissingBridge(tool: String?): Boolean = tool != null && DeviceToolPolicy.needsBridge(tool) &&
        !runCatching(bridgeReady).getOrDefault(false)

    /**
     * The precheck and counters of ONE owner turn. [modelTurn]: the results go back to the active brain (the owner's
     * own chat turn); a fixed in-app sequence or the system centre is not one. [systemCallId]: the single call the
     * system centre started itself.
     */
    fun turn(ownerTurn: String, modelTurn: Boolean, systemCallId: String? = null): DeviceToolTurn =
        DeviceToolTurn(this, ownerTurn, modelTurn, systemCallId)

    internal fun state(ownerTurn: String, modelTurn: Boolean, systemCall: Boolean, documents: Int, opens: Int) =
        DeviceToolPolicy.State(
            cloudBrain = runCatching(cloudBrain).getOrDefault(true),
            modelTurn = modelTurn,
            namesConsent = settings.cloudNames,
            contentConsent = settings.cloudContent,
            bridgeReady = runCatching(bridgeReady).getOrDefault(false),
            filesAccess = native?.filesGranted() == true,
            ownerTurn = ownerTurn,
            documentsCreated = documents,
            filesOpened = opens,
            systemCall = systemCall,
            readable = { path -> native?.readable(path) == true })

    /** What a native changing tool would refuse anyway, said before the owner is asked to approve it. */
    internal fun changeProblem(request: ToolCallRequest): ToolRefusal? {
        val files = native?.takeIf { it.filesGranted() }?.files ?: return null
        val problem = when (request.name) {
            "delete_file" -> files.deleteProblem(request.arguments["path"]?.toString().orEmpty())
            "move_file" -> files.moveProblem(request.arguments["source"]?.toString().orEmpty(), request.arguments["destination"]?.toString().orEmpty())
            else -> null
        } ?: return null
        return ToolRefusal("FILE_CHANGE_NOT_POSSIBLE", problem)
    }

    /**
     * With the owner's switch on, a document he ordered in this very sentence is created without the dialog; every
     * other call – and every document he did not order – goes to [delegate] (the existing confirmation dialog).
     */
    fun approvals(ownerTurn: String, delegate: AgentToolApprovalProvider): AgentToolApprovalProvider =
        AgentToolApprovalProvider { proposal, risk ->
            if (preApproved(proposal.toolName, risk, ownerTurn)) AgentToolApproval(true) else delegate.requestApproval(proposal, risk)
        }

    fun preApproved(tool: String, risk: Risk, ownerTurn: String): Boolean = tool == "create_document" && risk == Risk.CONFIRM &&
        settings.documentsWithoutAsking && DeviceOwnerWish.ordersDocument(ownerTurn)

    class CommandReply(val text: String, val openFileAccessSettings: Boolean)

    /** The reply to a switch command, or null when [utterance] is none. */
    fun command(utterance: String, assistantName: String?): CommandReply? {
        val command = DeviceToolCommand.parse(utterance, assistantName) ?: return null
        return when (command) {
            DeviceToolCommand.Command.STATUS -> CommandReply(DeviceToolCommand.status(settings, native?.filesGranted()), false)
            DeviceToolCommand.Command.FILE_ACCESS -> if (native?.filesGranted() == true)
                CommandReply("„Zugriff auf alle Dateien“ ist für Continuum schon erlaubt. Du kannst ihn in Android jederzeit wieder ausschalten.", false)
                else CommandReply("Ich öffne die Android-Einstellung. Schalte dort „Zugriff auf alle Dateien zulassen“ für Continuum ein und " +
                    "komm zurück. Ohne diese Erlaubnis lese und durchsuche ich keine Datei.", true)
            else -> CommandReply(DeviceToolCommand.apply(command, settings), false)
        }
    }

    /** The dialog text for a tool whose arguments the owner should read in full; null keeps the generic summary. */
    fun confirmationText(proposal: ToolProposal): String? {
        fun shown(key: String, max: Int) = DeviceSearch.clean(proposal.arguments[key]?.toString().orEmpty()).take(max)
        return when (proposal.toolName) {
            "create_document" -> {
                val content = proposal.arguments["content"]?.toString().orEmpty()
                val format = DeviceDocumentWriter.formatOf(proposal.arguments["format"]?.toString())?.label ?: "unbekannt"
                "Dokument erstellen\n\nTitel: ${shown("title", 160)}\nFormat: $format\nZiel: Download-Ordner – eine neue Datei, " +
                    "nichts wird überschrieben" + (shown("name", 100).takeIf { it.isNotEmpty() }?.let { "\nDateiname: $it" } ?: "") +
                    "\nUmfang: ${content.length} Zeichen\n\nAnfang:\n${DeviceFileText.normalise(content).take(400)}" +
                    (if (content.length > 400) " …" else "") + "\n\nDauerhaft ohne diese Rückfrage: „${DeviceToolCommand.SAY_DOCS_ON}“."
            }
            "write_clipboard" -> "In Zwischenablage kopieren\n\nDer jetzige Inhalt der Zwischenablage wird ersetzt durch:\n" +
                DeviceFileText.normalise(proposal.arguments["text"]?.toString().orEmpty()).take(600)
            "read_clipboard" -> "Zwischenablage lesen\n\nIch lese einmal den Text, der gerade in der Zwischenablage liegt" +
                (if (runCatching(cloudBrain).getOrDefault(true)) ", und gebe ihn an Claude (Cloud) weiter." else "; er bleibt auf dem Gerät.")
            "share_file" -> "Datei teilen\n\nDatei: ${shown("path", 300)}\nIch öffne Androids Teilen-Dialog. Das Ziel wählst du dort " +
                "selbst; ich sende nichts."
            else -> null
        }
    }

}

/** One owner turn of the device tools: asked by the ToolGate before approval and dispatch, told about real effects. */
internal class DeviceToolTurn(
    private val access: DeviceToolAccess,
    private val ownerTurn: String,
    private val modelTurn: Boolean,
    private val systemCallId: String?
) : ToolPrecheck {
    private val documents = AtomicInteger()
    private val opens = AtomicInteger()

    override fun refusal(request: ToolCallRequest): ToolRefusal? {
        val state = access.state(ownerTurn, modelTurn, systemCallId != null && request.callId == systemCallId, documents.get(), opens.get())
        return when (val decision = DeviceToolPolicy.decide(request.name, request.arguments, state)) {
            is DeviceToolPolicy.Decision.Refused -> decision.refusal
            is DeviceToolPolicy.Decision.Run -> if (decision.route == DeviceToolPolicy.Route.NATIVE) access.changeProblem(request) else null
        }
    }

    /** Runs the call's backend and counts what really happened in this turn (a created document, a shown file). */
    fun run(request: ToolCallRequest, backend: () -> String): String = backend().also { output ->
        when (request.name) {
            "create_document" -> if (output.startsWith("Gespeichert:")) documents.incrementAndGet()
            "open_file" -> if (output.startsWith("Geöffnet:")) opens.incrementAndGet()
        }
    }
}
