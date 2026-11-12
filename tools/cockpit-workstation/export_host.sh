#!/usr/bin/env bash
set -euo pipefail
scene_root=$(cd "$(dirname "$0")/../.." && pwd)
scene_out=${1:?Output directory required}
scene_java=${SCENE_JAVA:-java}
scene_lib=${SCENE_KOTLIN_LIB:?Kotlin compiler library directory required}
scene_json=${SCENE_JSON_JAR:?org.json jar required}
scene_stdlib=${SCENE_STDLIB_JAR:?Kotlin standard library jar required}
mkdir -p "$scene_out/classes"
cd "$scene_root"
scene_sources=(
  app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumFonts.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumTheme.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramPlexus.kt
  app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarFraming.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/GazeBlinkPolicy.kt
  app/src/main/java/at/heinz/deviceai/avatar3d/CriticalDampedSpring.kt
  app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt
  app/src/main/java/at/heinz/deviceai/avatar/AvatarSentenceAccentPolicy.kt
  tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt
  tools/cockpit-workstation/HostSceneSources.kt
)
git rev-parse HEAD > "$scene_out/source-commit.txt"
sha256sum "${scene_sources[@]}" > "$scene_out/source-inputs.sha256"
"$scene_java" -Xmx768m -cp "$scene_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 17 -cp "$scene_stdlib:$scene_json" \
  -d "$scene_out/classes" "${scene_sources[@]}"
"$scene_java" -Djava.awt.headless=true -cp "$scene_out/classes:$scene_stdlib:$scene_json" \
  at.heinz.deviceai.ui.HostSceneSources "$scene_root" "$scene_out"
