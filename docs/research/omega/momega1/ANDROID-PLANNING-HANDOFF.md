# Owner-pinned local model → typed draft: source handoff

2026-11-03. Base `19474261c15a3503a9f041244b4b4c3eaed4636f`; accepted source
`4b98da9362f91ab9930a7f4b279737a9e80f94bd`. Isolated branch
`codex/momega1-android-20261103`, draft PR [414](https://github.com/Ullmann27/device-ai/pull/414)
targets Chief, not main. EXP-02/05/07/08/11/12. This increment follows the
[metadata-gap audit](ANDROID-MODEL-DRAFT-GAP.md); the earlier full-task approval
and revocation fix remains documented in [ANDROID-HANDOFF.md](ANDROID-HANDOFF.md).

## Implemented source path

Only existing production file `AgentActivity.kt` and new
`ContinuumCodingPlanning.kt` changed. ToolGate, ToolRegistry, native runtime and
manifest are unchanged. Normal chat defaults to its original generation,
dialogue evaluator, memories and persistence.

The existing coding dialog now offers **MIT LOKALEM MODELL ENTWERFEN**. Before
inference, the owner must supply a goal, full lowercase 40-character source
commit, one to three valid source paths of one language and fixed task limits.
The context copies the selected paths, generates the app-owned ID, and derives
the fixed check chain. It does not infer the Termux checkout from an APK, host
Git checkout, model hash or old prepared/build receipt. Source contents are not
read for this requirements draft.

`AgentActivity.runLocal` (`:6470`) accepts an optional planning context. Its same
bounded orchestrator selects the context's `CancellableAgentModel` (`:6528`),
which uses existing `LocalModelReasoningAdapter` / `runtime.streamThinking` on
the already loaded engine. It makes one bounded invocation, rejects tools and
overflow, checks cancellation and model identity, and returns no proposal.
Planning sets zero tool calls/retries, supplies no owner-memory hints and uses
an ephemeral experience store with no checkpoint file. No scheduler, runtime
instance, service, exported component or IPC was introduced.

The whole JSON candidate has exactly `schema`, `version`, `kind`, `goal` and
`acceptance`. StrictJson rejects duplicates, trailing content and malformed JSON;
the helper validates the task via existing `Draft`, caps output/canonical UTF-8
bytes, and rejects candidate IDs, source commits, scope, chain, limits, commands
and approval fields. The immutable owner context supplies all those task bindings.
The helper produces `Draft`, never `Authorization`.

The real `runLocal` UI completion (`:6799`) checks its still-active cancellation
token before clearing it, and requires uncancelled state, same session, same
foreground epoch and same model id/version/full weight SHA. Pause increments
the epoch, so resume cannot revive an old queued candidate. A successful
candidate alone can prefill the existing dialog (`:7160`); it does not enable
PREPARE until the owner reviews it. Edits invalidate review. The existing
revision/hash-bound owner approval, internal Builder full PREPARE review,
separate full RUN review, validated callbacks and cancellation revocation remain
the execution route. Model planning dispatches zero RUN_COMMAND intents.

## Exact-source RED → GREEN

Raw evidence and source/test hashes are preserved under
[android/planning](../../../evidence/momega1/android/planning/README.md).

| Snapshot | Actual host result |
| --- | --- |
| `e8c155e706924d3f611cdce4591849efe0902067` | Unchanged production: 13 tests, 13 failures, zero errors/skips; source-before/after identical. Eight authored parser/runtime contracts and three real dialog cases exposed the missing feature; two independent intake cases exposed the missing button. |
| `4b98da9362f91ab9930a7f4b279737a9e80f94bd`, first attempt | Production compiled; two independent fake runtimes omitted required `unload(callback)`, so test compilation stopped. No tests ran. Old copied XML was explicitly marked stale and is not a result. |
| Same `4b98da93`, fixture correction only | **102/102 PASS**, 17 classes, zero failures/errors/skips; Gradle exit 0; source-before/after identical. |

Final count: 11 authored planning tests + 30 independent planning tests + the
prior 45 typed review/lifecycle/revocation tests + 16 existing model binding,
bounded-agent and reasoning-adapter tests. This is a focused suite, not a claim
that the full app suite, lint or release build ran.

The independent reviewer froze cases before seeing implementation, then accepted
the exact source after inspecting 20 pure cases, two intake widget cases and
eight tests of the real `runLocal` with fake native callbacks and actual dialogs.
Those delivery cases cover positive full prefill, cancellation, session/turn
supersession, model changes, finish/pause, rejected tools and zero transport or
tool-receipt side effects. Independent ACCEPT is published in
[PR 435](https://github.com/Ullmann27/device-ai/pull/435), remote review commit
`0704d5517ac4d27f9d5067f97620715d795f98fa`; reviewer local
`daa9fc4b6e3b20140f03c0966eda6572a53c151e` has the same tree
`526482efa989332c769c13ae777e0f23d22327f0`. The runtime fixture uses the actual
`LocalModelRuntime` interface; it is not native inference evidence.

## Limits, next entry and rollback

The model-to-draft **source path is closed under supplied owner metadata**.
Automatic authenticated discovery of the live Termux checkout/file metadata is
still open, as described in the audit. Owner-supplied SHA/scope are checked
against the actual clean checkout by existing Node PREPARE/RUN gates later.

Actual Android LiteRT inference, app↔Termux delivery, Fold7 lifecycle behavior,
device source freshness, APK installation/signing and release remain NOT_RUN.
The owner confirmed that the device is unavailable. Host benchmark output with
synthetic metadata is a separate result and does not close these device gates;
this handoff does not claim MΩ-2 or owner-device completion.

Chief may integrate the exact reviewed source and tests with the published review.
Rollback is reverting this increment's two production files and associated
planning tests; no model/catalog/owner data migration or installed app changed.
