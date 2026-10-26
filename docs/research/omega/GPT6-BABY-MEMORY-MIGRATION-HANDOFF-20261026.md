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
