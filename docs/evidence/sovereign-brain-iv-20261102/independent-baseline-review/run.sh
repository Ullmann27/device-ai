#!/usr/bin/env bash
# Host-only scheduling/size probes against exact PR357 source; no model/device/owner state.
set -euo pipefail
repo=$(cd "$(dirname "$0")/../../../.." && pwd)
out=${1:?fresh output directory}
[[ ! -e "$out" ]]
mkdir -p "$out/src" "$out/classes"
for name in AgentToolLayer ToolExecutionReceiptStore; do
  git -C "$repo" show "d49031843b03af6ce21427a41e761d671aa05b58:app/src/main/java/at/heinz/deviceai/$name.kt" > "$out/src/$name.kt"
done
for name in BoundedAgent AgentTaskEvidence ReadOnlyToolPlan; do
  git -C "$repo" show "d49031843b03af6ce21427a41e761d671aa05b58:app/src/main/java/at/heinz/deviceai/cognitive/$name.kt" > "$out/src/$name.kt"
done
cp "$(dirname "$0")/BaselineProbe.kt" "$out/src/"
/workspace/continuum-toolchain/kotlinc -jvm-target 17 -module-name app \
  -Xfriend-paths=/workspace/sovereign-reviewed-full/main \
  -cp /workspace/sovereign-reviewed-full/main:/workspace/continuum-toolchain/stdlib.jar:/workspace/continuum-toolchain/json.jar:/workspace/continuum-toolchain/android-all-15.jar \
  "$out"/src/*.kt -d "$out/classes" > "$out/compile.txt" 2>&1
java -cp "$out/classes:/workspace/sovereign-reviewed-full/main:/workspace/continuum-toolchain/stdlib.jar:/workspace/continuum-toolchain/json.jar" \
  BaselineProbeKt > "$out/probe.txt" 2>&1
cat "$out/probe.txt"
sha256sum "$out"/src/*.kt > "$out/source.sha256"
