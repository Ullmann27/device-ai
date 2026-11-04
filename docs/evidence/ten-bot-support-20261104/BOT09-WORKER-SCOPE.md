# Bot 09 — immutable worker tool scope

## Reproduction and repair

Bot 09 wrote six deterministic regression tests through the public pool and
adapter APIs. They cover adding and removing tools after queue admission and
after creation of a direct runner or model adapter. The queued tests use latches,
not sleeps, to ensure the mutation happens before execution.

On the original source, all six tests failed in a real offline Android Gradle
run (19 seconds). The coordinator confirmed the same six failures in a second
serialized run (11 seconds) after taking over an in-flight handoff. These are
six distinct cases, not twelve. RED logs and XML preserve both executions.
An earlier Bot 09 attempt expired at the shared build lock with exit 73 before
Gradle started.

The coordinator then applied the bounded repair in `ChiefWorkerPool.kt`:

- Pool admission copies the accepted tool set into an unmodifiable set before
  publishing the queued assignment.
- Direct model and runner adapters independently snapshot their tool sets at
  creation, so callers cannot subsequently widen or shrink those scopes.

The existing ToolGate, policy and approval wiring remain in place. Cancellation,
shutdown, capacity and report handling are unchanged. No permission or API is
added. The source/test hashes are recorded in the RED/GREEN source manifests.

## Verified result

The first GREEN attempt also expired at the shared lock (exit 73; no Gradle
execution). A subsequent attempt acquired the lock and completed successfully:

**37 tests passed; zero failures, errors or skips; exit 0; 24 seconds.**
Production Kotlin and test Kotlin compilation both ran. The count consists of
six new worker regressions and the previous 31 routine, dispatcher and pool
tests; the 31 include the 12 routine cases. Test-source and production-source
hashes matched the pre-run manifests after completion.

Raw evidence: `bot09-worker-scope-green.log`, `bot09-green-summary.json` and
`bot09-green-results/`. Independent bounded code review found no demonstrated
critical, important or minor issue; its scope is in `BOT10-INDEPENDENT-REVIEW.md`.

This is host JVM verification within the Android build. No APK, physical device,
native model, release or installation was exercised. Rollback is a normal revert
of this isolated worker repair and its tests; no foreign workstream is reset.
