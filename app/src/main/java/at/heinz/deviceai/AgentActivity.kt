package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import at.heinz.deviceai.cognitive.AgentEvaluator
import at.heinz.deviceai.cognitive.AgentModel
import at.heinz.deviceai.cognitive.AgentModelOutput
import at.heinz.deviceai.cognitive.AgentToolApproval
import at.heinz.deviceai.cognitive.AgentToolApprovalProvider
import at.heinz.deviceai.cognitive.AiContextCompiler
import at.heinz.deviceai.cognitive.AiContextInput
import at.heinz.deviceai.cognitive.AiContextPolicy
import at.heinz.deviceai.cognitive.AndroidKeystoreIdentitySigner
import at.heinz.deviceai.cognitive.BoundedAgentOrchestrator
import at.heinz.deviceai.cognitive.ChatReasoningRouter
import at.heinz.deviceai.cognitive.LocalModelReasoningAdapter
import at.heinz.deviceai.cognitive.ContinuumRuntimeState
import at.heinz.deviceai.cognitive.ContinuumRuntimeStore
import at.heinz.deviceai.cognitive.Evaluation
import at.heinz.deviceai.cognitive.FileAgentCheckpointStore
import at.heinz.deviceai.cognitive.InMemoryExperienceStore
import at.heinz.deviceai.cognitive.InMemoryMemoryStore
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.MemoryWriteContext
import at.heinz.deviceai.cognitive.MemoryWritePolicy
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.DeepResearchEngine
import at.heinz.deviceai.cognitive.DevelopmentCoordinator
import at.heinz.deviceai.cognitive.DevelopmentUiController
import at.heinz.deviceai.cognitive.DevelopmentUiPhase
import at.heinz.deviceai.cognitive.DeterministicResearchSynthesizer
import at.heinz.deviceai.cognitive.FileResearchDossierStore
import at.heinz.deviceai.cognitive.HttpReadOnlyResearchProvider
import at.heinz.deviceai.cognitive.ResearchBudgetPolicy
import at.heinz.deviceai.cognitive.ResearchMode
import at.heinz.deviceai.cognitive.ResearchOptInPolicy
import at.heinz.deviceai.cognitive.ResearchPhase
import at.heinz.deviceai.cognitive.ResearchPromptBoundary
import at.heinz.deviceai.cognitive.ResearchProviderPolicy
import at.heinz.deviceai.cognitive.ResearchSynthesisOutput
import at.heinz.deviceai.cognitive.ResearchSynthesizer
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LiteRtLmLocalModelRuntime
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCatalog
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import at.heinz.deviceai.localmodel.LocalToolResponse
import at.heinz.deviceai.localmodel.NativeLiteRtModelInspector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.UUID

class AgentActivity : Activity() {
    private data class LocalGeneration(
        val response: String,
        val proposal: ToolProposal?,
        val rejectedToolResponses: List<LocalToolResponse>,
        val failure: String?
    )

    companion object {
        private const val SPEECH_REQUEST = 2207
        private const val MODEL_IMPORT_REQUEST = 2208
        private const val DEV_SOURCE_REQUEST = 2209
        private const val DEV_EXPORT_REQUEST = 2210
        private const val DEV_MAX_SOURCE_READ_BYTES = 4_000
        private const val COMPOSER_DRAFT_KEY = "device_ai_composer_draft"
        private val BG = Color.rgb(7, 10, 16)
        private val PANEL = Color.rgb(15, 21, 31)
        private val PANEL_2 = Color.rgb(21, 29, 42)
        private val SURFACE = Color.rgb(27, 37, 53)
        private val BORDER = Color.rgb(48, 64, 87)
        private val TEXT = Color.rgb(245, 248, 253)
        private val MUTED = Color.rgb(155, 169, 191)
        private val ACCENT = Color.rgb(71, 143, 255)
        private val ACCENT_DARK = Color.rgb(37, 87, 224)
        private val CYAN = Color.rgb(44, 208, 255)
        private val GREEN = Color.rgb(78, 214, 145)
        private val AMBER = Color.rgb(255, 192, 82)
        private val RED = Color.rgb(255, 102, 112)
        private val USER_BUBBLE = Color.rgb(48, 72, 148)
        private val ASSISTANT_BUBBLE = Color.rgb(24, 33, 47)
    }

    private lateinit var bridge: ShizukuBridge
    private lateinit var executor: DeviceToolExecutor
    private lateinit var backend: AgentBackendClient
    private lateinit var modelCatalog: LocalModelCatalog
    private lateinit var localRuntime: LocalModelRuntime
    private lateinit var receiptStore: FileToolExecutionReceiptStore
    private lateinit var continuumStore: ContinuumRuntimeStore
    @Volatile private var continuumState: ContinuumRuntimeState? = null
    private var continuumStartupNote: String? = null
    private val contextCompiler = AiContextCompiler(
        AiContextPolicy(maxBytes = 10_500, memoryLimit = 8, researchSourceLimit = 4)
    )

    private lateinit var shizukuBadge: TextView
    private lateinit var cloudBadge: TextView
    private lateinit var localBadge: TextView
    private lateinit var modelCardText: TextView
    private lateinit var localActionButton: Button
    private lateinit var activityText: TextView
    private lateinit var planCard: LinearLayout
    private lateinit var planText: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var chatContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private var dashboardSection: View? = null
    private var keyboardTitle: TextView? = null
    private var keyboardCompact = false

    private val modeButtons = linkedMapOf<AssistantMode, Button>()
    private var mode = AssistantMode.AUTO
    private var sessionId = UUID.randomUUID().toString()
    private var busy = false
    private var activeLocalRequest = false
    private var researchArmed = false
    private var researchResumeRequested = false
    private var researchMode = ResearchMode.STANDARD
    private var activeResearchCancellation: at.heinz.deviceai.cognitive.ResearchCancellation? = null
    private var agentLoops = 0
    private var pendingModelImport: Triple<String, String, String>? = null
    private var modelImportActive = false
    private var modelUnloading = false
    private var localRuntimeError: String? = null
    private var lifecycleTimeout: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val localAgentExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "device-ai-local-agent").apply { isDaemon = true }
    }
    private val memoryStore = InMemoryMemoryStore()
    private val experienceStore = InMemoryExperienceStore()
    private var activeOrchestrator: BoundedAgentOrchestrator? = null
    private var activeChatCancellation: at.heinz.deviceai.cognitive.DeliberationCancellation? = null
    @Volatile private var activeReasoningRouter: ChatReasoningRouter? = null
    private val devController = DevelopmentUiController { "dev-" + UUID.randomUUID() }
    @Volatile private var activeDevCoordinator: DevelopmentCoordinator? = null
    private var devDialog: AlertDialog? = null
    private var devStatusView: TextView? = null
    private var devSourcesView: TextView? = null
    private var devPreviewView: TextView? = null
    private var devStartButton: Button? = null
    private var devStopButton: Button? = null
    private var devExportButton: Button? = null
    private var devPickButton: Button? = null
    private var devClearButton: Button? = null
    private var devGoalInput: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        backend = AgentBackendClient(this)
        modelCatalog = LocalModelCatalog(
            java.io.File(filesDir, "local-models"),
            NativeLiteRtModelInspector()
        )
        localRuntime = LiteRtLmLocalModelRuntime(modelCatalog, java.io.File(cacheDir, "litertlm"))
        receiptStore = FileToolExecutionReceiptStore(java.io.File(filesDir, "tool-receipts"))
        initializeContinuum()
        bridge = ShizukuBridge(this) { message ->
            runOnUiThread {
                if (::shizukuBadge.isInitialized) updateShizukuBadge(message)
            }
        }
        executor = DeviceToolExecutor(this, bridge)

        setContentView(buildUi())
        savedInstanceState?.getString(COMPOSER_DRAFT_KEY)?.let { draft ->
            input.setText(draft)
            input.setSelection(draft.length)
        }
        bridge.start()
        updateCloudBadge()
        refreshLocalModelUi()
        showPendingReceiptReview()
        continuumStartupNote?.let(::addToolActivity)
        addAssistantMessage(
            "Device AI ist lokal-first. AUTO und LOKAL nutzen das installierte LiteRT-LM-Modell; " +
                "ONLINE bleibt ein optionaler Cloud-Modus."
        )
        modelCatalog.all().firstOrNull()?.let { loadModel(it) }
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) bridge.refresh()
        if (::cloudBadge.isInitialized) updateCloudBadge()
        if (::modelCatalog.isInitialized) refreshLocalModelUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::input.isInitialized) outState.putString(COMPOSER_DRAFT_KEY, input.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        clearLifecycleTimeout()
        if (::localRuntime.isInitialized) {
            activeOrchestrator?.cancel()
            activeChatCancellation?.cancel()
            activeDevCoordinator?.cancel()
            activeResearchCancellation?.cancel()
            localRuntime.cancel()
            if (localRuntime.state != LocalModelState.EMPTY && localRuntime.state != LocalModelState.LOADING) {
                runCatching { localRuntime.unload() }
            }
        }
        localAgentExecutor.shutdownNow()
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

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val viewport = ComposerUiPolicy.viewport(
                    bars.top, bars.bottom, ime.bottom,
                    insets.isVisible(WindowInsets.Type.ime()), wide
                )
                if (view.paddingLeft != bars.left || view.paddingTop != viewport.top ||
                    view.paddingRight != bars.right || view.paddingBottom != viewport.bottom
                ) {
                    view.setPadding(bars.left, viewport.top, bars.right, viewport.bottom)
                }
                if (keyboardCompact != viewport.compactDashboard) {
                    keyboardCompact = viewport.compactDashboard
                    dashboardSection?.visibility = if (keyboardCompact) View.GONE else View.VISIBLE
                    keyboardTitle?.visibility = if (keyboardCompact) View.VISIBLE else View.GONE
                }
                insets
            }
            root.post { root.requestApplyInsets() }
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

        side.addView(
            actionButton("Entwicklungsaufgabe").apply { setOnClickListener { showDevelopmentDialog() } },
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

        val dashboard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dashboardSection = dashboard
        dashboard.addView(buildHeader(wide))
        dashboard.addView(buildStatusRow(), matchWidth(top = 12))
        dashboard.addView(buildModeRow(), matchWidth(top = 10))
        dashboard.addView(buildLocalModelCard(), matchWidth(top = 10))
        dashboard.addView(buildQuickActions(), matchWidth(top = 10))

        planCard = verticalCard().apply {
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        planCard.addView(textView("AKTIONSPLAN", 11f, ACCENT, true))
        planText = textView("", 13f, TEXT).apply {
            setPadding(0, dp(7), 0, 0)
        }
        planCard.addView(planText)
        dashboard.addView(planCard, matchWidth(top = 10))

        activityText = textView("Bereit", 12f, MUTED).apply {
            setPadding(dp(4), dp(8), dp(4), dp(7))
        }
        dashboard.addView(activityText, matchWidth())
        // The dashboard must scroll independently: otherwise its fixed-height cards
        // can push the composer under the system navigation bar on narrow displays.
        val dashboardScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(6))
        }
        dashboardScroll.addView(dashboard, matchWidth())
        dashboardSection = dashboardScroll
        pane.addView(
            dashboardScroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f)
        )

        keyboardTitle = textView("Device AI · Nachricht", 15f, ACCENT, true).apply {
            visibility = View.GONE
            setPadding(dp(4), dp(3), 0, dp(6))
        }
        pane.addView(keyboardTitle, matchWidth())

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
                2f
            )
        )

        pane.addView(buildComposer(), matchWidth())
        return pane
    }

    private fun buildHeader(wide: Boolean): View {
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(
            textView("FRAG. PLANE. FÜHRE SICHER AUS.", 9.5f, Color.rgb(151, 190, 255), true)
                .apply { letterSpacing = 0.13f }
        )
        heading.addView(
            textView("Device AI", if (wide) 32f else 27f, TEXT, true).apply {
                val styled = SpannableString("Device AI")
                styled.setSpan(ForegroundColorSpan(CYAN), 7, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = styled
                setShadowLayer(dp(7).toFloat(), 0f, 0f, Color.rgb(24, 86, 178))
            },
            matchWidth(top = 3)
        )
        heading.addView(
            textView("Dein privater KI-Assistent direkt auf Android.",
                if (wide) 12.5f else 11.5f, MUTED),
            matchWidth(top = 3)
        )
        top.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(ImageView(this).apply {
            setImageResource(R.drawable.device_ai_robot)
            contentDescription = "Device AI Roboter-Maskottchen"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(if (wide) 126 else 82), dp(if (wide) 112 else 87)).apply {
            marginStart = dp(3)
        })
        top.addView(
            actionButton("⚙", compact = true).apply {
                contentDescription = "Cloud-Einstellungen"
                setOnClickListener { showCloudSettings() }
            },
            LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(4) }
        )
        hero.addView(top, matchWidth())
        if (!wide) {
            val secondary = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            secondary.addView(
                textView("LOKAL · SICHER · DEIN ASSISTENT", 10f, Color.rgb(114, 191, 255)),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            secondary.addView(
                actionButton("EXPERT", compact = true).apply {
                    setOnClickListener {
                        startActivity(Intent(this@AgentActivity, MainActivity::class.java))
                    }
                },
                LinearLayout.LayoutParams(dp(76), dp(33))
            )
            hero.addView(secondary, matchWidth(top = 2))
        }
        return hero
    }

    private fun buildStatusRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        shizukuBadge = statusBadge("● SHIZUKU …", AMBER)
        cloudBadge = statusBadge("● CLOUD …", AMBER)
        localBadge = statusBadge("● NO MODEL", AMBER)

        row.addView(
            shizukuBadge,
            LinearLayout.LayoutParams(0, dp(39), 1f).apply { marginEnd = dp(4) }
        )
        row.addView(
            localBadge,
            LinearLayout.LayoutParams(0, dp(39), 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
        )
        row.addView(
            cloudBadge,
            LinearLayout.LayoutParams(0, dp(39), 1f).apply { marginStart = dp(4) }
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
            AssistantMode.AUTO to "AUTO",
            AssistantMode.CLOUD to "ONLINE",
            AssistantMode.LOCAL to "LOKAL"
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

    private fun buildLocalModelCard(): View {
        val card = verticalCard().apply {
            background = roundedGradient(Color.rgb(10, 29, 61), Color.rgb(14, 24, 49), 18, Color.rgb(24, 127, 243))
            setPadding(dp(12), dp(11), dp(12), dp(11))
        }
        val masthead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        label.addView(textView("LOKALES MODELL · CPU", 11f, CYAN, true))
        label.addView(textView("KI-MODELL", 16f, TEXT, true), matchWidth(top = 5))
        masthead.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        masthead.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.device_ai_model_stack)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Verbundenes Modell-Paket mit blauen Leiterbahnen"
            },
            LinearLayout.LayoutParams(dp(128), dp(76))
        )
        card.addView(masthead, matchWidth())
        modelCardText = textView("Kein Modell installiert. Importiere eine kompatible .litertlm-Datei.", 12f, MUTED)
            .apply { setPadding(0, dp(2), 0, dp(9)) }
        card.addView(modelCardText)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        localActionButton = actionButton("MODELL LADEN", accent = true).apply {
            setOnClickListener {
                if (localRuntime.loadedModel != null) unloadModel() else chooseInstalledModel()
            }
        }
        actions.addView(localActionButton, LinearLayout.LayoutParams(0, dp(43), 1f))
        actions.addView(
            actionButton("MODELL IMPORTIEREN").apply { setOnClickListener { showModelImportDialog() } },
            LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginStart = dp(7) }
        )
        card.addView(actions)
        return card
    }

    private fun buildQuickActions(): View {
        val actions = listOf(
            "Lumo prüfen" to "Prüfe die installierte Lumo-Version und ob eine neuere Lumo APK in Downloads liegt.",
            "Fehler Lumo" to "Prüfe Lumo auf aktuelle Fehler und analysiere Logcat.",
            "Downloads" to "Zeig meine Downloads.",
            "Speicher" to "Welche Dateien brauchen am meisten Speicher?",
            "Akku" to "Prüfe Akku und Ladezustand.",
            "Entwicklung" to "",
            "Web" to "Recherchiere die neuesten Android-16- und Shizuku-Änderungen und nenne Quellen."
        )
        fun createAction(label: String, command: String): Button =
            chip(label).apply {
                setOnClickListener {
                    when (label) {
                        "Web" -> showResearchModeDialog()
                        "Entwicklung" -> showDevelopmentDialog()
                        else -> {
                            input.setText(command)
                            input.setSelection(command.length)
                        }
                    }
                }
            }
        if (resources.configuration.screenWidthDp >= 700) {
            val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.forEach { (label, command) ->
                row.addView(createAction(label, command),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)).apply {
                        marginEnd = dp(7)
                    })
            }
            scroll.addView(row)
            return scroll
        }
        // On a phone, no action is clipped off-screen or hidden behind horizontal scrolling.
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.chunked(3).forEachIndexed { index, group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEachIndexed { j, (label, command) ->
                row.addView(createAction(label, command),
                    LinearLayout.LayoutParams(0, dp(45), 1f).apply { if (j > 0) marginStart = dp(6) })
            }
            repeat(3 - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(45), 1f)) }
            grid.addView(row, matchWidth(top = if (index > 0) 6 else 0))
        }
        return grid
    }

    private fun buildComposer(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedGradient(Color.rgb(12, 28, 60), Color.rgb(10, 20, 39), 21, Color.rgb(32, 112, 231))
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }

        input = EditText(this).apply {
            hint = "Was soll ich auf deinem Gerät tun?"
            setHintTextColor(Color.rgb(103, 119, 143))
            setTextColor(TEXT)
            textSize = 15f
            minLines = 1
            maxLines = 4
            background = rounded(Color.rgb(9, 19, 37), 14, Color.rgb(40, 112, 226))
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
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
            setOnClickListener {
                if (busy && activeLocalRequest) cancelLocalRequest() else sendCurrentInput()
            }
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

        val researchSelected = ResearchOptInPolicy.shouldResearch(text, researchArmed)
        val route = LocalFirstPolicy.route(mode, localRuntime.state == LocalModelState.READY)
        when (ComposerUiPolicy.submission(
            route, localRuntime.loadedModel != null, backend.isConfigured(), researchSelected
        )) {
            DraftSubmission.READY -> Unit
            DraftSubmission.NEEDS_LOCAL_MODEL -> {
                addToolActivity("Kein lokales Modell geladen · Entwurf bleibt erhalten")
                Toast.makeText(this, "Bitte zuerst ein lokales Modell laden. Text bleibt erhalten.", Toast.LENGTH_LONG).show()
                return
            }
            DraftSubmission.NEEDS_CLOUD_SETUP -> {
                addToolActivity("ONLINE nicht konfiguriert · Entwurf bleibt erhalten")
                Toast.makeText(this, "Bitte ONLINE konfigurieren. Text bleibt erhalten.", Toast.LENGTH_LONG).show()
                return
            }
        }
        agentLoops = 0
        input.text.clear()
        addUserMessage(text)

        if (researchSelected) {
            researchArmed = false
            runResearch(text, researchMode)
            return
        }
        researchArmed = false
        when (route) {
            AssistantRoute.LOCAL_MODEL -> runLocal(text)
            AssistantRoute.CLOUD -> {
                if (backend.isConfigured()) {
                    runCloud(text, emptyList())
                } else {
                    addAssistantMessage("Online-Modus ist nicht eingerichtet. Tippe oben auf ⚙ und hinterlege HTTPS-Backend und Device-Token.")
                }
            }
            AssistantRoute.MODEL_SETUP_REQUIRED -> {
                addAssistantMessage(
                    "Kein lokales Modell ist geladen. Importiere eine kompatible .litertlm-Datei und lade sie oben. " +
                        "AUTO verwendet keine Cloud als stillen Ersatz; ONLINE kann separat gewählt werden."
                )
            }
        }
    }

    private fun showResearchModeDialog() {
        val choices = arrayOf(
            "QUICK · wenige Quellen, 15 s",
            "STANDARD · zwei Wellen, 45 s",
            "DEEP · Gegenbelege, 90 s"
        )
        AlertDialog.Builder(this)
            .setTitle("Explizite Web-Recherche")
            .setMessage(
                "Webzugriff startet nur nach dieser Auswahl. Allgemeine Websuche ist nicht konfiguriert; " +
                    "füge direkte HTTPS-Quell-URLs in die Frage ein. Seiteninhalte gelten als nicht vertrauenswürdig."
            )
            .setItems(choices) { _, index ->
                researchMode = ResearchMode.values()[index.coerceIn(0, ResearchMode.values().lastIndex)]
                val checkpointStore = FileResearchDossierStore(java.io.File(filesDir, "research"))
                val pending = runCatching { checkpointStore.load() }.getOrNull()
                val saved = runCatching { checkpointStore.loadDossier() }.getOrNull()
                val arm = { resume: Boolean ->
                    researchResumeRequested = resume
                    researchArmed = true
                    activityText.text = "RESEARCH OPT-IN · ${researchMode.name} · Quelle(n) als HTTPS-URL angeben"
                    input.hint = "Recherchefrage und direkte HTTPS-Quell-URL(s)…"
                    Toast.makeText(
                        this, "Web-Recherche ${researchMode.name} aktiviert – nur für die nächste Eingabe.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (pending != null && saved != null && pending.phase !in setOf(ResearchPhase.COMPLETE)) {
                    AlertDialog.Builder(this)
                        .setTitle("Unvollständige Recherche")
                        .setMessage("Gespeicherte Frage: ${saved.plan.question.take(512)}\n\nFortsetzen lädt bereits gespeicherte Quellen nicht erneut.")
                        .setNeutralButton("Fortsetzen") { _, _ -> arm(true) }
                        .setPositiveButton("Neu starten") { _, _ -> arm(false) }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                } else {
                    arm(false)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun runResearch(question: String, mode: ResearchMode) {
        if (!ResearchOptInPolicy.shouldResearch(question, explicitButtonSelected = true)) return
        activeLocalRequest = true
        activeResearchCancellation = at.heinz.deviceai.cognitive.ResearchCancellation()
        setBusy(true)
        val urls = Regex("""https://[^\s<>"']+""", RegexOption.IGNORE_CASE)
            .findAll(question).map { it.value.trimEnd('.', ',', ';', ')', ']') }.distinct().take(24).toList()
        val budget = ResearchBudgetPolicy.forMode(mode)
        activityText.text = "RESEARCH · PLANNING · ${mode.name}"
        addToolActivity("WEB-RESEARCH · ${mode.name} · read-only")
        val localSynthesis = ResearchSynthesizer { input ->
            if (localRuntime.state != LocalModelState.READY) {
                DeterministicResearchSynthesizer().synthesize(input)
            } else {
                val generation = generateLocal { callback ->
                    localRuntime.stream(ResearchPromptBoundary.build(input), callback)
                }
                if (generation.failure != null || generation.proposal != null || generation.response.isBlank()) {
                    DeterministicResearchSynthesizer().synthesize(input)
                } else {
                    ResearchSynthesisOutput(generation.response.trim().take(8_000))
                }
            }
        }
        val researchStore = FileResearchDossierStore(java.io.File(filesDir, "research"))
        val shouldResume = researchResumeRequested
        researchResumeRequested = false
        val engine = DeepResearchEngine(
            discovery = at.heinz.deviceai.cognitive.UnconfiguredResearchDiscoveryProvider(),
            provider = HttpReadOnlyResearchProvider(
                ResearchProviderPolicy(
                    totalTimeoutMillis = budget.totalDeadlineMillis.toInt(),
                    maxBytes = budget.maxBytesPerSource,
                    maxRedirects = budget.maxRedirects
                )
            ),
            synthesizer = localSynthesis,
            checkpointStore = researchStore,
            dossierRepository = researchStore,
            onSynthesisTimeout = { localRuntime.cancel() }
        )
        val cancellation = activeResearchCancellation!!
        val reportPhase: (ResearchPhase) -> Unit = { phase ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val label = when (phase) {
                        ResearchPhase.PLAN, ResearchPhase.DISCOVERY -> "PLANNING"
                        ResearchPhase.FETCH -> "FETCHING"
                        ResearchPhase.EXTRACT, ResearchPhase.GRAPH, ResearchPhase.GAP_ANALYSIS -> "CHECKING"
                        ResearchPhase.FOLLOW_UP -> "FOLLOW-UP"
                        ResearchPhase.SYNTHESIZE -> "SYNTHESIZING"
                        ResearchPhase.COMPLETE -> "DONE"
                        ResearchPhase.PARTIAL -> "PARTIAL"
                    }
                    activityText.text = "RESEARCH · $label"
                    addToolActivity("Research $label")
                }
            }
        }
        localAgentExecutor.execute {
            val result = runCatching {
                val savedQuestion = runCatching { researchStore.loadLatest()?.plan?.question }.getOrNull()
                val canResume = shouldResume && savedQuestion == question
                if (shouldResume && !canResume) {
                    error("Fortsetzen nicht möglich: Frage stimmt nicht exakt mit dem gespeicherten Checkpoint überein.")
                }
                val resumed = if (canResume) engine.resume(cancellation, reportPhase) else null
                resumed ?: engine.research(question, mode, urls, cancellation, reportPhase)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (activeResearchCancellation !== cancellation) return@runOnUiThread
                activeResearchCancellation = null
                activeLocalRequest = false
                input.hint = "Was soll ich auf deinem Gerät tun?"
                result.onSuccess { dossier ->
                    val citations = dossier.sources.filter { it.duplicateOf == null }.take(16)
                        .joinToString("\n") { "• ${it.title ?: it.domain}\n  ${it.canonicalUrl}" }
                    val body = buildString {
                        append(dossier.synthesis?.text ?: "Keine Synthese verfügbar.")
                        if (citations.isNotBlank()) append("\n\nQuellen:\n").append(citations)
                        if (!dossier.generalSearchAvailable) {
                            append("\n\nAllgemeine Websuche ist nicht eingerichtet; recherchiert wurden nur direkte HTTPS-Quellen.")
                        }
                        if (localRuntime.state != LocalModelState.READY) {
                            append("\n\nHinweis: Lokales Modell war nicht bereit; deterministische Zusammenfassung verwendet.")
                        }
                        dossier.partialReason?.let { append("\n\nTeilrecherche: ").append(it.take(256)) }
                    }
                    addAssistantMessage(body.take(8_000))
                    addToolActivity("RESEARCH · ${dossier.phase.name} · ${dossier.sources.size} Quellen")
                }.onFailure { error ->
                    addAssistantMessage("Recherche abgebrochen oder fehlgeschlagen: ${error.message?.take(256) ?: "unbekannter Fehler"}")
                    addToolActivity("RESEARCH · PARTIAL")
                }
                setBusy(false)
                refreshLocalModelUi()
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
                addAssistantMessage("Cloud-Fehler: ${error.message ?: error.javaClass.simpleName}")
            }.onSuccess { reply ->
                reply.message?.takeIf(String::isNotBlank)?.let(::addAssistantMessage)
                if (reply.citations.isNotEmpty()) {
                    addCitations(reply.citations)
                }

                if (reply.toolCalls.isEmpty()) {
                    planCard.visibility = View.GONE
                    addToolActivity("Fertig")
                    setBusy(false)
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

        val preparedReceipt = runCatching {
            createToolGate().recordProposal(ToolInvocation(call.callId, call))
        }.getOrElse {
            outputs += ToolOutput(call.callId, "TOOL_REJECTED: durable receipt could not be verified.")
            executeCloudCalls(calls, index + 1, outputs)
            return
        }
        val risk = ToolRegistry.risk(call.name)
        val executeNow = {
            executeTool(call, true, risk == Risk.DESTRUCTIVE) { result ->
                outputs += ToolOutput(call.callId, result)
                executeCloudCalls(calls, index + 1, outputs)
            }
        }

        when (risk) {
            Risk.READ_ONLY -> executeTool(call, false, false) { result ->
                outputs += ToolOutput(call.callId, result)
                executeCloudCalls(calls, index + 1, outputs)
            }
            Risk.CONFIRM -> {
                if (preparedReceipt != null &&
                    preparedReceipt.state !in setOf(ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED)) {
                    executeTool(call, false, false) { result ->
                        outputs += ToolOutput(call.callId, result)
                        executeCloudCalls(calls, index + 1, outputs)
                    }
                } else showToolConfirmation(call, destructive = false) { allowed ->
                    if (allowed) {
                        executeNow()
                    } else {
                        executeTool(call, false, false) { result ->
                            outputs += ToolOutput(call.callId, result)
                            executeCloudCalls(calls, index + 1, outputs)
                        }
                    }
                }
            }
            Risk.DESTRUCTIVE -> {
                if (preparedReceipt != null &&
                    preparedReceipt.state !in setOf(ToolReceiptState.PROPOSED, ToolReceiptState.APPROVED)) {
                    executeTool(call, false, false) { result ->
                        outputs += ToolOutput(call.callId, result)
                        executeCloudCalls(calls, index + 1, outputs)
                    }
                } else showToolConfirmation(call, destructive = true) { allowed ->
                    if (allowed) {
                        executeNow()
                    } else {
                        executeTool(call, false, false) { result ->
                            outputs += ToolOutput(call.callId, result)
                            executeCloudCalls(calls, index + 1, outputs)
                        }
                    }
                }
            }
        }
    }

    private fun executeTool(
        call: ToolCallRequest,
        userConfirmed: Boolean,
        destructiveConfirmed: Boolean,
        done: (String) -> Unit
    ) {
        val label = ToolRegistry.label(call.name)
        addToolActivity("⏳ $label")
        Thread({
            val execution = createToolGate().execute(
                ToolInvocation(call.callId, call), userConfirmed, destructiveConfirmed
            )
            val result = execution.output ?: "TOOL_${execution.record.status}: ${execution.record.decisionReason}"
            runOnUiThread {
                val preview = result.replace("\n", " ").take(120)
                addToolActivity("${execution.record.status} · $label · $preview")
                done(result)
            }
        }, "device-ai-cloud-tool").apply { isDaemon = true; start() }
    }

    private fun createToolGate() = ToolGate(
        ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true),
        DeviceToolExecutorRunner(executor),
        receiptStore = receiptStore
    )

    private fun showPendingReceiptReview() {
        val receipts = runCatching { receiptStore.all() }
            .getOrElse {
                addToolActivity("ERROR · durable receipt store invalid; tool execution fails closed")
                AlertDialog.Builder(this)
                    .setTitle("Tool receipt validation failed")
                    .setMessage("Persisted tool state is corrupt or unsupported. Device tools remain blocked until operator review.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
        val uncertain = receipts.filter {
            it.state == ToolReceiptState.STARTED ||
                it.state in setOf(ToolReceiptState.FAILED, ToolReceiptState.TIMED_OUT, ToolReceiptState.CANCELLED) &&
                it.transitions.any { transition -> transition.state == ToolReceiptState.STARTED }
        }
        if (uncertain.isEmpty()) return
        addToolActivity("${uncertain.size} tool call(s) require operator review")
        AlertDialog.Builder(this)
            .setTitle("Previous tool outcome requires review")
            .setMessage(
                uncertain.take(12).joinToString("\n") {
                    "• ${ToolRegistry.label(it.toolName)} · ${it.callId.take(32)} · ${it.state}"
                } + "\n\nThe outcome may be unknown. These call ids will not be retried automatically."
            )
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun showToolConfirmation(
        call: ToolCallRequest,
        destructive: Boolean,
        callback: (Boolean) -> Unit
    ) {
        showStructuredToolConfirmation(ToolProposal(call.name, call.arguments), destructive, callback)
    }

    private fun showStructuredToolConfirmation(
        proposal: ToolProposal,
        destructive: Boolean,
        callback: (Boolean) -> Unit
    ) {
        val spec = ToolRegistry.get(proposal.toolName)
        val message = "${spec?.label ?: proposal.toolName}\nRisk: ${spec?.risk?.name ?: "UNKNOWN"}\n\n" +
            "Reason: ${spec?.description ?: "Tool is not registered."}\n\n" +
            "Arguments:\n${safeArgumentSummary(proposal.arguments)}"
        AlertDialog.Builder(this)
            .setTitle(if (destructive) "Kritische Aktion bestätigen" else "Aktion freigeben")
            .setMessage(message)
            .setNegativeButton("Ablehnen") { _, _ -> callback(false) }
            .setPositiveButton(if (destructive) "Weiter prüfen" else "Ausführen") { _, _ ->
                if (!destructive) {
                    callback(true)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Starke Bestätigung erforderlich")
                        .setMessage("Diese Aktion kann Daten verändern oder löschen.\n\n$message")
                        .setNegativeButton("Abbrechen") { _, _ -> callback(false) }
                        .setPositiveButton("Destruktiv ausführen") { _, _ -> callback(true) }
                        .setOnCancelListener { callback(false) }
                        .show()
                }
            }
            .setOnCancelListener { callback(false) }
            .show()
    }

    private fun safeArgumentSummary(arguments: Map<String, Any?>): String =
        if (arguments.isEmpty()) "Keine Parameter"
        else arguments.entries.take(16).joinToString("\n") { (key, value) ->
            val summary = when {
                key.contains(Regex("password|token|secret|credential|authorization", RegexOption.IGNORE_CASE)) ->
                    "[REDACTED]"
                value is String -> "[Text, ${value.length.coerceAtMost(4_096)} Zeichen]"
                value is List<*> -> "[Liste, ${value.size.coerceAtMost(100)} Elemente]"
                value is Number || value is Boolean -> value.toString().take(32)
                else -> "[Wert]"
            }
            "• ${key.take(64)}: $summary"
        }

    private fun runLocal(text: String) {
        val imported = localRuntime.loadedModel
        if (imported == null || localRuntime.state != LocalModelState.READY) {
            setBusy(false)
            addAssistantMessage("Kein lokales Modell ist bereit. Importiere und lade zuerst eine kompatible .litertlm-Datei.")
            refreshLocalModelUi()
            return
        }
        activeLocalRequest = true
        localRuntimeError = null
        val cancellation = at.heinz.deviceai.cognitive.DeliberationCancellation()
        activeChatCancellation = cancellation
        setBusy(true)
        addToolActivity("Lokales Modell plant …")
        planCard.visibility = View.GONE
        val turnId = UUID.randomUUID().toString().replace("-", "").take(16)
        val agentTask = text.take(7_900)
        var submittedToolObservationCount = 0
        val orchestrator = BoundedAgentOrchestrator(
            model = AgentModel { task, memories, reflection, observations ->
                cancellation.throwIfCancelled()
                val state = continuumState
                val compiledContext = state?.let { runtimeState ->
                    runCatching {
                        contextCompiler.compile(
                            AiContextInput(
                                seed = runtimeState.seed,
                                genome = runtimeState.archive.genome,
                                identityState = runtimeState.archive.identityState,
                                userRequest = task,
                                memories = memories,
                                activeTaskSummary = reflection?.correction?.take(1_024),
                                unresolvedContradictions = memories
                                    .filter { memory -> memory.validationState.name == "CONTRADICTED" }
                                    .map { memory -> memory.content.take(512) },
                                model = imported
                            )
                        )
                    }.getOrNull()
                }
                val advisory = if (observations.isEmpty() && reflection == null && submittedToolObservationCount == 0) {
                    ChatReasoningRouter(LocalModelReasoningAdapter(localRuntime), { localRuntime.cancel() })
                        .also { activeReasoningRouter = it }
                        .advise("chat-$turnId", task, compiledContext?.text ?: task)
                        .also { activeReasoningRouter = null }
                } else null
                cancellation.throwIfCancelled()
                val promptTail = "\nRequest at most one registered structured tool, or answer the user."
                val promptBody = buildString {
                    append("You are Device AI running on-device. Use registered tools only; never generate or execute shell commands.\n")
                    if (compiledContext != null) {
                        append(compiledContext.text)
                    } else {
                        append("User task:\n").append(task.take(7_900))
                        if (memories.isNotEmpty()) {
                            append("\n\nRelevant local memory:\n")
                            memories.take(8).forEach { append("- ").append(it.content.take(512)).append('\n') }
                        }
                        reflection?.let { append("\n\nCorrection: ").append(it.correction.take(512)) }
                    }
                    if (observations.isNotEmpty()) {
                        append("\n\nTool observations:\n")
                        observations.take(4).forEach {
                            append("- ").append(it.toolName).append(" [").append(it.status.name).append("]: ")
                                .append(it.output.orEmpty().take(2_000)).append('\n')
                        }
                    }
                }
                val prompt = ChatReasoningRouter.composePrompt(promptBody, advisory, promptTail, 15_500)
                val newObservations = observations.drop(submittedToolObservationCount)
                if (newObservations.isNotEmpty()) submittedToolObservationCount = observations.size
                val toolResponses = newObservations.take(4).map {
                    LocalToolResponse(
                        it.toolName,
                        org.json.JSONObject()
                            .put("status", it.status.name)
                            .put("output", it.output?.take(6_000) ?: org.json.JSONObject.NULL)
                            .toString()
                    )
                }
                var generation = generateLocal(cancellation) { callback ->
                    if (toolResponses.isEmpty()) localRuntime.stream(prompt, callback)
                    else localRuntime.respondToTools(toolResponses, callback)
                }
                generation.failure?.let { error(it) }
                var rejectionRecoveries = 0
                while (generation.rejectedToolResponses.isNotEmpty()) {
                    if (++rejectionRecoveries > 1) {
                        error("The model repeated a rejected tool request; no tool was executed.")
                    }
                    val rejected = generation.rejectedToolResponses
                    generation = generateLocal(cancellation) { callback -> localRuntime.respondToTools(rejected, callback) }
                    generation.failure?.let { error(it) }
                }
                AgentModelOutput(
                    "litertlm:${imported.id}@${imported.version}",
                    generation.response,
                    generation.proposal
                )
            },
            memories = { memoryStore.all() },
            toolGate = createToolGate(),
            evaluator = AgentEvaluator { response, _ ->
                Evaluation(response.isNotBlank(), if (response.isNotBlank()) 0.8 else 0.2, "On-device response evaluated.")
            },
            experienceStore = experienceStore,
            approvalProvider = AgentToolApprovalProvider { proposal, risk ->
                cancellation.throwIfCancelled()
                if (risk == Risk.READ_ONLY) return@AgentToolApprovalProvider AgentToolApproval(false)
                val latch = CountDownLatch(1)
                var approval = AgentToolApproval(false)
                runOnUiThread {
                    if (isFinishing || isDestroyed || runCatching { cancellation.throwIfCancelled() }.isFailure) {
                        latch.countDown()
                        return@runOnUiThread
                    }
                    showStructuredToolConfirmation(proposal, risk == Risk.DESTRUCTIVE) { approved ->
                        approval = AgentToolApproval(approved, approved && risk == Risk.DESTRUCTIVE)
                        latch.countDown()
                    }
                }
                val approvalDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
                while (!latch.await(10, TimeUnit.MILLISECONDS)) {
                    cancellation.throwIfCancelled()
                    if (System.nanoTime() >= approvalDeadline) return@AgentToolApprovalProvider AgentToolApproval(false)
                }
                cancellation.throwIfCancelled()
                approval
            },
            checkpointStore = FileAgentCheckpointStore(java.io.File(filesDir, "agent-checkpoints")),
            callIdPrefix = "local-$turnId",
            cancellation = cancellation
        )
        activeOrchestrator = orchestrator
        localAgentExecutor.execute {
            val result = runCatching { orchestrator.run(agentTask) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (activeChatCancellation !== cancellation) return@runOnUiThread
                activeChatCancellation = null
                activeOrchestrator = null
                activeLocalRequest = false
                planCard.visibility = View.GONE
                result.onSuccess { run ->
                    if (localRuntime.state != LocalModelState.GENERATING) localRuntimeError = null
                    addAssistantMessage(run.response.ifBlank { "Das lokale Modell hat keine Antwort erzeugt." })
                    persistRequestedMemory(text, run.experience?.evaluation)
                    checkpointContinuumAsync()
                    addToolActivity(if (run.blocker == null) "Lokal fertig · Experience evaluiert" else "Lokal beendet · ${run.blocker}")
                }.onFailure { error ->
                    localRuntimeError = error.message ?: "Local inference failed."
                    addAssistantMessage("Lokaler Modellfehler: ${error.message ?: error.javaClass.simpleName}")
                    addToolActivity("ERROR · lokale Inferenz")
                }
                setBusy(false)
                refreshLocalModelUi()
            }
        }
    }

    private fun persistRequestedMemory(request: String, evaluation: Evaluation?) {
        val explicit = Regex("\\b(remember|memorize|merke dir|merk dir)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(request)
        val sensitive = Regex("password|token|secret|credential|api[-_ ]?key", RegexOption.IGNORE_CASE)
            .containsMatchIn(request)
        val confidence = evaluation?.score ?: 0.0
        val decision = MemoryWritePolicy().decide(
            MemoryWriteContext(
                memoryType = MemoryType.SEMANTIC,
                provenance = ProvenanceType.USER_PROVIDED,
                confidence = confidence,
                explicitlyRequested = explicit,
                containsPersonalInformation = true,
                containsSensitiveInformation = sensitive
            )
        )
        if (!decision.shouldWrite || sensitive || evaluation?.success != true) return
        val now = System.currentTimeMillis()
        runCatching {
            memoryStore.put(
                MemoryRecord(
                    id = "user-${UUID.randomUUID()}",
                    type = MemoryType.SEMANTIC,
                    content = request.take(2_048),
                    provenance = ProvenanceType.USER_PROVIDED,
                    confidence = confidence,
                    createdAt = now,
                    updatedAt = now,
                    privacyClass = decision.privacyClass
                )
            )
        }
    }

    private fun initializeContinuum() {
        runCatching {
            continuumStore = ContinuumRuntimeStore(
                baseDirectory = java.io.File(filesDir, "continuum"),
                signer = AndroidKeystoreIdentitySigner()
            )
            continuumStore.openOrCreate()
        }.onSuccess { state ->
            continuumState = state
            state.archive.memories.forEach(memoryStore::put)
            state.archive.experiences.forEach(experienceStore::store)
            continuumStartupNote = buildString {
                append("CONTINUUM · generation ").append(state.manifest.generation)
                if (state.seedLoadSource.name != "PRIMARY" || state.archiveLoadSource.name != "PRIMARY") {
                    append(" · recovered from verified LKG")
                } else {
                    append(" · verified")
                }
            }
        }.onFailure { error ->
            continuumState = null
            continuumStartupNote =
                "CONTINUUM · unavailable; persistent state failed closed: " +
                    (error.message ?: error.javaClass.simpleName).take(180)
        }
    }

    private fun checkpointContinuumAsync() {
        if (!::continuumStore.isInitialized || localAgentExecutor.isShutdown) return
        localAgentExecutor.execute {
            val state = continuumState ?: return@execute
            val memories = memoryStore.all()
            val experiences = experienceStore.all()
            runCatching {
                continuumStore.checkpoint(
                    current = state,
                    memories = memories,
                    experiences = experiences
                )
            }.onSuccess { updated ->
                continuumState = updated
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        addToolActivity("CONTINUUM · state generation " + updated.manifest.generation + " committed")
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        addToolActivity(
                            "CONTINUUM · checkpoint failed closed · " +
                                (error.message ?: error.javaClass.simpleName).take(140)
                        )
                    }
                }
            }
        }
    }

    private fun showDevelopmentDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        box.addView(textView(
            "Ein Entwicklungsauftrag: Developer, Critic, TestDesigner und Judge laufen nacheinander auf dem geladenen " +
                "lokalen Modell und liefern EINEN prüfbaren Vorschlag. Nichts wird angewendet, kompiliert, getestet oder gepusht. " +
                "Quelltext wird als nicht vertrauenswürdige Daten behandelt.", 12f, MUTED))
        devGoalInput = EditText(this).apply {
            hint = "Entwicklungsziel für diese App…"
            setText(devController.state.goal)
            minLines = 2
        }
        box.addView(devGoalInput, matchWidth(top = 8))
        devPickButton = actionButton("QUELLTEXT WÄHLEN (SAF)").apply {
            setOnClickListener {
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(picker, DEV_SOURCE_REQUEST)
            }
        }
        box.addView(devPickButton, matchWidth(top = 8))
        devClearButton = actionButton("QUELLENAUSWAHL LEEREN").apply {
            setOnClickListener {
                devController.clearSources()
                refreshDevelopmentUi()
            }
        }
        box.addView(devClearButton, matchWidth(top = 4))
        devSourcesView = textView("", 12f, TEXT)
        box.addView(devSourcesView, matchWidth(top = 6))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        devStartButton = actionButton("START", accent = true).apply { setOnClickListener { startDevelopmentJob() } }
        devStopButton = actionButton("STOP").apply { setOnClickListener { cancelLocalRequest() } }
        devExportButton = actionButton("EXPORT").apply {
            setOnClickListener {
                if (devController.exportText() == null) return@setOnClickListener
                val create = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "device-ai-proposal.patch.txt")
                }
                startActivityForResult(create, DEV_EXPORT_REQUEST)
            }
        }
        listOf(devStartButton!!, devStopButton!!, devExportButton!!).forEach {
            row.addView(it, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        }
        box.addView(row, matchWidth(top = 8))
        devStatusView = textView("", 12f, AMBER, true)
        box.addView(devStatusView, matchWidth(top = 8))
        devPreviewView = textView("", 11f, TEXT).apply { setTextIsSelectable(true) }
        box.addView(devPreviewView, matchWidth(top = 6))
        val scroll = ScrollView(this).apply { addView(box) }
        devDialog = AlertDialog.Builder(this)
            .setTitle("Entwicklungsassistent")
            .setView(scroll)
            .setPositiveButton("Schließen", null)
            .setOnDismissListener {
                devController.setGoal(devGoalInput?.text?.toString().orEmpty())
                devDialog = null
            }
            .show()
        refreshDevelopmentUi()
    }

    private fun refreshDevelopmentUi() {
        if (devDialog == null) return
        val st = devController.state
        devSourcesView?.text = if (st.sources.isEmpty()) "Keine Quellen gewählt."
        else st.sources.joinToString("\n") { "• ${it.path} (${it.content.toByteArray(Charsets.UTF_8).size} B · sha256 ${it.sha256.take(12)}…)" }
        devStatusView?.text = st.status + (st.error?.let { "\n$it" } ?: "")
        devPreviewView?.text = st.preview.orEmpty()
        devStartButton?.isEnabled = st.canStart && !busy
        devPickButton?.isEnabled = st.canStart
        devClearButton?.isEnabled = st.canStart && st.sources.isNotEmpty()
        devStopButton?.isEnabled = st.canStop
        devExportButton?.isEnabled = st.canExport
    }

    private fun loadDevelopmentSources(uris: List<Uri>) {
        if (uris.isEmpty() || localAgentExecutor.isShutdown || !devController.beginSourceImport()) return
        refreshDevelopmentUi()
        try {
            localAgentExecutor.execute {
            val loaded = uris.map { uri ->
                runCatching {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: error("Dateiname unbekannt.")
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(DEV_MAX_SOURCE_READ_BYTES + 1) }
                        ?: error("Dokument kann nicht geöffnet werden.")
                    require(bytes.size <= DEV_MAX_SOURCE_READ_BYTES) { "$name ist größer als $DEV_MAX_SOURCE_READ_BYTES Bytes; kleinere Auswahl treffen." }
                    val text = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    name to text
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loaded.forEach { r ->
                    r.onSuccess { (name, text) -> devController.addSource(name, text) }
                        .onFailure { Toast.makeText(this, it.message ?: "Quelle nicht lesbar.", Toast.LENGTH_LONG).show() }
                }
                devController.finishSourceImport()
                refreshDevelopmentUi()
            }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            devController.finishSourceImport()
            refreshDevelopmentUi()
            Toast.makeText(this, "Dateiauswahl konnte nicht gestartet werden.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDevelopmentJob() {
        if (busy || localAgentExecutor.isShutdown) {
            Toast.makeText(this, "Der Assistent arbeitet noch; bitte zuerst stoppen.", Toast.LENGTH_SHORT).show()
            return
        }
        devController.setGoal(devGoalInput?.text?.toString().orEmpty())
        val task = devController.start(localRuntime.state == LocalModelState.READY && localRuntime.loadedModel != null)
        if (task == null) {
            refreshDevelopmentUi()
            return
        }
        val coordinator = DevelopmentCoordinator(LocalModelReasoningAdapter(localRuntime), { localRuntime.cancel() })
        activeDevCoordinator = coordinator
        activeLocalRequest = true
        setBusy(true)
        refreshDevelopmentUi()
        localAgentExecutor.execute {
            val outcome = coordinator.run(task) { state ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        devController.onJobState(state)
                        refreshDevelopmentUi()
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                devController.onOutcome(outcome)
                if (activeDevCoordinator === coordinator) activeDevCoordinator = null
                activeLocalRequest = false
                setBusy(false)
                refreshDevelopmentUi()
            }
        }
    }

    private fun stopDevelopmentJob() {
        activeDevCoordinator?.let {
            devController.stop()
            it.cancel()
            refreshDevelopmentUi()
        }
    }

    private fun exportDevelopmentProposal(uri: Uri) {
        val text = devController.exportText() ?: return
        localAgentExecutor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: error("Ziel kann nicht geöffnet werden.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this,
                    if (result.isSuccess) "Vorschlag exportiert (nur Text, nichts angewendet)." else "Export fehlgeschlagen.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cancelLocalRequest() {
        if (activeDevCoordinator != null) {
            // Keep the single-assistant lane busy until the cancelled worker has returned.
            stopDevelopmentJob()
            return
        }
        activeResearchCancellation?.cancel()
        input.hint = "Was soll ich auf deinem Gerät tun?"
        activeChatCancellation?.cancel()
        activeOrchestrator?.cancel()
        activeReasoningRouter?.cancel()
        stopDevelopmentJob()
        localRuntime.cancel()
        localRuntimeError = "Local request cancelled; a started Shizuku side effect may continue."
        activeLocalRequest = false
        setBusy(true)
        addToolActivity("Lokale Anfrage abgebrochen · ein bereits gestarteter Shizuku-Befehl kann extern weiterlaufen")
        refreshLocalModelUi()
    }

    private fun generateLocal(
        cancellation: at.heinz.deviceai.cognitive.DeliberationCancellation? = null,
        start: (at.heinz.deviceai.localmodel.LocalInferenceCallback) -> Unit
    ): LocalGeneration {
        cancellation?.throwIfCancelled()
        val completed = CountDownLatch(1)
        val response = StringBuilder()
        val rejectedResponses = mutableListOf<LocalToolResponse>()
        var proposal: ToolProposal? = null
        var failure: String? = null
        runOnUiThread { if (!isFinishing && !isDestroyed) refreshLocalModelUi() }
        val callback = at.heinz.deviceai.localmodel.LocalInferenceCallback { event ->
            when (event) {
                is LocalInferenceEvent.TextDelta -> if (response.length < 8_000) {
                    response.append(event.text.take(8_000 - response.length))
                }
                is LocalInferenceEvent.ToolProposal -> if (proposal == null) proposal = event.proposal
                is LocalInferenceEvent.ToolProposalRejected -> rejectedResponses += LocalToolResponse(
                    event.name,
                    org.json.JSONObject()
                        .put("status", "REJECTED")
                        .put("reason", event.reason.take(512))
                        .toString()
                )
                is LocalInferenceEvent.ToolBatchRejected -> rejectedResponses += event.responses
                is LocalInferenceEvent.Failed -> { failure = event.message; completed.countDown() }
                LocalInferenceEvent.Cancelled -> { failure = "Local generation cancelled."; completed.countDown() }
                is LocalInferenceEvent.Completed -> completed.countDown()
            }
        }
        val started = runCatching { cancellation?.throwIfCancelled(); start(callback) }
        if (started.isFailure) return LocalGeneration("", null, emptyList(), started.exceptionOrNull()?.message)
        val generationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120)
        while (!completed.await(10, TimeUnit.MILLISECONDS)) {
            cancellation?.throwIfCancelled()
            if (System.nanoTime() < generationDeadline) continue
            localRuntime.cancel()
            localRuntimeError = "Local generation exceeded its 120-second limit."
            runOnUiThread { refreshLocalModelUi() }
            return LocalGeneration("", null, emptyList(), "Local generation exceeded its 120-second limit.")
        }
        cancellation?.throwIfCancelled()
        val readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (localRuntime.state == LocalModelState.GENERATING && System.nanoTime() < readyDeadline) {
            cancellation?.throwIfCancelled()
            Thread.sleep(10)
        }
        if (localRuntime.state == LocalModelState.GENERATING) {
            localRuntime.cancel()
            localRuntimeError = "Local runtime did not finish generation."
            runOnUiThread { refreshLocalModelUi() }
            return LocalGeneration("", null, emptyList(), "Local runtime did not finish generation.")
        }
        return LocalGeneration(response.toString(), proposal, rejectedResponses.toList(), failure)
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

    private fun showModelImportDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), 0)
        }
        val id = EditText(this).apply {
            hint = "model-id (z. B. functiongemma)"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val version = EditText(this).apply {
            hint = "version (z. B. 1.0.0)"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val digest = EditText(this).apply {
            hint = "SHA-256 der Modelldatei"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        fields.addView(textView("Es werden keine Modelldaten heruntergeladen. CPU-Textmodelle mit LiteRT-LM-Unterstützung sind erforderlich.", 12f, MUTED))
        fields.addView(id, matchWidth(top = 8))
        fields.addView(version, matchWidth(top = 4))
        fields.addView(digest, matchWidth(top = 4))
        AlertDialog.Builder(this)
            .setTitle("Lokales Modell importieren")
            .setView(fields)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Datei auswählen") { _, _ ->
                val modelId = id.text.toString().trim()
                val modelVersion = version.text.toString().trim()
                val sha256 = digest.text.toString().trim().lowercase()
                if (!modelId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) ||
                    !modelVersion.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) ||
                    !sha256.matches(Regex("[a-f0-9]{64}"))) {
                    Toast.makeText(this, "Bitte Model-ID, Version und gültigen SHA-256 angeben.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                pendingModelImport = Triple(modelId, modelVersion, sha256)
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }
                startActivityForResult(picker, MODEL_IMPORT_REQUEST)
            }
            .show()
    }

    private fun chooseInstalledModel() {
        val models = modelCatalog.all()
        if (models.isEmpty()) {
            refreshLocalModelUi()
            Toast.makeText(this, "NO MODEL · importiere zuerst eine .litertlm-Datei.", Toast.LENGTH_LONG).show()
            return
        }
        if (localRuntime.state != LocalModelState.EMPTY && localRuntime.state != LocalModelState.FAILED) {
            Toast.makeText(this, "Entlade zuerst das aktuell geladene Modell.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = models.map { "${it.id} · ${it.version}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Installiertes Modell auswählen")
            .setItems(labels) { _, selected -> loadModel(models[selected]) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun loadModel(model: ImportedLocalModel) {
        if (localRuntime.state != LocalModelState.EMPTY && localRuntime.state != LocalModelState.FAILED) return
        localRuntimeError = null
        refreshLocalModelUi()
        scheduleLifecycleTimeout("Model initialization exceeded 120 seconds; the native runtime may still be working.")
        localRuntime.load(model.id, model.version) { result ->
            runOnUiThread {
                clearLifecycleTimeout()
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onFailure {
                    localRuntimeError = it.message ?: "Model compatibility could not be verified."
                    Toast.makeText(this, "Modellfehler: ${it.message ?: "Kompatibilität unbekannt"}", Toast.LENGTH_LONG).show()
                }
                if (result.isSuccess) localRuntimeError = null
                refreshLocalModelUi()
            }
        }
        refreshLocalModelUi()
    }

    private fun unloadModel() {
        localRuntimeError = null
        modelUnloading = true
        localRuntime.cancel()
        scheduleLifecycleTimeout("Model unload exceeded 120 seconds; native cleanup may still be in progress.")
        runCatching {
            localRuntime.unload { result ->
                runOnUiThread {
                    clearLifecycleTimeout()
                    modelUnloading = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    result.onFailure {
                        localRuntimeError = it.message ?: "Model unload failed."
                        Toast.makeText(this, "Modell konnte nicht entladen werden.", Toast.LENGTH_LONG).show()
                    }
                    if (result.isSuccess) localRuntimeError = null
                    refreshLocalModelUi()
                }
            }
        }.onFailure {
            modelUnloading = false
            localRuntimeError = it.message ?: "Model unload could not start."
            Toast.makeText(this, "Modell-Lifecycle ist bereits beschäftigt.", Toast.LENGTH_SHORT).show()
        }
        refreshLocalModelUi()
    }

    private fun importModel(uri: Uri) {
        val metadata = pendingModelImport ?: return
        pendingModelImport = null
        modelImportActive = true
        localRuntimeError = null
        refreshLocalModelUi()
        scheduleLifecycleTimeout("Model import exceeded 120 seconds; the document provider may still be reading.")
        localAgentExecutor.execute {
            val result = runCatching {
                val source = contentResolver.openInputStream(uri)
                    ?: error("Dokument kann nicht geöffnet werden.")
                modelCatalog.import(metadata.first, metadata.second, source, metadata.third)
            }
            runOnUiThread {
                clearLifecycleTimeout()
                modelImportActive = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { model ->
                    localRuntimeError = null
                    addToolActivity("Import geprüft · SHA-256 und CPU-Fähigkeit bestätigt")
                    refreshLocalModelUi()
                    loadModel(model)
                }.onFailure { error ->
                    localRuntimeError = error.message ?: "Model import failed."
                    addAssistantMessage("Modellimport fehlgeschlagen: ${error.message ?: "Kompatibilität unbekannt"}")
                    refreshLocalModelUi()
                }
            }
        }
    }

    private fun refreshLocalModelUi() {
        if (!::modelCardText.isInitialized || !::localBadge.isInitialized) return
        val models = runCatching { modelCatalog.all() }.getOrDefault(emptyList())
        val loaded = localRuntime.loadedModel
        val state = when {
            localRuntimeError != null -> LocalModelUiState.ERROR
            modelImportActive || modelUnloading -> LocalModelUiState.LOADING
            else -> LocalFirstPolicy.modelUiState(
                localRuntime.state, models.isNotEmpty(), mode == AssistantMode.LOCAL
            )
        }
        val label = when (state) {
            LocalModelUiState.NO_MODEL -> "NO MODEL"
            LocalModelUiState.INSTALLED -> "INSTALLED"
            LocalModelUiState.READY -> "READY"
            LocalModelUiState.LOADING -> "LOADING"
            LocalModelUiState.GENERATING -> "GENERATING"
            LocalModelUiState.ERROR -> "ERROR"
            LocalModelUiState.OFFLINE -> "OFFLINE"
        }
        val color = when (state) {
            LocalModelUiState.READY -> GREEN
            LocalModelUiState.GENERATING, LocalModelUiState.LOADING -> AMBER
            LocalModelUiState.ERROR -> RED
            LocalModelUiState.OFFLINE, LocalModelUiState.NO_MODEL, LocalModelUiState.INSTALLED -> MUTED
        }
        localBadge.text = "● $label"
        localBadge.setTextColor(color)
        localBadge.background = rounded(PANEL, 14, BORDER)
        val shown = loaded ?: models.firstOrNull()
        modelCardText.text = when {
            localRuntimeError != null -> "ERROR · ${localRuntimeError!!.take(256)}"
            modelImportActive -> "LOADING · Datei wird geprüft und sicher importiert."
            modelUnloading -> "LOADING · Modell wird entladen."
            loaded == null && models.isEmpty() ->
                "NO MODEL · Importiere eine kompatible .litertlm-Datei. Kein Download oder Modellgewicht ist enthalten."
            loaded == null && shown != null ->
                "${shown.id} · ${shown.version} installiert, noch nicht geladen. Tippe MODELL LADEN.\n" +
                    "CPU: ${shown.capabilities.supportedBackends.contains(LocalModelBackend.CPU)} · " +
                    "Funktionen: ${shown.capabilities.functionCalling}"
            shown != null -> {
                val caps = shown.capabilities
                "${shown.id} · ${shown.version} · LiteRT-LM\n" +
                    "CPU baseline · ${caps.supportedBackends.sortedBy { it.name }.joinToString()} · " +
                    "Text ${caps.text} · Vision ${caps.vision} · Audio ${caps.audio} · " +
                    "Function calling ${caps.functionCalling} · Context ${caps.maxContextTokens} tokens\n" +
                    if (state == LocalModelUiState.OFFLINE) "LOCAL-only · Cloud wird nicht verwendet." else "Ready for on-device inference."
            }
            else -> "Kompatibilität des Modells ist unbekannt."
        }
        if (::localActionButton.isInitialized) {
            localActionButton.text = if (loaded != null) "MODELL ENTLADEN" else "MODELL LADEN"
            localActionButton.isEnabled = !busy && !modelUnloading && (loaded != null || models.isNotEmpty())
        }
    }

    private fun scheduleLifecycleTimeout(message: String) {
        clearLifecycleTimeout()
        lifecycleTimeout = Runnable {
            localRuntimeError = message
            refreshLocalModelUi()
        }.also { mainHandler.postDelayed(it, 120_000) }
    }

    private fun clearLifecycleTimeout() {
        lifecycleTimeout?.let(mainHandler::removeCallbacks)
        lifecycleTimeout = null
    }

    private fun setMode(value: AssistantMode) {
        mode = value
        applyModeStyles()
        addToolActivity(
            when (value) {
                AssistantMode.AUTO -> "AUTO · lokales Modell zuerst; kein stiller Cloud-Fallback"
                AssistantMode.CLOUD -> "ONLINE · optionaler Cloud-Modus"
                AssistantMode.LOCAL -> "LOKAL · keine Cloud-Anfrage"
            }
        )
        refreshLocalModelUi()
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
            sendButton.isEnabled = true
            sendButton.text = if (value && activeLocalRequest) "STOPPEN" else if (value) "ARBEITET …" else "SENDEN"
            sendButton.alpha = if (value && !activeLocalRequest) 0.65f else 1f
        }
        if (::modelCatalog.isInitialized && ::localBadge.isInitialized) refreshLocalModelUi()
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
        when (requestCode) {
            SPEECH_REQUEST -> if (resultCode == RESULT_OK) {
                val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    input.setText(spoken)
                    input.setSelection(spoken.length)
                }
            }
            MODEL_IMPORT_REQUEST -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) importModel(uri)
                else pendingModelImport = null
            }
            DEV_SOURCE_REQUEST -> if (resultCode == RESULT_OK && data != null) {
                val uris = mutableListOf<Uri>()
                data.data?.let(uris::add)
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
                val unique = uris.distinct()
                if (unique.size > at.heinz.deviceai.cognitive.DevelopmentLimits.MAX_SOURCES) {
                    Toast.makeText(this, "Maximal vier Quellen auswählen; keine Datei wurde geladen.", Toast.LENGTH_LONG).show()
                } else {
                    loadDevelopmentSources(unique)
                }
            }
            DEV_EXPORT_REQUEST -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) exportDevelopmentProposal(uri)
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
            background = if (accent) {
                roundedGradient(Color.rgb(27, 133, 246), Color.rgb(53, 78, 236), 13, Color.rgb(81, 187, 255))
            } else {
                roundedGradient(Color.rgb(29, 42, 65), SURFACE, 13, BORDER)
            }
            setPadding(if (compact) dp(7) else dp(12), 0, if (compact) dp(7) else dp(12), 0)
            minWidth = 0
            minHeight = 0
        }

    private fun verticalCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedGradient(Color.rgb(19, 35, 59), Color.rgb(19, 28, 46), 18, BORDER)
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

    private fun roundedGradient(
        start: Int, end: Int, radius: Int, stroke: Int
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
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

