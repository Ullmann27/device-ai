# Continuum Coding – mandatory isolation and verified owner-review gates

Date: 2026-10-25
Starting point: PR #128, commit `8ff3dbf04522e9667a3d875ab74a86e4557f3b58`
Repository: `Ullmann27/device-ai`.

## Evidence-led risk identified

The existing `runCodingTask` orchestration originally defaulted to `executor=null`. The underlying `runProfile` then selected the legacy noncontainer host runner. A real Android/Termux Coding RUN called the typed bridge without explicitly provisioning an executor. This allowed untrusted model-generated code to reach the host fallback. An unchanged owner-approved task or a second Android confirmation does **not** authorize unsafe host execution.

## Mitigations in this PR

1. The existing orchestrator now accepts `requireIsolation=true`. It rejects any executor not branded by the actual `isIsolatedExecutor` factory and rejects any abort mode other than `CHECKPOINT_BOUNDARY_ONLY`, **before reading the grant, journal or contacting the model**. Legacy tests and explicitly separate trusted repair operations retain their current API; the typed Android RUN always requests strict isolation.
2. The Termux entrypoint loads an owner-controlled external configuration from `CONTINUUM_CODING_ISOLATION_CONFIG` and calls existing `loadIsolationConfig` and `createIsolatedExecutor`. Relative paths, absent files, unsafe symlink/source-root configs and Docker absence are rejected; error messages are fixed codes and do not expose secrets. There is **no** host execution fallback. The typed Android callback shows `BLOCKED / CODING_ISOLATION_UNAVAILABLE`, never patch success.
3. Even a purported `PATCH_PENDING_OWNER_REVIEW` must carry a matching source commit, CCC1 task ID, `DONE` backlog state, hashed patch and verifier, explicitly sufficient independent evidence, positive failed-baseline and executed-test counts, and the fixed three test phases. Otherwise the Android-facing status is `EXECUTION_REPORTED_UNVERIFIED` and the patch hash is withheld. This tightens evidence presentation; it does not replace the deeper CCC1 or independent verifier.
4. Existing two-step owner permission, one-shot Android callback, package/signature invariants and read-only main remain unchanged.

## Validation actually performed

- Static V8 parse of all three modified ESM files (after stripping import/export syntax for the parse-only check): passed.
- The **actual extracted** `verifiedCodingReviewReceipt` helper was executed against eight controlled positive/negative cases, **8/8 passed**, including missing evidence, insufficient evidence, wrong commit, incomplete test chain and forged task ID.
- The **actual extracted** `ownerCodingExecutor` helper was executed against four isolated dependency-injected cases, **4/4 passed**: missing config, relative config, unsafe config and authorized configuration propagation. These helper tests did **not** create a Docker container or prove Android isolation.
- Expanded repository Node regression tests were committed in `scripts/continuum-typed-coding-bridge.test.mjs`, but **the changed branch's full Node suite was not executed** in this session. The existing Render static-test service is attached to the earlier branch, and the account reached its 25-service limit. No claim of full green QA is made here.
- Existing PR #128's prior Android unit/lint/build results apply only to the earlier Android code and were not re-run for this new branch. This security follow-up changes no Kotlin or Android app files.

## Device capability and release boundary

On the Galaxy Z Fold7, a conforming Docker-equivalent confinement engine is **not demonstrated**. Until such a trusted local executor is provisioned and measured, the correct behavior is `CODING_ISOLATION_UNAVAILABLE` and **no model-generated patch execution**. Calling that a functioning Fold7 coding agent would be inaccurate. Research into a stronger Termux/Android-specific sandbox remains a separate future task.

Keep this PR as a draft. Do not merge into main, sign/install/build a second Preview APK, download or install unverified toolchains, or alter production memory. A complete test checkout, independent isolation proof, on-device validation, owner sign-off and the existing owner-signed update chain are required before release.
