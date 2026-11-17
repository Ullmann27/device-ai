# Native evidence index — JARVIS-MO closure

Final code SHA: `d1a5a215f42d6e7dbf08bc92eb8837653c1d119c`.
Final handoff: `docs/research/omega/SOL-JARVIS-FINAL-HANDOFF-20261026.md`.

- `final-full-node.json` / `.tap`: exact full native command, Node version, source,
  exit0, **1351 PASS / 0 FAIL / 7 SKIP**. Six skips require a kotlinc executable;
  one cross-language test runs separately below. Process/transport fixtures remain fixtures.
- `final-syntax.json`: **400** tracked Node ESM modules parse, zero errors.
- `jvm-final/`: real selected Kotlin source graph, **15 JUnit tests**, and actual
  JVM-produced JSON consumed by **9 Node tests**, zero skips. Compiler2.2.0 /
  Java21 / JVM target17; no Android Gradle/APK/device claim.
- `final-selected-verification.json`: source binding and actual restricted executor
  config. `abc-final/` contains five real RED→GREEN→fresh-GREEN arm records and one
  strict rank1 nonapplicable outcome, test identities, source snapshots and fixture
  Git bundles. Two public development tasks; untrained network; no learning.
- `abc-native/`: earlier native development checkpoint, retained separately.
- `executor-setup-attempts.json`: real aborted initial image setups; no valid tests,
  no positive/negative training labels.
- `initial-*`: original GitHub heads and initial native baseline, including real
  five test failures. `dependency-ancestry.json` distinguishes full ancestry from
  selectively integrated branches.
- `registry-*`, `fabric-*-red*`, `ccc1-*-red*`: native regression RED evidence.
- `ccc1-final-safety-green.tap`: final focused evidence/ledger/verifier checks,
  **31 PASS**. Signal/OOM cases are structured process-boundary test data, not
  claims of real OOM injection.
- `deepseek-integration-green.tap`: **40 PASS**, including real local TLS sockets
  with synthetic protocol replies; NOT DeepSeek inference.
- `android-worker-*.tap`: independent reviewer targeted lifecycle/authority tests;
  source-wiring assertions do not constitute Android execution.
- `unfinished-local-inference-preflight.json`: public toolchain preparation
  stopped at owner request. No model weights, real LLM call or paid resource.

Raw JUnit and hashes are not authenticated semantic receipts. All broad research
patches stay review-only; no productive weight or owner-memory promotion.
