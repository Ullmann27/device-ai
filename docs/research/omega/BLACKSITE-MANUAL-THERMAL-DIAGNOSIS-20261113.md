# Manual diagnosis thermal series

Source reviewed: `02c38cc03a2c5f8be0d38a47d3c3a4b0b9ff69d8`.

This connects the existing ThermalPhaseRecorder to the existing owner-started system diagnosis. It is a manual sequence of diagnostic snapshots, not workload-phase profiling or a Fold7 performance result.

## Production path

`Systemdiagnose → NEUE MESSREIHE STARTEN → JETZT ANALYSIEREN → existing ToolGate / DeviceToolExecutor → lastDiagnosis → ThermalPhaseRecorder`.

Only a current session/generation callback with SUCCEEDED, a certain outcome and no replay can submit a sample. The snapshot timestamp must fall within that request and completion interval and must be newer than the last accepted snapshot. The recorder must still be explicitly active in DIAGNOSTIC_SNAPSHOT. No previous snapshot is copied when recording starts.

Only the already captured Android thermal status and battery temperature are retained. Invalid or absent readings remain unknown. No additional AndroidSystemDiagnosis.capture, timer, thread, model request or scheduler is added. GPU, CPU temperature, thermal headroom and model timing remain unmeasured. Existing SystemPerformanceEvaluator comparison stays in place, with the truthful diagnostic-snapshot workload label.

MESSREIHE STOPPEN, the existing cancellation path and onPause stop recording. Resume does not restart it. A new explicit start creates an empty series. At 2,000 accepted points recording stops. The series is process-local; the existing save-report action still saves the individual diagnosis.

## Regression coverage

All four existing ThermalPhaseRecorderTest methods and all eleven SystemIntelligenceUiTest methods remain. Existing assertion lines are retained.

Five additional recorder tests cover opt-in/phase/stop, stale/future/replayed/out-of-order timestamps, exact numeric values and unknown fields, invalid readings, and bounded stop/new explicit session.

One additional UI test uses the existing diagnosis buttons and real host-side diagnosis path: diagnosis without opt-in records nothing; start does not copy an old snapshot; a newly completed diagnosis adds exactly one point and retains the existing baseline comparison; explicit stop, pause/resume and cancellation stop collection.

Source comparison and assertion-preservation checks were performed through the GitHub connector. Kotlin compilation, host tests, UI tests and Fold7 execution for this change: **NOT_RUN** here. They require the integrated candidate's CI/device validation. No frame rate, GPU load, thermal headroom or sustained-device performance is asserted.
