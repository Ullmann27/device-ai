# Cinema control pressure — host-verified checkpoint

Canonical integration: PR #715, root integrator. Own branch: `cinema/control-material-pressure-20261110`.
Base local `47a86df300210de7298e7768959cd5bf0c96fb4f`; published equivalent `5d36c5a57c031308c8de91049e19e9e8cb8365d5`, identical tree `970445b70dcd57d344f43ff95bfd58fd505f42ab`.

## Connected change

- Existing native `ShellActionView.setPressed` propagates the actual control key, target and normalized contact point into the existing shell renderer. This includes the Android delayed pressed state inside scrolling parents. UP, CANCEL, move-out, disabled state, visibility, focus loss and detach release only that control's state.
- The existing bottom-nav `pressed` parameter is connected. Luxury glass compresses 1.8 dp and draws two bounded contact-gradient rim strokes while held; its pulse samples the existing shell clock. No extra animator, timer, event bus, hitbox or overlay.
- Reduced motion and thermal/power/foreground policy preserve static pressure feedback without starting another frame lane. Existing frame pacing controls held feedback.
- A native activation supplies its exact source View only during its synchronous click callback, consumed once. The work-window lease retains that source and its initial bounds. Same-source/same-viewport refits retain the flight origin, including the source hidden by its own work scene. Real geometry or viewport changes, detached/offscreen sources and unsupported inputs use existing validated geometry/fallback paths. Voice/document/tool scenes never consume a stale activation.

## Evidence at this checkpoint

HOST VERIFIED / NOT Android verified. Root owns `CinemaButtonMaterialUiTest.kt` and native PNG before/after captures.

- Focused 256 MB Kotlin compilation of changed platform-free painter/render files and tests: PASS against the previously verified Java2D preview dependency jar.
- 47/47 JUnit PASS: seven `LuxuryHoloButtonRendererTest`, three `JarvisTileAmbientNeonTest`, and 37 existing fluid/entrance cases. The five new pressure cases cover bounded clock/disabled pressure, actual contact placement, exact dock routing, reduced-motion/thermal static feedback and a frozen background remaining frozen during contact animation.
- Foreground pause, window hiding and detach clear native pending input, native pressed flags and shared material state. Delayed callbacks must match the existing `presentationGeneration`; a fresh native touch/key down captures the current generation. Native Android teardown verification remains assigned to root.
- Material time and background time sample the same existing `now`; their presentation gates are separate so held feedback cannot restart a disabled ambient background.
- A larger preview compilation was killed with exit 137 during shared host memory pressure. A premature dependent test compilation then lacked the missing jar. Neither is application-test RED evidence.
- Android native lifecycle/geometry checks, actual native PNG comparison, GPU/Fold7/frame-time acceptance: NOT_RUN for this patch.
- No Gradle, signing, APK or installation performed by this worker.
- Root independently reported the baseline native capture RED for both layouts: held `bottom:system` did not reach renderer press state. That is baseline evidence, not this patch's Android result.

## Files and rollback

Production: `ContinuumHomeView`, `JarvisShellView`, `JarvisShellRenderer`, `JarvisTiles`, `LuxuryHoloButtonRenderer`, `JarvisWorkWindowHost`.
Test: `LuxuryHoloButtonRendererTest`.
Revert this branch's pressure commits to remove this change. Prior viscous edge commit remains independent.

Older isolated shader checkpoint was separately archived as `04a89b8c25ce46baf5074f85d1277f96c71ff474` on `cinema/fluid-edge-shader-20261110`, exact tree of local `cfbe792c22c83921d746a617a7b4c0838aceabeb`; intermediate, superseded by canonical viscous implementation.
