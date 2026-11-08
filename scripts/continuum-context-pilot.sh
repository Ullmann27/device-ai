#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:?pass an external output directory}
ref=${2:-WORKTREE}
junit=${CONTINUUM_JUNIT_JARS:?set to JUnit4 and Hamcrest jar paths separated by colon}
command -v kotlinc >/dev/null || { echo 'UNAVAILABLE: kotlinc' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
source_path=app/src/main/java/at/heinz/deviceai/cognitive/LocalConversationFollowUpPolicy.kt
if [[ "$ref" == WORKTREE ]]; then
    cp "$root/$source_path" "$out/LocalConversationFollowUpPolicy.kt"
else
    git -C "$root" show "$ref:$source_path" > "$out/LocalConversationFollowUpPolicy.kt"
fi
{
    git -C "$root" rev-parse HEAD
    printf 'source_ref=%s\n' "$ref"
    git -C "$root" status --porcelain
    sha256sum "$out/LocalConversationFollowUpPolicy.kt" "$root/tools/continuum-cognitive-pilots/context-cases.tsv"
    kotlinc -version
    java -version
    uname -sm
} > "$out/provenance.txt" 2>&1
kotlinc "$out/LocalConversationFollowUpPolicy.kt" \
    "$root/tools/continuum-cognitive-pilots/ContextPilot.kt" \
    "$root/app/src/test/java/at/heinz/deviceai/cognitive/LocalConversationFollowUpPolicyTest.kt" \
    "$root/app/src/test/java/at/heinz/deviceai/cognitive/ContextDiscoursePrefixTest.kt" \
    -jvm-target 17 -cp "$junit" -include-runtime -d "$out/context.jar" > "$out/compile.txt" 2>&1
java -cp "$out/context.jar" at.heinz.deviceai.cognitive.ContextPilot \
    "$root/tools/continuum-cognitive-pilots/context-cases.tsv" > "$out/context.tsv"
# A baseline RED is an actual nonzero exit, never converted to success.
java -cp "$out/context.jar:$junit" org.junit.runner.JUnitCore \
    at.heinz.deviceai.cognitive.LocalConversationFollowUpPolicyTest \
    at.heinz.deviceai.cognitive.ContextDiscoursePrefixTest | tee "$out/junit.txt"
