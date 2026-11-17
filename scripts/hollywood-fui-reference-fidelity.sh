#!/usr/bin/env bash
# Measures reference fidelity of the work-window glass construct: the production painter stack is rendered with Java2D
# at reference pixel scale and compared against glass panels of the owner's reference designs with the same pixel
# statistics (rim luminance, halo spill, interior darkness, palette). Negative controls must fail. Usage:
#   scripts/hollywood-fui-reference-fidelity.sh OUT_DIR [LABEL] [CINEMATICS_OVERRIDE.kt]
# OUT_DIR must be outside the source tree. LABEL names the measured source (default: candidate). The optional third
# argument substitutes app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiCinematics.kt, so another branch's material
# can be measured read-only without checking it out. Uses an installed Kotlin CLI and JDK 17+; never downloads tools,
# never builds an APK. Results are Java2D evidence, not device pixels or frame times.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd -P)
out=${1:?external output directory}
label=${2:-candidate}
override=${3:-}
[[ "$label" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { echo 'Invalid measurement label' >&2; exit 2; }
command -v "${KOTLINC:-kotlinc}" >/dev/null || { echo 'UNAVAILABLE: kotlinc' >&2; exit 2; }
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$root" && "$out" != "$root/"* ]] || { echo 'Output must be outside source' >&2; exit 2; }
cinematics=$root/app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiCinematics.kt
if [[ -n "$override" ]]; then
    cp "$override" "$out/HollywoodFuiCinematics.kt"
    cinematics=$out/HollywoodFuiCinematics.kt
fi
sources=(
  "$root/app/src/main/java/at/heinz/deviceai/ui/ContinuumPainter.kt"
  "$root/app/src/main/java/at/heinz/deviceai/ui/ContinuumFonts.kt"
  "$root/app/src/main/java/at/heinz/deviceai/ui/ContinuumTheme.kt"
  "$root/app/src/main/java/at/heinz/deviceai/ui/JarvisDesign.kt"
  "$root/app/src/main/java/at/heinz/deviceai/ui/JarvisRenderQuality.kt"
  "$root/app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiConstruct.kt"
    "$root/app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiWorkActivity.kt"
"$cinematics"
  "$root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt"
  "$root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiCinematicsPreview.kt"
  "$root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiReferenceFidelity.kt"
  "$root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/HollywoodFuiReferenceFidelityChecks.kt"
)
{
    echo "label=$label"
    echo "head=$(git -C "$root" rev-parse HEAD)"
    git -C "$root" status --porcelain | sed 's/^/dirty: /'
    echo "cinematics_override=${override:-none}"
    sha256sum "${sources[@]}" "$root/docs/design-screenshots/ref5-fold7-offen.png" "$root/docs/design-screenshots/jarvis-master-20261104.jpg"
    "${KOTLINC:-kotlinc}" -version 2>&1
    "${JAVA:-java}" -version 2>&1
} > "$out/$label-provenance.txt"
"${KOTLINC:-kotlinc}" "${sources[@]}" -jvm-target 17 -nowarn -include-runtime -d "$out/$label-fidelity.jar" > "$out/$label-compile.txt" 2>&1
"${JAVA:-java}" -Djava.awt.headless=true -cp "$out/$label-fidelity.jar" at.heinz.deviceai.ui.HollywoodFuiReferenceFidelityChecks \
    > "$out/$label-checks.txt"
set +e
"${JAVA:-java}" -Djava.awt.headless=true -cp "$out/$label-fidelity.jar" at.heinz.deviceai.ui.HollywoodFuiReferenceFidelity \
    "$root" "$out" "$label" | tee "$out/$label-run.txt"
status=${PIPESTATUS[0]}
set -e
echo "exit=$status" >> "$out/$label-provenance.txt"
exit "$status"
