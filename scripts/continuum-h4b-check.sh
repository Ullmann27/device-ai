#!/usr/bin/env bash
# Compiles the situation tracker/model from a git ref (original arm) or the worktree
# (candidate arms) together with the JUnit tests and the H4-B replay harness.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?external output directory}
ref=${2:-WORKTREE}
junit=${CONTINUUM_JUNIT_JARS:?JUnit4 and Hamcrest classpath}
command -v kotlinc >/dev/null || { echo 'UNAVAILABLE: kotlinc' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
pilots=$root/tools/continuum-cognitive-pilots
sources=()
for name in LocalConversationSituationTracker ConversationSituationModel; do
    path=app/src/main/java/at/heinz/deviceai/cognitive/$name.kt
    if [[ "$ref" == WORKTREE ]]; then cp "$root/$path" "$out/$name.kt"; else git -C "$root" show "$ref:$path" > "$out/$name.kt"; fi
    sources+=("$out/$name.kt")
done
if [[ "$ref" == WORKTREE ]]; then arms=$pilots/h4b-arms/candidate/H4BArms.kt; else arms=$pilots/h4b-arms/original/H4BArms.kt; fi
tests=(ConversationSituationModelTest LocalConversationSituationTrackerTest SituationReturnMorphologyTest)
test_paths=()
for name in "${tests[@]}"; do test_paths+=("$root/app/src/test/java/at/heinz/deviceai/cognitive/$name.kt"); done
{
    git -C "$root" rev-parse HEAD
    printf 'source_ref=%s\n' "$ref"
    git -C "$root" status --porcelain
    sha256sum "${sources[@]}" "$arms" "$pilots/H4BReturnPilot.kt" "${test_paths[@]}"
    kotlinc -version
    java -version
    uname -sm
} > "$out/provenance.txt" 2>&1
kotlinc "${sources[@]}" "${test_paths[@]}" "$pilots/H4BReturnPilot.kt" "$arms" \
    -jvm-target 17 -cp "$junit" -include-runtime -d "$out/h4b.jar" > "$out/compile.txt" 2>&1
# A baseline RED is a real nonzero exit; it is recorded, never converted to success.
set +e
java -Xmx256m -cp "$out/h4b.jar:$junit" org.junit.runner.JUnitCore \
    "${tests[@]/#/at.heinz.deviceai.cognitive.}" | tee "$out/junit.txt"
status=${PIPESTATUS[0]}
set -e
echo "junit_exit=$status" >> "$out/provenance.txt"
exit "$status"
