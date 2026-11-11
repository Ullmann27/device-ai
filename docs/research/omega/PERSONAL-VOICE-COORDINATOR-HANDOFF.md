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

34 unique JVM privacy/review cases PASS (see evidence/privacy-unit.json and .txt), including two final stale-consent regressions. Some bridge unit checks use a context port test double; they are NOT reported as actual context understanding. Independent evaluator separately compiles original context sources and tests the adapter. Android factory and isolated review dialog compile against the real Android 15 API jar. The final compilation hashes match the integrated source; three independent consent-interleaving checks pass. UI rendering/Keystore on a device remain NOT_RUN. No Activity integration, production capture or Fold7 proof, model adoption, signing or release.

## Existing learning integration contract

ASR continues to feed the editable composer. Before `inspectRequest` can invalidate a correction scope, the adapter atomically obtains `sessionEvidence`; null means incomplete, requiring clarification. Never treat model replies/web research as owner input. Only an explicit review action can create `OwnerVoiceReview` or `VoiceTrainingApproval`.

Existing `SignedOwnerCorrectionJournal` authenticates owner facts and must not store voice-training text. Existing `LearningExperienceType.USER_FEEDBACK` with `OBSERVED` status may hold a non-sensitive proposal reference after review; it must not become `USER_FACT`, permissions, or a signed factual assertion. Private text remains in the encrypted staging area. The canonical public JSON schema maps recognized/corrected text, confirmed meaning, context, prior task references, desired outcome, forbidden interpretations, provenance and consent. Local binary encoding is a storage transport, not another public research dataset.

The isolated `ui/AndroidPersonalVoiceReviewDialog.kt` now provides learning opt-in, separate transcript/meaning editing, inspection, replacement and deletion. It is API-compiled, not installed or wired into AgentActivity. A later reviewed Activity integration and explicit batch approval remain required. Audio recording is not implemented in this pilot. Text training consent never authorizes audio. Current staging excludes all third-party examples. Revoking consent immediately blocks batch retrieval; deletion does not remove information from a model already trained on it, so affected candidates must be excluded or retrained from the preserved original checkpoint. No claim of neural unlearning.

## Rollback and next entry

Keep all research branches open. Exclude this experimental directory to roll back; no production file or Gemma checkpoint was changed. Start with RESEARCH-PLAN.md, frozen evaluation protocol, runregistration and independent results. Do not wholesale-merge this older research base into the APK branch. Compare source hashes and transfer only independently reviewed components.

Git CLI reads worked; pushes lacked credentials. Authorized GitHub connector published identical trees. Remote commit IDs are canonical; local IDs can differ because connector commits carry server metadata. Never force-push.

## Independent privacy findings repaired

Bot1 reproduced three defects in the initial isolated store: per-instance consent allowed another instance to retain training access, deletion left old approvals reusable, and full capacity prevented replacement. The store now serializes access per directory with a JVM monitor and OS file lock, authenticates durable consent plus a monotonic review revision, binds approvals to store/content/revision, invalidates prior approvals on revocation/deletion/replacement, and allows one recovery slot during replacement without silent data loss. `inspectForReview` atomically returns content plus revision; the UI must supply that captured revision to `approveForTraining`. A queued stale form cannot mint a fresh approval. New privacy tests and four independent before/after checks pass. Before evidence remains under data/store-review and evidence/store-red-team-before.txt.

The 64-case LM1 blind evaluation rejected both candidates. Context results are a separate narrow host-component comparison, not whole current Code53/Gemma/ASR performance. Current speech source was separately audited at PR747 SHA23146e7f and Code53 hashes; see data/CURRENT-SPEECH-SOURCE-AUDIT.md.

Bot3 subsequently reproduced two stale UI consent races. The final store exposes atomic consentForReview plus setTextTrainingConsent(enabled, expectedReviewRevision), and the UI passes the revision captured for the displayed policy. This rejects delayed enables after a newer revocation and never restores stale audio consent. Three independent adversarial call-order checks against the real encrypted store pass; the UI itself has not been executed on Android. See evaluation/UI-CONSENT-RACE.md and ui/consent-checks-receipt.json. Closed-dialog queued jobs are discarded before they start; this does not substitute for the required Activity lifecycle/device test.

## Final disposition

See experimental/personal-voice-20261111/RESULT.md for the German report and INTEGRATION-MANIFEST.json for artifact bindings. Both candidates are rejected: 17/64 exact versus 16/64 to 17/64 restricted-class decisions, 1/32 full contrast pairs, 0/17 same-wordbag pairs, and retention regression from 32/64 to 18/64 or 25/64. Context automatic cases improve from 4/10 to 8/10, separately from three human-confirmed repairs; two correction variants remain unresolved.

The native component evaluation finished after training, a deviation from the intended pre-training end-to-end baseline. Its results cannot stand in for the NOT_RUN full current-app A/B/C comparison. The frozen language test was not changed; privacy repairs were verified separately. Future hypothesis development must use a new unseen confirmation set after these published tests have been consumed.

Agent remote delivery heads: data 3a85bd3fea03e26d348386c61bdac8e7fe6bb8d5 (PR756), training 756b361a9929ba7d3d99ad0caa132eeda39d55bc (PR754), evaluation d1bfb901a1e76b80e754336e1b67687bb62aee7d (PR755). All are integrated into the separate coordinator research branch; no main or APK branch is a publication target.
