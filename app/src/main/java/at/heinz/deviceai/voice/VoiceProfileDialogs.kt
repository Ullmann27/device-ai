package at.heinz.deviceai.voice

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

/**
 * Settings UI for voice profiles. Everything that may cost ElevenLabs credits runs only on an explicit tap and says
 * the character count first. Listing voices and playing a hosted sample synthesise no text.
 */
internal class VoiceProfileDialogs(private val host: Host) {
    interface Host {
        val context: Context
        fun selection(): VoiceProfileSelection
        /** Saves and applies [selection] (stops a running reply first). */
        fun apply(selection: VoiceProfileSelection)
        fun elevenLabsKey(): String?
        fun internetAlwaysOn(): Boolean
        /** Speaks [VoiceProfiles.TEST_SENTENCE] once with [selection]; false when no voice could start. */
        fun speakTest(selection: VoiceProfileSelection): Boolean
        /** Runs [work] off the main thread and [done] on it. */
        fun <T> io(work: () -> T, done: (Result<T>) -> Unit)
        fun openElevenLabsKeySettings()
    }

    private val preview = VoicePreviewPlayer()
    private var voices: List<ElevenLabsVoiceCatalog.Voice>? = null

    fun stopPreview() = preview.stop()

    fun showMenu() {
        val s = host.selection()
        val cost = if (s.engine == VoiceEngine.ELEVENLABS) "${VoiceProfiles.testCharacters} Zeichen ElevenLabs-Credits"
            else "lokal, kostenlos"
        AlertDialog.Builder(host.context).setTitle("Stimmprofil · " + VoiceProfiles.describe(s))
            .setItems(arrayOf(
                "Sprechweise · " + s.preset.label,
                "ElevenLabs-Stimme wählen · " + VoiceProfiles.voiceName(s),
                "Mit Continuum-Satz testen · $cost",
                "Bisherige Stimme wiederherstellen" + if (s.isFormerVoice) " (aktiv)" else ""
            )) { _, which ->
                when (which) {
                    0 -> showPresets()
                    1 -> showVoiceList()
                    2 -> confirmTest(s)
                    3 -> {
                        host.apply(VoiceProfiles.former(s))
                        toast("Bisherige Stimme und bisherige Einstellungen sind wieder aktiv.")
                    }
                }
            }.setNegativeButton("Schließen", null).show()
    }

    private fun showPresets() {
        val s = host.selection()
        val presets = ProsodyPreset.entries
        AlertDialog.Builder(host.context).setTitle("Sprechweise")
            .setSingleChoiceItems(presets.map { it.label + " – " + it.summary }.toTypedArray(),
                presets.indexOf(s.preset)) { dialog, which ->
                val chosen = presets[which]
                host.apply(s.copy(preset = chosen, rateStep = 0, calmStep = 0))
                dialog.dismiss()
                toast("Sprechweise „${chosen.label}“ gilt ab der nächsten Antwort. Wie sie klingt, hörst nur du.")
            }.setNegativeButton("Schließen", null).show()
    }

    private fun showVoiceList() {
        val key = host.elevenLabsKey()
        if (key == null) {
            toast("Für ElevenLabs-Stimmen zuerst den Schlüssel speichern.")
            host.openElevenLabsKeySettings()
            return
        }
        if (!host.internetAlwaysOn()) {
            toast("Die Stimmenliste braucht den Internetmodus ‚Immer an‘. Er wird hier nicht verändert.")
            return
        }
        voices?.let { showVoices(it); return }
        toast("Ich lade deine ElevenLabs-Stimmen. Dabei wird keine Sprache erzeugt.")
        host.io({ ElevenLabsVoiceCatalog.load(key, ElevenLabsVoiceCatalog.https) }) { result ->
            result.fold(onSuccess = { page ->
                voices = page.voices
                if (page.voices.isEmpty()) toast("ElevenLabs hat keine Stimmen geliefert.") else showVoices(page.voices)
            }, onFailure = { toast("Die Stimmenliste konnte nicht geladen werden. Prüfe Internet und Schlüssel.") })
        }
    }

    private fun showVoices(list: List<ElevenLabsVoiceCatalog.Voice>) {
        val current = host.selection().elevenVoiceId ?: VoiceProfiles.FORMER_VOICE_ID
        AlertDialog.Builder(host.context).setTitle("ElevenLabs-Stimmen (${list.size})")
            .setItems(list.map { v ->
                (if (v.id == current) "● " else "") + v.name + (v.labelLine().takeIf { it.isNotEmpty() }?.let { "\n$it" } ?: "")
            }.toTypedArray()) { _, which -> showVoice(list[which]) }
            .setNegativeButton("Schließen", null).show()
    }

    private fun showVoice(voice: ElevenLabsVoiceCatalog.Voice) {
        val candidate = host.selection().copy(engine = VoiceEngine.ELEVENLABS, elevenVoiceId = voice.id,
            elevenVoiceName = voice.name)
        val items = buildList {
            if (voice.previewUrl != null) add("Probe hören · ElevenLabs-Beispiel" + if (voice.germanPreview) " (Deutsch)" else "")
            add("Mit Continuum-Satz testen · ${VoiceProfiles.testCharacters} Zeichen Credits")
            add("Diese Stimme verwenden")
        }
        val message = listOfNotNull(voice.labelLine().takeIf { it.isNotEmpty() }, voice.description).joinToString("\n")
        val hasPreview = voice.previewUrl != null
        AlertDialog.Builder(host.context).setTitle(voice.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (if (hasPreview) which else which + 1) {
                    0 -> playPreview(voice)
                    1 -> confirmTest(candidate)
                    else -> {
                        preview.stop()
                        host.apply(candidate)
                        toast("${voice.name} spricht ab der nächsten Antwort, Sprechweise „${candidate.preset.label}“.")
                    }
                }
            }.setNegativeButton("Zurück", null).show()
        if (message.isNotEmpty()) toast(message.take(180))
    }

    private fun playPreview(voice: ElevenLabsVoiceCatalog.Voice) {
        val url = voice.previewUrl ?: return
        if (!host.internetAlwaysOn()) {
            toast("Die Probe braucht den Internetmodus ‚Immer an‘.")
            return
        }
        val started = preview.play(url) { completed -> if (!completed) toast("Die Probe konnte nicht abgespielt werden.") }
        toast(if (started) "ElevenLabs-Beispiel mit deren Beispieltext, nicht Continuums Satz. Continuum erzeugt dafür " +
            "keine Sprache und sendet keinen Text; ob ElevenLabs das Abrufen berechnet, steht nicht in seiner Dokumentation."
            else "Die Probe konnte nicht gestartet werden.")
    }

    private fun confirmTest(selection: VoiceProfileSelection) {
        preview.stop()
        if (selection.engine != VoiceEngine.ELEVENLABS) {
            if (!host.speakTest(selection)) toast("Die lokale Stimme ist gerade nicht bereit.")
            return
        }
        val count = VoiceProfiles.testCharacters
        AlertDialog.Builder(host.context).setTitle("Mit Continuum-Satz testen")
            .setMessage("„${VoiceProfiles.testSpoken}“\n\nDas erzeugt einmal Sprache bei ElevenLabs " +
                "(${VoiceProfiles.voiceName(selection)}, ${selection.preset.label}). ElevenLabs rechnet pro Zeichen ab: " +
                "$count Zeichen werden deinem Guthaben berechnet.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Ja, $count Zeichen verwenden") { _, _ ->
                if (!host.speakTest(selection))
                    toast("Der Test konnte nicht starten. Für ElevenLabs braucht es Schlüssel und Internetmodus ‚Immer an‘.")
            }.show()
    }

    private fun toast(text: String) = Toast.makeText(host.context, text, Toast.LENGTH_LONG).show()
}
