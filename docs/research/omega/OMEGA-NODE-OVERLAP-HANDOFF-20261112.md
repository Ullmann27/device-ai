# Code54 Node overlap fixture — 12 November 2026

Canonical integrator: root, PR #769, `integrate/omega-apk54-20261111`.
Helper base: `921cc382a1510ae5cf047aafa82900a945def731`.
Relevant contracts: EXP-07/EXP-11. Only the existing MCP observer test changes;
no new runtime interface, authority, worker, production limit or device behavior.

## Current audit

The entire `.github/continuum`, `scripts` and `backend` source diff from the
last complete Node source `5b935b4f` to base `921cc382` is empty. Its complete
354-file result remains **2455 PASS / 6 FAIL / 37 SKIP**, Node24.22.1, uid1000.
The later `f0120e1b` contract-only run used Node22.23.4/uid0 and recorded
**1588 PASS / 26 FAIL / 23 SKIP**. Its summary identifies the same 26 names
as its Code53 baseline and explicitly does not recheck the six full-profile
failures. These are different profiles; neither result means full Node PASS.
The root isolation refusal is an intended guard, not an app regression.

The six full-profile failures remain documented in
`docs/evidence/omega-apk54-20261111/node-serial-5b935b4f/failures.json`.
The read-only triage in its sibling `node-serial-triage/README.md` remains valid:
child setup/probe failures did not establish failed process cleanup or a common
root cause. No new application regression was proved by this audit.

Recent MCP/overlap PR search found no concrete alternative fix. The known
Code55 source `caba9c83d13a9dc25b74da5ef9a4c0317ef5e7c8` retains the same
MCP fixture (Git blob `bb289d9821a8cc529a320eff520cfecaf59fcaa7`).
This bounded check is not a full audit of every historical branch.

## Specific defect and repair

The original fixture waited 10ms before expecting the second request to receive
429, while the first request had a 40ms timeout. It did not establish entry or
continued overlap. A valid delayed second request arrives after cleanup and
correctly receives HTTP200, making that assertion fail.

The original real 40ms timeout, oversized-result and oversized-body checks stay
in a separate test. The overlap test uses the actual loopback HTTP server and
waits for the first adapter call to enter. Only that test controls `setTimeout`,
after real session setup. It proves HTTP429 with `MCP_READ_BUDGET`, exactly one
reader call, no `listTasks` dispatch, and one active read until the unchanged
timer reaches 40ms. At 39ms the AbortSignal is clear; the next 1ms triggers the
real production callback, an error response, one timeout and zero active reads.
Cleanup restores timers, closes the server and settles the pending HTTP call.
All production bytes and limits are unchanged.

## Actual evidence

`docs/evidence/omega-node-overlap-20261112/` contains raw logs, timestamps,
commands, environment, exact source hashes and a checksum manifest.

| Run | Result | Meaning |
|---|---|---|
| Unmodified baseline file | 12 PASS / 0 FAIL / 0 SKIP | Original fixture can pass normally |
| Deliberately delayed second request | 0 PASS / 1 FAIL / 0 SKIP | Unmodified assertion reproduces `200 !== 429` after actual timeout |
| Entire repaired test file | 13 PASS / 0 FAIL / 0 SKIP | Real timeout and deterministic overlap both pass |

The delayed control changes only client dispatch ordering with the included
preload. It is a controlled valid interleaving, not an uninstrumented baseline
failure and not a changed server. Replay it against the base checkout using:

```sh
node --import /absolute/path/to/delayed-second-request.mjs --test \
  --test-name-pattern='timeouts, oversized results/body and per-session parallel reads fail closed' \
  .github/continuum/coding-mcp.test.mjs
```

On the repaired source, the focused verification command is:

```sh
node --test --test-concurrency=1 .github/continuum/coding-mcp.test.mjs
```

Independent read-only source/evidence review by `/root/pdf_host`: no blocker.
Test SHA256 `14018260eed046f424d95e110b63b12f0b0c4d087990f7eac82b2024864f3f9f`;
unchanged server SHA256
`4a3db639300c6a9645d6a732e78ea6aaa44fe36eee9cacb9cdaab55e4b6f8547`.
The run receipt's HEAD is the base; the changed test is bound by its byte hash.

## Integration and limits

Root integrates this helper commit into PR #769 and runs the focused command on
the integrated source. The other five old full-profile failures remain open;
no complete Node, Android, APK, native-PDF or Fold7 result is inferred here.
The existing full runner is
`docs/evidence/omega-apk54-20261111/harness/full-node-serial.mjs` with
`--repo PATH --expected-sha SHA --out NEW_DIRECTORY`. It retains the 354-file
inventory and all original limits; a full rerun is a separate integrator task.
Rollback is a revert of this test/evidence-only helper commit.
