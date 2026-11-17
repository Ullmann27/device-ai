#!/usr/bin/env bash
# Host renders (JVM/Java2D, not device) of the Blender assets inside the real renderers.
# Needs compiled debug classes (./gradlew :app:compileDebugKotlin), KOTLINC, KOTLIN_STDLIB and the TwelveMonkeys
# imageio-webp jars (WEBP_JARS, colon-separated; preview only, never in the APK).
# Output stays OUTSIDE git: default /workspace/code52-screenshots/<sha>/blender.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT"
SHA=$(git rev-parse HEAD)
OUT=${1:-/workspace/code52-screenshots/$SHA/blender}
SDK=${ANDROID_HOME:-/opt/android-sdk}
CLASSES=app/build/tmp/kotlin-classes/debug
STDLIB=${KOTLIN_STDLIB:?set KOTLIN_STDLIB to kotlin-stdlib.jar}
WEBP=${WEBP_JARS:?set WEBP_JARS to the TwelveMonkeys imageio-webp jars}
P=tools/continuum-jvm-preview/src/at/heinz/deviceai/ui
"${KOTLINC:-kotlinc}" $P/Java2DContinuumPainter.kt $P/BlenderAssetsPreview.kt -cp "$CLASSES:$STDLIB:$SDK/platforms/android-36/android.jar" \
  -jvm-target 17 ${KOTLINC_FLAGS:-} -d /tmp/blender-assets-preview
"${JAVA:-java}" -Djava.awt.headless=true -cp "/tmp/blender-assets-preview:$CLASSES:$STDLIB:$WEBP:$SDK/platforms/android-36/android.jar" \
  at.heinz.deviceai.ui.BlenderAssetsPreview "$ROOT" "$OUT" "$SHA"
