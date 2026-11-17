"""Bound navigation to visible evidence."""
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET

import native_web_offline as proof


class WebNavigationTest(unittest.TestCase):
    def scene(self, offset=0, target=False, scrollable=True, duplicate=False):
        root = ET.Element('node', {'class': 'android.widget.ScrollView', 'enabled': 'true',
            'scrollable': 'true', 'bounds': '[0,0][200,400]',
            'content-desc': 'Continuum-Cockpit, vertikal scrollbar'})
        panel = ET.SubElement(root, 'node', {'index': '0', 'class': 'android.widget.ScrollView',
            'enabled': 'true', 'scrollable': str(scrollable).lower(), 'bounds': '[10,50][190,350]'})
        ET.SubElement(panel, 'node', {'index': '0', 'class': 'android.widget.TextView',
            'bounds': f'[20,{80 + offset}][180,{110 + offset}]', 'text': 'Web und Quellen'})
        if target:
            ET.SubElement(panel, 'node', {'index': '1', 'class': 'android.widget.Button',
                'enabled': 'true', 'clickable': 'true', 'bounds': '[20,270][180,320]',
                'text': 'INTERNETFREIGABE'})
        if duplicate:
            ET.SubElement(root, 'node', {'index': '1', **panel.attrib})
        return list(root.iter('node'))

    def probe(self, scenes, result=None, next_size=(200, 400)):
        captures, commands = [], []
        probe = object.__new__(proof.Probe)
        probe.nodes, probe.display, probe.pid = scenes[0], (200, 400), '100'
        probe.label = 'web-center-initial'
        probe.observation = {'captures': [probe.label]}
        def snapshot(label, **options):
            captures.append(label)
            return scenes[min(len(captures), len(scenes) - 1)], '100', next_size
        def command(*args):
            commands.append(args)
            return result or SimpleNamespace(returncode=0, stdout=b'', stderr=b'')
        probe.api = {'snapshot': snapshot, 'captured_device': command, 'save': lambda *args: None}
        return probe, captures, commands

    def test_visible_action_needs_no_scroll_even_when_panel_fits(self):
        probe, captures, commands = self.probe([self.scene(target=True, scrollable=False)])
        probe.seek_settings('initial')
        self.assertEqual([], commands)
        self.assertEqual([], captures)
        self.assertEqual('web-center-initial', probe.observation['settings_snapshots']['initial'])

    def test_offscreen_action_uses_only_inspected_panel_swipe(self):
        probe, captures, commands = self.probe([self.scene(), self.scene(offset=20, target=True)])
        probe.seek_settings('initial')
        self.assertEqual([('web-center-initial-scroll-a-input', 'shell', 'input', 'swipe',
                           '100', '300', '100', '100', '700')], commands)
        self.assertEqual(['web-center-initial-scroll-a'], captures)
        self.assertEqual(captures[0], probe.observation['settings_snapshots']['initial'])

    def test_unchanged_geometry_aborts_without_repeated_swipes(self):
        probe, _, commands = self.probe([self.scene(), self.scene()])
        with self.assertRaisesRegex(AssertionError, 'no progress'):
            probe.seek_settings('initial')
        self.assertEqual(1, len(commands))

    def test_cycle_aborts_before_third_swipe(self):
        probe, _, commands = self.probe([self.scene(), self.scene(offset=20), self.scene()])
        with self.assertRaisesRegex(AssertionError, 'cycled'):
            probe.seek_settings('initial')
        self.assertEqual(2, len(commands))

    def test_missing_control_has_six_swipe_ceiling(self):
        probe, _, commands = self.probe([self.scene(offset=i * 10) for i in range(8)])
        with self.assertRaisesRegex(AssertionError, 'limit reached'):
            probe.seek_settings('initial')
        self.assertEqual(6, len(commands))

    def test_ambiguous_panel_never_receives_input(self):
        probe, _, commands = self.probe([self.scene(duplicate=True)])
        with self.assertRaisesRegex(AssertionError, 'Unique web-center'):
            probe.seek_settings('initial')
        self.assertEqual([], commands)

    def test_control_outside_panel_is_not_a_target(self):
        scene = self.scene(target=True, scrollable=False)
        scene[-1].set('bounds', '[20,360][180,390]')
        probe, _, commands = self.probe([scene])
        with self.assertRaisesRegex(AssertionError, 'does not scroll'):
            probe.seek_settings('initial')
        self.assertEqual([], commands)

    def test_failed_swipe_never_creates_capture(self):
        probe, captures, _ = self.probe([self.scene()], result=SimpleNamespace(
            returncode=1, stdout=b'', stderr=b'failed'))
        with self.assertRaisesRegex(AssertionError, 'swipe failed'):
            probe.seek_settings('initial')
        self.assertEqual([], captures)

    def test_display_change_aborts_after_observation(self):
        probe, _, _ = self.probe([self.scene(), self.scene(offset=20, target=True)],
                                 next_size=(400, 200))
        with self.assertRaisesRegex(AssertionError, 'display changed'):
            probe.seek_settings('initial')
