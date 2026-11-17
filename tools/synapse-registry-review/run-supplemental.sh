#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
classes=${1:?compiled candidate classes}
evidence_dir=${2:?fresh evidence directory}
base_classes=${SYNAPSE_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
[[ ! -e "$evidence_dir" ]] || exit 2
mkdir -p "$evidence_dir/classes"
cp=$(find "$deps" -maxdepth 1 -type f -name '*.jar' -printf '%p:')
cp="$classes:$base_classes:$cp"
sources=("$review_root/tools/synapse-registry-review/RegistryCostProbe.kt" "$review_root/tools/synapse-registry-review/RegistryMutationChecks.kt")
sha256sum "${sources[@]}" > "$evidence_dir/reviewer-source-hashes.txt"
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$cp" -jvm-target 17 -d "$evidence_dir/classes" "${sources[@]}" > "$evidence_dir/compile.log" 2>&1
set +e
timeout 30s java -Xmx512m -cp "$evidence_dir/classes:$cp" at.heinz.deviceai.chief.RegistryMutationChecks > "$evidence_dir/mutations.log" 2>&1
mutation_status=$?
set -e
printf '%s\n' "$mutation_status" > "$evidence_dir/mutations.exit"
cat "$evidence_dir/mutations.log"
ps -eo pid,pcpu,pmem,etime,args | rg 'GradleDaemon|K2JVMCompiler|blender|gradle.*test|gradle.*assemble' > "$evidence_dir/concurrent-heavy-processes.txt" || true
cat /sys/fs/cgroup/cpu.max /sys/fs/cgroup/memory.max > "$evidence_dir/host-cgroup-limits.txt"
java -version > "$evidence_dir/java-version.txt" 2>&1
set +e
python3 "$review_root/tools/synapse-registry-review/measure-process.py" "$evidence_dir/process-resource-cost.json" timeout 180s java -Xmx512m -cp "$evidence_dir/classes:$cp" at.heinz.deviceai.chief.RegistryCostProbe > "$evidence_dir/cost.jsonl" 2> "$evidence_dir/cost.stderr"
cost_status=$?
set -e
printf '%s\n' "$cost_status" > "$evidence_dir/cost.exit"
cat "$evidence_dir/cost.jsonl"
[[ "$mutation_status" == 0 && "$cost_status" == 0 ]]
