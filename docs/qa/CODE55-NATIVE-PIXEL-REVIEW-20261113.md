# Native Filament pixel review

Application source: `889e06d47fffd2230fa5b062dd1f31b65992e728`.
This isolated validation branch changes only instrumentation and its capture
workflow. The workflow checks the full diff against that application source;
production sources, assets, permissions, shaders and APK build logic must be
unchanged. The canonical QA/APK source remains frozen.

The existing native Filament probe already loads the original avatar, orb and
workstation into one engine/scene. Its lifecycle proof checked buffers and
native resources but did not preserve their pixels for visual inspection.
An optional instrumentation-only capture now saves 24 actual RGBA output
buffers as PNGs with image timestamps, dimensions and hashes. Pixel stride,
row padding and RGBA channel order are checked. The original lifecycle
assertions remain active; no production renderer or launch hook is added.

The separate job repeats the lifecycle probe, captures a synthetic waiting
state with bounded animation, verifies all PNG bytes/CRCs/dimensions, and
encodes a 12-FPS offline review sequence. Export rate is not measured FPS.
No MediaProjection grant, Android action, model inference, Maya audio or
verified task result is simulated as a real observation. This uses the
existing disposable API35 x86_64 ANGLE/SwiftShader test package, never the
original-signed owner package or a connected phone.

Local shell syntax/diff checks only; native compilation/execution and visual
acceptance require the CI artifact. Fold7/Android17, real capture/action,
full Activity composition, live lip synchronization and thermals remain
NOT_RUN. This review does not replace or weaken release QA.
