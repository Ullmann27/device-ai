# Fable avatar continuation — 2026-11-04

Task: FABLE-CONTINUE-AVATAR-001. Base: Fable PR #500 at `0e3685a3bc6cb3a4af61988450371f11e9cb0f3c`. Working branch: `feature/continuum-fable51-avatar-motion-20261104`.

## Current checkpoint

`WIP / NOT_RUN`: this initial checkpoint has no new app implementation or test results. FABLE_JARVIS_HANDOFF.md and NEXT_AGENT_INSTRUCTIONS.md were read. Their priority 1 visual/geometry work is still open; it is not declared complete or superseded.

Direct git/network downloads failed in this execution environment, while authorized GitHub text reads/writes work. No Android SDK, adb, Gradle, Blender or independently dispatchable coding-agent tool has been confirmed here. Java 21 and Kotlin 1.9.0 are available. No new independent agents were started; the coordinator implements and checks the bounded change itself.

## Bounded next implementation

Continue the executable portion of Fable priority 2: a deterministic, bounded idle eye/head presentation and occasional double blink, attached to the existing AvatarMorphMapper and existing frame clock. Preserve all existing mouth crossfade, jaw compensation, STOP/reduced-motion clearing and observed-gaze precedence. No new runtime states, listeners, timers, background workers or permissions.

Ruling: Do not edit the facial geometry or GLB materials blind. Binary asset transfer and GPU/visual validation are unavailable here. Deliver a reviewable motion implementation instead of falsely claiming a rendered reference match. The original asset, asset digest, geometry and renderer selection remain unchanged.

## Scope / ownership

Allowed production paths: a new `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt` and the small call-site change in `AvatarMorphMapper.kt`. Add focused tests/check tooling and this evidence/handoff only. Do not edit AgentActivity, existing avatar contracts, Filament initialization, memory, weights, APK identity, version, original signer or update channel. No main merge, force push, release, S01 or model training.

The new branch is stacked on Fable, not written into someone else's active branch. Local `git merge-tree` cannot be claimed without a complete checkout; cross-branch integration and the full Android suite remain separate gates.

## Verification plan

First demonstrate the missing behavior against the fetched baseline. Then execute focused Kotlin behavior checks against the changed source. Any declaration-only fixture used to isolate target-name metadata must be identified explicitly: it is not a GLB validation or Android build. Record real exit codes, source hashes, missing gates and reproducible commands. Existing mouth behavior is a regression boundary, not an opportunity to rewrite the speech pipeline.

## Completion / rollback

Publish implementation, evidence and exact final commit in this PR. Rollback is reverting this branch's motion changes; no data migration is involved. Reference likeness, native GPU rendering, speech synchronization, APK and Fold7 remain NOT_RUN unless separately evidenced.
