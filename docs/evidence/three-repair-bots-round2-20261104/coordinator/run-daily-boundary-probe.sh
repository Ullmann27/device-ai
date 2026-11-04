#!/usr/bin/env bash
# Full production source; no Android, network, model or event-decorator execution.
set -euo pipefail
repo=${1:?Pass source repository}
compiler_lib=${2:?Pass Gradle 8.11.1/lib}
java_bin="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
probe_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
temp_dir=$(mktemp -d)
trap 'rm -rf -- "$temp_dir"' EXIT
mkdir "$temp_dir/classes"
cat > "$temp_dir/EventEmittingRetriever.kt" <<'KOTLIN'
package at.heinz.deviceai.learning
class EventEmittingRetriever(delegate: ContinuumMemoryRetriever, appDirectory: java.io.File) : ContinuumMemoryRetriever by delegate {
    init { error("Event decorator is outside the daily-boundary probe") }
}
KOTLIN
source="$repo/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt"
sha256sum "$source" "$probe_root/DailyBoundaryProbe.kt" "$probe_root/run-daily-boundary-probe.sh"
"$java_bin" -Xmx512m -cp "$compiler_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -classpath "$compiler_lib/kotlin-stdlib-2.0.20.jar" \
    -d "$temp_dir/classes" "$source" "$temp_dir/EventEmittingRetriever.kt" "$probe_root/DailyBoundaryProbe.kt"
"$java_bin" -Xmx256m -cp "$temp_dir/classes:$compiler_lib/kotlin-stdlib-2.0.20.jar" at.heinz.deviceai.learning.DailyBoundaryProbe
