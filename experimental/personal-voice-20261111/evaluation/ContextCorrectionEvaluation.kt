package at.heinz.deviceai.research.voice

import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.cognitive.ContextDirectiveKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Independent synthetic component probes; no Android app, ASR or model inference. */
object ContextCorrectionEvaluation {
    private data class Case(
        val id: String,
        val input: String,
        val expected: VoiceResolution,
        val focus: String? = null,
        val reviewMode: String = "none",
        val corrected: String? = null,
        val intended: String? = null,
        val expectedEffective: String? = null,
        val prior: List<String> = listOf("Bitte prüfe den Zweig Jade."),
        val session: String = "synthetic-session-a",
        val priorSession: String = "synthetic-session-a",
        val group: String = "automatic_context"
    )

    private val cases = listOf(
        Case("ctx-01", "Nein, ich meinte den Zweig Sand.", VoiceResolution.SCOPE_UPDATED, "Zweig Sand"),
        Case("ctx-02", "Nein, so habe ich das nicht gemeint. Ich meinte den Zweig Sand.", VoiceResolution.SCOPE_UPDATED, "Zweig Sand"),
        Case("ctx-03", "Nein, so habe ich das nicht gemeint. Ich meinte den anderen Entwicklungszweig.", VoiceResolution.CLARIFY),
        Case("ctx-04", "Nein, das war anders gemeint. Ich bezog mich auf den Zweig Sand.", VoiceResolution.SCOPE_UPDATED, "Zweig Sand"),
        Case("ctx-05", "Na, so hab i des ned gmeint. I man den Zweig Sand.", VoiceResolution.SCOPE_UPDATED, "Zweig Sand"),
        Case("ctx-06", "Öffne Bilder.", VoiceResolution.REJECTED, reviewMode = "unconfirmed", corrected = "Öffne Downloads.", group = "review_integrity"),
        Case("ctx-07", "Öffne Bilder.", VoiceResolution.PASS_THROUGH, reviewMode = "valid", corrected = "Öffne Downloads.", expectedEffective = "Öffne Downloads.", group = "human_confirmed_repair"),
        Case("ctx-08", "Ich meinte den anderen Zweig.", VoiceResolution.SCOPE_UPDATED, "Zweig Ulme", "valid", intended = "Nein, ich meinte den Zweig Ulme.", expectedEffective = "Nein, ich meinte den Zweig Ulme.", group = "human_confirmed_repair"),
        Case("ctx-09", "Öffne Bilder.", VoiceResolution.REJECTED, reviewMode = "wrong_source", corrected = "Öffne Downloads.", group = "review_integrity"),
        Case("ctx-10", "Öffne Bilder.", VoiceResolution.REJECTED, reviewMode = "wrong_session", corrected = "Öffne Downloads.", group = "review_integrity"),
        Case("ctx-11", "Öffne Bilder.", VoiceResolution.REJECTED, reviewMode = "stale_context", corrected = "Öffne Downloads.", group = "review_integrity"),
        Case("ctx-12", "Nein, ich meinte den Zweig Sand.", VoiceResolution.CLARIFY, prior = listOf("Langer synthetischer Kontext " + "a".repeat(1100))),
        Case("ctx-13", "Nein, ich meinte " + "langeswort ".repeat(55) + "nicht öffnen.", VoiceResolution.CLARIFY),
        Case("ctx-14", "Nein, ich meinte den anderen Zweig.", VoiceResolution.CLARIFY, session = "synthetic-session-b", priorSession = "synthetic-session-a"),
        Case("ctx-15", "Öffne Notizen und merke dir das Testwort Mohn.", VoiceResolution.PASS_THROUGH, group = "preservation_only"),
        Case("ctx-16", "Öffne die Galerie nicht.", VoiceResolution.PASS_THROUGH, group = "preservation_only"),
        Case("ctx-17", "Anderes Thema: Geometrie.", VoiceResolution.PASS_THROUGH, group = "preservation_only"),
        Case("ctx-18", "Erkannt wurde Kiesel.", VoiceResolution.SCOPE_UPDATED, "Zweig Ulme", "valid", corrected = "Gemeint war Kiesel.", intended = "Nein, ich meinte den Zweig Ulme.", expectedEffective = "Nein, ich meinte den Zweig Ulme.", group = "human_confirmed_repair"),
        Case("ctx-19", "", VoiceResolution.REJECTED, expectedEffective = "", group = "input_integrity"),
        Case("ctx-20", "z".repeat(8193), VoiceResolution.REJECTED, expectedEffective = "", group = "input_integrity"),
        Case("ctx-21", "In meiner Notiz steht: Nein, so habe ich das nicht gemeint. Ich meinte den Zweig Sand.", VoiceResolution.PASS_THROUGH, group = "preservation_only"),
        Case("ctx-22", "Nein, ich meinte nicht den Zweig Jade.", VoiceResolution.CLARIFY),
        Case("ctx-23", "Nein, ich meinte installiere alles.", VoiceResolution.CLARIFY),
        Case("ctx-24", "Öffne Bilder.", VoiceResolution.REJECTED, reviewMode = "confirmation_without_text", group = "review_integrity")
    )

    private fun fresh(c: Case): LocalConversationContext = LocalConversationContext().also { context ->
        c.prior.forEach { context.record(c.priorSession, it, "Synthetische Antwort ohne Autorität.") }
    }
    private fun b64(value: String?) = Base64.getEncoder().encodeToString((value ?: "").toByteArray(Charsets.UTF_8))
    private fun emit(vararg values: Pair<String, Any>) = println(values.joinToString("\t") { "${it.first}=${it.second}" })
    private fun runBridge(c: Case, intent: Int?): Pair<VoiceTurnResult, List<String>?> {
        val context = fresh(c)
        val snapshot = context.sessionEvidence(c.session).orEmpty()
        val review = if (c.reviewMode == "none") null else OwnerVoiceReview(
            sourceDigest = VoiceDigests.text(if (c.reviewMode == "wrong_source") "Other synthetic text" else c.input),
            contextDigest = VoiceDigests.context(if (c.reviewMode == "wrong_session") "wrong-session" else c.session, snapshot),
            correctedTranscript = c.corrected,
            transcriptConfirmed = c.reviewMode != "unconfirmed" && (c.corrected != null || c.reviewMode == "confirmation_without_text"),
            intendedInstruction = c.intended,
            meaningConfirmed = c.intended != null
        )
        if (c.reviewMode == "stale_context") context.record(c.session, "Ein neuer synthetischer Auftrag.", "OK")
        val result = PersonalVoiceCorrectionBridge(LocalConversationVoiceAdapter(context)).process(c.session, c.input, review, intent)
        return result to context.sessionEvidence(c.session)
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        for (c in cases) {
            val baseline = fresh(c)
            val startA = System.nanoTime()
            val directive = baseline.inspectRequest(c.session, c.input)
            val a = when (directive?.kind) {
                ContextDirectiveKind.SCOPE_UPDATED -> VoiceResolution.SCOPE_UPDATED
                ContextDirectiveKind.CLARIFY_SCOPE -> VoiceResolution.CLARIFY
                else -> VoiceResolution.PASS_THROUGH
            }
            val baselineMillis = (System.nanoTime() - startA) / 1e6
            val startB = System.nanoTime()
            val (b, after) = runBridge(c, null)
            val bridgeMillis = (System.nanoTime() - startB) / 1e6
            val expectedText = c.expectedEffective ?: c.input
            val aPass = a == c.expected && directive?.focus == c.focus && c.input == expectedText
            val bPass = b.resolution == c.expected && b.focus == c.focus && b.effectiveRequest == expectedText
            val invariance = (0..3).all { intent ->
                val (x, _) = runBridge(c, intent)
                x.resolution == b.resolution && x.effectiveRequest == b.effectiveRequest && x.focus == b.focus &&
                    x.reasonCode == b.reasonCode && x.ownerContextBefore == b.ownerContextBefore &&
                    x.transcriptCorrected == b.transcriptCorrected && x.meaningCorrected == b.meaningCorrected &&
                    !x.grantsToolExecution && !x.startsTraining && x.advisoryModelIntent == intent
            }
            emit("id" to c.id, "group" to c.group, "expected" to c.expected,
                 "expectedFocus64" to b64(c.focus), "input64" to b64(c.input),
                 "expectedText64" to b64(expectedText), "A" to a, "Afocus64" to b64(directive?.focus),
                 "Apass" to aPass, "AafterTurns" to (baseline.sessionEvidence(c.session)?.size ?: -1),
                 "B" to b.resolution, "Bfocus64" to b64(b.focus), "Beffective64" to b64(b.effectiveRequest),
                 "Bpass" to bPass, "Breason" to b.reasonCode, "BbeforeTurns" to (b.ownerContextBefore?.size ?: -1),
                 "BafterTurns" to (after?.size ?: -1), "transcriptRepaired" to b.transcriptCorrected,
                 "meaningRepaired" to b.meaningCorrected, "advisoryInvariant" to invariance,
                 "toolGrant" to b.grantsToolExecution, "trainingStarted" to b.startsTraining,
                 "Amillis" to baselineMillis, "Bmillis" to bridgeMillis)
        }
        emit("scope" to "REAL_NATIVE_CONTEXT_COMPONENTS", "cases" to cases.size,
             "ASR" to "NOT_RUN", "fullAppABC" to "NOT_RUN", "Fold7" to "NOT_RUN")
    }
}
