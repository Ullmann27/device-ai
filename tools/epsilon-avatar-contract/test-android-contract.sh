#!/usr/bin/env bash
set -euo pipefail
source /workspace/continuum-build-env/env.sh
cd "$(dirname "$0")/../.."
artifact=$(mktemp -d)
trap 'rm -rf "$artifact"' EXIT
cp_path="$CONTINUUM_FABRIC_JVM_DEPS/junit-4.13.2.jar:$CONTINUUM_FABRIC_JVM_DEPS/hamcrest-core-1.3.jar:$CONTINUUM_FABRIC_JVM_DEPS/json-20240303.jar"
kotlinc app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt app/src/test/java/at/heinz/deviceai/avatar3d/Avatar3dContractTest.kt -cp "$cp_path" -d "$artifact/tests.jar"
java -cp "$artifact/tests.jar:$cp_path:$CONTINUUM_FABRIC_JVM_DEPS/kotlin-stdlib-2.4.0.jar" org.junit.runner.JUnitCore at.heinz.deviceai.avatar3d.Avatar3dContractTest
