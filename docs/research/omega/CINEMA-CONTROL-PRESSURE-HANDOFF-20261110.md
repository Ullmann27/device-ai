# Cinema control pressure — WIP checkpoint

Canonical integration: PR #715, root integrator. Own branch: `cinema/control-material-pressure-20261110`.
Base local `47a86df300210de7298e7768959cd5bf0c96fb4f`; published equivalent `5d36c5a57c031308c8de91049e19e9e8cb8365d5`, identical tree `970445b70dcd57d344f43ff95bfd58fd505f42ab`.

## Connected change

- Existing native `ShellActionView.setPressed` propagates the actual control key, target and normalized contact point into the existing shell renderer. This includes the Android delayed pressed state inside scrolling parents. UP, CANCEL, move-out, disabled state, visibility, focus loss and detach release only that control's state.
- The existing bottom-nav `pressed` parameter is connected. Luxury glass compresses 1.8 dp and draws two bounded contact-gradient rim strokes while held; its pulse samples the existing shell clock. No extra animator, timer, event bus, hitbox or overlay.
- Reduced motion and thermal/power/foreground policy preserve static pressure feedback without starting another frame lane. Existing frame pacing controls held feedback.
- A native activation supplies its exact source View only during its synchronous click callback, consumed once. The work-window lease retains that source and its initial bounds. Same-source/same-viewport refits retain the flight origin, including the source hidden by its own work scene. Real geometry or viewport changes, detached/offscreen sources and unsupported inputs use existing validated geometry/fallback paths. Voice/document/tool scenes never consume a stale activation.

## Evidence at this checkpoint

WIP / NOT Android verified. Root owns `CinemaButtonMaterialUiTest.kt` and native PNG before/after captures.

- Focused 256 MB Kotlin compilation of changed platform-free painter/render files: PASS against the previously verified Java2D preview dependency jar.
- Added four focused pure tests (six total in `LuxuryHoloButtonRendererTest`): bounded clock/disabled pressure, actual contact placement, exact dock routing and reduced-motion/thermal static feedback. Execution pending root build lease release.
- A larger preview compilation was killed with exit 137 during shared host memory pressure. A premature dependent test compilation then lacked the missing jar. Neither is application-test RED evidence.
- Android native lifecycle/geometry checks, actual native PNG comparison, GPU/Fold7/frame-time acceptance: NOT_RUN for this patch.
- No Gradle, signing, APK or installation performed by this worker.

## Files and rollback

Production: `ContinuumHomeView`, `JarvisShellView`, `JarvisShellRenderer`, `JarvisTiles`, `LuxuryHoloButtonRenderer`, `JarvisWorkWindowHost`.
Test: `LuxuryHoloButtonRendererTest`.
Revert this branch's pressure commits to remove this change. Prior viscous edge commit remains independent.

Older isolated shader checkpoint was separately archived as `04a89b8c25ce46baf5074f85d1277f96c71ff474` on `cinema/fluid-edge-shader-20261110`, exact tree of local `cfbe792c22c83921d746a617a7b4c0838aceabeb`; intermediate, superseded by canonical viscous implementation.
