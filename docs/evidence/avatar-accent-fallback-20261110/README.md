# Avatar projected-window accent fallback

HOST VERIFIED / ANDROID PENDING. Integration owner: root, PR #739. Base `a07b183b67b9c00192bb96b61a0002f74e23372b`.

The existing Filament render path captures one `CinemaWindowLightState.Snapshot`. Its previous `open ? accent : fallback` selection loses a valid scene accent when projected geometry is open but its own accent is null. The resulting light keeps the projected position but incorrectly uses the neutral color/intensity.

The selection is extracted into `Snapshot.accentOr` and called by the actual renderer. An open snapshot now falls back to the supplied scene accent only when its own accent is null; explicit projected colors still win, and closed snapshots still use the scene fallback. A new regression drives that selection into the existing `SpatialWindowLight.tint`. Focused 256 MiB Kotlin/JUnit run: 8 tests, 1 expected failure. Open geometry with null accent lost the violet fallback; explicit projected accent, closed state, absent colors and existing lighting tests pass. The identical suite passes after the one-line null fallback: 8/8 GREEN, no failures. Geometry remains sourced from the same immutable snapshot.

Android/Filament build, GPU and Fold7: NOT_RUN by this worker. Root owns combined source verification. No asset, rig, light-position, quality-gate or clock change; no signing/release/main merge. Next step: integrate these two commits into PR #739 and run the combined Android checks. Rollback: revert this feature's commits.

The focused host compile used Kotlin 2.4 with `-Xmx256m`, compiling `AvatarCoreLighting.kt`, `SpatialWindowLight.kt`, `CinemaWindowLightState.kt` and `SpatialWindowLightTest.kt` against JUnit 4.13.2. JUnitCore ran `at.heinz.deviceai.avatar3d.SpatialWindowLightTest` with 256 MiB. The Filament caller was reviewed, not executed by this pure-JVM run; root must compile the combined caller. `red-tests.txt`, `green-tests.txt` and `RESULT.json` retain exact outcomes and source hashes.
