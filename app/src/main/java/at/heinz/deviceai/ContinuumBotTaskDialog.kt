package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.chief.ChiefBotAccent
import at.heinz.deviceai.chief.ChiefBotPersonas
import at.heinz.deviceai.chief.ChiefBotProfile
import at.heinz.deviceai.chief.ChiefBotTaskMode
import at.heinz.deviceai.chief.ChiefBotTaskRequest
import at.heinz.deviceai.chief.ChiefBotTaskTicket
import at.heinz.deviceai.ui.ContinuumFonts
import at.heinz.deviceai.ui.FontFamily
import at.heinz.deviceai.ui.JarvisDesign

/** An editable owner draft. Nothing is dispatched until an explicit mode and final click. */
class ContinuumBotTaskDialog(
    private val activity: Activity,
    private val profile: ChiefBotProfile,
    private val onAssign: (ChiefBotTaskRequest) -> Boolean,
    private val runtimeStatus: ((ChiefBotProfile) -> String)? = null
) {
    private val ui = ContinuumBotDialogStyle(activity)
    private var dialog: AlertDialog? = null
    private var revision = 0L
    private var submitting = false
    private var statusView: TextView? = null

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        close()
        val token = ++revision
        var handoffUncertain = false
        val persona = ChiefBotPersonas.describe(profile)
        val content = ui.column()
        content.addView(ui.label("AUFTRAG VORBEREITEN", 12f, JarvisDesign.Colors.cyan))
        content.addView(ui.identity(profile))
        content.addView(ui.label(persona.tagline))
        statusView = ui.label(runtimeStatus?.invoke(profile) ?: "Profil gespeichert · Modellstatus nicht verfügbar",
            13f, JarvisDesign.Colors.textSecondary).also { content.addView(it) }

        content.addView(ui.label("Was soll ${profile.displayName} für dich tun?", 18f))
        val request = ui.editor(multiline = true).apply {
            hint = "Ziel, gewünschtes Ergebnis und deine Grenzen …"
            contentDescription = "Vollständiger Auftrag für ${profile.displayName}"
            tag = "bot-task-text"
            minLines = 4
            maxLines = 10
        }
        content.addView(request)
        val count = ui.label("", 12f, JarvisDesign.Colors.textSecondary)
        content.addView(count)
        val feedback = ui.label("Wähle eine Auftragsart und prüfe deinen Text.", 13f, JarvisDesign.Colors.textSecondary).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            tag = "bot-task-feedback"
        }
        content.addView(feedback)

        content.addView(ui.label("Art des Auftrags bewusst wählen", 16f))
        val modes = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            contentDescription = "Art des Bot-Auftrags"
        }
        val choices = linkedMapOf<Int, ChiefBotTaskMode>()
        val descriptions = listOf(
            Triple(ChiefBotTaskMode.PRIVATE_REPLY, "Private Antwort",
                "Antwort im privaten Bot-Dialog mit dessen Gesprächskontext. Es werden keine Werkzeuge gestartet."),
            Triple(ChiefBotTaskMode.LOCAL_TOOLS, "Lokaler Auftrag über JARVIS",
                "Nutzt vorhandene JARVIS-Freigaben. Höchstens ${ChiefBotTaskTicket.MAX_OWNER_MODEL_CALLS} Modellaufrufe, " +
                    "${ChiefBotTaskTicket.MAX_OWNER_TOOL_CALLS} Werkzeuge und ${ChiefBotTaskTicket.MAX_OWNER_DEADLINE_MILLIS / 1_000} Sekunden. " +
                    "Private Bot-Notizen und Dialoge werden nicht übernommen."),
            Triple(ChiefBotTaskMode.CODING_DRAFT, "Coding-Entwurf vorbereiten",
                "Übergibt dein Ziel an den bestehenden prüfbaren Entwicklungsentwurf. " +
                    "Kein automatisches Übernehmen oder Ausführen von Änderungen.")
        )
        for ((mode, title, explanation) in descriptions) {
            val id = View.generateViewId()
            choices[id] = mode
            modes.addView(RadioButton(activity).apply {
                this.id = id
                text = title
                textSize = 15f
                typeface = ui.bodyTypeface
                setTextColor(JarvisDesign.Colors.text)
                buttonTintList = android.content.res.ColorStateList.valueOf(JarvisDesign.Colors.cyan)
                minHeight = ui.dp(48)
                setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6))
                tag = "bot-task-mode-${mode.name}"
            }, LinearLayout.LayoutParams(-1, -2))
            modes.addView(ui.label(explanation, 13f, JarvisDesign.Colors.textSecondary).apply {
                setPadding(ui.dp(12), 0, ui.dp(8), ui.dp(12))
            })
        }
        content.addView(modes)

        content.addView(ui.label("Ideen zum Bearbeiten", 16f))
        content.addView(ui.label("Ein Beispiel füllt nur das Textfeld. Du kannst es vor der Übergabe ändern.",
            13f, JarvisDesign.Colors.textSecondary))
        persona.starterRequests.forEachIndexed { index, example ->
            content.addView(ui.action(example) {
                if (live(token) && !submitting) {
                    request.setText(example)
                    request.setSelection(request.text.length)
                }
            }.apply { tag = "bot-task-example-$index" })
        }

        val shell = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        shell.addView(ScrollView(activity).apply {
            isFillViewport = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = ui.column().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(10)) }
        val submit = ui.action("Auftrag übergeben", primary = true) {}.apply { tag = "bot-task-submit" }
        footer.addView(submit)
        footer.addView(ui.action("Abbrechen") { if (live(token)) close() })
        shell.addView(footer, LinearLayout.LayoutParams(-1, -2))

        fun updateValidation() {
            val value = request.text.toString()
            val mode = choices[modes.checkedRadioButtonId]
            val bytes = value.toByteArray(Charsets.UTF_8).size
            val tooLong = bytes > ChiefBotTaskRequest.MAX_TEXT_BYTES ||
                (mode == ChiefBotTaskMode.CODING_DRAFT && value.length > ChiefBotTaskRequest.MAX_CODING_CHARACTERS)
            count.text = "$bytes / ${ChiefBotTaskRequest.MAX_TEXT_BYTES} UTF-8-Bytes" +
                if (mode == ChiefBotTaskMode.CODING_DRAFT)
                    " · ${value.length} / ${ChiefBotTaskRequest.MAX_CODING_CHARACTERS} Zeichen für den Coding-Entwurf" else ""
            count.setTextColor(if (tooLong) JarvisDesign.Colors.warn else JarvisDesign.Colors.textSecondary)
            submit.isEnabled = !submitting && mode != null && value.isNotBlank() && !tooLong
            submit.alpha = if (submit.isEnabled) 1f else .55f
            feedback.text = when {
                handoffUncertain -> "Die Annahme des Auftrags konnte nicht bestätigt werden. Prüfe den Auftragsstatus, bevor du ihn erneut übergibst. Dein Text bleibt erhalten."
                tooLong -> "Der Text ist zu lang. Bitte selbst kürzen; deine Eingabe bleibt vollständig erhalten."
                mode == null -> "Wähle zuerst eine Auftragsart."
                value.isBlank() -> "Beschreibe zuerst deinen Auftrag."
                else -> "Erst „Auftrag übergeben“ sendet diesen vollständigen Text."
            }
        }
        request.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateValidation()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        modes.setOnCheckedChangeListener { _, _ -> updateValidation() }
        submit.setOnClickListener {
            if (!live(token) || submitting) return@setOnClickListener
            val mode = choices[modes.checkedRadioButtonId] ?: return@setOnClickListener
            val task = runCatching {
                ChiefBotTaskRequest(profile.agentId, profile.profileVersion, request.text.toString(), mode)
            }.getOrElse {
                feedback.text = "Der Auftrag enthält unzulässige Zeichen oder überschreitet die Grenze. Bitte die Eingabe prüfen."
                return@setOnClickListener
            }
            submitting = true
            submit.isEnabled = false
            val accepted = try { onAssign(task) } catch (_: Exception) {
                // A callback may have accepted work before throwing; never turn that into a retry.
                handoffUncertain = true
                updateValidation()
                return@setOnClickListener
            }
            if (accepted) {
                close()
            } else if (live(token)) {
                submitting = false
                updateValidation()
                feedback.text = "Übergabe derzeit nicht möglich. Dein vollständiger Auftrag bleibt hier zum Bearbeiten oder erneuten Übergeben."
            }
        }
        updateValidation()
        dialog = AlertDialog.Builder(activity).setView(shell).create().also { current ->
            current.setOnDismissListener {
                if (dialog === current) {
                    revision++
                    dialog = null
                    statusView = null
                }
            }
            current.show()
            ui.fitWindow(current)
        }
    }

    fun refreshRuntimeStatus() {
        if (dialog?.isShowing != true || activity.isFinishing || activity.isDestroyed) return
        runtimeStatus?.let { statusView?.text = it(profile) }
    }

    fun close() {
        revision++
        val current = dialog
        dialog = null
        statusView = null
        submitting = false
        current?.dismiss()
    }

    private fun live(token: Long) = token == revision && dialog?.isShowing == true &&
        !activity.isFinishing && !activity.isDestroyed
}

/** Native dialog treatment over the same bundled typography and semantic colors as the cockpit. */
internal class ContinuumBotDialogStyle(private val activity: Activity) {
    val bodyTypeface: Typeface = font(FontFamily.TEXT, 400)
    private val headingTypeface = font(FontFamily.DISPLAY, 600)

    private fun font(family: FontFamily, weight: Int) = runCatching {
        Typeface.createFromAsset(activity.assets, ContinuumFonts.assetPath(family, weight))
    }.getOrDefault(Typeface.DEFAULT)

    fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()

    fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(16))
        setBackgroundColor(JarvisDesign.Colors.background)
    }

    fun label(value: String, size: Float = 15f, color: Int = JarvisDesign.Colors.text) = TextView(activity).apply {
        text = value
        textSize = size
        typeface = if (size >= 18f) headingTypeface else bodyTypeface
        setTextColor(color)
        setPadding(0, dp(5), 0, dp(5))
        setTextIsSelectable(true)
    }

    fun surface(accent: Int = JarvisDesign.Colors.rim, primary: Boolean = false) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (primary) intArrayOf(JarvisDesign.Colors.tileFillStart, JarvisDesign.Colors.tileFillEnd)
            else intArrayOf(JarvisDesign.Colors.surfaceLow, JarvisDesign.Colors.background)).apply {
            cornerRadius = dp(JarvisDesign.Metrics.radiusCard.toInt()).toFloat()
            setStroke(dp(1).coerceAtLeast(1), accent)
        }

    fun action(value: String, primary: Boolean = false, click: () -> Unit) = Button(activity).apply {
        text = value
        textSize = 14f
        typeface = bodyTypeface
        isAllCaps = false
        setSingleLine(false)
        ellipsize = null
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setTextColor(if (primary) JarvisDesign.Colors.activeWhite else JarvisDesign.Colors.cyanSoft)
        background = surface(if (primary) JarvisDesign.Colors.cyanDeep else JarvisDesign.Colors.rim, primary)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        setOnClickListener { click() }
    }

    fun editor(multiline: Boolean = false) = EditText(activity).apply {
        textSize = 16f
        typeface = bodyTypeface
        setTextColor(JarvisDesign.Colors.text)
        setHintTextColor(JarvisDesign.Colors.textMuted)
        minHeight = dp(52)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = surface(JarvisDesign.Colors.cyanDeep)
        setSingleLine(!multiline)
        gravity = Gravity.TOP or Gravity.START
        if (multiline) {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 9
        }
    }

    fun accent(profile: ChiefBotProfile): Int = when (ChiefBotPersonas.describe(profile).accent) {
        ChiefBotAccent.CYAN -> JarvisDesign.Colors.cyan
        ChiefBotAccent.VIOLET -> JarvisDesign.Colors.pillRimEnd
        ChiefBotAccent.AMBER -> JarvisDesign.Colors.warn
        ChiefBotAccent.TEAL -> JarvisDesign.Colors.okSoft
    }

    fun identity(profile: ChiefBotProfile) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        val initial = String(Character.toChars(profile.displayName.codePointAt(0))).uppercase(java.util.Locale.ROOT)
        val badge = label(initial, 18f, accent(profile)).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            background = surface(accent(profile), primary = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setTextIsSelectable(false)
        }
        addView(badge, LinearLayout.LayoutParams(dp(48), dp(48)).apply { topMargin = dp(5); rightMargin = dp(12) })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(profile.displayName, 21f))
            addView(label(profile.role, 13f, JarvisDesign.Colors.textSecondary))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    fun fitWindow(dialog: AlertDialog) {
        val metrics = activity.resources.displayMetrics
        val configuration = activity.resources.configuration
        val availableWidth = minOf(metrics.widthPixels,
            (configuration.screenWidthDp * metrics.density).toInt().takeIf { it > 0 } ?: metrics.widthPixels)
        val availableHeight = minOf(metrics.heightPixels,
            (configuration.screenHeightDp * metrics.density).toInt().takeIf { it > 0 } ?: metrics.heightPixels)
        dialog.window?.apply {
            setBackgroundDrawable(surface(JarvisDesign.Colors.violetRim))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout((availableWidth - dp(20)).coerceAtLeast(1).coerceAtMost(dp(760)),
                (availableHeight * .94f).toInt().coerceAtLeast(1))
        }
    }
}
