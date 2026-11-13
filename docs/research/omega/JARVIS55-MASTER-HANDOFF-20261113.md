# Jarvis55 connected cockpit integration

Status: WIP / UNVERIFIED.

Base: #811 `8a882e662cb48e3363213eb5005cb091828d89ec`, descendant of #809 `779863955825b65931ef569dde79a6d2f8f8c771`. #808 reflection orientation is already ported.

Integrator branch: `work/jarvis55-master-20261113`, targeting #809 after review. Three authorized development lanes: graphics, cognition, Android reliability. These are development workers, not evidence that phone agents are running.

Scope: preserve existing Filament, avatar, five-ring orb, cognitive and permission paths; improve real monitor presentation and geometry-bound attention, interrupted task continuity, and actual computer evidence. Build an exact-source APK candidate with unchanged original package.

Existing uncommitted attention changes from the earlier workspace will be reviewed and tested before adoption. Full QA on parent run 43297198210 failed; scoped run 43297197973 is insufficient for full acceptance.

EXP-00/05/06/07/08/11/12 compatibility maintained. No model-weight promotion, extra renderer/event bus, main merge, signing-key change or permission bypass.

Validation pending: focused Kotlin/Node, full native Gradle/lint, source-bound host frames, APK inspection. Fold7/GPU/voice/live projection/thermal tests require a connected authorized device and remain NOT_RUN until observed.

Rollback: revert this branch's commits; preserve all upstream work.
