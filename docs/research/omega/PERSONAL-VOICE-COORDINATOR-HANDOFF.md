# Personal voice coordinator handoff

Research integration: PR #753, branch research/continuum-personal-voice-integration-20261111.
Baseline: PR #728, 1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667 (older #530 research base, not latest APK). EXP-05/12 with independent EXP-07 gates.

## Implemented research components

- Existing native LM1 training is preserved; two prospectively registered candidates only. See training branch PR #754.
- Public synthetic data, separate wording/meaning confirmation and content-bound consent: PR #756.
- Independent frozen evaluation: PR #755.
- `experimental/personal-voice-20261111/native/PersonalVoiceCorrectionBridge.kt` wraps owner-only context snapshot plus current discourse inspection. `LocalConversationVoiceAdapter.kt` calls the actual `LocalConversationContext`; no second conversation memory. Expanded correction control is normalized without removing negations. Unknown referents still ask.
- Human wording and intent corrections are distinct and tied to the full observed input plus current session/context digest. A stale review is rejected. An LM1 label is advisory; it cannot grant tool permission or start training.
- `PersonalVoiceLearningStore.kt`: opt-in encrypted review staging, content-bound approval, distinct consent flags, no audio writer or cloud export, deduplication, review/replace/delete and opt-in bounded batch retrieval. AES-GCM uses a supplied key; `AndroidPersonalVoiceStore.kt` provides a dedicated Android Keystore alias and app no-backup directory.

## Verification

26 unique JVM privacy/review cases PASS (see evidence/privacy-unit.json and .txt). Some bridge unit checks use a context port test double; they are NOT reported as actual context understanding. Independent evaluator separately compiles original context sources and tests the adapter. Android factory has not yet been compiled/tested on a device. No Activity integration, production capture, Android UI, Fold7 proof, model adoption, signing or release.

## Existing learning integration contract

ASR continues to feed the editable composer. Before `inspectRequest` can invalidate a correction scope, the adapter atomically obtains `sessionEvidence`; null means incomplete, requiring clarification. Never treat model replies/web research as owner input. Only an explicit review action can create `OwnerVoiceReview` or `VoiceTrainingApproval`.

Existing `SignedOwnerCorrectionJournal` authenticates owner facts and must not store voice-training text. Existing `LearningExperienceType.USER_FEEDBACK` with `OBSERVED` status may hold a non-sensitive proposal reference after review; it must not become `USER_FACT`, permissions, or a signed factual assertion. Private text remains in the encrypted staging area. The canonical public JSON schema maps recognized/corrected text, confirmed meaning, context, prior task references, desired outcome, forbidden interpretations, provenance and consent. Local binary encoding is a storage transport, not another public research dataset.

A later reviewed Activity integration must provide visible controls for learning opt-in, transcript vs meaning editing, inspecting/replacing/deleting examples, and explicit batch approval. Audio recording is not implemented in this pilot. Text training consent never authorizes audio. Current staging excludes all third-party examples. Revoking consent immediately blocks batch retrieval; deletion does not remove information from a model already trained on it, so affected candidates must be excluded or retrained from the preserved original checkpoint. No claim of neural unlearning.

## Rollback and next entry

Keep all research branches open. Exclude this experimental directory to roll back; no production file or Gemma checkpoint was changed. Start with RESEARCH-PLAN.md, frozen evaluation protocol, runregistration and independent results. Do not wholesale-merge this older research base into the APK branch. Compare source hashes and transfer only independently reviewed components.

Git CLI reads worked; pushes lacked credentials. Authorized GitHub connector published identical trees. Remote commit IDs are canonical; local IDs can differ because connector commits carry server metadata. Never force-push.
