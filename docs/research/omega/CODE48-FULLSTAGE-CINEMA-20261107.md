# Code48 full-stage cinema continuation

Status: WIP / NOT_IMPLEMENTED / NOT_RUN.
Base: PR590 at d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de.

## Concrete remaining reference gap

The recovered document flight is connected to genuine document/recall events,
but its current canvas is restricted to the avatar slot. The existing original
ContinuumHologramRenderer already provides the energy core, lightning and orbit
layers. A larger finite composition from document flight to the centred original
orb and back to the actual cockpit is not connected yet.

## Authorized implementation boundary

Reuse the actual original renderer, frame state and clock. Keep current head,
orb assets, palette, data, model and ToolGate. Preserve visible navigation/STOP
and foreground work-card access. End immediately on STOP, foreground/visibility
loss, detach, reduced motion or disallowed thermal policy; do not replay stale
events or prolong an active episode on repeated events. No demo button, fake
telemetry, second renderer, animation thread, cloud service or device install.

## Acceptance

First reproduce the missing large transition through a real positive event and
actual native view composition. Independently verify staging/clipping, use of
the original orb, return to the unchanged cockpit and cancellation boundaries.
Full-source Node/backend/Android/lint and the unsigned Code48 build must be rerun
on the final integrated source; the d73fc46 baseline is not final-source proof.
Host canvas evidence does not establish Filament GPU, Fold7 performance or exact
pixel parity with the original movie. Signing/publication remains subject to
the exact-SHA owner release freeze. No such action is authorized by this note.

Contracts: EXP-02/07 regression and independent evidence, EXP-11 event routing,
EXP-12 lifecycle. Rollback: revert only this bounded scene/host connection.
