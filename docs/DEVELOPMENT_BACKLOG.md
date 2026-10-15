# Persistent development backlog (Node ↔ Kotlin)

One backlog format and one set of state rules for every writer: the Node developer
agent and CLI (`.github/continuum/development-backlog.mjs`) and the Android app
(`app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentBacklog.kt` +
`DevelopmentBacklogStore.kt`). It extends the existing development pipeline:
`code-analyzer` findings (ranked by `development-planner.mjs`) and repair-loop
sessions become backlog tasks, and Android `DevelopmentCoordinator` jobs are
recorded as backlog tasks instead of being lost on restart.

## Document

* Canonical JSON: object keys sorted (UTF-16 order), no whitespace, **integers
  only** (|n| ≤ 2^53−1; floats are rejected because JS and the JVM print them
  differently), strings escaped exactly like `JSON.stringify`, UTF-8 without BOM,
  one trailing newline. Same state ⇒ same bytes on both platforms.
* `integrity` = SHA-256 of the canonical document without that field. It detects
  corruption and partial writes. **It is not a signature** and proves nothing
  about who wrote the file.
* Strict reading on both sides: invalid UTF-8, BOM, duplicate keys, floats, lone
  surrogates, trailing data, unknown states, dependency cycles, unsorted tasks
  ⇒ `CORRUPT`; hash mismatch ⇒ `INTEGRITY_MISMATCH`; `schemaVersion` > 2 ⇒
  `UNSUPPORTED_VERSION` (never overwritten, never "recovered" away).
* Unknown fields (top level and inside tasks) are preserved through every
  operation and rewrite (forward compatibility).
* `schemaVersion` 1 (the earlier `development-planner` snapshot) is migrated
  explicitly: every finding becomes a `PROPOSED` `INVESTIGATE` task with a
  deterministic id `inv-<sha256(kind,file,symbol)[0:16]>`; all planner fields are
  kept in `subject`/`migratedFrom`; the original file is kept as `<file>.v1.json`.

## Task states

```
PROPOSED ─accept─▶ READY ─claim/claimNext─▶ IN_PROGRESS ─submit─▶ VERIFYING ─complete(evidence)─▶ DONE
    │                │  ▲                      │    │                  │
    │                │  └── fail / expire ─────┘    └── fail ──────────┤ (attempts < maxAttempts)
    │                │                         FAILED ◀── fail/expire ─┘ (budget exhausted) ─retry─▶ READY
    │                └─ (dependency FAILED/ABORTED/REJECTED) ⇄ BLOCKED      (automatic)
    └─reject─▶ REJECTED        any non-terminal ─abort─▶ ABORTED          DONE/ABORTED/REJECTED are final
```

| Operation | Rule |
|---|---|
| `propose` | new id (`[a-z][a-z0-9-]{2,63}`), title 1..200 chars without control characters, dependencies must exist; `ifAbsent` makes re-proposing idempotent |
| `claim` / `claimNext` | only `READY`, all dependencies `DONE`, `attempts < maxAttempts`; sets a lease (1 min .. 24 h) and a 32-hex token; `claimNext` = highest priority, then oldest, then id; optional `kinds` filter |
| `renew` / `submit` | only with the claim token and before the lease ends; `submit` records the artifact (`PATCH`/`PROPOSAL`/`REPORT` + SHA-256) |
| `complete` | only `VERIFYING`, only with evidence that names the task, the submission token and the submitted artifact hash, is not older than the submission, has every check passed (`failed = 0`), at least one executed test, and `executed > 0` for every test profile (`node-continuum`, `android-unit`, `jvm-harness`) |
| `fail` | with the claim or submission token; back to `READY` while budget remains, otherwise `FAILED`; optional (failing) evidence is kept |
| `expire` | every `IN_PROGRESS` task whose lease ended returns to `READY`/`FAILED` with `LEASE_EXPIRED` (crash recovery, resumable work) |
| `retry` | `FAILED` → `READY`, budget reset (a human decision) |
| `reject` / `abort` / `setPriority` | explicit reasons; terminal tasks cannot change |
| any operation with `opId` | applied at most once: the last 256 applied ids are kept in `recentOpIds`; a repeated id is a reported no-op (`duplicate`). This makes operation logs (CI re-runs, re-imported exports from the phone) exactly-once within that window |

The DONE rule is enforced **twice**: by the `complete` operation and on every
load (a file whose DONE task lacks matching evidence is `CORRUPT`). History is
bounded to 40 entries and evidence to 8 per task; drops are counted
(`historyDropped`, `evidenceDropped`), never silent. Every rejected operation
returns a stable error code (`INVALID_TRANSITION`, `TOKEN_MISMATCH`,
`LEASE_EXPIRED`, `EVIDENCE_REJECTED`, `BUDGET_EXHAUSTED`, `DEPENDENCY_NOT_DONE`,
`NO_ELIGIBLE_TASK`, `DUPLICATE_ID`, `UNKNOWN_DEPENDENCY`, `NOT_FOUND`,
`INVALID_FIELD`, `INVALID_OP`, `LIMIT`).

## Store guarantees (both platforms)

* Cross-process lock `<file>.lock` (exclusive create). A lock older than 60 s is
  moved aside under a unique name and broken; if what was moved turns out to be
  fresh it is put back. Waiting is bounded (`LOCK_TIMEOUT`).
* Read-modify-write is a compare-and-swap: before the rename the file must still
  hold the bytes that were read, else `CONFLICT` and nothing is written; callers
  may also pass `expectedRevision`.
* Atomic replace: synced temp file + rename (+ directory sync where supported).
  The previous version is kept as `<file>.bak`. An interrupted write leaves only
  a temp file, which is never read and is removed later.
* A corrupt file is recovered from `<file>.bak` and quarantined as
  `<file>.corrupt-*` (reported in the result), never deleted; without a valid
  backup loading fails closed.
* Batches are all-or-nothing by default; the lenient mode reports every rejected
  operation with its code.

## Who writes what

| Writer | How |
|---|---|
| Analyzer findings | `syncBacklogOps` (`development-planner.mjs`): new `DEAD_CLUSTER`/`TEST_ONLY`/`UNREFERENCED` findings become `PROPOSED` `INVESTIGATE` tasks (at most 12 new per run, highest priority first); vanished findings withdraw only untouched proposals; accepted, rejected or aborted tasks are never changed by the planner |
| Repair loop | `repairSessionOps`: one `REPAIR` task per failure identity (`rep-` + hash of chain and diagnostic fingerprints); a session is recorded as propose → accept → claim → submit (patch hash) → complete with the evidence of the very tests that verified the patch, or → fail. Every step has an `opId`, so recording a session twice changes nothing. A task that is DONE, FAILED (budget), REJECTED or ABORTED is not touched, and `publish` opens no further draft PR for it |
| CI | `backlog-save` (publish jobs, write token) on branch `continuum-agent-state`: `expire`, repair sessions, planner sync; compare-and-swap on the branch head. `watch` reads ledger and backlog through the API and fails instead of assuming empty state when they are unreadable |
| Humans / other agents | `node .github/continuum/developer-agent.mjs backlog show|plan|accept|reject|abort|retry|priority|claim|renew|submit|verify|fail|expire|apply --file <backlog.json>`; `verify` is the only producer of test evidence: it checks that the patch is the submitted artifact (SHA-256), applies it to `--base` in an isolated worktree and runs a fixed check chain |
| Android app | `DevelopmentBacklogStore` in app storage; see `AgentActivity` (development backlog) |

## Verification

| What | Where |
|---|---|
| Shared conformance vectors (every operation and error code, bounded history/evidence) | `app/src/test/resources/continuum/backlog/conformance.json`, run by `development-backlog.test.mjs` and `DevelopmentBacklogTest.kt` |
| Byte-identical encoding, invalid documents, v1 migration | same fixture directory (`valid-v2.json`, `unknown-fields.json`, `invalid*.json`, `v1-*.json`) |
| Store: backup, quarantine, fail-closed, CAS conflicts, stale/fresh locks, interrupted writes | both test suites |
| Concurrency without lost updates | 5 Node writer **processes**; 6 Kotlin writer threads with separate stores |
| Real cross-runtime round trip | CI (`android.yml`): `backlog-fixtures.mjs roundtrip-prepare` (Node writes) → `DevelopmentBacklogRoundtripTest` inside `:app:testDebugUnitTest` (Kotlin verifies, changes through its store, writes an operation log) → `roundtrip-verify` (Node checks bytes, revision, states, unknown fields, UTF-8, no lock/temp leftovers, applies the Kotlin operation log) |

Regenerate fixtures only after an intentional rule or format change:
`node .github/continuum/backlog-fixtures.mjs write` (the Node test fails while
committed fixtures are stale; the Kotlin test then checks the new expectations).

## Honest limits

* The integrity hash is not authentication. Anyone who can write the file can
  forge a consistent document, including evidence; trust comes from where the
  file lives (the agent state branch is written only by the CI publish job).
* File locks are advisory. Mutual exclusion holds unless a holder exceeds the
  60 s stale threshold; the compare-and-swap then turns a lost race into an
  explicit `CONFLICT` instead of a lost update (the remaining window is the
  instant between the final check and the rename).
* Evidence produced on Android is impossible by design: the app cannot run the
  test suites, so app-side work stops at `VERIFYING` until CI or a developer
  records real test evidence.
