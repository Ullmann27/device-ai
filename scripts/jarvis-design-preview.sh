#!/usr/bin/env bash
# JVM preview of the Jarvis design shell (Java2D, same drawing code as the device). No APK build.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/docs/design-screenshots"}
mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd); cd "$ROOT"
U=app/src/main/java/at/heinz/deviceai/ui; P=tools/continuum-jvm-preview/src/at/heinz/deviceai/ui
"${KOTLINC:-kotlinc}" $U/ContinuumPainter.kt $U/ContinuumFonts.kt $U/ContinuumTheme.kt $U/JarvisDesign.kt $U/JarvisNeon.kt \
  $U/JarvisMotion.kt $U/JarvisShellLayout.kt $U/JarvisShellRenderer.kt $P/Java2DContinuumPainter.kt $P/JarvisDesignPreview.kt \
  -jvm-target 17 -d /tmp/jarvis-preview.jar
"${JAVA:-java}" -Djava.awt.headless=true -cp "/tmp/jarvis-preview.jar:${KOTLIN_STDLIB:?set KOTLIN_STDLIB to kotlin-stdlib.jar}" at.heinz.deviceai.ui.JarvisDesignPreview "$ROOT" "$OUT"
