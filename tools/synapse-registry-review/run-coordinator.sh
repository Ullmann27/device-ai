#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
source_file=${1:?coordinator source file}
evidence_dir=${2:?evidence directory}
base_classes=${SYNAPSE_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
mkdir -p "$evidence_dir/classes"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$base_classes:$cp"
sha256sum "$source_file" "$review_root/tools/synapse-registry-review/CoordinatorIndependentChecks.kt" > "$evidence_dir/source-hashes.txt"
find "$base_classes/at/heinz/deviceai/chief" -type f -name '*.class' -exec sha256sum {} + > "$evidence_dir/base-chief-class-hashes.txt"
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$evidence_dir/classes" "$source_file" "$review_root/tools/synapse-registry-review/CoordinatorIndependentChecks.kt" > "$evidence_dir/compile.log" 2>&1
set +e
timeout 30s java -Xmx256m -cp "$evidence_dir/classes:$cp" at.heinz.deviceai.chief.CoordinatorIndependentChecks > "$evidence_dir/checks.log" 2>&1
status=$?
set -e
printf '%s\n' "$status" > "$evidence_dir/checks.exit"
cat "$evidence_dir/checks.log"
exit "$status"
