package at.heinz.deviceai.voice

import java.util.Locale

/**
 * Voice profiles of Continuum: which engine speaks, with which voice, and with which prosody.
 *
 * Pure and Android-free. The values below are reasoned from the official documentation (see
 * docs/research/STIMME-PROSODIE-20261119.md); nobody involved in writing them has heard them. How any preset sounds
 * is NOT_RUN until the owner listens on the device.
 *
 * ElevenLabs voice_settings (POST /v1/text-to-speech/{voice_id}, elevenlabs.io/docs/api-reference/text-to-speech):
 *  - stability 0..1, default 0.5: "Lower values introduce broader emotional range"; higher values steadier, "can
 *    sound monotonous". The product guide: raise it "for a more serious, steadier delivery".
 *  - similarity_boost 0..1, default 0.75; style default 0 ("we recommend keeping this setting at 0 at all times");
 *    use_speaker_boost default true; speed default 1.0, minimum 0.7, maximum 1.2.
 * Android TextToSpeech: setSpeechRate 1.0 = normal ("0.5 is half the normal speech rate"); setPitch 1.0 = normal.
 */
enum class VoiceEngine { ELEVENLABS, DEVICE }

/** Concrete numbers one preset gives to both engines. */
data class Prosody(
    val stability: Double,
    val similarityBoost: Double,
    val style: Double,
    val useSpeakerBoost: Boolean,
    /** ElevenLabs speed; null means the field is not sent at all (exactly the request of the former voice). */
    val speed: Double?,
    /** Android TextToSpeech.setSpeechRate before the conversation-register factor. */
    val deviceRate: Float,
    /** Android TextToSpeech.setPitch. */
    val devicePitch: Float,
    /** Scales the pause between spoken pieces of one reply (percent of HumanSpeechTiming's value). */
    val pausePercent: Int
)

enum class ProsodyPreset(val label: String, val summary: String, val prosody: Prosody) {
    /** Exactly the request body and device rate/pitch the app used before profiles existed. */
    BISHERIG("Bisherige Stimme", "Genau die bisherigen Einstellungen.",
        Prosody(0.5, 0.75, 0.0, true, null, 0.96f, 1.0f, 100)),
    /**
     * Calm, controlled, low variability, slightly slower. Stability 0.70: the guide's "more serious, steadier"
     * direction, kept clearly below 1.0 because the API reference warns high values "can sound monotonous".
     * similarity 0.80: stays close to the chosen (ideally deep) voice. style 0 per documentation. speed 0.94: about
     * 6 % slower, inside 0.7..1.2. Longer pauses (115 %) still stay inside HumanSpeechTiming's 200–950 ms band.
     */
    SOUVERAEN("Souverän", "Ruhig, kontrolliert, etwas langsamer, klare Pausen.",
        Prosody(0.70, 0.80, 0.0, true, 0.94, 0.90f, 1.0f, 115)),
    /** Neutral delivery: a little steadier than the default, normal speed. */
    SACHLICH("Sachlich", "Neutral und gleichmäßig, normales Tempo.",
        Prosody(0.60, 0.75, 0.0, true, 1.0, 0.98f, 1.0f, 100)),
    /** More variation (lower stability), slightly slower; style stays 0 as documented. */
    WARM("Warm", "Etwas lebendiger und weicher, leicht langsamer.",
        Prosody(0.40, 0.75, 0.0, true, 0.97, 0.94f, 1.0f, 108));

    companion object {
        fun fromName(value: String?): ProsodyPreset = entries.firstOrNull { it.name == value } ?: BISHERIG
    }
}

/**
 * What the owner selected. [elevenVoiceId] null = the former ElevenLabs voice (Maya). Pitch is never shifted by a
 * preset: Android resamples for setPitch and the project found earlier that this makes a voice sound processed
 * (JarvisVoiceProfile). Depth therefore comes from choosing a deeper voice, not from post-processing.
 */
data class VoiceProfileSelection(
    val engine: VoiceEngine = VoiceEngine.DEVICE,
    val elevenVoiceId: String? = null,
    val elevenVoiceName: String? = null,
    val preset: ProsodyPreset = ProsodyPreset.BISHERIG,
    /** −3..+3: „Sprich langsamer/schneller“. */
    val rateStep: Int = 0,
    /** 0..2: „Sprich ruhiger“. */
    val calmStep: Int = 0
) {
    val isFormerVoice: Boolean get() = preset == ProsodyPreset.BISHERIG && elevenVoiceId == null &&
        rateStep == 0 && calmStep == 0
}

/** One ElevenLabs request as Continuum will send it: voice + model + settings. */
data class ElevenLabsSpec(val voiceId: String, val modelId: String, val prosody: Prosody) {
    init { require(VoiceProfiles.validVoiceId(voiceId)) }
}

object VoiceProfiles {
    /** The former ElevenLabs voice (Maya – Warm Californian), unchanged. */
    const val FORMER_VOICE_ID = "ii0s2u4R3UFnxKL6DOrz"
    const val FORMER_VOICE_NAME = "Maya – Warm Californian"
    /**
     * Kept for every preset. The models page calls Multilingual v2 the most stable on long-form, and the best
     * practices page names it as the model that reads numbers correctly (Flash v2.5 does not). Flash v2.5 would be
     * cheaper (API pricing: $0.04 vs $0.08 per 1,000 characters) and faster; switching is the owner's decision.
     */
    const val MODEL_ID = "eleven_multilingual_v2"

    const val MIN_RATE_STEP = -3
    const val MAX_RATE_STEP = 3
    const val MAX_CALM_STEP = 2
    private const val SPEED_PER_STEP = 0.04
    private const val DEVICE_RATE_PER_STEP = 0.05f
    private const val STABILITY_PER_CALM = 0.08
    private const val SPEED_PER_CALM = 0.02
    private const val PAUSE_PER_CALM = 6

    private val voiceId = Regex("[A-Za-z0-9]{8,40}")
    fun validVoiceId(id: String?): Boolean = id != null && voiceId.matches(id)

    /** Effective numbers after the spoken steps; always inside the documented ranges. */
    fun prosody(selection: VoiceProfileSelection): Prosody {
        val base = selection.preset.prosody
        val rate = selection.rateStep.coerceIn(MIN_RATE_STEP, MAX_RATE_STEP)
        val calm = selection.calmStep.coerceIn(0, MAX_CALM_STEP)
        if (rate == 0 && calm == 0) return base
        val speed = ((base.speed ?: 1.0) + rate * SPEED_PER_STEP - calm * SPEED_PER_CALM).coerceIn(0.7, 1.2)
        val deviceRate = (base.deviceRate * (1f + rate * DEVICE_RATE_PER_STEP) * (1f - calm * 0.02f))
            .coerceIn(0.6f, 1.3f)
        return base.copy(
            stability = (base.stability + calm * STABILITY_PER_CALM).coerceIn(0.0, 0.9),
            speed = round2(speed), deviceRate = round2f(deviceRate),
            pausePercent = (base.pausePercent + calm * PAUSE_PER_CALM).coerceIn(80, 140))
    }

    fun elevenLabs(selection: VoiceProfileSelection): ElevenLabsSpec = ElevenLabsSpec(
        selection.elevenVoiceId?.takeIf(::validVoiceId) ?: FORMER_VOICE_ID, MODEL_ID, prosody(selection))

    /** Request body of ElevenLabs text-to-speech; BISHERIG without steps is byte-identical to the former body. */
    fun requestBody(text: String, spec: ElevenLabsSpec): org.json.JSONObject {
        val p = spec.prosody
        val settings = org.json.JSONObject().put("stability", p.stability).put("similarity_boost", p.similarityBoost)
            .put("style", p.style).put("use_speaker_boost", p.useSpeakerBoost)
        p.speed?.let { settings.put("speed", it) }
        return org.json.JSONObject().put("text", text).put("model_id", spec.modelId).put("voice_settings", settings)
    }

    fun voiceName(selection: VoiceProfileSelection): String =
        if (selection.elevenVoiceId == null) FORMER_VOICE_NAME else selection.elevenVoiceName ?: selection.elevenVoiceId

    /** One line for the settings list. */
    fun describe(selection: VoiceProfileSelection): String {
        val engine = if (selection.engine == VoiceEngine.ELEVENLABS) voiceName(selection) + " · ElevenLabs"
            else "Android-Offline-Stimme"
        val steps = buildList {
            if (selection.rateStep < 0) add("langsamer ×${-selection.rateStep}")
            if (selection.rateStep > 0) add("schneller ×${selection.rateStep}")
            if (selection.calmStep > 0) add("ruhiger ×${selection.calmStep}")
        }
        return "$engine · ${selection.preset.label}" + if (steps.isEmpty()) "" else " (" + steps.joinToString(", ") + ")"
    }

    enum class Step { SLOWER, FASTER, CALMER, NORMAL }

    data class StepResult(val selection: VoiceProfileSelection, val sentence: String, val changed: Boolean)

    /** A spoken step, bounded; [StepResult.sentence] says in one sentence what changed (or why nothing did). */
    fun step(selection: VoiceProfileSelection, step: Step): StepResult {
        val next = when (step) {
            Step.SLOWER -> selection.copy(rateStep = (selection.rateStep - 1).coerceAtLeast(MIN_RATE_STEP))
            Step.FASTER -> selection.copy(rateStep = (selection.rateStep + 1).coerceAtMost(MAX_RATE_STEP))
            Step.CALMER -> selection.copy(calmStep = (selection.calmStep + 1).coerceAtMost(MAX_CALM_STEP))
            Step.NORMAL -> selection.copy(rateStep = 0, calmStep = 0)
        }
        if (next == selection) return StepResult(selection, when (step) {
            Step.SLOWER -> "Langsamer geht es nicht; ich bleibe beim langsamsten Tempo."
            Step.FASTER -> "Schneller geht es nicht; ich bleibe beim schnellsten Tempo."
            Step.CALMER -> "Ruhiger geht es mit dieser Einstellung nicht mehr."
            Step.NORMAL -> "Ich spreche bereits im normalen Tempo des Profils ${selection.preset.label}."
        }, false)
        val before = prosody(selection)
        val after = prosody(next)
        val engineDetail = if (next.engine == VoiceEngine.ELEVENLABS)
            "Tempo ${pct(before.speed ?: 1.0)} → ${pct(after.speed ?: 1.0)}"
            else "Tempo ${pct(before.deviceRate.toDouble())} → ${pct(after.deviceRate.toDouble())}"
        val sentence = when (step) {
            Step.SLOWER -> "Ich spreche jetzt etwas langsamer ($engineDetail)."
            Step.FASTER -> "Ich spreche jetzt etwas schneller ($engineDetail)."
            Step.CALMER -> "Ich spreche jetzt ruhiger: gleichmäßiger, etwas langsamer und mit längeren Pausen ($engineDetail)."
            Step.NORMAL -> "Ich spreche wieder im normalen Tempo des Profils ${next.preset.label}."
        }
        return StepResult(next, sentence, true)
    }

    /**
     * The ONE fixed sentence of „Mit Continuum-Satz testen“: calm content, a clock time and a clause, so the owner
     * hears pauses and number reading. Shaped exactly as replies are; [testCharacters] is what ElevenLabs bills.
     */
    const val TEST_SENTENCE = "Guten Tag. Ich habe die Lage geprüft: Alles ist ruhig, der nächste Termin ist um 14:30 Uhr."
    val testSpoken: String get() = SpeechTextShaper.shape(TEST_SENTENCE)
    val testCharacters: Int get() = testSpoken.length

    /** „Nimm wieder die bisherige Stimme“: former voice and former settings; the engine stays as chosen. */
    fun former(selection: VoiceProfileSelection): VoiceProfileSelection = VoiceProfileSelection(engine = selection.engine)

    private fun pct(value: Double) = String.format(Locale.GERMAN, "%d %%", Math.round(value * 100))
    private fun round2(value: Double) = Math.round(value * 100) / 100.0
    private fun round2f(value: Float) = Math.round(value * 100) / 100f
}
