# Sovereign cognition S2: Prospective context-loss experiment and conditioning counterexamples

Milestone commit: `c6f79e0e0c91ab6ca5a5ca062eaa8568071dac00` · [Draft PR357](https://github.com/Ullmann27/device-ai/pull/357).
Follow-up code repair: `aabd8ea7dcd09863fb5fe214ed027cd145ce2fb0`; stronger Android source contract: `babba3326475521bfc8d03807ee1ad8873e01b91`.

SOURCE_INTEGRATED / HOST_VERIFIED within the stated focused scope; DEVICE_VERIFIED=NOT_RUN. RESEARCH_SHADOW; no productive promotion.

Actual path: Frozen compiler projection → existing evidence-bound location probe/verifier; separate existing SWB1/CDX seeded diagnostic.

Changed/reused files: tools/continuum-span-research/ContextBudgetExperiment.kt, CdxConditioningAudit.kt; scripts/continuum-context-budget-audit.sh.

Tests and result: Corpus frozen in eefbd231922745ee487fbfff46ef222316867c80:72 cases; parent48 correct/24 wrong, candidate72 correct including24 proper negative abstentions;24 improvements,0 regressions,48 unchanged. Fresh full-class harness rerun reproduced it.12 novel role swaps:0 identical bucket sets,12 structurally correct bindings; no neural performance claim.

Limits: RESEARCH_SHADOW. Author-generated prospective synthetic holdout, not independently authored blind tasks. Seeds control input bindings/seeded controls, not trained model replicas. No fitting, checkpoints or productive weights touched.

Complete raw evidence: `docs/evidence/sovereign-cognition-20261102/`; final acceptance commands/source digests/status: `final-verification.json`. Historical failures remain in the same directory. Read [central handoff](SOVEREIGN-COGNITION-HANDOFF-20261102.md), [architecture matrix](SOVEREIGN-COGNITION-ARCHITECTURE-20261102.md) and the research result before continuing. Native Gradle/LiteRT/Fold7/model tests NOT_RUN; no APK built/signed/installed, no main/merge, no owner-memory or productive weight change.

Reproduction: existing `scripts/android-kotlin-unit-check.sh` with the documented Kotlin2.4/JUnit/Android-all dependencies; `node scripts/continuum-reliability-qa.mjs critical --out <fresh-outside-checkout-dir>`; full Node with profile`full-node`. S2 additionally uses `scripts/continuum-context-budget-audit.sh <parent-main-classes> <candidate-main-classes> <fresh-output>` with the same compiler/stdlib/JUnit/json environment documented in that script.

Rollback: Discard experimental reports/candidate hypotheses; productive decoder is unchanged. Retain negative results and the frozen corpus.
