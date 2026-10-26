# Sol grounding eligibility continuation — 2026-10-26

Status: WIP / UNVERIFIED. Official repository branch `sol/grounding-relations-20261026`, isolated worktree `/workspace/device-ai-memory-20261026`, starts at `c391b83244bd2cc945a1b37bcdd829da27e15ba6`. Integration target: `sol/continuum-brain-breakthroughs-20261026`; PR150 base `ad14f8b82f9be2cd0026bc16eef71a16bec41424` remains preserved.

## Bounded task and connections

Read `AGENTS.md`, the current experiment contract/register, Issue41, `SOL-BRAIN-HANDOFF.md`, current Node retrieval and Kotlin dialogue sources before code changes. Scope: mirrored `.github/continuum/dialogue-retrieval.mjs`, `ContinuumMemoryDialogue.kt`, related tests, and research evidence/handoff. Root owns main-dialogue/Gemma routing; no AgentActivity/localmodel edits.

Affected experiments: EXP-04 (retain CRE1 immutable admitted candidate pool), EXP-06 (source provenance/eligibility), EXP-07 (independent negative/holdout evidence). No new memory architecture, schema migration, weight training or promotion. CTX3/LM1 failed benchmark paths remain inactive. Existing facts, weights, revocation and conversation context contracts must remain intact.

## Current evidence and intended next step

Known open cases are multiple explicit identifier mismatches and unsupported ordinary subjects/relations: `Wo wohnen ROBOT-36 und MOTOR-52?` must not admit `ROBOT-36 und MOTOR-53 wohnen in Graz.`; `Wo wohnt Nemo?` must not admit `Leo wohnt in Graz.`; `Wo arbeitet Leo?` must not admit `Leo wohnt in Graz.`. Existing single-identifier fix is already included in the base; preserve its independent 74/96 positive recall and all no-ID golden scores where applicable.

Next: reproduce failures with RED tests, read existing LCT1/CSX proof/relation sources and their boundaries, choose the smallest supported eligibility rule, mirror exact Node/Kotlin behavior, run focused and broader regressions. Freeze a candidate before requesting independent newly authored benchmark cases from parent; do not tune against those heldouts.

## Tests / limits

At initial checkpoint: code changes NONE; tests NOT_RUN; Gradle/device/APK tests NOT_RUN. Parent will integrate and run heavy Gradle verification. Host standalone Kotlin checks may use actual precompiled classes with JDK17/kotlinc from `/workspace/continuum-toolchain`; distinguish host checks from Android/device validation.

Rollback: revert individual new source commits. No main merges, APK signing/installation, owner data writes, failed-path activation or foreign branch changes.
