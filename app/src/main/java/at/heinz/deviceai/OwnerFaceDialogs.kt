package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.ui.JarvisDesign

internal class OwnerFaceDialogs(private val activity: Activity, private val actions: Actions) {
    interface Actions {
        fun status(): OwnerFaceStatus
        fun cameraActive(): Boolean
        fun startEnrollment(name: String): Boolean
        fun startRecognition(): Boolean
        fun canContinueConversation(): Boolean
        fun continueConversation(): Boolean
        fun stop()
        fun deleteProfile()
    }

    private var dialog: AlertDialog? = null
    private var confirmation: AlertDialog? = null
    private var stateText: TextView? = null
    private var profileText: TextView? = null
    private var enrollButton: Button? = null
    private var compareButton: Button? = null
    private var deleteButton: Button? = null
    private var continueButton: Button? = null
    private var continueOnDismiss = false
    val isShowing: Boolean get() = dialog?.isShowing == true

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    private fun row(top: Int = 8) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun text(value: String, size: Float = 14f) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(JarvisDesign.Colors.text)
    }

    private fun button(label: String, key: String, action: () -> Unit) = Button(activity).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        minHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setTextColor(JarvisDesign.Colors.text)
        tag = key
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFF092B46.toInt(), 0xFF171B3D.toInt())).apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), JarvisDesign.Colors.cyan)
        }
        setOnClickListener { if (isShowing && !activity.isFinishing && !activity.isDestroyed) action() }
    }

    fun show() {
        if (activity.isFinishing || activity.isDestroyed || isShowing) return
        continueOnDismiss = false
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(JarvisDesign.Colors.background)
        }
        body.addView(text("Ich kann dein Gesicht mit einer von dir eingelernten Vorlage vergleichen. " +
            "Die Vorlage bleibt verschlüsselt auf diesem Gerät. Es werden keine Fotos gespeichert oder übertragen. " +
            "Der Vergleich entsperrt keine Funktionen und gibt keine Aktionen frei."), row(0))
        profileText = text("", 15f).also { it.tag = "owner-face-profile"; body.addView(it, row(14)) }
        stateText = text("").also {
            it.tag = "owner-face-status"
            it.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            body.addView(it, row())
        }
        enrollButton = button("Gesicht einlernen", "owner-face-enroll", ::confirmEnrollment)
            .also { body.addView(it, row(14)) }
        compareButton = button("Mit meiner Vorlage vergleichen", "owner-face-compare", ::confirmRecognition)
            .also { body.addView(it, row()) }
        deleteButton = button("Gesichtsvorlage löschen", "owner-face-delete", ::confirmDeletion)
            .also { body.addView(it, row()) }
        continueButton = button("Zum Gespräch", "owner-face-continue") {
            if (actions.continueConversation()) {
                continueOnDismiss = true
                dialog?.dismiss()
            }
        }.also { body.addView(it, row(14)) }
        body.addView(text("Zum Gespräch lässt den freigegebenen Vergleich weiterlaufen. " +
            "Schließen oder STOPPEN beendet die Kamera. Nach dem Verlassen der App " +
            "startet sie erst nach deiner erneuten Freigabe.", 12f), row(14))
        val window = AlertDialog.Builder(activity).setTitle("Continuum · Wiedererkennen")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Schließen", null)
            .setNegativeButton("STOPPEN", null).create()
        dialog = window
        window.setOnDismissListener {
            if (dialog === window) {
                dialog = null
                confirmation?.dismiss()
                confirmation = null
                stateText = null
                profileText = null
                enrollButton = null
                compareButton = null
                deleteButton = null
                continueButton = null
                val continued = continueOnDismiss
                continueOnDismiss = false
                if (!continued) actions.stop()
            }
        }
        window.show()
        window.window?.setLayout(minOf(dp(440), activity.resources.displayMetrics.widthPixels),
            ViewGroup.LayoutParams.WRAP_CONTENT)
        window.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            actions.stop()
            update(actions.status())
        }
        update(actions.status())
    }

    fun update(state: OwnerFaceStatus) {
        if (!isShowing) return
        val profile = when {
            state.hasProfile -> "Lokales Profil: ${state.displayName ?: "—"}"
            state.phase == OwnerFacePhase.ERROR -> "Das lokale Profil konnte nicht bestätigt werden."
            else -> "Noch kein Gesicht eingelernt."
        }
        if (profileText?.text?.toString() != profile) profileText?.text = profile
        val progress = if (state.phase == OwnerFacePhase.ENROLLING && state.requiredSamples > 0)
            "\nAufnahmen: ${state.acceptedSamples} / ${state.requiredSamples}" else ""
        val message = state.message + progress
        if (stateText?.text?.toString() != message) stateText?.text = message
        enrollButton?.text = if (state.hasProfile) "Gesicht erneut einlernen" else "Gesicht einlernen"
        val busy = state.sessionActive || state.phase == OwnerFacePhase.LOADING
        enrollButton?.isEnabled = !busy
        compareButton?.isEnabled = state.hasProfile && !busy
        deleteButton?.isEnabled = (state.hasProfile || state.phase == OwnerFacePhase.ERROR) &&
            state.phase != OwnerFacePhase.LOADING
        continueButton?.isEnabled = actions.canContinueConversation()
        listOf(enrollButton, compareButton, deleteButton, continueButton).filterNotNull()
            .forEach { it.alpha = if (it.isEnabled) 1f else .45f }
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = state.sessionActive || actions.cameraActive()
    }

    fun dismiss() {
        continueOnDismiss = false
        confirmation?.dismiss()
        confirmation = null
        dialog?.dismiss()
    }

    private fun confirmEnrollment() {
        val state = actions.status()
        if (confirmation?.isShowing == true || state.sessionActive || state.phase == OwnerFacePhase.LOADING) return
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        body.addView(text("Welcher Name gehört zu deiner Gesichtsvorlage? " +
            "Mit deiner Bestätigung verwendet Continuum die Frontkamera zum Einlernen und " +
            "speichert deinen Namen und eine verschlüsselte Gesichtsvorlage lokal. " +
            "Eine vorhandene Vorlage wird durch das neue Einlernen ersetzt."), row(0))
        val name = EditText(activity).apply {
            tag = "owner-face-name"
            hint = "Dein Name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(64))
            setSingleLine(true)
            setText(actions.status().displayName.orEmpty())
            setTextColor(JarvisDesign.Colors.text)
            setHintTextColor(JarvisDesign.Colors.textMuted)
        }
        body.addView(name, row(12))
        val window = AlertDialog.Builder(activity).setTitle("Mein Gesicht einlernen")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Einwilligen & einlernen", null)
            .setNegativeButton("Abbrechen", null).create()
        showConfirmation(window)
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (confirmation !== window || !window.isShowing || !isShowing) return@setOnClickListener
            val confirmedName = name.text.toString().trim()
            if (confirmedName.isEmpty()) {
                name.error = "Bitte bestätige deinen Namen."
                return@setOnClickListener
            }
            window.dismiss()
            actions.startEnrollment(confirmedName)
            update(actions.status())
        }
    }

    private fun confirmRecognition() {
        val state = actions.status()
        if (!state.hasProfile || state.sessionActive || state.phase == OwnerFacePhase.LOADING ||
            confirmation?.isShowing == true) return
        val window = AlertDialog.Builder(activity).setTitle("Gesicht lokal vergleichen?")
            .setMessage("Die Frontkamera vergleicht dein Gesicht mit der Vorlage für „${state.displayName ?: "—"}“. " +
                "Es werden keine Fotos gespeichert oder übertragen. STOPPEN oder Schließen beendet die Kamera.")
            .setPositiveButton("Mit Kamera vergleichen", null)
            .setNegativeButton("Abbrechen", null).create()
        showConfirmation(window)
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (confirmation !== window || !window.isShowing || !isShowing) return@setOnClickListener
            window.dismiss()
            actions.startRecognition()
            update(actions.status())
        }
    }

    private fun confirmDeletion() {
        val state = actions.status()
        if ((!state.hasProfile && state.phase != OwnerFacePhase.ERROR) || state.phase == OwnerFacePhase.LOADING ||
            confirmation?.isShowing == true) return
        actions.stop()
        val window = AlertDialog.Builder(activity).setTitle("Gesichtsvorlage löschen?")
            .setMessage("Die lokale Gesichtsvorlage und ihr gespeicherter Name werden gelöscht. " +
                "Für eine spätere Wiedererkennung musst du dich neu einlernen.")
            .setPositiveButton("Vorlage löschen", null)
            .setNegativeButton("Behalten", null).create()
        showConfirmation(window)
        window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (confirmation !== window || !window.isShowing || !isShowing) return@setOnClickListener
            window.dismiss()
            actions.deleteProfile()
            update(actions.status())
        }
    }

    private fun showConfirmation(window: AlertDialog) {
        confirmation = window
        window.setOnDismissListener { if (confirmation === window) confirmation = null }
        window.show()
        window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(JarvisDesign.Colors.background))
    }
}
