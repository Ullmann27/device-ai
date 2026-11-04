"""Local QA launcher using this task's SDK/cache and the inherited proxy/CA."""
from __future__ import annotations

import fcntl
import json
import os
import pathlib
import subprocess
import sys
import urllib.parse

root = pathlib.Path(__file__).resolve().parent
checkout = pathlib.Path(os.environ['CONTINUUM_BUILD_ROOT']).resolve()
log_name = os.environ.get('CONTINUUM_BUILD_LOG', 'gradle.txt')
if pathlib.Path(log_name).name != log_name:
    raise SystemExit('CONTINUUM_BUILD_LOG must be a file name, not a path.')
if not sys.argv[1:]:
    raise SystemExit('Pass explicit Gradle tasks and test filters.')

env = dict(os.environ)
env.update(JAVA_HOME=str(root / 'jdk17'), ANDROID_HOME=str(root / 'android-sdk'),
           ANDROID_SDK_ROOT=str(root / 'android-sdk'),
           ANDROID_USER_HOME=str(root / 'android-user'),
           GRADLE_USER_HOME=str(root / 'gradle-cache'))
env['PATH'] = os.pathsep.join([str(root / 'jdk17/bin'),
                             str(root / 'android-sdk/build-tools/35.0.0'),
                             env.get('PATH', '')])
args = [str(root / 'gradle-8.11.1/bin/gradle'), '--no-daemon', '--max-workers=1',
        '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process']
for scheme in ('http', 'https'):
    proxy = urllib.parse.urlparse(env.get(scheme.upper() + '_PROXY', ''))
    if proxy.hostname:
        args += ['-D' + scheme + '.proxyHost=' + proxy.hostname,
                 '-D' + scheme + '.proxyPort=' + str(proxy.port or 8080)]
args += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts',
         '-Djavax.net.ssl.trustStorePassword=changeit', *sys.argv[1:]]

(root / 'logs').mkdir(exist_ok=True)
with (root / 'gradle-build.lock').open('a') as lock:
    # This coordinates this helper's tasks only. It does not replace another team's lock.
    fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
    source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=checkout,
                                     text=True).strip()
    tree = subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=checkout,
                                   text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=checkout,
                                    text=True).strip()
    with (root / 'logs' / log_name).open('x') as log:
        header = json.dumps({'checkout': str(checkout), 'source_commit': source,
                             'source_tree': tree, 'worktree_clean': not dirty,
                             'requested': sys.argv[1:]}) + '\n'
        log.write(header)
        log.flush()
        print(header, end='', flush=True)
        with subprocess.Popen(args, cwd=checkout, env=env, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True) as process:
            assert process.stdout is not None
            for line in process.stdout:
                log.write(line)
                log.flush()
                print(line, end='', flush=True)
            code = process.wait()
        result = '\nEXIT_CODE=' + str(code) + '\n'
        log.write(result)
        print(result, end='', flush=True)
sys.exit(code)
