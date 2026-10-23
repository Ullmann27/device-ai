#!/usr/bin/env bash
# Uses an already installed Kotlin CLI and JDK17; never downloads tools or builds/installs an APK.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/hologram-motion"}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
cd "$ROOT"
SOURCES=()
for name in Painter Fonts Theme HologramPlexus OrbitalRing HologramRenderer; do
  SOURCES+=("app/src/main/java/at/heinz/deviceai/ui/Continuum${name}.kt")
done
SOURCES+=(tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt
          tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/ContinuumMotionPreview.kt)
"${KOTLINC:-kotlinc}" "${SOURCES[@]}" -jvm-target 17 -include-runtime -d "$OUT/preview.jar"
"${JAVA:-java}" -Djava.awt.headless=true -cp "$OUT/preview.jar" at.heinz.deviceai.ui.ContinuumMotionPreview "$ROOT" "$OUT"
