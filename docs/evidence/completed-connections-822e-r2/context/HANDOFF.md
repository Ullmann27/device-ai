# Completed connection: context accepted-byte accounting

## Result and boundary

Integrates the retained R1 `AiContextCompiler.compile` / `appendSection` byte counter from PR #511 onto the C1-derived `3510c26110098b42d46bb3b7788027fa7aed59e4` checkout. Production diff is **5 additions, 1 deletion**, all in the compiler's local append accounting. The existing six-case `AiContextByteBudgetTest` is imported unchanged.

The counter resets for every compilation and increases only after accepting a complete sanitized section. Candidate sizes still use the original UTF-8 conversion. A rejected optional section spends no budget. Required over-budget sections retain their exception. ASCII section boundaries prevent surrogate pairing between appended sections, so summing accepted section byte sizes equals encoding the assembled buffer. This removes repeated encoding of the accumulated buffer without changing the section decision.

A mechanical reverse-patch comparison verifies that the **entire compiler outside this exact diff equals frozen C1**. Recall observer fields/factory/notifications, private-bot policy checks, overflow-safe untrusted text sizing, contradiction handling and redaction are preserved. `production.patch` is the exact production diff; `provenance.json` contains source hashes and preservation checks.

No R2 cached-regex/matcher work was imported. Contradiction privacy remains with the separate #522 owner. The known preexisting `authorization=Bearer ...` redaction edge is unchanged and outside this byte-accounting slice; passing these budget/parity checks is not an exhaustive privacy assertion.

## Source provenance

- Official repository: `Ullmann27/device-ai`.
- Frozen #511 remote commit: `73b64ec299fd314dfdf07835fc7d8fc0d74b51a2`.
- Read-only source checkout: `/workspace/scratch/59ad19c6d3b7/continuum-tuning`, local commit `2f20c19b7e37197a6accac0c226b5401e25f1f99`.
- Remote compiler blob `a2d41ffb0cff834764e4ee6180929905f04784a6` was checked through GitHub and equals the local source blob. [Pinned compiler](https://github.com/Ullmann27/device-ai/blob/73b64ec299fd314dfdf07835fc7d8fc0d74b51a2/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt).
- Frozen C1 baseline: `8873030fdfe23bdd4153f96004b786bff2cddecf`; same complete tree as remote C1 `1a5302c504a78a50d5e64ca727f2915529bf2b06`.
- Candidate compiler SHA-256: `6adf073f1e9b06c45c847ca46c379f295f5be0598ba7fd028b63993da30de02a`.
- `AiContextByteBudgetTest.kt` SHA-256: `093b3fd5bd554f3420b547c880085ee62e5752f69403bc5c9d85b828bd5d7e40`, unchanged from #511.
- Original parity oracle SHA-256: `b76a4fe0587291757af85be833cf6ed2a67815cd7f7765229194a89c542eb269`, unchanged from #511.
- Existing compiler, integrity and prompt-size tests plus `ContextIntegrityFixture` were already byte-identical to #511 and remain unchanged.

## Existing product callers

`AgentActivity` constructs the observed compiler at line 195 with a 10,500-byte policy and `ContinuumMemoryRecallPublisher`. Its local owner-chat path calls `contextCompiler.compile` at line 7821, appends `compiledContext.text` at line 7903 and enforces `requireCompleteIn(prompt)` at line 7944. Thus this local append implementation is on the existing actual prompt path.

`chief/ChiefBotConversation` injects the same compiler at line 104 with the existing private-bot policy (`maxBytes=8000`, zero memory/research limits, no tool catalog/identity details). It calls `compiler.compile` at line 147, places the result in the prompt at line 165 and checks completeness at line 185. Both caller files were mechanically checked unchanged from C1.

## Fresh verification

All results below come from the current candidate source, not historical #511 timing evidence. Full source/dependency SHA-256 hashes and exact compiler command are in `evidence/manifest.json` and `evidence/compile-command.json`. OpenJDK 21.0.13 and Kotlin 2.4.0 targeted JVM 17. The graph contains 47 real app/test files, the unchanged parity oracle and five generated baseline/test copies. No replacement source stubs were used. Android SDK classes are compile-time dependencies; Android framework execution was not performed.

| Check | Fresh result |
| --- | --- |
| Source provenance and full-file preservation outside the patch | PASS, exit 0 |
| First host compilation | Exit 1: two transitive real source files were missing from the standalone manifest; raw log retained |
| Final host compilation after including those real files | PASS, exit 0 |
| Baseline C1 JUnit: compiler, six-case byte budget, integrity, prompt-size/stability | PASS, 21 tests, exit 0 |
| Candidate JUnit: same four test classes | PASS, 21 tests, exit 0 |
| Unchanged exact parity oracle | PASS, 462 cases, exit 0 |
| Overall check runner | PASS, exit 0 |
| Android/Gradle tests | NOT_RUN here; parent coordinates combined-source Android tests |
| Device inference, benchmarks, control timing | NOT_RUN |

Parity covers 408 successful contexts and 54 identical exceptions, including 300 truncated contexts. It compares complete result data classes (text, ordered included IDs/URLs and truncation), UTF-8 bytes, budget bounds, exception class/message and clock call counts. The original cases include exact and adjacent byte boundaries, multibyte/combining characters, NULs, unpaired surrogates, rejected/blank sections, redacted inputs, required failure behavior, selection policies and deterministic random cases. Seed: `0x434F4E54455854`; output SHA-256: `a3d389027eaf618170e352ca9d974ff31902695e228cb38e4e7db594d35fb854`.

The first host compile failed because `Lex3LearningExperienceStore` and `OwnerMemoryRecovery` were absent from the old standalone graph. They and the other C1 recall transitive dependencies are now included unchanged. This is a harness dependency correction, not a production edit. Final compile warnings are retained in `evidence/compile.log`.

## Minimal harness adaptation

Copied `run.py`, `sources.txt` and `ContextCompilerParity.kt` from #511's `tools/tuning-memory-20261104`:

1. Changed comparison baseline from old #491 to frozen C1 `8873030...`.
2. Resolved repository root through Git from this evidence directory; source/oracle paths are local to this directory.
3. Renamed the baseline class **and its self-references**, preserving C1's observer factory type and constructor. The shared pre-class declarations must still be byte-identical. No comparison oracle logic changed.
4. Added seven existing real source dependencies required by C1's `RecallHit` type to the original 40-file source list.
5. Retained only the runner's `check` entry point; no benchmark/control was run. The copied Kotlin oracle itself is byte-identical and still contains its original uninvoked timing mode.

To reproduce, supply `CONTINUUM_KOTLIN_COMPILER_CP` and `CONTINUUM_KOTLIN_DEPENDENCY_CP` with the hashed jars listed in `evidence/manifest.json`, then run:

```sh
python3 docs/evidence/completed-connections-822e-r2/context/run.py check /absolute/path/outside/repository
```

The original run output/classes live outside Git at `/workspace/scratch/822e72396ed9/r2-context-proof`; only small evidence files are committed. Committed text logs omit trailing whitespace and trailing blank lines; the external raw logs retain the original output. This bounded host result supports byte-accounting parity. It makes no Android-device performance or full integration acceptance claim.

## Coordinator portability correction

The original helper execution above used local equivalent commit `8873030`.
The coordinator and independent reviewer identified that this commit is not part
of the API-published Git history. The replay runner now reads the exact immutable
baseline compiler blob `f42f526cefaa96295205f161241fbc8035f27c01` and identifies
its published C1 source as `1a5302c504a78a50d5e64ca727f2915529bf2b06`.
The baseline bytes and comparison oracle are unchanged. Use a complete checkout
containing this ancestor blob. The historical helper manifests remain untouched;
the coordinator's separate replay receipt records any newly executed run.
