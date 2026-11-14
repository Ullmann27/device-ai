"""Reject incomplete routine proof."""
import base64
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zlib

import native_routines as proof


class RoutineEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        fixture = json.loads(Path(__file__).with_name('native-routines-fixture.json').read_text())
        self.payloads = {key: base64.b64decode(value) for key, value in fixture['payloads'].items()}
        self.expected = fixture['expected']
        self.report = {'mode': 'routines', 'package': proof.PACKAGE, 'sdk': 35, 'abi': 'x86_64',
                       'source_sha': 'a' * 40, 'source_tree': 'b' * 40}

    def raw(self, label, data, code=0, error=b''):
        (self.directory / (label + '-command.json')).write_text(json.dumps({'returncode': code}))
        (self.directory / (label + '-stdout.txt')).write_bytes(data or b'')
        (self.directory / (label + '-stderr.txt')).write_bytes(error)

    def ui(self, label, texts, pid='20', rotation='0'):
        root = ET.Element('hierarchy', rotation=rotation)
        for text in texts:
            ET.SubElement(root, 'node', {'package': proof.PACKAGE, 'text': text})
        xml = ET.tostring(root, encoding='utf-8')
        def chunk(kind, payload):
            return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 200, 400, 8, 6, 0, 0, 0))
        png += chunk(b'tEXt', b'fixture\0' + b'x' * 4100)
        png += chunk(b'IDAT', zlib.compress((b'\0' + b'\0\0\0\xff' * 200) * 400)) + chunk(b'IEND', b'')
        window = {'status': 'ok', 'elapsed_ms': '40', 'helper_package': proof.PACKAGE + '.test',
                  'helper_pid': '99', 'rotation': rotation, 'hierarchy_base64': base64.b64encode(xml).decode(),
                  'screenshot_sha': hashlib.sha256(png).hexdigest(), 'screenshot_width': '200',
                  'screenshot_height': '400', 'node_count': str(len(texts)), 'window_id': '8',
                  'windows': '[{"display":0,"focused":true,"type":1,"id":8}]',
                  'windows_after': '[{"display":0,"focused":true,"type":1,"id":8}]',
                  'capture_started_ms': '1', 'hierarchy_finished_ms': '10',
                  'screenshot_finished_ms': '20', 'capture_finished_ms': '30'}
        raw = ''.join(f'INSTRUMENTATION_RESULT: {key}={value}\n' for key, value in window.items()) + 'INSTRUMENTATION_CODE: -1\n'
        self.raw(label + '-ui-automation', raw.encode())
        window.update(app_pid_before=pid, app_pid_after=pid, host_started_unix_ns=10, host_finished_unix_ns=20)
        (self.directory / (label + '-window.json')).write_text(json.dumps(window))
        (self.directory / (label + '.xml')).write_bytes(xml)
        (self.directory / (label + '.png')).write_bytes(png)

    def complete(self):
        e = self.expected
        for label, fixture in [('approved', 'approved'), ('rotated', 'approved'), ('after-start', 'completed'),
                               ('stopped', 'empty'), ('stopped-restart', 'empty'), ('blocked', 'blocked'), ('final', 'empty')]:
            self.raw(label + '-routines', self.payloads[fixture])
            self.raw(label + '-receipts', self.payloads['receipts'] if label not in ('approved', 'rotated') else None,
                     0 if label not in ('approved', 'rotated') else 44)
        self.ui('battery-confirmation', ['Routine dauerhaft freigeben?', 'DAUERHAFT FREIGEBEN', 'Prüfe Akku\nAkku prüfen'])
        self.ui('battery-approved', ['approved'], pid='10')
        self.ui('routine-rotation', ['rotated'], pid='10', rotation='1')
        self.ui('routine-process-start', ['started'], pid='20')
        body = f"Routine {e['battery_id']}:\n{e['result']}"
        blocked = f"Routine {e['downloads_id']} ist blockiert: {e['blocked']}"
        for label, text, at in [('after-start', body, 2000), ('blocked', blocked, 4000)]:
            self.ui(label, [text], pid='20' if label == 'after-start' else '30')
            self.raw(label + '-conversation', b'44444444-4444-4444-8444-444444444444')
            self.raw(label + '-chat', (json.dumps({'u': False, 't': text, 'at': at}, ensure_ascii=False) + '\n').encode())
        self.ui('battery-stopped', [f"Routine {e['battery_id']} gestoppt und Freigabe entfernt."])
        self.ui('routine-stopped-restart', ['restarted'], pid='30')
        self.ui('downloads-confirmation', ['Routine dauerhaft freigeben?', 'DAUERHAFT FREIGEBEN',
                'Prüfe Downloads /sdcard/Download; bestehende Shizuku-Freigabe nötig'])
        self.ui('routines-stopped', ['Routinen gestoppt und Freigaben entfernt: 1.'], pid='30')
        self.raw('schedule-state', b'waiting\n')
        service = proof.PACKAGE + '/at.heinz.deviceai.routines.OwnerRoutineJobService'
        self.raw('schedule-dump', f'  JOB #u0a209/620159: test\n    Service: {service}\n    PERIODIC: interval=+15m0s0ms flex=+15m0s0ms\n    PERSISTED\n'.encode())
        self.raw('schedule-after-stop', b'', 23,
                 f'Could not find job 620159 in package {proof.PACKAGE} / user 0\n'.encode())

    def verify(self):
        return proof.verify(self.directory, self.report, 'a' * 40, 'b' * 40)

    def test_original_kotlin_store_bytes_decode(self):
        row = proof.single(proof.decode_state(self.payloads['approved']))
        self.assertEqual(self.expected['battery_id'], row['id'])
        receipt = proof.decode_receipts(self.payloads['receipts'])[0]
        self.assertEqual(self.expected['call_id'], receipt['call_id'])
        self.assertEqual(hashlib.sha256(self.expected['output'].encode()).hexdigest(), receipt['output_sha256'])

    def test_complete_synthetic_contract_keeps_periodic_worker_unproved(self):
        self.complete()
        result = self.verify()
        self.assertEqual('PASS', result['app_start_battery_worker'])
        self.assertEqual('NOT_RUN', result['periodic_due_worker_execution'])

    def test_corrupt_or_extended_store_is_rejected(self):
        original = self.payloads['approved']
        for invalid in (original[:-1], original + b'junk', original[:30] + bytes([original[30] ^ 1]) + original[31:]):
            with self.subTest(size=len(invalid)), self.assertRaises(AssertionError):
                proof.decode_state(invalid)

    def test_valid_checksum_cannot_change_approved_goal(self):
        original = self.payloads['approved']
        data = original[len(proof.STATE_MAGIC) + 32:].replace(b'Akku', b'Apps')
        invalid = proof.STATE_MAGIC + hashlib.sha256(data).digest() + data
        with self.assertRaisesRegex(AssertionError, 'Approval scope'):
            proof.decode_state(invalid)

    def test_missing_file_error_text_never_becomes_empty_state(self):
        self.raw('broken', b'cat: no such file\n', 0)
        with self.assertRaises(AssertionError):
            proof.decode_state(proof.evidence_bytes(self.directory, 'broken'))
        self.raw('broken', b'failure', 44)
        with self.assertRaises(AssertionError):
            proof.evidence_bytes(self.directory, 'broken')

    def test_rotation_cannot_execute_first_worker(self):
        self.complete()
        self.raw('rotated-receipts', self.payloads['receipts'])
        with self.assertRaises(AssertionError):
            self.verify()

    def test_duplicate_or_wrong_chat_destination_is_rejected(self):
        self.complete()
        path = self.directory / 'after-start-chat-stdout.txt'
        path.write_bytes(path.read_bytes() * 2)
        with self.assertRaisesRegex(AssertionError, 'exactly once'):
            self.verify()
        self.complete()
        self.raw('blocked-conversation', b'55555555-5555-4555-8555-555555555555')
        with self.assertRaises(AssertionError):
            self.verify()

    def test_restart_requires_actual_new_process(self):
        self.complete()
        self.ui('routine-process-start', ['started'], pid='10')
        with self.assertRaises(AssertionError):
            self.verify()

    def test_stop_must_remove_persistent_authorization(self):
        self.complete()
        self.raw('stopped-restart-routines', self.payloads['approved'])
        with self.assertRaises(AssertionError):
            self.verify()

    def test_periodic_registration_and_cancellation_are_both_required(self):
        self.complete()
        self.raw('schedule-state', b'Could not find job', 23)
        with self.assertRaisesRegex(AssertionError, 'registration'):
            self.verify()
        self.complete()
        self.raw('schedule-after-stop', b'waiting\n')
        with self.assertRaises(AssertionError):
            self.verify()

    def test_derived_window_metadata_cannot_replace_original_stdout(self):
        self.complete()
        self.raw('after-start-ui-automation', b'INSTRUMENTATION_CODE: -1\n')
        with self.assertRaises(AssertionError):
            self.verify()

    def test_exact_source_binding_is_required(self):
        self.complete()
        self.report['source_sha'] = 'c' * 40
        with self.assertRaises(AssertionError):
            self.verify()

    def test_modified_utf8_preserves_nul_and_supplementary_character(self):
        original = 'Akku\0\U0001f50b'
        reader = proof.Reader(proof.mutf(original))
        self.assertEqual(original, reader.utf())
        reader.end()


if __name__ == '__main__':
    unittest.main()
