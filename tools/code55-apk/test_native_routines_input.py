"""Reject altered routine input."""
import json
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import native_routines as proof


class RoutineInputTest(unittest.TestCase):
    def setUp(self):
        self.calls, self.saved, self.taps = [], {}, []
        self.result = SimpleNamespace(returncode=0, stderr=b'')
        self.editor = {'class': 'android.widget.EditText', 'text': '', 'focused': 'true'}
        self.actual = 'Routinen'
        self.api = {'save': self.saved.__setitem__, 'captured_device': self.capture,
                    'visible_bounds': lambda *args: (0, 0, 10, 10),
                    'shell': lambda *args: '', 'keyboard_visible': lambda text: False,
                    'snapshot': self.snapshot, 'tap': self.tap}
        self.probe = proof.Probe(self.api, [self.editor], '1', (100, 200))

    def capture(self, label, *arguments):
        self.calls.append((label, arguments))
        return self.result

    def snapshot(self, label, **options):
        return [{**self.editor, 'text': self.actual}], '1', (100, 200)

    def tap(self, nodes, labels, display):
        self.taps.append(labels)
        return True

    def test_characters_are_ordered_and_recorded(self):
        with patch.object(proof.time, 'sleep') as pause:
            self.probe.type_text('command', 'R a;')
        self.assertEqual(['R', '%s', 'a', "';'"], [call[1][-1] for call in self.calls])
        self.assertEqual(3, pause.call_count)
        self.assertTrue(all(call.args == (.05,) for call in pause.call_args_list))
        self.assertEqual('R a;', json.loads(self.saved['command-input-plan.json'])['text'])

    def test_injection_failure_stops_remaining_input(self):
        self.result = SimpleNamespace(returncode=1, stderr=b'failed')
        with self.assertRaisesRegex(AssertionError, 'Character injection failed'):
            self.probe.type_text('command', 'Routinen')
        self.assertEqual(1, len(self.calls))
        self.assertEqual([], self.taps)

    def test_unsupported_input_is_rejected_before_device_calls(self):
        for text in ['', 'a' * 161, 'a\nb', 'a%s', 'ä']:
            with self.subTest(text=text), self.assertRaises(AssertionError):
                self.probe.type_text('command', text)
        self.assertEqual([], self.calls)
        self.assertEqual({}, self.saved)

    def test_exact_observed_text_is_sent_once(self):
        with patch.object(proof.time, 'sleep'):
            self.probe.send('command', 'Routinen')
        self.assertEqual(8, len(self.calls))
        self.assertEqual([['Nachricht senden', 'Routinenbefehl senden']], self.taps)

    def test_recorded_character_reordering_never_sends(self):
        self.actual = 'Rioutnen'
        with patch.object(proof.time, 'sleep'), self.assertRaisesRegex(AssertionError, 'nothing sent'):
            self.probe.send('command', 'Routinen')
        self.assertEqual([], self.taps)

    def test_existing_draft_is_not_overwritten(self):
        self.editor['text'] = 'owner draft'
        with self.assertRaises(AssertionError):
            self.probe.send('command', 'Routinen')
        self.assertEqual([], self.calls)
        self.assertEqual([], self.taps)
