# GAMMA independent challenger B

Status: independent bounded host assessment completed. Branch `codex/gamma-challenger-20261103`; baseline `64ec06a73f50cae9ee49035c840710d87777d50f`; [Draft PR406](https://github.com/Ullmann27/device-ai/pull/406) targets `codex/team-chief/omega-20261103`.

Scope: independent frozen counterexamples and descriptive microbenchmarks for FileLearningExperienceStore replay; EXP-06/07/12. No production changes. Current plan: `docs/evidence/gamma-challenger-20261103/PREREGISTRATION.md`. Candidate source/scores not read before freeze. Parent-supplied baseline and bounded dualagent protocol take precedence over historical research pointers; no earlier candidate handoff or PR399 is consulted.

Freeze: [e788d770f212a9b93f5a6ac703b67e82422e1839](https://github.com/Ullmann27/device-ai/commit/e788d770f212a9b93f5a6ac703b67e82422e1839), UTC2026-11-03T18:39:25Z, before candidate source inspection or author scores. Candidate evaluated: `a6007442768b24f5aa700c61dae7c46049c1911a` supplied by Chief after freeze. Frozen checksums remain unchanged.

Tests: baseline and candidate each PASS10 frozen groups, including actual child-JVM lease/writer, concurrent independent instances, unselected-segment corruption/delete/symlink, manifest loss, whole-store replacement, chronological/status selection, result ownership, retained records and unchanged cap constants. Five exact-source Kotlin/JDK runs exit0; full commands and raw logs are in [assessment](../../evidence/gamma-challenger-20261103/ASSESSMENT.md). No Gradle, Android or device acceptance.

Independent conclusion: no correctness blocker discovered. At4096 records warm same-type replay49.085→4.925ms and101.876→8.621MiB allocation, max128 retained records. Caller-shaped source48→intent96 pair109.221→116.986ms (baseline repeat112.356ms) shows no benefit; cold and mutation timings disclosed, with JVM/JIT/host-noise limitations. No app/training speedup claim. Same-instance mutation tested serially; concurrent tests use distinct instances/processes; final QA owns additional race/boundary coverage.

Next entry: third-party QA integrates this frozen harness/evidence with its independently frozen fixtures at exact combined source SHA. Do not rewrite frozen fixtures to fit candidate outcomes. Rollback: discard this research branch; storage implementation is unchanged. Quota: NOT_VISIBLE. EXP-06/07/12; no owner data, weights, main, sign/release/install.
