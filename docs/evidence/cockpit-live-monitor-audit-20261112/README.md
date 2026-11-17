# Live capture → cockpit monitor audit

## Exact scope

Audit base: public `5f87bdc610944b93d3173a83c5beeea315abeb66` and local
`9918891a6da1d74275b087a04f4aa86c79074d46` have the identical Git tree
`cae47c3abaee1a378b4157f26abf7e6ec33df2c1`.

This child implements the render-feedback correction in
`ab42bf6e7d03de1880c4b6bf37e558bdb8d910ca`, with status-label correction
`27e668aafa38c9faa89cf4d4e2d1c27b18ba752d`. Controller waiting, the final
native-action viewport check, and measured projection-delivery latency are
separate, coordinated integration changes. This document does not claim that
the child branch alone contains their final combination.

No scene design, model asset, preview renderer, or example image was changed.

## Observed image and action paths

The production path consumes real Android `HardwareBuffer` frames. The immutable
GPU copy enters `LatestLiveFrameHolder`, is leased and retained by
`CockpitLiveTexture`, and is passed to Filament's external stream. The actual
`monitor-live-pixels` primitive uses the unlit, opaque live-monitor material.
`sourceViewport` describes the crop in normalized coordinates of the entire
target window. The crop already exists in the buffer; shader UVs remain 0–1.

This is an actual capture path, but literal resolution or byte identity is not
its contract. `ContinuumLiveProjectionService.bufferSize` caps the longest
capture edge at 1920 pixels, `AndroidGpuFrameCopier` produces sRGB output, and
the monitor uses linear sampling and aspect-preserving fitting. The cockpit
world also uses its bounded render surface. Device-level orientation, color
calibration, and the actual panel are not proved by a host render or these tests.

Actual AI input reaches Android accessibility node actions through
`AndroidLiveNodePort`. The monitor's click marker describes an already
dispatched action. `VirtualComputerMonitorView` is a status view; it does not
implement a manual remote touchscreen.

## Confirmed coupling defect and correction

At the audit base, `LiveComputerDispatchGuard` accepted a fresh capture before
the monitor had ever leased or rendered it. The existing test
`freshBoundFrameAllowsInputWithoutLeasingOrConsumingIt` explicitly demonstrated
that behavior. A missing surface or failed texture import could therefore
coexist with a permitted native action.

`LiveMonitorSubmission` now keeps the source frame and a render-submission
receipt together. An accepted `setAcquiredImage` call only prepares a token.
`FilamentAvatarRenderer` confirms it only after both `render` and `endFrame`
return successfully. The guard requires both fresh capture and this fresh
receipt. `isReady` supplies the controller's initial wait, and `submittedFrame`
supplies the source viewport and sequence actually submitted to the monitor.
The visible `Live` label also requires the receipt; import readiness alone
continues to display the transfer status.

The receipt requires the same task/session binding, source window, viewport,
buffer dimensions, and source epoch. A newer capture sequence does not reject
an otherwise fresh submitted image. Capture age remains anchored to its
original sampled age; repeated rendering cannot make old pixels young.

Binding changes, capture pause/stop/cover, invalid surfaces, view disappearance,
detach, and import or render failures invalidate the receipt. The source epoch
also rejects an old snapshot if capture pauses and resumes before its delayed
import completes. A regular `beginFrame == false` skip neither creates a new
receipt nor discards a still-fresh old one. The view retains only an already
drawn image during that explicit skip; invalid surfaces and exceptions still
clear it.

This acknowledges successful API submission. It does **not** certify GPU
completion, Android composition, or physical display illumination.

## Adjacent confirmed crop case

The action agent owns the final correction for this independently identified
case: on a 2000×1000 display, a target window at `[1000,0,2000,1000]` and a
floating IME at `[1200,300,1800,600]` leave multiple visible regions.
`ProjectionCropGeometry` selects the largest rectangle, `[1000,600,2000,1000]`.
The node `[1300,100,1500,200]` is unobstructed according to
`LiveWindowSafety.permitsNode`, but is outside that displayed crop. A final
action check must therefore use the submitted monitor viewport, not merely
window visibility. This child does not change `AndroidLiveNodePort`.

The materials/capture agent separately checks projection-delivery age. The
unknown producer timebase of a queued `Image.timestamp` is not guessed here.

## Verification

The existing direct Kotlin/JUnit harness compiled current selected production
sources and six suites under the shared heavy-work lock. Result: **52 tests,
52 passed**, including 11 submission tests and 9 dispatch-guard tests. Source
hashes were unchanged across execution and match this implementation commit.

| Suite | Scope |
|---|---|
| `LiveMonitorSubmissionTest` | Import without render, fresh older sequence, preserved age, pause/import race, invalidation, changed crop/window/size, binding and clock errors |
| `LiveComputerDispatchGuardTest` | Missing capture, missing render, stopped/revoked/covered source, wrong task/session, lost surface |
| `LiveComputerFramesTest` | Existing latest-frame ownership and capture refusal contract |
| `LiveWindowSafetyTest` | Existing window and obstruction selection |
| `ProjectionCropGeometryTest` | Existing inward crop, split screen, keyboard and viewport mapping |
| `LiveWindowCaptureTest` | Existing capture identity and freshness contract |

The exact source hashes, compiler version, dependency-base hash, test classes,
and limits are in `verification.json`; `junit.txt` contains the actual result.
The test sources remain under `app/src/test/java` and can also be selected with
the repository's normal `:app:testDebugUnitTest --tests` target. That Gradle
command was not the execution method of this child run.

Native renderer compilation and the combined controller/NodePort lifecycle
checks belong to the parent's exact combined-tree gate. GPU imports, real
multiwindow interaction, and physical device presentation remain device
acceptance work. No host-only result here is labeled device-confirmed.
