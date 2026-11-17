# Planning adapter host evidence

Accepted source: `4b98da9362f91ab9930a7f4b279737a9e80f94bd`, based on Chief
`19474261c15a3503a9f041244b4b4c3eaed4636f`. See
[handoff](../../../../research/omega/momega1/ANDROID-PLANNING-HANDOFF.md).
No device or native Android inference result is claimed.

- `red-e8c155e7`: exact unchanged-production RED, 13/13 failures, zero errors/skips.
- `green-4b98da93`: exact final GREEN, 102/102 passes in 17 classes, zero errors/skips.
- Each includes raw Gradle output/exit, JUnit XML, actual runner/filter, source
  before/after receipts and comparison exit 0.
- `harness-compile-failure`: first combined attempt's raw compilation failure
  and receipts. Production compiled but two independent runtime fixtures lacked
  required `unload(callback)`. No tests executed. The runner had copied old RED
  XML; its explicit `ATTEMPT-STATUS.txt` marks that XML STALE_NOT_RESULT. The stale
  copies are deliberately excluded here from functional results.
- `independent-snapshots`: byte-exact, attributed independent reviewer fixtures
  after interface-only correction. No assertions changed in that correction.
- `manifest.json`: source and raw evidence SHA-256 plus XML totals, checked when
  copied. Original paths in raw receipts identify the actual execution; portable
  relative source directories appear only in `reproduce.init.gradle`.

The actual runner used the provisioned official JDK17/Gradle8.11.1/SDK36 build
environment and JDK21 test JVM required by the published LiteRT dependency. It
did not download or load model weights for this suite. Reproduce on that host
from a docs-only descendant whose app/scripts/.github/tools/backend source is
unchanged from the candidate:

```sh
source /workspace/momega1-build-env/env.sh
git diff --exit-code 4b98da9362f91ab9930a7f4b279737a9e80f94bd -- app scripts .github tools backend
gradle --no-daemon --max-workers=2 --console=plain \
  -I docs/evidence/momega1/android/planning/reproduce.init.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```

The independent planning source hashes are `681c1c3c…ab28` (delivery),
`c229de16…f33f` (intake), `be19e518…dc9f` (pure); the raw `harness.sha256`
contains full values. The previous 13 independent authorization/cancellation
fixtures remain in `../independent-snapshots`, unchanged from the accepted
prepared-task fix. The focused 102-test count is 11 authored planning + 30 new
independent + 45 previous typed-route + 16 adjacent model/agent tests.
