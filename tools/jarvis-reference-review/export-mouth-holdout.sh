#!/usr/bin/env bash
set -euo pipefail
source_root=$(realpath "$1")
expected_sha=$2
output=$(realpath -m "$3")
review_root=$(cd "$(dirname "$0")/../.." && pwd)
[[ $(git -C "$source_root" rev-parse HEAD) == "$expected_sha" ]] || { echo SOURCE_SHA_MISMATCH >&2; exit 2; }
[[ -z $(git -C "$source_root" status --porcelain --untracked-files=no) ]] || { echo DIRTY_TRACKED_SOURCE >&2; exit 2; }
mkdir -p "$output"
build_env=${MOMEGA_BUILD_ENV:-/workspace/momega1-build-env}
export JAVA_HOME="$build_env/jdk17"
export JAVA_OPTS='-XX:ActiveProcessorCount=1 -Xmx768m'
"$build_env/kotlinc/bin/kotlinc" \
 "$source_root/app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt" \
 "$source_root/app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt" \
 "$source_root/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt" \
 "$source_root/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt" \
 "$review_root/tools/jarvis-reference-review/ExportMouthHoldoutPoses.kt" \
 -jvm-target 17 -cp "$build_env/jvm-deps/android-all-15.jar" -include-runtime -d "$output/exporter.jar" > "$output/compile.log" 2>&1
"$JAVA_HOME/bin/java" -XX:ActiveProcessorCount=1 -Xmx256m -cp "$output/exporter.jar:$build_env/jvm-deps/android-all-15.jar" \
 at.heinz.deviceai.referencecheck.ExportMouthHoldoutPosesKt "$expected_sha" > "$output/poses.json"
