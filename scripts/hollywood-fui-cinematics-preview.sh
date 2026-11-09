#!/usr/bin/env bash
# Renders the production painter layers of the work-window construct (HollywoodFuiCinematics driven by
# HollywoodFuiConstruct) at the phase timestamps of the law with Java2D. Uses an installed Kotlin CLI and JDK 17+;
# never downloads tools or builds an APK. Frames are evidence of the drawing law, not of device frame times.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/hollywood-fui-cinematics"}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
cd "$ROOT"
SOURCES=(
  app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumFonts.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumTheme.kt
  app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt
  app/src/main/java/at/heinz/deviceai/ui/JarvisRenderQuality.kt
  app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiConstruct.kt
  app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiWorkActivity.kt
  app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiCinematics.kt
  tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt
  tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiCinematicsPreview.kt
  tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiPainterBenchmark.kt
)
"${KOTLINC:-kotlinc}" "${SOURCES[@]}" -jvm-target 17 -nowarn -include-runtime -d "$OUT/preview.jar"
"${JAVA:-java}" -Djava.awt.headless=true -cp "$OUT/preview.jar" at.heinz.deviceai.ui.HollywoodFuiCinematicsPreview "$ROOT" "$OUT"
"${JAVA:-java}" -cp "$OUT/preview.jar" at.heinz.deviceai.ui.HollywoodFuiPainterBenchmark > "$OUT/geometry-benchmark.csv"
