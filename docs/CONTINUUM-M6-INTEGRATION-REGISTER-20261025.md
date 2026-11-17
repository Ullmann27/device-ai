# Continuum M6 – Integration Register and Release Gate
Date: 2026-10-25

Repository: `Ullmann27/device-ai`
Source code integration branch: `integration/continuum-m6-all-verified-20261025`
Immutable starting head: `efadbc1ac65ccc4a0943d4b47f4290de9be9797e` (PR #130)

## Ancestry and merge discipline

The original approved 0.3.21 owner release source (`aa8780a8489e9e972e9a35a5af9e22866a0ea6e2`) and drafts PR #109, #116, #123, #124, #125, #127, #128, #129 and #130 are already reachable from the integration branch. They have **not** been copied a second time. Original PRs remain open and unmerged, preserving provenance.

PR #126 is a sibling of #127, **not** an ancestor of this branch. Its concrete missing improvements have been selectively adapted into the newer safe feedback protocol: explicit `CODING_NOOP_PATCH` on unchanged oldText/newText (SHA-256 of rejected raw reply, never raw output in feedback), provider transport with a strict regex, distinct model-revision guidance, and specific `CODING_PATCH_NOT_EXACT` diagnostics for oldText absent or nonunique in an authorized baseline file. Added negative and recovery regression tests. The rest of #126 duplicates existing code or uses a different incompatible error protocol. No blind merge of its older implementation.

## Main branch conflict audit

At examination, `main` was at `190f5eabaa60fc59292b2b80d3086aafd7743a80` (October 15), diverged from the source branch with 24 commits unique to main and over 2,000 commits unique to the release/coding lineage. Compared trees show `world-model.mjs` identical, while `baby-core.mjs`, `public-web.mjs`, `zero-cost-core.mjs` and `android.yml` have additional code/gates in the later integration branch. A main-only hourly-watcher workflow remains on `main` and is not overwritten or duplicated. The separate workflow `continuum-zero-cost.yml` also diverges. These are **not silently replaced by old main versions**. Final main merge requires an explicit three-way merge and a complete post-merge regression pass. No production-branch modification is authorized by this integration PR.

## Actual checks performed for this integration

- GitHub parent/merge-base comparisons against PR #109, #116, #123, #124, #125, #126, #127, #128, #129 and #130 plus main.
- Four edited ESM source/test files were fetched from the **new integration branch**, top-level import/export boundaries removed for syntax-only parsing, and V8 accepted all four: **4/4 syntax passes**.
- The committed `queryLocalCodingBrain` function was extracted and executed with strictly stubbed local dependencies, fake loopback responses and a deterministic stand-in for the SHA routine: **3/3 logic cases** (valid changed proposal accepted; no-op separately rejected; malformed JSON separately rejected). This was **not** a real Qwen call, nor proof of a real SHA-256 implementation or complete Node compatibility.
- A read-only GitHub Actions integration workflow was created with complete-history checkout, targeted and full Node regression, Android unit tests, lint and compilation. Initial run: https://github.com/Ullmann27/device-ai/actions/runs/40164992687 . It failed before any runner/test step began: the failed Node job reports zero steps and runner ID 0; the dependent Android job was skipped. The **cause has not been verified**. No red code-test result can be inferred and **no green test suite may be claimed**.
- Earlier Render 22/22 scoped Node and Android Gradle green outcomes were on **older commits**, not on the current integration branch. They must not be attributed to this branch.
- Neither `main`, signed versionCode 22 package `at.heinz.deviceai.mentorpaired`, nor signing keys were modified. No APK was generated, distributed or installed by this integration.

## Unrecovered Sol M6 work

No reference or downloadable Git object has yet been found for `53dfe75a72618789123dd1119fcb501d4b34cca3` or experiment commit `4f01eab30756c47e5272efd5102211b6414fdd90`. Original M6 recovery, FEATURE-on-green-baseline, proposal-only mobile mode and new Qwen research **must not** be marked integrated without authentic source, even if a screenshot reports a local result. This integrated branch contains all **known reachable predecessors**, not Sol's inaccessible unpublished original M6 artifacts.

## Next mandatory release gates

1. Restore GitHub Actions runner availability or use another already-authorized, source-exact isolated checkout. Execute targeted and entire Node test suite with full Git history; investigate any actual failing tests.
2. Execute Android JVM unit, lint and debug compile on the exact integration SHA without signing/distributing another package.
3. Run the canonical repair pipeline with a trusted isolation executor and an independently verifiable local model result. Real source, toolchain and device capabilities must be identified.
4. Obtain exact original M6 commit or verified Git bundle before claiming those specific missing capabilities.
5. Review/merge production main changes separately and re-run test gates on the combined source.
6. Only after explicit new owner approval may a later versionCode of the **single** installed Continuum main app be signed and distributed by the established owner-certificate process.

Current status: `INTEGRATED_SOURCE_DRAFT / QA_BLOCKED_RUNNER / NOT_DEVICE_VERIFIED`. No main merge, no owner release.
