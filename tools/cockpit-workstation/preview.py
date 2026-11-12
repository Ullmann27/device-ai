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

from compose_host import compose


TOOL_ROOT = Path(__file__).resolve().parent
UI_SOURCES = '''ContinuumPainter ContinuumFonts ContinuumTheme JarvisDesign JarvisNeon
JarvisMotion JarvisShellLayout JarvisShellRenderer JarvisTiles ContinuumIcons JarvisReferenceHud
JarvisVisualDepth JarvisDeckClearance JarvisCinemaEntrance HollywoodFuiConstruct JarvisRenderQuality
JarvisWindowProfile JarvisCockpitGrid JarvisRailIcons JarvisTileEnergy LuxuryHoloButtonRenderer
PerimeterWingChoreographer JarvisSceneFills ContinuumHologramRenderer ContinuumHologramPlexus
ContinuumOrbitalRing JarvisFluidLight CinematicWorkstationLayout CockpitGlassButtons JarvisHeadAura JarvisEnergyFlows
CockpitMonitorMotion AvatarPresentation'''.split()


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
    parser.add_argument('--draft', action='store_true')
    parser.add_argument('--content', choices=('empty', 'example'), default='empty')
    args = parser.parse_args()
    root, out = args.repo_root.resolve(), args.output.resolve()
    if args.draft and args.video:
        raise ValueError('Draft renders two stills only')
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
    sources += [path for path in (ui / 'CockpitWorkstationChrome.kt',) if path.is_file()]
    sources += [main / 'motion' / f'{name}.kt' for name in
                ('DampedSpring', 'Easings', 'MotionPresets', 'MotionContracts', 'Staggers', 'FaceMotion')]
    sources += [main / 'cockpit' / f'{name}.kt' for name in
                ('CockpitAnimationEvent', 'CockpitContracts', 'CockpitTimeline')]
    sources += [main / 'avatar' / f'{name}.kt' for name in ('AvatarContract', 'AvatarSentenceAccentPolicy')]
    sources += [main / 'avatar3d' / f'{name}.kt' for name in
                ('AvatarGlbContract', 'AvatarMorphMapper', 'AvatarIdleMotion', 'GazeBlinkPolicy',
                 'CriticalDampedSpring', 'AvatarFraming', 'CockpitStageFrame')]
    sources += [root / 'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt']
    assets = [root / 'app/src/main/assets/cinema3d/cockpit-workstation.glb',
              root / 'app/src/main/assets/avatar3d/continuum-avatar.glb']
    tracked_sources = sources + assets + [ui / 'JarvisCockpitGridContent.kt', ui / 'JarvisCockpitPanels.kt',
                                         ui / 'ContinuumHomeView.kt',
                                         main / 'avatar3d/FilamentCockpitStage.kt',
                                         main / 'avatar3d/FilamentAvatarRenderer.kt',
                                         main / 'ContinuumHologramView.kt', ui / 'CockpitMonitorPainter.kt']
    before = {str(path.relative_to(root)): sha(path) for path in tracked_sources}
    stage_source = (main / 'avatar3d/FilamentCockpitStage.kt').read_text()
    pin = re.search(r'const val ASSET_SHA256\s*=\s*"([0-9a-f]{64})"', stage_source)
    if not pin or pin.group(1) != sha(assets[0]):
        raise ValueError('Cockpit asset does not match runtime pin')
    tool_hashes = {path.name: sha(path) for path in
                   (TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'HostExampleContent.kt',
                    TOOL_ROOT / 'render_host.py', TOOL_ROOT / 'compose_host.py', Path(__file__))}
    home_source = (ui / 'ContinuumHomeView.kt').read_text()
    layer_depths = {}
    for label, member in (('foreground', 'shellForeground'), ('stage', 'cockpitStage'),
                          ('monitor', 'virtualMonitor')):
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
        hologram = (main / 'ContinuumHologramView.kt').read_text()
        initial = re.search(r'private var phase\s*=\s*([.0-9]+f)', hologram)
        advance = re.search(r'if \(renderBudget\(\).animate\) (phase = [^\n]+)', hologram)
        if not initial or not advance:
            raise ValueError('Production orb clock boundary changed')
        clock = work / 'HostOrbClock.kt'
        clock.write_text('package at.heinz.deviceai.ui\nobject HostOrbClock {\n'
                         'fun sample(delta: Float, energy: Float): Float {\n'
                         'var phase = ' + initial.group(1) + '\n' + advance.group(1) + '\nreturn phase\n}\n}\n')
        cp = [str(stdlib[0]), str(args.json_jar.resolve())]
        if args.android_jar:
            cp.append(str(args.android_jar.resolve()))
        jar = work / 'preview.jar'
        command = ['java', '-Xmx768m', '-cp', str(libs / '*'),
                   'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                   '-jvm-target', '17', '-nowarn', '-cp', os.pathsep.join(cp), '-d', str(jar)]
        command += [str(path) for path in sources + [boundary, constants, clock,
                     TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'HostExampleContent.kt']]
        run(command, work / 'compile.log')
        run(['java', '-Xmx512m', '-Djava.awt.headless=true', '-cp', os.pathsep.join([str(jar)] + cp),
             'at.heinz.deviceai.ui.WorkstationHostExport', str(root), str(work),
             'video' if args.video else 'draft' if args.draft else 'stills', args.content], work / 'export.log')
        run(['blender', '-b', '-t', '2', '--python', str(TOOL_ROOT / 'render_host.py'), '--',
             '--repo-root', str(root), '--work-dir', str(work), '--samples', str(args.samples),
             '--video-samples', str(args.video_samples or args.samples)], work / 'blender.log')
        exported = json.loads((work / 'scene-export.json').read_text())
        compose(work, out, exported, layer_depths)
        if args.video:
            rate = str(exported['videoFramesPerSecond'])
            run(['ffmpeg', '-v', 'error', '-y', '-framerate', rate, '-i', str(work / 'fold-sequence-%03d.png'),
                 '-vf', 'pad=ceil(iw/2)*2:ceil(ih/2)*2',
                 '-c:v', 'libx264', '-threads', '2', '-pix_fmt', 'yuv420p', '-crf', '19',
                 str(out / 'fold-monitor-motion.mp4')], work / 'video.log')
        for file in ('scene-export.json', 'fold-layout.json', 'phone-layout.json', 'blender-manifest.json'):
            shutil.copyfile(work / file, out / file)
    after = {str(path.relative_to(root)): sha(path) for path in tracked_sources}
    tools_after = {path.name: sha(path) for path in
                   (TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'HostExampleContent.kt',
                    TOOL_ROOT / 'render_host.py', TOOL_ROOT / 'compose_host.py', Path(__file__))}
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
                       'monitorContent': 'Host-only document example; opaque, no tint, no app capture' if args.content == 'example'
                                         else 'Empty; no Android app pixels or live connection'},
        'contentMode': args.content,
        'motion': 'Production Flight.sample at fixed intervals, with rest holds; no extra easing',
        'orbClock': {'initial': initial.group(1), 'productionAdvance': advance.group(1),
                     'mode': 'READY', 'audio': 0, 'sampledDurationMs': exported['videoDurationMs']},
        'limits': ['Blender studio is not Filament light equivalence', 'No Android layout lifecycle exercised',
                   'No speech, model, capture, or real device measurements'],
        'Filament': 'NOT_RUN', 'Fold7': 'NOT_RUN',
        'outputs': {path.name: sha(path) for path in sorted(out.iterdir()) if path.is_file()},
    }
    (out / 'preview-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'status': 'HOST_RENDERED', 'frames': 2 if args.draft else 6, 'video': args.video,
                      'sourceCommit': source_commit, 'sourceStable': stable}))


if __name__ == '__main__':
    main()
