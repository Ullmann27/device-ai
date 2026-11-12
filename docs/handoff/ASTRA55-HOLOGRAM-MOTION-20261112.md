# Hologram monitor motion · 12.11.2026

Integration target: Code55 / PR #791 through the existing scene integration.
Source base: `9ffd3b193`.

## Connected change

`setLiveComputerFeed` drives one reversible 420 ms focus trajectory through the existing
`ValueAnimator`. `CockpitMonitorMotion.pose` supplies the same pitched, yawed and depth-shifted
frame to the existing Filament cockpit and native app-pixel view. Working and rest endpoints
settle. Rebinding the same target does not restart a flight. Background, hidden cockpit and
detachment settle and release the animator; system-disabled motion uses the endpoint directly.

Midflight direction changes carry current velocity into a short bounded braking segment,
then finish with a monotone Hermite segment. The position and velocity remain continuous;
no second animation clock is introduced. A reversal takes at most 480 ms.

Native foreground labels and questions remain above the room and monitor layers. The existing
foreground overlay is raised to 0.8 dp; app pixels retain their own unchanged layer.

The original avatar, sphere, app pixels and live-status rules are unchanged. The trajectory
retains the current workstation composition. No new render clock, engine, capture, model task
or permission path is introduced. Existing cinematic experiment settings are preserved.

## Verification

- HOST-VERIFIED: 14 focused Kotlin/JUnit tests, compiler exit 0 and JUnit exit 0.
- Projection checked against independent matrix multiplication for combined pitch, yaw and depth.
- All 101 sampled poses fit each of four phone/Fold/tablet geometries; pixel orientation remains correct.
- Finite-difference velocity checks cover four reversal points and the braking join. The former
  interpolator restart fails the velocity countercheck. 200 repeated reversals remain bounded.
- Four Android UI tests added for reversal, rebinding, lifecycle and reduced motion: NOT_RUN here.
- Full Android compilation, Filament rendering and Fold7 validation: NOT_RUN here.

Run the focused host classes `CockpitStageFrameTest` and `CockpitMonitorMotionTest`.
On the combined source, run `CockpitMonitorMotionUiTest` and `CockpitMonitorPixelsUiTest`
with `-PcontinuumUiWidgetTests=true`. The base scene branch needs its existing live-computer
dependencies from the shared integration before compiling the complete app.

## Required renderer transform

`FilamentCockpitStage.render` must translate the monitor by `frame.depthOffset` along Z,
then apply Y yaw and X pitch before scaling: `T * RY * RX * S`.
The material agent owns that file; the integrator applies the two-line transform change there.
Without it the GPU mesh and native app pixels would disagree. Render snapshots must use
the production Kotlin pose rather than reimplementing its trajectory.

Rollback: revert this motion change and its paired renderer transform together.
PORTIERT locally; host math VERIFIED; Android UI and GERÄTEBESTÄTIGT remain open.
