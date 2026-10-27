# Continuum autonomous cycle — live research handoff

Status: WIP / UNVERIFIED, 2026-10-27. Official repository only.

Branch: `astra/continuum-autonomous-cycle-20261027`.
Base: `54d9de3cde4a4f72f61e2f6c4e62a6dc9bd5439e`, verified with GitHub PR #187 and commit API, and authenticated Git checkout. PR #187 is open/draft, not merged. No PR numbered above #187 existed at intake. `main` remains `190f5eab`. PR #187 contains the #182 → #183 → #186 ancestry and the three coding-journal blobs from #185. #184 is a separate storage prototype, not a newer integrated app. Historical PR prose is not a build proof or release authorization.

Scope: EXP-01/02/05/07/08/11/12. Extend the canonical CodingProvider → CCC1 → mobile coding job → teamleader → isolated repair → independent verifier; do not create another agent/backlog or app. A model candidate is always untrusted. No release, signing, installation, main merge, forced update, weight training or owner-memory promotion is authorized by this branch.

Plan: (1) reproduce and freeze one real software defect and acceptance oracle; (2) execute a real local model through the existing provider; (3) improve error-driven correction/recovery where measurements show missing information; (4) run fresh independent verification and a separate unseen case; (5) connect actual progress through the existing typed Android bridge and publish raw evidence. No target repair may be authored by the infrastructure developer and attributed to the model. Provider outages and rejected candidates remain failures.

Current findings: existing loop already supports scoped multi-file proposals, test diagnostics, up to four model calls, durable request reservations, CCC1 journals, isolation branding, owner review and fresh-worktree proof. Prior Qwen attempts include both a single early pagination success and subsequent invalid/no-op/incorrect failures. This task must therefore demonstrate a new, reproducible capability, not claim the first ever patch. Exact experiment selection and measurements follow in subsequent commits.

Next entry: `.github/continuum/repair-loop.mjs` (`scopedRepairSession`), `coding-provider.mjs`, `coding-orchestrator.mjs`, `scripts/continuum-typed-coding-bridge.mjs`, and their tests. Tests at this initial checkpoint: NOT_RUN. Android/device/APK: NOT_RUN. Rollback: discard this unmerged branch; base and all historical artifacts remain unchanged.
