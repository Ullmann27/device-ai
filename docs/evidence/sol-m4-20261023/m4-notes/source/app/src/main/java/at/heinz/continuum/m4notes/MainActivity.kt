package at.heinz.continuum.m4notes

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Deterministic offline M4 app: two screens and private local persistence; no network. */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("continuum_m4_notes", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private var draft: String? = null
    private var editor: EditText? = null

    private fun captureDraft() { editor?.let { draft = it.text.toString() } }

    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft()
        draft?.let { outState.putString("continuum_m4_draft", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = savedInstanceState?.getString("continuum_m4_draft")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val notes = Button(this).apply { text = "Notizen"; setOnClickListener { captureDraft(); showNotes() } }
        val about = Button(this).apply { text = "Über"; setOnClickListener { captureDraft(); showAbout() } }
        navigation.addView(notes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navigation.addView(about, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(navigation)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        showNotes()
    }

    private fun showNotes() {
        body.removeAllViews()
        val heading = TextView(this).apply { text = "Meine lokalen Notizen"; textSize = 23f }
        val editor = EditText(this).apply {
            setText(draft ?: prefs.getString("note", "") ?: "")
            hint = "Hier schreiben"
            minLines = 5
            maxLines = 12
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        this.editor = editor
        val result = TextView(this).apply { text = "Noch keine Änderungen gespeichert." }
        val save = Button(this).apply {
            text = "Lokal speichern"
            setOnClickListener {
                val normalized = NotePolicy.normalized(editor.text.toString())
                val persisted = prefs.edit().putString("note", normalized).commit()
                if (persisted) draft = normalized
                result.text = if (persisted) "Gespeichert (${normalized.length} Zeichen)." else "Speichern fehlgeschlagen."
            }
        }
        body.addView(heading)
        body.addView(editor)
        body.addView(save)
        body.addView(result)
    }

    private fun showAbout() {
        editor = null
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = "Continuum M4: Eigenständig erzeugte Android-Notiz-App. Zwei Ansichten, lokaler Speicher, keine Netzberechtigung."
            textSize = 18f
        })
    }
}
