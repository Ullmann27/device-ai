# Astra Continuum integration — 2026-10-26

Status: START_CHECKPOINT / UNVERIFIED. Official branch: `astra/continuum-brain-integration-20261026`, based on PR151 `9d66813419f08468ba99bd7e45ae38ff6c0e6b99`. Main and all previous branches remain unchanged. Owner authorized integration, tests and regular pushes; no production merge, signing, installation, remote inference, new weights promotion or paid resources.

Read AGENTS.md, the experiment contract/register and live Issue #41. Branch snapshot is in `ASTRA-BRAIN-INTEGRATION-SOURCES-20261026.json`. PR157 descends from the selected base. PR158 has merge base `64a7550bc4b48ff5317b78067d5751fc4e55e8f4` and must be compared selectively. Existing workspace was empty; authenticated Git checkout is available.

Plan: audit actual PR151–158 sources and dependencies; reproduce the first dialogue/evidence failure; integrate only tested fixes; exercise the canonical coding/sandbox path; run the existing neural experiment with frozen evaluation; verify combined Node/Kotlin/Android capabilities and publish exact raw results. Existing memory, backlog, builder and ToolGate are the only canonical systems. Research remains disabled unless independently justified and owner approved.

Current tests: NOT_RUN. Physical Fold7 access, Android SDK, local models and OS sandbox capabilities not yet established. No capability or breakthrough claim.

Resume: fetch this official branch, read this handoff and source manifest, inspect git status and real remote heads, then audit the PR source diffs. Recover from this commit; rollback subsequent source changes with targeted revert commits on a review branch, never reset shared branches.

## Checkpoint 2: source audit and validation setup

Draft PR #159; initial remote commit `a58d1f873c96bf30b37694a7636c70e9394aa9e8` and handoff file independently fetched through GitHub. Complete PR path/merge-base snapshot and preliminary component dependency graph are machine-readable in the source manifest. Two independent read-only source reviews are in progress. PR158 cannot overwrite the newer Baby Core: explicit-citation provenance rules must be preserved.

PR157 actual test file imported FIRST against unchanged parent routing to obtain real Gradle RED evidence; test outcome pending. Official checksum-verified SDK commandline tools and Gradle8.11.1 are configured outside the repository, no signing/build/install action. Host is Linux x86_64, cgroup2CPU/8GiB, Node24.19.0, JDK21.0.12.1; managed Docker28.4.0 reachable. No cached native language model or physical phone connection found.

PR152 frozen research sources/configurations imported byte-for-byte, still UNTRAINED/UNVERIFIED and disconnected from production. Existing independent CDXA1 test labels remain unopened. No tuning on consumed grounding60. Remaining candidate source integration awaits tests/review.

## First source RED/GREEN: provenance before consolidation

Reused PR158's bounded same-claim evidence-tier separation, adapted to the newer integration source. A new regression proves legacy GROUNDED rows lacking EXPLICIT_CITATION could contaminate a qualified duplicate, regardless of source order; simply copying PR158 would retain this defect. `baby-core.mjs` now recalibrates each legacy row BEFORE deduplication, keeps grounded metadata isolated and blocks weaker reingestion. Existing quarantine/topic/URL gates retained. `.github/continuum/baby-core.test.mjs` contains two adapted PR158 tests plus the new four-combination/restart test. Actual Node RED: 19 cases, 3 failed. GREEN: baby-core + epistemic graph + research failure suite 30 passed, 0 failed/skipped; raw logs in `docs/evidence/astra-brain-integration-20261026`. This is GitHub-worker memory, not an Android database replacement.

Android Gradle initial attempt failed before tests because the installed Java21 runtime lacked JAVA_COMPILER. Full official Temurin17.0.21+9 downloaded and checksum verified; actual Android compile/test retry running. This setup failure is not a routing RED test. No signing or APK tasks run.
