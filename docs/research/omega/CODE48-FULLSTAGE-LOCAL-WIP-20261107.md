# Fullstage memory scene: WIP checkpoint

Base: `d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de`.
This checkpoint is incomplete and is not release evidence.

- Adds a finite 4,800 ms document-flight, original-orb and return controller.
- Uses a Home-owned drawing view below native work windows and foreground controls.
- Excludes actual visible action-view and navigation bounds from its canvas.
- Samples the visible scroll viewport; the return target includes the existing avatar offset.
- Reuses the original `ContinuumHologramRenderer` instance, live frame and existing HologramView clock.
- Positive existing document/recall events request the scene. No demo control, asset, service or rendering thread.
- Existing STOP, visibility, foreground, motion-policy, detach and reparent boundaries clear the scene.
- The original small orb is omitted during the temporary scene; original appearance preferences stay unchanged.
- Tunnel documents use bounded existing geometry with legible paper and folder fills.

## Open validation

Focused production compilation has passed. A direct existing avatar-adapter event reached the new large canvas during diagnosis. The real Presenter/Activity event route still needs verified admission in the native host test; the initial outside-avatar assertion failed without that admission. The diagnostic direct call was removed. A source-bound baseline run with an explicit recall-delivery assertion is running.

Independent Activity, control, reparent, cancellation and native-frame review is in progress. The pure scene tests and full integrated gates have not yet run on this checkpoint. Do not treat this WIP as a completed reference-video implementation or a device/GPU result.
