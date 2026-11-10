#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:?external output directory}
compiler=${KOTLINC:-kotlinc}
junit=${CONTINUUM_JUNIT_JARS:?JUnit4 and Hamcrest classpath}
classes=${CONTINUUM_COMPILED_CLASSES:?frozen Android debug classes}
json=${CONTINUUM_JSON_JAR:?org.json jar}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || exit 2
sources=()
for name in MemoryEcology AgentExperienceRecall CognitiveCore; do
    sources+=("$root/app/src/main/java/at/heinz/deviceai/cognitive/$name.kt")
done
tests=()
for name in ExperienceRecallRecency AgentExperienceRecall MemoryEcology; do
    sources+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/${name}Test.kt")
    tests+=("at.heinz.deviceai.cognitive.${name}Test")
done
git -C "$root" rev-parse HEAD > "$out/head.txt"
git -C "$root" status --short > "$out/worktree.txt"
sha256sum "${sources[@]}" "$json" > "$out/source-sha256.txt"
python3 - "$classes" > "$out/dependency-sha256.txt" <<'PY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1])
digest = hashlib.sha256()
files = sorted(root.rglob('*.class'))
for path in files:
    digest.update(str(path.relative_to(root)).encode())
    digest.update(b'\0')
    digest.update(hashlib.sha256(path.read_bytes()).digest())
print(len(files), digest.hexdigest())
PY
"$compiler" -version > "$out/toolchain.txt" 2>&1
java -version >> "$out/toolchain.txt" 2>&1
printf '%s\n' 'DEPENDENCY_CLASS_SNAPSHOT: not a full Android build' >> "$out/toolchain.txt"
"$compiler" "${sources[@]}" -jvm-target 17 -Xfriend-paths="$classes" \
    -cp "$classes:$json:$junit" -include-runtime -d "$out/recall.jar" > "$out/compile.txt" 2>&1
java -XX:-UsePerfData -cp "$out/recall.jar:$classes:$json:$junit" \
    org.junit.runner.JUnitCore "${tests[@]}" | tee "$out/junit.txt"
