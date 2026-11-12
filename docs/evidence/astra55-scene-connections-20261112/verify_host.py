"""Compile production geometry; no rendering."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'kotlin-lib-dir', 'android-jar', 'filament-jar', 'gltfio-jar', 'json-jar', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    root, out, libs = args.root.resolve(), args.output.resolve(), args.kotlin_lib_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    main = root / 'app/src/main/java/at/heinz/deviceai'
    ui, avatar = main / 'ui', main / 'avatar3d'
    tool = root / 'tools/cockpit-workstation'
    spec = importlib.util.spec_from_file_location('cockpit_preview', tool / 'preview.py')
    preview = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(preview)
    sources = [ui / (name + '.kt') for name in preview.UI_SOURCES]
    sources += [main / 'motion' / (name + '.kt') for name in
                ('DampedSpring', 'Easings', 'MotionPresets', 'MotionContracts', 'Staggers', 'FaceMotion')]
    sources += [main / 'cockpit' / (name + '.kt') for name in
                ('CockpitAnimationEvent', 'CockpitContracts', 'CockpitTimeline')]
    sources += [main / 'avatar/AvatarContract.kt', main / 'ContinuumWorkAreaNavigationSpec.kt']
    sources += [avatar / (name + '.kt') for name in
                ('AvatarGlbContract', 'AvatarMorphMapper', 'AvatarIdleMotion', 'GazeBlinkPolicy',
                 'CriticalDampedSpring', 'AvatarFraming', 'CockpitStageFrame', 'FilamentCockpitStage')]
    sources += [root / 'tools/continuum-jvm-preview/src/at/heinz/deviceai/ui/Java2DContinuumPainter.kt',
                tool / 'WorkstationHostExport.kt']
    tests = ['avatar3d.CockpitStageFrameTest', 'ui.CockpitMonitorMotionTest',
             'ui.CinematicWorkstationLayoutTest', 'ui.CinematicWorkstationVisibilityTest']
    sources += [root / 'app/src/test/java/at/heinz/deviceai' / (name.replace('.', '/') + '.kt') for name in tests]
    original_assets = [root / 'app/src/main/assets/cinema3d/cockpit-workstation.glb',
                       root / 'app/src/main/assets/avatar3d/continuum-avatar.glb']
    cp = [libs / 'kotlin-stdlib-2.0.20.jar', libs / 'annotations-24.0.1.jar',
          libs / 'junit-4.13.2.jar', libs / 'hamcrest-core-1.3.jar',
          args.json_jar.resolve(), args.android_jar.resolve(), args.filament_jar.resolve(), args.gltfio_jar.resolve()]
    tracked = sources + original_assets + [tool / 'build_scene.py', tool / 'preview.py', tool / 'render_host.py',
        ui / 'JarvisCockpitGridContent.kt', ui / 'JarvisCockpitPanels.kt', ui / 'ContinuumHomeView.kt',
        ui / 'VirtualComputerMonitorView.kt', main / 'ContinuumHologramView.kt']
    before = {str(path.relative_to(root)): digest(path) for path in tracked}
    receipt = {'source_head': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
        'source_sha256': before,
        'compiled_sources': [str(path.relative_to(root)) for path in sources],
        'dependencies': [{'file': path.name, 'sha256': digest(path)} for path in cp],
        'source_boundary': 'Production classes; two existing preview boundary extracts; no app lifecycle execution',
        'compiler': 'Kotlin 2.0.20 / JVM17 / max512MiB', 'FilamentGPU': 'NOT_RUN', 'Fold7': 'NOT_RUN',
        'android_ui': 'NOT_RUN', 'render': 'NOT_RUN'}
    def run(command, filename):
        completed = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (out / filename).write_text(completed.stdout.rstrip() + '\n' if completed.stdout else '')
        return completed.returncode
    with tempfile.TemporaryDirectory(prefix='astra55-scene-check-') as temporary:
        work = Path(temporary)
        text = (ui / 'JarvisCockpitGridContent.kt').read_text()
        fit = text[text.index('    private fun fit('):text.index('    private val CLOCK_STYLE')]
        members = text[text.index('    fun wrap('):]
        boundary = work / 'HostTileMembers.kt'
        boundary.write_text('package at.heinz.deviceai.ui\nimport at.heinz.deviceai.ui.JarvisShellLayout.Box\n'
            'import at.heinz.deviceai.ui.JarvisDesign.Colors\nimport at.heinz.deviceai.ui.JarvisDesign.Type\n'
            'object JarvisCockpitGridContent {\n' + fit + members)
        stop = next(line for line in (ui / 'JarvisCockpitPanels.kt').read_text().splitlines() if 'const val STOP_TARGET =' in line)
        constants = work / 'HostStopConstant.kt'
        constants.write_text('package at.heinz.deviceai.ui\nobject JarvisCockpitPanels {\n' + stop + '\n}\n')
        jar = work / 'checks.jar'
        command = ['java', '-XX:-UsePerfData', '-Xmx512m', '-cp', str(libs / '*'),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
            '-classpath', os.pathsep.join(map(str, cp)), '-d', str(jar)] + list(map(str, sources + [boundary, constants]))
        receipt['compile_exit'] = run(command, 'compile.txt')
        receipt['test_exit'] = run(['java', '-XX:-UsePerfData', '-Xmx256m', '-cp',
            os.pathsep.join(map(str, [jar] + cp)), 'org.junit.runner.JUnitCore'] +
            ['at.heinz.deviceai.' + name for name in tests], 'junit.txt') if receipt['compile_exit'] == 0 else 'NOT_RUN'
        generator = work / 'tools/cockpit-workstation/build_scene.py'
        generator.parent.mkdir(parents=True)
        generated = work / 'app/src/main/assets/cinema3d/cockpit-workstation.glb'
        generated.parent.mkdir(parents=True)
        shutil.copyfile(tool / 'build_scene.py', generator)
        generated_hashes = []
        for index in range(2):
            if run(['python3', str(generator)], f'asset-reproduction-{index + 1}.txt'):
                raise RuntimeError('Existing generator failed')
            generated_hashes.append(digest(generated))
        pin = re.search(r'const val ASSET_SHA256 = "([0-9a-f]{64})"', (avatar / 'FilamentCockpitStage.kt').read_text()).group(1)
        receipt['asset_reproduced_twice'] = generated_hashes == [digest(original_assets[0])] * 2
        receipt['runtime_pin_matches'] = pin == digest(original_assets[0])
    receipt['source_stable'] = before == {str(path.relative_to(root)): digest(path) for path in tracked}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({key: value for key, value in receipt.items() if key not in ('source_sha256', 'compiled_sources', 'dependencies')}))
    if receipt['compile_exit'] or receipt['test_exit'] or not all(receipt[key] for key in
            ('source_stable', 'asset_reproduced_twice', 'runtime_pin_matches')):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
