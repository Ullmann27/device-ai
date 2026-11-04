# TUNING-BOT 1 — round 2 handoff, 2026-11-04

**Status: `RETURNED_TO_R1 / CANDIDATE_NOT_ADOPTED`. Experiment EXP-05.**

## Basis and preserved scope

- Published R1 baseline: `04b46f125d1086e6ea540d3c68700407f340f607`; supplied local baseline: `db3295d51c769afdaf809ac1dfae2ada84a3274f`; common verified tree `d753188fe0e67f6c6f098db6b7073ea199e05530`.
- Own branch `tuning/inference-r2-59ad`. Root publishes the reviewed evidence package through #511; this bot does not push or merge.
- Active `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt` is exactly R1 again, SHA-256 `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d`. No active production file change remains.
- One tested candidate replaced boxed weight storage with `DoubleArray`, preserving the existing RNG/sum/subtraction expressions. Its exact full source and minimal patch are archived, candidate SHA-256 `c329c9d78d28cbcccbd94671eed4f009a6c8c95a5ba69251c07c05bd845d4448`.
- No model/training/tokenizer/format, Runtime integration, gates, worker/memory path or offline-switch change.

## Result and Root decision

Full [results, archived source and raw evidence](../../evidence/three-tuning-bots-r2-20261104/inference/RESULTS.md).

Both measured versions passed the same 10 JUnit methods. Bytecode confirms unchanged greedy selection, removed Double boxing and the same summation order. Both complete JVM processes retain all 18 scenarios × nine A/B pairs, with matching token/RNG checksums. A single startup Perfdata-lock warning is preserved in the AFTER raw output and separately recorded, not silently stripped.

Allocation traffic decreased in all ten target cases: for example V512/k50, 15,824.055 → 14,800.055 bytes/call; V8192/k8192, 534,105.438 → 370,209.438 bytes/call. All eight greedy controls retained equal allocations.

The final target-case CPU medians were all higher, with +8.63%, +11.63% and +8.97% at full top-k. The BEFORE identical-algorithm control also has meaningful timing variability. Root explicitly decided that the allocation benefit is useful evidence but does not establish sufficient overall benefit to adopt the candidate. This is not a proven device regression. There was only one candidate and exactly the two declared timing processes; no favorable-number search or extra tuning followed.

## Reproduction and next action

The versioned runner under `docs/evidence/three-tuning-bots-r2-20261104/inference/` resolves the public R1 commit first, uses the local commit only when the public object is absent, and enforces the common tree. BEFORE extracts exact baseline sources. AFTER uses those same sources plus **the archived candidate**, with every source hash verified; it never benchmarks the restored active method under a candidate label.

The actual test/timing source receipts are preserved unchanged. Final source-resolution/archival adjustments were statically checked; Root will execute the final reproduction route independently. No further JVM or timing work was started during Memory's assigned slot.

Root: independently check reproduction and publish the evidence/tests with `[skip ci]`. Android/Fold7/whole-model acceptance remains `NOT_RUN` / `UNAVAILABLE` / `NOT_MEASURED`. Any future reconsideration is a separate justified experiment. Product rollback is already complete; no data, weight or format migration exists.
