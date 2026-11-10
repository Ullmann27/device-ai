package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val SPEECH_REQUEST = 1207

        private val BG = Color.rgb(8, 12, 18)
        private val CARD = Color.rgb(18, 25, 35)
        private val SURFACE = Color.rgb(29, 38, 51)
        private val SURFACE_2 = Color.rgb(37, 48, 64)
        private val BORDER = Color.rgb(55, 69, 89)
        private val TEXT = Color.rgb(244, 247, 252)
        private val MUTED = Color.rgb(157, 171, 194)
        private val ACCENT = Color.rgb(124, 156, 255)
        private val ACCENT_DARK = Color.rgb(61, 82, 145)
        private val GREEN = Color.rgb(92, 214, 145)
        private val AMBER = Color.rgb(255, 196, 92)
        private val RED = Color.rgb(255, 112, 112)
    }

    private lateinit var bridge: ShizukuBridge
    private lateinit var interpreter: CommandInterpreter

    private lateinit var statusText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var connectButton: Button

    private lateinit var input: EditText
    private lateinit var runButton: Button

    private lateinit var planCard: LinearLayout
    private lateinit var planTitle: TextView
    private lateinit var planBody: TextView

    private lateinit var resultText: TextView
    private lateinit var resultTitle: TextView
    private lateinit var historyContainer: LinearLayout

    private var lastResult: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG

        interpreter = CommandInterpreter(this)
        bridge = ShizukuBridge(this) { message ->
            runOnUiThread {
                updateConnectionUi(message)
                runButton.isEnabled = true
            }
        }

        setContentView(buildUi())
        bridge.start()
        renderHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) {
            bridge.refresh()
        }
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(34))
        }
        scroll.addView(content, matchWidth())

        content.addView(buildHeader())
        content.addView(buildConnectionCard(), matchWidth(top = 14))
        content.addView(
            textView("Manuelle Expert-Eingabe · niemals durch das lokale Modell aufrufbar", 12f, AMBER, bold = true)
                .apply { setPadding(dp(4), dp(10), dp(4), 0) },
            matchWidth(top = 10)
        )
        content.addView(buildCommandCard(), matchWidth(top = 6))
        content.addView(buildQuickActions(), matchWidth(top = 14))

        planCard = verticalCard().apply {
            visibility = View.GONE
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        planTitle = textView("Plan", 13f, ACCENT, bold = true)
        planBody = textView("", 14f, TEXT)
        planBody.setPadding(0, dp(8), 0, 0)
        planCard.addView(planTitle)
        planCard.addView(planBody)
        content.addView(planCard, matchWidth(top = 14))

        content.addView(buildResultCard(), matchWidth(top = 14))
        content.addView(buildHistoryCard(), matchWidth(top = 14))

        val footer = textView(
            "LOCAL · SHIZUKU · KEIN ROOT · v0.2",
            11f,
            Color.rgb(104, 118, 140)
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        content.addView(footer, matchWidth())

        return scroll
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        left.addView(textView("Device AI", 31f, TEXT, bold = true))
        left.addView(
            textView(
                "EXPERT · USER-ONLY · SHIZUKU",
                14f,
                MUTED
            ).apply { setPadding(0, dp(3), 0, 0) }
        )

        statusBadge = textView("● START", 12f, AMBER, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(31, 35, 44), 999, BORDER)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        row.addView(
            left,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(statusBadge)

        return row
    }

    private fun buildConnectionCard(): View {
        val card = verticalCard().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }

        val label = textView("VERBINDUNG", 11f, MUTED, bold = true)
        card.addView(label)

        statusText = textView("Prüfe Shizuku …", 15f, TEXT).apply {
            setPadding(0, dp(7), 0, dp(12))
        }
        card.addView(statusText)

        connectButton = actionButton("SHIZUKU VERBINDEN", accent = true).apply {
            setOnClickListener {
                when {
                    bridge.isReady() -> {
                        Toast.makeText(this@MainActivity, "Device AI ist bereits verbunden.", Toast.LENGTH_SHORT).show()
                    }
                    !bridge.isShizukuRunning() -> openShizuku()
                    else -> bridge.requestAccess()
                }
            }
        }
        card.addView(connectButton, matchWidth())

        return card
    }

    private fun buildCommandCard(): View {
        val card = verticalCard().apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        card.addView(textView("Was soll ich tun?", 20f, TEXT, bold = true))
        card.addView(
            textView(
                "Du kannst mehrere Aktionen in einem Satz kombinieren.",
                13f,
                MUTED
            ).apply { setPadding(0, dp(4), 0, dp(12)) }
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            background = rounded(SURFACE, 18, BORDER)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        input = EditText(this).apply {
            hint = "z. B. „Zeige die Dateien in Downloads“"
            setHintTextColor(Color.rgb(112, 126, 148))
            setTextColor(TEXT)
            textSize = 16f
            minLines = 3
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = null
            setPadding(dp(12), dp(11), dp(8), dp(11))
        }

        val mic = actionButton("🎙", compact = true).apply {
            contentDescription = "Spracheingabe"
            setOnClickListener { startVoiceInput() }
        }

        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        inputRow.addView(
            mic,
            LinearLayout.LayoutParams(dp(54), dp(54))
        )
        card.addView(inputRow, matchWidth())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        runButton = actionButton("AUSFÜHREN", accent = true).apply {
            setOnClickListener { handleInput() }
        }

        val clearButton = actionButton("LEEREN").apply {
            setOnClickListener {
                input.text.clear()
                planCard.visibility = View.GONE
            }
        }

        actions.addView(
            runButton,
            LinearLayout.LayoutParams(0, dp(50), 2f).apply {
                topMargin = dp(10)
                marginEnd = dp(6)
            }
        )
        actions.addView(
            clearButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                topMargin = dp(10)
                marginStart = dp(6)
            }
        )

        card.addView(actions)
        return card
    }

    private fun buildQuickActions(): View {
        val card = verticalCard().apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        card.addView(textView("Schnellaktionen", 15f, TEXT, bold = true))

        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        val actions = listOf(
            "Apps" to "Apps",
            "Downloads" to "Downloads",
            "Fotos" to "Fotos",
            "Screenshot" to "Screenshot",
            "Speicher" to "Speicher",
            "Akku" to "Akku",
            "Fehler" to "Zeig letzte Fehler",
            "Große Dateien" to "Zeig große Dateien",
            "CPU" to "Prozesse"
        )

        actions.forEach { (label, command) ->
            row.addView(
                chip(label, command),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                ).apply { marginEnd = dp(8) }
            )
        }

        horizontal.addView(row)
        card.addView(horizontal, matchWidth())
        return card
    }

    private fun buildResultCard(): View {
        val card = verticalCard().apply {
            setPadding(dp(16), dp(15), dp(16), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        resultTitle = textView("Ergebnis", 16f, TEXT, bold = true)
        header.addView(
            resultTitle,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        header.addView(
            actionButton("KOPIEREN", compact = true).apply {
                setOnClickListener { copyResult() }
            },
            LinearLayout.LayoutParams(dp(104), dp(40))
        )
        card.addView(header)

        resultText = textView(
            "Bereit. Sag mir, was ich auf dem Gerät tun soll.",
            14f,
            Color.rgb(216, 225, 239)
        ).apply {
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(12, 17, 24), 15, Color.rgb(40, 51, 67))
        }
        card.addView(resultText, matchWidth(top = 12))

        return card
    }

    private fun buildHistoryCard(): View {
        val card = verticalCard().apply {
            setPadding(dp(16), dp(15), dp(16), dp(16))
        }
        card.addView(textView("Zuletzt verwendet", 15f, TEXT, bold = true))
        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(historyContainer, matchWidth())
        return card
    }

    private fun handleInput() {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) {
            Toast.makeText(this, "Schreib zuerst einen Auftrag.", Toast.LENGTH_SHORT).show()
            return
        }

        val plan = interpreter.interpret(raw) ?: return
        renderPlan(plan)
        addHistory(raw)

        when (plan.risk) {
            Risk.READ_ONLY -> execute(plan)
            Risk.CONFIRM -> {
                AlertDialog.Builder(this)
                    .setTitle(plan.title)
                    .setMessage(ExpertModeConsent.confirmMessage(plan))
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Ausführen") { _, _ -> execute(plan) }
                    .show()
            }
            Risk.DESTRUCTIVE -> {
                AlertDialog.Builder(this)
                    .setTitle(ExpertModeConsent.DESTRUCTIVE_TITLE)
                    .setMessage(ExpertModeConsent.destructiveFirstMessage(plan))
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton(ExpertModeConsent.DESTRUCTIVE_CONTINUE) { _, _ ->
                        AlertDialog.Builder(this)
                            .setTitle(ExpertModeConsent.STRONG_TITLE)
                            .setMessage(ExpertModeConsent.destructiveStrongMessage(plan))
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton(ExpertModeConsent.STRONG_CONFIRM) { _, _ -> execute(plan) }
                            .show()
                    }
                    .show()
            }
        }
    }

    private fun execute(plan: CommandPlan) {
        if (!bridge.isReady()) {
            resultTitle.text = "Nicht verbunden"
            resultText.text = "Shizuku ist noch nicht bereit. Starte Shizuku und verbinde Device AI."
            return
        }

        runButton.isEnabled = false
        resultTitle.text = "Agent arbeitet"
        lastResult = ""
        resultText.text = "⏳ ${plan.title}\n\n${plan.summary}"

        val results = mutableListOf<String>()

        fun runStep(index: Int) {
            // A recreated or closed activity must not keep running confirmed steps unseen.
            if (isFinishing || isDestroyed) return
            if (index >= plan.steps.size) {
                runButton.isEnabled = true
                resultTitle.text = "Fertig"
                lastResult = results.joinToString("\n\n")
                resultText.text = lastResult.ifBlank { "✓ Auftrag abgeschlossen." }
                return
            }

            val step = plan.steps[index]
            resultTitle.text = "Schritt ${index + 1}/${plan.steps.size}"
            resultText.text = buildString {
                append("⏳ ${step.label}\n\n")
                if (results.isNotEmpty()) {
                    append(results.joinToString("\n\n"))
                }
            }

            bridge.execute(step.shellCommand) { result ->
                results += "✓ ${step.label}\n$result"
                runStep(index + 1)
            }
        }

        runStep(0)
    }

    private fun renderPlan(plan: CommandPlan) {
        planCard.visibility = View.VISIBLE
        planTitle.text = when (plan.risk) {
            Risk.READ_ONLY -> "PLAN · SICHER"
            Risk.CONFIRM -> "PLAN · BESTÄTIGUNG"
            Risk.DESTRUCTIVE -> "PLAN · KRITISCH"
        }
        planTitle.setTextColor(
            when (plan.risk) {
                Risk.READ_ONLY -> GREEN
                Risk.CONFIRM -> AMBER
                Risk.DESTRUCTIVE -> RED
            }
        )
        planBody.text = plan.steps.mapIndexed { index, step ->
            "${index + 1}. ${step.label}"
        }.joinToString("\n")
    }

    private fun updateConnectionUi(message: String) {
        val ready = message.startsWith("Bereit")
        statusText.text = message
        statusText.setTextColor(if (ready) GREEN else TEXT)

        statusBadge.text = if (ready) "● ONLINE" else "● OFFLINE"
        statusBadge.setTextColor(if (ready) GREEN else AMBER)
        statusBadge.background = rounded(
            if (ready) Color.rgb(18, 48, 36) else Color.rgb(49, 38, 19),
            999,
            if (ready) Color.rgb(45, 104, 76) else Color.rgb(100, 78, 37)
        )

        connectButton.text = when {
            ready -> "✓ SHIZUKU VERBUNDEN"
            message.contains("noch nicht gestartet", ignoreCase = true) -> "SHIZUKU ÖFFNEN"
            else -> "SHIZUKU VERBINDEN"
        }
        connectButton.background = rounded(
            if (ready) Color.rgb(28, 91, 61) else ACCENT_DARK,
            14
        )
    }

    private fun openShizuku() {
        val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Shizuku ist nicht installiert.", Toast.LENGTH_LONG).show()
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

    private fun copyResult() {
        val text = lastResult.ifBlank { resultText.text.toString() }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Device AI Ergebnis", text))
        Toast.makeText(this, "Ergebnis kopiert.", Toast.LENGTH_SHORT).show()
    }

    private fun addHistory(command: String) {
        val history = loadHistory().toMutableList()
        history.remove(command)
        history.add(0, command)
        val trimmed = history.take(6)

        getSharedPreferences("device_ai", MODE_PRIVATE)
            .edit()
            .putString("history", trimmed.joinToString("\u001F"))
            .apply()

        renderHistory()
    }

    private fun loadHistory(): List<String> {
        val raw = getSharedPreferences("device_ai", MODE_PRIVATE)
            .getString("history", "")
            .orEmpty()
        return raw.split("\u001F").filter { it.isNotBlank() }.take(6)
    }

    private fun renderHistory() {
        if (!::historyContainer.isInitialized) return
        historyContainer.removeAllViews()
        val history = loadHistory()

        if (history.isEmpty()) {
            historyContainer.addView(textView("Noch keine Aufträge.", 13f, MUTED))
            return
        }

        history.forEachIndexed { index, command ->
            val item = textView(command, 14f, Color.rgb(220, 228, 240)).apply {
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = rounded(SURFACE, 13, BORDER)
                setOnClickListener {
                    input.setText(command)
                    input.setSelection(command.length)
                }
            }
            historyContainer.addView(
                item,
                matchWidth(top = if (index == 0) 0 else 7)
            )
        }
    }

    private fun chip(label: String, command: String): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.rgb(226, 233, 245))
            background = rounded(SURFACE_2, 14, BORDER)
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = 0
            minHeight = 0
            setOnClickListener {
                input.setText(command)
                input.setSelection(command.length)
                handleInput()
            }
        }

    private fun actionButton(
        label: String,
        accent: Boolean = false,
        compact: Boolean = false
    ): Button =
        Button(this).apply {
            text = label
            textSize = if (compact) 12f else 13f
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT)
            background = rounded(if (accent) ACCENT_DARK else SURFACE_2, 14, BORDER)
            setPadding(
                if (compact) dp(8) else dp(14),
                0,
                if (compact) dp(8) else dp(14),
                0
            )
            minWidth = 0
            minHeight = 0
        }

    private fun verticalCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 22, Color.rgb(31, 42, 56))
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
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
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
