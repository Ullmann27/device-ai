# Focused verification

Result: 160 tests, 0 failures. `final-focused/junit.txt` is the final run.
`final-focused/first-junit.txt` retains the preceding two test-fixture failures.
Neither those fixture corrections nor the final rerun changed production sources.

The real compiled dependencies were supplied by Root from source24547da after a complete Android36 production compilation. `receipt.json` binds their class manifest and the original regression sources; `source-sha256.json` binds every production/test source in the focused run. There are no production stubs. Tests use the real JVM org.json implementation instead of Android's unimplemented test-framework JSON methods.

## Executed GREEN command

Run from the repository root after loading the centrally restored toolchain. The actual argument file originally held absolute paths; `sources.txt` contains the same paths relative to this repository for reproducibility. Test class names are identical.

```bash
source /workspace/scratch/b31a9d731016/build-env/env.sh
ctx_base=/workspace/scratch/b31a9d731016/device-ai-wiring/app/build/tmp/kotlin-classes/debug
ctx_libs=/workspace/scratch/b31a9d731016/build-env/test-libs
ctx_cp="$ctx_base:$ctx_libs/junit-4.13.2.jar:$ctx_libs/hamcrest-core-1.3.jar:$ctx_libs/json-20240303.jar:/workspace/scratch/b31a9d731016/build-env/android-sdk/platforms/android-36/android.jar"
ctx_evidence=docs/evidence/code47-context-identity-20261106/final
ctx_out=/workspace/scratch/b31a9d731016/context-evidence/final-focused
kotlinc -jvm-target 17 -Xfriend-paths="$ctx_base" -cp "$ctx_cp" @"$ctx_evidence/sources.txt" -d "$ctx_out/classes" > "$ctx_out/compile.txt" 2>&1
mapfile -t ctx_tests < "$ctx_evidence/tests.txt"
java -cp "$ctx_out/classes:$ctx_cp:/workspace/scratch/b31a9d731016/build-env/kotlinc/lib/kotlin-stdlib.jar" org.junit.runner.JUnitCore "${ctx_tests[@]}" > "$ctx_out/junit.txt" 2>&1
```

The compiler exit was checked before executing JUnit; both final exits were0.

## Identity / answer-end baseline RED

Using the same dependency classpath and source tests, compile only `ContextIntegrityFixture.kt`, `AiContextIdentityRegressionTest.kt` and `LocalConversationAnswerEndRegressionTest.kt`, without the changed production sources. Run the two test classes. The actual baseline gives4 tests /3 expected assertion failures: missing required chosen name/model, missing identity at the mandatory byte boundary and missing real answer end. The private-bot control passes. Raw output is in `identity-tail-red/`.

The additional baseline RED runs are retained in sibling evidence folders: original Store loses both long owner corrections; original continuation policy rejects the basic continuation intent; original Bootstrap2048 gate prevents otherwise admitted long dialogue from entering the local route. Their GREEN forms and existing guard tests are included in the final160 tests.

## Scope limit

No Activity mutation in this branch, no heavy Gradle suite, no device/model invocation, no APK build or deployment. Root must compile/test the actual integrated Activity and all three agents' sources before release. Byte/character task and overall prompt caps remain unchanged.
