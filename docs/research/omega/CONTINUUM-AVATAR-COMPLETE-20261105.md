# Continuum Avatar completion — integration handoff

Status: SOURCE_GATES_PASS / APK_ASSEMBLY_PENDING / NOT_RELEASED.

## Source and authorization

- Repository: `Ullmann27/device-ai`, public, owner account verified.
- Baseline: PR #543, `89ae402d88b80ca5c48ab241e20c0369b7db517d`, tree `70c3d700a36e4e88850843fa9b392db92d99a388`.
- Branch: `integration/continuum-avatar-complete-20261105`.
- Owner requested three assistants, completion of the existing reference design,
  connected avatar/dialogue/camera consent and an update of the existing app.
- Preserve C1 compact layout, memory, tool gates, original avatar asset and orb.
- EXP-05/06/11/12 remain compatible; no research-weight or memory promotion.

## Implemented connections

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

## Completed source and verification, 5 November 2026

The tested implementation is local commit
`5a50af1129248a7031ff3c803e5de8540a65f197`, tree
`7563a881220c143df6fa8dfd04fd84baf5207ef7`. Its exact tree is published as
[`0628c38ba96b9012887f209425aa5c0a7e69944e`](https://github.com/Ullmann27/device-ai/commit/0628c38ba96b9012887f209425aa5c0a7e69944e)
in draft PR #544. Later documentation commits do not alter executable source.

- Retained the original #535 GLB, framing, aura and reference colors. Repaired
  ambient-effect recovery and obsolete framing/label assertions. The native
  Filament head is not represented by Java2D or Robolectric preview evidence.
- Connected the existing Camera2 lease to separately consented local face
  enrollment/comparison, while normal gaze mode continues without pixel analysis.
- Added pinned YuNet detection, SFace features and quality/multiple-face gates.
  Eight separated enrollment frames produce an encrypted Android Keystore/AES-GCM
  profile in no-backup storage; no photo or template is transmitted or logged.
- Connected session tokens and shared profile revisions to frame admission,
  worker results, staged profile replacement, status callbacks and watchdogs.
  Stop, deletion and replacement revoke old comparisons and late saves.
- Added explicit enrollment/comparison/deletion controls and a visible camera
  stop. Only the explicit conversation action continues an approved comparison.
  A current match can greet once through the existing TTS/lips path; stop,
  expired match and layout changes stop that greeting without cancelling other
  conversation speech. Recognition grants no authentication or tool authority.
- Made ARM64 packaging explicitly opt-in for this Fold7 owner build. Existing
  package, original signing, version, release freeze and QA gates remain intact.

| Actual check on the unchanged implementation tree | Result |
| --- | --- |
| Full Android unit and native-widget host suite | 3152 total; 3133 PASS, 0 FAIL, 19 SKIP |
| Configured Android debug lint | PASS; 0 reported issues |
| Full Node suite with actual Kotlin CLI 2.4.0 available | 2404 total; 2373 PASS, 0 FAIL, 31 SKIP |
| Backend suite | 64 PASS, 0 FAIL, 0 SKIP |
| New real-controller session/race cases | 16 PASS, included above |
| Actual Activity face/speech bridge and dialog cases | 7 + 10 PASS, included above |

The host suite's skipped cases are recorded explicitly, not counted as passes.
The exact test commands, source identities, report hashes and skip identities are
in `evidence/continuum-avatar-source-qa-20261105.json`. Earlier red runs are not
relabelled: the initial shell/frame fixture failures, obsolete portrait assertions
and unattached screenshot fixture were repaired and then exercised by this full run.

The real 360dp face-dialog screenshot was inspected for text clipping, overlap,
control reachability and disabled states. It shows the actual initial UI, not an
enrolled person or a device camera. The model assistant's separate actual Linux
inference and pinned-license/ABI evidence is in
`CONTINUUM-OWNER-FACE-MODELS-20261105.md`.

## Final artifact and publisher continuation

The verified existing update remains versionCode41 / 0.3.40, package
`at.heinz.deviceai.continuumlocal`, original certificate SHA-256
`6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
The intended next version is code42 / 0.3.41; reread the live catalog before
publication to avoid reusing a version. This file does not claim that code42
has already been assembled, signed or served.

Build the clean final commit with `continuumOwnerArm64=true` and inspect the
actual APK's package/version, every native ABI/16KB LOAD segment, ZIP alignment,
both model hashes and original GLB hash. The original publisher service is
`continuum-owner-v23-release-preflight` on its existing URL and original private
signer. Its authorized branch can advance only by checked fast-forward; do not
merge main or change signing material. Set the exact final source SHA in the
existing approval/freeze variables and pass
`ORG_GRADLE_PROJECT_continuumOwnerArm64=true` to retain the same property through
its mandatory QA and signed build. Read back the deployed catalog and APK bytes,
then independently verify the original v2 signer and matching SHA-256.

Heinz must explicitly enroll his own face on the device before a personal match
is possible. Real Fold7 camera frames, JNI execution, recognition accuracy,
audible speech, Filament rendering and install/update acceptance remain device
checks. Host tests and model shapes are not proof of those outcomes.

## Rollback

Revert only this branch's individual commits or return to baseline #543.
Do not reset another team's branch or remove existing evidence or user files.
