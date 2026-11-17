#!/usr/bin/env bash
set -euo pipefail

# Exact production sources; fixture-only Android classes are never placed in app/src.
# Does not execute Android, Robolectric, rendering, a real Looper or a device.
root=$(cd "$(dirname "$0")/../.." && pwd)
compiler_lib=${CONTINUUM_KOTLIN_COMPILER_LIB:?Set to Kotlin compiler dependency directory (Gradle 8.11.1/lib is supported)}
out=${1:?Pass a fresh output directory}
mkdir -p "$out"
out=$(cd "$out" && pwd)
if [ -e "$out/classes" ]; then
    echo 'Refusing to reuse compiled classes; choose a fresh output directory.' >&2
    exit 2
fi
mkdir "$out/classes"
stdlib="$compiler_lib/kotlin-stdlib-2.0.20.jar"
junit="$compiler_lib/junit-4.13.2.jar"
hamcrest="$compiler_lib/hamcrest-core-1.3.jar"
for required in "$stdlib" "$junit" "$hamcrest" "$compiler_lib/kotlin-compiler-embeddable-2.0.20.jar"; do
    test -s "$required" || { echo "Missing compiler/test dependency: $required" >&2; exit 2; }
done
cd "$root"
source_root=app/src/main/java/at/heinz/deviceai
sources=(
    "$source_root/avatar/AvatarContract.kt"
    "$source_root/cockpit/CockpitAnimationEvent.kt"
    "$source_root/cockpit/CockpitContracts.kt"
    "$source_root/motion/AvatarPoseBridge.kt"
    "$source_root/motion/DampedSpring.kt"
    "$source_root/motion/Easings.kt"
    "$source_root/motion/FaceMotion.kt"
    "$source_root/motion/MotionContracts.kt"
    "$source_root/motion/MotionPresets.kt"
    "$source_root/motion/Staggers.kt"
    "$source_root/motion/TrackTimeline.kt"
    "$source_root/ui/ContinuumPainter.kt"
    "$source_root/ui/ContinuumTheme.kt"
    "$source_root/ui/ContinuumIcons.kt"
    "$source_root/ui/ContinuumSurfaces.kt"
    "$source_root/ui/JarvisDesign.kt"
    "$source_root/ui/JarvisMotion.kt"
    "$source_root/ui/JarvisNeon.kt"
    "$source_root/ui/JarvisReferenceHud.kt"
    "$source_root/ui/JarvisCockpitGrid.kt"
    "$source_root/ui/JarvisWindowProfile.kt"
    "$source_root/ui/JarvisShellLayout.kt"
    "$source_root/ui/JarvisSequenceEffects.kt"
    "$source_root/ui/BrainOrbAnimationRenderer.kt"
    "$source_root/ui/JarvisSequenceView.kt"
)
fixtures=(tools/sequence-turn-check/fixtures/*.kt tools/sequence-turn-check/SequenceLifecycleHostTest.kt)
regressions=(app/src/test/java/at/heinz/deviceai/ui/BrainOrbSignalsTest.kt)
sha256sum "${sources[@]}" "${fixtures[@]}" "${regressions[@]}" tools/sequence-turn-check/run.sh > "$out/input-sha256.txt"
java -Xmx1g -cp "$compiler_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -no-reflect -jvm-target 17 -nowarn \
    -classpath "$stdlib:$junit:$hamcrest" -d "$out/classes" \
    "${sources[@]}" "${fixtures[@]}" "${regressions[@]}" > "$out/compile.log" 2>&1
set +e
java -Xmx512m -cp "$out/classes:$stdlib:$junit:$hamcrest" org.junit.runner.JUnitCore \
    at.heinz.deviceai.ui.SequenceLifecycleHostTest at.heinz.deviceai.ui.BrainOrbSignalsTest > "$out/junit.log" 2>&1
status=$?
set -e
cat "$out/junit.log"
printf 'HOST_FIXTURE_ONLY_EXIT_CODE=%s\n' "$status"
sha256sum -c "$out/input-sha256.txt" > "$out/input-integrity.log"
exit "$status"
