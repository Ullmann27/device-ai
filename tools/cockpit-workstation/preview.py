#!/usr/bin/env python3
"""Reproduce source-bound cockpit inspection images."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

from PIL import Image, ImageChops, ImageDraw, ImageFont


TOOL_ROOT = Path(__file__).resolve().parent
UI_SOURCES = '''ContinuumPainter ContinuumFonts ContinuumTheme JarvisDesign JarvisNeon
JarvisMotion JarvisShellLayout JarvisShellRenderer JarvisTiles ContinuumIcons JarvisReferenceHud
JarvisVisualDepth JarvisDeckClearance JarvisCinemaEntrance HollywoodFuiConstruct JarvisRenderQuality
JarvisWindowProfile JarvisCockpitGrid JarvisRailIcons JarvisTileEnergy LuxuryHoloButtonRenderer
PerimeterWingChoreographer JarvisSceneFills ContinuumHologramRenderer ContinuumHologramPlexus
ContinuumOrbitalRing JarvisFluidLight CinematicWorkstationLayout CockpitGlassButtons JarvisHeadAura JarvisEnergyFlows
CockpitMonitorMotion'''.split()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, log):
    with log.open('w') as target:
        result = subprocess.run(command, stdout=target, stderr=subprocess.STDOUT)
    if result.returncode:
        print(log.read_text()[-9000:])
        raise RuntimeError(f'Command failed: {Path(command[0]).name}; exit={result.returncode}')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def compose(work, out, exported, layer_depths):
    layouts = {name: json.loads((work / f'{name}-layout.json').read_text()) for name in ('fold', 'phone')}
    for frame in exported['frames']:
        name, key = frame['layout'], frame['key']
        layout = layouts[name]
        result = Image.open(work / f'{name}-background.png').convert('RGBA')
        result.alpha_composite(Image.open(work / f'{key}-orb.png').convert('RGBA'))
        avatar = Image.open(work / f'{name}-avatar.png').convert('RGBA')
        mask = Image.open(work / f'{name}-head-mask.png').getchannel('A')
        avatar.putalpha(ImageChops.multiply(avatar.getchannel('A'), mask))
        result.alpha_composite(avatar, (int(layout['head']['left']), int(layout['head']['top'])))
        for layer in sorted(layer_depths, key=layer_depths.get):
            if layer == 'foreground':
                result.alpha_composite(Image.open(work / f'{name}-foreground.png').convert('RGBA'))
            else:
                result.alpha_composite(Image.open(work / f'{key}-stage.png').convert('RGBA'),
                                       (int(layout['scene']['left']), int(layout['scene']['top'])))
        footer = Image.new('RGB', (result.width, result.height + 70), '#040816')
        footer.paste(result, (0, 0), result)
        draw = ImageDraw.Draw(footer)
        font = ImageFont.load_default(size=12)
        draw.text((10, result.height + 9), f'HOSTRENDER | {name.upper()} | {frame["phase"]}',
                  font=font, fill='#8deeff')
        draw.text((10, result.height + 29), 'Originale Assets + Kotlin-Geometrie | Blender + Java2D',
                  font=font, fill='#d9e4ef')
        draw.text((10, result.height + 49), 'Kein Filament/Fold7-Beleg | Monitor ohne Livebild',
                  font=font, fill='#c2abc8')
        target = work if frame['phase'].startswith('sequence-') else out
        footer.save(target / f'{key}.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=TOOL_ROOT.parents[1])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--kotlin-lib-dir', type=Path, default=os.environ.get('KOTLIN_LIB_DIR'))
    parser.add_argument('--json-jar', type=Path, default=os.environ.get('JSON_JAR'))
    parser.add_argument('--android-jar', type=Path, default=os.environ.get('ANDROID_JAR'))
    parser.add_argument('--samples', type=int, default=12)
    parser.add_argument('--video-samples', type=int)
    parser.add_argument('--video', action='store_true')
    args = parser.parse_args()
    root, out = args.repo_root.resolve(), args.output.resolve()
    if not args.kotlin_lib_dir or not args.json_jar:
        raise ValueError('Set Kotlin compiler lib directory and org.json jar')
    libs = args.kotlin_lib_dir.resolve()
    stdlib = sorted(libs.glob('kotlin-stdlib-*.jar'))
    if not stdlib or not args.json_jar.is_file():
        raise ValueError('Missing Kotlin stdlib or org.json jar')
    if not 1 <= args.samples <= 64:
        raise ValueError('Samples outside review bounds')
    if args.video_samples is not None and not 1 <= args.video_samples <= 64:
        raise ValueError('Video samples outside review bounds')
    for executable in ('java', 'blender') + (('ffmpeg',) if args.video else ()):
        if shutil.which(executable) is None:
            raise ValueError(f'Missing installed executable: {executable}')
    if out.exists() and any(out.iterdir()):
        raise ValueError('Use an empty output directory')
    out.mkdir(parents=True, exist_ok=True)
    source_commit = git(root, 'rev-parse', 'HEAD')
    main = root / 'app/src/main/java/at/heinz/deviceai'
    ui = main / 'ui'
    sources = [ui / f'{name}.kt' for name in UI_SOURCES]
    sources += [main / 'motion' / f'{name}.kt' for name in
                ('DampedSpring', 'Easings', 'MotionPresets', 'MotionContracts', 'Staggers', 'FaceMotion')]
    sources += [main / 'cockpit' / f'{name}.kt' for name in
                ('CockpitAnimationEvent', 'CockpitContracts', 'CockpitTimeline')]
    sources += [main / 'avatar' / 'AvatarContract.kt']
    sources += [main / 'avatar3d' / f'{name}.kt' for name in
                ('AvatarGlbContract', 'AvatarMorphMapper', 'AvatarIdleMotion', 'GazeBlinkPolicy',
                 'CriticalDampedSpring', 'AvatarFraming', 'CockpitStageFrame')]
    sources += [root / 'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt']
    assets = [root / 'app/src/main/assets/cinema3d/cockpit-workstation.glb',
              root / 'app/src/main/assets/avatar3d/continuum-avatar.glb']
    tracked_sources = sources + assets + [ui / 'JarvisCockpitGridContent.kt', ui / 'JarvisCockpitPanels.kt',
                                         ui / 'ContinuumHomeView.kt',
                                         main / 'avatar3d/FilamentCockpitStage.kt']
    before = {str(path.relative_to(root)): sha(path) for path in tracked_sources}
    stage_source = (main / 'avatar3d/FilamentCockpitStage.kt').read_text()
    pin = re.search(r'const val ASSET_SHA256\s*=\s*"([0-9a-f]{64})"', stage_source)
    if not pin or pin.group(1) != sha(assets[0]):
        raise ValueError('Cockpit asset does not match runtime pin')
    tool_hashes = {path.name: sha(path) for path in
                   (TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'render_host.py', Path(__file__))}
    home_source = (ui / 'ContinuumHomeView.kt').read_text()
    layer_depths = {}
    for label, member in (('foreground', 'shellForeground'), ('stage', 'cockpitStage')):
        match = re.search(r'private val ' + member + r'\b.{0,350}?translationZ\s*=\s*density\s*\*\s*([.0-9]+)f',
                          home_source, flags=re.DOTALL)
        if not match:
            raise ValueError(f'Production layer boundary changed: {member}')
        layer_depths[label] = float(match.group(1))
    with tempfile.TemporaryDirectory(prefix='continuum-cockpit-preview-') as temporary:
        work = Path(temporary)
        text = (ui / 'JarvisCockpitGridContent.kt').read_text()
        members = text[text.index('    fun wrap('):]
        fit = text[text.index('    private fun fit('):text.index('    private val CLOCK_STYLE')]
        boundary = work / 'HostTileMembers.kt'
        boundary.write_text('package at.heinz.deviceai.ui\n'
                            'import at.heinz.deviceai.ui.JarvisShellLayout.Box\n'
                            'import at.heinz.deviceai.ui.JarvisDesign.Colors\n'
                            'import at.heinz.deviceai.ui.JarvisDesign.Type\n'
                            'object JarvisCockpitGridContent {\n' + fit + members)
        stop = next(line for line in (ui / 'JarvisCockpitPanels.kt').read_text().splitlines()
                    if 'const val STOP_TARGET =' in line)
        constants = work / 'HostStopConstant.kt'
        constants.write_text('package at.heinz.deviceai.ui\nobject JarvisCockpitPanels {\n' + stop + '\n}\n')
        cp = [str(stdlib[0]), str(args.json_jar.resolve())]
        if args.android_jar:
            cp.append(str(args.android_jar.resolve()))
        jar = work / 'preview.jar'
        command = ['java', '-Xmx768m', '-cp', str(libs / '*'),
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-nowarn', '-cp', os.pathsep.join(cp), '-d', str(jar)]
        command += [str(path) for path in sources + [boundary, constants, TOOL_ROOT / 'WorkstationHostExport.kt']]
        run(command, work / 'compile.log')
        run(['java', '-Xmx512m', '-Djava.awt.headless=true', '-cp', os.pathsep.join([str(jar)] + cp),
             'at.heinz.deviceai.ui.WorkstationHostExport', str(root), str(work),
             'video' if args.video else 'stills'], work / 'export.log')
        run(['blender', '-b', '-t', '2', '--python', str(TOOL_ROOT / 'render_host.py'), '--',
             '--repo-root', str(root), '--work-dir', str(work), '--samples', str(args.samples),
             '--video-samples', str(args.video_samples or args.samples)], work / 'blender.log')
        exported = json.loads((work / 'scene-export.json').read_text())
        compose(work, out, exported, layer_depths)
        if args.video:
            rate = f'24000/{exported["motionDurationMs"]}'
            run(['ffmpeg', '-v', 'error', '-y', '-framerate', rate, '-i', str(work / 'fold-sequence-%03d.png'),
                 '-vf', 'pad=ceil(iw/2)*2:ceil(ih/2)*2',
                 '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '19', str(out / 'fold-monitor-motion.mp4')], work / 'video.log')
        for file in ('scene-export.json', 'fold-layout.json', 'phone-layout.json', 'blender-manifest.json'):
            shutil.copyfile(work / file, out / file)
    after = {str(path.relative_to(root)): sha(path) for path in tracked_sources}
    tools_after = {path.name: sha(path) for path in
                   (TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'render_host.py', Path(__file__))}
    stable = before == after and tool_hashes == tools_after and source_commit == git(root, 'rev-parse', 'HEAD')
    if not stable:
        raise RuntimeError('Source changed during rendering; discard output')
    manifest = {
        'schema': 'cockpit-host-preview-v1', 'sourceCommit': source_commit,
        'sourceFilesSha256': before, 'toolFilesSha256': tool_hashes,
        'evidenceClass': 'HOST_COMPOSITE', 'sourceStableDuringRun': stable,
        'productionLayerDepthDp': layer_depths,
        'sourceWorkingTree': git(root, 'status', '--porcelain', '--untracked-files=no'),
        'components': {'cockpit': 'Exact packaged GLB, production Kotlin pose and projection',
                       'avatar': 'Exact original GLB, neutral rest pose, production framing and alpha mask',
                       'sphere': 'Existing ContinuumHologramRenderer, head aura, and energy flows through Java2D',
                       'chrome': 'Existing JarvisShellRenderer, synthetic neutral labels',
                       'monitorContent': 'Empty; no Android app pixels or live connection'},
        'motion': 'Production Flight.sample at fixed intervals, forward then reverse from rest; no extra easing',
        'limits': ['Blender studio is not Filament light equivalence', 'No Android layout lifecycle exercised',
                   'No speech, model, capture, or real device measurements'],
        'Filament': 'NOT_RUN', 'Fold7': 'NOT_RUN',
        'outputs': {path.name: sha(path) for path in sorted(out.iterdir()) if path.is_file()},
    }
    (out / 'preview-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'status': 'HOST_RENDERED', 'frames': 6, 'video': args.video,
                      'sourceCommit': source_commit, 'sourceStable': stable}))


if __name__ == '__main__':
    main()
