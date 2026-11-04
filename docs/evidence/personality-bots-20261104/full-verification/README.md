# Regular full unit suite and configured Android API lint — GREEN

- Source: `dbb8e2f7085b0140ed437502b27fac5f988ac834`. Root compared all 1,009
  application/build/test input files against this published Git tree: zero mismatches.
- Full regular unit suite: **2,402 total, 2,383 passed, 19 skipped, 0 failures, 0 errors**,
  across 427 test classes. Individual skipped cases are listed in `summary.json`.
- Application and test compilation succeeded.
- Configured `lintDebug`: **0 issues**. The repository selects `NewApi` only;
  this result is not a claim about every Android lint rule.
- Gradle exited 0, `BUILD SUCCESSFUL in 2m 31s`.
- `continuumUiWidgetTests` is disabled in this full regular unit run. The
  separately executed focused gate includes 31 actual Android-widget/Activity
  tests and passed 114/114 in total; its unit cases overlap this full suite.

Command (using the existing shared-lock/JDK/SDK wrapper):

```text
./gradlew --no-daemon --max-workers=1 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process \
  --init-script docs/evidence/personality-bots-20261104/full-verification/test-progress.init.gradle \
  :app:testDebugUnitTest :app:lintDebug
```

`test-progress.init.gradle` changes logging only. `junit-results.tar.gz` contains
the actual 427 XML reports; `summary.json` is derived from those reports. The
source manifest stayed unchanged through execution. The earlier broad all-widget
attempt was interrupted without a summary and remains UNVERIFIED; see
`../all-widget-suite-interrupted`. No physical Fold7, native LiteRT model, GPU,
speech, signature, installation or combined-release acceptance.
