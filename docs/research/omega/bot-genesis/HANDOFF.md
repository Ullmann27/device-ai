# Bot Genesis Ω — active integration checkpoint

Date: 2026-11-03. Quota: `NOT_VISIBLE`. Base: Synapse Chief
`4b881f81ddfbe38729a551d9e460e818c6b3b715`, production source
`c7ec853e2ffaa488b92826306f015bfaa67f5bb7`.

## Current verified source (supersedes the starting-status notes below)

Production source: `1595389274acb840c4ab544fa699c9eb2a04bd53` on
`codex/bot-genesis-chief-20261103`, Draft PR439. Later evidence/documentation
commits do not change the tested production files.

- Direct bot addressing and roster selection now call the shared local runtime
  with private persisted conversation frames. The existing compiler is reused;
  owner identity details, owner memories and tool grants are excluded.
- The registry reads V1 and writes V2 metadata. Conversation objects read V1 and
  write strictly validated UTF-8 V2. Profile IDs and existing notes are preserved.
- Scoped speech/model invalidation, source/session/profile correlation and native
  time/output limits are connected. A microphone interruption does not stop the
  coding coordinator. The original no-argument avatar-stop entry point is retained.
- Host CCC1 can bind actual persisted FORGE/LINT identities to distinct observed
  execution stages. Optional ECHO writes a deterministic receipt-derived report.
  The Kotlin handoff adapter has no installed Android transport/durable authority.
- Exact-source independent Android gate recovered after an environment status
  transition: Gradle exit0; 2,224 PASS / 17 SKIP / 0 FAIL, 2,241 total in404 suites.
  The34 focused UI/speech cases are included, not added again. Compile and the
  repository-configured NewApi lint gate pass (0 lint issues). No APK was built.
- Exact-source canonical Node regression:274/274PASS, zero skips; raw evidence
  at `docs/evidence/bot-genesis/final-node-1595/`.
- Independent private-context review:27 groups plus4 compiler tests PASS.
  Independent workspace review:12 Kotlin and17 Node probes PASS; actual Docker
  split-verifier fixture PASS with a disclosed scripted patch.
- Genuine new A0:0/3 verified repairs, six reserved calls/five admitted genuine
  observations, no false success or verified repair memories. Preserve this
  negative result. The original prior12-case4/12 result is a different experiment.

Final native acceptance has completed (PR455 commit0176702a, integrated into
Independence PR456): g04 FAIL with one genuine call and correct abstention;
separate KNOWN-u03 PASS with one genuine call, FORGE/LINT execution, deterministic
ECHO report and one eligible fresh-process experience recall. Both independent
audits PASS. Raw logs and hashes: `coding-review/final-native/`; interpretation:
`coding-review/FINAL-NATIVE-RESULTS.md`. Both attempts are consumed; no rerun is
pending. The known success does not replace the unknown failure.

## Remaining limits

- No attached ADB device; Fold7, installed Android version, audio/GPU/thermal,
  camera, offline device integration and update installation remain NOT_RUN.
- Private conversation snapshots have a4MiB per-bot cap and no garbage collection.
  The independent long-turn probe filled it after90 commits; withdrawal/reset
  clears the live reference but does not reclaim the immutable objects.
- Runtime cancellation requests cannot guarantee immediate native termination;
  the lane remains occupied while native state is GENERATING. A synchronous JNI
  start stall is not a demonstrated hard kill. Controller I/O locks may delay UI
  invalidation on slow storage; no device latency measurement exists.
- Working directories/identity names are not OS isolation. No Android shell,
  container, durable task-authority implementation or implicit network permission
  has been enabled by this integration.
- ATLAS/MNEMOS and the other profiles are not reported as completed workflow
  stages merely because they exist. Personality quality and automatic specialist
  selection have not been demonstrated by a blind local-model evaluation.

## Scope and decisions

Activate existing stable bot identities through the existing local runtime,
Chief/CCC1 contracts and object store. No second registry, scheduler or model.
The first direct-chat increment uses the shared loaded engine with a fresh,
tool-free native conversation and a private persisted bot transcript. Tool
execution remains subject to current canonical CCC1 grants and verified runner
availability. Personality cannot grant tools or turn an assertion into evidence.

Seven real sessions are available: Chief plus six specialists. Parallelism is
bounded by that actual capacity; the next builder/reviewer pairs run in waves.
Only one Gradle/native-build/model workload runs at a time.

| Owner | Exclusive change area | Independent check |
|---|---|---|
| Chief | AgentActivity, roster integration, final assembly | UI/overall reviewer |
| Registry builder | ChiefBotConversation, profile migration, compiler policy | Registry challenger |
| Registry challenger | tools/docs brain-review | Frozen before candidate |
| Native builder | Approved native build artifacts and provenance | CCC1 reviewer |
| CCC1 reviewer | New undisclosed host cases and canonical execution | Runner receipts and independent tests |
| Workspace reviewer | Typed handoff/current grant/budget acceptance | Next-wave builder uses frozen contract |
| UI reviewer | Roster regression and interaction acceptance | Exact-source host Gradle |

## Verified starting facts

- Executor recovered: actual commands work; retained weights and partial build
  exist. Provider status alone was not accepted as proof.
- The queued UI storage repair `407cdea413dbc8f31e5e662e0317eec5daf897dc`
  is independently rerun on exact base4b: 15 tests PASS, including the unchanged
  16-open queue bound (one pending operation). Two previously disclosed
  concurrency tests were adapted to the new busy/retry contract; raw old RED
  evidence remains in the review branch. Evidence integration pending.
- Latest external MΩ QA reports original 12-case experiment: 4 independently
  verified repairs, 8 abstentions, 18 genuine calls / 20 reservations. This is
  not a new holdout and does not meet its 8/10 goal. A separate known-case
  persistence-fix diagnostic is not added to that score.
- Native build is resumed only after UI reviewer releases the CPU window.
  Approved source/model/binary hashes remain unchanged; no success claim until
  the actual rebuilt binary matches its approved hash.

## Current acceptance

PROFILE_CREATED: prior implementation and restart tests PASS.
MODEL_CONNECTED / WORKSPACE_CONNECTED / TASK_EXECUTED: host known-u03 PASS;
independent LINT execution and ECHO report confirmed. New unknown g04 FAIL.
Standalone-service acceptance is a separate open Independence milestone.
Fold7, APK, installation, thermal/audio/display acceptance: NOT_RUN.

## Next executable steps

1. Complete private conversation migration and challenge it before integration.
2. Wire roster selection and explicit addressed chat before owner command routers.
3. Review current proof-persistence fix; freeze new three-case genuine host test.
4. Resume approved native build and hash verification; then execute canonical
   host CCC1 under one shared resource window, with all failures retained.
5. Add typed profile/handoff adapter to existing mailbox/CCC1 authority using
   the frozen workspace criteria. Keep logical workspace and sandbox IDs distinct.
6. Re-run combined tests on exact final source; preserve original package,
   signature, sphere, avatar/TTS pipeline and independent coding jobs on barge-in.

No main merge, installation, signing, release, new paid service or productive
weight change is authorized by this checkpoint. Regular pushes preserve WIP;
WIP is not acceptance.
