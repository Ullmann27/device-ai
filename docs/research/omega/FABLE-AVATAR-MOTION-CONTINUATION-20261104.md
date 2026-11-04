# Fable avatar continuation — 2026-11-04

Task: **FABLE-CONTINUE-AVATAR-001**. Base: Fable PR #500 at
`0e3685a3bc6cb3a4af61988450371f11e9cb0f3c`. Stacked Draft PR: **#502**.
Branch: `feature/continuum-fable51-avatar-motion-20261104`.

## Delivered scope

- New production `AvatarIdleMotion.kt`: deterministic cosmetic gaze targets,
  3–5 s intervals, 180 ms zero-end-velocity/acceleration transitions, existing
  small head harmonics, primary 150 ms blink plus occasional separate double blink.
- The real `AvatarMorphMapper.update()` consumes these outputs. It still uses
  the existing renderer frame clock. No new timer, listener, executor or runtime state.
- Actual observed gaze and non-idle runtime gaze retain precedence. Existing
  70 ms viseme crossfade, jaw compensation, reduced-motion and interrupted-speech
  behavior are unchanged. No memory, consent or tool logic has been modified.
- An executable host-limited checker and original RED/GREEN logs are included.

This is **source implementation plus focused host verification**, not completion
of Fable priority 1 or the complete avatar task. Geometry, materials, asset bytes,
asset digest, renderer selection and visual reference status are unchanged.

## Real execution evidence

The baseline mapper fetched through GitHub was copied locally and its Git blob
hash matched `546832d4c4b7248fb865154879744eeb0be9ab05` exactly. The unchanged full
AvatarContract source matched `d9a0f6b1d72d2b9107d620526e311e41007a573d` exactly.

Checks were written and executed before the implementation:

| Run | Exit | Result |
| --- | --- | --- |
| Original mapper / new requirements | 1 | 23 passed, 3 failed |
| Changed mapper + new motion component | 0 | 26 passed, 0 failed |
| Full Android test command in partial workspace | 127 | UNAVAILABLE: Gradle wrapper/full checkout absent |

The three RED cases are missing requested fixation/double-blink behavior, not
three newly discovered safety defects. The GREEN run includes checks preserving
speech/jaw transitions, STOP/reducer behavior, observed-gaze precedence, name
mapping, finite bounds, clock wrap and skipped-frame determinism.

Environment: Kotlin 1.9.0, Java 21.0.12.1, compilation target JVM 17.
Evidence: `docs/evidence/fable-avatar-motion-20261104/`.
Reproduction and baseline override: `tools/fable-avatar-motion-check/README.md`.

**Important fixture boundary:** the real mapper/reducer are compiled, but only
the exact TARGETS declaration is extracted from AvatarGlbContract into a temporary
metadata fixture. The org.json GLB parser and native/Android components are NOT
executed. These are not JUnit or complete Android-suite results. No independent
reviewer/agent ran the tests; they were performed by the coordinator in this session.

## Rulings / environment limits

FABLE_JARVIS_HANDOFF.md, NEXT_AGENT_INSTRUCTIONS.md, AGENTS.md and experiment
contracts were read. This is presentation work; no EXP-00/05/08/11/12 capability,
model, worker or continuity gate is promoted by it.

Direct git/raw binary download was unavailable while authorized GitHub text
reads/writes worked. Local work used a hash-checked **partial source snapshot**,
not a claimed full checkout. Android SDK/adb, Gradle and Blender are unavailable
here; no screenshot, APK, GPU run or Fold7 result was produced.

Ruling: do not modify the reference face or GLB materials blind. The original
priority 1 reference geometry/material work remains open. Execute the safely
checkable part of priority 2 rather than substituting a fake visual result.
The requested new ACKNOWLEDGING/SLEEP states and 40/90 ms coarticulation remain
unimplemented; existing tested behavior is intentionally retained in this slice.

New independently launched coding agents: **0**. No permanent/background work is
claimed. Three logical responsibilities (implementation, regression review,
handoff) are not counted as separate bots.

## Integration, next steps and rollback

Review #502 against the live #500 head and relevant #491/#495/#499/#494/#489 work
before merging. No local merge-tree or cross-branch integration result is claimed.
Do not overwrite those branches or replace their Activity sources. This PR changes
only two production paths in avatar3d plus its own test tooling/evidence/docs.

Next: run the existing full Android suite/lint with the real GLB in a complete
checkout; validate native rendering on the actual selected 3D path; then continue
Fable priority 1 reference geometry/material comparison with current screenshots.
The default 2D fallback is unchanged, so this PR is not evidence of a changed
installed app or finished dashboard.

Rollback: revert this PR's two production changes together. No data migration.
Main, package/version, original signer, update channel, memory and model weights
are untouched. No Actions, S01, model training, sign/release/deploy or installation
was triggered. Both Fable source branches are preserved.
