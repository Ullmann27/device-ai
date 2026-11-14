"""Inspect actual routine UI evidence."""
import hashlib
import base64
import json
from pathlib import Path
import re
import shlex
import struct
import time
import xml.etree.ElementTree as ET

PACKAGE = 'at.heinz.deviceai.mentorpaired'
UUID = r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}'
STATE_PATH = 'files/routines/owner-routines-v1.bin'
RECEIPT_PATH = 'files/tool-receipts/tool-execution-receipts.bin'
ACTIVE_PATH = 'files/chat-history/active-conversation'
STATE_MAGIC = b'CONTINUUM-ROUTINES-1\n'


def mutf(value):
    units = value.encode('utf-16-be')
    encoded = bytearray()
    for offset in range(0, len(units), 2):
        code = int.from_bytes(units[offset:offset + 2], 'big')
        if 0 < code < 128:
            encoded.append(code)
        elif code < 2048:
            encoded.extend((0xc0 | code >> 6, 0x80 | code & 63))
        else:
            encoded.extend((0xe0 | code >> 12, 0x80 | code >> 6 & 63, 0x80 | code & 63))
    assert len(encoded) <= 65535
    return struct.pack('>H', len(encoded)) + encoded


class Reader:
    def __init__(self, payload):
        self.payload, self.position = payload, 0

    def take(self, count):
        assert 0 <= count <= len(self.payload) - self.position, 'Truncated store'
        result = self.payload[self.position:self.position + count]
        self.position += count
        return result

    def number(self, code):
        return struct.unpack('>' + code, self.take(struct.calcsize('>' + code)))[0]

    def count(self, limit):
        count = self.number('i')
        assert 0 <= count <= limit, 'Store count outside bound'
        return count

    def boolean(self):
        value = self.number('B')
        assert value in (0, 1), 'Invalid boolean'
        return bool(value)

    def string(self):
        return self.take(self.count(128 * 1024)).decode('utf-8')

    def utf(self):
        payload = self.take(self.number('H'))
        value = payload.replace(b'\xc0\x80', b'\x00').decode('utf-8', 'surrogatepass')
        value = value.encode('utf-16-be', 'surrogatepass').decode('utf-16-be')
        assert mutf(value)[2:] == payload, 'Noncanonical modified UTF-8'
        return value

    def optional(self):
        return self.string() if self.boolean() else None

    def end(self):
        assert self.position == len(self.payload), 'Trailing store bytes'


def read_report(reader):
    report = dict(zip(('routine_id', 'run_id', 'digest', 'result'), (reader.string() for _ in range(4))))
    report['at'] = reader.number('q')
    report['message'] = None
    if reader.boolean():
        message = dict(zip(('id', 'from', 'to'), (reader.string() for _ in range(3))))
        message.update(priority=reader.number('i'), body=reader.string())
        assert message['priority'] in (0, 1) and message['to'] == 'chief'
        assert message['from'] == ('routine:' + report['routine_id'])[:64]
        assert message['id'] == f"routine-{report['routine_id']}-{report['at']}-{report['run_id'].replace('-', '')}"
        report['message'] = message
    report['append'] = None
    if reader.boolean():
        report['append'] = {'conversation': reader.string(), 'offset': reader.number('q'), 'text': reader.string()}
    report['delivered'] = reader.boolean()
    assert re.fullmatch(UUID, report['run_id']) and report['at'] >= 0
    return report


def decode_state(payload):
    if payload is None:
        return {'revision': 0, 'entries': [], 'outbox': []}
    assert len(STATE_MAGIC) + 32 <= len(payload) <= 8 * 1024 * 1024 + len(STATE_MAGIC) + 32
    assert payload.startswith(STATE_MAGIC), 'Routine schema mismatch'
    digest = payload[len(STATE_MAGIC):len(STATE_MAGIC) + 32]
    data = payload[len(STATE_MAGIC) + 32:]
    assert hashlib.sha256(data).digest() == digest, 'Routine checksum mismatch'
    reader = Reader(data)
    state = {'revision': reader.number('q'), 'entries': [], 'outbox': []}
    assert state['revision'] >= 0
    for _ in range(reader.count(32)):
        row = {'id': reader.string(), 'goal': reader.string()}
        trigger = reader.number('B')
        assert trigger in (1, 2)
        row['trigger'] = ({'kind': 'periodic', 'period': reader.number('q'), 'first': reader.number('q')}
                          if trigger == 1 else {'kind': 'event', 'name': reader.string()})
        row['tools'] = [reader.string() for _ in range(reader.count(3))]
        assert row['tools'] and len(set(row['tools'])) == len(row['tools'])
        row['approved_at'], row['approval'] = reader.number('q'), reader.string()
        definition = mutf(row['id']) + mutf(row['goal']) + bytes([trigger])
        definition += (struct.pack('>qq', row['trigger']['period'], row['trigger']['first'])
                       if trigger == 1 else mutf(row['trigger']['name']))
        definition += b''.join(mutf(tool) for tool in sorted(row['tools']))
        assert hashlib.sha256(definition).hexdigest() == row['approval'], 'Approval scope mismatch'
        assert row['approved_at'] >= 0 and re.fullmatch('r-' + UUID, row['id'])
        row.update(next_at=reader.number('q'), last_digest=reader.optional(), last_result=reader.optional(),
                   last_at=reader.number('q'), run_id=reader.optional())
        row['report'] = read_report(reader) if reader.boolean() else None
        state['entries'].append(row)
    state['outbox'] = [read_report(reader) for _ in range(reader.count(64))]
    reader.end()
    assert len({row['id'] for row in state['entries']}) == len(state['entries'])
    return state


def decode_receipts(payload):
    if payload is None:
        return []
    assert 0 < len(payload) <= 256 * 1024 + 128
    outer = Reader(payload)
    assert outer.utf() == 'DEVICE_AI_TOOL_RECEIPTS' and outer.number('i') == 1
    data = outer.take(outer.count(256 * 1024))
    assert outer.take(32) == hashlib.sha256(data).digest(), 'Receipt checksum mismatch'
    outer.end()
    reader, receipts = Reader(data), []
    for _ in range(reader.count(512)):
        receipt = dict(zip(('call_id', 'tool', 'fingerprint', 'risk'), (reader.utf() for _ in range(4))))
        receipt['created'] = reader.number('q')
        arguments = {}
        for _ in range(reader.count(16)):
            key, kind = reader.utf(), reader.number('B')
            assert key not in arguments and kind in range(5)
            arguments[key] = (None if kind == 0 else reader.boolean() if kind == 1 else
                              reader.number('i') if kind == 2 else reader.number('q') if kind == 3 else reader.utf())
        receipt['arguments'] = arguments
        receipt['transitions'] = [(reader.utf(), reader.number('q')) for _ in range(reader.count(8))]
        assert receipt['transitions'] and receipt['transitions'][0] == ('PROPOSED', receipt['created'])
        assert all(a[1] <= b[1] for a, b in zip(receipt['transitions'], receipt['transitions'][1:]))
        allowed = {'PROPOSED': {'APPROVED', 'REJECTED', 'CANCELLED'},
                   'APPROVED': {'STARTED', 'START_PENDING', 'REJECTED', 'CANCELLED'},
                   'START_PENDING': {'DISPATCH_COMMITTED', 'FAILED', 'TIMED_OUT', 'CANCELLED', 'UNKNOWN_AFTER_DISPATCH'},
                   'DISPATCH_COMMITTED': {'SUCCEEDED', 'UNKNOWN_AFTER_DISPATCH'},
                   'STARTED': {'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'}}
        assert all(b[0] in allowed.get(a[0], set()) for a, b in zip(receipt['transitions'], receipt['transitions'][1:]))
        receipt['output_length'] = reader.number('i')
        receipt['output_sha256'] = reader.utf() if reader.boolean() else None
        receipts.append(receipt)
    reader.end()
    assert len({row['call_id'] for row in receipts}) == len(receipts)
    return receipts


def evidence_bytes(directory, label):
    command = json.loads((directory / (label + '-command.json')).read_text())
    payload = (directory / (label + '-stdout.txt')).read_bytes()
    assert not (directory / (label + '-stderr.txt')).read_bytes(), 'Private read emitted an error'
    if command['returncode'] == 44:
        assert not payload, 'Missing file returned data'
        return None
    assert command['returncode'] == 0, 'Private read failed'
    return payload


def stored(directory, label):
    return decode_state(evidence_bytes(directory, label + '-routines'))


def receipts(directory, label):
    return decode_receipts(evidence_bytes(directory, label + '-receipts'))


def single(state):
    assert len(state['entries']) == 1, 'Expected one authorized routine'
    return state['entries'][0]


def registered_job_uid(job):
    service = PACKAGE + '/at.heinz.deviceai.routines.OwnerRoutineJobService'
    sections = re.split(r'(?m)^\s*JOB #', job)
    matches = [section for section in sections if re.match(r'[^\n]*/620159:', section) and 'Service: ' + service in section]
    assert len(matches) == 1 and 'PERSISTED' in matches[0]
    assert re.search(r'PERIODIC: interval=\+15m0s0ms\b', matches[0]), 'Wrong Android job interval'
    identity = re.match(r'(u0a[0-9]+)/620159:', matches[0])
    assert identity, 'Android job UID missing'
    return identity.group(1)


def verify_job_absent(code, stdout, stderr, uid):
    assert type(code) is int and code == 23, 'Android job absence not observed'
    assert stdout == f'unknown({uid}/jid620159)\n'.encode(), 'Stopped Android job identity differs'
    assert stderr == f'Could not find job 620159 in package {PACKAGE} / user 0\n'.encode(), 'Stopped Android job still exists'


def command_evidence(directory, label, serial, arguments):
    command = json.loads((directory / (label + '-command.json')).read_text())
    args = command['args']
    assert len(args) == len(arguments) + 3 and Path(args[0]).name == 'adb'
    assert args[1:] == ['-s', serial, *arguments], 'Unexpected scheduler observation command'
    assert type(command['returncode']) is int and command['returncode'] == 0
    assert not (directory / (label + '-stderr.txt')).read_bytes()
    return (directory / (label + '-stdout.txt')).read_text()


def parse_clock(text):
    values = re.findall(r'^\s*nowRTC=([0-9]+)=[^\r\n]* nowELAPSED=([0-9]+)\s*$', text, re.M)
    assert len(values) == 1, 'Actual Android clock pair missing'
    rtc, elapsed = map(int, values[0])
    assert rtc > 0 and elapsed > 0
    return {'rtc_ms': rtc, 'elapsed_ms': elapsed}


def clock_evidence(directory, label, serial):
    result = parse_clock(command_evidence(directory, label, serial, ['shell', 'dumpsys', 'alarm']))
    host = json.loads((directory / (label + '-host-time.json')).read_text())
    assert set(host) == {'started_ns', 'finished_ns'} and all(type(n) is int and n > 0 for n in host.values())
    assert 0 < host['finished_ns'] - host['started_ns'] <= 10_000_000_000
    result.update(host)
    return result


def clock_follows(before, after):
    assert after['started_ns'] >= before['finished_ns'], 'Host observation order changed'
    assert after['elapsed_ms'] >= before['elapsed_ms'] and after['rtc_ms'] >= before['rtc_ms']
    uncertainty = (before['finished_ns'] - before['started_ns'] +
                   after['finished_ns'] - after['started_ns']) // 1_000_000 + 2
    assert abs((after['rtc_ms'] - before['rtc_ms']) -
               (after['elapsed_ms'] - before['elapsed_ms'])) <= uncertainty, 'Device wall clock jumped'


def history_age(text):
    if text == '0':
        return 0
    match = re.fullmatch(r'-(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?', text)
    assert match and any(value is not None for value in match.groups()), 'Invalid scheduler history age'
    return sum(int(value or 0) * scale for value, scale in zip(match.groups(), (86400000, 3600000, 60000, 1000, 1)))


def periodic_history(text, uid):
    sections = re.findall(r'(?ms)^[ \t]*Job history:\n(.*?)(?=\n[ \t]*\n|\Z)', text)
    assert len(sections) == 1, 'Actual scheduler history missing'
    service = PACKAGE + '/at.heinz.deviceai.routines.OwnerRoutineJobService'
    pattern = (r'^[ \t]*(\S+)[ \t]+(START-P|STOP-P): #' + re.escape(uid) + r'/620159 ' +
               re.escape(service) + r'(?: ([^\r\n]*))?[ \t]*$')
    events = [(kind, history_age(age), reason or '')
              for age, kind, reason in re.findall(pattern, sections[0], re.M)]
    assert len(events) <= 100
    return events


def battery_receipt(tool, row):
    assert tool['tool'] == 'get_battery_info' and tool['risk'] == 'READ_ONLY' and tool['arguments'] == {}
    assert re.fullmatch('worker-rt-' + UUID + '-1', tool['call_id'])
    assert tool['fingerprint'] == hashlib.sha256(b'').hexdigest()
    assert tool['transitions'][-1][0] == 'SUCCEEDED'
    assert any(state in ('STARTED', 'DISPATCH_COMMITTED') for state, _ in tool['transitions'])
    prefix = 'Akku prüfen:\n'
    assert row['last_result'].startswith(prefix)
    output = row['last_result'][len(prefix):]
    assert re.search(r'^Aktueller Akkustand: (?:100|[0-9]{1,2}) % \(Android-Batteriedienst\)\.$', output, re.M)
    assert tool['output_sha256'] == hashlib.sha256(output.encode()).hexdigest()
    assert tool['output_length'] == len(output.encode('utf-16-be')) // 2
    assert row['approved_at'] <= tool['created'] <= tool['transitions'][-1][1] <= row['last_at']
    assert tool['transitions'][-1][1] - tool['created'] <= 120000, 'Tool completion exceeded worker budget'


def verify_periodic(directory, report, previous, conversation):
    serial = report['serial']
    first_state, last_state = stored(directory, 'periodic-first'), stored(directory, 'periodic-complete')
    first, last = single(first_state), single(last_state)
    assert not first_state['outbox'] and not last_state['outbox']
    for key in ('id', 'goal', 'trigger', 'tools', 'approved_at', 'approval'):
        assert first[key] == last[key], 'Periodic authorization changed'
    assert first['goal'] == 'Prüfe Akku' and first['tools'] == ['get_battery_info']
    assert first['trigger']['kind'] == 'periodic' and first['trigger']['period'] == 900000
    assert first['trigger']['first'] <= first['approved_at'] <= first['last_at']
    assert first['run_id'] is None and first['report'] is None and last['run_id'] is None and last['report'] is None
    assert last_state['revision'] > first_state['revision']
    assert first['next_at'] >= first['last_at'] + 900000 and last['next_at'] >= last['last_at'] + 900000
    assert last['last_at'] >= first['next_at'] and last['next_at'] > first['next_at']
    assert first['last_result'] == last['last_result'], 'Unchanged measurement proof needs identical battery output'
    assert first['last_digest'] == last['last_digest'] == re.sub(r'\s+', ' ', first['last_result'].strip())
    before = {row['call_id']: row for row in previous}
    initial = {row['call_id']: row for row in receipts(directory, 'periodic-first')}
    final = {row['call_id']: row for row in receipts(directory, 'periodic-complete')}
    assert all(initial.get(key) == value for key, value in before.items()), 'Existing receipt changed'
    assert all(final.get(key) == value for key, value in initial.items()), 'Initial receipt changed'
    assert len(initial) == len(before) + 1 and len(final) == len(initial) + 1, 'Expected two distinct periodic reads'
    first_tool = initial[(initial.keys() - before.keys()).pop()]
    last_tool = final[(final.keys() - initial.keys()).pop()]
    battery_receipt(first_tool, first)
    battery_receipt(last_tool, last)
    assert first_tool['created'] - first['approved_at'] < 120000, 'Initial execution was not separated'
    assert last_tool['created'] >= first['next_at'], 'Second tool call preceded actual due time'
    confirmation, _ = text_nodes(directory, 'periodic-confirmation')
    assert 'Routine dauerhaft freigeben?' in confirmation and 'DAUERHAFT FREIGEBEN' in confirmation
    assert any('Prüfe Akku' in text and '15 Minuten' in text and 'Akku prüfen' in text for text in confirmation)
    body = f"Routine {first['id']}:\n{first['last_result']}"
    assert chat_binding(directory, 'periodic-first', first, body) == conversation
    assert chat_binding(directory, 'periodic-complete', first, body) == conversation
    assert evidence_bytes(directory, 'periodic-first-chat') == evidence_bytes(directory, 'periodic-complete-chat'), 'Unchanged result appended another chat entry'
    _, initial_ui = text_nodes(directory, 'periodic-first')
    _, completed_ui = text_nodes(directory, 'periodic-complete')
    assert initial_ui['app_pid_before'] == completed_ui['app_pid_before'], 'Period wait restarted app'
    observation = json.loads((directory / 'periodic-observation.json').read_text())
    assert observation['source_sha'] == report['source_sha'] and observation['source_tree'] == report['source_tree']
    assert observation['routine_id'] == first['id']
    start = clock_evidence(directory, 'periodic-wait-start', serial)
    assert type(observation['probe_started_ns']) is int and 0 < observation['probe_started_ns'] <= start['started_ns']
    assert start['finished_ns'] + 1_260_000_000_000 <= observation['probe_started_ns'] + 1_500_000_000_000, 'No time reserved for periodic completion and stop'
    labels = observation['observations']
    assert 1 <= len(labels) <= 110 and labels == [f'periodic-wait-{index}' for index in range(len(labels))]
    prior = start
    for label in labels:
        current = clock_evidence(directory, label, serial)
        clock_follows(prior, current)
        assert command_evidence(directory, label + '-pid', serial, ['shell', 'pidof', PACKAGE]).strip() == initial_ui['app_pid_before']
        observed = single(stored(directory, label))
        assert observed['id'] == first['id'] and observed['approval'] == first['approval']
        prior = current
    assert observed == last, 'Final poll and completed state differ'
    waited_ms = (prior['started_ns'] - start['finished_ns']) // 1_000_000
    assert 900000 <= waited_ms <= 1020000, 'Real minimum period or worker deadline violated'
    assert prior['elapsed_ms'] - start['elapsed_ms'] >= 900000 and prior['rtc_ms'] >= first['next_at']
    statuses = command_evidence(directory, labels[-1] + '-job-state', serial,
        ['shell', 'cmd', 'jobscheduler', 'get-job-state', '-u', '0', PACKAGE, '620159']).split()
    assert statuses and set(statuses) <= {'ready', 'waiting'}, 'Completed job is still active or unavailable'
    mode = observation['mode']
    assert mode in ('NATURAL_SCHEDULER', 'SATISFIED_SCHEDULER_TEST_TRIGGER')
    request = directory / 'periodic-run-s-command.json'
    if mode == 'SATISFIED_SCHEDULER_TEST_TRIGGER':
        ready = clock_evidence(directory, 'periodic-run-before', serial)
        clock_follows(start, ready)
        assert ready['rtc_ms'] >= first['next_at'] and ready['elapsed_ms'] - start['elapsed_ms'] >= 900000
        assert (ready['started_ns'] - start['finished_ns']) // 1_000_000 >= 900000
        assert command_evidence(directory, 'periodic-run-state', serial,
            ['shell', 'cmd', 'jobscheduler', 'get-job-state', '-u', '0', PACKAGE, '620159']).strip() == 'ready'
        assert command_evidence(directory, 'periodic-run-s', serial,
            ['shell', 'cmd', 'jobscheduler', 'run', '-s', '-u', '0', PACKAGE, '620159']) == 'Running job\n'
    else:
        assert not request.exists(), 'Natural execution included a scheduler test trigger'
    job_args = ['shell', 'dumpsys', 'jobscheduler', PACKAGE]
    initial_job = command_evidence(directory, 'periodic-first-job', serial, job_args)
    completed_job = command_evidence(directory, 'periodic-complete-job', serial, job_args)
    uid = registered_job_uid(initial_job)
    assert registered_job_uid(completed_job) == uid
    due_job = command_evidence(directory, 'periodic-due-job', serial, job_args)
    assert registered_job_uid(due_job) == uid
    first_lower = clock_evidence(directory, 'periodic-first-job-before', serial)
    first_upper = clock_evidence(directory, 'periodic-first-job-after', serial)
    clock_follows(first_lower, first_upper)
    clock_follows(first_upper, start)
    assert first['last_at'] <= first_lower['rtc_ms']
    lower = clock_evidence(directory, 'periodic-complete-job-before', serial)
    upper = clock_evidence(directory, 'periodic-complete-job-after', serial)
    due_lower = clock_evidence(directory, 'periodic-due-job-before', serial)
    due_upper = clock_evidence(directory, 'periodic-due-job-after', serial)
    clock_follows(start, due_lower)
    clock_follows(due_lower, due_upper)
    clock_follows(due_upper, lower)
    assert due_lower['rtc_ms'] >= first['next_at']
    clock_follows(prior, lower)
    clock_follows(lower, upper)
    history = periodic_history(completed_job, uid)
    pairs = []
    for begin, end in zip(history, history[1:]):
        if begin[0] != 'START-P' or end[0] != 'STOP-P' or end[2] != 'app called jobFinished':
            continue
        assert begin[1] >= end[1], 'Scheduler stop preceded its start'
        start_bounds = [lower['rtc_ms'] - begin[1], upper['rtc_ms'] - begin[1]]
        stop_bounds = [lower['rtc_ms'] - end[1], upper['rtc_ms'] - end[1]]
        if start_bounds[0] <= last_tool['created'] <= last['last_at'] <= stop_bounds[1]:
            pairs.append((start_bounds, stop_bounds))
    assert len(pairs) == 1, 'Due receipt is not bound to one completed Android service job'
    assert command_evidence(directory, 'periodic-wake', serial,
        ['shell', 'input', 'keyevent', 'KEYCODE_WAKEUP']) == ''
    stopped = stored(directory, 'periodic-stopped')
    assert not stopped['entries'] and not stopped['outbox']
    assert {r['call_id']: r for r in receipts(directory, 'periodic-stopped')} == final
    stopped_text, _ = text_nodes(directory, 'periodic-stopped')
    assert f"Routine {first['id']} gestoppt und Freigabe entfernt." in stopped_text
    stopped_command = json.loads((directory / 'periodic-after-stop-command.json').read_text())
    assert stopped_command['args'][1:] == ['-s', serial, 'shell', 'cmd', 'jobscheduler',
        'get-job-state', '-u', '0', PACKAGE, '620159']
    assert Path(stopped_command['args'][0]).name == 'adb'
    verify_job_absent(stopped_command['returncode'],
        (directory / 'periodic-after-stop-stdout.txt').read_bytes(),
        (directory / 'periodic-after-stop-stderr.txt').read_bytes(), uid)
    return list(final.values()), {'periodic_due_worker_execution': 'PASS', 'periodic_due_trigger': mode,
        'periodic_real_wait_ms': waited_ms, 'periodic_routine_id': first['id'],
        'periodic_initial_call_id': first_tool['call_id'], 'periodic_due_call_id': last_tool['call_id'],
        'periodic_job_start_rtc_bounds': pairs[0][0], 'periodic_job_stop_rtc_bounds': pairs[0][1],
        'periodic_unchanged_chat': 'PASS', 'periodic_stop_removes_job': 'PASS'}


def text_nodes(directory, label):
    xml = (directory / (label + '.xml')).read_bytes()
    root = ET.fromstring(xml)
    window = json.loads((directory / (label + '-window.json')).read_text())
    command = json.loads((directory / (label + '-ui-automation-command.json')).read_text())
    raw = (directory / (label + '-ui-automation-stdout.txt')).read_text()
    assert command['returncode'] == 0 and 'INSTRUMENTATION_FAILED' not in raw
    assert re.findall(r'^INSTRUMENTATION_CODE: (.*?)\r?$', raw, re.M) == ['-1']
    fields = re.findall(r'^INSTRUMENTATION_RESULT: ([a-z][a-z0-9_]*)=(.*?)\r?$', raw, re.M)
    assert len(fields) == len(dict(fields)) and all(window[key] == value for key, value in fields)
    assert set(dict(fields)) == set(window) - {'host_started_unix_ns', 'host_finished_unix_ns', 'app_pid_before', 'app_pid_after'}
    assert base64.b64decode(window['hierarchy_base64'], validate=True) == xml
    assert window['status'] == 'ok' and 0 < int(window['elapsed_ms']) < 45000
    assert window['helper_package'] == PACKAGE + '.test'
    assert window['app_pid_before'] == window['app_pid_after']
    assert window['helper_pid'] not in window['app_pid_before'].split()
    png = (directory / (label + '.png')).read_bytes()
    assert png.startswith(b'\x89PNG\r\n\x1a\n') and len(png) > 4096
    assert hashlib.sha256(png).hexdigest() == window['screenshot_sha']
    assert struct.unpack('>II', png[16:24]) == (int(window['screenshot_width']), int(window['screenshot_height']))
    assert root.get('rotation') == window['rotation']
    assert len(list(root.iter('node'))) == int(window['node_count'])
    for key in ('windows', 'windows_after'):
        focused = [item for item in json.loads(window[key]) if item['display'] == 0 and item['focused'] and item['type'] == 1]
        assert len(focused) == 1 and focused[0]['id'] == int(window['window_id'])
    times = [int(window[key]) for key in ('capture_started_ms', 'hierarchy_finished_ms', 'screenshot_finished_ms', 'capture_finished_ms')]
    assert times[0] > 0 and times == sorted(times)
    return [node.get('text', '') for node in root.iter('node') if node.get('package') == PACKAGE], window


def chat_binding(directory, label, row, expected_body):
    active = evidence_bytes(directory, label + '-conversation').decode().strip()
    assert re.fullmatch(UUID, active), 'Unexpected owner conversation'
    raw = evidence_bytes(directory, label + '-chat')
    assert raw and raw.endswith(b'\n'), 'Incomplete persisted chat'
    lines = [json.loads(line) for line in raw.decode('utf-8').splitlines()]
    matches = [item for item in lines if item.get('u') is False and item.get('t') == expected_body]
    assert len(matches) == 1 and matches[0]['at'] == row['last_at'], 'Chat result not bound exactly once'
    assert all(item.get('t') != expected_body for item in lines if item.get('u') is True)
    texts, _ = text_nodes(directory, label)
    assert texts.count(expected_body) == 1, 'Bound result absent or duplicated in actual UI'
    return active


def verify(directory, report, source_sha, source_tree):
    directory = Path(directory)
    assert report['mode'] == 'routines' and report['package'] == PACKAGE
    assert report['sdk'] == 35 and report['abi'] == 'x86_64'
    assert re.fullmatch(r'[a-f0-9]{40}', report['source_sha']) and re.fullmatch(r'[a-f0-9]{40}', report['source_tree'])
    assert report['source_sha'] == source_sha and report['source_tree'] == source_tree
    approved, rotated = single(stored(directory, 'approved')), single(stored(directory, 'rotated'))
    assert approved == rotated and approved['goal'] == 'Prüfe Akku'
    assert approved['trigger'] == {'kind': 'event', 'name': 'owner-app-start'}
    assert approved['tools'] == ['get_battery_info'] and approved['run_id'] is None
    assert approved['last_result'] is None and not receipts(directory, 'approved') and not receipts(directory, 'rotated')
    confirmation, _ = text_nodes(directory, 'battery-confirmation')
    assert 'Routine dauerhaft freigeben?' in confirmation and 'DAUERHAFT FREIGEBEN' in confirmation
    assert any('Prüfe Akku' in text and 'get_battery_info' not in text and 'Akku prüfen' in text for text in confirmation)
    _, rotation = text_nodes(directory, 'routine-rotation')
    _, initial = text_nodes(directory, 'battery-approved')
    assert rotation['rotation'] == '1' and rotation['app_pid_before'] == initial['app_pid_before']
    completed = single(stored(directory, 'after-start'))
    assert completed['id'] == approved['id'] and completed['approval'] == approved['approval']
    actual = receipts(directory, 'after-start')
    assert len(actual) == 1, 'Unexpected actual tool calls'
    tool = actual[0]
    assert tool['tool'] == 'get_battery_info' and tool['risk'] == 'READ_ONLY' and tool['arguments'] == {}
    assert re.fullmatch('worker-rt-' + UUID + '-1', tool['call_id'])
    assert tool['fingerprint'] == hashlib.sha256(b'').hexdigest()
    assert tool['transitions'][-1][0] == 'SUCCEEDED'
    assert any(state in ('STARTED', 'DISPATCH_COMMITTED') for state, _ in tool['transitions'])
    prefix = 'Akku prüfen:\n'
    assert completed['last_result'].startswith(prefix)
    output = completed['last_result'][len(prefix):]
    assert re.search(r'^Aktueller Akkustand: (?:100|[0-9]{1,2}) % \(Android-Batteriedienst\)\.$', output, re.M)
    assert tool['output_sha256'] == hashlib.sha256(output.encode()).hexdigest()
    assert tool['output_length'] == len(output.encode('utf-16-be')) // 2
    body = f"Routine {completed['id']}:\n{completed['last_result']}"
    conversation = chat_binding(directory, 'after-start', completed, body)
    _, started = text_nodes(directory, 'routine-process-start')
    assert started['app_pid_before'] != initial['app_pid_before']
    assert tool['created'] >= approved['approved_at']
    for label in ('stopped', 'stopped-restart'):
        assert stored(directory, label)['entries'] == [] and stored(directory, label)['outbox'] == []
        assert receipts(directory, label) == actual, 'Revoked routine executed again'
    stop_texts, _ = text_nodes(directory, 'battery-stopped')
    assert f"Routine {approved['id']} gestoppt und Freigabe entfernt." in stop_texts
    _, stopped_restart = text_nodes(directory, 'routine-stopped-restart')
    assert stopped_restart['app_pid_before'] != started['app_pid_before']
    blocked = single(stored(directory, 'blocked'))
    assert blocked['goal'] == 'Prüfe Downloads' and blocked['tools'] == ['list_files']
    assert blocked['trigger']['kind'] == 'periodic' and blocked['trigger']['period'] == 900000
    assert blocked['last_result'] == 'Downloads benötigen eine bereits erteilte Shizuku-Freigabe.'
    confirmation, _ = text_nodes(directory, 'downloads-confirmation')
    assert 'Routine dauerhaft freigeben?' in confirmation and 'DAUERHAFT FREIGEBEN' in confirmation
    assert any('Prüfe Downloads' in text and '/sdcard/Download' in text and 'bestehende Shizuku-Freigabe nötig' in text for text in confirmation)
    assert receipts(directory, 'blocked') == actual, 'Unauthorized download dispatched'
    blocked_body = f"Routine {blocked['id']} ist blockiert: {blocked['last_result']}"
    assert chat_binding(directory, 'blocked', blocked, blocked_body) == conversation
    command = json.loads((directory / 'schedule-state-command.json').read_text())
    assert command['returncode'] == 0, 'Android job registration not observed'
    dump = json.loads((directory / 'schedule-dump-command.json').read_text())
    assert dump['returncode'] == 0, 'Android job dump failed'
    uid = registered_job_uid((directory / 'schedule-dump-stdout.txt').read_text())
    assert stored(directory, 'final')['entries'] == [] and stored(directory, 'final')['outbox'] == []
    assert receipts(directory, 'final') == actual
    stopped_texts, _ = text_nodes(directory, 'routines-stopped')
    assert 'Routinen gestoppt und Freigaben entfernt: 1.' in stopped_texts
    stopped = json.loads((directory / 'schedule-after-stop-command.json').read_text())
    verify_job_absent(stopped['returncode'],
                      (directory / 'schedule-after-stop-stdout.txt').read_bytes(),
                      (directory / 'schedule-after-stop-stderr.txt').read_bytes(), uid)
    _, periodic_checks = verify_periodic(directory, report, actual, conversation)
    return {'app_start_battery_worker': 'PASS', 'rotation_did_not_trigger_worker': 'PASS',
            'durable_owner_chat': 'PASS', 'stop_survives_process_restart': 'PASS',
            'missing_shizuku_blocks_dispatch': 'PASS', 'android_periodic_registration': 'PASS',
            **periodic_checks, 'battery_call_id': tool['call_id'],
            'owner_conversation_id': conversation, 'battery_routine_id': approved['id'],
            'downloads_routine_id': blocked['id']}


class Probe:
    def __init__(self, api, nodes, pid, display):
        self.api, self.nodes, self.pid, self.display = api, nodes, pid, display

    def take(self, label, **options):
        self.nodes, self.pid, self.display = self.api['snapshot'](label, record=False, **options)
        return self.nodes

    def tap(self, labels):
        assert self.api['tap'](self.nodes, labels, self.display), 'Required real UI control missing: ' + str(labels)

    def open_chat(self, label):
        if not any(node.get('class') == 'android.widget.EditText' for node in self.nodes):
            self.tap(['Konversation öffnen', 'JARVIS: Konversation öffnen', 'Gespräch öffnen', 'Gespräch', 'Chat'])
            self.take(label)

    def send(self, label, text):
        editors = [node for node in self.nodes if node.get('class') == 'android.widget.EditText'
                   and self.api['visible_bounds'](node, self.display)]
        assert len(editors) == 1 and editors[0].get('text', '') in ('', 'Schreibe eine Nachricht …')
        if editors[0].get('focused') != 'true':
            self.api['tap_node'](editors[0], self.display)
            self.take(label + '-focus')
            editors = [node for node in self.nodes if node.get('class') == 'android.widget.EditText']
            assert len(editors) == 1 and editors[0].get('focused') == 'true'
        result = self.api['captured_device'](label + '-type', 'shell', 'input', 'text', shlex.quote(text.replace(' ', '%s')))
        assert result.returncode == 0
        keyboard = self.api['shell']('dumpsys', 'input_method')
        self.api['save'](label + '-input-method.txt', keyboard)
        if self.api['keyboard_visible'](keyboard):
            self.api['shell']('input', 'keyevent', 'KEYCODE_BACK')
        self.take(label + '-input', await_text=text)
        editors = [node for node in self.nodes if node.get('class') == 'android.widget.EditText']
        assert len(editors) == 1 and editors[0].get('text') == text
        self.tap(['Nachricht senden', 'Routinenbefehl senden'])

    def private(self, label, path, limit=8 * 1024 * 1024 + 128, timeout=10):
        assert path in (STATE_PATH, RECEIPT_PATH, ACTIVE_PATH) or re.fullmatch('files/chat-history/' + UUID + r'\.jsonl', path)
        command = f'if [ ! -e {path} ]; then exit 44; fi; [ -f {path} ] && [ ! -L {path} ] || exit 45; head -c {limit + 1} {path}'
        result = self.api['captured_device'](label, 'shell', '-T', 'run-as', PACKAGE, 'sh', '-c', shlex.quote(command), timeout=timeout)
        assert not result.stderr and result.returncode in (0, 44), 'Private evidence read failed'
        assert len(result.stdout) <= limit, 'Private evidence too large'
        assert result.returncode == 0 or not result.stdout
        return result.stdout if result.returncode == 0 else None

    def state(self, label, timeout=10):
        return decode_state(self.private(label + '-routines', STATE_PATH, timeout=timeout))

    def wait_state(self, label, predicate):
        deadline = time.monotonic() + 30
        for attempt in range(60):
            assert time.monotonic() < deadline, 'Actual routine state did not complete: ' + label
            state = self.state(f'{label}-wait-{attempt}', min(10, max(.1, deadline - time.monotonic())))
            assert time.monotonic() < deadline, 'Routine state observation exceeded deadline'
            if predicate(state):
                return state
            time.sleep(.25)
        raise AssertionError('Routine observation budget exhausted')

    def files(self, label, chat=False):
        state = self.state(label)
        decode_receipts(self.private(label + '-receipts', RECEIPT_PATH, 256 * 1024 + 128))
        if chat:
            conversation = self.private(label + '-conversation', ACTIVE_PATH, 128).decode().strip()
            assert re.fullmatch(UUID, conversation)
            self.private(label + '-chat', f'files/chat-history/{conversation}.jsonl', 2 * 1024 * 1024)
        return state

    def clock(self, label):
        started = time.monotonic_ns()
        result = self.api['captured_device'](label, 'shell', 'dumpsys', 'alarm', timeout=10)
        finished = time.monotonic_ns()
        self.api['save'](label + '-host-time.json', json.dumps({'started_ns': started, 'finished_ns': finished}))
        assert result.returncode == 0 and not result.stderr
        return clock_evidence(self.api['out'], label, self.api['serial'])

    def job_dump(self, label):
        self.clock(label + '-before')
        result = self.api['captured_device'](label, 'shell', 'dumpsys', 'jobscheduler', PACKAGE, timeout=10)
        self.clock(label + '-after')
        assert result.returncode == 0 and not result.stderr
        return registered_job_uid(result.stdout.decode())

    def periodic(self):
        self.send('periodic', 'Routine alle 15 Minuten: Akku')
        self.take('periodic-confirmation', await_text='Routine dauerhaft freigeben?')
        self.tap(['DAUERHAFT FREIGEBEN'])
        initial = single(self.wait_state('periodic-initial', lambda value: len(value['entries']) == 1 and
            single(value)['last_result'] is not None and single(value)['run_id'] is None))
        body = f"Routine {initial['id']}:\n{initial['last_result']}"
        self.take('periodic-first', await_text=body)
        first = single(self.files('periodic-first', chat=True))
        assert first['next_at'] >= first['last_at'] + 900000
        scheduled_uid = self.job_dump('periodic-first-job')
        start = self.clock('periodic-wait-start')
        assert start['finished_ns'] + 1_260_000_000_000 <= self.api['probe_started_ns'] + 1_500_000_000_000, 'Insufficient process time for periodic wait, result and stop'
        deadline_ns = start['finished_ns'] + 1_020_000_000_000
        observation = {'source_sha': self.api['source'], 'source_tree': self.api['report']['source_tree'],
            'routine_id': first['id'], 'mode': 'NATURAL_SCHEDULER', 'observations': [],
            'probe_started_ns': self.api['probe_started_ns']}
        requested = False
        due_job_observed = False
        for index in range(110):
            assert time.monotonic_ns() < deadline_ns, 'Periodic wait and worker deadline exceeded'
            label = f'periodic-wait-{index}'
            current = self.clock(label)
            process = self.api['captured_device'](label + '-pid', 'shell', 'pidof', PACKAGE, timeout=10)
            assert process.returncode == 0 and not process.stderr and process.stdout.decode().strip() == self.pid
            state = single(self.state(label))
            assert time.monotonic_ns() < deadline_ns, 'Periodic observation exceeded deadline'
            assert state['id'] == first['id'] and state['approval'] == first['approval']
            observation['observations'].append(label)
            self.api['save']('periodic-observation.json', json.dumps(observation))
            elapsed_ns = current['started_ns'] - start['finished_ns']
            ready_time = elapsed_ns >= 900_000_000_000 and current['elapsed_ms'] - start['elapsed_ms'] >= 900000 and current['rtc_ms'] >= first['next_at']
            if ready_time:
                if not due_job_observed:
                    assert self.job_dump('periodic-due-job') == scheduled_uid
                    due_job_observed = True
                result = self.api['captured_device'](label + '-job-state', 'shell', 'cmd', 'jobscheduler',
                    'get-job-state', '-u', '0', PACKAGE, '620159', timeout=10)
                assert result.returncode == 0 and not result.stderr
                statuses = result.stdout.decode().split()
                assert statuses and set(statuses) <= {'ready', 'waiting', 'active', 'pending'}, 'Android job unavailable'
                assert time.monotonic_ns() < deadline_ns, 'Scheduler observation exceeded deadline'
                complete = state['last_at'] > first['last_at'] and state['run_id'] is None and state['report'] is None
                if complete and not {'active', 'pending'} & set(statuses):
                    break
                if not complete and state['run_id'] is None and not requested and statuses == ['ready']:
                    trigger_clock = self.clock('periodic-run-before')
                    assert trigger_clock['rtc_ms'] >= first['next_at']
                    result = self.api['captured_device']('periodic-run-state', 'shell', 'cmd', 'jobscheduler',
                        'get-job-state', '-u', '0', PACKAGE, '620159', timeout=10)
                    assert result.returncode == 0 and not result.stderr
                    if result.stdout.strip() == b'ready':
                        result = self.api['captured_device']('periodic-run-s', 'shell', 'cmd', 'jobscheduler',
                            'run', '-s', '-u', '0', PACKAGE, '620159', timeout=10)
                        assert result.returncode == 0 and not result.stderr and result.stdout == b'Running job\n', 'Satisfied scheduler trigger was rejected'
                        requested = True
                        observation['mode'] = 'SATISFIED_SCHEDULER_TEST_TRIGGER'
                        self.api['save']('periodic-observation.json', json.dumps(observation))
            remaining = (deadline_ns - time.monotonic_ns()) / 1_000_000_000
            assert remaining > 0, 'Periodic observation deadline exceeded'
            time.sleep(min(10, remaining))
        else:
            raise AssertionError('Periodic observation count exhausted')
        assert self.job_dump('periodic-complete-job') == scheduled_uid
        result = self.api['captured_device']('periodic-wake', 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        assert result.returncode == 0 and not result.stdout and not result.stderr
        self.take('periodic-complete', await_text=body)
        self.files('periodic-complete', chat=True)
        self.send('periodic-stop', 'Routine stoppen ' + first['id'])
        self.wait_state('periodic-stopped', lambda value: not value['entries'] and not value['outbox'])
        self.take('periodic-stopped', await_text=f"Routine {first['id']} gestoppt und Freigabe entfernt.")
        self.files('periodic-stopped')
        result = self.api['captured_device']('periodic-after-stop', 'shell', 'cmd', 'jobscheduler',
            'get-job-state', '-u', '0', PACKAGE, '620159')
        verify_job_absent(result.returncode, result.stdout, result.stderr, scheduled_uid)

    def restart(self, label):
        previous = self.pid
        self.api['shell']('am', 'force-stop', PACKAGE)
        assert not self.api['shell']('pidof', PACKAGE, check=False)
        self.nodes, self.pid, self.display = self.api['launch'](label)
        assert self.pid != previous, 'Actual process restart missing'

    def close_list(self, label, expected):
        self.tap(['Schließen', 'SCHLIESSEN'])
        self.take(label, await_text=expected)


def run(api, nodes, pid, display):
    probe = Probe(api, nodes, pid, display)
    api['establish_offline']('routine')
    probe.open_chat('routine-chat')
    probe.send('battery', 'Routine beim Appstart: Akku')
    probe.take('battery-confirmation', await_text='Routine dauerhaft freigeben?')
    probe.tap(['DAUERHAFT FREIGEBEN'])
    state = probe.wait_state('approved', lambda value: len(value['entries']) == 1)
    approved = single(state)
    assert approved['last_result'] is None
    probe.take('battery-approved', await_text=f"Routine {approved['id']} dauerhaft gespeichert. Auslöser ist die erste App-Öffnung in einem neuen Prozess; Rotation zählt nicht.")
    probe.files('approved')
    api['shell']('settings', 'put', 'system', 'user_rotation', '1')
    probe.take('routine-rotation', rotation=1)
    probe.files('rotated')
    api['shell']('settings', 'put', 'system', 'user_rotation', '0')
    probe.restart('routine-process-start')
    state = probe.wait_state('battery-complete', lambda value: single(value)['last_result'] is not None)
    row = single(state)
    expected = f"Routine {row['id']}:\n{row['last_result']}"
    probe.open_chat('routine-restarted-chat')
    probe.send('list-battery', 'Routinen')
    probe.take('battery-list', await_text='CONTINUUM · ROUTINEN')
    probe.close_list('after-start', expected)
    probe.files('after-start', chat=True)
    probe.send('stop-battery', 'Routine stoppen ' + approved['id'])
    probe.wait_state('stopped', lambda value: not value['entries'] and not value['outbox'])
    probe.take('battery-stopped', await_text=f"Routine {approved['id']} gestoppt und Freigabe entfernt.")
    probe.files('stopped')
    probe.restart('routine-stopped-restart')
    probe.open_chat('routine-stopped-chat')
    probe.files('stopped-restart')
    probe.send('downloads', 'Routine alle 15 Minuten: Downloads')
    probe.take('downloads-confirmation', await_text='Routine dauerhaft freigeben?')
    probe.tap(['DAUERHAFT FREIGEBEN'])
    state = probe.wait_state('downloads-blocked', lambda value: len(value['entries']) == 1 and single(value)['last_result'] is not None)
    blocked = single(state)
    expected = f"Routine {blocked['id']} ist blockiert: {blocked['last_result']}"
    probe.take('blocked', await_text=expected)
    probe.files('blocked', chat=True)
    result = api['captured_device']('schedule-state', 'shell', 'cmd', 'jobscheduler', 'get-job-state', '-u', '0', PACKAGE, '620159')
    assert result.returncode == 0, 'Actual Android job not registered'
    result = api['captured_device']('schedule-dump', 'shell', 'dumpsys', 'jobscheduler')
    assert result.returncode == 0
    scheduled_uid = registered_job_uid(result.stdout.decode())
    probe.send('list-downloads', 'Routinen')
    probe.take('downloads-list', await_text='CONTINUUM · ROUTINEN')
    probe.tap(['ALLE STOPPEN'])
    probe.wait_state('final', lambda value: not value['entries'] and not value['outbox'])
    probe.take('routines-stopped', await_text='Routinen gestoppt und Freigaben entfernt: 1.')
    probe.files('final')
    result = api['captured_device']('schedule-after-stop', 'shell', 'cmd', 'jobscheduler', 'get-job-state', '-u', '0', PACKAGE, '620159')
    verify_job_absent(result.returncode, result.stdout, result.stderr, scheduled_uid)
    probe.periodic()
    api['report']['routine_checks'] = verify(api['out'], api['report'], api['source'], api['report']['source_tree'])
    api['report']['limits'].append('Periodic evidence distinguishes natural scheduling from an explicit constraint-satisfied scheduler test request; device time was not changed')


if __name__ == '__main__':
    import sys
    assert len(sys.argv) == 4, 'Expected directory, source SHA and tree'
    directory = Path(sys.argv[1])
    report = json.loads((directory / 'RESULT.json').read_text())
    assert report['status'] == 'PASS' and not report.get('cleanup_errors')
    print(json.dumps(verify(directory, report, sys.argv[2], sys.argv[3]), indent=2, ensure_ascii=False))
