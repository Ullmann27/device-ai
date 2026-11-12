"""Replay actual stage statements; no renderer."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time

BASE = '5f87bdc610944b93d3173a83c5beeea315abeb66'
STAGE = 'app/src/main/java/at/heinz/deviceai/avatar3d/FilamentCockpitStage.kt'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument('--baseline', default=BASE)
    for name in ('kotlin-lib-dir', 'android-jar', 'filament-jar', 'json-jar', 'tmp-dir', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    root, out, libs = args.root.resolve(), args.output.resolve(), args.kotlin_lib_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    args.tmp_dir.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, TMPDIR=str(args.tmp_dir.resolve()))
    raw_logs = args.tmp_dir.resolve() / 'raw-logs' / str(time.time_ns())
    raw_logs.mkdir(parents=True)
    def git(*arguments):
        return subprocess.check_output(['git', '-C', str(root), *arguments])
    baseline = git('show', args.baseline + ':' + STAGE).decode()
    current = (root / STAGE).read_text()
    def block(source):
        start = source.index('        require(frame.valid())', source.index('    fun update('))
        return source[start:source.index('        pixels?.update(', start)]
    before_block, after_block = block(baseline), block(current)
    if after_block.replace('transformUpdates.setTransform', 'engine.transformManager.setTransform') != before_block:
        raise RuntimeError('Unexpected change inside production transform calculations')
    field = next(line for line in current.splitlines() if 'private val transformUpdates =' in line)
    main = root / 'app/src/main/java/at/heinz/deviceai'
    ui, avatar = main / 'ui', main / 'avatar3d'
    preview = ast.parse((root / 'tools/cockpit-workstation/preview.py').read_text())
    names = next(node.value.func.value.value.split() for node in preview.body
        if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == 'UI_SOURCES' for target in node.targets))
    sources = [ui / (name + '.kt') for name in names]
    sources += [main / 'motion' / (name + '.kt') for name in
        ('DampedSpring', 'Easings', 'MotionPresets', 'MotionContracts', 'Staggers', 'FaceMotion')]
    sources += [main / 'cockpit' / (name + '.kt') for name in ('CockpitAnimationEvent', 'CockpitContracts', 'CockpitTimeline')]
    sources += [main / 'avatar' / (name + '.kt') for name in ('AvatarContract', 'AvatarSentenceAccentPolicy')]
    sources += [avatar / (name + '.kt') for name in ('AvatarGlbContract', 'AvatarMorphMapper', 'AvatarIdleMotion',
        'GazeBlinkPolicy', 'CriticalDampedSpring', 'AvatarFraming', 'CockpitStageFrame', 'CockpitWorldTransforms',
        'CockpitOrbMotion', 'CockpitTransformUpdates')]
    sources += [main / 'ContinuumWorkAreaNavigationSpec.kt']
    tests = ['avatar3d.CockpitTransformUpdatesTest', 'avatar3d.CockpitWorldTransformsTest',
        'avatar3d.CockpitStageFrameTest', 'avatar3d.CockpitOrbMotionTest', 'ui.CockpitMonitorMotionTest',
        'ui.CinematicWorkstationLayoutTest']
    sources += [root / 'app/src/test/java/at/heinz/deviceai' / (name.replace('.', '/') + '.kt') for name in tests]
    sources += [root / 'app/src/test/java/at/heinz/deviceai/avatar3d/GlbOrbGeometry.kt',
        root / 'app/src/test/java/at/heinz/deviceai/avatar3d/GlbSkinningRig.kt',
        Path(__file__).with_name('CockpitTransformReplay.kt')]
    assets = [root / 'app/src/main/assets' / name for name in
        ('cinema3d/cockpit-workstation.glb', 'cinema3d/continuum-orb.glb', 'cinema3d/cockpit-world.json',
         'cinema3d/live-monitor.filamat', 'avatar3d/continuum-avatar.glb')]
    protected = [main / name for name in ('ContinuumHologramView.kt', 'avatar3d/FilamentAvatarRenderer.kt',
        'avatar3d/CockpitLiveTexture.kt', 'avatar3d/CockpitSceneAssets.kt', 'ui/ContinuumHomeView.kt')]
    tracked = sources + assets + protected + [root / STAGE, Path(__file__), ui / 'JarvisCockpitGridContent.kt',
        ui / 'JarvisCockpitPanels.kt', root / 'tools/cockpit-workstation/source/original-orb-geometry.json',
        root / 'tools/cockpit-workstation/preview.py']
    hashes = {str(path.relative_to(root)): digest(path) for path in tracked}
    for path in assets + protected + [avatar / 'CockpitWorldTransforms.kt', avatar / 'CockpitStageFrame.kt',
            avatar / 'CockpitOrbMotion.kt', ui / 'CinematicWorkstationLayout.kt', ui / 'CockpitMonitorMotion.kt']:
        if path.read_bytes() != git('show', args.baseline + ':' + str(path.relative_to(root))):
            raise RuntimeError('Baseline protected input changed: ' + str(path.relative_to(root)))
    cp = [libs / 'kotlin-stdlib-2.0.20.jar', libs / 'annotations-24.0.1.jar', libs / 'junit-4.13.2.jar',
        libs / 'hamcrest-core-1.3.jar', args.json_jar.resolve(), args.android_jar.resolve(), args.filament_jar.resolve()]
    receipt = {'baseline': args.baseline, 'source_head': git('rev-parse', 'HEAD').decode().strip(),
        'source_sha256': hashes, 'dependencies': [{'file': path.name, 'sha256': digest(path)} for path in cp],
        'compiled_sources': [str(path.relative_to(root)) for path in sources],
        'transform_calculations_unchanged': True, 'protected_inputs_unchanged': True,
        'host_boundary': 'Source-extracted update block; RectF value holder and recording TransformManager; GLB TRS evaluated on host',
        'native_gpu': 'NOT_RUN', 'full_android_class_compile': 'DEFERRED_TO_COMBINED_ROOT_CHECK', 'device': 'NOT_RUN'}
    def run(command, filename):
        result = subprocess.run(command, cwd=root, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (raw_logs / filename).write_text(result.stdout)
        public = result.stdout
        substitutions = {str(out): '${OUTPUT}', str(root): '${REPO}', str(libs): '${TOOLS}',
            str(args.tmp_dir.resolve()): '${TMPDIR}', str(args.android_jar.resolve()): '${ANDROID_JAR}',
            str(args.filament_jar.resolve()): '${FILAMENT_JAR}', str(args.json_jar.resolve()): '${JSON_JAR}'}
        for original in sorted(substitutions, key=len, reverse=True):
            public = public.replace(original, substitutions[original])
        if '/workspace/' in public:
            raise RuntimeError('Unmapped internal path in log; inspect raw scratch log')
        (out / filename).write_text(public.rstrip() + '\n' if public else '')
        return result.returncode
    with tempfile.TemporaryDirectory(prefix='compile-', dir=args.tmp_dir) as temporary:
        work = Path(temporary)
        generated = []
        for name, body, cached in (('BaselineStage', before_block, False), ('CandidateStage', after_block, True)):
            source = 'package at.heinz.deviceai.avatar3d\nclass ' + name + '(graph: ReplayGraph, manager: RecordingTransformManager, cacheTransforms: Boolean = true) {\n'
            source += '    private val engine = RecordingEngine(manager)\n'
            for prop, node in (('roomTransform', 'cockpit-room'), ('sideMonitorTransform', 'cockpit-side-monitors'),
                    ('monitorTransform', 'cockpit-monitor'), ('orbTransform', 'continuum-orb')):
                source += f'    private val {prop} = graph.id("{node}")\n'
            source += '    private val orbitTransforms = (0..4).map { graph.id("orb-orbit-$it").let { id -> id to graph.rest.getValue(id) } }\n'
            source += '    private val transform = FloatArray(16)\n    private val orbitCombined = FloatArray(16)\n'
            if cached:
                source += field + '\n'
            source += '    fun update(frame: CockpitStageFrame, orbBounds: ReplayBounds, phase: Float, motion: Boolean) {\n' + body + '    }\n}\n'
            path = work / (name + '.kt')
            path.write_text(source)
            generated.append(path)
            (out / (name + '.kt')).write_text(source)
        probe = work / 'FilamentBindingProbe.kt'
        probe.write_text('package at.heinz.deviceai.avatar3d\nimport com.google.android.filament.Engine\n'
            'class FilamentBindingProbe(engine: Engine, cacheTransforms: Boolean) {\n' + field + '\n}\n')
        generated.append(probe)
        text = (ui / 'JarvisCockpitGridContent.kt').read_text()
        fit = text[text.index('    private fun fit('):text.index('    private val CLOCK_STYLE')]
        members = text[text.index('    fun wrap('):]
        boundary = work / 'HostTileMembers.kt'
        boundary.write_text('package at.heinz.deviceai.ui\nimport at.heinz.deviceai.ui.JarvisShellLayout.Box\n'
            'import at.heinz.deviceai.ui.JarvisDesign.Colors\nimport at.heinz.deviceai.ui.JarvisDesign.Type\n'
            'object JarvisCockpitGridContent {\n' + fit + members)
        constants = work / 'HostStopConstant.kt'
        stop = next(line for line in (ui / 'JarvisCockpitPanels.kt').read_text().splitlines() if 'const val STOP_TARGET =' in line)
        constants.write_text('package at.heinz.deviceai.ui\nobject JarvisCockpitPanels {\n' + stop + '\n}\n')
        generated += [boundary, constants]
        jar = work / 'experiment.jar'
        command = ['java', '-XX:-UsePerfData', '-Xmx512m', '-Djava.io.tmpdir=' + str(work), '-cp', str(libs / '*'),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
            '-classpath', os.pathsep.join(map(str, cp)), '-d', str(jar)] + list(map(str, sources + generated))
        receipt['compile_exit'] = run(command, 'compile.txt')
        receipt['junit_exit'] = receipt['replay_exit'] = 'NOT_RUN'
        if receipt['compile_exit'] == 0:
            java = ['java', '-XX:-UsePerfData', '-Xmx384m', '-Djava.io.tmpdir=' + str(work), '-cp', os.pathsep.join(map(str, [jar] + cp))]
            receipt['junit_exit'] = run(java + ['org.junit.runner.JUnitCore'] + ['at.heinz.deviceai.' + test for test in tests], 'junit.txt')
            receipt['replay_exit'] = run(java + ['at.heinz.deviceai.avatar3d.CockpitTransformReplay', str(out / 'replay.json')], 'replay.txt')
        receipt['generated_sources'] = {path.name: digest(path) for path in generated}
    receipt['source_stable'] = hashes == {str(path.relative_to(root)): digest(path) for path in tracked}
    receipt['decision'] = 'ACCEPT_CALL_BUDGET_GATE' if all(receipt[key] == 0 for key in ('compile_exit', 'junit_exit', 'replay_exit')) and receipt['source_stable'] else 'REJECT_OR_INCOMPLETE'
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({key: value for key, value in receipt.items() if key not in ('source_sha256', 'compiled_sources', 'dependencies', 'generated_sources')}))
    if receipt['decision'] != 'ACCEPT_CALL_BUDGET_GATE':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
