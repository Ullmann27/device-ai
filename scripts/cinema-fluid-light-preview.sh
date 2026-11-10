#!/usr/bin/env bash
# Java2D preview; no device proof.
set -euo pipefail
script_root=$(cd "$(dirname "$0")/.." && pwd -P)
source_root=${CONTINUUM_PREVIEW_SOURCE_ROOT:-$script_root}
out=${1:?Pass external output directory}
label=${2:-candidate}
sdk_jar=${ANDROID_JAR:?Set platform android.jar path}
mkdir -p "$out"
out=$(cd "$out" && pwd -P)
[[ "$out" != "$source_root" && "$out" != "$source_root/"* ]]
[[ "$label" =~ ^[a-zA-Z0-9._-]+$ ]]
python3 - "$source_root" "$out" <<'PY'
from pathlib import Path
import sys
root, out = map(Path, sys.argv[1:])
ui = root / 'app/src/main/java/at/heinz/deviceai/ui'
text = (ui / 'JarvisCockpitGridContent.kt').read_text()
members = text[text.index('    fun wrap('):]
fit = text[text.index('    private fun fit('):text.index('    private val CLOCK_STYLE')]
header = '''package at.heinz.deviceai.ui
import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Type
object JarvisCockpitGridContent {
'''
(out / 'HostTileMembers.kt').write_text(header + fit + members)
stop = next(line for line in (ui / 'JarvisCockpitPanels.kt').read_text().splitlines()
            if 'const val STOP_TARGET =' in line)
(out / 'HostStopConstant.kt').write_text(
    'package at.heinz.deviceai.ui\nobject JarvisCockpitPanels {\n' + stop + '\n}\n')
renderer = (ui / 'JarvisShellRenderer.kt').read_text()
call = 'JarvisShellRenderer.drawForeground(p, shell, content)'
if 'data class CinemaFrame(' in renderer:
    call = '''JarvisShellRenderer.drawForeground(p, shell, content,
        cinemaFrame = JarvisShellRenderer.CinemaFrame(elapsedMs = time,
            clearIdle = work, exiting = exit, budget = budget, timeMs = time))'''
(out / 'HostForegroundBoundary.kt').write_text('''package at.heinz.deviceai.ui
object HostForegroundBoundary {
    fun draw(p: ContinuumPainter, shell: JarvisShellLayout.Shell,
             content: Map<JarvisShellLayout.Slot, JarvisShellRenderer.SlotContent>,
             time: Long, work: Boolean, exit: Boolean, budget: JarvisRenderQuality.Budget) {
        ''' + call + '''
    }
}
''')
PY
cd "$source_root"
ui=app/src/main/java/at/heinz/deviceai/ui
base=app/src/main/java/at/heinz/deviceai
sources=()
for name in ContinuumPainter ContinuumFonts ContinuumTheme JarvisDesign JarvisNeon JarvisMotion \
    JarvisShellLayout JarvisShellRenderer JarvisTiles ContinuumIcons JarvisReferenceHud JarvisVisualDepth \
    JarvisDeckClearance JarvisCinemaEntrance HollywoodFuiConstruct JarvisRenderQuality JarvisWindowProfile \
    JarvisCockpitGrid JarvisRailIcons JarvisTileEnergy LuxuryHoloButtonRenderer PerimeterWingChoreographer \
    JarvisSceneFills ContinuumHologramRenderer ContinuumHologramPlexus ContinuumOrbitalRing; do
    sources+=("$ui/$name.kt")
done
[[ ! -f "$ui/JarvisFluidLight.kt" ]] || sources+=("$ui/JarvisFluidLight.kt")
for name in DampedSpring Easings MotionPresets MotionContracts Staggers FaceMotion; do
    sources+=("$base/motion/$name.kt")
done
sources+=("$base/cockpit/CockpitAnimationEvent.kt" "$base/cockpit/CockpitContracts.kt"
    "$base/cockpit/CockpitTimeline.kt" "$base/avatar/AvatarContract.kt"
    "$base/avatar/ContinuumAvatarRenderer.kt" "$base/avatar/AvatarIconBurst.kt"
    "$out/HostTileMembers.kt" "$out/HostStopConstant.kt" "$out/HostForegroundBoundary.kt"
    "$script_root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt"
    "$script_root/tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/CinemaFluidLightPreview.kt")
printf '%s\n' "${sources[@]}" > "$out/source-files.txt"
{
    printf 'label=%s\nsource_sha=%s\n' "$label" "$(git rev-parse HEAD)"
    git status --porcelain
    sha256sum "${sources[@]}"
    printf 'renderer=Java2D\nAGSL=NOT_RUN\nFilament=NOT_RUN\nFold7=NOT_RUN\n'
    printf 'boundaries=exact production tile members and STOP constant\n'
} > "$out/provenance.txt"
"${KOTLINC:-kotlinc}" "${sources[@]}" -cp "$sdk_jar" -jvm-target 17 -nowarn \
    -include-runtime -d "$out/preview.jar" > "$out/compile.log" 2>&1
"${JAVA:-java}" -Xmx512m -Djava.awt.headless=true -cp "$out/preview.jar:$sdk_jar" \
    at.heinz.deviceai.ui.CinemaFluidLightPreview "$source_root" "$out" "$label"
if command -v ffmpeg >/dev/null; then
    for size in fold cover; do
        ffmpeg -v error -y -framerate 24 -i "$out/$size-sequence/%03d.png" \
            -vf 'pad=ceil(iw/2)*2:ceil(ih/2)*2' -c:v libx264 -pix_fmt yuv420p -crf 19 "$out/$size-sequence.mp4"
    done
fi
