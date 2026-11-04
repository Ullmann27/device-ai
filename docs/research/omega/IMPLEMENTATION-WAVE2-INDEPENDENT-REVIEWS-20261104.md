# Three additional helpers: connection and independent reviews

Date: 4 November 2026. All three helpers were actually started in separate worktrees. Existing teams were left read-only. Reports below distinguish source review from executed checks. Root owns the combined Gradle result.

## Brain / memory helper — NO_CHANGE

Inspected base `e5c06e6bf657db556bc4861c7f46a55ebcd6e03a`. No missing connection was established and no code commit was created. The actual chain was traced:

`AgentActivity.runLocal` → lazy `AiContextCompiler` with `ContinuumMemoryRecallPublisher(filesDir)` → privacy/identity filters and inclusion only after the context-budget check → ranked included IDs and salted query hash → canonical `ContinuumMemoryEventHub` → subscribed `MemoryEventMapper` on the main looper → `memoryActivityTracker` / `CockpitAnimationPresenter` → `JarvisHoloAvatar` → `JarvisSequenceView` recall state and `BrainOrbAnimationRenderer` / `JarvisSequenceEffects` → the live `ContinuumHologramView`. The brain scene uses the same bus. Detach closes the subscription and tracker; no new pipeline was introduced.

This helper read Root's baseline evidence but did not execute a separate suite or a model/device run.

## Worker / tools helper — NO_CHANGE

Inspected the same base without a source change. The actual chain was traced:

`ChiefBotTaskIntake.prepare/claim/requireCurrent/reserveModelCall` → `AgentActivity.submitBotTask` LOCAL_TOOLS → active task and deadline → `runLocal` / `BoundedAgentOrchestrator` → shared ticket rechecked before the existing `ToolGate` runner → actual `DeviceToolExecutorRunner` callback, timeout, error or cancellation → tool observation and evidence handling → `AgentActivity.deliverResult` after actual GENERATING completion → stale-result rejection and truthful result status → `finishBotTask` clears deadline and ticket. Cancellation reaches the existing orchestrator, gate and runtime.

This is a source connection review, not proof of a continuously running external CCC1 worker. No worker or gate implementation was replaced. The helper did not execute a separate suite.

## Avatar helper — implemented and checked

The helper selectively integrated the pinned #502/#503 sources, then repaired bounded speech reattachment and added Boolean callback admission for the Activity owner. See `WAVE2-AVATAR-HANDOFF-20261104.md` for source blobs, upstream changes and actual 85/85 JUnit plus 26/26 motion checks.

Helper commit `7aa327a9cffa4d5b7d697f1bd967f6ca087dd83a` was cherry-picked as `6c6fbfb3abed6afeccfb2eadcd24ce4754e7a349`. Final child commit `68dac30c940ca0349daeb70657b39f0395043a3f` differs only by nine raw evidence logs and four review-note lines; that documentation-only delta was added by Root in `fc4c6465d9e480d9fca936809b7ff0a2613ee72e`. All production, test and host-check source bytes are identical between those child commits.

## Independent review of reattachment and admission

Both the brain/memory and worker helpers independently read the completed Director/Reducer addition and its 11 new JUnit cases. Neither reported a blocking defect. They verified:

- at most 64 cached last-word cues and the existing 64-entry reducer bound;
- unchanged original cue timestamps and exact expiry, including upcoming cues;
- no restoration of expired, ended or replaced speech; clock rollback before the word clears the cache;
- duplicate reattachment is rejected before queue/default-clock mutation;
- punctuation advances valid text/frame markers without inventing visemes or extending the fallback interval;
- accepted callback admission remains independent of sink rendering exceptions.

Reviewed source SHA-256:

- `avatar/AvatarDirector.kt`: `5b40cbcab5512ec50423c7b894efa476af429648204188872833d03cc6d9a203`
- `avatar/AvatarContract.kt`: `02b04292e0cd3c9e46cfa8cdfe262e17ffee5eb7da7a29746adf1a4b561fba9d`

## Independent final Activity review

Worker helper reviewed exact Root commit `fc4c6465d9e480d9fca936809b7ff0a2613ee72e`: no blocker found. `onStart` admits the active ID/foreground event through the Director before changing PCM, speech state or the frame schedule. `onRangeStart` forwards the actual frame and anchors PCM only after admission.

The two new actual-listener/reparent UI cases were also reviewed: the same live view restores the original AA cue at 210 ms; replay cannot extend it at 380 ms; a genuinely later O range remains valid; pause rejects late callbacks and clears PCM. Director and View share explicitly verified Robolectric clocks. These reviews did not execute tests; Root's separate combined run is the execution evidence.

## Final combination with the completed parallel team

After #512 completed, the same three helpers performed a further read-only compatibility review; no fourth helper was started. Remote #512 `f9267a3743a87a77bf6421011c014b2ca2d6b0d6` and local `58b2f12eecffde498b5cdc3718e07370250cd364` have the identical complete tree `fba27bc2200f0d318ce3ea7fc175499277ad5aba`. Root combined that frozen tree in the isolated #513 branch, leaving the sibling checkout and branch unchanged.

The memory helper verified that the calendar/overflow correction changes the existing learning retriever only. Main-chat `BoundedAgent` and `AiContextCompiler` still use `cognitive.HybridMemoryRetriever`. The known synthetic Top-3 ranking result of 43/44 to 42/44 stays open; the combination does not activate M2.9 or present calendar correctness as overall retrieval improvement.

The worker helper reviewed `JarvisSequenceView` and the QA preflight. Global/navigation events no longer own a worker-card slot; a bounded 24-ID completed/displaced history rejects late turn callbacks; recall events remain separate. These are presentation rules, not tool authorization. Canonical decimal QA version validation moves before setup in both modes while existing owner SHA/publisher/host, signing, branch and clean-source checks remain. App identity, signer, current version and update route are unchanged.

The avatar helper verified that CockpitAnimationEvent handling and AvatarEvent mouth cues remain separate. All three reviews found the same outdated #495 test expectation: after 170 ms speech, 40 ms reparent layout and another 170 ms, the original 320 ms word must already be SIL. Root reproduced exactly this single assertion failure (the other two imported live-listener cases passed), then changed that fixture to an actual next text range and viseme O. The independent stricter 210/380 ms reattachment test remains unchanged and continues to require original-time expiry and replay rejection. No production guard or assertion tolerance was weakened.

These compatibility reviews found no additional production blocker. They did not execute suites. Root owns the fresh combined Android, NewApi and 70-case QA contract execution.

## Combined motion-fixture compatibility

The first broader combined run executed 352 cases: 351 passed, with one assertion failure in `LipSyncFaceSourceTest.sharedOpeningMatchesAvatarMorphMapper`. The avatar helper independently confirmed two inherited fixture assumptions that #503 intentionally changed: the static loop reused one stateful mapper at an identical timestamp for all visemes, and its universal RMS-jaw formula incorrectly expected an open PP mouth.

Root retained every viseme case, gave each independent static sample a fresh mapper, and made the active PP override explicit: JawOpen=0, PP morph=1 and jawPitch=0. The authored geometric PP opening ratio remains0. All other geometric ratios and assertion tolerances remain unchanged. The helper found no main-app consumer of the optional MotionLibrary faceWeights/pose bridge beyond registration/bridge/tests; the live 2D renderer already closes active PP lips. No additional live-renderer defect is claimed, and no optional producer was rebuilt or newly activated. This was a test-contract migration; the combined production code was unchanged.
