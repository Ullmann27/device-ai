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
CockpitMonitorMotion CockpitWorkstationChrome'''.split()


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


def packaged_assets(root):
    directory = root / 'app/src/main/assets'
    manifest = directory / 'cinema3d/cockpit-world.json'
    if manifest.stat().st_size > 16_384:
        raise ValueError('Packaged manifest exceeds runtime bound')
    data = json.loads(manifest.read_text())
    if data['version'] != 1:
        raise ValueError('Unsupported packaged asset version')
    files = [manifest, directory / 'avatar3d/continuum-avatar.glb']
    for key, expected, limit in (
            ('workstation', 'cinema3d/cockpit-workstation.glb', 2_000_000),
            ('orb', 'cinema3d/continuum-orb.glb', 2_000_000),
            ('monitorMaterial', 'cinema3d/live-monitor.filamat', 1_000_000)):
        entry = data[key]
        path = directory / expected
        if entry['path'] != expected or not 1 <= entry['bytes'] <= limit:
            raise ValueError(f'Packaged asset contract failed: {key}')
        if path.stat().st_size != entry['bytes'] or sha(path) != entry['sha256']:
            raise ValueError(f'Packaged asset pin failed: {key}')
        files.append(path)
    return files, data


def orb_clock_source(view_source):
    patterns = {
        'initial': r'private var phase = ([.0-9]+f)',
        'energy': r'State\.READY\s*->\s*([.0-9]+f)',
        'clamp': r'val delta = if \(lastFrameMs == 0L\)[^\n]+\.coerceIn\(0f,\s*([.0-9]+f)\)',
        'update': r'if \(renderBudget\(\)\.animate\) (phase = [^\n]+)',
    }
    values = {}
    for name, pattern in patterns.items():
        match = re.search(pattern, view_source)
        if not match:
            raise ValueError(f'Production orb clock boundary changed: {name}')
        values[name] = match.group(1)
    return '''package at.heinz.deviceai.ui
internal object HostOrbClock {
    const val INITIAL_PHASE = %(initial)s
    const val READY_ENERGY = %(energy)s
    const val MAX_DELTA_SECONDS = %(clamp)s
    fun sample(elapsedMs: Float, fps: Int): Float {
        require(elapsedMs.isFinite() && elapsedMs in 0f..60_000f && fps in 1..120)
        var phase = INITIAL_PHASE
        var remaining = elapsedMs
        val tick = 1_000f / fps
        val energy = READY_ENERGY
        while (remaining > 0f) {
            val step = minOf(tick, remaining)
            val delta = (step / 1_000f).coerceIn(0f, MAX_DELTA_SECONDS)
            %(update)s
            remaining = (remaining - step).coerceAtLeast(0f)
        }
        return phase
    }
}
''' % values


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
    parser.add_argument('--qa-lock', type=Path)
    parser.add_argument('--denoise', action='store_true', help='Use Cycles OpenImageDenoise; fail if unavailable')
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
    sources += [main / 'motion' / f'{name}.kt' for name in
                ('DampedSpring', 'Easings', 'MotionPresets', 'MotionContracts', 'Staggers', 'FaceMotion')]
    sources += [main / 'cockpit' / f'{name}.kt' for name in
                ('CockpitAnimationEvent', 'CockpitContracts', 'CockpitTimeline')]
    sources += [main / 'avatar' / f'{name}.kt' for name in ('AvatarContract', 'AvatarSentenceAccentPolicy')]
    sources += [main / 'avatar3d' / f'{name}.kt' for name in
                ('AvatarGlbContract', 'AvatarMorphMapper', 'AvatarIdleMotion', 'GazeBlinkPolicy',
                 'CriticalDampedSpring', 'AvatarFraming', 'AvatarMaterialVariant',
                 'CockpitStageFrame', 'CockpitWorldTransforms', 'CockpitOrbMotion')]
    sources += [root / 'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt']
    assets, asset_pins = packaged_assets(root)
    tracked_sources = sources + assets + [ui / 'JarvisCockpitGridContent.kt', ui / 'JarvisCockpitPanels.kt',
                                         ui / 'ContinuumHomeView.kt',
                                         main / 'avatar3d/FilamentCockpitStage.kt',
                                         main / 'avatar3d/FilamentAvatarRenderer.kt',
                                         main / 'avatar3d/CockpitSceneAssets.kt',
                                         main / 'ContinuumHologramView.kt',
                                         root / 'tools/cockpit-workstation/materials/live-monitor.mat']
    before = {str(path.relative_to(root)): sha(path) for path in tracked_sources}
    tool_hashes = {path.name: sha(path) for path in
                   (TOOL_ROOT / 'WorkstationHostExport.kt', TOOL_ROOT / 'HostExampleContent.kt',
                    TOOL_ROOT / 'render_host.py', TOOL_ROOT / 'compose_host.py', Path(__file__))}
    home_source = (ui / 'ContinuumHomeView.kt').read_text()
    layer_depths = {}
    for label, member in (('foreground', 'shellForeground'),):
        match = re.search(r'private val ' + member + r'\b.{0,350}?translationZ\s*=\s*density\s*\*\s*([.0-9]+)f',
                          home_source, flags=re.DOTALL)
        if not match:
            raise ValueError(f'Production layer boundary changed: {member}')
        layer_depths[label] = float(match.group(1))
    native_z = re.search(r'hologram\.translationZ\s*=\s*density\s*\*\s*([.0-9]+)f', home_source)
    layer_depths['scene'] = float(native_z.group(1)) if native_z else 0.0
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
        clock = work / 'HostOrbClock.kt'
        clock.write_text(orb_clock_source((main / 'ContinuumHologramView.kt').read_text()))
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
        render_command = ['blender', '-b', '-t', '2', '--python', str(TOOL_ROOT / 'render_host.py'), '--',
             '--repo-root', str(root), '--work-dir', str(work), '--samples', str(args.samples),
             '--video-samples', str(args.video_samples or args.samples)]
        if args.denoise:
            render_command += ['--denoise']
        if args.qa_lock:
            render_command = ['flock', '-w', '45', str(args.qa_lock.resolve())] + render_command
        run(render_command, work / 'blender.log')
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
        'schema': 'cockpit-unified-preview-v2', 'sourceCommit': source_commit,
        'sourceFilesSha256': before, 'toolFilesSha256': tool_hashes,
        'evidenceClass': 'HOST_UNIFIED_3D_COMPOSITE', 'sourceStableDuringRun': stable,
        'sharedRenderLockUsed': args.qa_lock is not None,
        'packagedAssetPins': asset_pins,
        'productionLayerDepthDp': layer_depths,
        'sourceWorkingTree': git(root, 'status', '--porcelain', '--untracked-files=no'),
        'components': {'cockpit': 'Packaged GLB, actual Kotlin world and monitor matrices',
                       'avatar': 'Original GLB through native AvatarMaterialVariant; neutral pose, actual world matrix',
                       'sphere': 'Packaged original five-ring GLB; native orb depth and Kotlin ring matrices; no 2D sphere layer',
                       'chrome': 'Existing JarvisShellRenderer and CockpitWorkstationChrome; neutral host data',
                       'monitorContent': 'Host-only document texture on actual live-pixel mesh; unlit, no tint, no app capture' if args.content == 'example'
                                         else 'Empty; no Android app pixels or live connection'},
        'contentMode': args.content,
        'motion': 'Production Flight.sample at fixed intervals, with rest holds; no extra easing',
        'orbMotion': {'source': 'CockpitWorldTransforms.ringTransform and CockpitOrbMotion.sample',
                      'timeSource': 'Actual ContinuumHologramView phase update; fixed READY energy replay',
                      'clock': exported['orbClock'], 'ringCount': exported['orbRingCount'],
                      'sampledDurationMs': exported['videoDurationMs']},
        'denoising': {'enabled': args.denoise, 'backend': 'OPENIMAGEDENOISE' if args.denoise else None,
                      'scope': 'Cycles 3D stage only; production Java2D chrome composed afterwards'},
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
