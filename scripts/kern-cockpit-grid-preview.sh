#!/usr/bin/env bash
# Jarvis Kern: ref5-grid preview (JVM/Java2D) side by side with ref5. Needs compiled debug classes
# (./gradlew :app:compileDebugKotlin) and KOTLINC. Output stays OUTSIDE git (default: ../jarvis-design-referenz/kontrolle-3).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT"
REF=${REF:-/workspace/jarvis-design-referenz/ref5-fold7-offen.png}
OUT=${1:-/workspace/jarvis-design-referenz/kontrolle-3/kern-cockpit-raster.png}
SDK=${ANDROID_HOME:-/opt/android-sdk}
CLASSES=app/build/tmp/kotlin-classes/debug
STDLIB=${KOTLIN_STDLIB:?set KOTLIN_STDLIB to kotlin-stdlib.jar}
P=tools/continuum-jvm-preview/src/at/heinz/deviceai/ui
"${KOTLINC:-kotlinc}" $P/Java2DContinuumPainter.kt $P/KernCockpitGridPreview.kt -cp "$CLASSES:$STDLIB:$SDK/platforms/android-36/android.jar" \
  -jvm-target 17 -d /tmp/kern-grid-preview
"${JAVA:-java}" -Djava.awt.headless=true -cp "/tmp/kern-grid-preview:$CLASSES:$STDLIB:$SDK/platforms/android-36/android.jar" \
  at.heinz.deviceai.ui.KernCockpitGridPreview "$ROOT" "$OUT" "$REF"
