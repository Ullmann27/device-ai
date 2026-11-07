#!/usr/bin/env bash
set -euo pipefail
review_root=$(cd "$(dirname "$0")/../.." && pwd)
old_run=${1:?reviewed real V1 conversation compiled run}
new_run=${2:?reviewed candidate compiled run}
out=${3:?fresh output directory}
base_classes=${GENESIS_BASE_CLASSES:-/workspace/continuum-breakthrough-quality/app/build/tmp/kotlin-classes/debug}
deps=${CONTINUUM_FABRIC_JVM_DEPS:-/workspace/continuum-build-env/native-graph-deps}
[[ ! -e "$out" ]] || { echo 'Output must be fresh' >&2; exit 2; }
mkdir -p "$out/classes" "$out/fixture"
cp=$(find "$deps" -maxdepth 1 -name '*.jar' -type f -printf '%p:')
cp="$base_classes:$cp"
source_file="$review_root/tools/genesis-brain-review/ConversationCodecChecks.kt"
sha256sum "$source_file" > "$out/test.sha256"
cp "$old_run/candidate-sha.txt" "$out/old-source-sha.txt"
cp "$new_run/candidate-sha.txt" "$out/new-source-sha.txt"
for version in old new; do
  if [[ "$version" == old ]]; then candidate_classes="$old_run/classes"; else candidate_classes="$new_run/classes"; fi
  find "$candidate_classes/at/heinz/deviceai/chief" -name '*.class' -type f -exec sha256sum {} + > "$out/$version-class.sha256"
done
java -Xmx512m -cp "$cp" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect \
  -Xfriend-paths="$new_run/classes" -classpath "$new_run/classes:$cp" -jvm-target 17 \
  -d "$out/classes" "$source_file" > "$out/compile.log" 2>&1
timeout 30s java -Xmx256m -cp "$old_run/classes:$out/classes:$cp" \
  at.heinz.deviceai.chief.ConversationCodecChecks write-v1 "$out/fixture" > "$out/old-fixture.log" 2>&1
set +e
timeout 30s java -Xmx256m -cp "$new_run/classes:$out/classes:$cp" \
  at.heinz.deviceai.chief.ConversationCodecChecks verify "$out/fixture" > "$out/codec.log" 2>&1
result=$?
set -e
printf '%s\n' "$result" > "$out/codec.exit"
cat "$out/old-fixture.log" "$out/codec.log"
exit "$result"
