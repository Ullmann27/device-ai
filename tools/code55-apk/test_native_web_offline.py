"""Synthetic evidence must preserve actual contracts."""
import base64
import hashlib
import json
from pathlib import Path
import shlex
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET

import native_web_offline as proof
import test_native_routines


class OfflineWebEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        fixture = json.loads(Path(__file__).with_name('native-offline-web-fixture.json').read_text())
        self.payloads = {name: base64.b64decode(data) for name, data in fixture['payloads'].items()}
        self.report = {'package': proof.PACKAGE, 'sdk': 35, 'abi': 'x86_64',
                       'source_sha': 'a' * 40, 'source_tree': 'b' * 40, 'serial': 'emulator-5554',
                       'checks': [{'name': 'fresh-empty-no-permissions', 'pid': '100'}]}
        self.helper = test_native_routines.RoutineEvidenceTest()
        self.helper.directory, self.helper.report = self.directory, self.report
        self.dossier = proof.decode_empty_dossier(self.payloads['dossier'])
        self.complete()

    def raw(self, label, data, args, code=0):
        self.helper.raw(label, data, code=code, arguments=args)

    def private(self, label, path, data):
        args, _ = proof.private_args(path)
        self.raw(label, data, args, code=44 if data is None else 0)

    def ui(self, label, items):
        self.helper.ui(label, [item.get('text', '') for item in items], pid='100')
        root = ET.parse(self.directory / (label + '.xml')).getroot()
        for index, (node, values) in enumerate(zip(root.iter('node'), items)):
            node.attrib.update({'class': 'android.widget.TextView', 'enabled': 'true', 'clickable': 'true',
                                'bounds': f'[10,{10 + index * 50}][190,{50 + index * 50}]', **values})
        self.replace_ui(label, root)

    def replace_ui(self, label, root):
        xml = ET.tostring(root, encoding='utf-8')
        path = self.directory / (label + '-window.json')
        window = json.loads(path.read_text())
        window['hierarchy_base64'] = base64.b64encode(xml).decode()
        window['node_count'] = str(len(list(root.iter('node'))))
        path.write_text(json.dumps(window))
        (self.directory / (label + '.xml')).write_bytes(xml)
        fields = {key: value for key, value in window.items() if key not in
                  {'host_started_unix_ns', 'host_finished_unix_ns', 'app_pid_before', 'app_pid_after'}}
        raw = ''.join(f'INSTRUMENTATION_RESULT: {key}={value}\n' for key, value in fields.items())
        self.raw(label + '-ui-automation', (raw + 'INSTRUMENTATION_CODE: -1\n').encode(),
                 ['shell', 'am', 'instrument', '-w', '-r', proof.PACKAGE + '.test/at.heinz.deviceai.ContinuumWindowSnapshotInstrumentation'])

    def mutate_ui(self, label, transform):
        root = ET.parse(self.directory / (label + '.xml')).getroot()
        transform(list(root.iter('node')))
        self.replace_ui(label, root)

    def complete(self):
        for path in self.directory.iterdir():
            path.unlink()
        text = lambda value: {'text': value}
        control = lambda value: {'content-desc': value}
        editor = {'class': 'android.widget.EditText', 'text': '', 'focused': 'false'}
        scenes = {
            'web-home-initial': [control('Internet')],
            'web-center-initial': [text('INTERNETFREIGABE')],
            'web-settings-initial': [text('Continuum · Internetzugang'), text(proof.OFF)],
            'web-off-initial': [control('Konversation öffnen')],
            'web-chat-before': [control('Recherche nur nach Freigabe')],
            'web-off-prompt': [text('Internet ist ausgeschaltet'), text('AUF NACHFRAGE')],
            'web-modes': [text(proof.QUICK)],
            'web-topic': [text('Device AI · Webrecherche'), editor],
            'web-topic-input': [{**editor, 'text': proof.URL}, text('JETZT SUCHEN')],
            'web-approval': [text('Internetzugriff einmal erlauben?'),
                text('Diese eine HTTPS-Quelle wird gelesen; sichere öffentliche Weiterleitungen sind begrenzt erlaubt.\n\n' + proof.URL),
                text('EINMAL ERLAUBEN')],
            'web-result': [text(proof.result_body(self.dossier)), editor, control('Nachricht senden'),
                           control('Zur Startseite mit allen KI-Bereichen')],
            'web-home-final': [control('Internet')],
            'web-center-final': [text('INTERNETFREIGABE')],
            'web-settings-final': [text('Continuum · Internetzugang'), text(proof.OFF)],
            'web-off-final': [control('Konversation öffnen')],
            'web-chat-restored': [editor, control('Nachricht senden')]}
        for label, items in scenes.items():
            self.ui(label, items)
        actions = [
            ('web-open-center-initial', 'Internet', 'web-home-initial'),
            ('web-open-settings-initial', 'INTERNETFREIGABE', 'web-center-initial'),
            ('web-select-off-initial', proof.OFF, 'web-settings-initial'),
            ('web-open', 'Recherche nur nach Freigabe', 'web-chat-before'),
            ('web-select-ask', 'Auf Nachfrage', 'web-off-prompt'),
            ('web-select-quick', proof.QUICK, 'web-modes'),
            ('web-search', 'JETZT SUCHEN', 'web-topic-input'),
            ('web-allow-once', 'Einmal erlauben', 'web-approval'),
            ('web-home-final', 'Zur Startseite mit allen KI-Bereichen', 'web-result'),
            ('web-open-center-final', 'Internet', 'web-home-final'),
            ('web-open-settings-final', 'INTERNETFREIGABE', 'web-center-final'),
            ('web-select-off-final', proof.OFF, 'web-settings-final'),
            ('web-return-chat', 'Konversation öffnen', 'web-off-final')]
        observation = {'source_sha': self.report['source_sha'], 'source_tree': self.report['source_tree'],
                       'url': proof.URL, 'test_basis': 'OFF_SET_THROUGH_UI', 'original_mode': 'ALWAYS_ON',
                       'captures': list(scenes), 'actions': []}
        for name, label, snapshot in actions:
            node = next(n for n in ET.parse(self.directory / (snapshot + '.xml')).iter('node')
                        if label.casefold() in (n.get('text', '').casefold(), n.get('content-desc', '').casefold()))
            x1, y1, x2, y2 = proof.visible(node, (200, 400))
            self.raw(name, b'', ['shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)])
            observation['actions'].append({'name': name, 'control': label, 'snapshot': snapshot})
        (self.directory / 'web-observation.json').write_text(json.dumps(observation))
        for label in ['web-before-network', 'web-after-network']:
            self.raw(label, b'Active default network: none\n', ['shell', 'dumpsys', 'connectivity'])
        for label, mode in [('web-mode-original', 'ALWAYS_ON'), ('web-mode-off', 'OFF'),
                            ('web-mode-ask', 'ASK'), ('web-mode-final', 'OFF')]:
            self.private(label, proof.PREFS_PATH, f'<map><string name="mode">{mode}</string></map>'.encode())
        self.private('web-before-dossier', proof.DOSSIER_PATH, None)
        self.private('web-before-receipts', proof.RECEIPT_PATH, None)
        self.private('web-after-dossier', proof.DOSSIER_PATH, self.payloads['dossier'])
        self.private('web-after-receipts', proof.RECEIPT_PATH, self.payloads['receipts'])
        self.raw('web-type-url', b'', ['shell', 'input', 'text', shlex.quote(proof.URL)])

    def verify(self):
        return proof.verify(self.directory, self.report, self.report['source_sha'], self.report['source_tree'])

    def reject(self):
        with self.assertRaises((AssertionError, ValueError, KeyError, FileNotFoundError)):
            self.verify()

    def rewrite_payload(self, label, transform):
        path = self.directory / (label + '-stdout.txt')
        data = path.read_bytes()
        reader = proof.Reader(data)
        reader.utf(); reader.number('i')
        count_offset = reader.position
        size = reader.number('i')
        body = transform(reader.take(size))
        path.write_bytes(data[:count_offset] + struct.pack('>i', len(body)) + body + hashlib.sha256(body).digest())

    def test_real_store_fixture_requires_one_time_consent_and_honest_partial_result(self):
        result = self.verify()
        self.assertEqual('PASS', result['status'])
        self.assertEqual('SUCCEEDED', result['receipt_terminal'])
        self.assertEqual('PARTIAL_TRANSPORT_FAILURE', result['network_result'])
        self.assertEqual('ALWAYS_ON', result['original_mode'])
        self.assertEqual('OFF', result['final_mode'])

    def test_active_default_network_is_rejected(self):
        (self.directory / 'web-before-network-stdout.txt').write_text('Active default network: 100\n')
        self.reject()

    def test_missing_exact_owner_consent_is_rejected(self):
        path = self.directory / 'web-observation.json'
        value = json.loads(path.read_text())
        value['actions'] = [a for a in value['actions'] if a['name'] != 'web-allow-once']
        path.write_text(json.dumps(value))
        self.reject()

    def test_confirmation_for_another_url_is_rejected(self):
        self.mutate_ui('web-approval', lambda nodes: nodes[1].set('text', nodes[1].get('text').replace(proof.URL, 'https://example.org/')))
        self.reject()

    def test_offscreen_confirmation_is_rejected(self):
        self.mutate_ui('web-approval', lambda nodes: nodes[2].set('bounds', '[0,500][100,550]'))
        self.reject()

    def test_existing_receipt_cannot_replace_new_dispatch(self):
        self.private('web-before-receipts', proof.RECEIPT_PATH, self.payloads['receipts'])
        self.reject()

    def test_output_fingerprint_must_match_actual_dossier(self):
        receipt = proof.decode_receipts(self.payloads['receipts'])[0]
        self.rewrite_payload('web-after-receipts', lambda data: data.replace(receipt['output_sha256'].encode(), b'0' * 64))
        self.reject()

    def test_receipt_argument_fingerprint_requires_exact_url(self):
        receipt = proof.decode_receipts(self.payloads['receipts'])[0]
        other = hashlib.sha256(('url=string:' + hashlib.sha256(b'https://example.org/').hexdigest()).encode()).hexdigest()
        self.rewrite_payload('web-after-receipts', lambda data: data.replace(receipt['fingerprint'].encode(), other.encode()))
        self.reject()

    def test_committed_dispatch_is_required(self):
        self.rewrite_payload('web-after-receipts', lambda data: data.replace(b'DISPATCH_COMMITTED', b'DISPATCH_COMM1TTED'))
        self.reject()

    def test_corrupt_dossier_checksum_is_rejected(self):
        path = self.directory / 'web-after-dossier-stdout.txt'
        data = path.read_bytes()
        path.write_bytes(data[:-1] + bytes([data[-1] ^ 1]))
        self.reject()

    def test_complete_phase_is_not_an_offline_partial_result(self):
        marker = struct.pack('>iiiii', 9, 0, 0, 0, 0)
        def changed(data):
            self.assertEqual(1, data.count(marker))
            return data.replace(marker, struct.pack('>iiiii', 8, 0, 0, 0, 0))
        self.rewrite_payload('web-after-dossier', changed)
        self.reject()

    def test_preexisting_dossier_is_not_a_new_research_attempt(self):
        self.private('web-before-dossier', proof.DOSSIER_PATH, self.payloads['dossier'])
        self.reject()

    def test_unsuitable_provider_error_does_not_prove_transport_failure(self):
        before = b'Research host could not be resolved.'
        after = b'Search provider is not configured.  '
        self.assertEqual(len(before), len(after))
        self.rewrite_payload('web-after-dossier', lambda data: data.replace(before, after))
        self.reject()

    def test_honest_ui_result_is_required(self):
        self.mutate_ui('web-result', lambda nodes: nodes[0].set('text', 'Die Webseite wurde erfolgreich gelesen.'))
        self.reject()

    def test_busy_composer_is_rejected(self):
        self.mutate_ui('web-result', lambda nodes: nodes[2].set('content-desc', 'Device AI arbeitet'))
        self.reject()

    def test_final_off_must_be_saved_after_ui_action(self):
        self.private('web-mode-final', proof.PREFS_PATH, b'<map><string name="mode">ASK</string></map>')
        self.reject()

    def test_evidence_from_another_source_is_rejected(self):
        path = self.directory / 'web-observation.json'
        value = json.loads(path.read_text()); value['source_sha'] = 'c' * 40
        path.write_text(json.dumps(value))
        self.reject()


if __name__ == '__main__':
    unittest.main()
