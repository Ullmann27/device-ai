# Avatar ocular transition audit

Status: WIP / NOT_RUN. Runtime remains unchanged. Target: Cinema root integrator, PR #715, `cinema/fluid-light-stage-20261110`. Own branch: `fix/avatar-idle-light-audit-20261110`.

Base verified before edits: local `a7b4d1a090ab7117f29f6fcc3440b60b863395a5` and PR715 remote `5457351a7585eada2393f80aa81db4bff5cc8897` share tree `6906b84e8434d4247a759ac0e7cf701ad82416dc`.

The actual Filament renderer uses AvatarMorphMapper, splits Neck/Head rotation, updates both eye joints and uploads the original bone matrices. Window light consumes the shared projected-window snapshot independently of gaze. No alternate renderer, clock or pose engine is proposed.

Candidate defect: idle ocular offsets bypass the existing idle-layer fade, work ocular sway bypasses the work-layer fade, and gaze stabilization switches immediately when leaving/entering idle. A state change can therefore replace eye rotation without elapsed render time while head layers already crossfade. The focused test checks IDLE ↔ LISTENING/THINKING/SPEAKING at the same timestamp and first subsequent frame, plus intentional immediate reduced-motion settling.

Planned minimal fix: reuse existing idle/work layer weights for ocular offsets and crossfade the existing stabilization gain with idle weight. No new state, clock or speech source. Pure-host RED/GREEN requires the integrator's compile lease. No Gradle, Blender, native GPU, microphone or physical-device work has run for this checkpoint.

Original GLB SHA-256: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`; 21 targets, bytes unchanged. Speech timestamps, visemes, mouth mapping, joint split, lights and camera remain unchanged. Home/Shell/WorkWindow/AgentActivity are outside ownership.

Next: reproduce on the actual mapper, apply only if the regression fails as predicted, run bounded existing mapper/rig tests, publish exact source evidence for root integration. Rollback: revert this isolated step; no asset or persisted-data migration.
