# Independent Android review

Status: ACCEPT scoped host/source repair037370f0544d3ed2ee7686082c41c78046daa64d; FOLD7_NOT_RUN. Reviewer branch begins at ca93fb4f7a17ff7a2a13debd93197ba485594f06. Final Android candidate not yet inspected. Independent reviewer is distinct from feature author and QA. Scope is review artifacts only; no feature edits.

## Frozen adversarial cases (before candidate inspection)

1. Full approved task: display every source path, all acceptance criteria, exact full source SHA, fixed chain, bounded limits and task identity. Long values must remain available; do not authorize omitted content.
2. Alter each tuple field after preparation: task ID, job ID, source SHA, source list, criteria, limits, chain, serialized snapshot, snapshot digest and nonce. Reject stale/tampered authorization before command execution.
3. Approval: absence, mismatch, stale identity, task switching, app recreation and process death must fail closed; successful approval is consumed once.
4. Replay and concurrency: duplicate taps, duplicate callback, old callback arriving during a new job, mismatched command ID/action/nonce/job, malformed result and prepare/run interleaving must not issue a second command or accept stale state.
5. Cancellation: canceled/replaced task cannot dispatch or resurrect success. Callback and cancellation must correlate to exact CCC1 job target and current command; a cancellation request alone must not imply terminal cancellation.
6. Lifecycle: destruction/recreation, process death, missing in-memory state and pending callback races must preserve uncertainty rather than restore execution authorization.
7. Production call paths: validate actual manual task draft → full approval → bridge → builder → fixed Termux RUN_COMMAND → callback and canonical CCC1 correlation. Existing runLocal/model path must not be described as model-authored task integration without a real caller.
8. Callback provenance and atomicity: external/forged result, wrong result type and partially persisted tuple cannot establish valid prepared state; updates and callback consumption must be atomic enough to avoid mixed identity.

## Validation boundaries

QA owns every Gradle schedule; this reviewer will coordinate focused host checks. All commands, exact source SHAs, exits, case counts and hashes will be recorded. Contract/stub tests do not establish Android lifecycle or Termux/device operation. No device access: FOLD7_NOT_RUN. No local-model inference established.

## Next step and rollback

Obtain final candidate freeze and prior actual RED evidence from QA, then inspect actual source and execute independently written focused adversarial checks. Rollback is omission of this review-only branch. No main merge, signature, installation, owner data or deployment changes.

## Candidate and independent checks in progress

Frozen author candidate: `b95858c103243ec921c1de9c8fb9ae8da0e88c29`. Original frozen review cases: local `4556be48`, byte-identical connector publication `56ee1f008f9f850573758484f12d3671950d65b8`, draft PR419. CLI GitHub token expired; connector publication does not reuse the local commit identity.

Independent exact-production Kotlin checks: `./tools/momega1-android-review/run-pure.sh b95858c103243ec921c1de9c8fb9ae8da0e88c29 docs/evidence/momega1/android-review/pure-b95858c1`, compile exit0, run exit0,31/31 cases PASS,0 skipped. Actual production bridge/task/canonical JSON/build-plan files are extracted by SHA and compiled; no Android or transport stubs. Checks cover maximal full review (3 paths,8 criteria, full600-character goal), per-field tamper, binding omissions, canonical bytes, numeric overflow, fixed command construction and hostile callback results. Initial reviewer harness compile typo and invalid chain-fixture construction failed before correction; both retained in sibling evidence directories and are not product REDs.

Separate independent9-case Robolectric UI suite is frozen at local `a3e257a1`, connector `ae0120f68d982bd93b67810e44a6fc6a96950c23`. QA owns execution. Reviewer mistakenly added separate pure-harness files to its registered source directory while initial QA compile was pending. The UI class bytes stayed unchanged, but QA will retain that initial attempt and rerun an isolated copy of only the frozen UI class; ambiguous compilation input is not accepted as evidence.

Static candidate finding under reproduction: matching terminal CCC1 abort removes canonical target but retains prepared task authorization. Existing RUN dialog can apparently restore canceled target and dispatch. This is an Android consent/dispatch defect; canonical Node `runTask` checks terminal journal state before invoking the repair/model callback, so this finding is not evidence of canceled model re-execution. No feature edits made by reviewer.

## Actual call-path audit

`AgentActivity.showCodingTaskDraftDialog` manually constructs a typed task via `ContinuumCodingConversation`, checks reviewed revision/hash/JSON again on confirmation and opens the Builder using JSON/hash extras. Builder is exported, so hash is not an author credential; it parses exact canonical task bytes and requires a new complete preparation dialog. PREPARE persists full expected snapshot, then fixed Node arguments are sent through explicit Termux RUN_COMMAND with one-shot mutable PendingIntent to the nonexported result receiver. Immutable callback URI and pending request/operation gate result consumption. Validated PREPARE stores snapshot plus journal ID. RUN shows full task and journal ID again, rechecks the current snapshot and journal ID, durably removes preparation while recording pending state, then starts Termux. Process death after recording pending state cannot automatically restore a consumed RUN approval; actual device death remains untested.

`AgentActivity.runLocal` separately constructs the existing model adapter/orchestrator; no model-to-coding-draft dispatch was added. The Node typed bridge routes through existing `runCodingTask` and canonical CCC1 `runTask`. The canonical terminal journal gate returns before model/repair callback execution. AgentActivity, AndroidManifest, typed Node bridge and canonical orchestrator files are unchanged between ca93fb4f and b95858c1. These source observations are not real local inference or Fold7 evidence.

## Final independent acceptance

Finding AR-1 was reproduced on b95858c1:9 independent UI tests,7PASS/2FAIL/0SKIP, exit1. The exact repair037370f0544d3ed2ee7686082c41c78046daa64d passes13/13 independent UI tests plus32/32 authored regressions,0skips, Gradle exit0; before/after source manifests match. Reviewer inspected original XML, not just reported totals. Pure exact-source Kotlin checks31/31PASS on both candidates (compile/run exits0). **ACCEPT this narrowly scoped Android consent/callback repair**, with no claim of local inference, model-to-draft integration, real process death, Fold7 or release acceptance.

Read `ANDROID-INDEPENDENT-REVIEW.md` for finding, source call paths, commands, limitations and failed harness/scheduling attempts. Evidence is self-contained under `docs/evidence/momega1/android-review`, including RED/GREEN raw logs/XML/source hashes and SHA256SUMS.json. Test sources are in `tools/momega1-android-review`. QA alone ran Gradle from immutable snapshots of the two reviewer UI classes. No production files were changed in this review branch. Chief should integrate only reviewed feature source037370f0 (or prove identical production bytes), then run combined exact-source checks; do not transfer host acceptance to a changed feature source.
