# Avatar ocular transition audit

Status: PORTIERT / VERIFIZIERT on pure host; GERÄTEBESTÄTIGT: NOT_RUN. **41/41 tests PASS.** Target: Cinema root integrator, PR #715, `cinema/fluid-light-stage-20261110`. Own branch: `fix/avatar-idle-light-audit-20261110`.

Base verified before edits: local `a7b4d1a090ab7117f29f6fcc3440b60b863395a5` and PR715 remote `5457351a7585eada2393f80aa81db4bff5cc8897` share tree `6906b84e8434d4247a759ac0e7cf701ad82416dc`.

The actual Filament renderer uses AvatarMorphMapper, splits Neck/Head rotation, updates both eye joints and uploads the original bone matrices. Window light consumes the shared projected-window snapshot independently of gaze. No alternate renderer, clock or pose engine is proposed.

Verified defect: idle ocular offsets bypass the existing idle-layer fade, work ocular sway bypasses the work-layer fade, and gaze stabilization switches immediately when leaving/entering idle. A state change can therefore replace eye rotation without elapsed render time while head layers already crossfade. The focused test checks IDLE ↔ LISTENING/THINKING/SPEAKING at the same timestamp and first subsequent frame, plus intentional immediate reduced-motion settling.

Implemented minimal fix: reuse existing idle/work layer weights for ocular offsets and crossfade the existing stabilization gain with idle weight. No new state, clock or speech source. Pure-host RED/GREEN ran under the integrator's 384 MB compile lease. No Gradle, Blender, native GPU, microphone or physical-device work ran for this checkpoint.

Original GLB SHA-256: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; 21 targets, bytes unchanged. Speech timestamps, visemes, mouth mapping, joint split, lights and camera remain unchanged. Home/Shell/WorkWindow/AgentActivity are outside ownership.

Next: root integrates the focused mapper diff and runs combined-source caller checks; the separate original-avatar render worker may compare actual GLB frame sequences. No visual device acceptance is implied. Rollback: revert this isolated step; no asset or persisted-data migration.


## Source-bound result

- RED: **2 tests, 1 expected failure**. IDLE → LISTENING at the identical 2.4-second timestamp changed eye yaw from +0.630° to −3.0632906° (3.6932906° jump); immediate reduced-motion settling already passed.
- GREEN: **41 tests PASS**, 11 suites, JUnit time 2.193 s. Actual mapper, idle motion, critically damped spring and gaze-blink source recompiled. Existing head-follow, VOR, speech-accent, blink, viseme-transition, real GLB neck-skinning and 21-target contract tests pass.
- Exact compile/test commands, source SHA-256 pins and dependency JAR pin: `docs/evidence/avatar-ocular-transition-20261110/RESULT.json`. Kotlin/JVM and JUnit each used `-Xmx384m`; no Gradle or GPU work.
- The fix only scales existing ocular contributions by their already-smoothed idle/work weights and uses the complementary idle weight for VOR gain. Steady-state behavior and intentional reduced-motion snapping remain. No extra state, clock, speech event or phoneme source.

Suggested visual comparison: identical monotone 60 Hz poses, IDLE until 2.4 s then THINKING, with gaze/jaw/viseme values fixed at zero/SIL. Export before the transition and at +16/+96/+320 ms using the original GLB and actual mapper. Compare against remote base `5457351`; do not alter materials/mesh from preview alone. The numeric same-timestamp regression is an independent discontinuity probe, not a claim that a production frame has duplicate timestamps.
