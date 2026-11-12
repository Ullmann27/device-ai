#!/usr/bin/env python3
"""Run frozen workflow review."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

FILES = [
    'app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/CockpitWorkEvent.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/LiveActionCockpitAdapter.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/OwnerComputerIntentParser.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/OwnerComputerWorkflow.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/LiveComputerActions.kt',
    'app/src/main/java/at/heinz/deviceai/cognitive/VerifiedNodeClick.kt',
    'app/src/main/java/at/heinz/deviceai/ToolExecutionReceiptStore.kt',
    'app/src/main/java/at/heinz/deviceai/AgentProtocol.kt',
    'app/src/main/java/at/heinz/deviceai/ToolRegistry.kt',
    'app/src/test/java/at/heinz/deviceai/cognitive/LiveComputerActionsTest.kt',
]
DECLARATION_SOURCES = [
    'app/src/main/java/at/heinz/deviceai/cockpit/CockpitContracts.kt',
    'app/src/main/java/at/heinz/deviceai/CommandInterpreter.kt',
    'app/src/main/java/at/heinz/deviceai/AgentToolLayer.kt',
]

def digest(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--compiler-lib', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--patch', type=Path)
    args = parser.parse_args()
    repo, lib, out = args.repo.resolve(), args.compiler_lib.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    def git(*command):
        return subprocess.check_output(['git', *command], cwd=repo)
    commit = git('rev-parse', args.commit + '^{commit}').decode().strip()
    head_before = git('rev-parse', 'HEAD').decode().strip()
    test_file = Path(__file__).resolve().with_name('WorkflowReviewTest.kt')
    cp = ':'.join(str(lib / name) for name in [
        'kotlin-stdlib-2.0.20.jar', 'junit-4.13.2.jar', 'hamcrest-core-1.3.jar'])
    for item in cp.split(':'):
        if not Path(item).is_file():
            raise SystemExit('Missing dependency: ' + item)
    manifest = []
    with tempfile.TemporaryDirectory(prefix='astra55-workflow-review-') as temp:
        frozen = Path(temp)
        for name in FILES + DECLARATION_SOURCES:
            data = git('show', commit + ':' + name)
            target = frozen / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            manifest.append({'path': name, 'frozen_sha256': digest(data)})
        contracts = (frozen / DECLARATION_SOURCES[0]).read_text()
        patterns = [r'data class CalcStep[^\n]*', r'enum class ChartKind[^\n]*',
                    r'enum class TransitionPattern[^\n]*', r'enum class AlarmKind \{[^}]*\}']
        declarations = [re.search(pattern, contracts).group(0) for pattern in patterns]
        cockpit_types = frozen / 'CockpitTypes.kt'
        cockpit_types.write_text('package at.heinz.deviceai.cockpit\n\n' + '\n\n'.join(declarations) + '\n')
        risk = re.search(r'enum class Risk \{[^}]*\}',
                         (frozen / DECLARATION_SOURCES[1]).read_text()).group(0)
        status = re.search(r'enum class ToolExecutionStatus[^\n]*',
                           (frozen / DECLARATION_SOURCES[2]).read_text()).group(0)
        tool_types = frozen / 'ToolTypes.kt'
        tool_types.write_text('package at.heinz.deviceai\n\n' + risk + '\n\n' + status + '\n')
        if args.patch:
            patch = args.patch.resolve()
            subprocess.run(['git', 'apply', '--check', str(patch)], cwd=frozen, check=True)
            subprocess.run(['git', 'apply', str(patch)], cwd=frozen, check=True)
        test_copy = frozen / test_file.name
        test_copy.write_bytes(test_file.read_bytes())
        sources = [str(frozen / name) for name in FILES]
        sources.extend([str(cockpit_types), str(tool_types), str(test_copy)])
        command = ['java', '-XX:ActiveProcessorCount=1', '-XX:-UsePerfData', '-Xmx512m',
                   '-cp', str(lib / '*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                   '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', cp,
                   '-d', str(frozen / 'classes'), *sources]
        compiled = subprocess.run(command, capture_output=True, text=True)
        (out / 'compile.log').write_text(compiled.stdout + compiled.stderr)
        results = {'compile_exit': compiled.returncode}
        print('COMPILE_EXIT', compiled.returncode)
        if compiled.returncode == 0:
            for name, test in [('existing', 'LiveComputerActionsTest'), ('review', 'WorkflowReviewTest')]:
                run = subprocess.run(['java', '-XX:ActiveProcessorCount=1', '-XX:-UsePerfData',
                                      '-Xmx512m', '-cp', str(frozen / 'classes') + ':' + cp,
                                      'org.junit.runner.JUnitCore', 'at.heinz.deviceai.cognitive.' + test],
                                     capture_output=True, text=True)
                (out / (name + '-junit.log')).write_text(run.stdout + run.stderr)
                results[name + '_exit'] = run.returncode
                print(name.upper() + '_EXIT', run.returncode)
                print(run.stdout + run.stderr)
        else:
            print(compiled.stdout + compiled.stderr)
        for item in manifest:
            item['compiled_sha256'] = digest((frozen / item['path']).read_bytes())
            current = repo / item['path']
            item['working_tree_after_sha256'] = digest(current.read_bytes()) if current.exists() else None
        (out / 'source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        versions = subprocess.run(['java', '-version'], capture_output=True, text=True)
        (out / 'java-version.log').write_text(versions.stdout + versions.stderr)
        metadata = {'source_commit': commit, 'head_before': head_before,
                    'head_after': git('rev-parse', 'HEAD').decode().strip(),
                    'test_sha256': digest(test_file.read_bytes()),
                    'patch_sha256': digest(args.patch.read_bytes()) if args.patch else None,
                    'compiler': 'Kotlin K2JVMCompiler 2.0.20',
                    'compiler_sha256': digest((lib / 'kotlin-compiler-embeddable-2.0.20.jar').read_bytes()),
                    'max_heap_mb': 512, 'active_processors': 1,
                    'unmodified_declarations_extracted': 6,
                    'android_gradle': 'NOT_RUN', 'fold7': 'NOT_RUN', **results}
        (out / 'result.json').write_text(json.dumps(metadata, indent=2) + '\n')
        return 1 if any(value != 0 for value in results.values()) else 0

if __name__ == '__main__':
    raise SystemExit(main())
