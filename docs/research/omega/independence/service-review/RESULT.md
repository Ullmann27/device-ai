# Independent service review — interim evidence

The criteria were frozen before candidate/API inspection at
`d1a280b64e092e063339b44e3e9426b3a7dcf43e` (criteria SHA-256
`874268217036c5309b7d5fd5c4de47dffffe8386dab9919691f3bca00d29f7bd`).

## Executed

| Source / environment | Result | Scope |
|---|---|---|
| Baseline `527d43f93f6b1837bd8da38cfefcfc84a84fd464`, Linux x86_64 | PASS 22/22, 0 skipped | Existing durable scheduler, including real SIGKILL/restart. |
| Candidate `fd51d648d63488d84ab1d354b8864f4e5ebfd43e`, Linux x86_64 | PASS 8/8, 0 skipped; 29.889 seconds | Actual owner CLI, daemon, IPC, disposable real Kotlin profile store, source/grant admission. |
| Attempted active-worker tests while the builder changed its worktree | BLOCKED at setup, 2 test errors | Both startups refused `SERVICE_SOURCE_DIRTY`. No worker or native inference ran. Retained under `source-dirty-blocked/`. |

The eight candidate cases exercised idle start/stop/restart, simultaneous starts,
SIGKILL of an idle daemon, refusal to kill an unrelated live PID named in stale
metadata, concurrent last-slot admission, durable task identity and queued
cancellation, exact owner-review/source binding, and corrupt inbox refusal.
They ran as independent Node CLI processes with cloud credential/provider
environment removed. The Kotlin profile store was actually seeded with twenty
synthetic identities; no second JavaScript registry was supplied.

The approved model/runtime files were hashed during daemon startup. No model
response or execution receipt was injected. These lifecycle results do not prove
neural coding, independent successful patch verification, or Fold7 execution.

The active-worker checks now use an exact-hash private copy of the approved
runtime executable. After successful startup, only that copy is removed before
submission, making inference unavailable independent of process polling timing.
The real child can then be paused, cancelled or interrupted without touching the
shared approved artifact. Their actual result is pending.

## Reproduce

Set the five environment paths documented in
`tools/independence-service-review/README.md`, then run:

```sh
node --test --test-concurrency=1 tools/independence-service-review/lifecycle.test.mjs
node --test --test-concurrency=1 tools/independence-service-review/active-recovery.test.mjs
```

Use a clean detached candidate checkout so concurrent development cannot change
the tested source. The original eight-case run used bridge source `773fbc94` and
`/workspace/independence-profile-773fbc94/runtime-classpath.txt`, approved runtime
environment `/workspace/synapse-local-model/runtime-env.json`, and existing
`/workspace/genesis-a0-experiment/isolation.json`. These paths are host artifacts,
not installable Android components.

## Still open

- Active child cancellation and dispatched-task crash/recovery on clean source.
- Successful genuine Qwen task through daemon, verifier, ECHO and restart recall
  (separate independent native reviewer owns this; not replaced by fixture tests).
- Newest integrated source retest, complete combined test chain and ARM64/Fold7.
- Lifecycle tests alone cannot certify the separate MCP evidence projection.

No original signature, owner data, main branch, installation or release changed.
Weekly quota: `NOT_VISIBLE`; work and raw logs are saved regularly in Draft PR #458.
