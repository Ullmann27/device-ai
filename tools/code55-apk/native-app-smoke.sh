#!/usr/bin/env bash
set -Eeuo pipefail
if [[ $# -lt 2 || $# -gt 3 || ! "$1" =~ ^emulator-[0-9]+$ || ( $# -eq 3 && "$3" != routines ) ]]; then
  echo 'Usage: native-app-smoke.sh emulator-serial evidence-directory [routines]' >&2
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

serial, destination = sys.argv[1:3]
routine_mode = sys.argv[3:] == ['routines']
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
if routine_mode:
    report['mode'] = 'routines'
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

def read_hierarchy(label, await_avatar_diagnostic=False, await_text=None):
    started = time.time_ns()
    before = shell('pidof', package)
    assert re.fullmatch(r'\d+(?: \d+)*', before), f'{label}: app process missing'
    options = ['-e', 'await_avatar_diagnostic', 'true'] if await_avatar_diagnostic else []
    if await_text is not None:
        options += ['-e', 'await_text_base64', base64.b64encode(await_text.encode()).decode()]
    result = captured_device(label + '-ui-automation', 'shell', 'am', 'instrument', '-w', '-r', *options,
                             snapshot_runner, timeout=60)
    after = shell('pidof', package)
    assert before == after, f'{label}: snapshot changed app process'
    output = result.stdout.decode(errors='replace')
    assert result.returncode == 0 and 'INSTRUMENTATION_FAILED' not in output, f'{label}: snapshot runner failed'
    assert re.search(r'^INSTRUMENTATION_CODE: -1\r?$', output, re.M), f'{label}: snapshot instrumentation failed'
    pairs = re.findall(r'^INSTRUMENTATION_RESULT: ([a-z_][a-z0-9_]*)=(.*?)\r?$', output, re.M)
    values = dict(pairs)
    assert len(values) == len(pairs) and values.get('status') == 'ok', f'{label}: invalid snapshot result'
    assert 0 < int(values['elapsed_ms']) < 45000, f'{label}: snapshot deadline exceeded'
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
    if await_text is not None:
        assert any(n.get('package') == package and n.get('text') == await_text
                   for n in hierarchy.iter('node')), f'{label}: expected UI text missing'
    return hierarchy

def snapshot(label, rotation=None, record=True, await_avatar_diagnostic=False, await_text=None):
    if routine_mode:
        report['capture_count'] = report.get('capture_count', 0) + 1
        assert report['capture_count'] <= 28, 'Routine capture budget exceeded'
    pid = shell('pidof', package, check=False)
    assert re.fullmatch(r'\d+(?: \d+)*', pid), f'{label}: app process missing'
    activities = shell('dumpsys', 'activity', 'activities')
    save(label + '-activities.txt', activities)
    assert any(package + '/' in line and 'AgentActivity' in line for line in
               activities.splitlines() if 'ResumedActivity' in line), f'{label}: activity not resumed'
    png = device('exec-out', 'screencap', '-p')
    assert png.startswith(b'\x89PNG\r\n\x1a\n') and len(png) > 4096, f'{label}: invalid screenshot'
    save(label + '-before.png', png)
    hierarchy = read_hierarchy(label, await_avatar_diagnostic=await_avatar_diagnostic, await_text=await_text)
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
    if record:
        report['checks'].append({'name': label, 'status': 'PASS', 'pid': pid,
                                 'bounded_app_nodes': len(nodes), 'display': [width, height]})
    return nodes, pid, (width, height)

def launch(label):
    result = shell('am', 'start', '-W', '-n', activity, timeout=60)
    save(label + '-launch.txt', result)
    assert re.search(r'^Status: ok\s*$', result, re.M) and 'Error:' not in result, result
    time.sleep(2)
    return snapshot(label)

def visible_bounds(node, display):
    match = re.fullmatch(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]', node.get('bounds', ''))
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    if 0 <= x1 < x2 <= display[0] and 0 <= y1 < y2 <= display[1]:
        return x1, y1, x2, y2
    return None

def control(nodes, label, display=None):
    return next((n for n in nodes if n.get('enabled') == 'true' and n.get('clickable') == 'true'
                 and label in (n.get('text'), n.get('content-desc'))
                 and (display is None or visible_bounds(n, display))), None)

def tap_node(node, display):
    bounds = visible_bounds(node, display)
    assert bounds, 'Tap control outside viewport'
    x1, y1, x2, y2 = bounds
    shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def tap(nodes, labels, display=None):
    for label in labels:
        candidate = control(nodes, label, display)
        if candidate is not None:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', candidate.get('bounds')))
            shell('input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(1)
            return True
    return False

def establish_offline(label):
    shell('cmd', 'connectivity', 'airplane-mode', 'enable')
    shell('svc', 'wifi', 'disable')
    shell('svc', 'data', 'disable')
    deadline = time.monotonic() + 20
    while True:
        network = shell('dumpsys', 'connectivity')
        save(label + '-connectivity.txt', network)
        if re.search(r'(?:Active default network:|mActiveDefaultNetwork=)\s*(?:none|null)(?:\s|$)', network, re.I):
            return
        assert time.monotonic() < deadline, 'No-network state not established'
        time.sleep(1)

def keyboard_visible(dump):
    # API 35 visibility state.
    states = re.findall(r'^\s*mDecorViewVisible=(true|false)\b', dump, re.M)
    assert len(states) == 1, 'Actual IME visibility missing or ambiguous'
    return states[0] == 'true'

def collect_failure_threads():
    state = {'status': 'UNAVAILABLE', 'source_sha': source, 'serial': serial,
             'started_unix_ns': time.time_ns(), 'primary_error': report.get('error')}
    original_uid = None
    root_attempted = False

    def read(label, *args, timeout=15):
        result = captured_device('failure-threads-' + label, *args, timeout=timeout)
        assert result.returncode == 0, f'{label}: command failed ({result.returncode})'
        return result.stdout.decode(errors='replace').strip()

    def identity(label):
        for prop, value in [('ro.kernel.qemu', '1'), ('ro.build.version.sdk', '35'),
                            ('ro.product.cpu.abi', 'x86_64')]:
            assert read(label + '-' + prop, 'shell', 'getprop', prop) == value, 'Disposable emulator changed'

    try:
        identity('before')
        original_uid = read('uid-before', 'shell', 'id', '-u')
        state['adb_uid_before'] = original_uid
        assert original_uid in ('0', '2000'), 'Unexpected adb UID'
        pid = read('pid-before', 'shell', 'pidof', package)
        assert re.fullmatch(r'[1-9]\d*', pid), 'Unique QA app process missing'
        state['app_pid'] = pid
        if original_uid == '2000':
            root_attempted = True
            read('root', 'root')
            read('root-wait', 'wait-for-device')
        assert read('uid-root', 'shell', 'id', '-u') == '0', 'Root stack diagnosis unavailable'
        identity('root')
        assert read('pid-root', 'shell', 'pidof', package) == pid, 'QA app process changed'
        cmdline = read('cmdline', 'shell', 'cat', '/proc/' + pid + '/cmdline').rstrip('\x00')
        assert cmdline == package, 'QA app process identity mismatch'
        state['dumps'] = {}
        for label, flag in [('java', '-j'), ('native', '-b')]:
            try:
                assert read(label + '-pid', 'shell', 'pidof', package) == pid, 'QA app process changed'
                output = read(label, 'shell', 'debuggerd', flag, pid, timeout=20)
                assert output, 'Empty thread dump'
                state['dumps'][label] = 'CAPTURED'
            except BaseException as error:
                state['dumps'][label] = 'UNAVAILABLE'
                state.setdefault('errors', []).append(str(error))
        state['status'] = 'CAPTURED' if all(v == 'CAPTURED' for v in state['dumps'].values()) else 'INCOMPLETE'
    except BaseException as error:
        state.setdefault('errors', []).append(str(error))
    finally:
        restore = [('unroot', ('unroot',)), ('unroot-wait', ('wait-for-device',))] if root_attempted else []
        if original_uid in ('0', '2000'):
            restore.append(('uid-after', ('shell', 'id', '-u')))
        for label, args in restore:
            try:
                value = read(label, *args)
                if label == 'uid-after':
                    state['adb_uid_after'] = value
                    assert value == original_uid, 'Original adb UID not restored'
            except BaseException as error:
                state.setdefault('errors', []).append(str(error))
                report.setdefault('cleanup_errors', []).append(str(error))
                state['status'] = 'INCOMPLETE'
        state['finished_unix_ns'] = time.time_ns()
        save('failure-threads.json', json.dumps(state, ensure_ascii=False, indent=2) + '\n')

def capability_response(nodes):
    texts = [re.sub(r'\s+', ' ', n.get('text', '')).strip() for n in nodes]
    prefix = 'Continuum · Aktuelle App-Fähigkeiten Mein aktueller Stand auf diesem Gerät:'
    replies = [text for text in texts if text.startswith(prefix)]
    assert len(replies) == 1, 'Actual local capability answer missing'
    reply = replies[0]
    assert 'Lokales Sprachmodell: keines geladen.' in reply, 'Unexpected model state'
    assert 'Diese Statusantwort startet kein Modell, keine Recherche, kein Mikrofon und keinen Entwicklungsauftrag.' in reply
    editors = [n for n in nodes if n.get('class') == 'android.widget.EditText']
    assert len(editors) == 1 and editors[0].get('text', '') in ('', 'Schreibe eine Nachricht …'), 'Submitted input not cleared'
    return reply

def collect_avatar_diagnostic(nodes, pid, display):
    buttons = [n for n in nodes if n.get('enabled') == 'true' and n.get('clickable') == 'true'
               and n.get('content-desc', '').startswith('Systemstatus: Gedächtnis:')
               and visible_bounds(n, display)]
    assert len(buttons) == 1, 'Visible system-status control missing'
    tap_node(buttons[0], display)
    nodes, current, _ = snapshot('avatar-system-opened', rotation=1, record=False)
    assert current == pid, 'Avatar diagnostic changed process'
    gestures = []
    label = 'HOLOGRAMM · LIVE-FRAMES PRÜFEN'
    for attempt in range(7):
        if control(nodes, label, display) is not None:
            break
        assert attempt < 6, 'Avatar diagnostic action unreachable after scrolling'
        scrolls = [n for n in nodes if n.get('class') == 'android.widget.ScrollView'
                   and n.get('scrollable') == 'true' and visible_bounds(n, display)
                   and not n.get('content-desc', '').startswith('Continuum-Cockpit, vertikal scrollbar')]
        assert len(scrolls) == 1, 'Unique system-status scroll container missing'
        x1, y1, x2, y2 = visible_bounds(scrolls[0], display)
        x = (x1+x2)//2
        start_y, end_y = y2-(y2-y1)//6, y1+(y2-y1)//6
        result = captured_device(f'avatar-system-scroll-{attempt+1}-input', 'shell', 'input', 'swipe',
                                 str(x), str(start_y), str(x), str(end_y), '700')
        assert result.returncode == 0, 'System-status swipe failed'
        gestures.append([x, start_y, x, end_y, 700])
        nodes, current, _ = snapshot(f'avatar-system-scroll-{attempt+1}', rotation=1, record=False)
        assert current == pid, 'Avatar diagnostic changed process'
    assert tap(nodes, [label], display), 'Avatar diagnostic action missing'
    nodes, current, _ = snapshot('avatar-diagnostic', rotation=1, record=False, await_avatar_diagnostic=True)
    assert current == pid, 'Avatar diagnostic changed process'
    texts = [n.get('text', '') for n in nodes if n.get('text')]
    assert 'Continuum · Hologramm-Gerätediagnose' in texts, 'Avatar diagnostic error dialog'
    text = '\n'.join(texts)
    assert all(marker in text for marker in ['Animations-Callbacks:', 'Wirkliche Canvas-Zeichnungen:',
                                            'Phasenänderung:', 'Start:', 'Ende:']), 'Avatar diagnostic incomplete'
    report['avatar_diagnostic'] = {'status': 'OBSERVED', 'text': text,
        'stage_evidence': 'rotation-top.png', 'dialog_evidence': 'avatar-diagnostic.xml',
        'scroll_gestures': gestures, 'limit': 'Read-only report; no visible-avatar or performance certification'}
    assert tap(nodes, ['Schließen', 'SCHLIESSEN'], display), 'Avatar diagnostic close control missing'
    nodes, current, _ = snapshot('avatar-return', rotation=1, record=False)
    assert current == pid, 'Avatar diagnostic changed process'
    return nodes

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
        nodes, _, _ = snapshot('setup-dismissed', await_text='Darf ich dich beim Gespräch anschauen?')
    if tap(nodes, ['NEIN']):
        nodes, _, _ = snapshot('camera-declined')
    if routine_mode:
        import importlib.util
        sys.dont_write_bytecode = True
        spec = importlib.util.spec_from_file_location('native_routines', repo / 'tools/code55-apk/native_routines.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.run(globals(), nodes, first_pid, portrait)
    else:
        conversation = ['Konversation öffnen', 'JARVIS: Konversation öffnen', 'Gespräch öffnen', 'Gespräch', 'Chat']
        assert tap(nodes, conversation), 'Conversation control missing'
        establish_offline('conversation')
        nodes, _, display = snapshot('conversation-opened', record=False)
        editors = [n for n in nodes if n.get('class') == 'android.widget.EditText']
        assert len(editors) == 1, 'Composer missing'
        assert editors[0].get('focused') == 'true', 'Composer not focused'
        assert editors[0].get('text', '') in ('', 'Schreibe eine Nachricht …'), 'Composer not empty'
        question = 'Was kannst du?'
        shell('input', 'text', "'Was%skannst%sdu?'")
        keyboard = shell('dumpsys', 'input_method')
        save('conversation-typing-input-method.txt', keyboard)
        if keyboard_visible(keyboard):
            shell('input', 'keyevent', 'KEYCODE_BACK')
        nodes, _, display = snapshot('conversation-input', record=False)
        editors = [n for n in nodes if n.get('class') == 'android.widget.EditText']
        assert len(editors) == 1 and editors[0].get('text') == question, 'Typed question missing'
        assert tap(nodes, ['Nachricht senden'], display), 'Send control missing'
        nodes, _, _ = snapshot('conversation')
        reply = capability_response(nodes)
        report['conversation_submission'] = {'question': question, 'response_text': reply,
            'input_evidence': 'conversation-input.xml', 'response_evidence': 'conversation.xml',
            'offline_evidence': 'conversation-connectivity.txt'}
        keyboard = shell('dumpsys', 'input_method')
        save('conversation-input-method.txt', keyboard)
        if keyboard_visible(keyboard):
            shell('input', 'keyevent', 'KEYCODE_BACK')
        nodes, _, _ = snapshot('conversation-keyboard-dismissed')
        keyboard = shell('dumpsys', 'input_method')
        save('conversation-dismissed-input-method.txt', keyboard)
        assert not keyboard_visible(keyboard), 'IME window still visible'
        assert tap(nodes, ['Zur Startseite mit allen KI-Bereichen']), 'START control missing'
        nodes, _, _ = snapshot('start-roundtrip')
        assert any(n.get('content-desc') in conversation or n.get('text') in conversation for n in nodes), 'Home not restored'
        shell('settings', 'put', 'system', 'user_rotation', '1')
        time.sleep(2)
        nodes, rotated_pid, landscape = snapshot('rotation-top', rotation=1, record=False)
        assert portrait[0] == landscape[1] and portrait[1] == landscape[0], 'Display did not rotate'
        assert rotated_pid == first_pid, 'Process died during rotation'
        nodes = collect_avatar_diagnostic(nodes, first_pid, landscape)
        navigation = ['Start', 'Gespräch', 'Gedächtnis', 'Forschung', 'Aufgaben', 'Internet', 'Lernen', 'Geräte']
        gestures = []
        for attempt in range(4):
            if control(nodes, 'Konversation öffnen', landscape) is not None and all(
                    control(nodes, label, landscape) is not None for label in navigation):
                break
            assert attempt < 3, 'Landscape input/navigation unreachable after scrolling'
            scrolls = [n for n in nodes if n.get('class') == 'android.widget.ScrollView'
                       and n.get('scrollable') == 'true' and visible_bounds(n, landscape)
                       and n.get('content-desc', '').startswith('Continuum-Cockpit, vertikal scrollbar')]
            assert len(scrolls) == 1, 'Landscape scroll container missing'
            x1, y1, x2, y2 = visible_bounds(scrolls[0], landscape)
            x = (x1+x2)//2
            start_y, end_y = y2-(y2-y1)//6, y1+(y2-y1)//6
            result = captured_device(f'rotation-scroll-{attempt+1}-input', 'shell', 'input', 'swipe',
                                     str(x), str(start_y), str(x), str(end_y), '700')
            assert result.returncode == 0, 'Landscape swipe failed'
            gestures.append([x, start_y, x, end_y, 700])
            nodes, pid, _ = snapshot(f'rotation-scroll-{attempt+1}', rotation=1, record=False)
            assert pid == first_pid, 'Process changed during scrolling'
        assert gestures, 'Expected real landscape scroll was not exercised'
        nodes, pid, _ = snapshot('rotation', rotation=1)
        assert pid == first_pid, 'Process changed during landscape navigation'
        controls = {label: control(nodes, label, landscape) for label in ['Konversation öffnen', *navigation]}
        assert all(n is not None for n in controls.values()), 'Landscape controls not visible'
        report['landscape_navigation'] = {'scroll_gestures': gestures,
            'visible_controls': {label: list(visible_bounds(n, landscape)) for label, n in controls.items()},
            'controls_evidence': 'rotation.xml', 'conversation_evidence': 'landscape-conversation.xml'}
        assert tap(nodes, ['Konversation öffnen'], landscape), 'Landscape input not reachable'
        nodes, pid, _ = snapshot('landscape-conversation', rotation=1, record=False)
        assert pid == first_pid and any(n.get('class') == 'android.widget.EditText'
                                       and visible_bounds(n, landscape) for n in nodes), 'Landscape composer missing'
        assert tap(nodes, ['Zur Startseite mit allen KI-Bereichen'], landscape), 'Landscape START not reachable'
        nodes, pid, _ = snapshot('landscape-start-roundtrip', rotation=1, record=False)
        assert pid == first_pid and any(n.get('class') == 'android.widget.ScrollView'
            and n.get('content-desc', '').startswith('Continuum-Cockpit, vertikal scrollbar')
            and visible_bounds(n, landscape) for n in nodes), 'Landscape home not restored'
        shell('am', 'force-stop', package)
        assert not shell('pidof', package, check=False), 'Force-stop failed'
        _, restarted_pid, _ = launch('process-restart')
        assert restarted_pid != first_pid, 'New process not observed'
        establish_offline('offline')
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
    if failure:
        try:
            collect_failure_threads()
        except BaseException as error:
            report['thread_diagnostic_error'] = str(error)
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
print(('NATIVE_ROUTINES_PASS ' if routine_mode else 'NATIVE_APP_SMOKE_PASS ') + source)
PY
