# Independent integrated Cinema review

Reviewed local commits: `1cf970c5890dab93f00195cd4017ae5f21de42cb`
(fluid geometry), `030bd5703ff9e860e185eadfd7823521b412307a`
(avatar deadlines), `4209453ac649a2ab4fca67608e1e109e662cf58d`
(native sequence), and its fixture correction
`9fd5d1455a7b765e32660c1be37a0ffffacaefed`.
Integrator retains the sole Gradle/build lease. This review made no production edits.

## Findings

No blocking defect found in the inspected changes. This is a bounded code and
host-shader review, not a general correctness or device-performance guarantee.

| Change | Checked behavior | Limit |
| --- | --- | --- |
| Fluid transfer | Maximum four strands ×18 segments ×3 body lanes; arrays sized exactly for 864 body and 288 highlight floats; half strand count below HIGH; additive mode restored in `finally` | More work than baseline: HIGH makes four batched calls; a warm reduced-bloom budget makes three. A cool MEDIUM budget can retain bloom and four calls |
| Edge shader | At most four disjoint perimeter rectangles, radius clamped by caller; positive dimensions and finite inputs; software fallback after shader failure; no added layer/timer | Spread increases 6→10 dp. For a 352×192 dp panel with radius14, raster area grows 26832→35088 dp² (+30.8%). This is geometric area, not measured GPU cost |
| Quality floor | Existing sample gate suppresses fluid motion at LOW/SUSPENDED, disabled animations and invalid scale; reduced bloom omits the broad transfer glow | Mouth/eyes/head use their separate existing essential policy. No achieved FPS can be inferred |
| Avatar deadline fix | Both estimated viseme and sentence events keep their original word timestamp; reducer expires past cues/gestures at sample time; current utterance/PCM/range admission unchanged | Explicit text-pace estimate remains synthetic fallback, not actual audio timing |
| Native sequence | Real Activity/View hierarchy; bounded scripted work/return/abort sequence; same avatar instance; window presence and closure assertions; deterministic main-loop advancement | API28 native Canvas + explicitly selected 2D fallback. Hardware AGSL, Filament and physical Fold7 are NOT_RUN |

The native sequence's distinct PNG hashes do not independently prove fluid
phase correctness: other clock-driven pixels can also change. Its explicit
window assertions are the meaningful lifecycle checks. The abort path invokes
`finishLocalReasoningWorkScene(immediate=false)` plus `turnEnded(false)`;
it does not click STOP or test `cancelLocalRequest` itself. Describe it as
scripted cancellation, not end-to-end STOP input.

The fixture sets busy/request state directly and opens the actual work scene.
That scene includes the product text “reale lokale Inferenz”, although the
fixture does not run a model. Its JSON correctly identifies scripted inputs.
Standalone videos/contact sheets should also visibly state host capture and
no model execution; a detached screenshot must not imply real inference.

## Independent focused check

Command on the existing toolchain:

```bash
PYTHONPATH=/tmp/continuum-toolchain/skia python3 -B scripts/continuum-fluid-edge-shader-test.py
```

Result: **11/11 PASS**, host software Skia, 0 failures/skips. Root advanced from
4209453 to 9fd5d14 while it ran; direct Git comparison confirmed that shader and
test-script bytes were identical between those commits. The fixture alone changed.

| File | SHA-256 |
| --- | --- |
| `JarvisGlassRenderer.kt` | `380e0e7336028bc901e917bcafde42dab89f3b5cde999e1697bd467cc7b20dac` |
| `continuum-fluid-edge-shader-test.py` | `f4283a1f148c88278bd106c269839805aeb37d0b5fc9e1637f886deebad884a1` |
| `JarvisFluidLight.kt` | `2e082d43f3695db8823aae8ae7144840d16f4550b37a41d78fc1961d45886735` |
| `AvatarDirector.kt` | `074b60fba0bbf963631d50069e7d4b38fba3f33dbe946a4b6b40aeec5c21056b` |

No Gradle rerun was started by this reviewer. Root owns the current-source
Android results. APK/signing/install/main were untouched. GPU/CPU/thermal/FPS
acceptance still requires the existing physical-device protocol in
`CINEMA-PERFORMANCE-VERIFICATION-20261110.md`.
