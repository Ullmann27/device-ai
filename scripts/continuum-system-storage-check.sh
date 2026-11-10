#!/usr/bin/env bash
set -euo pipefail
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="${1:-/tmp/continuum-system-storage-check}"
compiler="${KOTLINC:-kotlinc}"
: "${CONTINUUM_JUNIT_JARS:?Set JUnit4 and Hamcrest classpath}"
mkdir -p "$out_dir"
cd "$root_dir"
main=app/src/main/java/at/heinz/deviceai/systemintelligence
tests=app/src/test/java/at/heinz/deviceai/systemintelligence
sources=("$main/StorageContentAnalysis.kt" "$main/OwnDiagnosticCache.kt"
  "$tests/StorageContentAnalysisTest.kt" "$tests/OwnDiagnosticCacheTest.kt")
sha256sum "${sources[@]}" "$main/SystemStorageInspector.kt" > "$out_dir/sources.sha256"
java -version > "$out_dir/java.txt" 2>&1
JAVA_OPTS=-Xmx384m "$compiler" -version > "$out_dir/kotlin.txt" 2>&1
JAVA_OPTS=-Xmx384m "$compiler" "${sources[@]}" -cp "$CONTINUUM_JUNIT_JARS" \
  -include-runtime -d "$out_dir/tests.jar" > "$out_dir/compile.txt" 2>&1
java -Xmx192m -cp "$out_dir/tests.jar:$CONTINUUM_JUNIT_JARS" org.junit.runner.JUnitCore \
  at.heinz.deviceai.systemintelligence.StorageContentAnalysisTest \
  at.heinz.deviceai.systemintelligence.OwnDiagnosticCacheTest | tee "$out_dir/junit.txt"
if [[ -n "${ANDROID_JAR:-}" ]]; then
  JAVA_OPTS=-Xmx384m "$compiler" "$main/StorageContentAnalysis.kt" "$main/OwnDiagnosticCache.kt" \
    "$main/SystemStorageInspector.kt" -cp "$ANDROID_JAR" -d "$out_dir/android-adapter.jar" \
    > "$out_dir/android-sdk-compile.txt" 2>&1
fi
