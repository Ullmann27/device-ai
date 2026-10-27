# Native autonomy and semantic learning result — 2026-10-27

Research branch `sol/continuum-autonomy-contrastive-20261027`, Draft [PR178](https://github.com/Ullmann27/device-ai/pull/178), tested code `448a4b76d3380a6fb5c7cf6e07ae0228ab4f7464`. Integration branch `integration/continuum-autonomy-learning-20261027`, Draft [PR179](https://github.com/Ullmann27/device-ai/pull/179), tested code `84d4e132637b15043217947338b0a28b2f3d0375`. Original PR176/177 heads remain `3585d99cc71b9f9d238db317256e7461f2110c6d` / `e736664abb91542da7e6cc7dba91e1b1473ebb3e`; merge base `ec4b4caecd64835e56af924dae1f7ad825059bfc`. No blind merge or foreign branch overwrite.

## Actual native results

Node v24.19.0; Java 21.0.12.1; official Kotlin compiler-embeddable 2.2.0 with a real CLI launcher, not a compiler test double. All raw logs are under `docs/evidence/sol-autonomy-20261027/` in PR178; integrated raw Node log is in `docs/evidence/sol-autonomy-integration-20261027/full-native/` in PR179.

- Clean research worker: **331/331 PASS, 0 FAIL, 0 SKIP**, exit0, ALL_EXECUTED_PASS; actual Git/source/lease checks unchanged. Command: `PATH=/workspace/toolchains/continuum-kotlinc/bin:$PATH node scripts/continuum-local-development-worker.mjs --once /workspace/continuum-local-state/autonomy177-all-native-kotlin-green`.
- Complete integrated `.github/continuum` plus `scripts` Node suite: **1424 PASS / 1 FAIL / 2 SKIP**, 1427 tests, exit1. The immutable owner-v25 gate refuses the modified `autonomous-teamleader.mjs` source. This is a retained release block, not an approved release; do not regenerate its approval manifest. Two opt-in infrastructure cases lack their configured snapshot / Docker Android runner and remain NOT_RUN.
- Selected integrated JVM suites: feedback/process safety **6 JUnit PASS**, neuron preservation/cancellation **3 PASS**. Original SWB1 and research SWB2 codec roundtrips both32960bytes; 4 actual Node/JVM scores match within1e-12; productive SWB1 loader rejects SWB2.
- Native retained worker acceptance:11 event receipts cover healthy, real test failure/retry, timeout/retry, TERM/restart, KILL/recovery, and failure then success within one continuing scheduler. Source fixture Git bundle verified. These are Linux host process tests, not Android background-service proofs.
- Prior RED runs retained: four original native failures repaired (duplicate import, CRE1 single-term rounding, inherited Node test context, absent Android-source capability); enabling Kotlin uncovered a fifth old diagnostic-specificity bug. The latter uses the exact already-newer PR176 fix. No vanished tests or weakened assertions.

## Learning: rejected, no productive promotion

Starting public SWB1 hash `5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1` remains unchanged:12 rounds,2 accepted,13000 updates.33 research trials share unchanged starting weights, balanced authored positive/negative pairs, fixed seeds73/101/211 and retention gates. Known52 examples are report-only, never pristine holdouts. Validation-driven dual-head adaptation was disclosed before opening the final set; snapshots/selection were pushed first.

Only frozen-intent + SWB2 semantic sidecar passed validation (19/36 vs15/36 semantic,12/12 intent unchanged). Final baseline:17/20 intent,26/60 semantic,3/6 dialogue consistency. Sidecar seeds:17/20 intent each;26/26/27 of60 semantic;3/6 dialogue each. **REJECT_NO_STABLE_FINAL_GAIN.** False-similarity errors remain27/30 vs28/30 baseline. No neural improvement, model migration or owner-weight promotion is claimed. Public research checkpoints remain openly reviewable. Final data is now a regression set: no further strategy selection on it.

Post-final read-only diagnosis identifies four role-swapped negative pairs with identical weighted feature maps. The current pooled encoder cannot distinguish these permutations by more same-format training. This explains a concrete failure class, not every semantic failure. Future order-sensitive designs require NEW independent evaluation data.

## Automation, limits, next step

GitHub Actions check annotations explicitly report an account payments/spending-limit start block. Which of those two alternatives applies is not established; no billing or runner setting was changed. Local monitoring has no Actions/API requirement. Worker evidence/heap/time budgets, OS lease, crash journal and STOP handling are implemented; it does not autonomously patch production or grant its own PR/release/model rights. No daemon was left installed.

No Android SDK/Gradle/ADB/model runtime/device/signing access is configured here. No complete Android build, APK, certificate compatibility, new Gemma inference, Fold7 run or complete RED→GREEN→GREEN model repair occurred. Existing owner package remains `at.heinz.deviceai.mentorpaired`.

Continue on PR179: inspect the existing main-chat `BoundedAgentOrchestrator`, typed ToolGate and confirmed-memory flow. Connect one genuine bounded observation through this existing path, add native positive/negative E2E tests, then run device validation when tooling is available. Keep SWB2 disabled and preserve owner-v25 release refusal. Detailed original handoff and recovery scripts accompany this report. No main/state/release merge was performed.
