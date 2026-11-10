# Document reading priority repair

Root integration owner: unsigned APK53 QA. Base `c7a8b7f415f738993df2f26b1d2b589ef90ea885`, tree `205ccc6b93ff9b4279f1f067dee3a0d6bdaaeaac`.

## Cause and connected correction

The full Android run reports two actual `ContinuumDocumentFlowUiTest` failures before STOP: `reading=true` is lost while a controlled model is still running. Code52 shared the physical window ID between the host and projection bridge. The Cinema bridge correctly introduced turn-scoped gaze IDs to reject obsolete events, but those IDs could open a new brief glance after the physical document window had started a real reading lease. That glance replaced the reading intent.

`AvatarDirector.requestWindow` now declines brief glances while its current reading intent remains valid. The existing expiration check runs first, so a missing end event cannot block later glances forever. Rejected glances are not retired; the same projected window can acquire a glance after work ends. Scoped bridge IDs, stale-turn protection, window-light updates and physical `windowMoved` updates stay intact. No AgentActivity or fixture-work simulation change was needed for the reading defect.

## Verification

Actual AvatarDirector source was compiled in both arms against the same integrator-built Android classes, with Kotlin compiler256MB and JUnit128MB. The final matched tests use bounded in-range gaze coordinates. Baseline24/26 (exactly two new reading-priority regressions fail); candidate26/26 PASS. Suites: `CinemaWindowGazeBridgeTest`, `AvatarWindowWorkReadingTest`, `AvatarWindowMovedTest`.

New coverage: physical reading remains active after projected turn change; physical window movement still changes both gaze axes; current light still tracks projection; old CLOSED/TurnEnded do not close current reading/light; workEnded permits the same pending projected glance; existing maximum work lifetime releases priority. An initial new-test expected pitch outside the existing directed-gaze clamp was corrected to an in-range fixture; both final arms were rerun unchanged.

The existing document STOP UI test retains immediate STOP reply, immediate reading cessation, duplicate-STOP and ABORTED checks, adds immediate CLOSING phase, and waits for canonical `HollywoodFuiConstruct.CLOSE_END_MS +64ms` instead of400ms. Its old at-once disappearance expectation contradicted the already integrated420ms controlled return. This UI-test change remains NOT_RUN here pending root's next Android run; no Gradle run was launched by this worker.

Native document images for this patch, physical Fold7, real PDF/model inference and hardware Filament remain NOT_RUN. Existing UI fixture explicitly uses a synthetic page and controlled runtime. It must not be described as real document understanding.

Integrate this isolated source/test/handoff commit on the candidate branch, rerun `ContinuumDocumentFlowUiTest` plus the three host suites, and retain the original full-run failures. Reverting this commit restores the prior glance priority and legacy STOP assertion. Main, signing, installation, original GLB and all memories are untouched.
