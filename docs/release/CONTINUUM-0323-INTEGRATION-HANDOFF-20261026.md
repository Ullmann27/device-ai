# Continuum 0.3.23 integration and APK acceptance — 2026-10-26

Status: REVIEW_WIP / NOT_BUILD_VERIFIED / NOT_SIGNED / NOT_INSTALLED.
Owner's explicit goal: integrate mature unused development, three control passes, architecture-wide audit, and build the next same-package owner APK if and only if actual checks and original signer prove it safe.

Current source base: 83fc4a16f9fff7f37b9b27576b1abab293c956d7 (PR #170, which descends from Sol PR #169).
Only official repo Ullmann27/device-ai. The target Android signed owner variant is at.heinz.deviceai.mentorpaired, code 24 / 0.3.23; the installed owner's package/certificate/data must not be changed by review operations.

Expected owner signer cert SHA-256 from historical release plan: 2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc. This is a constraint, not a newly measured certificate.

## Source inventory before integration
- #143 experiment UI is ALREADY an ancestor of this source: do not cherry-pick.
- #169 brain/fabric/CCC1 integration and #170 completeness fail-closed patch ARE ALREADY ancestors.
- #140 (14 divergent commits, 9 paths) preserves isolated old owner-release signing gates and V22 historical proof, but targets *code 23*, not code 24. Do not reuse V23 permissions for V24.
- #153 grounding subject/relation candidate: 5 files, separate independent holdout not established. Do not promote without new frozen benchmark.
- #158 baby-core provenance: diverges from old main, conflict risk; investigate only as selective diff, not direct whole-tree overwrite.
- #145 offline native model corpus: research-only; do not enable in Android release.
- #134 OMEGA security tooling: research-only; no new production execution grants.

## Mandatory review
Pass 1 — file provenance, owner package/version/signature and dependency ancestry; no regressions in canonical backlog/ToolGate/typed task.
Pass 2 — native Node/Backend/Kotlin/Android Gradle/lint, with exact head/exit codes, no historical suite conflation.
Pass 3 — release-in-place and compatibility/security: APK sha/cert/aapt identity, device/Fold7 smoke, rollback artifact and user-data survival.
Architect review — unused high-quality branches, dependency graph, runtime sandbox, real model routing, unresolved system-level E2E.

No main merge, no release key access, no unsafe model work or automatic install. A source-only checkpoint is never an APK.

Start status: tests NOT_RUN on new branch, no build yet, no service changed.
