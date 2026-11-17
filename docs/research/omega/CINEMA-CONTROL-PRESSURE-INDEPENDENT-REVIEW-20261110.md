# Independent control-pressure review

Reviewed source: local `ac27dbb22e6f80356b021e2451da51603ab5a6b8`, published [0f1ff95c4f1c3d1c9bfb11fad5c04b50c63912e8](https://github.com/Ullmann27/device-ai/commit/0f1ff95c4f1c3d1c9bfb11fad5c04b50c63912e8). GitHub readback and local tree both equal `ad58fbf3bf4bbc3496399c05189a96d6ced906c8`. Diff base: local `47a86df300210de7298e7768959cd5bf0c96fb4f`.

Canonical integration owner: root integrator, PR #715, `cinema/fluid-light-stage-20261110`. Implementation owner: `cinema/control-material-pressure-20261110`. This review changes documentation only; no production mutation, Gradle run, signing, installation or device measurement was performed by the reviewer.

## Result and boundaries

No blocking defect found in the inspected final diff. This is source review with inspection of the implementation worker's host-test receipt, not an independent Android execution pass.

| Concern | Inspected behavior |
| --- | --- |
| Delayed native press | `ShellActionView.setPressed` publishes native state, including callbacks after DOWN inside a scrolling parent. Rendering does not infer pressure only from DOWN. |
| Release | UP/CANCEL explicitly release emitted pressure after native dispatch, even when Android temporarily retains its pressed flag for click dispatch. MOVE delegates native slop/outside handling before publication. Disable, focus loss, child visibility and detach clear only the matching key. |
| Suspend and stale callbacks | Foreground pause, window hiding, ancestor hiding and detach clear shared state, cancel pending native input and reset native pressed flags. Publication requires active presentation and the current `presentationGeneration`; a new touch/key DOWN captures that generation. |
| One clock and quality | Material and background times derive from the same existing `onDraw` timestamp. Separate gates preserve a frozen ambient background during a held contact. Existing motion/thermal/power/frame-delay controls govern refresh; disabled animation gives static pressure. No new timer or animator. |
| Exact activation source | `activatingShellAction` exists only within the synchronous native click callback and is consumed once for the matching target. Its `finally` cleanup prevents later voice/tool work from inheriting an old clicked View. |
| Refit | A window-local source snapshot preserves its initial origin only for the same attached/enabled View, unchanged full source bounds and unchanged viewport, with either current visibility or deliberate hiding by its own deck scene. Reflow/detach uses current validated geometry or existing fallback. Existing scroll changes close the window. |

Two source-review concerns were corrected before this binding: suspended/hidden presentation could retain a held contact, and contact animation could advance a deliberately frozen background clock. The earlier source-origin refit concern was addressed by the scoped snapshot and deliberate-deck-hide check. These were review findings, not reproduced physical-device failures.

## Inspected host evidence

The worker's `control-pressure-host/pressure-tests.txt` contains `OK (47 tests)`; SHA-256 `5445e32ba7c6bb7c93ccd339c76a71432d45eadec36e1aa0728cfb53400b76ad`. The worker reports 7 material, 3 ambient and 37 fluid/entrance JUnit cases. The inspected focused compiler log contains two unnecessary-safe-call warnings in `JarvisShellRenderer`, no error text. The reviewer did not rerun these tests or independently rebuild their dependency jar.

Pure painter tests cover bounded pressure, disabled pressure, pointer placement, exact dock routing, static reduced/thermal behavior and frozen background time. They do not execute Android View dispatch, ScrollView interception, lifecycle callbacks, work-window refit, GPU presentation or physical thermal behavior.

Root's native acceptance must bind to its integrated source: delayed ScrollView press, UP/CANCEL/move-out, focus/disable/hide/pause/detach, delayed callback after pause/resume, exact clicked origin, a later voice-origin fallback, and geometry refit. Native before/after PNGs are host-render evidence unless captured on the device. Physical Fold7 FPS, GPU/CPU time and thermal acceptance remain `NOT_RUN` in this review; no stutter-free guarantee follows.

## Source binding

Paths below are under `app/src/main/java/at/heinz/deviceai/ui/`, except the stated test.

| File | SHA-256 |
| --- | --- |
| `ContinuumHomeView.kt` | `ff3971d0626a4a01c562c7098e1b9d9d475256c4f842d126c7378ab76b06873d` |
| `JarvisShellRenderer.kt` | `14296a36c0f77ddca564775148695ab6db00fe31c502bbfd08e17b726fb3b22d` |
| `JarvisShellView.kt` | `78502f34953d3ec3dfeefd23c442b08f2e6fd316d7de35e41466327903b59379` |
| `JarvisWorkWindowHost.kt` | `1e811fda3255d061dc90e48c06478ea9e1572da2c910f28395bae044f0583495` |
| `JarvisTiles.kt` | `1a89e0c4a4bcff81ac613f96f2726a530123e77014673a7f204f48a4131a735d` |
| `LuxuryHoloButtonRenderer.kt` | `d697756f9a85236672958e0aa7625fa5eff55edf3e9f14777a6405bdc39bc58a` |
| `app/src/test/java/at/heinz/deviceai/ui/LuxuryHoloButtonRendererTest.kt` | `b340aac4ad0ca7f97cbc9a885cb8653591e89de93f0d93ecefbe2399854af62f` |

Rollback: revert this review-document commit to remove the review only. Functional rollback belongs to the implementation's two pressure commits; prior viscous-edge work is separate.
