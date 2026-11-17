# BOT GENESIS independent conversation review

2026-11-03. Reviewer: the separately running Team A challenger. Production code was
written by the builder and Chief; this branch changes review tests and evidence only.
Quota: NOT_VISIBLE. No deployment, installation, signing or main merge performed.

## Decision and exact source

**ACCEPT_SOURCE_SLICE** for
`a9acd007bc7bd485b2bda3c2db2dc2e57791af73`, builder PR448.
This is independent host verification of the private conversation adapter, registry
migration and compiler scope. It is not Android, JNI, actual-model or Fold7 acceptance.
Baseline: `4b881f81ddfbe38729a551d9e460e818c6b3b715`.
Review branch: `codex/genesis-brain-review-20261103`, draft PR438.

Frozen acceptance predates the candidate/API: commit
`4851d50f893119344db4d05f7309c2fd93d3f4eb` and FROZEN-CRITERIA.md
SHA256 `522e370c02bc84c9233b44eb00fb2878da6ca37d1822167544626127a5a5aa2c`.
Cases were added for subsequently introduced deadline and serialization boundaries
before those checks were run. These exposed cases are now regression material;
they are not unknown neural generalization tasks.

## Executed results

| Suite | Exact source | Result | Evidence |
| --- | --- | --- | --- |
| Public conversation counterexamples | a9acd007 | 15/15 groups PASS | green-a9acd007/conversation.log |
| Migration, foreign object, missing object | a9acd007 | 3/3 groups PASS | green-a9acd007/storage.log |
| Budget, deadline, withdrawal, Unicode, actual storage pressure | a9acd007 | 5/5 groups PASS | green-a9acd007/pressure.log |
| Actual old conversation codec compatibility and malformed data | fcea5908 writer / a9acd007 reader | 4/4 groups PASS | codec-a9acd007/codec.log |
| Existing compiler regressions | a9acd007 | 4 JUnit tests PASS | green-a9acd007/compiler-regressions.log |
| Existing runtime boundary/delivery regressions | 4b881f81 | 6 JUnit tests PASS | runtime-baseline/ |

The 27 new review groups contain multiple assertions; the separate 4+6 existing
JUnit tests are not inflated into model-quality metrics. No actual model is called.
The runtime baseline tests are not a rerun of the final combined Android caller.

Two real failures were retained and repaired without weakening their checks:

1. Candidate `3f5c293b4db437c12659f2e02c8dd4557ff116bd` passed a 16,000-character
   prompt bound to an existing assembler limited to 15,500. All preparation paths
   failed closed. See RED-3f5c293b.md and red-3f5c293b/.
2. Candidate fcea5908 counted history as UTF-8 but wrote Java modified UTF-8.
   Valid emoji conversations exceeded the actual codec bound. See
   RED-UNICODE-fcea5908.md and red-unicode-fcea5908/.
   The repair writes bounded strict UTF-8 for dialogue text under
   BOT_CONVERSATION_V2 and retains V1 decoding. A separate JVM running the actual
   old fcea5908 classes produced the positive V1 payload; a9acd007 decoded it and
   rewrote V2. Negative tests reject malformed UTF-8, invalid lengths, future
   versions, foreign identities and trailing bytes.

## Verified boundaries

- Stable bot ID binds private notes/history independently of display name. Owner
  facts, seed/genome details, model labels and other bots' data are canary-tested
  out of the generated prompt. Persona supplies quoted descriptive data only;
  model output remains unverified conversation, never owner truth or tool proof.
- Exact frame identity, selection epoch, model digest/version, session, profile
  revision and deadline gate completion. Delayed or cancelled frames cannot commit
  or cancel a newer frame. Eight concurrent prepares admit one pending frame.
- Working-note withdrawal invalidates pending output and derived history. Reset
  preserves notes and clears active conversation; restart reopens only committed
  data. Existing V1 profiles migrate without losing identities/notes.
- A valid object hash alone cannot rebind another bot's conversation. Missing
  objects fail closed. A zero model-call allowance and elapsed deadline reject
  work rather than fabricate a result.
- Real runtime inspection found that `streamThinking` creates a fresh, tool-free
  native Conversation. Its software boundary/delivery tests passed. Actual JNI
  context disposal and KV isolation still require runtime/device evidence.

## Limits and required integration checks

- This is an application-trusted identity seam, not an OS sandbox or secure host
  filesystem boundary. Actor-ID equality cannot authenticate an arbitrary caller.
- The controller is not a model runner, scheduler or independent permission system.
  Chief must route through the existing single runtime and retain the native lease
  until actual terminal completion. Generic runtime cancel is globally scoped;
  bot switching must not cancel unrelated coding/workspace jobs.
- `isCurrent` checks persistent revision off the UI thread. `isPresentationBound`
  checks only in-memory correlation and cannot authorize recall or persistence.
  Immediate cross-process withdrawal between strict checking and UI delivery is
  not claimed; Chief owns mutation invalidation and final caller review.
- Conversation objects accumulate under a 4 MiB **per-bot** cap. The final pressure
  run committed 90 synthetic long ASCII turns and retained 4,175,692 bytes; the
  next append was refused while the last committed profile stayed intact. Reset
  still cleared the active pointer after reopening. Old objects remain: no garbage
  collection, physical erasure or indefinitely renewable chat capacity is proven.
  Twenty such caps are not a global disk reservation/enforcement proof.
- 27 synthetic profiles in a routing test do not mean 27 model sessions. CPU,
  latency, RAM, energy, neural accuracy and autonomous coding success were not
  benchmarked by this review. The 1 ms deadline test verifies expiry, not latency.
- The candidate checkout had one untracked builder documentation harness
  `docs/research/omega/bot-genesis/brain-builder/run-focused.sh`. It was not compiled.
  All compiled production/test file hashes and actual dependency class hashes are
  preserved, so the tested source slice is unambiguous.
- ANDROID_BUILD_VERIFIED, FOLD7_VERIFIED and RELEASED remain outside this review.

## Reproduction and next action

The focused JVM harness uses actual Kotlin implementation classes, existing compiled
app dependencies and official dependency jars. Synthetic identity/model metadata
fixtures do not load model weights. OpenJDK 21.0.12.1; compiler JVM cap 768 MiB,
individual check JVM cap 512 MiB; each run has a 30/60 second process timeout.

```sh
tools/genesis-brain-review/run-candidate.sh /path/to/a9acd007-checkout /fresh/review-output
tools/genesis-brain-review/run-codec.sh /path/to/fcea5908-review-output /fresh/review-output /fresh/codec-output
```

Override GENESIS_BASE_CLASSES, GENESIS_V1_CLASSES and CONTINUUM_FABRIC_JVM_DEPS
when rebuilding elsewhere. The first script records the exact source, hashes,
Java version, compile logs and exit codes. The old reviewed V1 profile-store classes
come from source `2c5cd84ad7f4e19b9f52430856d311bfdf9dc18c`; the old conversation
classes come from the separately recorded fcea5908 run. No replacement API stubs
are used. Replay requires compiling those actual source/dependency versions first.

Chief can integrate the reviewed builder source onto its isolated research branch,
then rerun affected tests and the separate Activity/lifecycle review on that exact
combined SHA. Follow-up priorities: bounded transactional object reclamation with
withdrawal preservation, global storage budgeting, then real JNI/Fold7 isolation,
interruption and restart tests. No production rollback or data deletion was used.
