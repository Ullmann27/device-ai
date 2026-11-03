# Local coding service builder handoff

Production source tested: `0c54a7bc33bc3979f59a1b4d1bcd9e5b4e39a115`.
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
`49 PASS / 0 FAIL / 0 SKIP`, 2.281 s, including service controls and existing
loop/assignment/report/orchestrator contracts. TAP SHA256:
`6002639f8c2111a0d6707f82c35d6b0cae879461f38e006ab86d1562b7e7a4ba`.
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
Weekly quota: `NOT_VISIBLE`; work is committed and pushed at each checkpoint.

## Next executable work

1. Integrate service commits `fd51d648`, `a588d361`, `0c54a7bc` with reviewed MCP.
2. Freeze the combined source, rerun paired lifecycle/reader/combined tests there.
3. Under one host CPU/service window, execute the independently frozen genuine
   model task through this CLI; retain both success and failed/abstained results.
4. Verify canonical patch/report and fresh-process experience retrieval; separate
   unknown-task generalization from known regression success.
5. Continue ARM64/Fold7 preparation without reusing host proof as device evidence.
