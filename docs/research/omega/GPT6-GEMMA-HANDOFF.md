# GPT6 Handoff — Model-aware local inference quality (25.10.2026)

Repository `Ullmann27/device-ai`, independent branch `fix/continuum-local-model-quality-gemma-20261025`, draft PR #149. Base: current shared infrastructure `integration/continuum-soul-infrastructure-20261025` (review PR #147). Android owner package must remain `at.heinz.deviceai.mentorpaired`. Existing signing identity and all memory/weight data untouched. Do not merge into `main` without independent review.

## Problem and original wiring
`LocalQwenPromptPolicy.directUserPrompt` forced `/no_think` on every ordinary Qwen3 direct prompt, impairing compatible thinking checkpoints. `LiteRtLmLocalModelRuntime` hardcoded `maxOutputToken=512` in its three `ConversationConfig` constructors, including separate tool-free thinking. These behaviors apply before the model's actual ability is measured. Owners want Gemma4-E4B to replace Qwen after SHA verification and successful Fold7 device import and inference.

## Exact source changes
- `app/src/main/java/at/heinz/deviceai/localmodel/LocalQwenPromptPolicy.kt`: retains compatibility method and strict bounds; no automatic `/no_think` injection. Explicit owner-provided model syntax remains untouched. No privileged tool tokens injected.
- `app/src/main/java/at/heinz/deviceai/localmodel/LocalModelGenerationPolicy.kt`: bounded 1024 tokens for legacy/unknown model IDs, 2048 for Gemma4 E4B/E2B model identities, locale-independent.
- `app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt`: uses policy in all 3 conversation constructors; loaded Gemma identity selects its own limit. No change to CPU engine backend, model catalog, signing or ToolGate.
- Kotlin JUnit policy tests cover original prompt identity, bounding, explicit user switch, family detection and fallback.
- `scripts/continuum-local-gemma-qa.sh`: separate pinned source full Node/backend + Gradle unit/lint/compile QA without signing/APK creation.

## Gates
EXP-05 on-device generative model and EXP-12 checkpoint continuity. Reproducible QA:
`CONTINUUM_GEMMA_QA_SHA=$(git rev-parse HEAD) bash scripts/continuum-local-gemma-qa.sh`
The Render runner has to use exactly the pinned SHA, and only its own source. Results are not claimed until logs are actually inspected. Genuine Gemma `.litertlm` compatibility, successful Fold7 initialization, latency/thermal behavior, German answers and native inference quality remain **NOT RUN** on the device. A higher output cap alone does not prove increased intelligence; checkpoints may have different native limits.

## Safe next integration
Read PR #149 and compare against PR #147 head before merging. Only promote after Android Kotlin compile/lint tests. On Samsung, import Gemma and compare `Continuum Modelltest Direkt` against Qwen with model fingerprint and latency. Do not remove Qwen or its catalog until Gemma is known-good and owner approves. No APK, server model, weight download or privileged shell execution performed in this branch.

## Rollback
Revert exact isolated commits on this branch or leave PR unmerged. Model registry, training checkpoints, main branch and existing installed APK are unchanged.
