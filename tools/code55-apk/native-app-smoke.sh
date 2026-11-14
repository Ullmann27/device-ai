#!/usr/bin/env bash
set -Eeuo pipefail
if [[ $# -ne 2 || ! "$1" =~ ^emulator-[0-9]+$ ]]; then
  echo 'Usage: native-app-smoke.sh emulator-serial evidence-directory' >&2
  exit 2
fi
: "${ANDROID_HOME:?Android SDK required}"
python3 -I - "$@" <<'PY'
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

serial, destination = sys.argv[1:]
sdk = Path(os.environ['ANDROID_HOME'])
adb = sdk / 'platform-tools/adb'
package = 'at.heinz.deviceai.mentorpaired'
activity = package + '/at.heinz.deviceai.AgentActivity'
snapshot_runner = package + '.test/at.heinz.deviceai.ContinuumWindowSnapshotInstrumentation'

def command(args, timeout=30, check=True):
    result = subprocess.run(list(map(str, args)), capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f'Command failed ({result.returncode}): {args[0:3]}: '
                           + result.stderr.decode(errors='replace')[-1000:])
    return result.stdout

def device(*args, **options):
    return command([adb, '-s', serial, *args], **options)

def shell(*args, **options):
    return device('shell', *args, **options).decode(errors='replace').strip()

# Validate before device mutations.
assert shell('getprop', 'ro.kernel.qemu') == '1', 'Emulator required'
assert shell('getprop', 'ro.build.version.sdk') == '35', 'API 35 required'
assert shell('getprop', 'ro.product.cpu.abi') == 'x86_64', 'x86_64 required'
repo = Path(command(['git', 'rev-parse', '--show-toplevel']).decode().strip())
os.chdir(repo)
source = command(['git', 'rev-parse', 'HEAD']).decode().strip()
assert source == os.environ.get('GITHUB_SHA', source), 'Source SHA mismatch'
assert not command(['git', 'status', '--porcelain', '--untracked-files=normal']).strip(), 'Dirty source'
app = repo / 'app/build/outputs/apk/debug/app-debug.apk'
test = repo / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
aapt = sdk / 'build-tools/35.0.0/aapt'
for apk, expected in [(app, package), (test, package + '.test')]:
    badging = command([aapt, 'dump', 'badging', apk]).decode()
    match = re.search(r"^package: name='([^']+)'", badging, re.M)
    assert match and match.group(1) == expected, 'Disposable QA package required'
out = Path(destination).resolve()
assert out != repo and repo not in out.parents, 'Evidence must be outside source'
out.mkdir(parents=True, exist_ok=True)
report = {'status': 'FAILED', 'source_sha': source,
          'source_tree': command(['git', 'rev-parse', 'HEAD^{tree}']).decode().strip(),
          'package': package, 'serial': serial, 'sdk': 35, 'abi': 'x86_64',
          'apk_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (app, test)},
          'checks': [], 'limits': ['Emulator only; no Fold7 confirmation',
          'No local model inference, microphone, camera, cloud or external-app automation tested',
          'Screenshots and UI hierarchy prove startup/navigation, not visual quality']}
saved = {(scope, name): shell('settings', 'get', scope, name) for scope, name in
         [('system', 'accelerometer_rotation'), ('system', 'user_rotation'),
          ('global', 'airplane_mode_on'), ('global', 'wifi_on'), ('global', 'mobile_data')]}

def save(name, data):
    (out / name).write_bytes(data if isinstance(data, bytes) else data.encode())

def captured_device(label, *args, timeout=30):
    argv = list(map(str, [adb, '-s', serial, *args]))
    try:
        result = subprocess.run(argv, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        save(label + '-stdout.txt', error.stdout or b'')
        save(label + '-stderr.txt', error.stderr or b'')
        save(label + '-command.json', json.dumps({'args': argv, 'timeout': timeout}))
        raise
    save(label + '-stdout.txt', result.stdout)
    save(label + '-stderr.txt', result.stderr)
    save(label + '-command.json', json.dumps({'args': argv, 'returncode': result.returncode}))
    return result

def read_hierarchy(label):
    started = time.time_ns()
    before = shell('pidof', package)
    assert re.fullmatch(r'\d+(?: \d+)*', before), f'{label}: app process missing'
    result = captured_device(label + '-ui-automation', 'shell', 'am', 'instrument', '-w', '-r',
                             snapshot_runner, timeout=60)
    after = shell('pidof', package)
    assert before == after, f'{label}: snapshot changed app process'
    output = result.stdout.decode(errors='replace')
    assert result.returncode == 0 and 'INSTRUMENTATION_FAILED' not in output, f'{label}: snapshot runner failed'
    assert re.search(r'^INSTRUMENTATION_CODE: -1\r?$', output, re.M), f'{label}: snapshot instrumentation failed'
    pairs = re.findall(r'^INSTRUMENTATION_RESULT: ([a-z_][a-z0-9_]*)=(.*?)\r?$', output, re.M)
    values = dict(pairs)
    assert len(values) == len(pairs) and values.get('status') == 'ok', f'{label}: invalid snapshot result'
    assert values.get('helper_package') == package + '.test', f'{label}: wrong snapshot process package'
    assert re.fullmatch(r'\d+', values.get('helper_pid', '')), f'{label}: helper process missing'
    assert values['helper_pid'] not in before.split(), f'{label}: helper instrumented app process'
    windows = json.loads(values['windows'])
    focused = [w for w in windows if w['display'] == 0 and w['focused'] and w['type'] == 1]
    assert len(focused) == 1 and focused[0]['id'] == int(values['window_id']), f'{label}: focused window mismatch'
    windows_after = json.loads(values['windows_after'])
    focused_after = [w for w in windows_after if w['display'] == 0 and w['focused'] and w['type'] == 1]
    assert len(focused_after) == 1 and focused_after[0]['id'] == focused[0]['id'], f'{label}: focus changed during capture'
    times = [int(values[key]) for key in ('capture_started_ms', 'hierarchy_finished_ms',
                                         'screenshot_finished_ms', 'capture_finished_ms')]
    assert 0 < times[0] <= times[1] <= times[2] <= times[3], f'{label}: inconsistent capture times'
    xml = base64.b64decode(values['hierarchy_base64'], validate=True)
    assert 0 < len(xml) <= 262144, f'{label}: invalid hierarchy size'
    save(label + '.xml', xml)
    png = device('exec-out', 'run-as', package + '.test', 'cat', 'cache/continuum-window-snapshot.png')
    save(label + '.png', png)
    assert hashlib.sha256(png).hexdigest() == values['screenshot_sha'], f'{label}: screenshot hash mismatch'
    assert png.startswith(b'\x89PNG\r\n\x1a\n') and len(png) > 4096, f'{label}: invalid screenshot'
    dimensions = struct.unpack('>II', png[16:24])
    assert dimensions == (int(values['screenshot_width']), int(values['screenshot_height'])), f'{label}: screenshot size mismatch'
    values.update(host_started_unix_ns=started, host_finished_unix_ns=time.time_ns(),
                  app_pid_before=before, app_pid_after=after)
    save(label + '-window.json', json.dumps(values, ensure_ascii=False))
    hierarchy = ET.fromstring(xml)
    assert hierarchy.tag == 'hierarchy', f'{label}: UI hierarchy root missing'
    assert hierarchy.get('rotation') == values['rotation'], f'{label}: rotation metadata mismatch'
    assert len(list(hierarchy.iter('node'))) == int(values['node_count']), f'{label}: hierarchy node count mismatch'
    return hierarchy

def snapshot(label, rotation=None):
    pid = shell('pidof', package, check=False)
    assert re.fullmatch(r'\d+(?: \d+)*', pid), f'{label}: app process missing'
    activities = shell('dumpsys', 'activity', 'activities')
    save(label + '-activities.txt', activities)
    assert any(package + '/' in line and 'AgentActivity' in line for line in
               activities.splitlines() if 'ResumedActivity' in line), f'{label}: activity not resumed'
    png = device('exec-out', 'screencap', '-p')
    assert png.startswith(b'\x89PNG\r\n\x1a\n') and len(png) > 4096, f'{label}: invalid screenshot'
    save(label + '-before.png', png)
    hierarchy = read_hierarchy(label)
    png = (out / (label + '.png')).read_bytes()
    width, height = struct.unpack('>II', png[16:24])
    assert min(width, height) >= 200, f'{label}: empty display'
    assert shell('pidof', package) == pid, f'{label}: app process changed during snapshot'
    if rotation is not None:
        assert hierarchy.get('rotation') == str(rotation), f'{label}: rotation not applied'
    nodes = []
    for node in hierarchy.iter('node'):
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if node.get('package') == package and bounds:
            x1, y1, x2, y2 = map(int, bounds.groups())
            if x2 > x1 and y2 > y1:
                nodes.append(node)
    assert any(n.get('text') or n.get('content-desc') for n in nodes), f'{label}: empty app UI'
    report['checks'].append({'name': label, 'status': 'PASS', 'pid': pid,
                             'bounded_app_nodes': len(nodes), 'display': [width, height]})
    return nodes, pid, (width, height)

def launch(label):
    result = shell('am', 'start', '-W', '-n', activity, timeout=60)
    save(label + '-launch.txt', result)
    assert re.search(r'^Status: ok\s*$', result, re.M) and 'Error:' not in result, result
    time.sleep(2)
    return snapshot(label)

def tap(nodes, labels):
    for label in labels:
        candidates = [n for n in nodes if n.get('enabled') == 'true' and n.get('clickable') == 'true'
                      and label in (n.get('text'), n.get('content-desc'))]
        if candidates:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', candidates[0].get('bounds')))
            shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(1)
            return True
    return False

failure = None
try:
    save('android-fingerprint.txt', shell('getprop', 'ro.build.fingerprint'))
    save('install-app.txt', device('install', '-r', app, timeout=90))
    save('install-test.txt', device('install', '-t', '-r', test, timeout=90))
    instrumentation = shell('pm', 'list', 'instrumentation')
    save('instrumentation-targets.txt', instrumentation)
    expected = 'instrumentation:' + snapshot_runner + ' (target=' + package + '.test)'
    assert instrumentation.splitlines().count(expected) == 1, 'Snapshot runner must target its test package'
    shell('am', 'force-stop', package)
    cleared = shell('pm', 'clear', package)
    save('clear-empty-data.txt', cleared)
    assert cleared == 'Success', 'QA app data not cleared'
    device('logcat', '-b', 'all', '-c')
    for permission in ['CAMERA', 'RECORD_AUDIO', 'POST_NOTIFICATIONS']:
        shell('pm', 'revoke', package, 'android.permission.' + permission, check=False)
    permissions = shell('dumpsys', 'package', package)
    save('permissions-before.txt', permissions)
    for permission in ['CAMERA', 'RECORD_AUDIO', 'POST_NOTIFICATIONS']:
        assert re.search(r'^\s*android\.permission\.' + permission + r': granted=false\b',
                         permissions, re.M), f'{permission}: permission unexpectedly granted'
    shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
    shell('settings', 'put', 'system', 'user_rotation', '0')
    nodes, first_pid, portrait = launch('fresh-empty-no-permissions')
    if tap(nodes, ['SCHLIESSEN']):
        nodes, _, _ = snapshot('setup-dismissed')
    if tap(nodes, ['NEIN']):
        nodes, _, _ = snapshot('camera-declined')
    conversation = ['Konversation öffnen', 'JARVIS: Konversation öffnen', 'Gespräch öffnen', 'Gespräch', 'Chat']
    assert tap(nodes, conversation), 'Conversation control missing'
    nodes, _, _ = snapshot('conversation')
    assert any(n.get('class') == 'android.widget.EditText' for n in nodes), 'Composer missing'
    keyboard = shell('dumpsys', 'input_method')
    save('conversation-input-method.txt', keyboard)
    if re.search(r'(?:mInputShown|mIsInputViewShown)=true\b', keyboard):
        shell('input', 'keyevent', 'KEYCODE_BACK')
    nodes, _, _ = snapshot('conversation-keyboard-dismissed')
    assert tap(nodes, ['Zur Startseite mit allen KI-Bereichen']), 'START control missing'
    nodes, _, _ = snapshot('start-roundtrip')
    assert any(n.get('content-desc') in conversation or n.get('text') in conversation for n in nodes), 'Home not restored'
    shell('settings', 'put', 'system', 'user_rotation', '1')
    time.sleep(2)
    _, rotated_pid, landscape = snapshot('rotation', rotation=1)
    assert portrait[0] == landscape[1] and portrait[1] == landscape[0], 'Display did not rotate'
    assert rotated_pid == first_pid, 'Process died during rotation'
    shell('am', 'force-stop', package)
    assert not shell('pidof', package, check=False), 'Force-stop failed'
    _, restarted_pid, _ = launch('process-restart')
    assert restarted_pid != first_pid, 'New process not observed'
    shell('cmd', 'connectivity', 'airplane-mode', 'enable')
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    deadline = time.monotonic() + 20
    while True:
        network = shell('dumpsys', 'connectivity')
        save('offline-connectivity.txt', network)
        if re.search(r'(?:Active default network:|mActiveDefaultNetwork=)\s*(?:none|null)(?:\s|$)', network, re.I):
            break
        assert time.monotonic() < deadline, 'No-network state not established'
        time.sleep(1)
    shell('am', 'force-stop', package)
    launch('offline-restart')
    assert command(['git', 'rev-parse', 'HEAD']).decode().strip() == source
    assert not command(['git', 'status', '--porcelain', '--untracked-files=normal']).strip()
except BaseException as error:
    failure = error
    report['error'] = str(error)
finally:
    for filename, args in [('logcat.txt', ['logcat', '-d', '-b', 'main', '-b', 'system', '-v', 'threadtime']),
                           ('crash-logcat.txt', ['logcat', '-d', '-b', 'crash', '-v', 'threadtime'])]:
        try:
            data = device(*args)
            save(filename, data)
            text = data.decode(errors='replace')
            if filename == 'crash-logcat.txt' and package in text:
                raise RuntimeError('QA app crash in crash buffer')
            if re.search(r'ANR in ' + re.escape(package) + r'\b', text):
                raise RuntimeError('QA app ANR')
        except BaseException as error:
            failure = failure or error
            report.setdefault('error', str(error))
    for (scope, name), value in saved.items():
        try:
            if name in ['airplane_mode_on', 'wifi_on', 'mobile_data']:
                args = {'airplane_mode_on': ['cmd', 'connectivity', 'airplane-mode'],
                        'wifi_on': ['svc', 'wifi'], 'mobile_data': ['svc', 'data']}[name]
                shell(*args, 'enable' if value == '1' else 'disable')
            else:
                shell('settings', 'delete', scope, name) if value == 'null' else shell('settings', 'put', scope, name, value)
        except BaseException as error:
            report.setdefault('cleanup_errors', []).append(str(error))
            failure = failure or error
            report.setdefault('error', str(error))
    report['status'] = 'FAILED' if failure else 'PASS'
    save('RESULT.json', json.dumps(report, ensure_ascii=False, indent=2) + '\n')
if failure:
    raise failure
print('NATIVE_APP_SMOKE_PASS ' + source)
PY
