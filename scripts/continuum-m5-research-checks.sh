#!/usr/bin/env bash
# Reproduce M5 host checks. SDK/JDK17/Gradle8.11.1/Kotlin2.4 dependencies must already be installed.
# TEST3 is now opened after its single final run: repeats are regressions, never fresh blind evidence.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
output=${1:?Usage: continuum-m5-research-checks.sh /absolute/new-output-dir [--opened-test3]}
[[ "$output" = /* && ! -e "$output" ]] || { echo 'Output must be a new absolute directory'; exit 2; }
[[ "${2:-}" = '' || "${2:-}" = '--opened-test3' ]] || exit 2
mkdir -p "$output"
cd "$root"
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug >"$output/android.log" 2>&1
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -I scripts/continuum-test-classpath.init.gradle -PcontinuumClasspathFile="$output/classpath.txt" :app:continuumExportTestClasspath >"$output/classpath.log" 2>&1
classpath="$(cat "$output/classpath.txt"):$root/app/src/test/resources:$root/app/src/main/resources"
java -Xmx2g -cp "$classpath" at.heinz.deviceai.intelligence.maxultra.Ctx3Performance "$output/performance.json"
if [[ "${2:-}" = '--opened-test3' ]]; then
    java -Xmx2g -cp "$classpath" at.heinz.deviceai.intelligence.maxultra.Ctx3Experiment docs/research/ctx3-independent-test3-20261024.json "$output/test3.json" "$(git rev-parse HEAD)" >"$output/test3.stdout"
fi
