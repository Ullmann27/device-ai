# Completed memory connection — bounded #522 import

Baseline: `f4b0a403b4e51fc0b0df5d697345d6f94308af05`. Imported committed source only: local `61b961bfcd63cf572a06a7d5b31bf2fe622e7ae3` = remote `eadc72bac95f8b1a554a59e5baff624977b09f73`, tree `187aab3fe44a94b9b01451a867589c8e967cc641`. Source handoff: `docs/research/omega/MEMORY-BRAIN-R3-HANDOFF-20261104.md`.

## Changes

- Contradiction projections use the same snapshotted memory identities and privacy policy as retrieval. Forbidden/ambiguous sources are withheld; permitted evidence is marked untrusted.
- Memory mapper atomically coalesces stored metadata with its count and rechecks detachment between callbacks.
- Copied all three original regression files unchanged: 10 compiler tests and 4 mapper tests.
- Preserved baseline #523 running UTF-8 byte count and recall observer. Budget comparison remains `candidateBytes <= policy.maxBytes - usedBytes`; count advances only after an accepted append. Existing byte-budget tests remain unchanged.

## Live-path audit

Read-only inspection of `AgentActivity.kt` confirms the compiler already uses `withRecallObserver(ContinuumMemoryRecallPublisher(filesDir))`, passes the original `memories` together with contradicted-content projections, and subscribes `MemoryEventMapper` through `MemoryEventSources.forApp(filesDir)`, detaching it on cleanup. No Activity, factory, UI, AGENTS, or unrelated source changes. Caller annotations without a retained memory record remain unattributed untrusted evidence.

## Verification

- `git fetch --no-tags /workspace/scratch/624aeb26d481/continuum-brain-r3 61b961bfcd63cf572a06a7d5b31bf2fe622e7ae3`: PASS, exact committed source fetched.
- `git diff --check`: PASS.
- Python source-binding assertions: PASS; mapper and three tests equal source bytes; baseline compiler byte-budget block and observer equal preserved bytes; 14 new `@Test` declarations.
- Kotlin compile, JUnit, Gradle/Android gates: **NOT_RUN** here; parent owns serial combined-source validation. Prior source-branch results are not claimed for this integration.
- Old 462-case compiler parity: **NOT_RUN**; contradiction semantics intentionally change, so historical equality is not a blanket integration requirement.

## Blob provenance

| Path | Imported source blob | Integrated blob |
|---|---|---|
| `app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt` | `beb0f37d8de8965984bf38846b6479e0137d2340` | `2aab35194170710b216f81b6ec886521ca767fbe` |
| `app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt` | `42f4714c72ee057566be54171f01703f39d48aaf` | `42f4714c72ee057566be54171f01703f39d48aaf` |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryRegressionTest.kt` | `0c798951d4f19d0ac873330caf2c5b6ce6a29d99` | `0c798951d4f19d0ac873330caf2c5b6ce6a29d99` |
| `app/src/test/java/at/heinz/deviceai/cognitive/ContradictionBoundaryHoldoutTest.kt` | `8e0d89fee6ed05af7b32e4ca589c83fb008c5f8d` | `8e0d89fee6ed05af7b32e4ca589c83fb008c5f8d` |
| `app/src/test/java/at/heinz/deviceai/cockpit/MemoryEventMapperLifecycleRegressionTest.kt` | `b8803c074ad56c6b51d1b587818ae4bc5e1003dd` | `b8803c074ad56c6b51d1b587818ae4bc5e1003dd` |

Structured provenance and SHA-256 hashes: `source-binding.json`.
