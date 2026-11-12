# Code55 – holographic cockpit support

## Integration

- Repository: `Ullmann27/device-ai`.
- Source baseline: `9ffd3b193` from scene PR #800.
- Target: scene #800, integration #799, then Code55 #791.
- Support branch: `work/astra55-holographic-support-20261112`.
- Scope: existing cockpit materials, spatial animation and reproducible renders.
- Initial status: WIP / UNVERIFIED. New checks and renders: NOT_RUN.

## Ownership

| Agent | Changes | Branch |
| --- | --- | --- |
| Materials | Existing workstation generator/GLB, light resources, asset pin | `work/astra55-hologram-materials-20261112` |
| Motion | Shared frame projection, bounded focus animation, targeted checks | `work/astra55-hologram-motion-20261112` |
| Render evidence | Host pipeline using production assets and exported layout | `work/astra55-hologram-render-20261112` |
| Support integrator | Compose the small shared transform edit, review, validate, hand off | `work/astra55-holographic-support-20261112` |

The existing scene owner remains the integration owner for #800. Active work by
other teams is not edited. Shared renderer changes are composed once in this
support branch. No second Android engine, render clock or event bus is introduced.

## Acceptance

1. Preserve the original avatar and sphere. Follow the current workstation
   arrangement: sphere behind the avatar, upright readable monitor.
2. Keep application pixels free from decorative overlays and preserve their
   source binding. Do not populate the app with example contacts or files.
3. Keep the native pixel projection and 3D monitor transformation aligned,
   including the intermediate focus poses and reverse motion.
4. Bound asset size, light count and animation duration. Respect pause and motion
   settings without disabling the existing cinematic presentation.
5. Produce source-identified Fold and phone host images. These do not constitute
   Android Filament, Fold7 performance or device acceptance.
6. Review and run relevant host checks before transferring to #800. Record actual
   commands, results and source hashes. Device verification remains separate.

## Compatibility and rollback

EXP-05/06/11/12 keep their existing model, evidence, task and checkpoint contracts.
This support work changes presentation only. No changes to live app actions,
owner permissions, model weights, signing, package identity or main are in scope.
Rollback is a revert of the support commits on the development branch.

## Progress

Start checkpoint: responsibilities and acceptance recorded; implementation and
render validation in progress. PORTIERT / VERIFIZIERT / GERÄTEBESTÄTIGT are recorded
only when supported by the corresponding source and execution evidence.
