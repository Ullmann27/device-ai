"""Run only named unit-test classes offline on an exact clean local source."""
from __future__ import annotations

import argparse
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--checkout', required=True, type=Path)
    parser.add_argument('--toolchain', required=True, type=Path)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--test-class', action='append', required=True)
    parser.add_argument('--log-name', required=True)
    args = parser.parse_args()
    checkout, toolchain = args.checkout.resolve(), args.toolchain.resolve()
    if Path(args.log_name).name != args.log_name:
        raise ValueError('Log name must be a basename.')
    if shutil.disk_usage(toolchain).free < 1024 ** 3:
        raise RuntimeError('Less than 1 GiB free; build not started.')

    def git(*parts: str) -> str:
        return subprocess.check_output(['git', *parts], cwd=checkout, text=True).strip()

    source = git('rev-parse', 'HEAD')
    if source != args.source_commit or git('status', '--porcelain'):
        raise RuntimeError('Build requires the exact requested clean checkout.')
    snapshot = json.loads((toolchain / 'snapshot-receipt.json').read_text())
    if not snapshot['donorManifestStable'] or not snapshot['destinationManifestMatches']:
        raise RuntimeError('Cache snapshot was not validated.')
    env = dict(os.environ)
    for key in ('CONTINUUM_LOCAL_SIGNING_FILE', 'CONTINUUM_LOCAL_SIGNING_PASSWORD',
                'CONTINUUM_LOCAL_SIGNING_P12_BASE64', 'CONTINUUM_MENTOR_SIGNING_FILE',
                'CONTINUUM_MENTOR_SIGNING_PASSWORD', 'CONTINUUM_MENTOR_SIGNING_P12_BASE64'):
        env.pop(key, None)
    env.update(JAVA_HOME=str(toolchain / 'jdk17'),
               ANDROID_HOME=str(toolchain / 'android-sdk'),
               ANDROID_SDK_ROOT=str(toolchain / 'android-sdk'),
               ANDROID_USER_HOME=str(toolchain / 'android-user'),
               GRADLE_USER_HOME=str(toolchain / 'gradle-cache'),
               TMPDIR=str(toolchain / 'tmp'))
    env['PATH'] = str(toolchain / 'jdk17/bin') + os.pathsep + env.get('PATH', '')
    command = [str(toolchain / 'gradle-8.11.1/bin/gradle'), '--offline', '--no-daemon',
               '--max-workers=1', '--console=plain', '--stacktrace',
               '-Dorg.gradle.jvmargs=-Xmx1g -Djava.io.tmpdir=' + str(toolchain / 'tmp'),
               '-Pkotlin.compiler.execution.strategy=in-process',
               '-Pandroid.builder.sdkDownload=false', '-PcontinuumUiWidgetTests=true',
               '--init-script', str(toolchain / 'bounded-tests.init.gradle'),
               ':app:testDebugUnitTest']
    for test_class in args.test_class:
        command.extend(['--tests', test_class])
    header = {'sourceCommit': source, 'sourceTree': git('rev-parse', 'HEAD^{tree}'),
              'checkout': str(checkout), 'cleanBeforeRun': True,
              'testClasses': args.test_class, 'command': command,
              'freeBeforeBytes': shutil.disk_usage(toolchain).free,
              'signingEnvironmentRemoved': True, 'realNetworkDownloadsPermitted': False}
    with (toolchain / 'gradle-build.lock').open('a') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        with (toolchain / 'logs' / args.log_name).open('x') as log:
            def emit(text: str) -> None:
                log.write(text)
                log.flush()
                print(text, end='', flush=True)

            emit(json.dumps(header) + '\n')
            with subprocess.Popen(command, cwd=checkout, env=env, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True) as process:
                assert process.stdout is not None
                for line in process.stdout:
                    emit(line)
                code = process.wait()
            emit('\nEXIT_CODE=' + str(code) + '\n')
            emit('FREE_AFTER_BYTES=' + str(shutil.disk_usage(toolchain).free) + '\n')
    return code


if __name__ == '__main__':
    sys.exit(main())
