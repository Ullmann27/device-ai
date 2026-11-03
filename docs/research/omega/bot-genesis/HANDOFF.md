# Bot Genesis Ω — active integration checkpoint

Date: 2026-11-03. Quota: `NOT_VISIBLE`. Base: Synapse Chief
`4b881f81ddfbe38729a551d9e460e818c6b3b715`, production source
`c7ec853e2ffaa488b92826306f015bfaa67f5bb7`.

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
MODEL_CONNECTED / WORKSPACE_CONNECTED / TASK_EXECUTED for the new bot path:
NOT_RUN. Three-bot genuine end-to-end acceptance: NOT_RUN.
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
