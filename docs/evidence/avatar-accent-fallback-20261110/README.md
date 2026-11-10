# Avatar projected-window accent fallback

WIP / RED reproduced. Integration owner: root, PR #739. Base `a07b183b67b9c00192bb96b61a0002f74e23372b`.

The existing Filament render path captures one `CinemaWindowLightState.Snapshot`. Its previous `open ? accent : fallback` selection loses a valid scene accent when projected geometry is open but its own accent is null. The resulting light keeps the projected position but incorrectly uses the neutral color/intensity.

The selection is extracted unchanged into `Snapshot.accentOr` and called by the actual renderer. A new regression drives that selection into the existing `SpatialWindowLight.tint`. Focused 256 MiB Kotlin/JUnit run: 8 tests, 1 expected failure. Open geometry with null accent lost the violet fallback; explicit projected accent, closed state, absent colors and existing lighting tests pass. This checkpoint still reproduces the defect.

Android/Filament build, GPU and Fold7: NOT_RUN by this worker. Root owns combined source verification. No asset, rig, light-position, quality-gate or clock change; no signing/release/main merge. Next step: preserve fallback for an open snapshot whose accent is null, rerun identical tests, commit and push.
