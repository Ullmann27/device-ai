# Independent roster executions

Test criteria froze before source inspection. No feature edits by reviewer.

- `a479-initial`: Gradle configuration failure only. Existing standalone QA guard rejects command-line `--tests` because its value appears in taskNames at configuration; no compiler/test execution. Corrected by init-script test filter, never changing guard. Earlier mkdir-path harness typo prevented a process launch and is recorded separately.
- `a479-corrected-invocation`: exact production sourcea4798506d2bac25e9212d6ebdf6c14595b6d0931, testdf45c0771. Actual Android compile **FAILED**: `ContinuumBotRosterDialog.kt:38:28`, `setBackgroundColor(background)` resolves the View Drawable receiver property instead of outer Int color. Zero tests executed; this is a real source compile RED, not a test failure or device issue. Source hashes unchanged before/after. Root informed; minimal fix requested before exercising queue counterexample.

Corrected scoped invocation (root /workspace/synapse-ui-candidate):
```
source /workspace/continuum-build-env/env.sh
gradle --no-daemon --max-workers=2 --console=plain -I /workspace/continuum-build-env/test-jvm21.init.gradle -I /workspace/synapse-ui-review/tools/synapse-ui-review/source.init.gradle -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=40 -PcontinuumUiWidgetTests=true -PsynapseUiReviewOnly=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```
Final full QA must omit synapseUiReviewOnly. Host tests are not Fold7/GPU or local-model inference evidence.
