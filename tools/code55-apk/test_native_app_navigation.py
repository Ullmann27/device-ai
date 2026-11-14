"""Check navigation, not Android rendering."""
import ast
import base64
import hashlib
import json
from pathlib import Path
import re
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET
import zlib


class RecordedEvidenceEnded(Exception):
    pass


class AvatarNavigationTest(unittest.TestCase):
    def setUp(self):
        source = Path(__file__).with_name('native-app-smoke.sh').read_text().split("<<'PY'\n", 1)[1]
        parsed = ast.parse(source.rsplit('\nPY', 1)[0])
        names = {'visible_bounds', 'control', 'tap_node', 'tap', 'system_status_scroll',
                 'scroll_geometry', 'collect_avatar_diagnostic'}
        module = ast.Module(body=[node for node in parsed.body
                                  if isinstance(node, ast.FunctionDef) and node.name in names], type_ignores=[])
        fixture = json.loads(Path(__file__).with_name('native-app-navigation-fixture.json').read_text())
        self.original = {}
        for label, entry in fixture['xml'].items():
            raw = zlib.decompress(base64.b64decode(entry['zlib_base64'], validate=True))
            self.assertEqual(entry['sha256'], hashlib.sha256(raw).hexdigest())
            self.original[label] = raw
        self.display = (2400, 1080)
        self.pid = '8295'
        self.now = 0.0
        self.inputs = []
        self.taps = []
        self.snapshots = []
        self.responses = {}
        self.snapshot_seconds = 20
        self.input_exit = 0
        self.env = {'re': re, 'time': SimpleNamespace(monotonic=lambda: self.now, sleep=self.advance),
                    'snapshot': self.snapshot, 'captured_device': self.input,
                    'shell': self.shell, 'report': {}}
        exec(compile(module, '<actual-native-app-navigation>', 'exec'), self.env)

    def advance(self, seconds):
        self.now += seconds

    def nodes(self, label):
        return list(ET.fromstring(self.original[label]).iter('node'))

    def shell(self, *args, **kwargs):
        self.assertEqual(('input', 'tap'), args[:2])
        self.taps.append(tuple(map(int, args[2:])))

    def input(self, label, *args, **kwargs):
        self.assertEqual(('shell', 'input', 'swipe'), args[:3])
        self.inputs.append((label, tuple(map(int, args[3:]))))
        return SimpleNamespace(returncode=self.input_exit)

    def snapshot(self, label, **kwargs):
        self.snapshots.append(label)
        self.advance(self.snapshot_seconds)
        if label in self.responses:
            return self.responses[label]
        if label not in self.original:
            raise RecordedEvidenceEnded(label)
        return self.nodes(label), self.pid, self.display

    def navigate(self):
        return self.env['collect_avatar_diagnostic'](self.nodes('rotation-top'), self.pid, self.display)

    def node(self, parent, index, text='', **attributes):
        values = {'index': str(index), 'class': 'android.widget.TextView', 'text': text,
                  'resource-id': '', 'content-desc': '', 'enabled': 'true', 'clickable': 'false',
                  'bounds': '[1600,350][2200,450]'}
        values.update(attributes)
        return ET.SubElement(parent, 'node', values)

    def scene(self, row=0, text='Status', target=False):
        root = ET.Element('hierarchy', rotation='1')
        self.node(root, 0, **{'class': 'android.widget.ScrollView', 'scrollable': 'true',
            'content-desc': 'Continuum-Cockpit, vertikal scrollbar', 'bounds': '[136,74][2400,1017]'})
        scroll = self.node(root, 1, **{'class': 'android.widget.ScrollView', 'scrollable': 'true',
                                      'bounds': '[1519,232][2358,975]'})
        self.node(scroll, row, text)
        if target:
            self.node(scroll, row + 1, 'HOLOGRAMM · LIVE-FRAMES PRÜFEN', clickable='true',
                      bounds='[1600,500][2300,600]')
        return list(root.iter('node'))

    def respond(self, label, nodes, pid=None, display=None):
        self.responses[label] = (nodes, pid or self.pid, display or self.display)

    def dialog(self, title='Continuum · Hologramm-Gerätediagnose'):
        root = ET.Element('hierarchy', rotation='1')
        self.node(root, 0, title)
        self.node(root, 1, 'Animations-Callbacks: 10\nWirkliche Canvas-Zeichnungen: 9\n'
                          'Phasenänderung: 1\nStart: 1\nEnde: 2')
        self.node(root, 2, 'SCHLIESSEN', clickable='true', bounds='[2000,700][2300,800]')
        self.respond('avatar-diagnostic', list(root.iter('node')))
        self.respond('avatar-return', self.nodes('rotation-top'))

    def test_original_six_progressing_views_do_not_end_navigation(self):
        with self.assertRaisesRegex(RecordedEvidenceEnded, 'avatar-system-scroll-7'):
            self.navigate()
        self.assertEqual(7, len(self.inputs))
        self.assertTrue(all(gesture == (1938, 852, 1938, 355, 700) for _, gesture in self.inputs))
        self.assertNotIn('avatar_diagnostic', self.env['report'])

    def test_equal_geometry_with_later_child_identity_keeps_scrolling(self):
        self.respond('avatar-system-opened', self.scene(row=0))
        for step in range(1, 9):
            self.respond(f'avatar-system-scroll-{step}', self.scene(row=step, target=step == 8))
        self.dialog()
        self.navigate()
        self.assertEqual(8, len(self.inputs))
        self.assertEqual((1950, 550), self.taps[1])
        self.assertEqual((2150, 750), self.taps[2])
        result = self.env['report']['avatar_diagnostic']
        self.assertEqual('OBSERVED', result['status'])
        self.assertEqual(181000, result['navigation_elapsed_ms'])
        self.assertEqual(900000, result['navigation_deadline_ms'])

    def test_live_value_alone_does_not_count_as_scroll_progress(self):
        self.respond('avatar-system-opened', self.scene(text='Akku: 50 %'))
        self.respond('avatar-system-scroll-1', self.scene(text='Akku: 49 %'))
        with self.assertRaisesRegex(AssertionError, 'no progress or cycled'):
            self.navigate()
        self.assertEqual(1, len(self.inputs))
        self.assertEqual(1, len(self.taps))

    def test_return_to_previous_child_positions_is_rejected(self):
        self.respond('avatar-system-opened', self.scene(row=0))
        self.respond('avatar-system-scroll-1', self.scene(row=1))
        self.respond('avatar-system-scroll-2', self.scene(row=0))
        with self.assertRaisesRegex(AssertionError, 'no progress or cycled'):
            self.navigate()
        self.assertEqual(2, len(self.inputs))

    def test_swipe_failure_cannot_reach_next_capture(self):
        self.input_exit = 1
        with self.assertRaisesRegex(AssertionError, 'swipe failed'):
            self.navigate()
        self.assertEqual(['avatar-system-opened'], self.snapshots)

    def test_restarted_process_cannot_continue_navigation(self):
        self.respond('avatar-system-scroll-1', self.nodes('avatar-system-scroll-1'), pid='9999')
        with self.assertRaisesRegex(AssertionError, 'changed process'):
            self.navigate()

    def test_changed_display_cannot_continue_navigation(self):
        self.respond('avatar-system-scroll-1', self.nodes('avatar-system-scroll-1'), display=(1080, 2400))
        with self.assertRaisesRegex(AssertionError, 'changed display'):
            self.navigate()

    def test_background_scroll_does_not_replace_missing_status_container(self):
        nodes = self.scene()
        nodes[1].set('class', 'android.view.View')
        self.respond('avatar-system-opened', nodes)
        with self.assertRaisesRegex(AssertionError, 'scroll container missing'):
            self.navigate()
        self.assertEqual([], self.inputs)

    def test_container_position_change_is_rejected(self):
        nodes = self.scene(row=1)
        nodes[1].set('bounds', '[1518,232][2357,975]')
        self.respond('avatar-system-opened', self.scene())
        self.respond('avatar-system-scroll-1', nodes)
        with self.assertRaisesRegex(AssertionError, 'container moved'):
            self.navigate()

    def test_disabled_unclickable_or_offscreen_action_is_never_tapped(self):
        for attribute, value in [('enabled', 'false'), ('clickable', 'false'),
                                 ('bounds', '[2400,500][2500,600]')]:
            with self.subTest(attribute=attribute):
                self.setUp()
                nodes = self.scene(target=True)
                nodes[-1].set(attribute, value)
                self.respond('avatar-system-opened', nodes)
                self.respond('avatar-system-scroll-1', nodes)
                with self.assertRaisesRegex(AssertionError, 'no progress or cycled'):
                    self.navigate()
                self.assertEqual(1, len(self.taps))

    def test_target_outside_system_scroll_is_not_accepted(self):
        nodes = self.scene(target=True)
        target = nodes.pop()
        nodes[1].remove(target)
        nodes.append(target)
        self.respond('avatar-system-opened', nodes)
        self.respond('avatar-system-scroll-1', nodes)
        with self.assertRaisesRegex(AssertionError, 'no progress or cycled'):
            self.navigate()
        self.assertEqual(1, len(self.taps))

    def test_nested_node_rejected_by_snapshot_cannot_be_tapped(self):
        nodes = self.scene(target=True)
        nodes.pop()
        self.respond('avatar-system-opened', nodes)
        self.respond('avatar-system-scroll-1', nodes)
        with self.assertRaisesRegex(AssertionError, 'no progress or cycled'):
            self.navigate()
        self.assertEqual(1, len(self.taps))

    def test_ambiguous_action_is_not_clicked(self):
        nodes = self.scene(target=True)
        nodes.append(self.node(nodes[1], 99, 'HOLOGRAMM · LIVE-FRAMES PRÜFEN', clickable='true'))
        self.respond('avatar-system-opened', nodes)
        with self.assertRaisesRegex(AssertionError, 'action ambiguous'):
            self.navigate()
        self.assertEqual(1, len(self.taps))

    def test_late_visible_target_is_not_clicked(self):
        self.snapshot_seconds = 901
        self.respond('avatar-system-opened', self.scene(target=True))
        with self.assertRaisesRegex(AssertionError, 'navigation deadline exceeded'):
            self.navigate()
        self.assertEqual(1, len(self.taps))
        self.assertEqual([], self.inputs)

    def test_progress_never_extends_navigation_deadline(self):
        self.snapshot_seconds = 450
        self.respond('avatar-system-opened', self.scene())
        self.respond('avatar-system-scroll-1', self.scene(row=1))
        with self.assertRaisesRegex(AssertionError, 'navigation deadline exceeded'):
            self.navigate()
        self.assertEqual(1, len(self.inputs))

    def test_click_without_actual_diagnostic_dialog_is_rejected(self):
        self.respond('avatar-system-opened', self.scene(target=True))
        self.dialog(title='Fehler')
        with self.assertRaisesRegex(AssertionError, 'diagnostic error dialog'):
            self.navigate()
        self.assertEqual(2, len(self.taps))
        self.assertNotIn('avatar_diagnostic', self.env['report'])

    def test_diagnostic_without_actual_measurements_is_rejected(self):
        self.respond('avatar-system-opened', self.scene(target=True))
        self.dialog()
        self.responses['avatar-diagnostic'][0][1].set('text', 'Noch kein Ergebnis')
        with self.assertRaisesRegex(AssertionError, 'diagnostic incomplete'):
            self.navigate()
        self.assertNotIn('avatar_diagnostic', self.env['report'])


if __name__ == '__main__':
    unittest.main()
