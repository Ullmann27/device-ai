package at.heinz.deviceai.research.voice

import java.nio.file.Files
import javax.crypto.KeyGenerator

/** Synthetic unit fixtures; independent_eval separately runs the real context adapter. */
object PersonalVoicePrivacyChecks {
    @JvmStatic fun main(args: Array<String>) {
        var count = 0
        fun test(name: String, body: () -> Unit) { body(); count++; println("PASS $name") }
        fun fails(body: () -> Unit) { check(runCatching(body).isFailure) }
        fun example(text: String = "Nein, ich meinte Testzweig B.", corrected: String? = null,
                    transcript: Boolean = false, meaning: Boolean = true, reviewed: Boolean = true,
                    approved: Boolean = true, owner: Boolean = true, context: List<String> = listOf("Prüfe Testzweig A.")) =
            PersonalVoiceExample("synthetic-session", text, corrected, transcript, "Prüfe Testzweig B.", meaning,
                context, listOf("synthetic-task-1"), "Prüfbericht", listOf("Kein Merge"),
                "synthetic-local-test", owner, reviewed, approved)
        fun grant(e: PersonalVoiceExample) = VoiceTrainingApproval(e.digest, true)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val folder = Files.createTempDirectory("voice-privacy-check-")
        val store = PersonalVoiceLearningStore(folder.toFile(), key)
        val original = example()
        test("default_off_no_file") {
            check(store.stage(original, grant(original)) == VoiceStageResult.CONSENT_REQUIRED)
            check(store.ids().isEmpty())
        }
        test("audio_opt_in_does_not_enable_text_training") {
            store.setConsent(VoiceLearningConsent(audioRetention = true))
            check(store.stage(original, grant(original)) == VoiceStageResult.CONSENT_REQUIRED)
        }
        store.setConsent(VoiceLearningConsent(textTraining = true))
        test("text_opt_in_does_not_enable_audio") { check(!store.consent().audioRetention) }
        test("unreviewed_meaning_rejected") {
            val e = example(meaning = false)
            check(store.stage(e, grant(e)) == VoiceStageResult.REVIEW_REQUIRED)
        }
        test("transcript_edit_needs_separate_confirmation") {
            val e = example(corrected = "Korrigierter Wortlaut")
            check(store.stage(e, grant(e)) == VoiceStageResult.INVALID)
        }
        test("wording_confirmation_does_not_confirm_intent") {
            val e = example(corrected = "Korrigierter Wortlaut", transcript = true, meaning = false)
            check(store.stage(e, grant(e)) == VoiceStageResult.REVIEW_REQUIRED)
        }
        test("sensitive_content_needs_review_and_approval") {
            for (e in listOf(example(reviewed = false), example(approved = false)))
                check(store.stage(e, grant(e)) == VoiceStageResult.REVIEW_REQUIRED)
        }
        test("third_party_examples_excluded") {
            val e = example(owner = false)
            check(store.stage(e, grant(e)) == VoiceStageResult.REVIEW_REQUIRED)
        }
        test("owner_approval_content_bound") {
            check(store.stage(example("Abweichender Inhalt"), grant(original)) == VoiceStageResult.REVIEW_REQUIRED)
            check(store.stage(original, VoiceTrainingApproval(original.digest, false)) == VoiceStageResult.REVIEW_REQUIRED)
        }
        test("context_is_defensively_copied") {
            val supplied = mutableListOf("synthetischer Kontext")
            val e = example(context = supplied); val digest = e.digest
            supplied[0] = "nachträglich geändert"
            check(e.digest == digest && e.ownerContext.single() == "synthetischer Kontext")
        }
        test("encrypted_round_trip_and_deduplication") {
            check(store.stage(original, grant(original)) == VoiceStageResult.STORED)
            check(store.stage(original, grant(original)) == VoiceStageResult.DUPLICATE)
            check(store.inspect(original.digest).recognizedTranscript == original.recognizedTranscript)
            val bytes = folder.resolve(original.digest + ".pv1").toFile().readBytes()
            check(!String(bytes, Charsets.ISO_8859_1).contains("Testzweig"))
            check(store.ids().size == 1)
        }
        test("single_correction_does_not_trigger_batch") { check(store.reviewedBatch().isEmpty()) }
        test("wrong_key_and_changed_id_fail_closed") {
            val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            fails { PersonalVoiceLearningStore(folder.toFile(), otherKey).inspect(original.digest) }
            fails { store.inspect("../outside") }
        }
        test("ciphertext_modification_detected") {
            val path = folder.resolve(original.digest + ".pv1"); val saved = Files.readAllBytes(path)
            val changed = saved.copyOf(); changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
            Files.write(path, changed)
            fails { store.inspect(original.digest) }
            fails { store.stage(original, grant(original)) }
            Files.write(path, saved)
        }
        test("replacement_requires_fresh_approval") {
            val revised = example("Nein, ich meinte Testzweig C.")
            check(store.replace(original.digest, revised, grant(original)) == VoiceStageResult.REVIEW_REQUIRED)
            check(store.inspect(original.digest).digest == original.digest)
            check(store.replace(original.digest, revised, grant(revised)) == VoiceStageResult.STORED)
            check(original.digest !in store.ids() && revised.digest in store.ids())
        }
        test("reviewed_batch_is_opt_in_and_bounded") {
            val e = example("Ein zweites überprüftes synthetisches Beispiel")
            check(store.stage(e, grant(e)) == VoiceStageResult.STORED)
            check(store.reviewedBatch(2).size == 2)
            store.setConsent(VoiceLearningConsent())
            check(store.reviewedBatch(2).isEmpty())
            check(store.inspect(e.digest).digest == e.digest)
        }
        test("explicit_delete_retains_other_records") {
            val ids = store.ids(); check(store.delete(ids.first()))
            check(!store.delete(ids.first()) && store.ids() == ids.drop(1))
        }
        test("default_logging_does_not_contain_private_content") {
            check(!original.toString().contains("Testzweig"))
            check(!grant(original).toString().contains(original.digest))
        }

        // This port is a test double ONLY for review binding and safe dispatch properties.
        val port = object : VoiceContextPort {
            override val mutex = Any()
            var snapshots: List<String>? = listOf("Prüfe Testzweig A.")
            var calls = 0
            var last: String? = null
            override fun ownerEvidence(sessionId: String) = snapshots
            override fun inspect(sessionId: String, request: String): VoiceInspection {
                calls++; last = request
                return VoiceInspection(VoiceResolution.PASS_THROUGH)
            }
        }
        val bridge = PersonalVoiceCorrectionBridge(port)
        val raw = "Erkanntes synthetisches Fragment"
        fun review(text: String? = null, transcript: Boolean = false, intended: String? = null,
                   meaning: Boolean = false, session: String = "s") =
            OwnerVoiceReview(VoiceDigests.text(raw), VoiceDigests.context(session, port.snapshots.orEmpty()),
                text, transcript, intended, meaning)
        test("review_bound_to_session_context_and_input") {
            val old = review("Bestätigter Wortlaut", true)
            check(bridge.process("other-session", raw, old).resolution == VoiceResolution.REJECTED)
            check(bridge.process("s", raw + "anders", old).resolution == VoiceResolution.REJECTED)
            port.snapshots = listOf("Inzwischen geänderter Kontext")
            check(bridge.process("s", raw, old).resolution == VoiceResolution.REJECTED)
            check(port.calls == 0)
        }
        test("unconfirmed_repair_never_reaches_context") {
            check(bridge.process("s", raw, review("Vermutung", false)).resolution == VoiceResolution.REJECTED)
            check(port.calls == 0)
        }
        test("transcript_and_meaning_repairs_separately_reported") {
            val r = bridge.process("s", raw, review("Bestätigter Wortlaut", true))
            check(r.transcriptCorrected && !r.meaningCorrected && r.effectiveRequest == "Bestätigter Wortlaut")
            val m = bridge.process("s", raw, review(intended = "Bestätigte Bedeutung", meaning = true))
            check(!m.transcriptCorrected && m.meaningCorrected)
        }
        test("model_suggestion_cannot_grant_tool_or_training") {
            for (label in 0..3) {
                val r = bridge.process("s", "Nicht öffnen, nur besprechen.", modelIntent = label)
                check(!r.grantsToolExecution && !r.startsTraining)
                check(r.effectiveRequest == "Nicht öffnen, nur besprechen.")
            }
        }
        test("incomplete_context_requires_clarification") {
            port.snapshots = null; val calls = port.calls
            check(bridge.process("s", raw).resolution == VoiceResolution.CLARIFY)
            check(port.calls == calls)
            port.snapshots = emptyList()
        }
        test("expanded_correction_preserves_negation") {
            bridge.process("s", "Nein, so habe ich das nicht gemeint. Ich meinte nicht Testzweig A.")
            check(port.last == "Nein, ich meinte nicht Testzweig A.")
        }
        test("long_correction_is_not_truncated") {
            val text = "Nein, ich meinte " + "synthetisch ".repeat(60) + "nicht öffnen"
            val calls = port.calls; val r = bridge.process("s", text)
            check(r.resolution == VoiceResolution.CLARIFY && r.effectiveRequest == text && port.calls == calls)
        }
        test("input_above_explicit_bound_rejected") {
            check(bridge.process("s", "a".repeat(8193)).resolution == VoiceResolution.REJECTED)
        }
        println("PERSONAL_VOICE_PRIVACY_UNIT_CHECKS $count passed; actual Android/Keystore/Fold7 NOT_RUN")
    }
}
