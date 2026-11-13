# Jarvis55 connected cockpit integration

Status: WIP / UNVERIFIED.

Base: #811 `8a882e662cb48e3363213eb5005cb091828d89ec`, descendant of #809 `779863955825b65931ef569dde79a6d2f8f8c771`. #808 reflection orientation is already ported.

Integrator branch: `work/jarvis55-master-20261113`, targeting #809 after review. Three authorized development lanes: graphics, cognition, Android reliability. These are development workers, not evidence that phone agents are running.

Scope: preserve existing Filament, avatar, five-ring orb, cognitive and permission paths; improve real monitor presentation and geometry-bound attention, interrupted task continuity, and actual computer evidence. Build an exact-source APK candidate with unchanged original package.

Existing attention changes from the earlier workspace were reviewed, extended and tested before adoption. Full QA on parent run 43297198210 failed; scoped run 43297197973 is insufficient for full acceptance.

EXP-00/05/06/07/08/11/12 compatibility maintained. No model-weight promotion, extra renderer/event bus, main merge, signing-key change or permission bypass.

Validation pending: focused Kotlin/Node, full native Gradle/lint, source-bound host frames, APK inspection. Fold7/GPU/voice/live projection/thermal tests require a connected authorized device and remain NOT_RUN until observed.

Rollback: revert this branch's commits; preserve all upstream work.

## Connected attention checkpoint

- Restored and reviewed prior attention work: approval/blocked/listening/speaking suppress directed work gaze; fresh work can resume after a pause, STOP retires late events.
- Removed fabricated fixed monitor coordinates from ComputerWorkAvatarBridge. Gaze consumes the actually submitted capture binding, crop, image dimensions and projected CockpitStageFrame. Uses the existing JarvisWindowGaze adapter relative to the laid-out head region. This is a geometric gaze target, not measured eye fixation.
- Reuses the existing render/status callback to track moving monitors. Missing/stale/mismatched pixels yield no work target; rendering never dispatches actions.
- Fullstage memory rendering now restores the existing orb renderer dimensions immediately, including unified-3D scenes that do not draw the old 2D orb next frame.
- Legacy UI fixtures follow the shared head/orb scene and ambiguous two-app selection; no production permission rules changed.
- Native UI/Filament verification remains pending; focused JVM source hashes and checks are recorded with this checkpoint.

## Work surfaces and source continuity

The existing side-panel drawing path now presents the accepted CockpitWorkReducer snapshot. Left: app, bounded goal and actual stage/action. Right: pending/checked-step/checked-task distinction and workflow result. The existing controller binds intent and final result to the current task/session, clears displays on stop, and preserves panel state during dashboard refresh. No parallel event bus or Android dispatcher.

Source-bound host attention/action suite: 65 passing. This includes rejected stale/duplicate/foreign events never reaching the panel callback. New side-panel rendering tests and full Gradle compilation are pending at this checkpoint. Native PDF provider instrumentation now records actual query/open counts and source guards. No Fold7 proof yet.
