"""Compile changed views against existing classes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('root', 'gradle-cache', 'kotlin-lib-dir', 'android-jar', 'baseline-classes', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    root, cache, baseline, out = args.root.resolve(), args.gradle_cache.resolve(), args.baseline_classes.resolve(), args.output.resolve()
    libs = args.kotlin_lib_dir.resolve()
    out.mkdir(parents=True, exist_ok=True)
    modules = cache / 'modules-2/files-2.1'
    def module(pattern):
        found = list(modules.glob(pattern))
        if len(found) != 1:
            raise RuntimeError('Expected one cached dependency: ' + pattern)
        return found[0]
    compiler = [module('org.jetbrains.kotlin/' + name + '/2.4.0/*/*.jar') for name in
        ('kotlin-compiler-embeddable', 'kotlin-build-tools-api', 'kotlin-stdlib', 'kotlin-script-runtime', 'kotlin-daemon-embeddable')]
    compiler += [module('org.jetbrains.kotlin/kotlin-reflect/1.6.10/*/*.jar'),
        module('org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.0/*/*.jar'), libs / 'annotations-24.0.1.jar']
    cp = [baseline, args.android_jar.resolve(), module('org.jetbrains.kotlin/kotlin-stdlib/2.4.0/*/*.jar'),
        libs / 'junit-4.13.2.jar', libs / 'hamcrest-core-1.3.jar']
    cp += sorted((cache / '8.11.1/transforms').glob('*/transformed/*/jars/classes.jar'))
    cp += sorted(modules.glob('org.robolectric/*/4.14.1/*/*.jar'))
    cp += [module('androidx.annotation/annotation-jvm/1.9.1/*/*.jar'),
        module('org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.11.0/*/*.jar')]
    sources = [root / 'app/src/main/java/at/heinz/deviceai' / name for name in
        ('avatar3d/CockpitStageFrame.kt', 'avatar3d/FilamentCockpitStage.kt', 'ui/CockpitMonitorMotion.kt',
         'ui/CinematicWorkstationLayout.kt', 'ui/ContinuumHomeView.kt', 'ui/VirtualComputerMonitorView.kt')]
    sources += [root / 'app/src/uiTest/java/at/heinz/deviceai/ui' / (name + '.kt') for name in
        ('CockpitMonitorMotionUiTest', 'CockpitMonitorLifecycleUiTest')]
    source_hashes = {str(path.relative_to(root)): digest(path) for path in sources}
    def classes_digest():
        state = hashlib.sha256()
        for path in sorted(baseline.rglob('*')):
            if path.is_file():
                state.update(str(path.relative_to(baseline)).encode())
                state.update(bytes.fromhex(digest(path)))
        return state.hexdigest()
    baseline_before = classes_digest()
    with tempfile.TemporaryDirectory(prefix='astra55-android-api-') as temporary:
        command = ['java', '-XX:-UsePerfData', '-Xmx512m', '-cp', os.pathsep.join(map(str, compiler)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect', '-jvm-target', '17',
            '-Xfriend-paths=' + str(baseline), '-classpath', os.pathsep.join(map(str, cp)),
            '-d', str(Path(temporary) / 'api.jar')] + list(map(str, sources))
        result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / 'compile.txt').write_text(result.stdout.rstrip() + '\n' if result.stdout else '')
    receipt = {'compile_exit': result.returncode, 'compiler': 'Kotlin 2.4.0 / JVM17 / max512MiB',
        'source_head': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
        'source_sha256': source_hashes,
        'compiler_sha256': {path.name: digest(path) for path in compiler},
        'scope': 'Changed Android classes and imported UI tests; real Android and dependencies; existing compiled app classes',
        'baseline_classes_sha256': baseline_before,
        'baseline_build_provenance': 'Existing local app classes; not rebuilt or certified for current branch HEAD',
        'baseline_classes_stable': baseline_before == classes_digest(),
        'source_stable': source_hashes == {str(path.relative_to(root)): digest(path) for path in sources},
        'full_application_build': 'NOT_RUN', 'android_ui': 'NOT_RUN', 'FilamentGPU': 'NOT_RUN', 'Fold7': 'NOT_RUN'}
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({key: value for key, value in receipt.items() if key not in ('source_sha256', 'compiler_sha256')}))
    if result.returncode:
        print(result.stdout[-5000:])
    if result.returncode or not receipt['source_stable'] or not receipt['baseline_classes_stable']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
