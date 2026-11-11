# Omega scene continuation — 11 November 2026

Integration owner: root, `integrate/omega-apk54-20261111`, based on PR #768
`3cb8df27c42fbc6db03d05b0f9993a8650be43af`.
Working branch: `work/omega-scene-20261111`.

## First bounded repair

- EXP06 currently gates the Activity window flag only. The renderer combines
  it with `CinemaWindowLightState.snapshot.open`, re-enabling window lighting.
- EXP11 currently gates the orb pulse only. The overlay's transfer band and
  receipt packet do not read that preference.
- Repair the complete rendering paths; retain the studio/core light, independent
  receipt rim and original avatar/orb assets. Verify live preference changes.

## Next bounded milestone

Review the P3 shared Filament scene against the actual view hierarchy. Add only
an attached, default-off path with its native fallback intact. No second engine,
renderer, frame clock or unbounded texture/geometry allocations.

## Evidence

Initial state: source inspection only. Tests and all device/GPU acceptance are
`NOT_RUN`. Root owns the initial P1 Gradle run. Any later Gradle work must use
`flock /tmp/continuum-omega-gradle.lock` and a source-bound evidence directory.

No package, signing, release, installation, model weights or owner data changes.

## Bounded switch repair implemented

EXP06 now reaches the renderer as a separate permission for window lighting.
`CinemaWindowLightState.resolve` applies it before combining projected and
physical-window sources. OFF restores neutral position, tint and roughness;
the existing studio/core illumination remains. No event is replayed on re-enable.

EXP11 now controls the overlay transfer band and its travelling receipt.
EXP12 still independently accepts a verified receipt and lights the window rim.
The existing preference listener updates an already-open window. Reading
attention remains tied to actual work, STOP, reduced motion and budget; it is
independent of the orb link. Its separate owner switch remains open work.

Actual evidence in `docs/evidence/omega-scene-20261111/`: source gate contracts
RED 0/2 on unchanged base production; GREEN 10/10 including neighboring Node
contracts. Direct Kotlin/JUnit: 13/13 (five new gate cases and eight neighboring
light cases). Native Canvas now has two additional behavioral cases (seven
total), not yet executed. Root will run the combined Android/Canvas checks.
No claim of Android compilation or native/GPU verification from these tests.

## P3 scope decision

The native TextureView is not the only small viewport. `ContinuumHomeView`
measures and lays out the entire hologram inside `Slot.AVATAR`;
`ContinuumHologramView.drawChild` also masks the texture to `headDrawBounds`.
Moving monitor entities into the avatar scene alone would clip them. A complete
shared path therefore requires coordinated Home viewport, camera origin, head
mask and stage readiness changes. Root explicitly defers that broad change for
this APK candidate; the existing PDF stage remains the active path. No partial
renderer or unused default-off scaffolding is introduced by the switch repair.

Rollback: revert the switch repair commit; original assets and application data
are unchanged. Device/GPU/thermal acceptance remains `NOT_RUN`.

### Neighbor contract follow-up

The existing radiance source contract pinned the former inline resolver (1 PASS,
2 FAIL after the fix). It now follows the same accent fallback through the
production resolver. All former fallback/closed-source mutations remain and
three new resolver-gate mutations were added. The combined three-file Node
scope passes 13/13. Production files are unchanged by this follow-up.
