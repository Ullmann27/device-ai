"""Verify an owner-approved offline HTTPS attempt."""
import hashlib
import json
import math
from pathlib import Path
import re
import shlex
import time
import xml.etree.ElementTree as ET

from native_routines import (PACKAGE, UUID, RECEIPT_PATH, Reader, decode_receipts,
                             text_nodes, command_evidence)

URL = 'https://example.com/'
DOSSIER_PATH = 'files/research/research-dossier.bin'
PREFS_PATH = 'shared_prefs/continuum-internet.xml'
OFF = 'Aus (kein Webzugriff)'
QUICK = 'QUICK · bis 15 Sek.\nÖffentliche Quellen im gesamten Web'
TOPIC_HINT = 'Thema, z. B. Wie funktionieren Solarzellen?'
EMPTY = ('Es konnten keine verlässlichen Quellen geladen werden. Prüfe die Internetverbindung, '
         'versuche einen genaueren Suchbegriff oder gib eine HTTPS-Adresse an.')
TIMEOUT = 'Die Recherche hat zu lange gedauert. Bitte prüfe die Internetverbindung und versuche es erneut.'
SUFFIX = ('\n\nDirekte genehmigte HTTPS-Quelle.'
          '\n\nTipp: Ein konkreter Suchbegriff oder ein direkter HTTPS-Link kann helfen.'
          '\n\nHinweis: Deterministische Rechercheausgabe (NO_USABLE_EVIDENCE); keine bestätigte Gemma-Antwort.')
QUICK_BUDGET = [2, 1, 3, 64000, 128000, 32, 1, 5000, 15000, 0, 0, 2]


def java_length(value):
    return len(value.encode('utf-16-be')) // 2


def decode_empty_dossier(payload):
    assert payload is not None and 0 < len(payload) <= 1_000_064, 'Research dossier missing or oversized'
    outer = Reader(payload)
    assert outer.utf() == 'DEVICE_AI_RESEARCH' and outer.number('i') == 3, 'Research schema mismatch'
    data = outer.take(outer.count(1_000_000))
    assert outer.take(32) == hashlib.sha256(data).digest(), 'Research checksum mismatch'
    outer.end()
    reader = Reader(data)

    def string(limit):
        value = reader.take(reader.count(32000)).decode('utf-8')
        assert java_length(value) <= limit, 'Research string exceeds bound'
        return value

    def strings(count, length):
        return [string(length) for _ in range(reader.count(count))]

    def optional(limit):
        return string(limit) if reader.boolean() else None

    result = {'id': string(64), 'created': reader.number('q'), 'updated': reader.number('q'),
              'mode': reader.number('i'), 'question': string(2048)}
    assert re.fullmatch(r'[a-f0-9]{16,64}', result['id'])
    assert 0 <= result['created'] <= result['updated']
    result['questions'] = [(string(48), string(512), reader.number('i'))
                           for _ in range(reader.count(32))]
    result['paths'] = [(string(48), string(512), string(48), reader.boolean())
                       for _ in range(reader.count(12))]
    result['budget'] = [reader.number('i') for _ in range(8)] + [reader.number('q')]
    result['budget'] += [reader.number('i') for _ in range(3)]
    result['phase'] = reader.number('i')
    for name, maximum in [('sources', 24), ('claims', 256), ('evidence', 2048), ('relationships', 2048)]:
        assert reader.count(maximum) == 0, 'Offline dossier contains ' + name
    coverage = [(string(128), reader.number('d')) for _ in range(reader.count(256))]
    assert len(dict(coverage)) == len(coverage) and all(math.isfinite(v) and v == 0 for _, v in coverage)
    independent = [(string(128), reader.number('i')) for _ in range(reader.count(256))]
    assert len(dict(independent)) == len(independent) and all(v == 0 for _, v in independent)
    result['gaps'] = [(string(64), string(256), string(512), reader.boolean())
                      for _ in range(reader.count(32))]
    result['synthesis'] = optional(8000)
    assert strings(24, 64) == [], 'Offline dossier contains completed source hashes'
    result['general_search'] = reader.boolean()
    result['reason'] = optional(512)
    rounds = []
    for _ in range(reader.count(3)):
        row = {'number': reader.number('i'), 'phase': reader.number('i'),
               'queries': strings(12, 48), 'urls': strings(24, 4096), 'started': reader.number('q')}
        row['completed'] = reader.number('q') if reader.boolean() else None
        rounds.append(row)
    result['rounds'] = rounds
    result['privacy'] = reader.number('i')
    assert result['privacy'] in range(4)
    reader.end()
    return result


def result_body(dossier):
    reason = dossier['reason'] or ''
    return (TIMEOUT if re.search('timeout|deadline', reason, re.I) else EMPTY) + SUFFIX


def private_args(path):
    assert path in (RECEIPT_PATH, DOSSIER_PATH, PREFS_PATH)
    limit = {RECEIPT_PATH: 256 * 1024 + 128, DOSSIER_PATH: 1_000_064, PREFS_PATH: 16384}[path]
    shell = f'if [ ! -e {path} ]; then exit 44; fi; [ -f {path} ] && [ ! -L {path} ] || exit 45; head -c {limit + 1} {path}'
    return ['shell', '-T', 'run-as', PACKAGE, 'sh', '-c', shlex.quote(shell)], limit


def private_evidence(directory, label, path, serial):
    args, limit = private_args(path)
    meta = json.loads((directory / (label + '-command.json')).read_text())
    assert Path(meta['args'][0]).name == 'adb' and type(meta['returncode']) is int
    assert meta['args'][1:] == ['-s', serial, *args]
    assert not (directory / (label + '-stderr.txt')).read_bytes()
    data = (directory / (label + '-stdout.txt')).read_bytes()
    assert len(data) <= limit
    if meta['returncode'] == 44:
        assert not data
        return None
    assert meta['returncode'] == 0, 'Private evidence read failed'
    return data


def mode_value(payload):
    assert payload is not None
    root = ET.fromstring(payload)
    modes = [node.text for node in root if node.tag == 'string' and node.get('name') == 'mode']
    assert root.tag == 'map' and len(modes) == 1 and modes[0] in ('ALWAYS_ON', 'ASK', 'OFF')
    return modes[0]


def visible(node, size):
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        if 0 <= x1 < x2 <= size[0] and 0 <= y1 < y2 <= size[1]:
            return x1, y1, x2, y2
    return None


def verify(directory, report, source_sha, source_tree):
    directory = Path(directory)
    assert report['source_sha'] == source_sha and report['source_tree'] == source_tree
    assert report['package'] == PACKAGE and report['sdk'] == 35 and report['abi'] == 'x86_64'
    serial = report['serial']
    assert re.fullmatch(r'emulator-[0-9]+', serial)
    observation = json.loads((directory / 'web-observation.json').read_text())
    assert observation['source_sha'] == source_sha and observation['source_tree'] == source_tree
    assert observation['url'] == URL and observation['test_basis'] == 'OFF_SET_THROUGH_UI'
    nodes_by_label, windows = {}, {}
    for label in observation['captures']:
        assert re.fullmatch(r'web-[a-z-]+', label) and label not in nodes_by_label
        texts, window = text_nodes(directory, label)
        nodes_by_label[label] = [n for n in ET.parse(directory / (label + '.xml')).iter('node')
                                 if n.get('package') == PACKAGE]
        windows[label] = window
    assert len(windows) <= 18
    starts = [check for check in report['checks'] if check['name'] == 'fresh-empty-no-permissions']
    assert len(starts) == 1 and re.fullmatch(r'\d+', starts[0]['pid'])
    assert {window['app_pid_before'] for window in windows.values()} == {starts[0]['pid']}
    expected_actions = [
        ('web-open-center-initial', 'Internet', 'web-home-initial'),
        ('web-open-settings-initial', 'INTERNETFREIGABE', 'web-center-initial'),
        ('web-select-off-initial', OFF, 'web-settings-initial'),
        ('web-open', 'Recherche nur nach Freigabe', 'web-chat-before'),
        ('web-select-ask', 'Auf Nachfrage', 'web-off-prompt'),
        ('web-select-quick', QUICK, 'web-modes'),
        ('web-search', 'JETZT SUCHEN', 'web-topic-input'),
        ('web-allow-once', 'Einmal erlauben', 'web-approval'),
        ('web-home-final', 'Zur Startseite mit allen KI-Bereichen', 'web-result'),
        ('web-open-center-final', 'Internet', 'web-home-final'),
        ('web-open-settings-final', 'INTERNETFREIGABE', 'web-center-final'),
        ('web-select-off-final', OFF, 'web-settings-final'),
        ('web-return-chat', 'Konversation öffnen', 'web-off-final')]
    actions = observation['actions']
    assert [(a['name'], a['control'].casefold(), a['snapshot']) for a in actions] == [(n, v.casefold(), s) for n, v, s in expected_actions]
    for action in actions:
        window = windows[action['snapshot']]
        size = (int(window['screenshot_width']), int(window['screenshot_height']))
        candidates = [n for n in nodes_by_label[action['snapshot']] if n.get('enabled') == 'true'
                      and n.get('clickable') == 'true' and visible(n, size)
                      and action['control'].casefold() in (n.get('text', '').casefold(), n.get('content-desc', '').casefold())]
        assert len(candidates) == 1, 'Bound UI control missing or ambiguous'
        x1, y1, x2, y2 = visible(candidates[0], size)
        assert command_evidence(directory, action['name'], serial,
            ['shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2)]) == ''
    for label in ('web-before-network', 'web-after-network'):
        network = command_evidence(directory, label, serial, ['shell', 'dumpsys', 'connectivity'])
        assert len(re.findall(r'(?:Active default network:|mActiveDefaultNetwork=)\s*(?:none|null)(?:\s|$)', network, re.I)) == 1
    original = mode_value(private_evidence(directory, 'web-mode-original', PREFS_PATH, serial))
    assert original == observation['original_mode']
    for label, title in [('web-settings-initial', 'Continuum · Internetzugang'),
                         ('web-off-prompt', 'Internet ist ausgeschaltet'),
                         ('web-topic', 'Device AI · Webrecherche'),
                         ('web-settings-final', 'Continuum · Internetzugang')]:
        assert sum(n.get('text') == title for n in nodes_by_label[label]) == 1
    for label, expected in [('web-mode-off', 'OFF'), ('web-mode-ask', 'ASK'), ('web-mode-final', 'OFF')]:
        assert mode_value(private_evidence(directory, label, PREFS_PATH, serial)) == expected
    assert private_evidence(directory, 'web-before-dossier', DOSSIER_PATH, serial) is None
    before = {r['call_id']: r for r in decode_receipts(private_evidence(directory, 'web-before-receipts', RECEIPT_PATH, serial))}
    after = {r['call_id']: r for r in decode_receipts(private_evidence(directory, 'web-after-receipts', RECEIPT_PATH, serial))}
    assert all(after.get(key) == value for key, value in before.items()) and len(after) == len(before) + 1
    receipt = after[(set(after) - set(before)).pop()]
    assert re.fullmatch('web-' + UUID, receipt['call_id'])
    fingerprint = hashlib.sha256(('url=string:' + hashlib.sha256(URL.encode()).hexdigest()).encode()).hexdigest()
    assert receipt['tool'] == 'web_read' and receipt['risk'] == 'CONFIRM'
    assert receipt['arguments'] == {'url': f'[STRING {java_length(URL)} chars]'}
    assert receipt['fingerprint'] == fingerprint, 'Receipt belongs to another exact URL'
    assert [state for state, _ in receipt['transitions']] == ['PROPOSED', 'APPROVED', 'START_PENDING', 'DISPATCH_COMMITTED', 'SUCCEEDED']
    assert 0 <= receipt['transitions'][-1][1] - receipt['created'] <= 18000
    dossier_bytes = private_evidence(directory, 'web-after-dossier', DOSSIER_PATH, serial)
    dossier = decode_empty_dossier(dossier_bytes)
    assert dossier['question'] == URL and dossier['mode'] == 0 and dossier['budget'] == QUICK_BUDGET
    assert dossier['phase'] == 9 and dossier['general_search'] is False
    reason = dossier['reason'] or ''
    assert reason == 'Research host could not be resolved.' or re.search(
        r'(?i)(network is unreachable|ENETUNREACH|failed to connect|connect(?:ion)? timed out|read timed out|socket timeout)', reason), 'No actual transport failure'
    assert receipt['transitions'][3][1] <= dossier['created'] <= dossier['updated'] <= receipt['transitions'][-1][1]
    assert dossier['updated'] - dossier['created'] <= 15000
    assert dossier['synthesis'] == 'Keine auswertbaren Quellen gefunden. Verbindung und HTTPS-Quellen prüfen.'
    output = dossier['synthesis'] + '\n\nKeine abrufbaren Quellen; keine Antwort erfunden.\n\nTeilrecherche: ' + reason[:256]
    assert receipt['output_length'] == java_length(output) and receipt['output_sha256'] == hashlib.sha256(output.encode()).hexdigest()
    approval = [n.get('text', '') for n in nodes_by_label['web-approval']]
    assert 'Internetzugriff einmal erlauben?' in approval
    assert sum(URL in text and 'Diese eine HTTPS-Quelle wird gelesen' in text for text in approval) == 1
    typed = [n for n in nodes_by_label['web-topic-input'] if n.get('class') == 'android.widget.EditText']
    assert len(typed) == 1 and typed[0].get('text') == URL
    assert command_evidence(directory, 'web-type-url', serial,
        ['shell', 'input', 'text', shlex.quote(URL)]) == ''
    result = nodes_by_label['web-result']
    size = (int(windows['web-result']['screenshot_width']), int(windows['web-result']['screenshot_height']))
    assert sum(n.get('text') == result_body(dossier) and bool(visible(n, size)) for n in result) == 1, 'Honest partial result absent or duplicated'
    editors = [n for n in result if n.get('class') == 'android.widget.EditText' and visible(n, size)]
    assert len(editors) == 1 and editors[0].get('enabled') == 'true' and editors[0].get('text', '') in ('', 'Schreibe eine Nachricht …')
    assert sum(n.get('content-desc') == 'Nachricht senden' and n.get('enabled') == 'true'
               and n.get('clickable') == 'true' and bool(visible(n, size)) for n in result) == 1
    assert all(n.get('content-desc') != 'Device AI arbeitet' for n in result)
    return {'status': 'PASS', 'url': URL, 'test_basis': 'OFF_SET_THROUGH_UI', 'original_mode': original,
            'approval': 'ASK_ONE_TIME', 'final_mode': 'OFF', 'receipt_call_id': receipt['call_id'],
            'receipt_terminal': 'SUCCEEDED', 'network_result': 'PARTIAL_TRANSPORT_FAILURE',
            'sources': 0, 'partial_reason': reason, 'dossier_sha256': hashlib.sha256(dossier_bytes).hexdigest(),
            'composer_idle': 'PASS'}


class Probe:
    def __init__(self, api, pid, display):
        self.api, self.pid, self.display = api, pid, display
        self.observation = {'source_sha': api['source'], 'source_tree': api['report']['source_tree'],
            'url': URL, 'test_basis': 'OFF_SET_THROUGH_UI', 'captures': [], 'actions': []}

    def save(self):
        self.api['save']('web-observation.json', json.dumps(self.observation, ensure_ascii=False))

    def take(self, label, **options):
        self.nodes, pid, self.display = self.api['snapshot'](label, record=False, **options)
        assert pid == self.pid, 'Web test changed app process'
        self.label = label
        self.observation['captures'].append(label)
        assert len(self.observation['captures']) <= 18
        self.save()

    def press(self, name, control):
        candidates = [n for n in self.nodes if n.get('enabled') == 'true' and n.get('clickable') == 'true'
                      and visible(n, self.display) and control.casefold() in
                      (n.get('text', '').casefold(), n.get('content-desc', '').casefold())]
        assert len(candidates) == 1, 'Required visible web control missing: ' + control
        x1, y1, x2, y2 = visible(candidates[0], self.display)
        result = self.api['captured_device'](name, 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        assert result.returncode == 0 and not result.stdout and not result.stderr
        self.observation['actions'].append({'name': name, 'control': control, 'snapshot': self.label})
        self.save()

    def private(self, label, path):
        args, limit = private_args(path)
        result = self.api['captured_device'](label, *args, timeout=5)
        assert not result.stderr and len(result.stdout) <= limit
        assert result.returncode in (0, 44)
        if result.returncode == 44:
            assert not result.stdout
            return None
        return result.stdout

    def settings_off(self, suffix):
        self.press('web-open-center-' + suffix, 'Internet')
        self.take('web-center-' + suffix, await_text='INTERNETFREIGABE')
        self.press('web-open-settings-' + suffix, 'INTERNETFREIGABE')
        self.take('web-settings-' + suffix, await_text='Continuum · Internetzugang')
        if suffix == 'initial':
            self.observation['original_mode'] = mode_value(self.private('web-mode-original', PREFS_PATH))
        self.press('web-select-off-' + suffix, OFF)
        self.take('web-off-' + suffix)
        assert mode_value(self.private('web-mode-' + ('off' if suffix == 'initial' else 'final'), PREFS_PATH)) == 'OFF'

    def prepare(self):
        self.take('web-home-initial')
        self.settings_off('initial')
        return self.nodes

    def run(self):
        self.take('web-chat-before')
        self.press('web-open', 'Recherche nur nach Freigabe')
        self.take('web-off-prompt', await_text='Internet ist ausgeschaltet')
        self.press('web-select-ask', 'Auf Nachfrage')
        self.take('web-modes', await_text=QUICK)
        assert mode_value(self.private('web-mode-ask', PREFS_PATH)) == 'ASK'
        self.press('web-select-quick', QUICK)
        self.take('web-topic', await_text='Device AI · Webrecherche')
        editors = [n for n in self.nodes if n.get('class') == 'android.widget.EditText' and visible(n, self.display)]
        assert len(editors) == 1 and editors[0].get('text', '') in ('', TOPIC_HINT)
        self.api['tap_node'](editors[0], self.display)
        result = self.api['captured_device']('web-type-url', 'shell', 'input', 'text', shlex.quote(URL))
        assert result.returncode == 0 and not result.stdout and not result.stderr
        keyboard = self.api['shell']('dumpsys', 'input_method')
        self.api['save']('web-typing-input-method.txt', keyboard)
        if self.api['keyboard_visible'](keyboard):
            self.api['shell']('input', 'keyevent', 'KEYCODE_BACK')
        self.take('web-topic-input', await_text=URL)
        self.press('web-search', 'JETZT SUCHEN')
        self.take('web-approval', await_text='Internetzugriff einmal erlauben?')
        assert self.private('web-before-dossier', DOSSIER_PATH) is None
        baseline = {r['call_id'] for r in decode_receipts(self.private('web-before-receipts', RECEIPT_PATH))}
        network = self.api['captured_device']('web-before-network', 'shell', 'dumpsys', 'connectivity')
        assert network.returncode == 0 and not network.stderr
        assert re.search(r'(?:Active default network:|mActiveDefaultNetwork=)\s*(?:none|null)(?:\s|$)', network.stdout.decode(), re.I)
        self.press('web-allow-once', 'Einmal erlauben')
        deadline = time.monotonic() + 18
        while True:
            receipts = decode_receipts(self.private('web-after-receipts', RECEIPT_PATH))
            added = [r for r in receipts if r['call_id'] not in baseline]
            payload = self.private('web-after-dossier', DOSSIER_PATH)
            if payload is not None and len(added) == 1 and added[0]['transitions'][-1][0] == 'SUCCEEDED':
                dossier = decode_empty_dossier(payload)
                break
            assert time.monotonic() < deadline, 'Offline research did not finish within its tool budget'
            time.sleep(0.1)
        self.take('web-result', await_text=result_body(dossier))
        network = self.api['captured_device']('web-after-network', 'shell', 'dumpsys', 'connectivity')
        assert network.returncode == 0
        self.press('web-home-final', 'Zur Startseite mit allen KI-Bereichen')
        self.take('web-home-final')
        self.settings_off('final')
        self.press('web-return-chat', 'Konversation öffnen')
        self.take('web-chat-restored')
        self.api['report']['offline_web'] = verify(self.api['out'], self.api['report'], self.api['source'],
                                                   self.api['report']['source_tree'])
        return self.nodes
