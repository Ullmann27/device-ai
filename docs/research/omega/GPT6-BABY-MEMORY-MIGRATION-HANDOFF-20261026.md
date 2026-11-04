# Continuum GitHub Baby Core – independent migration and provenance audit (2026-10-26)

STATUS: START_CHECKPOINT / WIP / SOURCE-AUDIT; no CI, Android, owner-device or full Node success yet.

Repository `Ullmann27/device-ai`; exact starting `main` SHA `190f5eabaa60fc59292b2b80d3086aafd7743a80`. Own source-review branch `review/continuum-baby-memory-migration-20261026`, intentionally **separate** from SOL PR151–156 and GPT6 PR157. Do not modify main, signing materials, owner memories, model weights, GitHub Actions schedules or research artifacts.

Sources initially inspected:
- `.github/continuum/baby-core.mjs` main Git blob `f10c8ed8a2a401fa7c7f4e8cb20c415c04978683`
- `.github/continuum/baby-core.test.mjs` main Git blob `2b219d80f1266bf61c510f65a3bc67180c9822fa`
- `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`, Issue #41 reviewed in latest sibling branch; Android-first and evidence-authority bounds retained.

Two exact hypotheses to verify with RED tests before a bounded fix:
1. Migrator now upgrades schema 1 to 2 **then** 2 to 3; test `legacy overconfident beliefs are downgraded until revalidated` still expects schemaVersion **2**, so its expected value is stale against current source.
2. `consolidateBeliefs` selects higher-confidence duplicate and then marks it `GROUNDED_OBSERVATION` if **either** duplicate is grounded. A high-confidence unverified provisional row can replace the actual grounded row's ID, confidence, and creation provenance, while inheriting grounded status and concatenated source URLs. Source confidence/status escalation is a dangerous cross-record merge. Preserve an authentic grounded anchor and its support metadata if mixed-status duplicates are consolidated; do not invent stronger evidence.

Affected EXP-06 (provenance), EXP-07 (regression/negative tests), EXP-12 (state migration). Avoid changing Android Core `ContinuumMemoryDialogue` / SOL grounding logic; this is **GitHub runner Baby Core**, not yet demonstrated in the owner APK.

Plan: write two deterministic unit regressions, implement minimum correction, run real Node tests if exact full code and dependencies become available; otherwise report NOT_RUN. Publish evidence/rollback. Invariant: source support never upgrades merely through duplicate merging; old v1 schema remains support-downgraded after v3 migration.

Rollback: revert only this branch's changed files; all existing sibling PRs untouched.

## Actual completed source repair and independent evidence

- PR #158 Draft remains isolated from `main`, the SOL experiment branches and GPT6 PR #157.
- RED regression cases added to `.github/continuum/baby-core.test.mjs` in `802dd64e` (cross-tier duplicate source contamination) and `5b59662e` (weak → verified → weak same-claim reingestion).
- Source repair `54cae7d36605753e8b860dcb8931840fcc5c55ed`, exact new source blob `df049d3f2faac38b7fb263a64ba89a51d22cf5fd`: when the same normalized claim has different provenance statuses, preserve the actually grounded record instead of grafting authority onto a high-confidence provisional record. During reingestion, newly grounded evidence replaces provisional citations; a later weaker candidate cannot overwrite the grounded evidence or its validation timestamp. Same-tier aggregation remains preserved.
- Existing legacy v1 test expected obsolete schema2 and used a claim sanitized away by the now-active filter. Updated to schema3 with a valid meaningful provisional claim in `2c2e38b` and `838705f`.
- **Independent source-level V8 RED/GREEN executed against both exact GitHub source blobs**: baseline retained provisional ID `provisional-high`, confidence `0.94`, added unverified citation while labeling it GROUNDED; corrected source retained real grounded ID `grounded-verified`, confidence `0.73` and verified citation only. For repeated ingest, baseline kept four URLs and updated lastValidatedAt from200 to300 on weak replay; corrected source kept three stronger URLs and lastValidatedAt200. V1 meaningful legacy migrated to v3 as PROVISIONAL confidence0.55 in both. Detailed exact method and substitution limitations in `docs/evidence/gpt6-main-baby-memory-20261026/PROVENANCE-RED-GREEN.md`.
- All new Node tests are `COMMITTED / NOT_RUN_FULL_NODE`; V8 source execution uses test-only shims for Node crypto and structuredClone. This does **not** prove full Node test suite, GitHub Actions schedule, Android SDK, Fold7 or APK.
- Next developer: run `node --test .github/continuum/baby-core.test.mjs`, then full `node --test .github/continuum/*.test.mjs` in pinned authorized checkout. Check live state schema migration **without writing actual continuum-state**. Merge only through owner-approved source/integration gate; avoid blindly combining with PR #157's Android memory path.


## 2026-10-26 independent extended source-suite replay

The exact committed `.github/continuum/baby-core.test.mjs` source blob `3d73f58c39d7d88a667acfa761d164c3e3f800fc` was run as unchanged test callback bodies against both GitHub-retrieved source modules: baseline `f10c8ed8a2a401fa7c7f4e8cb20c415c04978683` **10/12 PASS, 2 FAIL**; patched `df049d3f2faac38b7fb263a64ba89a51d22cf5fd` **12/12 PASS, 0 FAIL**. Three public-web tests excluded equally. Environment was an isolated V8 runtime with deterministic Node crypto and structuredClone test-only shims, NOT full Node `--test` and NOT Android. The failing baseline checks were exactly the two new evidence-provenance regressions; no current patched failures among the 12 isolated tests. Details `docs/evidence/gpt6-main-baby-memory-20261026/ISOLATED-UNIT-SUITE-20261026.md` commit `da0b9c1b4c6302f879d571f62865ad1b9031d12f`.

GitHub Actions run `40272725587` on PR158 failed before executing any job steps; runner availability is unconfirmed, not a proven code failure. Do not promote PR158 without normal full Node and owner-side gate. No main, neural weights or private owner state changed.
