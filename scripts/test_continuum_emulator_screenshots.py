"""Offline negative fixtures for the emulator acceptance gate; no adb/device required."""
import importlib.util
from pathlib import Path
import sys
import os
import subprocess
import textwrap
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('continuum-emulator-screenshots.py')
with patch.object(sys, 'argv', [str(SCRIPT), 'fixture.apk', '/tmp/fixture']):
    spec = importlib.util.spec_from_file_location('screenshots', SCRIPT)
    qa = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(qa)


def fixture(text='', desc='', cls='android.view.View', clickable=False, focused=False, package=None):
    return '<hierarchy><node package="%s" text="%s" content-desc="%s" class="%s" clickable="%s" focused="%s" enabled="true" bounds="[0,0][200,200]" /></hierarchy>' % (
        package or qa.PKG, text, desc, cls, str(clickable).lower(), str(focused).lower())


class AcceptanceTests(unittest.TestCase):
    def test_default_is_isolated_preview_with_real_activity_namespace(self):
        self.assertEqual(qa.PKG, 'at.heinz.deviceai.featurepreview')
        self.assertEqual(qa.ACT, 'at.heinz.deviceai.AgentActivity')

    def test_malformed_dump_must_fail_instead_of_reporting_zero_clickables(self):
        with self.assertRaises((ValueError, RuntimeError)):
            qa.nodes('UI hierchary dumped to: /sdcard/ui.xml')

    def test_target_parser_preserves_enabled_and_focused_state(self):
        node = qa.nodes(fixture(cls='android.widget.EditText', focused=True))[0]
        self.assertTrue(node.get('focused'), 'Chat proof needs actual focused editor state')
        self.assertTrue(node.get('enabled'), 'Disabled nodes must never be tapped')

    def test_home_screen_is_not_proof_of_learning_navigation(self):
        self.assertFalse(qa.destination_reached('lernen', qa.nodes(fixture(desc=qa.HOME_DESCRIPTION))))

    def test_learning_requires_exact_destination_heading(self):
        self.assertTrue(qa.destination_reached('lernen', qa.nodes(fixture(text='Lernzentrum'))))
        self.assertFalse(qa.destination_reached('lernen', qa.nodes(fixture(desc='Lernen – Lernzentrum und Trainingsstand öffnen'))))

    def test_chat_requires_focused_editor_not_send_button(self):
        self.assertFalse(qa.destination_reached('chat', qa.nodes(fixture(desc='Nachricht senden'))))
        self.assertFalse(qa.destination_reached('chat', qa.nodes(fixture(cls='android.widget.EditText'))))
        self.assertTrue(qa.destination_reached('chat', qa.nodes(fixture(cls='android.widget.EditText', focused=True))))

    def test_other_package_cannot_supply_destination_proof(self):
        self.assertFalse(qa.destination_reached('lernen', qa.nodes(fixture(text='Lernzentrum', package='other.app'))))

    def test_unknown_or_ambiguous_clickable_is_never_tapped(self):
        danger = qa.nodes(fixture(desc='TEAMLEITER · JETZT PRÜFEN', clickable=True))
        with self.assertRaises(RuntimeError):
            qa.navigation_target(danger, 'lernen')
        target = qa.nodes(fixture(desc='Lernen', clickable=True))
        with self.assertRaises(RuntimeError):
            qa.navigation_target(target + target, 'lernen')

    def test_phone_and_fold_labels_resolve_to_same_safe_navigation(self):
        for label in ('Lernen', 'Lernen – Lernzentrum und Trainingsstand öffnen'):
            self.assertEqual(qa.navigation_target(qa.nodes(fixture(desc=label, clickable=True)), 'lernen')['desc'], label)

    def test_real_phone_or_wrong_apk_is_rejected_before_install(self):
        with patch.object(qa, 'sh', return_value='0\n'):
            with self.assertRaises(RuntimeError):
                qa.require_emulator()
        with self.assertRaises(RuntimeError):
            qa.validate_apk_badging("package: name='at.heinz.deviceai' versionCode='1'")
        qa.validate_apk_badging("package: name='at.heinz.deviceai.featurepreview' versionCode='9'")

    def test_workflow_gate_rejects_each_failed_skipped_or_cancelled_check(self):
        workflow = SCRIPT.parent.parent / '.github/workflows/continuum-ui-screenshots.yml'
        gate = workflow.read_text().split('      - name: Required UI acceptance gate', 1)[1]
        command = textwrap.dedent(gate.split('        run: |', 1)[1])
        good = dict(os.environ, **{key: 'success' for key in ('QA', 'NODE', 'UNIT', 'LINT', 'APK', 'VERIFY', 'SHOTS')})
        self.assertEqual(subprocess.run(['bash', '-c', command], env=good, capture_output=True).returncode, 0)
        for key in ('QA', 'NODE', 'UNIT', 'LINT', 'APK', 'VERIFY', 'SHOTS'):
            for outcome in ('failure', 'skipped', 'cancelled'):
                with self.subTest(check=key, outcome=outcome):
                    result = subprocess.run(['bash', '-c', command], env=dict(good, **{key: outcome}), capture_output=True)
                    self.assertNotEqual(result.returncode, 0)

    def test_required_apk_verification_rejects_bad_package_invalid_signature_and_missing_evidence(self):
        workflow = SCRIPT.parent.parent / '.github/workflows/continuum-ui-screenshots.yml'
        text = workflow.read_text()
        self.assertIn('      - name: Verify preview APK package and signature', text)
        step = text.split('      - name: Verify preview APK package and signature', 1)[1].split('      - name:', 1)[0]
        command = textwrap.dedent(step.split('        run: |', 1)[1])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            build_tools = root / 'sdk/build-tools/35.0.0'
            build_tools.mkdir(parents=True)
            apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b'fixture only, no real APK')
            for tool, variable in (('aapt', 'QA_BADGING'), ('apksigner', 'QA_SIGNATURE')):
                executable = build_tools / tool
                executable.write_text(
                    '#!/bin/bash\n' + "printf '%s\\n' \"$" + variable + '\"\n' +
                    'exit "${' + variable + '_EXIT:-0}"\n')
                executable.chmod(0o700)
            good = dict(os.environ, SDK=str(root / 'sdk'), EVIDENCE=str(root / 'evidence'),
                        QA_BADGING="package: name='at.heinz.deviceai.featurepreview' versionCode='9'",
                        QA_SIGNATURE='Signer #1 certificate SHA-256 digest: ' + 'a' * 64)
            self.assertEqual(subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', command], cwd=root,
                                            env=good, capture_output=True).returncode, 0)
            for changes in ({'QA_BADGING': "package: name='at.heinz.deviceai' versionCode='4'"},
                            {'QA_BADGING_EXIT': '1'}, {'QA_SIGNATURE_EXIT': '1'}, {'QA_SIGNATURE': ''}):
                with self.subTest(changes=changes):
                    result = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', command], cwd=root,
                                            env=dict(good, **changes), capture_output=True)
                    self.assertNotEqual(result.returncode, 0)

    def test_all_profiles_include_130_percent(self):
        self.assertEqual(len(list(qa.samples())), 8)
        self.assertEqual({name for name, _, scale in qa.samples() if scale == '1.3'},
                         {'phone-font130', 'fold-cover-font130', 'fold-inner-font130', 'fold-inner-landscape-font130'})


if __name__ == '__main__':
    unittest.main()
