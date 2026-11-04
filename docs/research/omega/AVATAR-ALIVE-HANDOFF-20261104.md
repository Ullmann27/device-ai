# Avatar alive — implementation checkpoint

Status: WIP / UNVERIFIED. Owner instruction on 2026-11-04: animate the approved corrected Continuum avatar through the existing application path.

## Pinned starting point

- Official parent: PR #521, `2350866904cb82484148c0c8c63416fa51034269`.
- Local execution twin: `a6f0b09bf53229ef40f039e0418e811852d32a11`, tree `c26e1f73d49901c7e9beb6f5fe8b639e5736702a`.
- Owner-reviewed head candidate: SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`. Its local corrected GLB, NOTICE and runtime hash must be imported together and verified before promotion.
- Own branch: `codex/continuum-avatar-alive-20261104`; original worktree remains untouched.

## Bounded implementation

1. Preserve the reviewed head, the existing 21 morph targets and the single Director/View/frame clock.
2. Reproduce and import the existing negative-cycle sampler correction from PR #520.
3. Correct display-camera eye contact in the current native mapper/renderer without replacing owner-controlled hardware gaze input.
4. Connect a subtle resting smile through the existing state/expression path; preserve neutral speech, blocked/approval states, articulation and STOP behavior.
5. Verify real source-bound Director → reducer → mapper behavior, current asset provenance, Android compilation and available lifecycle tests.
6. Render a short sequence from actual compiled production poses and the corrected GLB. Mark synthetic input, host rendering and device limits explicitly.

## Design decisions

Use existing Kotlin/Filament components and packaged local model. The alternative of independent animation clocks or generated video would not verify the actual app path. Default facial warmth is cosmetic, not a claim of inferred emotion. Speak/approval/error handling takes priority over decorative expression. No new cloud service, permissions, microphone/camera activation or persistent worker.

## Verification / remaining work

Baseline tests, new failing cases, fixes, Android build and rendered sequence: pending at this checkpoint. No prior parent tests are claimed as current-source execution. Native Fold7 pixels, audible synchronization and performance require actual device evidence.

No main merge, release, installation, paid API, model training or APK signature change. EXP-12 identity continuity and existing avatar contract remain applicable. Rollback omits this branch; previous branches and owner files remain intact.
