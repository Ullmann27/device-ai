# Personal Voice Learning — data handoff

Status: Data frozen and validated; source audit and protected-store review completed.
Isolated Android review UI compiles; no owner-speech or device-quality claim.
Research source: PR #728, 1d9eab1275a7fcc92ad4c5e2d1dd52d1db411667.
Agent branch: research/continuum-personal-voice-data-20261111.
Integration: research/continuum-personal-voice-integration-20261111.
Relevant experiments: EXP-04/05/07/12. Current explicit owner research request controls this bounded pilot; historical release-priority notes do not authorize app changes.

## Owned scope

Synthetic text data, provenance/consent contract, public-only export gate, grouped TRAIN/DEV splitting, shortcut audits and data tests. No independent holdout inspection, native training changes, app mutations, audio capture or private conversation export.

Native contract: TSV id / label / prompt, labels 0 no side effect, 1 open, 2 search, 3 remember. Existing wrapper `Auftrag: {prompt}\n`; unchanged tokenizer. Prefix including BOS must fit 252 BPE tokens in context256, verified by native preflight. Question/statement distinction, parameters and multi-action tasks stay explicit in rich records and pipeline probes, not falsely claimed as extra native output classes.

## Initial PR #728 code read

- AndroidOnDeviceSpeechInput: local de-AT free-form Android recognition, first result only, no buffer retention, 30-second bounded session through LocalSpeechSession.
- AgentActivity.startVoiceInput/applyLocalSpeechText: transcript enters owner-editable composer; explicit send gesture remains separate.
- LocalSpeechInputPolicy.mergeDictation: appends/inserts at cursor, does not turn transcription into command authorization.
- LocalConversationContext: ephemeral turns and explicit focus. Simple scope corrections clear old turns; ambiguous references require clarification. Any correction bridge must snapshot context before calling inspectRequest.
- ContinuumLearningExperience: USER_FEEDBACK and INTENT_LABEL already exist; USER_FACT must not receive training proposals.
- SignedOwnerCorrectionJournal: signed owner-fact mutation journal, not a transcription training store.
- SecureTokenStore: existing Android Keystore AES-GCM pattern; personal training requires separate purpose-specific key and explicit consent.

## Evidence limits

No real owner audio/transcript pair is available or used. ASR accuracy and Fold7 runtime remain NOT_RUN. Newly authored Austrian/German examples are synthetic diagnostics, not personal speech recordings. No generated example grants tool rights or facts to memory.

## Initial bounded plan and rollback

The planned public synthetic corpus, schema, validator and shortcut tests are now complete. The budget was 128 training and 32 open-development rows, with family and pair groups separated; 32 new simple replay rows were approved before training. The independent evaluator owns the frozen holdouts. Validated data were published before native training. Rollback is excluding these research files; original model and app stay unchanged.

## Data checkpoint

Public corpus implemented at experimental/personal-voice-20261111/data/v1:
128 four-class TRAIN contrasts, 32 family-separated open DEV contrasts, 32 newly
authored simple open/search replay rows and 12 rich pipeline diagnostics.
All 80 contrast pairs have equal word multisets and opposing labels, bounding
any order-insensitive keyword/word-bag classifier to 50%. Four train surface
families and two development families are intentionally a small diagnostic
scope, not proof of broad speech understanding. Historical retention overlap=0.

17 host Python tests passed, including deliberately invalid consent, stale
context/meaning, unconfirmed word correction, audio/third-party grants, private
publication attempts, split leakage and deterministic generation. The initial
ID-normalization failure and pre-training replay grammar review are recorded in
test-receipt.json. Numeric native token validation belongs to Bot2. No real ASR,
Android, Fold7 or owner-learning outcome is claimed.

Standalone Git push lacked credentials. Initial remote checkpoint was published
through the authorized GitHub connector as d3db049cd13d4d8dc42bc1ba7aeada1250422710,
Draft PR #756; local initial commit6968566 has the same file content, not the
same commit identity. Subsequent data snapshot is published by Git-tree update.

## Current-source correction to the initial baseline map

Read experimental/personal-voice-20261111/data/CURRENT-SPEECH-SOURCE-AUDIT.md
before any later integration. Current PR #747 (23146e7f) already has warm ASR
recovery, opt-in hands-free Send, persistent bounded chat context, topic-return
hints and specialized PDF/draft corrections. Existing synthetic ResearchFeedback
also has explicit review/revoke logic. The initial PR #728 map is a historical
research baseline, not the full current APK behavior. Four core files were
matched by exact Git blob identity against Code53 source. No runtime or ASR
accuracy result is inferred from this source audit. Frozen data files unchanged.

## Protected store review and isolated control screen

Three actual coordinator-store defects were independently reproduced: consent
revocation did not reach another store instance, old approvals survived deletion,
and full-capacity replacement failed. Root repaired the store; four independent
JVM checks now pass, including stale-form review rejection. Red and green probe
sources, outputs and receipts are preserved under data/store-review.

The isolated ui/AndroidPersonalVoiceReviewDialog.kt implements explicit local
text opt-in, reviewing/editing word correction separately from intended meaning,
content-specific owner and sensitivity confirmation, and deletion. No microphone,
cloud or model update is wired. The real Android API compiler check passed;
Activity integration, Android view runtime and physical Fold7 remain NOT_RUN.
Frozen model-training data are unchanged.

## Follow-up consent race review

Bot 3 then demonstrated two actual stale-consent races in the initial UI toggle.
The coordinator added an atomic consent/revision snapshot and a revision-checked
text-only consent setter. The UI uses that snapshot, captures revision at the
owner's click, never copies an entire old policy, and rejects queued work from
dismissed forms before it starts. Three independent native adversarial-order
checks now pass, with compiler and runtime receipts under ui/. The original red
evidence remains on the evaluation branch. No Android event-loop or physical
device result is inferred from these host checks. All frozen data stay unchanged.
