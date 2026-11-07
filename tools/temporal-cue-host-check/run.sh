#!/usr/bin/env bash
# Focused JVM check of the complete original M2.9 retriever source. No Gradle, Android, or network.
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s /path/to/gradle-8.11.1/lib\n' "$0" >&2
  exit 2
fi

gradle_lib=$(cd -- "$1" && pwd)
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
java_bin="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
for jar in kotlin-compiler-embeddable-2.0.20.jar kotlin-stdlib-2.0.20.jar junit-4.13.2.jar hamcrest-core-1.3.jar; do
  if [[ ! -f "$gradle_lib/$jar" ]]; then
    printf 'UNAVAILABLE: missing %s\n' "$gradle_lib/$jar" >&2
    exit 2
  fi
done

temp_dir=$(mktemp -d)
trap 'rm -rf -- "$temp_dir"' EXIT
mkdir "$temp_dir/classes"

# The event-decorated factory branch needs Android-linked learning-core types.
# It is outside this check. Fail if a test accidentally enters it; do not simulate it.
cat > "$temp_dir/EventEmittingRetriever.kt" <<'KOTLIN'
package at.heinz.deviceai.learning

class EventEmittingRetriever(
    delegate: ContinuumMemoryRetriever,
    @Suppress("UNUSED_PARAMETER") appDirectory: java.io.File,
) : ContinuumMemoryRetriever by delegate {
    init {
        error("Event-emitting retrieval is outside the TemporalCue host check")
    }
}
KOTLIN

source_file="$repo_root/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
calendar_test="$repo_root/app/src/test/java/at/heinz/deviceai/learning/TemporalCueCalendarTest.kt"
daily_test="$repo_root/app/src/test/java/at/heinz/deviceai/learning/TemporalCueDailyBoundaryTest.kt"
existing_test="$repo_root/app/src/test/java/at/heinz/deviceai/learning/ContinuumRetrievalExperimentTest.kt"
test_cp="$gradle_lib/kotlin-stdlib-2.0.20.jar:$gradle_lib/junit-4.13.2.jar:$gradle_lib/hamcrest-core-1.3.jar"

printf 'HOST_JVM_ONLY: original retrieval source; compile-only, fail-fast event-decorator stand-in\n'
printf 'HEAD: '
git -C "$repo_root" rev-parse HEAD
sha256sum "$source_file" "$calendar_test" "$daily_test" "$existing_test"
"$java_bin" -version
"$java_bin" -Xmx1g -cp "$gradle_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$test_cp" -d "$temp_dir/classes" \
  "$source_file" "$temp_dir/EventEmittingRetriever.kt" "$calendar_test" "$daily_test" "$existing_test"
"$java_bin" -Xmx1g -cp "$temp_dir/classes:$test_cp" org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.TemporalCueCalendarTest \
  at.heinz.deviceai.learning.TemporalCueDailyBoundaryTest \
  at.heinz.deviceai.learning.ContinuumRetrievalExperimentTest
