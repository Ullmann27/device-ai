#!/usr/bin/env bash
set -euo pipefail
if [[ -n ${EPSILON_BUILD_ENV:-} ]]; then source "$EPSILON_BUILD_ENV"; fi
: "${CONTINUUM_FABRIC_JVM_DEPS:?Set the local JSON/Kotlin dependency directory}"
project_root=$(cd "$(dirname "$0")/../.." && pwd)
artifact=$(mktemp -d)
trap 'rm -rf "$artifact"' EXIT
cp_path="$CONTINUUM_FABRIC_JVM_DEPS/json-20240303.jar"
kotlinc "$project_root/app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt" "$project_root/tools/epsilon-avatar-contract/ValidateAvatarGlb.kt" -cp "$cp_path" -d "$artifact/validator.jar"
java -cp "$artifact/validator.jar:$cp_path:$CONTINUUM_FABRIC_JVM_DEPS/kotlin-stdlib-2.4.0.jar" ValidateAvatarGlbKt "$@"
