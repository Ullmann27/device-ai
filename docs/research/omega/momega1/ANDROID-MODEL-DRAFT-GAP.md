# Main local chat → typed coding draft: read-only follow-up

Audit: 2026-11-03, Chief source `9aa499ff943d0df38ff4e79f5dbb0516aabfc107`.
Scope: EXP-02/05/07/08/11/12, existing MΩ-1 typed coding route. No production
source changed and no new runtime, scheduler, service, IPC or tool proposed.

**OPEN:** the main `AgentActivity.runLocal` model response does not currently
become a typed coding draft. The accepted full-task review/cancellation changes
do not close this transition. MΩ-2 completion or real-device inference is not
established by this audit or by the separate host benchmark using owner-approved
synthetic task metadata. The owner confirmed that the device is unavailable.

## Actual call sites

Paths below are relative to `app/src/main/java/at/heinz/deviceai/`; line numbers
refer to the audited Chief source, not a proposed implementation.

| Call site | Current behavior / missing transition |
| --- | --- |
| `AgentActivity.kt:6467`, `:6523` | `runLocal` captures the loaded model, session and cancellation, then supplies its inline `AgentModel` to the existing bounded orchestrator. No target checkout snapshot enters this call. |
| `AgentActivity.kt:6558`, `:6683` | Optional reasoning-router advisory and normal `LocalToolContinuation` generation. The normal continuation exposes registered device tools; it is unsuitable as an unrestricted coding-planning entry. |
| `cognitive/BoundedAgent.kt:33`, `:74` | Output is bounded response text plus optional `ToolProposal`; limits support zero tool calls and zero retries. `maxSteps` counts state transitions, so setting it to one would prevent a normal model turn. |
| `ToolRegistry.kt:68`, `AgentToolLayer.kt:119` | No coding tool is registered. Unknown proposals fail validation. `ToolGate` is an execution gate, not an existing typed-draft importer. Adding a coding tool would expand the change unnecessarily. |
| `AgentActivity.kt:6710`, `:6722`, `:6813` | Model output is evaluated as ordinary dialogue, then displayed. There is no strict candidate parser or typed-draft presentation. |
| `AgentActivity.kt:7133`, `:7177` | Existing coding dialog generates an app-owned task ID, but goal, source commit, scope and criteria are entered manually. |
| `AgentActivity.kt:7189`, `:7238`, `:7250` | Existing draft validation, revision/hash-bound explicit owner approval, and internal Builder handoff already exist. Preserve these stages and the Builder's separate full PREPARE/RUN review. |
| `ContinuumCodingTask.kt:22`, `:32`, `:47` | `Draft` supports incomplete fields, lexical scope/limits checks, immutable snapshots and conversion only after all requirements pass. It does not discover checkout contents. |
| `ContinuumTypedCodingBridge.kt:30` | Existing strict canonical JSON/hash parser returns an `Authorization`. Do not turn model text into that owner-approval object. Factor reusable task decoding if needed, keeping candidate data separate from owner consent. |

## Target metadata is missing from main chat

The host's Git SHA is available through Git, but that does not identify the
mutable checkout in the owner's Termux process. Neither an APK build SHA nor a
model weight SHA identifies that target checkout.

- No Git source commit build field appears in the audited Gradle build files.
  `LocalTurnModelBinding.kt:11` binds model id/version/full weight SHA, which is a
  different identity and must remain enforced.
- `ContinuumMobileBuildPlan.kt:20` pins the project path to
  `/data/data/com.termux/files/home/device-ai`; the Android app has no direct
  access to this other application's private checkout.
- `ContinuumCcc1Bridge.kt:138` validates the full preflight `sourceCommit` and
  branch, then returns only a human message containing the first 12 commit
  characters. Its `Result` does not expose a reusable full source snapshot.
  Submitted results similarly reduce source identity to display text.
- `ContinuumMobileBuildResultReceiver.kt:186` can read build-report `commit`
  provenance, but this is an artifact/build receipt, not fresh checkout/file
  metadata for the next coding task. Typed pending/prepared commit preferences
  originate in an already approved task; reusing them would inherit stale scope.
- `cognitive/DevelopmentTypes.kt:85` source snapshots contain path/content/hash;
  the development UI and backlog do not carry the target checkout's Git commit.
  `runLocal` does not import those source snapshots as a project manifest.
- `.github/continuum/coding-orchestrator.mjs:36` checks the actual Git HEAD and
  clean tree during PREPARE; `:110` checks again for execution. These gates
  correctly reject stale tasks, but occur after draft intake and cannot supply
  the missing initial chat metadata retroactively.

A minimal explicit planning entry could ask the owner for the full 40-character
commit and selected source paths before inference. That is owner-supplied scope,
not automatic project discovery. Automatic intake instead needs a separately
scoped fixed, read-only metadata operation on the existing Termux route: bounded
full commit, fixed project identity, clean/dirty status and bounded selected-file
metadata; fresh request/callback correlation; no model-controlled executable,
working directory or command arguments; reject stale, mismatched, truncated or
unsolicited receipts. Extending the existing preflight receipt is a possible
starting point, not an implemented metadata API. No localhost server or exported
unauthenticated component is needed. A receipt hash alone is not authentication.

## Smallest future integration, once metadata is supplied

1. Add an explicit coding-planning entry, with immutable owner-pinned target
   commit/scope and planning budgets. Do not interpret arbitrary chat as coding
   authorization or silently infer a target from previous task/build receipts.
2. Pass this context through the existing `runLocal` path. Inside its existing
   `AgentModel`, use the same loaded runtime's isolated `streamThinking`
   (`localmodel/LiteRtLmLocalModelRuntime.kt:199`, tools empty at `:247`). The
   existing `cognitive/LocalModelReasoningAdapter.kt:28` already enforces bounded
   output/deadline, sticky cancellation, model identity and rejection of tool
   events. Skip the advisory/router and normal tool continuation for this mode;
   this is one bounded request on the same engine, not a second model.
3. Set zero tool calls/retries for this mode and reject any proposal, including
   injected fake-runtime events. Replace the ordinary dialogue evaluator with
   strict whole-JSON candidate validation only in this mode. Reject duplicate or
   unknown keys, prose/fences/trailing data, wrong primitive types, numeric
   overflow, invalid enums, oversized UTF-8/canonical payloads and invalid scope.
   Generate the task ID in the app; bind the source commit outside model output.
   Model suggestions cannot widen the pinned owner-selected paths or budgets.
   Missing requirements remain incomplete; they are not invented to pass checks.
4. Do not use the current `generateLocal` capture as proof of whole output:
   `AgentActivity.kt:7455` clips captured text. Use the bounded adapter above or
   explicitly fail/cancel on overflow rather than accepting a clipped valid prefix.
5. Only a successful still-current turn may offer the candidate for review.
   Recheck cancellation, session/turn identity, activity lifecycle, pinned
   metadata and `LocalTurnModelBinding` on the UI thread immediately before
   presentation; the existing model check before returning `AgentModelOutput`
   does not cover a later UI queue delay. Never revive a checkpoint as new consent.
6. Prefill an overload of `showCodingTaskDraftDialog(initialDraft)`; preserve
   app-owned ID, edit-invalidates-review behavior and all explicit owner approval
   stages. Draft presentation creates no `Authorization`, Termux request, pending
   job or execution receipt. A successful model response alone must dispatch zero
   `com.termux.RUN_COMMAND` intents. Existing PREPARE/RUN, replay protection and
   target-bound cancellation remain the only execution route.

## Proposed verification and remaining acceptance

Host RED→GREEN can cover strict candidate parsing and canonical size limits;
owner commit/scope binding; app-generated IDs; one same-runtime model invocation;
zero tool executions; missing fields; invalid/prose/duplicate/trailing JSON;
overflow/deadline/cancel/model swap; stale session/metadata, destroyed activity,
late callback and duplicate completion; full prefill; edits invalidating review;
zero dispatch before approval; and unchanged existing PREPARE/RUN/revocation
tests. Use an injected fake runtime and real Robolectric UI events with observed
intents, explicitly labeling them host tests. Production code would require
Chief allocation; none is allocated or changed by this follow-up.

Host tests can close the source-level adapter with supplied metadata. They cannot
prove actual on-device LiteRT output, Android/Termux delivery, live checkout
freshness or lifecycle behavior on Fold7. Those remain device acceptance with
the owner's loaded model and checkout; no device result, install or release is
claimed. This document's verification is read-only call-site inspection only.
Rollback is removal of this documentation; existing binary behavior is unchanged.
