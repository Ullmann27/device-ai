# Continuum M2 – 20 connected acceptance stages, no simulated model success

**Mode:** Only the existing primary Android app `at.heinz.deviceai.mentorpaired`, version `0.3.24` / code `25`, and existing CCC1 repair modules. No new app or owner-key release. Head derives from independently QA-passed M1 integration `19a83aab0bd4402ce53a860bc8667d50f5ac5497`.

## Why this change is necessary

The owner-selected original Gemma 4 E4B at SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` has format `.litertlm` and requires Android/native LiteRT-LM. The existing local host CodingProvider is an offline loopback `llama.cpp` interface for GGUF models and **cannot simply load the chosen LiteRT file**. A model label is not a model load proof. Prior unverified Qwen coding experiments consumed iterations without a confirmed corrected patch. Model format and owner selection must be checked **before** any provider inference request.

## What the source changes actually do

1. Read the actual owner-selected Gemma model ID, pinned SHA and native format.
2. Prevent absent/blank/control-character/oversized model IDs from entering coding dispatch.
3. Prevent an unconfigured default `local` model from silently using the coding transport.
4. Explicitly detect the incompatible original `gemma4-e4b-it` / `.litertlm` on the GGUF adapter.
5. Require the supported loopback backend (`llama.cpp`).
6. Require an explicit `gguf` format declaration on the coding path.
7. Require a syntactically valid full lowercase GGUF artifact digest.
8. Reject attempts to misrepresent the official Gemma LiteRT SHA as a GGUF checkpoint digest.
9. Require `OWNER_APPROVED_ALTERNATE_GGUF` as a discrete explicit consent token, including when the alternate label is Qwen.
10. Return a frozen, **configuration-only** verdict; no model or repair success is inferred from env variables.
11. Enforce the verdict in the **real** local CLI for `coding` and `patch` before network/loopback requests.
12. Keep chat transport, original model code, owner data and approved release mechanism unchanged.
13. Add an explicit `Meilenstein Status` command to the actual Android chat, not an unrelated demo.
14. Add explicit `Meilenstein Modelltest` / `Gemma Modelltest` commands that invoke the **existing native localRuntime.stream** direct diagnostic on device.
15. The diagnostic refuses absent/unready native models and no tool execution is allowed during inference.
16. Bind session proof only to the owner-selected original Gemma ID **and** exact matching SHA.
17. Revoke old session proof whenever a new direct test starts; failure, reload or model replacement cannot show old evidence.
18. Present a truthful read-only ten-gate progress report in the actual Chat; no fake RED/GREEN, offline or long-term learning claims.
19. Add negative Kotlin/JUnit tests for missing, loaded-only and measured native cases, plus strict chat-command tests.
20. Add broad Node production boundary tests (more than 20 separate negative cases) and 30 contract assertions that retain existing CCC1 independent worktree scope, test identities and owner grants.

**This completes an implementable engineering batch**, NOT Gemma execution on the Fold7. The original official model must be imported and *native inference* performed on the owner device to establish that separate result. A real model-authored CCC1 patch with independent RED→GREEN→fresh GREEN and restart-memory replay still remains the final scientific milestone.

## Real test gate

- `node --test scripts/continuum-m2-model-preflight.test.mjs .github/continuum/m2-owner-brain-contract.test.mjs`
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin :app:assembleDebug` under existing Android QA configuration.
- Full `bash scripts/continuum-owner-current-v25-qa.sh` on an existing **non-owner** runner, exact commit pinned, static artifact only.
- Only declare a QA milestone when actual Render build logs and exact `STATUS.txt` match. No signed release without re-frozen manifest, independent owner approvals, owner certificate and on-device acceptance.

The rest of the project remains open, editable and independently reviewable; `main` and Astra's research branch are unaffected.
