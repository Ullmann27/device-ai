# Independent caller review — 2026-11-03

Status: **CHANGES_REQUESTED — source audit, not runtime execution**.

Reviewer session `synapse_lifecycle_builder` did not author these caller changes.
Criteria froze in `c09ef5b721a3cbb66f962c232066680425ff1f88` before inspection.
Assigned Activity/Roster candidate: `7473ffb6584b70b1023b1156fca823e2a76dff36`.
Composed candidate inspected: `66fce742c89236e7d5b78ceb2ec29ed1f1cd7b44`.
Activity, Roster, address router and native runtime are byte-identical across these
two commits. The composed candidate additionally contains the independently
developed private controller, store and compiler changes. Source hashes are in
`SOURCE-RECEIPT.json`.

No Gradle, native model, emulator or device run was performed in this review.
The separate UI review's 29 widget/speech results are not counted here. No frozen
unknown MΩ benchmark task bodies were inspected. No production file was changed.

## CR-1 — speech recognition scope survives a private bot switch

Priority: P1, stale private input routing. Source-demonstrated call path; the
callback sequence below has not been executed on Android by this reviewer.

`AgentActivity.enterBotConversation` (1524), `returnToJarvis` (1537) and
`botContextChanging` (1470) stop TTS, but do not call `cancelSpeechInputScope`.
They also leave `sessionId` unchanged. `startVoiceInput` (9083) captures only
`speechScopeGeneration`, `sessionId` and the optional developer dialog. Its
`onText` callback (9108) does not bind the selected bot. The microphone permission
continuation (9145) uses the same scope guards.

Counterexample:

1. Select Mira and start on-device recognition, or leave its microphone
   permission request outstanding.
2. Before the result arrives, select Atlas via the roster or switch to JARVIS.
3. The old recognition result still matches both captured guards, and
   `applyLocalSpeechText` writes Mira's delayed dictation into the current
   composer's text. The outstanding permission continuation can similarly start
   recognition in the new conversation.

This does **not** automatically send a message or authorize an action: the current
code requires the owner's separate send gesture. It nevertheless fails the
private conversation boundary and delayed callback criterion. TTS callback
correlation already uses utterance identity; that does not invalidate ASR.

Suggested narrow repair: invalidate/cancel the existing speech scope at an accepted
selection request, before queued history I/O, and whenever the selected bot context
changes, including return to JARVIS and owner profile/context mutations. Waiting
until history I/O finishes leaves an interval in which the old result can populate
the composer and then be carried into the new bot. Preserve the existing recognizer
and explicit-send contract. The UI
reviewer can add delayed-result and delayed-permission regressions to its existing
suite; do not count this source counterexample as an executed RED test.

## Audited boundaries that are present

- `runBotDialogue` uses direct UI bubbles rather than owner-learning
  `addUserMessage`/`addAssistantMessage`, and does not update `lastLocalReply`.
  Its input therefore does not enter those public-topic discovery or neural-radar
  hooks through the inspected private path.
- `streamThinking` creates a fresh native conversation with no tools. Actual
  READY admission is synchronized in `LiteRtLmLocalModelRuntime`; the UI busy flag
  is not the sole native admission boundary. Isolated conversation close completes
  before terminal delivery releases the runtime lane.
- The controller builds a private frame without owner identity details, archive
  memories, research sources or tool catalog. Private history and working notes
  are explicitly unverified context, not owner facts or tool grants.
- Prepare/complete correlate the private turn object, stable bot ID, controller
  epoch, model binding, chat session, profile revision and deadline. The store
  snapshots private notes/history under its existing lock and checks the revision
  again on commit. Successful storage is not advertised as independent tool proof.
- UI completion checks cancellation identity and private presentation binding.
  It retains the UI lane while the runtime reports GENERATING; cancellation is
  not claimed to terminate an interrupt-ignoring native call.
- Private selection/history I/O runs on the existing backlog executor. No second
  model loader, scheduler, TTS listener or public-memory database was introduced.

These are source observations, not end-to-end or Fold7 acceptance.

## Bounded limitations and follow-up checks

1. Direct addressed entry such as `MIRA, ...` calls `enterBotConversation` and
   clears the UI without replaying prior history. Roster selection and `rufe MIRA`
   use `selectBot` and restore the stored pairs. The model's prepare path still
   loads its correct private history; this is an inconsistent visible-history
   restoration path, not evidence of lost storage.
2. The new deadline loop runs after synchronous `streamThinking` returns. Native
   conversation creation/token counting/dispatch can block before that loop.
   This source does not establish hard native termination or a measured maximum
   wall time under JNI stalls. Device cancellation and stalled-dispatch tests
   remain necessary.
3. `ChiefBotConversation.complete` holds its monitor across store I/O/fsync.
   UI `invalidate/select` can wait for that monitor. A possible UI stall is
   identified; no deadlock or measured delay is claimed. Avoid weakening commit
   atomicity without a separately reviewed alternative.
4. The shared runtime exposes unscoped `cancel()` and process-wide state. This
   caller serializes its work and preserves identity checks, but source inspection
   cannot certify cross-Activity/native-session handoff races. Old-Activity
   destruction versus new-Activity inference needs a real lifecycle test.
5. Registry persistence, tool-free private model dialogue, independently verified
   tool execution and local-model repair success remain distinct milestones.
   This review establishes none of the latter two and no device performance claim.
6. `loadModel`/`unloadModel` do not explicitly invalidate the private controller.
   The normal model action button disables while busy, but model binding compares
   id/version/weight SHA rather than an engine generation. The controller handoff
   requires invalidation on lifecycle changes, including reload of identical
   weights. No executable same-weights reload race was demonstrated here; an
   explicit lifecycle hook and a caller regression would close that integration
   assumption without changing the controller contract.

Release, original signing, installation, main merge and owner data are untouched.
