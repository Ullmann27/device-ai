#!/usr/bin/env bash
# §11 comparison sheet (Java2D preview left, ref5 crop right). The reference stays outside Git.
# Usage: scripts/jarvis-ref5-compare.sh <tokens|bloom-glas-partikel> [ref5.png] [out-dir]
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd); cd "$ROOT"
MODE=${1:?tokens|bloom-glas-partikel}
REF=${2:-/workspace/jarvis-design-referenz/ref5-fold7-offen.png}
OUT=${3:-/workspace/jarvis-design-referenz/kontrolle-3}
U=app/src/main/java/at/heinz/deviceai/ui; P=tools/continuum-jvm-preview/src/at/heinz/deviceai/ui
"${KOTLINC:-kotlinc}" $U/ContinuumPainter.kt $U/ContinuumFonts.kt $U/ContinuumTheme.kt $U/JarvisDesign.kt $U/JarvisNeon.kt \
  $U/JarvisMotion.kt $U/JarvisShellLayout.kt $U/JarvisShellRenderer.kt $U/JarvisTiles.kt $U/ContinuumIcons.kt \
  $P/Java2DContinuumPainter.kt $P/JarvisRef5Compare.kt -jvm-target 17 -d /tmp/jarvis-ref5-compare.jar
"${JAVA:-java}" -Djava.awt.headless=true -cp "/tmp/jarvis-ref5-compare.jar:${KOTLIN_STDLIB:?set KOTLIN_STDLIB to kotlin-stdlib.jar}" \
  at.heinz.deviceai.ui.JarvisRef5Compare "$ROOT" "$REF" "$OUT/welten-optik-$MODE.png" "$MODE"
