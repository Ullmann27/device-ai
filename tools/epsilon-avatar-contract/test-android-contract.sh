#!/usr/bin/env bash
set -euo pipefail
if [[ -n ${EPSILON_BUILD_ENV:-} ]]; then source "$EPSILON_BUILD_ENV"; fi
: "${CONTINUUM_FABRIC_JVM_DEPS:?Set the local JUnit/JSON/Kotlin dependency directory}"
cd "$(dirname "$0")/../.."
artifact=$(mktemp -d)
trap 'rm -rf "$artifact"' EXIT
cp_path="$CONTINUUM_FABRIC_JVM_DEPS/junit-4.13.2.jar:$CONTINUUM_FABRIC_JVM_DEPS/hamcrest-core-1.3.jar:$CONTINUUM_FABRIC_JVM_DEPS/json-20240303.jar"
kotlinc app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt \
  app/src/main/java/at/heinz/deviceai/avatar/AvatarDirector.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt \
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt \
  app/src/test/java/at/heinz/deviceai/avatar/*.kt \
  app/src/test/java/at/heinz/deviceai/avatar3d/*.kt \
  tools/epsilon-avatar-contract/ValidateAvatarGlb.kt -cp "$cp_path" -d "$artifact/tests.jar"
runtime_cp="$artifact/tests.jar:$cp_path:$CONTINUUM_FABRIC_JVM_DEPS/kotlin-stdlib-2.4.0.jar"
java -cp "$runtime_cp" org.junit.runner.JUnitCore \
  at.heinz.deviceai.avatar3d.Avatar3dContractTest \
  at.heinz.deviceai.avatar3d.AvatarMouthCouplingTest \
  at.heinz.deviceai.avatar.AvatarDirectorTest \
  at.heinz.deviceai.avatar.AvatarPortedPartsTest \
  at.heinz.deviceai.avatar.AvatarExistingContractRegressionTest \
  at.heinz.deviceai.avatar.AvatarNativeRegressionTest \
  at.heinz.deviceai.avatar.AvatarObservedGazeTest
java -cp "$runtime_cp" ValidateAvatarGlbKt app/src/main/assets/avatar3d/continuum-avatar.glb
