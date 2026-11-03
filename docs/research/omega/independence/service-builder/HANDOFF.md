# Local coding service builder handoff

Final production source tested: `d84689868631466640893c9ee9a0374db857a8b8`.
Closing mode: owner reported 20% remaining; no new model runs or experiments were started.
Base: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`; existing profile bridge
`773fbc94` is included as equivalent cherry-pick `1ec115d4`.
Branch: `codex/independence-service-20261103`; draft PR: [463](https://github.com/Ullmann27/device-ai/pull/463).
Target: `codex/continuum-independence-chief-20261103`, never `main`.

## Implemented path

Explicit local owner CLI → reviewed typed task fingerprint → `prepareCodingTask`
→ canonical `DurableTaskJournal` → existing `runTeamleaderLoop` → one fixed child
→ strict `runCodingTask` → `ownerCodingExecutor` → existing isolated workspace,
repair and independent verifier → existing Echo report and technical experience.

The authoritative existing profile IDs are `bot-forge`, `bot-lint`, `bot-echo`.
Profiles are read by the existing Kotlin `ChiefBotProfileStore` bridge. Names,
budgets and workspace identities are not re-created in a JavaScript registry.
Model calls are limited by both the typed task and persisted profile; current
default Forge profile permits one model call. Lint and Echo remain actual
verification/reporting operations, not extra model conversations.

`deliveries.json` records inbox delivery only, not task truth. All task outcomes
and evidence are freshly checked against the CCC1 grant, journal and receipts.
Runner metrics are MAC-bound with the existing CCC1 runtime key; they do not
replace a verifier receipt. Public views omit keys, arbitrary paths, raw prompts,
raw model text and raw tool logs.

## Operator surface

Entrypoint: `node scripts/continuum-coding-service.mjs COMMAND --config /absolute/private/state/config.json`.

| Command | Additional arguments / behavior |
| --- | --- |
| `init` | `--project`, `--profiles`, `--classpath`, `--runtime-env`, `--isolation-config`; all paths absolute. Optional `--max-queue`, `--max-records`, `--max-cycles`. No model start. |
| `prepare` / `review` | `--task /absolute/task.json`; displays structured task and exact `reviewSha256`. No admission. |
| `submit` | Same `--task`, explicit `--approve REVIEW_SHA`; creates one bounded owner grant and delivery. Works while stopped. |
| `start` | Explicit detached daemon start, returns after private socket readiness. |
| `run` | Foreground equivalent. No automatic import/start behavior. |
| `status` | Current process identity and task/queue status. |
| `stop` | Stops admission and requests canonical cancellation at a checkpoint boundary. Never reports stopped before observed process completion. |
| `cancel` | `--task-id ID`; cancels only this canonical task. |
| `agents` / `tasks` | Optional `--cursor ID --limit 1..50`; actual paginated metadata/current journals. |
| `evidence` | `--task-id ID --evidence-id result\|report`; bounded authenticated canonical projection. |

The existing owner patch view is retained:

```sh
node .github/continuum/ccc1-cli.mjs status --state /absolute/private/state/ccc1 --format json
```

It returns the canonical confirmed patch path, SHA and bounded preview. No patch
is applied, merged, signed or installed by this service.

## Preconditions and boundaries

- Linux host, non-root supported isolated executor, reviewed Qwen 1.5B pair only.
  No 7B, cloud provider, arbitrary command or unprotected shell fallback.
- Code checkout and project are independently pinned; source must be clean.
- State outside code/project/profiles, directories 0700 and files 0600; reject
  symlinks/hardlinks for sensitive reads. Owner controls the local OS account.
  Generated code never receives this state directory or host credentials.
- Real compiled Kotlin bridge classpath is explicit, immutable and hash-pinned.
  Use the maintained `runtime-classpath.txt`; full compiler/Android-test classpaths
  are not needed and may exceed bounds. Every Java read validates the bridge pin.
- Runtime binary, model weights, model manifest and isolation configuration are
  pinned. Native runtime independently revalidates its reviewed pair.
- One per-UID host model-admission lease prevents multiple service instances from
  running different state directories concurrently. This is not a new scheduler.
- No persistent loaded engine is claimed. The current runtime loads on demand
  per task. During a live task, native phase is unobserved by the control process:
  `modelLoaded` / assigned bot `modelActive` are null, not fabricated false/true.
- Queue max 32, history max 10,000, session max 1,000 cycles; defaults 8/1,000/1,000.
  Inbox admission additionally enforces a 1 GiB state allowance with 128 MiB
  reserved per admitted pending task. Artifact producers retain their own bounds.
  This accounting is not an OS disk quota. Cancellation is never capacity-gated.
- Idle waiting is event-driven. Finite sessions are never auto-restarted; an
  explicit owner start creates another session. Per-task call counts persist.
- `RESERVING`, `DISPATCHED` or pending cancellation after a process crash becomes
  `UNKNOWN` and is never automatically replayed. Live orphan worker identity
  blocks startup, including another service state directory.
- Stop keeps control responsive. After five seconds without worker teardown it
  explicitly reports `STOP_BLOCKED_WORKER_ACTIVE`; it never kills an unrelated
  PID/group. No full teardown claim after a host-forced worker kill while nested
  native/OCI processes may remain; inspect those processes separately.
- Profiles, queue records and prior results survive a normal same-source restart.
  Code upgrades require a separately reviewed configuration/state migration;
  stale source grants are rejected, not silently upgraded.
- Read-only MCP is separately implemented/reviewed in PR459. This module cannot
  authorize MCP writes or establish a remote ChatGPT connection.

## Evidence

Builder final-source focused run: `focused.tap`, `run.json` beside this file.
`49 PASS / 0 FAIL / 0 SKIP`, 1.805 s, including service controls and existing
loop/assignment/report/orchestrator contracts. TAP SHA256:
`07b0ca303399d0ba2d7a5889eb144c88e4ed96cdfc7ad36931496ae8e512b350`.
These are HOST software contracts; fixture data is explicitly synthetic.

Independent paired reviewer (PR458) reported:

- `a588d361`: 10/10 actual CLI/process lifecycle checks, 43.603 s.
- `0c54a7bc`: 4/4 active/global lease checks, 28.137 s.
- Actual process starts/stops, duplicate starts, idle SIGKILL, unrelated PID
  protection, final-slot admission race, idempotency, cancellation, stale source,
  corrupt inbox, active worker crash/no replay, cross-state/project admission and
  live orphan exclusion. Native binary removed in active crash fixtures so no
  model execution can occur. These are not neuron/model quality results.

Final integrated source checks, genuine local-model service run and independent
receipt validation are owned by Chief and the separate final reviewers. At this
builder handoff they are **NOT_RUN / pending**, not claimed passed. Android build,
ARM64 runtime admission, Fold7, install and release are outside this host evidence.
Direct weekly quota display: `NOT_VISIBLE`; owner-reported 20% triggered orderly closure. Work is committed and pushed at each checkpoint.

## Next executable work

1. Integrate service commits `fd51d648`, `a588d361`, `0c54a7bc`, `07ed91e5`, `3b3f51d8`, `d8468986` with reviewed MCP; preserve the documentation checkpoints separately.
2. Freeze the combined source, rerun paired lifecycle/reader/combined tests there.
3. Under one host CPU/service window, execute the independently frozen genuine
   model task through this CLI; retain both success and failed/abstained results.
4. Verify canonical patch/report and fresh-process experience retrieval; separate
   unknown-task generalization from known regression success.
5. Continue ARM64/Fold7 preparation without reusing host proof as device evidence.

## Closure repair: unexpected descendants are quarantined

The independent reviewer reproduced an actual defect on `0c54a7bc`: after a
worker SIGKILL the next queued task was dispatched, although nested processes
could survive. A harmless C-sleep specimen through the actual native launcher
confirmed the process topology: outer worker death did not terminate the
intermediate CLI/bwrap sandbox. This was a CONTRACT_TEST, not neural inference.

Commits `07ed91e5`, `3b3f51d8`, `d8468986` write a private per-UID barrier before
worker GO. Unexpected exit leaves UNKNOWN and the next task PENDING, stops the
scheduler and blocks every service configuration on that host account. The
barrier persists after process death/restart/cancellation. It is never cleared
simply because a PID disappeared. Status reports
`RESOURCE_RECONCILIATION_REQUIRED` and unknown model presence.

Barrier location: `/tmp/continuum-coding-owner-<uid>/unresolved-execution.json`.
The record binds task, state directory, service instance, exact process identity
and source SHA. A trusted normal worker completion may clear it; a confirmed
preflight refusal before canonical execution may also clear it. No `--force`
or unproved reconciliation command exists.

This is intentionally conservative: after uncertain execution the owner must
verify actual native descendants and containers are stopped before a separate
explicit operator cleanup of the exact attributable barrier. Cancellation, a
claimed success, elapsed time and a process restart do not establish teardown.
A host reboot also does not automatically erase this record in the application.
The service remains blocked until this operational reconciliation; this is an
open usability/recovery limitation, not a finished autonomous recovery feature.

Independent targeted final-source regression: `d8468986`, **1 PASS / 0 FAIL**,
9.314 s. It proved no next dispatch, persistent barrier and refused restart even
after restoring the runtime binary. The reviewer verified its synthetic worker
and daemon dead, no second dispatch, and no possible native call before removing
only that fixture's barrier. No test processes/barriers were left behind.

The initial idle wait now precedes the existing bounded loop, preserving a
`maxCycles=1` budget until an actual owner task arrives. Final focused 49-case
regression passed on clean exact source `d8468986`; no Fold7 or real service-model
acceptance is inferred from these results.

## Immediate 2% budget stop — unfinished follow-up

Owner instructed immediate secure-and-stop at 2% remaining. No tests, model
runs, service starts, barrier cleanup or further integration were performed after
that instruction. This section records an **UNVERIFIED WIP**, not acceptance.

The isolated service branch merged the frozen integrated source
`8fd58d00c53c6c2257a102922243f413afb6bac1` without resetting history; merge commit
`7fec637677a82235cb8074dab23a4c6a266b934c` was pushed.

Read-only S01 evidence at `/workspace/independence-s01-experiment/run` showed:
canonical task `t-af864ab6f02c7dec` is ABSTAINED/REFUTED, its receipt reports a
failed baseline, and the model request ended after 32,520 ms with exit code 1 and
zero response bytes. `runCodingTask` throws when no local model observation was
verified, before constructing Echo/result. Consequently the service delivery is
UNKNOWN with `CODING_MODEL_UNAVAILABLE`, no Echo/public snapshot, and the global
resource uncertainty barrier correctly remains. Neither this service builder nor
this checkpoint clears that historical barrier. Native reviewer owns its review.

Proposed narrow follow-up (NOT IMPLEMENTED here): generate a truthful existing
Echo report plus MAC-bound negative snapshot only for current canonical terminal
negative results. Zero verified inference must stay zero and must not imply zero
consumed attempts. Report availability must remain separate from authority to
clear the resource barrier. Ordinary exit code 1 is insufficient teardown proof.
Chief/runtime lane plans a process-private
`isTrustedLocalRuntimeFailureObservation` and propagated
`error.localRuntimeFailureObservation`; serialized trace flags never authorize
barrier release. No service consumer or negative-result implementation has been
added at this stop.

Partial code saved in this checkpoint:

- `init --model-timeout-ms` is parsed and recorded in config (default 60,000 ms,
  bounds 100..300,000). Existing config without the field remains accepted.
- `prepare` displays requested timeout, persisted profile duration and an upper
  bound. The existing 120,000 ms profile cap is not increased.
- **Still missing:** passing this option into the actual fixed provider, clamping
  every invocation to the original assignment expiry/canonical deadline, exposing
  the option in status, tests and independent acceptance. The current worker still
  uses its old provider timeout. Do not integrate or advertise this WIP as active
  timeout control. No production profile/model-budget override was introduced.

This checkpoint changes service store/CLI/prepare projection only. It has not been
compiled or tested after editing. Last accepted test claims earlier in this file
remain bound to their original source SHAs, not this WIP.

Next session: inspect this checkpoint and independently frozen negative-result
criteria `038e6b2d57b265cf0e27cd58d8c9fbb05caa66c9`; finish only the bounded failure
report/closure-proof distinction and timeout wiring, then review/test before
integration. No unknown S01 action may be replayed. No cloud substitute, model
change, main merge, signing, install or release was performed.
