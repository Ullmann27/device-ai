# Independent service lifecycle review

Final critical finding: a dead worker could leave descendants alive while the
service started the next queued task. This was independently reproduced and
corrected with a durable uncertainty barrier. See `DESCENDANT-FINDING.md` for
the actual RED→GREEN evidence and explicit fixture-only cleanup.

The criteria were frozen before candidate/API inspection at
`d1a280b64e092e063339b44e3e9426b3a7dcf43e` (criteria SHA-256
`874268217036c5309b7d5fd5c4de47dffffe8386dab9919691f3bca00d29f7bd`).

## Executed

| Source / environment | Result | Scope |
|---|---|---|
| Baseline `527d43f93f6b1837bd8da38cfefcfc84a84fd464`, Linux x86_64 | PASS 22/22, 0 skipped | Existing durable scheduler, including real SIGKILL/restart. |
| Candidate `fd51d648d63488d84ab1d354b8864f4e5ebfd43e`, Linux x86_64 | PASS 8/8, 0 skipped; 29.889 seconds | Actual owner CLI, daemon, IPC, disposable real Kotlin profile store, source/grant admission. |
| Attempted active-worker tests while the builder changed its worktree | BLOCKED at setup, 2 test errors | Both startups refused `SERVICE_SOURCE_DIRTY`. No worker or native inference ran. Retained under `source-dirty-blocked/`. |
| Candidate `a588d361274af22e4ede602c3bfabcf3b08b8f22`, Linux x86_64 | PASS 10/10, 0 skipped; 43.603 seconds | All eight original cases plus real dispatched-child cancellation and daemon crash/no replay. |
| Candidate `0c54a7bc33bc3979f59a1b4d1bcd9e5b4e39a115`, Linux x86_64 | PASS 4/4, 0 skipped; 28.137 seconds | Both active-child cases repeated, plus two different project/config/state directories and live orphan exclusion. |
| `0c54a7bc`, supplemental descendant hazard | FAIL 1/1; 8.335 seconds | Unexpected worker death allowed a different queued task to dispatch; harmless actual-launcher proof independently showed descendant survival. |
| `d84689868631466640893c9ee9a0374db857a8b8`, critical fix | PASS 1/1; 9.314 seconds | Next task stays pending; restored-runtime restart refuses until explicit reconciliation. |

The a588d361/0c54a7bc success rows contain twelve distinct scenarios, with two
repeated across revisions. The additional critical case makes thirteen current
scenarios. The older successful orphan tests predate the stricter persistent
barrier and do not certify its safety. Their raw results remain unchanged.
The final thirteen-case integration rerun belongs to the separate final QA gate.

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

The active-worker checks use an exact-hash private copy of the approved
runtime executable. After successful startup, only that copy is removed before
submission, making inference unavailable independent of process polling timing.
The real child can then be paused, cancelled or interrupted without touching the
shared approved artifact. Both cases passed: cancellation preserved canonical
`ABORTED` and retired delivery, while daemon SIGKILL exposed `ORPHAN_WORKER` and
restart retained `UNKNOWN` without replacing the original worker identity.

The per-UID resource gate was exercised with genuinely different projects and
state directories. A second service refused startup while the first service was
alive. After the first daemon was verified dead, the second could start. A live
orphan worker after daemon SIGKILL also blocked a different configuration until
the test operator terminated that exact child. Contenders did not kill it or
silently run another worker.

At `a588d361`, 108 real CLI processes were observed. CLI status p50/p95 was
86.960/100.502 ms and start p50/p95 was 1289.597/1367.394 ms (nearest-rank
percentiles; startup includes file hashing). These are small host-fixture
measurements, not Fold7 performance promises. RAM and energy measurements were
`NOT_RUN`; no CPU-time proxy is labelled energy.

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

- Successful genuine Qwen task through daemon, verifier, ECHO and restart recall
  (separate independent native reviewer owns this; not replaced by fixture tests).
- Newest integrated source retest, complete combined test chain and ARM64/Fold7.
- Lifecycle tests alone cannot certify the separate MCP evidence projection.

Frozen-contract coverage is deliberately narrower than full end-to-end release
acceptance. S01–S10 have concrete lifecycle/admission counterexamples, including
the above no-native active-child boundary. S11 covers idle crash rather than a
successful resumed queued computation. S12 (successful result written before
delivery interruption), S16 (successful verifier/receipt authority), and S17
(successful report continuity) need the separate real native/evidence proofs.
S13 covers changed source at admission and actual cancellation; it does not
measure an in-flight native patch under source mutation. S14 covers corrupt
inbox state, while reader/path attacks are owned by the MCP reviewer. S15 covers
idle/repeated lifecycle and global admission, not long-duration device resource
stress. S18 proves credential-free local control and no model calls in these
fixtures; genuine offline neural work is a separate acceptance requirement.

The earlier source-only concern about repeated retries of an invalid pending
grant was addressed before execution testing; no RED result is invented for
that observation. The later descendant/resource gap was actually reproduced,
and its distinct RED and GREEN runs are retained.

No original signature, owner data, main branch, installation or release changed.
Weekly quota: `NOT_VISIBLE`; work and raw logs are saved regularly in Draft PR #458.
