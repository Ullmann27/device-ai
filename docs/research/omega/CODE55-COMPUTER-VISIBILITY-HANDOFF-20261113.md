# Code55 computer visibility stop

Status: SOURCE_INTEGRATED / PARTIAL_HOST_VERIFICATION; corrected completion fixture awaits rerun; device acceptance open.

- Base: `058d754b82e134b3eec0e3e3f21b788c46c8c479`.
- Patch branch: `work/jarvis55-visibility-reason-20261113`.
- Integration owner: current Code55 master integrator.
- Review target: `work/jarvis55-master-20261113`.
- Final APK integration: `integrate/code55-apk-fable-20261112`, after exact-source QA.
- Related contracts: EXP-07 regression evidence, EXP-11 existing orchestration. No new runtime agent or renderer.

## Change

An active computer session previously stopped silently when fullscreen Continuum lost visibility. The old worker callback was then correctly rejected because its plan had been cleared.

`AgentActivity.onPause/onStop` now call `OwnerComputerSessionController.stopForVisibilityLoss()`. It first uses the existing stop cleanup, revoking the grant, cancelling the workflow, ending capture and clearing feed/panels/plan. Only when a workflow is still pending, it then sends one CANCELLED reply explaining the stop and asking the owner to use split-screen and restart after checking the target app. The existing controller reply callback carries an explicit speech permission: only this notice passes false. No TTS/Maya path is entered for the notice.

Pending/deferred Android capture consent remains exempt. A visible split-screen pause keeps the existing session. Inactive/duplicate lifecycle calls produce no message. Completed workflows retain their original result when their remaining capture is closed; they receive no cancellation or restart instruction. Explicit owner STOP, correction and close use unchanged silent `stop()`; their existing caller replies remain intact. Plan identity still rejects late worker completions. Source/grant/capture/render-receipt gates are unchanged.

## Verification

Added `OwnerComputerVisibilityUiTest` using the real Activity/controller lifecycle, existing accessibility/capture shadows and a controllable executor:

1. Fullscreen pause/stop: one message, revoked grant, cancelled workflow, cleared feed/panels; enabled TTS stays silent, no Maya creation, no ASR restart or new active model turn.
2. Queued worker completion cannot replace the lifecycle reason.
3. Inactive pause adds no reply.
4. Split-screen pause keeps work; stop ends it once.
5. Pending and deferred Android consent survive lifecycle hiding.
6. Owner STOP keeps its existing reply.
7. Owner correction keeps its existing reply.
8. Ordinary verified reply retains its existing speech policy.
9. Actual normal completion retains its verified reply on pause/stop, with plan/capture still retained and workflow already null before cleanup.

Compatibility: all 126 existing UI Kotlin files at the pinned base were searched for direct/reflected controller/reply callers. The one existing three-argument reflection helper in `OwnerAppPlanningContextUiTest` now supplies the explicit speech flag. The new consent case reuses that harness's existing projection-consent shadow.

First integrated host run: `f505f97397ff14c98a7c77709f9818fcf337e707`, [43364020806](https://github.com/Ullmann27/device-ai/actions/runs/43364020806), Android job 131684039738. Kotlin compilation passed; 372 focused tests executed, 1 failed, 0 skipped. Eight visibility cases and the existing planning class passed. The completion case failed its verified-reply precondition before testing lifecycle cleanup. Its fixture omitted the Android content-change event: `connect()` leaves the observation revision at zero, which the real workflow rejects. The fixture now emits the same service event as `AndroidLiveNodePortTest`, asserts a positive real observation revision, and retains every completion/lifecycle assertion. **Corrected fixture rerun: NOT_RUN.**

This editing environment has no local Gradle/Android executor. No GitHub Actions run was dispatched by this worker. The integrator must run the new class and existing computer/voice/cancellation regressions on the integrated SHA:

```sh
./gradlew -PcontinuumUiWidgetTests=true :app:testDebugUnitTest --tests at.heinz.deviceai.OwnerComputerVisibilityUiTest
```

Use the existing registered Code55 QA workflow for final variant/task selection, Kotlin compile, unit/UI regressions, lint and test APK. A host pass is not Fold7 acceptance. Original signing, installation and main merge remain separate gates.

## Integration / rollback

Review and integrate this bounded patch into the named master branch; include this class in the existing focused regression selection if needed. Revert this commit to restore the prior lifecycle feedback. No persistence schema, permissions, package identity, signing files or renderer changed.

PORTIERT: source patch prepared on the isolated branch.
VERIFIZIERT: 8/9 new cases passed on integrated f505f973; corrected completion fixture needs exact-source rerun.
GERÄTEBESTÄTIGT: NOT_RUN; fullscreen switch, split-screen and Android capture-dialog checks still required.
