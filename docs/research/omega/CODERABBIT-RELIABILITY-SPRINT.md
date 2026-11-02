<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:ff38b1348abcb402d9cdb1bd3f408446a975a89e3952e999d1ed20592bb7f2e5 -->

# Continuum reliability sprint — Codex handoff

## Source and scope

Base PR #351: `057474de37d6e20d993bd64f166b6585bc4a357a`, parent PR #349 `df3b6930695abe6ddffc1388c941aaba6122be5e`. Both were checked remotely before work; no upstream repair was present. New explicitly requested branch: `coderabbit/continuum-reliability-sprint-20261102`, Draft PR #353. Initial code checkpoint `a4d58a27`. EXP-00/02/08/11; shared supervision and research storage tests only. Ubuntu26.04.1, Node24.21.0/npm11.19.0. No dependency install or new credentials.

## Proven defects and repairs

1. **P0: loss of cleanup evidence.** Under synthetic denied `/proc/<pid>/stat`, `runOwnedCommand` correctly returned code0 with `terminationConfirmed:false` and `TERMINATION_UNCONFIRMED`; the watchdog forwarded success while a real detached child remained alive. The new private per-step `.completion.json` stores the actual command code, signal, error and confirmation, bound to the task. Supervision rejects unconfirmed success. The parent also rejects missing, malformed or mismatched completion records. Original child diagnostics, exit code and signal remain available separately from supervisor failure.
2. **P0: root PID reuse changes discovery floor.** Injecting a later start time for the vanished root PID caused the old implementation to advance its minimum process start time and miss a still-live detached child. Discovery now binds that floor once to the original child identity. Regression checks actual descendant termination and that neither the replacement PID nor its process group receives a signal. PID reuse is simulated through `/proc` reads; no real kernel PID reuse is claimed.
3. **P0: interrupted supervision becomes success.** A command that catches SIGTERM and exits0 previously caused a signalled watchdog to return0. It now returns failure and retains the actual command code0 plus ABORTED in the completion. Normal nonzero exits, ending signals and output passthrough still pass the original tests.
4. **P1: incomplete snapshots abandoned too early.** Two synthetic denied `/proc` directory scans caused immediate unconfirmed completion on normal exit. In cleanup, the existing quiet counter also counted incomplete scans as quiet. Cleanup now uses its existing bounded retry window and requires two complete quiet observations. Persistent access denial still returns unconfirmed failure within the tested bound. This strengthens confirmation; it never treats unreadable processes as dead.

The original watchdog header promised unconditional exit passthrough. The owner's reliability-sprint instruction explicitly authorizes the exception above. Confirmed natural outcomes preserve the original contract; unconfirmed or interrupted supervision blocks success. No owner-abort receipt keys were removed. One old test reuses a receipt for successive commands; its new completion sidecar is explicitly cleared between those commands, matching production's unique scratch directory per step.

## Storage findings

The historical `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED` has no proven original transport cause. Both the previous clean-baseline run and new isolated tests passed the versions/tombstone case. An added real HTTP endpoint deliberately withholds its response past a50ms adapter deadline: observed TimeoutError, unchanged catalog revision, absent failed object, then a queued healthy write commits using real replicas and survives restart. No production storage code was changed.

`run()` serializes catalog writes; all configured replicas must acknowledge before catalog commit. HMAC validation, private-owner-data exclusion and tombstone-before-delete remain covered. The pre-existing research contract permits encrypted orphan blocks after failed partial replication; all-or-none is the catalog visibility/commit guarantee, not physical rollback of every replica byte. See `SOL-STORAGE-FABRIC-20261027.md`. No claim of power-loss recovery, distributed consensus or elimination of those orphan blocks.

## Tests

All commands run from the repository root. Raw output and machine-readable summary are retained with the sprint evidence. Baseline results from the earlier turn are reused only where code/test/environment equivalence holds; new process/storage regressions invalidate their affected baseline coverage.

| Check | Observed result |
|---|---|
| Baseline historical expanded suite (previous turn, clean PR351) | 1301tests:1277PASS/11FAIL/13SKIP, exit1 |
| New watcher/profile regressions before fix | 5FAIL, exit1; includes positive control requiring the new completion field |
| Root-PID-reuse regression before fix | 1FAIL, exit1, detached child remained live |
| Supervisor-SIGTERM regression before fix | 1FAIL, exit1, watchdog returned0 |
| Incomplete-scan regressions before retry fix | 2FAIL, exit1 |
| `node --test .github/continuum/step-watchdog-reliability.test.mjs .github/continuum/step-watchdog.test.mjs scripts/continuum-owned-process.test.mjs .github/continuum/research-verifier-integration.test.mjs .github/continuum/teamleader-end-to-end.test.mjs` | 35PASS/0FAIL/0SKIP, exit0 |
| `node --test .github/continuum/storage-fabric.test.mjs .github/continuum/coding-agent-loop.test.mjs scripts/continuum-architecture-proof.test.mjs` | 32PASS/0FAIL/0SKIP, exit0; reused after later changes confined to process supervision |
| Final expanded affected suite | 1367tests:1343PASS/11FAIL/13SKIP, exit1; all new process/storage cases and previously load-sensitive consumers passed |

Final expanded command:

```sh
node --test scripts/continuum-architecture-proof.test.mjs scripts/continuum-owned-process.test.mjs scripts/continuum-owned-app*.test.mjs .github/continuum/*test.mjs
```

Earlier broad runs are preserved, not replaced by the final result. Before bounded retries, one run had1365tests/1339PASS/13FAIL/13SKIP:11 known source assertions plus research-verifier and scheduled-teamleader failures; those7 consumer tests passed isolated. A diagnostic preload run had1338PASS/14FAIL/13SKIP and recorded actual unconfirmed completion records. This supports the presence of transient/incomplete observations under load, but does not identify the original historical kernel/permission cause. An earlier intermediate run during source refinement had1340PASS/11FAIL/13SKIP and is not final input-identity evidence.

## Independent review and optional automation

The official installed CodeRabbit CLI0.9.0 was invoked using the code-review skill: `coderabbit review --agent -t uncommitted`. Its runtime wrapper returned that reviews are disabled for this task; no review or findings were produced. No login, credentials, paid APIs, Autofix activation or alternative reviewer was introduced. <!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:a9edf7c50343d46b4b641005e704e6e6360a02a89bcc50b3d4848959317b2c6f -->

Independent CodeRabbit acceptance remains blocked; this draft is not ready for integration.

<!-- /coderabbit-ai-generated:v1 --> The optional QA-tool/skill integration is deferred until this critical patch and remaining failed contracts have passed that gate. Existing Node commands remain directly usable by Codex.

## Remaining risks / not run

- Eleven unchanged source assertions remain open (not proven Android runtime defects):
  - persistent ALWAYS_ON is explicit, revocable and uses the existing web gate
  - activity request codes are unique and every one has a result handler
  - Wikipedia article research uses bounded authentic extracts and original citations
  - owner facts win, and retrieval is clearly labelled a non-verified excerpt
  - developer speech uses actual German Android recognizer and a unique callback
  - voice text is always previewed; neither recognition nor chat command executes code
  - original 3.66 GB model import remains separate and SHA-verified
  - Android only sends bounded question text to the private backend
  - a rejected import cannot relabel the independently loaded runtime as failed
  - Nexus keeps real hologram and voice entry tied to Android speech and system state
  - owner consent, cellular network and measured 20pct resource gates remain required

- Fail-closed cleanup can still reject an operation if complete `/proc` evidence cannot be obtained within the bounded window. No false success is allowed to compensate.
- `/proc` lifecycle tracking is not a sandbox for hostile tools; PID identity checks retain OS-level race limits without pidfd. Android/Termux SELinux and device lifecycle remain untested.
- No Gradle, Android SDK, Fold7/device, power-loss or disk-full crash-injection test was performed. Thirteen existing suite skips remain explicit.
- No assertion was removed to force a green total, and no broad-suite pass is claimed.

## Integration and next Codex task

1. Fetch PR353 and verify its exact HEAD and parent PR351; do not replace the installed owner release or merge the stacked chain blindly.
2. Arrange the blocked independent CodeRabbit review and address findings before integration.
3. Re-run the final expanded command after any runtime change. For the11 source failures, map each original invariant to its current Kotlin caller and native tests; change assertions only after proving an equivalent implementation. Preserve consent, ToolGate, evidence labelling and voice preview.
4. Exercise denied/transient `/proc`, detached descendants, cancellation and supervision on the actual Linux/Termux target. Preserve completion records when diagnosing unconfirmed results.
5. Investigate storage with fault injection; retain original replica error classes without leaking tokens or content. Do not add retries or weaken full-replication gates without measured cause.
6. Rollback: revert only this sprint's commits on the isolated branch. No production state or schema migration is involved.


<!-- /coderabbit-ai-generated:v1 -->
