#!/usr/bin/env bash
# Narrow JVM acceptance. Dependency classes must come from a separately verified full build.
set -euo pipefail
source_root=${1:?source root}
test_root=${2:?test root}
dependency_classes=${3:?verified full-build classes}
out=${4:?output directory}
source /workspace/continuum-build-env/env.sh
jars=/workspace/continuum-build-env/jvm-deps
mkdir -p "$out/main" "$out/test"
cp="$dependency_classes:$jars/junit-4.13.2.jar:$jars/hamcrest-core-1.3.jar:$jars/kotlin-stdlib-2.4.0.jar"
{
    git -C "$source_root" rev-parse HEAD
    git -C "$source_root" status --short
    sha256sum "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/ResearchEngine.kt" "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/BoundedResearchTextSanitizer.kt"
    java -version
    kotlinc -version
} > "$out/environment.txt" 2>&1
kotlinc "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/ResearchEngine.kt" \
    "$source_root/app/src/main/java/at/heinz/deviceai/cognitive/BoundedResearchTextSanitizer.kt" \
    -module-name app -Xfriend-paths="$dependency_classes" -cp "$dependency_classes" \
    -XXLanguage:+BreakContinueInInlineLambdas -d "$out/main" > "$out/compile-main.log" 2>&1
classes=(ResearchClaimAdmissionTest ResearchPromptGraphBoundaryTest ResearchPromptControlTokenTest ResearchEngineTest
    UntrustedResearchEvidenceTest ResearchSanitizerBudgetTest ResearchSanitizerEdgeCaseTest
    ResearchNegationSignalsTest ResearchQuestionFocusTest ResearchTest)
test_sources=()
qualified=()
for test_class in "${classes[@]}"; do
    test_sources+=("$test_root/app/src/test/java/at/heinz/deviceai/cognitive/$test_class.kt")
    qualified+=("at.heinz.deviceai.cognitive.$test_class")
done
kotlinc "${test_sources[@]}" -module-name app -Xfriend-paths="$out/main,$dependency_classes" \
    -cp "$out/main:$cp" -d "$out/test" > "$out/compile-test.log" 2>&1
java -cp "$out/test:$out/main:$cp:$test_root/app/src/test/resources" \
    org.junit.runner.JUnitCore "${qualified[@]}" > "$out/junit.log" 2>&1
cat "$out/junit.log"
