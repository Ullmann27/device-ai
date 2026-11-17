#!/usr/bin/env bash
set -euo pipefail
candidate=${1:?exact candidate commit required}
evidence=${2:?evidence output directory required}
repo=$(git rev-parse --show-toplevel)
mkdir -p "$evidence"
evidence=$(cd "$evidence" && pwd)
extract=$(mktemp -d)
trap 'rm -rf "$extract"' EXIT
files=(ContinuumTypedCodingBridge.kt ContinuumCodingTask.kt ContinuumMobileBuildPlan.kt ContinuumOwnedProjectPlan.kt cognitive/DevelopmentBacklog.kt learning/StrictJson.kt)
printf 'candidate=%s\n' "$(git rev-parse "$candidate^{commit}")" > "$evidence/source.txt"
for file in "${files[@]}"; do
  path="app/src/main/java/at/heinz/deviceai/$file"
  target="$extract/${file//\//_}"
  git show "$candidate:$path" > "$target"
  sha256sum "$target" | sed "s|$extract/||" >> "$evidence/source.txt"
done
sha256sum "$repo/tools/momega1-android-review/IndependentTypedBridgeChecks.kt" >> "$evidence/source.txt"
set +e
/workspace/momega1-build-env/kotlinc/bin/kotlinc "$extract"/*.kt "$repo/tools/momega1-android-review/IndependentTypedBridgeChecks.kt" -include-runtime -d "$extract/checks.jar" > "$evidence/compile.txt" 2>&1
compile=$?
printf '%s\n' "$compile" > "$evidence/compile.exit"
if [ "$compile" -ne 0 ]; then cat "$evidence/compile.txt"; exit "$compile"; fi
java -jar "$extract/checks.jar" > "$evidence/checks.txt" 2>&1
result=$?
printf '%s\n' "$result" > "$evidence/checks.exit"
cat "$evidence/checks.txt"
exit "$result"
