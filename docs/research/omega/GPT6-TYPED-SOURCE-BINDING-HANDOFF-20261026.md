# Continuum integration continuation — source-bound Android/Termux coding receipts

Status: START_CHECKPOINT / CODE_NOT_YET_CHANGED.
Date: 2026-10-26.
Branch: `integration/continuum-typed-source-identity-20261026`; exact parent `bc424dfe208e2b06ae3c12a4adf7abb02ec79b05` (PR160, descendant of Astra PR159). No merge to main, no owner APK signing or installation, no weights or private memory changes.

## Focused end-to-end connection
Already implemented path: Android `AgentActivity` reviewed typed task → `ContinuumTypedCodingBridge` → `ContinuumMobileBuilderActivity` one-shot Termux `RUN_COMMAND` → `scripts/continuum-typed-coding-bridge.mjs` → canonical `coding-orchestrator.mjs`/CCC1/backlog → `repair-loop.mjs`/isolated executor → `ContinuumMobileBuildResultReceiver` → Android review-only status. The worker remains blocked without owner-provisioned OS isolation.

## Reproduced gap from exact production sources
`ContinuumTypedCodingBridge.evaluate` checks response `sourceCommit` only against the **syntax** of a forty-hex Git SHA, not against the source commit embedded in the originally approved task. Android persisted the pending/prepared task ID and SHA256 but not the commit as a separate expected property. A substitute Termux receipt carrying the same task ID/SHA256 but a different well-formed `sourceCommit` could return `PREPARED_NOT_EXECUTED` or `PATCH_PENDING_OWNER_REVIEW` despite the source mismatch. This is an identity/authorization reporting gap, not evidence of a reachable Android attacker or known exploitation.

## Intended minimal integration
- Carry the approved full source commit as a separate, private-preferences-bound property through PREPARE and RUN and validate it against returned sourceCommit for all success statuses.
- Keep one canonical job, one existing Termux channel and existing one-shot callback. No new worker/API/permissions.
- Negative Kotlin tests for wrong source commit, stale/missing preferred state, changed task SHA, and no approval/installation.
- Preserve blocked/aborted outcomes; migration is fail closed if legacy prepared-state has no commit, requiring explicit owner re-prepare.
- Android JUnit/Gradle/toolchain/full phone verification remain UNAVAILABLE unless actually run on exact new source; no APK accepted.

Experiments: EXP-00, EXP-02, EXP-08, EXP-11, EXP-12. Rollback: revert only source-binding commits in this Draft PR; other branches remain unchanged.

## Implemented and published in GitHub Draft PR #161

**Scope:** strict commit binding on the already-existing Android→Termux→CCC1→review-only callback, not a new executor.

Exact files and pinned Git blob identities:
- `app/src/main/java/at/heinz/deviceai/ContinuumTypedCodingBridge.kt`: `f7b9d421b36ff876015922c15bc50abf5f4b6c50`. The callback evaluator now REQUIRES `expectedSourceCommit` and compares the full 40-digit source to Termux's reported full source for any successful PREPARE/RUN result.
- `app/src/main/java/at/heinz/deviceai/ContinuumMobileBuildResultReceiver.kt`: `bb4be3fc7b4b82ba48de5711c9b6de9cc38abe0d`. Persists independent `TYPED_EXPECTED_COMMIT` during a pending job; only an accepted PREPARE stores `TYPED_PREPARED_COMMIT` along with the existing ID/SHA. Clears all expected fields on callback and clears all prepared fields when PREPARE fails. No raw Termux output, source or model text is persisted.
- `app/src/main/java/at/heinz/deviceai/ContinuumMobileBuilderActivity.kt`: `1d68e78ed6f99a87f6b3d6ddf758734f70a10ade`. PREPARE obtains the source from the already reviewed, hash-bound owner Authorization. RUN requires source from a prepared private state. The confirmation names the selected source commit, and stale/cancelled pending requests clear the expected commit. Legacy prepared records with no source commit are disabled until a new explicit owner PREPARE.
- `app/src/test/java/at/heinz/deviceai/ContinuumTypedCodingBridgeTest.kt`: latest negative-test blob `5a36f8b4dd3f9bb4a02e1394cad1e1e1d19614b5`. Existing tests use an explicit expected source; new regression tests reject a different, syntactically valid Git commit for BOTH PREPARE and RUN, a forged expected commit, malformed expected commit and all spurious success flags.
- `.github/continuum/typed-android-coding-wiring.test.mjs`: new static cross-source test exercises reviewed Android draft → prepared private identity → Termux launch → current Node typed bridge fingerprint/source binding → canonical CCC1 grant checkout source HEAD → review-only Android callback. Blob `e2ba7bea68078e8340622ce4dfc82b89612ae182`. No CLI/shell permission or duplicate transport added.

**Independent verification actually executed:** The full new static Node test callback was extracted unchanged from the exact GitHub file blob and executed under the V8 JavaScript isolate against the exact seven GitHub-fetched source file bodies. **1/1 PASS**, no source-wiring assertion failed. Separately, the amended Kotlin raw-string fixture syntax was compiled locally using kotlinc-jvm 1.9.0 (only those isolated test literal expressions; not the Android module).

**Required next tests still NOT_RUN:** The updated Kotlin Android classes and JUnit suite `ContinuumTypedCodingBridgeTest`, Android lint, standard `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin`, same-device Termux callback and independent device APK. Android SDK unavailable in this assistant's isolated environment; GitHub-hosted Actions were previously failing before execution. Do not mark DEVICE_TOOLCHAIN_VERIFIED or REAL_APK_BUILT for this source.

### Review sequence and operational compatibility
1. On the authorized Android SDK checkout:
   `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.ContinuumTypedCodingBridgeTest'`
   and `node --test .github/continuum/typed-android-coding-wiring.test.mjs`.
2. Run the existing `ContinuumMobileBuilderRouteTest` and `:app:lintDebug`, then the complete Android unit and Node suites on a single exact SHA.
3. On Fold7 only with user-granted Termux `RUN_COMMAND` and existing local isolation, prepare a reviewed task, run a safe bounded negative/positive fixture and verify the callback agrees with the reviewed source and fingerprint. Generated code must NOT execute when isolation capability is not independently established.
4. Any result remains owner-review only; no code from experiments is promoted, no main merge, no signed APK/installation, no owner-memory/weights write.

**Rollback:** Revert PR161 source-binding commits in its review branch if compatibility breaks, retaining the old verified PR160/PR159 heads. Never reset either source branch or `main`.
