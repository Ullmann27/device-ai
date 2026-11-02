# CONTINUUM – Critical QA failure investigation (2 November 2026)

**Pinned parent:** PR #349 HEAD `df3b6930695abe6ddffc1388c941aaba6122be5e`.
**Isolated remediation:** `research/continuum-critical-qa-repair-20261102`.
**Canonical source:** `docs/evidence/grand-challenge-20261102/node.tap`, an **earlier expanded Node run** with 1,301 cases / 1,272 passes / 16 failures / 13 skips; two obsolete structural contracts were repaired in PR349 after this transcript. **These counts do not describe an independently rerun post-repair suite.**

## Separate current Android acceptance
Existing Render deploy `dep-dbk4b1lh3lcs738rgga0` on **exact** `df3b6930` reported `CONTINUUM_ARCHITECTURE_PROOF=PASS` and `CONTINUUM_M8_RENDER_QA=PASS` on 2026-11-02 08:26 UTC; Android Gradle compilation/tests/lint completed, unsigned QA artifacts only. **Not a Fold7/device test, no signer or published owner APK.** This does **not** turn historical expanded Node 16 failures green.

## 16 failures: retain exactly until each can be reproduced or disproved
1. persistent ALWAYS_ON is explicit, revocable and uses the existing web gate
2. activity request codes are unique and every one has a result handler
3. Wikipedia article research uses bounded authentic extracts and original citations
4. owner facts win, and retrieval is clearly labelled a non-verified excerpt
5. a real failing test after the first candidate reaches the next revision, which corrects it (simulated brain)
6. developer speech uses actual German Android recognizer and a unique callback
7. voice text is always previewed; neither recognition nor chat command executes code
8. Android source routes bootstrap actions through the existing cognitive agent (source wiring only)
9. context is bounded, clearly untrusted, and never authorizes tools or factual memories
10. original 3.66 GB model import remains separate and SHA-verified
11. Android only sends bounded question text to the private backend
12. a rejected import cannot relabel the independently loaded runtime as failed
13. Nexus keeps real hologram and voice entry tied to Android speech and system state
14. owner consent, cellular network and measured 20pct resource gates remain required
15. an owner abort ends a running step and its detached grandchild within seconds
16. versions survive restart; tombstone commits before unavailable replica deletion and reconciles recovery

## Verified root cause, bounded fix
- Failure #5: The autonomous coding-loop revision-recovery test required `Expected values to be strictly equal:-5 !== 5` (flattened diagnostics). The runtime deliberately emits the original multiline Node assertion: `Expected values to be strictly equal:\n\n-5 !== 5`. The full transcript contains the actual failing data, expected/actual values and previous FAILED_TESTS history.
- Changed **only** `.github/continuum/coding-agent-loop.test.mjs` to accept whitespace **between the colon and exact negative result**, retaining the task identifier, source test path, line number and expected actual values. A mismatching value such as `6 !== 5` still fails. No runtime repair logic modified or negative safety assertion removed.
- Verified the replacement pattern against a transcript-shaped positive sample and a wrong-value negative sample using JavaScript RegExp locally through the connector. **The complete actual Node test with real repository checkout has NOT_RUN**; do not count a successful independent test run yet.

## P0 / P1 follow-up – no silent test weakening
- P0 **Process cancellation** (#15): `.github/continuum/step-watchdog.test.mjs` expects owner abort to terminate both the owned subprocess and detached grandchild and mark termination confirmed. The recorded failure `false !== true` must be reproduced in isolation and under load, with actual `/proc` process-state proof. Never silently omit this assertion or declare it merely a timing flake.
- P0 **Durable storage replication** (#16): `.github/continuum/storage-fabric-catalog.mjs` failed at `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED` during initial `put`; a real network/process transient vs. nondeterministic fixture failure remains UNDETERMINED. Retest at baseline and exact HEAD, preserve full-replication and tombstone-before-delete invariants.
- P1 **Android/source policy contract drift** (#1–4, #6–14): majority involve source-string patterns against an evolved API, but do not automatically label all obsolete. Map each assertion to current Kotlin call graph, verify native tests and ToolGate behavior before updating an assertion. The two source-contract fixes in parent PR349 cover some but are not independently rerun across all 1,301 tests.
- Never change main, publish an APK, disable ToolGate, mutate the owner's saved knowledge or promote model weights. Keep the critical-QA PR draft until independently tested.

## Required independent replay
`node --test .github/continuum/coding-agent-loop.test.mjs` (on full repository checkout).
`node --test .github/continuum/step-watchdog.test.mjs .github/continuum/storage-fabric.test.mjs`.
`node --test scripts/continuum-architecture-proof.test.mjs .github/continuum/*test.mjs` (historical extended suite).

Report each exit code, exact ref, fully executed counts and stderr. Only after investigating the two P0 failures should new Android actions be added; owner-approved Fold7 functional test remains separate.
