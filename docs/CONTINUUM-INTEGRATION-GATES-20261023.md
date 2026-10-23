# Continuum integration and APK release gates — 23 October 2026

## Frozen review snapshot

Source baseline: signed v0.3.16 / versionCode 17 source `9fb4361c896315e9e974dd5d8feab5ce1ce65803`, package `at.heinz.deviceai.mentorpaired`.
Combined candidate: PR #94, base `astra/render-mentor-apk-builder-20261019`. Do not merge to `main`.
Original inputs: UI PR #91 HEAD `c60addce`; Opus PR #92 HEAD `313e6ffc`; Sol GGUF PR #93 HEAD `adee374c`.
Additional previously absent, no-overwrite recoveries: 11 OS-isolation/capability-research files from `76b98b2`, four independent residual-span/counterfactual Kotlin files from `21365a4`.
Security repair: canonical CCC1 state-path preflight and use of canonical path in CLI; rejects symlinked state paths that resolve under the project.

GitHub integration validation: 175 changed files vs original baseline, complete repository tree, no missing checked paths; initial three source diffs shared no changed filenames. Manual static inspection of 27 relative imports in 14 selected Node modules found no missing target files. This is **NOT** proof of runnable modules or complete transitive safety.

## Executed here

- `node --check ccc1-path-policy.mjs` on the isolated local standalone module: pass.
- `node --test ccc1-path-policy.test.mjs` on identical authored isolated helper/tests: 2/2 pass, including symlink state redirection and non-directory paths.
- No Android SDK installed in the executing sandbox; no full integrated Git checkout available there. Android build, Gradle, whole CCC1/Node regression suite and device tests were **not executed**.
- GitHub commit status checks: none reported on this integration commit; GitHub Actions runner availability is not assumed.
- Render service could not be inspected/deployed here because no workspace is currently selected; do not guess workspace or change a live signing service.

## Required gates BEFORE new APK

1. Freeze the review SHA; verify Git tree cleanliness, test import resolution and explicit transitive dependency/network-denial paths for isolation.
2. Run the complete existing Node/backend suites plus:
   `node --test .github/continuum/ccc1-path-policy.test.mjs .github/continuum/ccc1-worker.test.mjs .github/continuum/ccc1.test.mjs .github/continuum/isolated-executor.test.mjs .github/continuum/capability-research.test.mjs`
   and `node --test scripts/continuum-gguf.test.mjs scripts/continuum-local-context.test.mjs scripts/continuum-local-brain.test.mjs`.
   Respect Docker capabilities and fail closed; an absent daemon is not a successful OS-sandbox proof.
3. Run Android Kotlin/JVM unit tests and Android lint on exact candidate code. Test APK construction with the same source SHA, including Fold/widget/UI checks and input preservation.
4. LM1 remains a research-only language model. Its documented instruction-answer quality is inadequate. It must NOT silently replace the owner's current Android chat/reasoning route; a verified quality fallback is essential.
5. CCC1 Node CLI and cyclic worker must not be presented as Android-controlled until an approved, authenticated app↔Termux/worker bridge, task state/owner gate, cancellation, journal resume and visible review are verified.
6. GGUF/local-context scripts are isolated admission/diagnosis proposals only: verify model origin, no unsafe downloads, correct model hashes, fixed loopback authentication, and OS-level isolation before runtime execution.
7. Prepare **new** same-package signed candidate with versionCode >17, existing cert fingerprint `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`. The current `render-mentor-owner-update-v17.sh` explicitly checks code17 and only accepts the established private signing branch: do not invoke it on PR #94, and do not overwrite the existing published v0.3.16 APK.
8. Archive and hash/signature-verify the existing v0.3.16 APK as rollback BEFORE any publication. Earlier 0.3.15 online rollback remains unverified. Do not manipulate the owner's private keys.
9. Samsung Fold7 acceptance: install as **update** with preserved user data; check unfolded/folded layouts, chat typing, focus/keyboard, TalkBack, background/resume, worker approvals, model loading, battery/animations and actual performance. Only then mark DEVICE_VERIFIED/RELEASE_APPROVED.

## Current gate matrix

| Gate | Status |
|---|---|
| SOURCE_INTEGRATED | staged as review candidate |
| STATIC_SELECTED_IMPORT_PATHS | 27 direct paths exist |
| CCC1_PATH_POLICY_LOCAL_TEST | 2/2 pass in isolated local test |
| ALL_NODE/BACKEND_TESTS | PENDING |
| ALL_KOTLIN/JVM/ANDROID_LINT | PENDING |
| LM1_CHAT_WIRING/CCC1_ANDROID_BRIDGE | PENDING |
| REAL_DOCKER_OS_ISOLATION/ARM64_MODEL | PENDING |
| SIGNED_APK_VERSION_GT_17 | NOT BUILT |
| SAMSUNG_DEVICE_VERIFIED | PENDING |
| RELEASE_APPROVED | NO |

### Results on `opus/ccc1-e2e-20261023` (Opus, 23 October 2026, executed in a Linux container)

Verified on that branch, not on the integration SHA itself; re-run on the frozen integration SHA before any APK.

| Gate | Result there |
|---|---|
| ALL_NODE (`.github/continuum` + `scripts`) | 981 tests: 975 pass, 0 fail, 6 skipped (need `kotlinc`); those 7 tests pass with `kotlinc` on PATH. The six historical failures are fixed (stale Fold7 reflow contract; unterminated awk in `continuum-isolated-node.sh`). |
| BACKEND_TESTS | 60/60 after `npm ci` from the lockfile (7 fail without installed dependencies) |
| KOTLIN/JVM unit tests | 813/813 + 4 Checks programs via `scripts/android-kotlin-unit-check.sh` (outside Gradle; LiteRT-LM compile-only stub) |
| ANDROID_LINT / Gradle / Robolectric UI | not executed (no SDK, Google Maven unreachable) |
| CCC1_ANDROID_BRIDGE | wired and JVM-checked; real repair through the app's Termux calls on a repository clone (`docs/evidence/ccc1-e2e-20261023/`); device not executed |
| SIGNED_APK_VERSION_GT_17 | NOT BUILT (see `docs/CONTINUUM-PREMIUM-UI-INTEGRATION-GATE-20261023.md`, PR #98) |

This document is a review ledger. It cannot itself authorize any model promotion, release merge or installation.
