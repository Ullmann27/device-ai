#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?Output directory required}
: "${KOTLINC:?KOTLINC compiler path required}"
: "${CONTINUUM_JUNIT_JARS:?JUnit and Hamcrest classpath required}"
mkdir -p "$out"
"$KOTLINC" -J-Xmx256m \
  "$root/app/src/main/java/at/heinz/deviceai/benchmark/BenchmarkModel.kt" \
  "$root/app/src/main/java/at/heinz/deviceai/systemintelligence/SystemPerformance.kt" \
  "$root/app/src/test/java/at/heinz/deviceai/systemintelligence/SystemPerformanceTest.kt" \
  -cp "$CONTINUUM_JUNIT_JARS" -include-runtime -d "$out/tests.jar"
java -Xmx128m -cp "$out/tests.jar:$CONTINUUM_JUNIT_JARS" \
  org.junit.runner.JUnitCore at.heinz.deviceai.systemintelligence.SystemPerformanceTest
