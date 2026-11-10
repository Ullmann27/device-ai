#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
baseline=${1:?baseline Git ref}
candidate=${2:?candidate Git ref}
out=${3:?external output directory}
compiler=${KOTLINC:-kotlinc}
classes=${CONTINUUM_COMPILED_CLASSES:?frozen baseline Android debug classes}
json=${CONTINUUM_JSON_JAR:?org.json jar}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
for arm in baseline candidate; do
    ref=$baseline
    [[ "$arm" == candidate ]] && ref=$candidate
    mkdir -p "$out/$arm/source"
    sources=()
    names=(ConversationSituationModel LocalConversationSituationTracker LocalConversationContext)
    [[ "$arm" == candidate ]] && names+=(WorkingTaskContext)
    for name in "${names[@]}"; do
        git -C "$root" show "$ref:app/src/main/java/at/heinz/deviceai/cognitive/$name.kt" > "$out/$arm/source/$name.kt"
        sources+=("$out/$arm/source/$name.kt")
    done
    git -C "$root" rev-parse "$ref" > "$out/$arm/source-commit.txt"
    sha256sum "${sources[@]}" "$root/scripts/research/omega/IndependentWorkingTaskHoldout.kt" \
        "$root/docs/research/omega/OMEGA-INDEPENDENT-HOLDOUT-20261110.json" > "$out/$arm/source-sha256.txt"
    "$compiler" "${sources[@]}" "$root/scripts/research/omega/IndependentWorkingTaskHoldout.kt" \
        -jvm-target 17 -Xfriend-paths="$classes" -cp "$classes:$json" -include-runtime \
        -d "$out/$arm/holdout.jar" > "$out/$arm/compile.log" 2>&1
    java -XX:-UsePerfData -cp "$out/$arm/holdout.jar:$classes:$json" \
        at.heinz.deviceai.cognitive.IndependentWorkingTaskHoldoutKt "$arm" \
        "$root/docs/research/omega/OMEGA-INDEPENDENT-HOLDOUT-20261110.json" "$out/$arm/results.json"
    java -XX:-UsePerfData -cp "$out/$arm/holdout.jar:$classes:$json" \
        at.heinz.deviceai.cognitive.IndependentWorkingTaskHoldoutKt "$arm" \
        "$root/docs/research/omega/OMEGA-INDEPENDENT-HOLDOUT-20261110.json" "$out/$arm/performance.json" performance
done
python3 - "$classes" > "$out/dependency-sha256.txt" <<'PY'
import hashlib,pathlib,sys
root=pathlib.Path(sys.argv[1]);h=hashlib.sha256();files=sorted(root.rglob('*.class'))
for path in files:
    h.update(str(path.relative_to(root)).encode()+b'\0'+hashlib.sha256(path.read_bytes()).digest())
print(len(files),h.hexdigest())
PY
"$compiler" -version > "$out/toolchain.txt" 2>&1
java -version >> "$out/toolchain.txt" 2>&1
