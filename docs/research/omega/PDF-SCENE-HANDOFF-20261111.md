# PDF work scene: integration and verification

Canonical target: `work/pdf-scene-20261111`, PR #761.
Integration baseline: #747 plus #757. Flow and review contributions are linked
through #763 and #762; their source changes are in the canonical target.

## Source checkpoint

Remote source: `21c69a76d38d4c5482e24bdf502d24ef6f3845cd`.
Matching local source: `f0349f4cbe4474e6df07ddf4685463688abb6ef2`.
Exact shared tree: `c778f73d7f2e1f79535f4d5af4875ec5cbdf56a1`.

- The bounded presentation suffix now reaches the existing owner PDF picker.
- Selection identity, request codes, cancellation and saved Activity state
  reject stale results and keep foreground delivery bound to the request.
- The document enters from the left; the draft enters from the right. The
  personal follow-up uses the existing voice path after actual window arrival.
- A derived five-mesh GLB carries the real first-page bitmap. It uses the
  existing Filament engine, renderer, asset loader and animation lifecycle.
- Native controls remain accessible. Native page/glass pixels yield only to
  confirmed rendering, and return on clipping, close, replacement or loss.
- Native scroll/layout/draw events refresh settled geometry. Identical frames
  remain idle, valid motion retains readiness, and hidden pages skip rendering.

The original avatar is unchanged. No second engine, APK/signing change, main
merge, installation or release is included.

## Verification checkpoint

`PORTIERT`: yes, connected to the real Android call paths.

`VERIFIZIERT`: partial. Main Kotlin, main Java and all test sources compiled on
the source checkpoint above. The selected 25-class Android/Robolectric run is
still in progress; its behavioral results have not yet been accepted.

The earlier source `8f54643b277fafa13c394e254f67de7190f20e3c` passed main Kotlin
and Java compilation, then failed test compilation on an unavailable test
import and an inferred intersection array type. Both were corrected using
existing Robolectric APIs and an explicit cursor-row type. Those failures are
retained in the run history, not converted into passing test counts.

Two earlier long-running checks on `09757b938734d7bf12347c3e5f69115f11c5621b`
were deliberately interrupted without a result. They are not PASS evidence.
The isolated production-router check passed 13 tests; its original red/green
logs remain under `docs/evidence/pdf-scene-20261111/flow/`.

Native Canvas captures use scripted inputs and the existing 2D fallback. The
separate Blender asset inspection used a synthetic page texture. Neither
proves Android Filament output, physical Fold7 behavior or actual speech.

`GERÄTEBESTÄTIGT`: not run. Physical GPU output, frame timing, thermals, real
model execution and audible follow-up require device acceptance.

## Reproduction and next step

Use JDK 21, the repository's Gradle/Kotlin configuration, Android API 36 and
the existing model preparation. Run `:app:testDebugUnitTest` with:

```sh
CONTINUUM_CAPTURE_SOURCE_COMMIT="$(git rev-parse HEAD)" \
./gradlew --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true \
  -PcontinuumOwnerArm64=true \
  -PcontinuumStandaloneVersionCode=53 \
  -PcontinuumLocalStandaloneQa=true \
  -PcontinuumUiWidgetTests=true \
  -PcontinuumUiEvidenceDir="$PWD/../qa-pdf-scene" \
  -I tools/capture/force_capture.gradle \
  -I tools/pdf-scene-review/focused.init.gradle \
  :app:testDebugUnitTest
```

Finish the selected scope and bind the JUnit results and captures to the exact
source. The actual synthetic-PDF/provider/native-decoder probe must run
strictly first. Any later host-capability skip must identify the observed
missing native component precisely. Complete Node regression sequentially
after Android QA, then append the final result evidence here.

Rollback uses reviewed reverts of the feature commits on the canonical
development branch. No force reset or deletion of other branches is required.
