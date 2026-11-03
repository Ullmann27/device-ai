#!/usr/bin/env bash
set -euo pipefail
if [[ -n ${EPSILON_BUILD_ENV:-} ]]; then source "$EPSILON_BUILD_ENV"; fi
: "${ANDROID_HOME:?Set the local Android SDK directory}"
: "${EPSILON_FILAMENT_DIR:?Set the directory containing verified Filament/gltfio classes JARs}"
cd "$(dirname "$0")/../.."
filament_dir=$EPSILON_FILAMENT_DIR
artifact=$(mktemp -d)
trap 'rm -rf "$artifact"' EXIT
kotlinc app/src/main/java/at/heinz/deviceai/avatar/*.kt app/src/main/java/at/heinz/deviceai/avatar3d/*.kt \
  app/src/main/java/at/heinz/deviceai/HologramAudio.kt app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt app/src/main/java/at/heinz/deviceai/ui/AndroidContinuumPainter.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramRenderer.kt app/src/main/java/at/heinz/deviceai/ui/ContinuumHologramPlexus.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumFonts.kt app/src/main/java/at/heinz/deviceai/ui/ContinuumTheme.kt \
  app/src/main/java/at/heinz/deviceai/ui/ContinuumOrbitalRing.kt \
  -cp "$ANDROID_HOME/platforms/android-36/android.jar:$filament_dir/filament-classes.jar:$filament_dir/gltfio-classes.jar" -d "$artifact/view.jar"
echo 'PASS: actual Android SDK36 + pinned Filament/gltfio AAR API compile; GPU/device not executed.'
