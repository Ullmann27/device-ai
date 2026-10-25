# SOL infrastructure repair handoff — 2026-10-25

Status: WIP / UNVERIFIED. No implementation changes in this first checkpoint.

## Source identity and authorization

Official repository only: Ullmann27/device-ai (private; accessible to authorized collaborators). Working branch: sol/continuum-infrastructure-repair-20261025. Base: PR #148 at 30303ca9103fa2034aa384fcf5f730402ecd0e6c. Owner requested source audit, fixes, tests, feature branches and Draft PR; no release signing, installation, paid compute or main merge authorized here.

Read AGENTS.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, docs/CONTINUUM-EXPERIMENTE.json and live Issue #41. EXP-00/02/04/05/08/11/12/14 are audit subjects; no research promotion.

Live GitHub metadata confirms PRs #139, #140, #142, #143, #145, #146, #147 and #148 are open Drafts at the SHAs in the owner handoff. Additional PR #149 at febffb293f12702df5714ee20c8fb66471b493b9 changes local-model prompt/token policy; inspect before overlapping work. Main remains 190f5eabaa60fc59292b2b80d3086aafd7743a80.

## Ordered work

1. Reconstruct ancestry and branch/file differences; preserve all developer branches. Check required historical handoffs and actual runtime entry points.
2. Run current selected-model/status/ORION and Node/backend baselines. Provision only official local Android toolchain if absent; run Gradle Kotlin compilation, unit/widget tests and configured lint. Record errors before repairs.
3. For each reproduced defect: add a failing behavioral regression, repair the cause, run focused and appropriate integration checks, commit and publish.
4. Inventory remaining experiments and real Android/backend/worker reachability, distinguishing source, host tests, physical-device and production status. Connect only evidence-supported components through existing gates.
5. Checkpoint reproducible evidence and exact SHAs; preserve signer, weights, owner memory, checkpoints and Qwen fallback.

## Verification at this checkpoint

GitHub PR metadata and main/head refs: read successfully. PR #148/#147 GitHub check-runs: zero registered checks (not evidence of failure or success; external QA needs receipts). Official checkout is in progress in authorized cloud workspace. Node/JVM/Gradle/lint/device: NOT_RUN at this checkpoint. No owner key or Fold7 device attached; signing and physical acceptance remain BLOCKED.

## Repair checkpoint 1

Base tested: `5d944d49af099a0a66660f5a126c9c7231e0547c`, implementation tree of PR #148. Actual Linux/Node 24.19 baseline: 1234 tests, 1227 passed, 1 failed, 6 skipped (unavailable standalone kotlinc); backend 60/60 passed. Failure was `model setup stays usable for beginners while preserving SHA-256 integrity`: its source scanner still searched the removed no-argument importer signature. Updated discovery to the actual parameterized function and preserved all mandatory hash/validation checks.

Reproduced QA defect with executable real-Git fixtures: `continuum-model-center-qa.sh` read `CONTINUUM_QA_SOURCE_SHA` although its documented pin is `CONTINUUM_MODEL_CENTER_QA_SHA`, causing an unbound-variable exit even for an exact pin. It also allowed missing pins and dirty sources. Added required 40-hex exact pin and clean-source guard before toolchain/network side effects. New regression cases were observed failing before the fix. Focused verification: `node --test scripts/continuum-model-center-qa.test.mjs .github/continuum/android-integration-wiring.test.mjs scripts/continuum-model-center.test.mjs scripts/continuum-model-selection.test.mjs` → 30/30 passed; `bash -n` and `git diff --check` passed. This is a QA/integration repair, not an Android inference claim.

Official Gradle 8.11.1 distribution and Android CLI downloaded locally and checksum-verified. SDK 36 provisioning and full Gradle checks are in progress; no Render services changed. Gemma original-file verification is in progress from official immutable Hub revision `e6a3892a8c22cf87ff99583996149cf3cb32452b`; original-file size is 3659530240 bytes and publisher LFS SHA matches the selected SHA. Full-byte result remains NOT_VERIFIED until hashing completes. DeepSeek weights are not downloaded.

Plan and task ledger: `docs/research/omega/SOL-INFRASTRUCTURE-PLAN.md`. Independent read-only audits investigate old experiment refs and CDX1 collapse; findings will be published without changing any other developer branch.

## Safety / rollback / next entry

No remote service mutation or model weight download. A git worktree is not an OS sandbox. No shell from model text, grant bypass, speculative training promotion or release-manifest reuse. Revert individual commits on this working branch to return to PR #148; never reset another branch. Next: finish local checkout of the exact base and inspect build scripts/toolchain, then publish measured results here.
