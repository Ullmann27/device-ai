"""Exercise evidence rejection without signing."""
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

import code59_signing_evidence as proof


class SigningEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def suite(self, cases, **counters):
        suite = ET.Element('testsuite', name='fixture', tests=str(len(cases)),
                           failures='0', errors='0', skipped='0')
        suite.attrib.update({key: str(value) for key, value in counters.items()})
        for name, outcome in cases:
            case = ET.SubElement(suite, 'testcase', classname='fixture', name=name)
            if outcome:
                ET.SubElement(case, outcome)
        ET.ElementTree(suite).write(self.directory / 'TEST-fixture.xml', encoding='utf-8')

    def test_actual_case_inventory_is_required(self):
        self.suite([('first', None), ('second', None)])
        result = proof.junit(self.directory, 2, required=[('fixture', 'second')])
        self.assertEqual((2, 2, 0), (result['tests'], result['executed'], result['skipped']))
        with self.assertRaisesRegex(ValueError, 'regression coverage'):
            proof.junit(self.directory, 2, required=[('fixture', 'missing')])

    def test_false_zero_failure_counter_does_not_hide_failure(self):
        self.suite([('broken', 'failure')])
        with self.assertRaisesRegex(ValueError, 'JUnit failed'):
            proof.junit(self.directory, 1)

    def test_duplicate_case_cannot_inflate_coverage(self):
        self.suite([('same', None), ('same', None)])
        with self.assertRaisesRegex(ValueError, 'Duplicate JUnit'):
            proof.junit(self.directory, 2)

    def test_truncated_case_inventory_is_rejected(self):
        self.suite([('only', None)], tests=100)
        with self.assertRaisesRegex(ValueError, 'Incomplete JUnit'):
            proof.junit(self.directory, 1)

    def test_unknown_skip_is_rejected(self):
        self.suite([('passed', None), ('omitted', 'skipped')], skipped=1)
        with self.assertRaisesRegex(ValueError, 'Unapproved skipped'):
            proof.junit(self.directory, 1, skipped=True)

    def test_declared_skip_never_satisfies_required_case(self):
        skipped = ('at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest',
                   'actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations')
        self.suite([('passed', None), ('omitted', 'skipped')], skipped=1)
        file = self.directory / 'TEST-fixture.xml'
        xml = ET.parse(file)
        xml.getroot().findall('testcase')[1].attrib.update(classname=skipped[0], name=skipped[1])
        xml.write(file)
        self.assertEqual(1, proof.junit(self.directory, 1, skipped=True)['skipped'])
        with self.assertRaisesRegex(ValueError, 'regression coverage'):
            proof.junit(self.directory, 1, skipped=True, required=[skipped])

    def test_duplicate_and_nonfinite_json_are_rejected(self):
        file = self.directory / 'report.json'
        for text in ['{"status":"FAIL","status":"PASS"}', '{"value":NaN}', '{"value":Infinity}']:
            file.write_text(text)
            with self.subTest(text=text), self.assertRaises(ValueError):
                proof.read_json(file)

    def test_ambiguous_proof_is_rejected(self):
        (self.directory / 'RESULT.json').write_text('{}')
        (self.directory / 'nested').mkdir()
        (self.directory / 'nested/RESULT.json').write_text('{}')
        with self.assertRaisesRegex(ValueError, 'ambiguous'):
            proof.one(self.directory, 'RESULT.json')

    def test_failed_native_run_never_reaches_apk_signing(self):
        for component in ['hologram', 'routines', 'app']:
            exits = {'source_sha': 'a' * 40, 'hologram': 0, 'routines': 0, 'app': 0}
            exits[component] = 1
            (self.directory / 'probe-exits.json').write_text(json.dumps(exits))
            with self.subTest(component=component), self.assertRaisesRegex(ValueError, 'Native probe failed'):
                proof.native(self.directory, 'a' * 40, 'b' * 40)

    def test_completed_native_probe_still_requires_source_binding(self):
        (self.directory / 'source-sha.txt').write_text('c' * 40)
        (self.directory / 'source-tree.txt').write_text('b' * 40)
        file = self.directory / 'native-pdf.txt'
        file.write_text('INSTRUMENTATION_RESULT: status=ok\nINSTRUMENTATION_CODE: -1\n')
        with self.assertRaisesRegex(ValueError, 'Native source mismatch'):
            proof.instrumentation(file, 'a' * 40, 'b' * 40)

    def test_native_success_word_without_completion_is_rejected(self):
        (self.directory / 'source-sha.txt').write_text('a' * 40)
        (self.directory / 'source-tree.txt').write_text('b' * 40)
        file = self.directory / 'native-pdf.txt'
        file.write_text('INSTRUMENTATION_RESULT: status=ok\n')
        with self.assertRaisesRegex(ValueError, 'did not complete'):
            proof.instrumentation(file, 'a' * 40, 'b' * 40)
