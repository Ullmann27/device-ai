# Original five-ring cockpit: focused verification

Source: `62c8602b1437c6b9f6637b2e101f603452089705`.
Published equivalent: `c3e9ddf4a7691a26b430b4e4a7778b41081b22f3`.
Exact shared Git tree: `4a56b1dbcc0e51ed41cdacf8f060b3e1a163b3c7`.
Main integration included through `7d6479f8c7c1c8e3c59f62399fb0a366676afe53`.

## Executed results

| Check | Result | Scope |
|---|---|---|
| Scoped Kotlin production compile | PASS | 31 changed production files and renderer dependencies |
| Pure JUnit tests | 37/37 PASS | Layout, original ring projection, world matrices, avatar framing and opacity |
| Native lifecycle and motion tests | 7/7 PASS | Feed identity, bound work events, retargeting, suspension and cleanup |
| Historical Canvas helper | 1/1 PASS | Retained separately; excluded from unified GPU acceptance |

The seven pure suites are CinematicWorkstationLayoutTest (7),
CockpitMonitorMotionTest (3), CockpitWorldTransformsTest (6),
CockpitStageFrameTest (5), CockpitOrbMotionTest (2),
AvatarMaterialVariantTest (4), and AvatarFramingTest (10).
Native coverage is CockpitMonitorLifecycleUiTest (3) and
CockpitMonitorMotionUiTest (4). CockpitMonitorPixelsUiTest is the separate
historical helper check.

Ring tests read actual packaged GLB vertices and compare their authored
geometry and phases with the original ContinuumOrbitalRing.project function.
They use actual projected bounds and front/far-plane values rather than assuming
that the complete orb, including its static pedestal, is a radius-1.72 sphere.
The skinned-avatar checks cover phone/Fold layouts, font scaling and the
complete sampled monitor flight.

## Execution binding

All three commands exited zero. Product and test hashes remained unchanged
before and after execution. Receipts contain exact commands, dependency
classpaths, source hashes, frozen baseline-jar hash and execution times.
Original-avatar SHA-256 remains
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

Kotlin 2.4.0/JDK 17 compiled changed native source against frozen integration
classes and Android/Filament dependencies. UI execution used Robolectric
4.14.1, SDK 28, NATIVE graphics and the native test resource APK.
The combined run acquired the shared agent-gradle.lock and released it before
the final render job. Paths are normalized to CHECKOUT, QA_OUTPUT,
INTEGRATION_BUILD, GRADLE_USER_HOME, JDK, PRIOR_QA_BUILD and SESSION_WORKSPACE
variables; hashes and outcomes are unchanged.

Actual asset rebuild and original-five-ring/material verifier results:
`../cockpit-five-ring-materials-20261112/`.

## Limits

This is a scoped compile and focused host test run, not a complete new
Gradle build, APK installation, physical Fold7 capture or Filament GPU result.
No FPS, thermal, device-render or actual application capture success is inferred.
Host example content is confined to the review tools and is not inserted into
the application's live feed.
