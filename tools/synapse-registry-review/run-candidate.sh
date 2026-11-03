#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
candidate=${1:?candidate checkout}
evidence_dir=${2:?fresh evidence directory}
base_classes=${SYNAPSE_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
[[ ! -e "$evidence_dir" ]] || { echo 'Evidence directory must be fresh' >&2; exit 2; }
mkdir -p "$evidence_dir/classes"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$base_classes:$cp"
sources=(
 "$candidate/app/src/main/java/at/heinz/deviceai/chief/ChiefBotProfileStore.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/chief/ChiefDelegationCoordinator.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/chief/ChiefWorkerPool.kt"
 "$candidate/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumState.kt"
 "$review_root/tools/synapse-registry-review/RegistryIndependentChecks.kt"
 "$review_root/tools/synapse-registry-review/CoordinatorIndependentChecks.kt"
)
git -C "$candidate" rev-parse HEAD > "$evidence_dir/candidate-sha.txt"
git -C "$candidate" status --short > "$evidence_dir/candidate-status.txt"
sha256sum "${sources[@]}" > "$evidence_dir/source-hashes.txt"
find "$base_classes/at/heinz/deviceai/chief" -type f -name '*.class' -exec sha256sum {} + > "$evidence_dir/base-chief-class-hashes.txt"
java -version > "$evidence_dir/java-version.txt" 2>&1
java -Xmx768m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$evidence_dir/classes" "${sources[@]}" > "$evidence_dir/compile.log" 2>&1
set +e
timeout 180s java -Xmx512m -cp "$evidence_dir/classes:$cp" at.heinz.deviceai.chief.RegistryIndependentChecks > "$evidence_dir/registry.log" 2>&1
registry_status=$?
timeout 30s java -Xmx256m -cp "$evidence_dir/classes:$cp" at.heinz.deviceai.chief.CoordinatorIndependentChecks > "$evidence_dir/coordinator.log" 2>&1
coordinator_status=$?
set -e
printf '%s\n' "$registry_status" > "$evidence_dir/registry.exit"
printf '%s\n' "$coordinator_status" > "$evidence_dir/coordinator.exit"
cat "$evidence_dir/registry.log" "$evidence_dir/coordinator.log"
[[ "$registry_status" == 0 && "$coordinator_status" == 0 ]]
