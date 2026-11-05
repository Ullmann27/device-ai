# MEMORY R13 — temporal lexical boundaries and consistent relative-day cues

5 November 2026. Isolated specialist work stacked **only** on R12 [#548](https://github.com/Ullmann27/device-ai/pull/548) HEAD `e8102654a34d435eee115643b414158cd34c6f1d`. C1 [#519](https://github.com/Ullmann27/device-ai/pull/519) is sole integrator; its central board, #522 and R12 handoff were read before writing. At intake GitHub branch/PR search found no named R13 branch/PR; an invisible concurrent chat process cannot be ruled out.

## Three observed failures in the existing temporal retrieval path

The existing `GermanText.numericEvidenceTerms` already drops `vor\s+N\s+tagen` as a time cue, but `TemporalCue.window` previously accepted only **single spaces**. A query such as `vor   2   Tagen` or `vor\t2\nTagen` therefore lost the number as factual evidence *without* activating the intended time window. Also, a substring match treated `zuvor 2 tagen`, `bevor 2 tagen` and `vor 2 tagenlos` as temporal cues. Finally, plain substring searches for `gestern` / `vorgestern` misclassified `gesternlich` and `vorgesternlich`.

The correction uses explicit word boundaries for `vor N Tagen`, permits flexible whitespace and requires standalone `vorgestern`, `gestern`, `heute` cues. Non-numeric cues, calendar arithmetic, DST boundaries, temporal weights, BM25/n-gram scoring, index generations and `confirmed` provenance are otherwise unchanged.

## Test-first and exact production identity

- Frozen Android-JUnit regressions **before** production: `fbfbd26712bbcda5df61426107fe3a5b2c4cbfca` (8 methods) and `e5956a58c4e297bd5ed155025f9e4b057ab3ea82` (4 methods).
- One source file changed by production commit **`33a0dacdaf454a787358127a0d775788857ccf8f`**: `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`.
- R12 baseline Git blob `dcaeed0b13eb3f4f88128e45c250d6d84a319c72` → R13 Git blob `8f595df9783b74e10d6b702e12b5078184a91856`. Direct remote comparison confirms **exactly four expression replacements, no other byte changed in the entire 234-line source**.
- The actual `TemporalCue` class body extracted from the original and patched GitHub sources was fingerprinted (FNV32 original `d7bf1612`, patched `cc26bf5b`); the local Kotlin/JVM host copies matched those exact class fragments. This does **not** certify full-project Kotlin dependency compilation.

## Real bounded host execution

Host: `kotlinc-jvm 1.9.0`, OpenJDK `21.0.12.1`. The checked production `TemporalCue` method/class body was compiled as Kotlin with limited `GermanText.normalize/rawTokens/numericEvidenceTerms` adapters; no Android Activity/event bus, full retriever or Gradle. The 20 assertions cover the 12 frozen JUnit methods' conditions expanded into independent boolean checks.

| Source | Compile | Host cases | Runtime exit | Log SHA256 |
| --- | --- | --- | --- | --- |
| R12 baseline | Exit 0 | **13 PASS / 7 FAIL** | 1 | `31b7a28066c7530e862d70b280545c4c7e3993526b8848f0b31c91e8876ecb9e` |
| R13 candidate | Exit 0 | **20 PASS / 0 FAIL** | 0 | `1390c7fce519fd116b8baf23d52ab173d4f7dc73b4dbe862501b7f62543aab05` |

Original raw receipts: [baseline20.txt](../../evidence/memory-r13-temporal-20261105/baseline20.txt), [patched20.txt](../../evidence/memory-r13-temporal-20261105/patched20.txt). Two previous R12 numeric tests were not re-run in this host check. The frozen **12 Android JUnit methods are NOT_RUN**. Full Android Gradle/JUnit, 1,000-document/44-question M2.9 holdout, Node/backend, full common tests, NewApi lint, actual model/AV/GPU, Fold7 and APK are also **NOT_RUN**. In the container, `git ls-remote https://github.com/Ullmann27/device-ai.git HEAD` failed with `Could not resolve host: github.com`; GitHub connector provided source reading and repository writes, not a local project checkout. No substitute Android demo is claimed.

**Source/CI caution:** Compound words (`gesternabend`, etc.) previously matched by substring but no longer count as standalone day words. That boundary change needs C1's independent temporal-quality judgment and the existing 44-question holdout; do not promote based solely on host cases.

## Sequencing, reservations and protection

GitHub searches observed no competing R13 named branch; #522's compiler privacy and mapper fixes, #529's privacy work and existing C1 reservations were left intact. R13 changes neither C1's common Activity/voice/factory/worker entries nor model weights, package, signing, owner files, offline switch or update channel. No buildlocks were acquired; no heavy builds started. No paid services, GitHub Actions, main merge, release, signing, APK or installation.

C1 should intake **R6 → R7 → R8 → R9 → R10 → R11 → R12 → R13**, once each, on an exact common SHA; run the 12 frozen project JUnit tests plus historic TemporalCue/R8–R12 and the full M2.9 holdout. Reject if quality, privacy or performance regress. External C2 / Fold7 release remain separate gates.

[Machine-readable continuation](MEMORY-R13-STATE-20261105.json).
