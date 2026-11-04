# TUNING-BOT 1 — inference handoff, 2026-11-04

## Scope / state

- Experiment `EXP-05`; verified base `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`, base tree `72e62267ba22309d60ad13abb3e2a5c6a155e544`.
- Work branch `tuning/inference-59ad`, own worktree only. Root coordinates publication in Draft PR #511. Until Root confirms publication, this bot's result is `LOCAL_ONLY / NOT_PUSHED`.
- Production change is confined to `LmEval.sample` in `app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt`: stable linear greedy selection instead of sorting the vocabulary. Final source SHA-256 `d9fc7493360e526323505ff1637981a6e024f6c61a47e4d6f24df22d248df65d`.
- No training, model-format, tokenizer, weights, offline-switch, gates, Memory/Worker, Main or release change.

## Verified results and limitations

See [the complete result and all raw evidence](../../evidence/tuning-inference-20261104/RESULTS.md). Five new JUnit methods passed before and after; 35,624 direct parity cases, 50 Unicode/model token checks, unchanged RNG/input bits/serialization and empty-array exception semantics. Actual local Kotlin 2.0.20/JVM17 compilation on JDK21; no Android build.

All three host benchmark runs are preserved: original before, preliminary candidate A with its patch/source hash, and complete final B. Each has nine scenarios × nine alternating A/B pairs. At vocabulary 512/random/greedy, final median CPU time is 61.615 → 0.566 µs per sampler call, allocations 14,120.055 → 0.055 bytes. These results concern **only the sampler**.

**Review tradeoff:** positive-temperature control retains a consistent +24 bytes/invocation (+0.152%) on HotSpot; median CPU difference +2.13%, with shared-host noise and no device confirmation. The before identical-algorithm comparison had equal allocations. This cost is not hidden or represented as proven noise. Root should make the explicit adoption decision; the bot stopped after the complete B run.

Actual source connection is `LmRuntime.generate` / `LmEval.generate` → `LmEval.sample`. No external production caller of `LmRuntime.load` was found in `app/src/main/java`; main Gemma/LiteRT chat acceleration is not established. No language-quality uplift, end-to-end model throughput or Frontier-level performance claim is made.

## Reproduction / next action / rollback

Run the versioned `docs/evidence/tuning-inference-20261104/run.sh` from the repo with an installed Gradle 8.11.1 library directory and a separate output directory, phase `before` or `after`. The runner extracts BEFORE production files from the fixed base and records source hashes/reference equality. `LM_SAMPLING_RUN_BENCHMARK=1` enables the full host benchmark. `summarize.py` verifies all archived measurement pairs and emits all scenario summaries.

Root: review exact diff, accept/reject the stochastic allocation tradeoff, publish the qualified commit through #511. Actual APK/Android/Fold7 acceptance remains `NOT_RUN` / `UNAVAILABLE`; wider pre-existing LM suites were not rerun by this bot. If accepted, the next meaningful performance check is on-device with the actual caller/model and both temperature modes. Rollback: revert this isolated tuning commit; no persisted-data or model migration exists.

## Coordinator publication and independent verification

Published in [Draft PR #511](https://github.com/Ullmann27/device-ai/pull/511), through commit [`f7c3076`](https://github.com/Ullmann27/device-ai/commit/f7c30760005c3c2614c8abe623304f27b6bcd1ef). Earlier local-only statements above describe the bot handoff before publication. The coordinator then reran relevant functional checks on the combined source; see [the final three-bot handoff](THREE-TUNING-BOTS-HANDOFF-20261104.md) and [the actual coordinator evidence](../../evidence/three-tuning-bots-20261104/coordinator/README.md). All documented performance limitations remain in force.
