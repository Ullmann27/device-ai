# Code48 dashboard visibility and load

Status: LOCAL_ONLY / NOT_PUSHED. Base `be256a1ae3f31fa04f0acdd7c7537a43beb219b3`.
Experiment compatibility: EXP-02 / EXP-07 repair evidence; EXP-05 / EXP-12 model and checkpoint contracts unchanged.

## Cause and change

`AgentActivity.dashboardRefresh` called the collector every five seconds whenever the Activity was resumed, even when CHAT hid the cockpit. The collector reads learning status/timeline, scans app file sizes, reads the backlog and diagnostics. The Activity now submits that work only while the cockpit or hardware gauges are shown. Returning to the cockpit and opening system gauges request fresh telemetry immediately; the existing in-flight guard coalesces repeated requests. A pending result updates shown surfaces only, and does not update presentation after pause.

The small shared five-second callback remains for the bot roster's visible runtime status. It stops when the Activity is paused/finishing/destroyed. The 33 ms `voiceFrame` already requires active speech and foreground state; it remains unchanged. No rendering quality, model, storage schema or scheduler was replaced.

## Executed checks

- Actual Activity pane/navigation and Android View visibility, controlled queued telemetry executor, Robolectric SDK28.
- RED: 4 tests / 3 failures. Hidden and detached surfaces queued reads; returning home did not request an immediate read. The visible system dialog control passed.
- GREEN: 4 tests / 0 failures. Activity production source compiled against Android SDK36 with Kotlin 2.4.0/JDK17.
- Test and production compile used cached Gradle dependencies/classes from `6a6f885f952784c79b209ad39b20d14ccf014df8`; `git diff` verified its `app/src/main` byte-identical to the base before this patch. GREEN puts the newly compiled Activity ahead of those classes.
- Exact executed commands, file digests and raw JUnit output: `docs/evidence/code48-dashboard-20261107/`.
- `git diff --check`: PASS.
- Complete integrated Gradle suite/APK, physical Fold7 temperature, battery draw and real GPU: NOT_RUN in this subtask. No measured temperature reduction is claimed.

## Reproduce in a configured local Android build

```sh
gradle --no-daemon --max-workers=2 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumStandaloneVersionCode=48 \
  -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true \
  :app:testDebugUnitTest --tests at.heinz.deviceai.ContinuumDashboardVisibilityUiTest
```

The command above is the portable full-Gradle follow-up, not the focused command already executed. The coordinator owns the full combined source gates and unsigned APK. No signing keys were accessed and no release/deploy was performed.

Next: integrate the small Activity hunks with the independent PDF/presentation fixes, then run the combined suite. Rollback: revert this commit; it does not migrate or delete owner data. Device acceptance should compare long CHAT vs START sessions, live system gauges, pause/resume and TTS under the same model/workload.
