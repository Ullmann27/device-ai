# Continuum Avatar completion — integration handoff

Status: WIP / NEW_TESTS_NOT_RUN / NOT_RELEASED.

## Source and authorization

- Repository: `Ullmann27/device-ai`, public, owner account verified.
- Baseline: PR #543, `89ae402d88b80ca5c48ab241e20c0369b7db517d`, tree `70c3d700a36e4e88850843fa9b392db92d99a388`.
- Branch: `integration/continuum-avatar-complete-20261105`.
- Owner requested three assistants, completion of the existing reference design,
  connected avatar/dialogue/camera consent and an update of the existing app.
- Preserve C1 compact layout, memory, tool gates, original avatar asset and orb.
- EXP-05/06/11/12 remain compatible; no research-weight or memory promotion.

## Implementation plan

1. Avatar/dialogue assistant: trace actual TTS, viseme, gaze and camera paths;
   repair missing connections with focused lifecycle/consent regressions.
   Distinguish face detection from enrolled identity recognition.
2. Graphics assistant: compare #535 with #543; integrate remaining reference
   styling without overwriting compact routing, and repair ambient-loop resume.
   Preserve reduced-motion, visibility, power-save and touch-target contracts.
3. APK/QA assistant: independently audit source, signature/update channel and
   build prerequisites. Review integrated changes and report real test results.
4. Coordinator: integrate tested commits sequentially, run combined Android,
   Node and backend gates, build the same-package candidate and inspect metadata.
   Continue through the existing original-signature path only when its actual
   prerequisites are met. Never substitute a debug certificate or second app.

## Verification focus

- Camera starts only after understandable consent and Android permission.
- Denial, pause, detach and stop end capture and speech-driven motion.
- TTS timing remains synchronized; no independent accelerating animation clock.
- Focus/visibility/power-save recovery restarts eligible ambient effects.
- Fold7 wide/compact layouts preserve reachable controls and original identity.

Each checkpoint must record exact source, commands, outcomes and limitations.
Historical #527 and #535 test counts are not new combined-source evidence.
Physical Fold7, camera identity and GPU/voice acceptance remain NOT_RUN until
performed on the actual device. No main merge, paid service or automatic install.

## Rollback

Revert only this branch's individual commits or return to baseline #543.
Do not reset another team's branch or remove existing evidence or user files.
