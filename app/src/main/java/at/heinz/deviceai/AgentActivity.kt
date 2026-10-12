package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class AgentActivity : Activity() {

    private enum class AgentMode {
        AUTO,
        CLOUD,
        LOCAL
    }

    companion object {
        private const val SPEECH_REQUEST = 2207
        private val BG = Color.rgb(7, 10, 16)
        private val PANEL = Color.rgb(15, 21, 31)
        private val PANEL_2 = Color.rgb(21, 29, 42)
        private val SURFACE = Color.rgb(27, 37, 53)
        private val BORDER = Color.rgb(48, 64, 87)
        private val TEXT = Color.rgb(245, 248, 253)
        private val MUTED = Color.rgb(155, 169, 191)
        private val ACCENT = Color.rgb(111, 143, 255)
        private val ACCENT_DARK = Color.rgb(54, 76, 154)
        private val GREEN = Color.rgb(78, 214, 145)
        private val AMBER = Color.rgb(255, 192, 82)
        private val RED = Color.rgb(255, 102, 112)
        private val USER_BUBBLE = Color.rgb(48, 72, 148)
        private val ASSISTANT_BUBBLE = Color.rgb(24, 33, 47)
    }

    private lateinit var bridge: ShizukuBridge
    private lateinit var interpreter: CommandInterpreter
    private lateinit var executor: DeviceToolExecutor
    private lateinit var backend: AgentBackendClient

    private lateinit var shizukuBadge: TextView
    private lateinit var cloudBadge: TextView
    private lateinit var activityText: TextView
    private lateinit var planCard: LinearLayout
    private lateinit var planText: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendButton: Button

    private val modeButtons = linkedMapOf<AgentMode, Button>()
    private var mode = AgentMode.AUTO
    private var sessionId = UUID.randomUUID().toString()
    private var busy = false
    private var agentLoops = 0
    private var pendingUserText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG

        interpreter = CommandInterpreter(this)
        backend = AgentBackendClient(this)
        bridge = ShizukuBridge(this) { message ->
            runOnUiThread {
                if (::shizukuBadge.isInitialized) updateShizukuBadge(message)
            }
        }
        executor = DeviceToolExecutor(this, bridge)

        setContentView(buildUi())
        bridge.start()
        updateCloudBadge()

        addAssistantMessage(
            "Bereit. Du kannst mir normale Aufträge geben. " +
                "Im Auto-Modus nutze ich die Cloud-KI, sobald dein HTTPS-Backend eingerichtet ist, " +
                "sonst arbeite ich lokal über Shizuku."
        )
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) bridge.refresh()
        if (::cloudBadge.isInitialized) updateCloudBadge()
    }

    override fun onDestroy() {
        if (::bridge.isInitialized) bridge.stop()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val wide = resources.configuration.screenWidthDp >= 700
        val root = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        if (wide) {
            root.addView(
                buildSidebar(),
                LinearLayout.LayoutParams(dp(292), LinearLayout.LayoutParams.MATCH_PARENT)
            )
            root.addView(
                buildConversationPane(wide = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        } else {
            root.addView(
                buildConversationPane(wide = false),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        return root
    }

    private fun buildSidebar(): View {
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(20))
            setBackgroundColor(Color.rgb(10, 15, 23))
        }

        side.addView(textView("DEVICE AI", 12f, ACCENT, true))
        side.addView(
            textView("Persönlicher\nAndroid-Agent", 27f, TEXT, true).apply {
                setPadding(0, dp(8), 0, dp(18))
            }
        )

        side.addView(
            actionButton("＋ Neue Sitzung", accent = true).apply {
                setOnClickListener { newSession() }
            },
            matchWidth()
        )

        side.addView(sectionLabel("SKILLS", top = 24))
        side.addView(
            infoCard(
                "Lumo",
                LumoSkill.description,
                "AKTIV"
            ),
            matchWidth(top = 8)
        )

        side.addView(sectionLabel("WERKZEUGE", top = 22))
        side.addView(
            actionButton("Lokaler Expertenmodus").apply {
                setOnClickListener {
                    startActivity(Intent(this@AgentActivity, MainActivity::class.java))
                }
            },
            matchWidth(top = 8)
        )

        val spacer = View(this)
        side.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        side.addView(textView("v${BuildConfig.VERSION_NAME} · kein Root", 11f, MUTED))
        return side
    }

    private fun buildConversationPane(wide: Boolean): View {
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                if (wide) dp(22) else dp(14),
                if (wide) dp(20) else dp(14),
                if (wide) dp(22) else dp(14),
                dp(14)
            )
        }

        pane.addView(buildHeader(wide))
        pane.addView(buildStatusRow(), matchWidth(top = 12))
        pane.addView(buildModeRow(), matchWidth(top = 10))
        pane.addView(buildQuickActions(), matchWidth(top = 10))

        planCard = verticalCard().apply {
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        planCard.addView(textView("AKTIONSPLAN", 11f, ACCENT, true))
        planText = textView("", 13f, TEXT).apply {
            setPadding(0, dp(7), 0, 0)
        }
        planCard.addView(planText)
        pane.addView(planCard, matchWidth(top = 10))

        activityText = textView("Bereit", 12f, MUTED).apply {
            setPadding(dp(4), dp(8), dp(4), dp(7))
        }
        pane.addView(activityText, matchWidth())

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(10))
        }
        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(4), 0, dp(4))
        }
        chatScroll.addView(chatContainer, matchWidth())
        pane.addView(
            chatScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pane.addView(buildComposer(), matchWidth())
        return pane
    }

    private fun buildHeader(wide: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBox.addView(textView("Device AI", if (wide) 30f else 26f, TEXT, true))
        titleBox.addView(
            textView("Frag. Plane. Führe sicher aus.", 13f, MUTED).apply {
                setPadding(0, dp(2), 0, 0)
            }
        )

        row.addView(
            titleBox,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        if (!wide) {
            row.addView(
                actionButton("EXPERT", compact = true).apply {
                    setOnClickListener {
                        startActivity(Intent(this@AgentActivity, MainActivity::class.java))
                    }
                },
                LinearLayout.LayoutParams(dp(88), dp(42)).apply { marginEnd = dp(7) }
            )
        }

        row.addView(
            actionButton("⚙", compact = true).apply {
                contentDescription = "Cloud-Einstellungen"
                setOnClickListener { showCloudSettings() }
            },
            LinearLayout.LayoutParams(dp(50), dp(42))
        )

        return row
    }

    private fun buildStatusRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        shizukuBadge = statusBadge("● SHIZUKU …", AMBER)
        cloudBadge = statusBadge("● CLOUD …", AMBER)

        row.addView(
            shizukuBadge,
            LinearLayout.LayoutParams(0, dp(39), 1f).apply { marginEnd = dp(5) }
        )
        row.addView(
            cloudBadge,
            LinearLayout.LayoutParams(0, dp(39), 1f).apply { marginStart = dp(5) }
        )
        return row
    }

    private fun buildModeRow(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(PANEL, 16, BORDER)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        val choices = listOf(
            AgentMode.AUTO to "AUTO",
            AgentMode.CLOUD to "ONLINE",
            AgentMode.LOCAL to "LOKAL"
        )

        choices.forEachIndexed { index, (value, label) ->
            val button = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(TEXT)
                minHeight = 0
                minWidth = 0
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { setMode(value) }
            }
            modeButtons[value] = button
            card.addView(
                button,
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (index > 0) marginStart = dp(4)
                }
            )
        }

        applyModeStyles()
        return card
    }

    private fun buildQuickActions(): View {
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val actions = listOf(
            "Lumo prüfen" to "Prüfe die installierte Lumo-Version und ob eine neuere Lumo APK in Downloads liegt.",
            "Fehler Lumo" to "Prüfe Lumo auf aktuelle Fehler und analysiere Logcat.",
            "Downloads" to "Zeig meine Downloads.",
            "Speicher" to "Welche Dateien brauchen am meisten Speicher?",
            "Akku" to "Prüfe Akku und Ladezustand.",
            "Web" to "Recherchiere die neuesten Android-16- und Shizuku-Änderungen und nenne Quellen."
        )

        actions.forEach { (label, command) ->
            row.addView(
                chip(label).apply {
                    setOnClickListener {
                        input.setText(command)
                        input.setSelection(command.length)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(42)
                ).apply { marginEnd = dp(7) }
            )
        }

        horizontal.addView(row)
        return horizontal
    }

    private fun buildComposer(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(PANEL, 21, BORDER)
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }

        input = EditText(this).apply {
            hint = "Was soll ich auf deinem Gerät tun?"
            setHintTextColor(Color.rgb(103, 119, 143))
            setTextColor(TEXT)
            textSize = 15f
            minLines = 2
            maxLines = 5
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        shell.addView(input, matchWidth())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mic = actionButton("🎙", compact = true).apply {
            contentDescription = "Spracheingabe"
            setOnClickListener { startVoiceInput() }
        }

        val newChat = actionButton("NEU", compact = true).apply {
            setOnClickListener { newSession() }
        }

        sendButton = actionButton("SENDEN", accent = true).apply {
            setOnClickListener { sendCurrentInput() }
        }

        actions.addView(mic, LinearLayout.LayoutParams(dp(49), dp(43)))
        actions.addView(
            newChat,
            LinearLayout.LayoutParams(dp(68), dp(43)).apply { marginStart = dp(6) }
        )
        actions.addView(
            sendButton,
            LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginStart = dp(6) }
        )

        shell.addView(actions, matchWidth(top = 3))
        return shell
    }

    private fun sendCurrentInput() {
        if (busy) return
        val text = input.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "Schreib zuerst einen Auftrag.", Toast.LENGTH_SHORT).show()
            return
        }

        input.text.clear()
        pendingUserText = text
        agentLoops = 0
        addUserMessage(text)

        when (mode) {
            AgentMode.LOCAL -> runLocal(text)
            AgentMode.CLOUD -> {
                if (backend.isConfigured()) {
                    runCloud(text, emptyList())
                } else {
                    addAssistantMessage("Online-Modus ist nicht eingerichtet. Tippe oben auf ⚙ und hinterlege HTTPS-Backend und Device-Token.")
                }
            }
            AgentMode.AUTO -> {
                if (backend.isConfigured()) {
                    runCloud(text, emptyList())
                } else {
                    addToolActivity("Cloud nicht eingerichtet · lokaler Fallback")
                    runLocal(text)
                }
            }
        }
    }

    private fun runCloud(message: String?, outputs: List<ToolOutput>) {
        if (agentLoops >= 12) {
            setBusy(false)
            addAssistantMessage("Ich habe die Sicherheitsgrenze von 12 Agent-Schleifen erreicht und stoppe hier.")
            return
        }
        agentLoops += 1
        setBusy(true)
        addToolActivity(if (outputs.isEmpty()) "Cloud-KI plant …" else "Cloud-KI verarbeitet Tool-Ergebnisse …")

        backend.send(
            sessionId = sessionId,
            message = message,
            shizukuReady = bridge.isReady(),
            toolOutputs = outputs
        ) { result ->
            result.onFailure { error ->
                setBusy(false)
                val fallback = pendingUserText
                if (mode == AgentMode.AUTO && outputs.isEmpty() && !fallback.isNullOrBlank()) {
                    addToolActivity("Cloud nicht erreichbar · lokaler Fallback")
                    runLocal(fallback)
                } else {
                    addAssistantMessage("Cloud-Fehler: ${error.message ?: error.javaClass.simpleName}")
                }
            }.onSuccess { reply ->
                if (!reply.message.isNullOrBlank()) {
                    addAssistantMessage(reply.message)
                }
                if (reply.citations.isNotEmpty()) {
                    addCitations(reply.citations)
                }

                if (reply.toolCalls.isEmpty()) {
                    planCard.visibility = View.GONE
                    addToolActivity("Fertig")
                    setBusy(false)
                    pendingUserText = null
                } else {
                    renderCloudPlan(reply.toolCalls)
                    executeCloudCalls(reply.toolCalls, 0, mutableListOf())
                }
            }
        }
    }

    private fun executeCloudCalls(
        calls: List<ToolCallRequest>,
        index: Int,
        outputs: MutableList<ToolOutput>
    ) {
        if (index >= calls.size) {
            runCloud(null, outputs)
            return
        }

        val call = calls[index]
        val validationError = ToolRegistry.validate(call)
        if (validationError != null) {
            outputs += ToolOutput(call.callId, "TOOL_VALIDATION_ERROR: $validationError")
            executeCloudCalls(calls, index + 1, outputs)
            return
        }

        val risk = ToolRegistry.risk(call.name)
        val executeNow = {
            executeTool(call) { result ->
                outputs += ToolOutput(call.callId, result)
                executeCloudCalls(calls, index + 1, outputs)
            }
        }

        when (risk) {
            Risk.READ_ONLY -> executeNow()
            Risk.CONFIRM -> {
                showToolConfirmation(call, destructive = false) { allowed ->
                    if (allowed) {
                        executeNow()
                    } else {
                        outputs += ToolOutput(call.callId, "USER_DENIED: Nutzer hat die Aktion nicht freigegeben.")
                        executeCloudCalls(calls, index + 1, outputs)
                    }
                }
            }
            Risk.DESTRUCTIVE -> {
                showToolConfirmation(call, destructive = true) { allowed ->
                    if (allowed) {
                        executeNow()
                    } else {
                        outputs += ToolOutput(call.callId, "USER_DENIED: Nutzer hat die destruktive Aktion nicht freigegeben.")
                        executeCloudCalls(calls, index + 1, outputs)
                    }
                }
            }
        }
    }

    private fun executeTool(call: ToolCallRequest, done: (String) -> Unit) {
        val label = ToolRegistry.label(call.name)
        addToolActivity("⏳ $label")
        executor.execute(call) { result ->
            val preview = result.replace("\n", " ").take(120)
            addToolActivity("✓ $label · $preview")
            done(result)
        }
    }

    private fun showToolConfirmation(
        call: ToolCallRequest,
        destructive: Boolean,
        callback: (Boolean) -> Unit
    ) {
        val spec = ToolRegistry.get(call.name)
        val args = if (call.arguments.isEmpty()) {
            "Keine Parameter"
        } else {
            call.arguments.entries.joinToString("\n") { "• ${it.key}: ${it.value}" }
        }

        AlertDialog.Builder(this)
            .setTitle(if (destructive) "Kritische Aktion" else "Aktion freigeben")
            .setMessage(
                "${spec?.label ?: call.name}\n\n" +
                    "${spec?.description.orEmpty()}\n\n$args" +
                    if (destructive) "\n\nDiese Aktion verändert oder löscht Daten." else ""
            )
            .setNegativeButton("Ablehnen") { _, _ -> callback(false) }
            .setPositiveButton(if (destructive) "Bewusst ausführen" else "Ausführen") { _, _ ->
                callback(true)
            }
            .setOnCancelListener { callback(false) }
            .show()
    }

    private fun runLocal(text: String) {
        val plan = interpreter.interpret(text)
        if (plan == null) {
            setBusy(false)
            addAssistantMessage("Der lokale Interpreter konnte den Auftrag nicht lesen.")
            return
        }

        renderLocalPlan(plan)
        when (plan.risk) {
            Risk.READ_ONLY -> executeLocalPlan(plan)
            Risk.CONFIRM -> showLocalPlanConfirmation(plan, false)
            Risk.DESTRUCTIVE -> showLocalPlanConfirmation(plan, true)
        }
    }

    private fun showLocalPlanConfirmation(plan: CommandPlan, destructive: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (destructive) "Kritischer lokaler Auftrag" else plan.title)
            .setMessage(
                plan.steps.joinToString("\n") { "• ${it.label}" } +
                    if (destructive) "\n\nDer Auftrag enthält eine destruktive Aktion." else ""
            )
            .setNegativeButton("Abbrechen") { _, _ ->
                setBusy(false)
                addToolActivity("Lokaler Auftrag abgebrochen")
            }
            .setPositiveButton("Ausführen") { _, _ -> executeLocalPlan(plan) }
            .setOnCancelListener {
                setBusy(false)
                addToolActivity("Lokaler Auftrag abgebrochen")
            }
            .show()
    }

    private fun executeLocalPlan(plan: CommandPlan) {
        if (!bridge.isReady()) {
            setBusy(false)
            addAssistantMessage("Shizuku ist nicht verbunden. Starte Shizuku und erteile Device AI Zugriff.")
            return
        }

        setBusy(true)
        val results = mutableListOf<String>()

        fun runStep(index: Int) {
            if (index >= plan.steps.size) {
                planCard.visibility = View.GONE
                addAssistantMessage(
                    results.joinToString("\n\n").ifBlank { "Auftrag abgeschlossen." }
                )
                addToolActivity("Lokal fertig")
                setBusy(false)
                pendingUserText = null
                return
            }

            val step = plan.steps[index]
            addToolActivity("⏳ ${step.label}")
            bridge.execute(step.shellCommand) { result ->
                results += "✓ ${step.label}\n$result"
                addToolActivity("✓ ${step.label}")
                runStep(index + 1)
            }
        }

        runStep(0)
    }

    private fun renderCloudPlan(calls: List<ToolCallRequest>) {
        planCard.visibility = View.VISIBLE
        planText.text = calls.mapIndexed { index, call ->
            val risk = ToolRegistry.risk(call.name)
            val badge = when (risk) {
                Risk.READ_ONLY -> "SICHER"
                Risk.CONFIRM -> "FREIGABE"
                Risk.DESTRUCTIVE -> "KRITISCH"
            }
            "${index + 1}. ${ToolRegistry.label(call.name)} · $badge"
        }.joinToString("\n")
    }

    private fun renderLocalPlan(plan: CommandPlan) {
        planCard.visibility = View.VISIBLE
        planText.text = plan.steps.mapIndexed { index, step ->
            val badge = when (step.risk) {
                Risk.READ_ONLY -> "SICHER"
                Risk.CONFIRM -> "FREIGABE"
                Risk.DESTRUCTIVE -> "KRITISCH"
            }
            "${index + 1}. ${step.label} · $badge"
        }.joinToString("\n")
    }

    private fun setMode(value: AgentMode) {
        mode = value
        applyModeStyles()
        addToolActivity(
            when (value) {
                AgentMode.AUTO -> "Auto-Modus · Cloud mit lokalem Fallback"
                AgentMode.CLOUD -> "Online-Modus · Cloud-KI erzwungen"
                AgentMode.LOCAL -> "Lokaler Modus · keine Cloud-Anfrage"
            }
        )
    }

    private fun applyModeStyles() {
        modeButtons.forEach { (value, button) ->
            val selected = value == mode
            button.background = rounded(
                if (selected) ACCENT_DARK else Color.TRANSPARENT,
                12,
                if (selected) ACCENT else null
            )
            button.setTextColor(if (selected) TEXT else MUTED)
        }
    }

    private fun updateShizukuBadge(message: String) {
        val ready = message.startsWith("Bereit")
        shizukuBadge.text = if (ready) "● SHIZUKU BEREIT" else "● SHIZUKU AUS"
        shizukuBadge.setTextColor(if (ready) GREEN else AMBER)
        shizukuBadge.background = rounded(
            if (ready) Color.rgb(17, 49, 35) else Color.rgb(51, 39, 18),
            14,
            if (ready) Color.rgb(38, 99, 70) else Color.rgb(100, 75, 30)
        )
        if (!ready) shizukuBadge.contentDescription = message
    }

    private fun updateCloudBadge() {
        val configured = backend.isConfigured()
        cloudBadge.text = if (configured) "● CLOUD BEREIT" else "● CLOUD SETUP"
        cloudBadge.setTextColor(if (configured) GREEN else AMBER)
        cloudBadge.background = rounded(
            if (configured) Color.rgb(17, 49, 35) else Color.rgb(51, 39, 18),
            14,
            if (configured) Color.rgb(38, 99, 70) else Color.rgb(100, 75, 30)
        )
    }

    private fun showCloudSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
        }

        val url = EditText(this).apply {
            hint = "https://dein-device-ai-backend.example"
            setText(backend.backendUrl())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        val token = EditText(this).apply {
            hint = if (backend.hasToken()) "Device-Token gespeichert · leer lassen zum Behalten" else "Device-Token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }

        box.addView(textView("Backend URL (nur HTTPS)", 12f, MUTED, true))
        box.addView(url, matchWidth(top = 4))
        box.addView(textView("Device-Token", 12f, MUTED, true).apply {
            setPadding(0, dp(12), 0, 0)
        })
        box.addView(token, matchWidth(top = 4))
        box.addView(
            textView(
                "Der OpenAI-API-Key gehört niemals hier hinein. Er bleibt ausschließlich als Secret am Backend.",
                11f,
                MUTED
            ).apply { setPadding(0, dp(10), 0, 0) }
        )

        AlertDialog.Builder(this)
            .setTitle("Cloud-KI einrichten")
            .setView(box)
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Token löschen") { _, _ ->
                SecureTokenStore.clear(this)
                updateCloudBadge()
                addToolActivity("Cloud-Token gelöscht")
            }
            .setPositiveButton("Speichern") { _, _ ->
                val endpoint = url.text.toString().trim().trimEnd('/')
                if (endpoint.isNotBlank() && !endpoint.startsWith("https://", ignoreCase = true)) {
                    Toast.makeText(this, "Für Cloud-KI ist HTTPS erforderlich.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                backend.saveBackendUrl(endpoint)
                val newToken = token.text.toString()
                if (newToken.isNotBlank()) SecureTokenStore.save(this, newToken)
                updateCloudBadge()
                addToolActivity("Cloud-Konfiguration gespeichert")
                if (backend.isConfigured()) testCloudHealth()
            }
            .show()
    }

    private fun testCloudHealth() {
        addToolActivity("Prüfe Backend …")
        backend.health { result ->
            result.onSuccess {
                addToolActivity("✓ Backend erreichbar")
            }.onFailure {
                addToolActivity("Backend nicht erreichbar: ${it.message ?: "Fehler"}")
            }
        }
    }

    private fun newSession() {
        if (busy) {
            Toast.makeText(this, "Ein Auftrag läuft noch.", Toast.LENGTH_SHORT).show()
            return
        }
        sessionId = UUID.randomUUID().toString()
        agentLoops = 0
        pendingUserText = null
        planCard.visibility = View.GONE
        chatContainer.removeAllViews()
        addAssistantMessage("Neue Sitzung gestartet. Was soll ich tun?")
        addToolActivity("Neue Sitzung")
    }

    private fun addUserMessage(value: String) {
        addBubble(value, user = true)
    }

    private fun addAssistantMessage(value: String) {
        addBubble(value, user = false)
    }

    private fun addBubble(value: String, user: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (user) Gravity.END else Gravity.START
        }

        val bubble = TextView(this).apply {
            text = value
            textSize = 14.5f
            setTextColor(TEXT)
            setTextIsSelectable(true)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(if (user) USER_BUBBLE else ASSISTANT_BUBBLE, 18, if (user) null else BORDER)
        }

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                if (resources.configuration.screenWidthDp >= 700) dp(620) else dp(310),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        chatContainer.addView(
            row,
            matchWidth(top = 8)
        )
        scrollToBottom()
    }

    private fun addCitations(citations: List<AgentCitation>) {
        val card = verticalCard().apply {
            setPadding(dp(13), dp(11), dp(13), dp(11))
        }
        card.addView(textView("QUELLEN", 11f, ACCENT, true))

        citations.take(8).forEachIndexed { index, citation ->
            val source = textView(
                "${index + 1}. ${citation.title}",
                13f,
                Color.rgb(188, 205, 255),
                true
            ).apply {
                setPadding(0, dp(8), 0, dp(2))
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)))
                    }
                }
            }
            card.addView(source)
            card.addView(textView(citation.url, 11f, MUTED))
        }

        chatContainer.addView(card, matchWidth(top = 8))
        scrollToBottom()
    }

    private fun addToolActivity(value: String) {
        if (::activityText.isInitialized) {
            activityText.text = value
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        if (::sendButton.isInitialized) {
            sendButton.isEnabled = !value
            sendButton.text = if (value) "ARBEITET …" else "SENDEN"
            sendButton.alpha = if (value) 0.65f else 1f
        }
    }

    private fun scrollToBottom() {
        if (::chatScroll.isInitialized) {
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-AT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Was soll Device AI tun?")
        }
        try {
            startActivityForResult(intent, SPEECH_REQUEST)
        } catch (_: Exception) {
            Toast.makeText(this, "Keine Spracheingabe verfügbar.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Android SDK, kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST && resultCode == RESULT_OK) {
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                input.setSelection(spoken.length)
            }
        }
    }

    private fun infoCard(title: String, body: String, badge: String): View {
        val card = verticalCard().apply {
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            textView(title, 15f, TEXT, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(statusBadge(badge, GREEN))
        card.addView(row)
        card.addView(textView(body, 12f, MUTED).apply { setPadding(0, dp(7), 0, 0) })
        return card
    }

    private fun sectionLabel(value: String, top: Int): View =
        textView(value, 11f, MUTED, true).apply {
            setPadding(0, dp(top), 0, 0)
        }

    private fun statusBadge(value: String, color: Int): TextView =
        textView(value, 11f, color, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), 0, dp(9), 0)
            background = rounded(Color.rgb(31, 37, 47), 14, BORDER)
        }

    private fun chip(label: String): Button =
        Button(this).apply {
            text = label
            textSize = 12.5f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(224, 232, 246))
            background = rounded(SURFACE, 14, BORDER)
            setPadding(dp(13), 0, dp(13), 0)
            minWidth = 0
            minHeight = 0
        }

    private fun actionButton(
        label: String,
        accent: Boolean = false,
        compact: Boolean = false
    ): Button =
        Button(this).apply {
            text = label
            textSize = if (compact) 11.5f else 12.5f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT)
            background = rounded(if (accent) ACCENT_DARK else SURFACE, 13, BORDER)
            setPadding(if (compact) dp(7) else dp(12), 0, if (compact) dp(7) else dp(12), 0)
            minWidth = 0
            minHeight = 0
        }

    private fun verticalCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(PANEL_2, 18, BORDER)
        }

    private fun textView(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun matchWidth(top: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
