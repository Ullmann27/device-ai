# Continuum – verified-source integration audit (25 October 2026)

**Repository:** `Ullmann27/device-ai`
**Integration branch:** `integration/continuum-m6-all-verified-20261025`
**Parent baseline:** `efadbc1ac65ccc4a0943d4b47f4290de9be9797e` (draft PR #130)
**Owner release ancestor:** `aa8780a8489e9e972e9a35a5af9e22866a0ea6e2`
**Product invariants:** one existing Android application, `at.heinz.deviceai.mentorpaired`, owner certificate and signed 0.3.21 unchanged.

## Commit ancestry audited directly through GitHub

PR #109 (M5) and PR #116 (dialogue/research), PR #123/124 (0.3.21 release line), PR #125 (Sol coding), PR #127 (bounded feedback), PR #128 (Android/Termux bridge), PR #129 (strict sandbox), and PR #130 (owner SHA preflight) are all **ancestors** of this branch. They must NOT be blind-copied or remerged.

PR #126 is an **alternative sibling** of #127 and was inspected patch by patch. Both implement response rejection/feedback, but use different fingerprints and failure protocols. The integration applies the useful *distinct NOOP response* behavior from #126 **to the newer #127 protocol**, rather than merging the entire conflicting branch.

## New compatible changes in this integration

- `scripts/continuum-local-brain.mjs`: distinguish invalid JSON (`CODING_RESPONSE_INVALID`) from syntactically valid, unchanged exact patch (`CODING_NOOP_PATCH`). Both yield only fixed codes and SHA-256 response fingerprints, never untrusted raw model text.
- `.github/continuum/coding-provider.mjs`: accept only these two fixed local CLI rejection formats with exact 64-character lowercase fingerprints. Reject all other stderr as unavailable; do not pass arbitrary stderr or command text to the model.
- `.github/continuum/repair-loop.mjs`: record the new trusted error code and provide one bounded, actionable no-op diagnostic to the next model call. Duplicate rejection fingerprints retain the existing early-stop guard.
- `.github/continuum/coding-rejection-feedback.test.mjs`: add regression cases for no-op vs malformed JSON, redacted provider propagation and correction after feedback.

## Additional branch-by-branch scrutiny

- `chatgpt/continuum-core-mirror-20261024`: all four mirrored, beveled hologram UI production files and `ContinuumCoreMirrorTest.kt` have **identical Git blobs** to this integration; no repeated merge needed.
- `integration/continuum-0320-core-cdx-20261024`: same mirrored UI file blobs, same CDX1 lab file, cycle test and CDX1 postmortem blobs; no reapplication needed.
- `chatgpt/continuum-cdx1-postrun-audit-20261024`: its cycle test and postmortem are already identical to this integration; its additional newer integration changes are already accounted for.
- `sol/continuum-hologram-intelligence-20261024`: one divergent commit explicitly says it preserves **failing** interrupted hologram work. Not included without reproducing/repairing its failing device/UI tests.
- `main`: diverged (24 main-only commits at audit, many older core files). `world-model.mjs` is byte-identical while the integration carries newer expanded `baby-core.mjs`, `public-web.mjs` and `zero-cost-core.mjs`. Main also has `continuum-hourly-watch.yml`, which is absent from this owner-release-based branch. Its scheduled CI behavior and workflow permission model must be reviewed independently; it is **not** silently added to the owner's signed release path. No uncontrolled merge into main.
- Sol M6 original implementation SHA `53dfe75a72618789123dd1119fcb501d4b34cca3` and experiment SHA `4f01eab30756c47e5272efd5102211b6414fdd90` remain unavailable through GitHub. No assumption these files have been integrated; do not invent replacement source.

## Validation and release gates

**This branch:** V8 syntactic parsing of the four modified JS sources passed (ESM imports/exports removed for parser-only assessment). The actual `queryLocalCodingBrain` implementation was run with controlled deterministic fetch/crypto/timer dependencies for three cases: valid change accepted, invalid JSON rejected, unchanged no-op rejected: **3/3 expected outcomes**. This isolated functional proof is **not a repository Node/Android/physical-device test**.

**Previously checked ancestor PRs:** PR #128's Android JVM test/lint/debug assembly and focused 14-test Node bridge were recorded on their original unchanged test commits; those results do not automatically carry over to this new integration. PR #130 preflight is commit-recorded but not full CI verified.

**Still required before release:** clean full-history checkout and all Node tests, backend, Android Gradle test/lint/build, independent Docker/Termux security proof, owner signed certificate and versionCode validation, physical Fold7 trial, *separate explicit owner authorization* for a new signed main app update. The current signer's APK 0.3.21 must remain untouched.

## Review decision

This branch is a **source integration candidate**, not a signed release or a claim that every laboratory experiment is production-ready. Keep it as Draft until the test and device gates above pass. Never install a second preview app, force-merge unrelated branches, bypass CI or use unverified model success as a release criterion.
