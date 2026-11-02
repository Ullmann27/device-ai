"""Unit tests for the tap-target table of continuum-emulator-screenshots.py (no emulator needed).

Run: python3 -m unittest discover -s scripts -p 'test_continuum_*.py'
"""
import importlib.util
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location('emulator', Path(__file__).with_name('continuum-emulator-screenshots.py'))
emu = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(emu)

SOURCES = '\n'.join(p.read_text(encoding='utf-8') for p in (ROOT / 'app/src/main/java/at/heinz/deviceai').rglob('*.kt'))


def node(text='', desc=''):
    return {'text': text, 'desc': desc, 'clickable': False, 'pkg': emu.PACKAGE, 'cls': '', 'bounds': [0, 0, 1, 1], 'w': 1, 'h': 1}


class TargetTableTest(unittest.TestCase):
    def test_every_expected_title_exists_in_the_app(self):
        # A renamed screen title must break this test instead of silently failing every tap on the emulator.
        for pattern, expected, where in emu.TARGETS:
            if where != 'text':
                continue
            self.assertTrue(any(e in SOURCES for e in expected), f'{pattern}: none of {expected} occurs in the app sources')

    def test_every_start_screen_element_and_navigation_entry_has_a_rule(self):
        labels = ['Modellstatus: CDX1 1, bereit.', 'Lernen und Forschung: 3 Lernereignisse heute',
                  'Speicher und Quellen: 41 Prozent belegt.', 'Entwicklung und Code: aktiv. 2 offene Aufgaben im Backlog',
                  'Systemstatus: normal. CPU-Anteil der App 3 %', 'Systemaktivität, Messung alle 5 Sekunden: App-CPU 3 %',
                  'Keine offenen Hinweise', '2 offene Hinweise', 'Wissensarchiv durchsuchen', 'Persönliche Einstellungen',
                  'VERBUNDEN: lokal', 'DENKEN: bereit', 'LERNEN: aktiv', 'ANALYSIEREN: aus', 'ENTWICKELN: 2 Aufgaben',
                  'EVOLVIEREN: 4 Lektionen', 'Continuum. Dein KI-Universum. Ohne Grenzen.']
        labels += ['Start', 'Chat', 'Lernen', 'Entwickeln', 'Analysieren', 'Speicher', 'System', 'Mehr']   # Fold rail
        labels += ['Start – Startseite und Übersicht öffnen', 'Chat – Mit der lokalen KI sprechen',
                   'Lernen – Lernzentrum und Trainingsstand öffnen', 'Entwickeln – Sichere Entwicklungswerkzeuge öffnen',
                   'Mehr – Weitere Bereiche']                                                           # phone bar
        for label in labels:
            self.assertIsNotNone(emu.rule_for(label), label)
        self.assertIn('Lernzentrum', emu.rule_for('Lernen und Forschung: 3')[1])
        self.assertIn('Systemstatus', emu.rule_for('System')[1])
        self.assertIn('Gedächtniszentrum', emu.rule_for('Speicher')[1])

    def test_the_tapped_cards_own_description_never_counts_as_its_target(self):
        # tap did nothing: the start screen is still there, its card describes itself as "Systemstatus: ..."
        still_home = [node(desc='Systemstatus: normal. CPU-Anteil der App 3 %'), node(desc='Modellstatus: CDX1')]
        self.assertEqual(emu.verify_tap('Systemstatus: normal', still_home)[0], 'FAIL')
        opened = still_home + [node(text='Systemstatus'), node(text='SHIZUKU · VERBINDEN')]
        self.assertEqual(emu.verify_tap('Systemstatus: normal', opened)[0], 'PASS')

    def test_start_returns_to_the_drawn_start_screen(self):
        self.assertEqual(emu.verify_tap('Start', [node(desc='Modellstatus: CDX1, bereit.')])[0], 'PASS')
        self.assertEqual(emu.verify_tap('Start', [node(text='Lernzentrum')])[0], 'FAIL')

    def test_unknown_elements_are_reported_not_passed(self):
        self.assertEqual(emu.verify_tap('CDX1 1, bereit', [node(text='DEVICE AI · MODELLE')])[0], 'UNMAPPED')
        taps = [{'verdict': v} for v in ('PASS', 'FAIL', 'UNMAPPED', 'PASS')]
        self.assertEqual(emu.summarise(taps), {'PASS': 2, 'FAIL': 1, 'UNMAPPED': 1})

    def test_the_candidate_is_the_feature_preview_with_a_qualified_activity(self):
        self.assertEqual(emu.PACKAGE, 'at.heinz.deviceai.featurepreview')
        self.assertEqual(emu.ACTIVITY, 'at.heinz.deviceai.AgentActivity')   # namespace stays at.heinz.deviceai
        gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('applicationIdSuffix = ".featurepreview"', gradle)
        self.assertRegex(gradle, r'versionCode = if \(deviceAiFeaturePreview\) ' + emu.EXPECTED_VERSION_CODE + r'\b')
        manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
        self.assertTrue(re.search(r'android:name="\.AgentActivity"\s+android:exported="true"', manifest))


class WorkflowTest(unittest.TestCase):
    def test_workflow_builds_installs_and_checks_the_feature_preview(self):
        wf = (ROOT / '.github/workflows/continuum-ui-screenshots.yml').read_text(encoding='utf-8')
        for task in (':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'):
            line = next(l for l in wf.splitlines() if task in l)
            self.assertIn('-PdeviceAiFeaturePreview=true', line, task)
        self.assertIn('at.heinz.deviceai.featurepreview at.heinz.deviceai.AgentActivity', wf)


if __name__ == '__main__':
    unittest.main()
