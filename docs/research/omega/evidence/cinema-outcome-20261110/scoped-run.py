import os
import sys
import json
import hashlib
import shutil
import subprocess
from pathlib import Path

root = Path('/workspace/scratch/006ff0a2565d/cinema-outcome-support')
evidence = Path('/workspace/scratch/006ff0a2565d/cinema-outcome-evidence')
name = sys.argv[1]
out = evidence / name
out.mkdir(parents=True, exist_ok=True)
snapshot = evidence / 'dependencies'
snapshot.mkdir(exist_ok=True)
source_cp = Path('/workspace/scratch/006ff0a2565d/dialog-integration-qa/runtime-classpath.txt')
cp = source_cp.read_text().strip().split(':')
runtime = next(p for p in cp if 'runtime_app_classes_jar' in p)
frozen = snapshot / 'app-classes.jar'
if not frozen.exists():
    shutil.copyfile(runtime, frozen)
assert hashlib.sha256(frozen.read_bytes()).hexdigest() == 'cd03ed3a98c63754ad6f02324c383e548ce479a56b210e77b9ca9f8bf9fc9fb2'
cp = [str(frozen) if p == runtime else p for p in cp if not any(s in p for s in ['kotlin-classes/debugUnitTest', 'javac/debugUnitTest', 'java_res/debugUnitTest'])]
config_dir = next(p for p in cp if 'unit_test_config_directory' in p)
config_out = snapshot / 'test-config'
config_file = config_out / 'com/android/tools/test_config.properties'
if not config_file.exists():
    config_file.parent.mkdir(parents=True, exist_ok=True)
    properties = (Path(config_dir)/'com/android/tools/test_config.properties').read_text().splitlines()
    rewritten = []
    for line in properties:
        key, sep, value = line.partition('=')
        if key in ['android_merged_assets', 'android_merged_manifest', 'android_resource_apk']:
            source = Path(value)
            if not source.is_absolute():
                source = Path('/workspace/scratch/006ff0a2565d/cinema-dialog-integration/app')/source
            target = snapshot / 'resources' / key / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():shutil.copytree(source, target, dirs_exist_ok=True)
            else:shutil.copyfile(source, target)
            value = str(target)
        rewritten.append(key+sep+value)
    config_file.write_text('\n'.join(rewritten)+'\n')
cp = [str(config_out) if p == config_dir else p for p in cp]
classpath = ':'.join(cp)
android = '/tmp/continuum-toolchain/android-sdk/platforms/android-36/android.jar'
sources = [
    'app/src/main/java/at/heinz/deviceai/AgentActivity.kt',
    'app/src/main/java/at/heinz/deviceai/chief/TurnLifecycle.kt',
    'app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationTriggers.kt',
    'app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt',
    'app/src/test/java/at/heinz/deviceai/cockpit/CockpitAnimationTriggersTest.kt',
    'app/src/uiTest/java/at/heinz/deviceai/LocalTurnOutcomeUiTest.kt',
]
if name != 'red':
    sources += [
        'app/src/main/java/at/heinz/deviceai/ui/ContinuumCockpitPresentation.kt',
        'app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitPanels.kt',
        'app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt',
        'app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceEffects.kt',
        'app/src/uiTest/java/at/heinz/deviceai/ui/JarvisSequenceOutcomeUiTest.kt',
        'app/src/uiTest/java/at/heinz/deviceai/ui/JarvisSequenceTurnLifecycleUiTest.kt',
        'app/src/main/java/at/heinz/deviceai/ui/JarvisWorkWindowHost.kt',
        'app/src/main/java/at/heinz/deviceai/ui/HollywoodFuiStageOverlay.kt',
    ]
manifest = {'sources': {p: hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sources},
            'appDependencySha256': hashlib.sha256(frozen.read_bytes()).hexdigest(),
            'dependencySourceCommit': 'ca839c0555c121b248b843c4030c0e928603a535',
            'mode': 'source-exact scoped Android compilation with frozen app dependencies'}
(out/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
env = os.environ.copy()
env['JAVA_HOME'] = '/tmp/continuum-toolchain/jdk'
env['JAVA_OPTS'] = '-Xmx1100m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
compile_cmd = ['/tmp/continuum-toolchain/kotlinc/bin/kotlinc', *[str(root/p) for p in sources],
               '-jvm-target', '17', '-module-name', 'DeviceAI_app_debug',
               '-Xfriend-paths='+str(frozen), '-classpath', android+':'+classpath,
               '-d', str(out/'classes')]
if '--reuse-compiled' not in sys.argv:
    with (out/'compile.log').open('w') as log:
        result = subprocess.run(compile_cmd, env=env, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    print('COMPILE_EXIT', result.returncode, flush=True)
    if result.returncode:
        print((out/'compile.log').read_text()[-16000:])
        raise SystemExit(result.returncode)
java_cmd = ['/tmp/continuum-toolchain/jdk/bin/java', '-Xmx768m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2',
            '--add-opens=java.base/java.io=ALL-UNNAMED', '-Drobolectric.offline=true',
            '-Drobolectric.dependency.dir=/workspace/scratch/5900afa6af29/device-ai/app/build/ui-test-sdk',
            '-cp', str(out/'classes')+':'+classpath, 'org.junit.runner.JUnitCore',
            'at.heinz.deviceai.cockpit.CockpitAnimationTriggersTest', 'at.heinz.deviceai.LocalTurnOutcomeUiTest',
            'at.heinz.deviceai.ui.JarvisSequenceOutcomeUiTest', 'at.heinz.deviceai.ui.JarvisSequenceTurnLifecycleUiTest']
with (out/'tests.log').open('w') as log:
    result = subprocess.run(java_cmd, env=env, cwd=root/'app', stdout=log, stderr=subprocess.STDOUT)
print('TEST_EXIT', result.returncode, flush=True)
print((out/'tests.log').read_text()[-14000:])
raise SystemExit(result.returncode)
