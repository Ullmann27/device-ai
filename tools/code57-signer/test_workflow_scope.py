"""Static contracts for the protected signing workflow."""
from pathlib import Path
import ast
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/continuum-code57-original-sign.yml'
SCRIPT = ROOT / 'scripts/continuum-code57-original-signer.sh'
SIGN_NAME = 'Verify exact source, sign with original key, retain unpublished draft only'


def signer_step(text):
    pattern = r'(?ms)^      - name: ' + re.escape(SIGN_NAME) + r'\n(.*?)(?=^      - |^  [^ ]|\Z)'
    matches = list(re.finditer(pattern, text))
    if len(matches) != 1:
        raise AssertionError('Exactly one signer step required')
    return matches[0]


class WorkflowScopeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding='utf-8')
        cls.script = SCRIPT.read_text(encoding='utf-8')

    def test_secrets_exist_only_inside_signer_step(self):
        match = signer_step(self.text)
        outside = self.text[:match.start()] + self.text[match.end():]
        self.assertNotIn('secrets.', outside)
        self.assertEqual(len(re.findall(r'\bsecrets\.', match.group())), 2)

    def test_signing_step_receives_both_original_secrets(self):
        body = signer_step(self.text).group(1)
        self.assertIn('\n        env:\n', '\n' + body)
        self.assertRegex(body, r'(?m)^          ORIGINAL_P12: \$\{\{ secrets\.CONTINUUM_LOCAL_SIGNING_P12_BASE64 \}\}$')
        self.assertRegex(body, r'(?m)^          ORIGINAL_PASSWORD: \$\{\{ secrets\.CONTINUUM_LOCAL_SIGNING_PASSWORD \}\}$')

    def test_actions_are_immutable_verified_pins(self):
        uses = re.findall(r'(?m)^\s+- uses: (\S+)', self.text)
        expected = [
            'actions/checkout@11d5960a326750d5838078e36cf38b85af677262',
            'actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3',
        ] * 2
        self.assertCountEqual(uses, expected)

    def test_unused_node_setup_is_absent(self):
        self.assertNotIn('actions/setup-node', self.text)

    def test_protected_owner_gate_is_preserved(self):
        self.assertIn('    environment: continuum-original-signing\n', self.text)
        self.assertIn('    needs: preflight\n', self.text)
        condition = "    if: github.repository == 'Ullmann27/device-ai' && github.ref == 'refs/heads/chatgpt/code52-owner-signing-20261109'"
        self.assertEqual(self.text.count(condition), 2)
        self.assertNotIn('pull_request_target:', self.text)
        self.assertNotIn('workflow_dispatch:', self.text)

    def test_exact_product_and_qa_stay_pinned(self):
        for line in [
            '      SOURCE_SHA: "c5e2faeabed5b421796c83afde19d5ea391c5f63"',
            '      OWNER_APPROVED_SHA: "c5e2faeabed5b421796c83afde19d5ea391c5f63"',
            '      QA_RUN_ID: "43579602223"',
        ]:
            self.assertEqual(self.text.count(line), 2)

    def test_checkout_never_persists_credentials(self):
        self.assertEqual(self.text.count('          persist-credentials: false'), 2)

    def test_signer_is_called_only_once(self):
        body = signer_step(self.text).group(1)
        self.assertEqual(self.text.count('bash scripts/continuum-code57-original-signer.sh sign\n'), 1)
        self.assertIn('bash scripts/continuum-code57-original-signer.sh sign\n', body)

    def test_five_jobs_and_native_artifact_are_required(self):
        self.assertIn('.total_count==5 and (.jobs|length)==5', self.script)
        self.assertIn('"unsigned-arm64","native-runtime"]|sort)', self.script)
        self.assertIn('FIVE_JOBS_NOT_GREEN', self.script)
        self.assertIn('proof Code59-native-runtime-QA native', self.script)
        self.assertIn('fail NATIVE_RUNTIME_EVIDENCE', self.script)
        self.assertNotIn('FOUR_JOBS_NOT_GREEN', self.script)

    def test_existing_signing_and_publication_boundaries_stay(self):
        self.assertIn('  group: continuum-original-sign-only\n  cancel-in-progress: false\n', self.text)
        self.assertIn('cert=6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23', self.script)
        self.assertIn('.reviewer.login=="Ullmann27"', self.script)
        self.assertIn('.branch_policies|length==1 and .[0].name==$b and .[0].type=="branch"', self.script)
        self.assertIn('--draft --latest=false', self.script)
        self.assertIn('&& "$approved_sha" == "$source_sha" ]] || fail OWNER_SHA_UNAPPROVED', self.script)
        self.assertNotIn('NO_DEPLOY', self.text)

    def test_all_embedded_validators_parse(self):
        snippets = re.findall(r"<<'PY'[^\n]*\n(.*?)\nPY", self.script, re.S)
        self.assertEqual(len(snippets), 3)
        for snippet in snippets:
            ast.parse(snippet)

    def test_wrong_signing_branch_fails_before_external_access(self):
        result = subprocess.run(['bash', str(SCRIPT), 'preflight'], cwd=ROOT,
            env={'PATH': os.environ['PATH'], 'GITHUB_REPOSITORY': 'Ullmann27/device-ai',
                 'GITHUB_REF': 'refs/heads/repair/code59-original-signing-prepare-20261114',
                 'GITHUB_EVENT_NAME': 'push', 'SOURCE_SHA': 'a'*40,
                 'OWNER_APPROVED_SHA': 'a'*40, 'QA_RUN_ID': '12345678'},
            capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 69)
        self.assertIn('CODE59_SIGN_WRONG_SIGN_BRANCH', result.stderr)


class NativeEvidenceTest(unittest.TestCase):
    """Synthetic receipts exercise native validation."""
    sha = 'a'*40
    tree = 'b'*40
    package = 'at.heinz.deviceai.mentorpaired'

    @classmethod
    def setUpClass(cls):
        script = SCRIPT.read_text()
        match = re.search(r"<<'PY' \|\| fail NATIVE_RUNTIME_EVIDENCE\n(.*?)\nPY", script, re.S)
        assert match is not None
        cls.validator = match.group(1)

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root/'app').mkdir()
        (self.root/'filament').mkdir()
        self.report = {'status': 'PASS', 'source_sha': self.sha, 'source_tree': self.tree,
            'package': self.package, 'sdk': 35, 'abi': 'x86_64', 'serial': 'emulator-5554',
            'apk_sha256': {'app-debug.apk': 'c'*64, 'app-debug-androidTest.apk': 'd'*64}, 'checks': []}
        labels = ['fresh-empty-no-permissions', 'conversation', 'conversation-keyboard-dismissed',
                  'start-roundtrip', 'rotation', 'process-restart', 'offline-restart']
        for name in labels:
            display = [800,400] if name in labels[4:] else [400,800]
            pid = '102' if name=='offline-restart' else '101' if name=='process-restart' else '100'
            self.report['checks'].append({'name': name, 'status': 'PASS', 'pid': pid,
                'bounded_app_nodes': 1, 'display': display})
            (self.root/'app'/f'{name}.png').write_bytes(self.png(*display))
            (self.root/'app'/f'{name}.xml').write_text(
                f'<hierarchy rotation="{1 if name=="rotation" else 0}"><node package="{self.package}" '
                'class="android.widget.EditText" text="Synthetic fixture" bounds="[0,0][200,200]"/></hierarchy>')
        (self.root/'app/clear-empty-data.txt').write_text('Success\n')
        (self.root/'app/permissions-before.txt').write_text('\n'.join(
            f'android.permission.{p}: granted=false' for p in ['CAMERA','RECORD_AUDIO','POST_NOTIFICATIONS']))
        (self.root/'app/offline-connectivity.txt').write_text('Active default network: none\n')
        self.submitted_ui_fixture()
        for log in ['logcat.txt','crash-logcat.txt']:
            (self.root/'app'/log).write_text('')
        for directory in [self.root,self.root/'filament']:
            (directory/'source-sha.txt').write_text(self.sha+'\n')
            (directory/'source-tree.txt').write_text(self.tree+'\n')
            (directory/'apk-sha256.txt').write_text('\n'.join(
                f'{digest}  app/build/{name}' for name,digest in self.report['apk_sha256'].items())+'\n')
        self.instrumentation('native-pdf.txt', {'status':'ok','provider_queries':'1','provider_opens':'1',
            'page_count':'1','rendered_ink_pixels':'20'})
        self.instrumentation('filament/native-filament.txt', {'status':'ok','sdk':'35',
            'context':'SYNTHETIC FILAMENT TEST; fixture', 'native_quads':'2','native_materials':'2',
            'native_textures':'2','upload_completions':'1','rendered_frames':'3','presented_frames':'3',
            'output_frames':'3','content_cleared':'true','panels_released':'true','engine_released':'true'})
        (self.root/'filament/surfaceflinger.txt').write_text('GLES: Synthetic SwiftShader fixture\n')

    def submitted_ui_fixture(self):
        reply = ('Continuum · Aktuelle App-Fähigkeiten Mein aktueller Stand auf diesem Gerät: '
                 'Lokales Sprachmodell: keines geladen. Diese Statusantwort startet kein Modell, '
                 'keine Recherche, kein Mikrofon und keinen Entwicklungsauftrag.')
        def nodes(name, values, rotation='0'):
            root = ET.Element('hierarchy', rotation=rotation)
            for item in values:
                ET.SubElement(root, 'node', {'package':self.package, **item})
            ET.ElementTree(root).write(self.root/'app'/name, encoding='unicode')
        editor = {'class':'android.widget.EditText', 'bounds':'[10,10][300,80]'}
        nodes('conversation-input.xml', [{**editor, 'text':'Was kannst du?'}])
        nodes('conversation.xml', [{**editor, 'text':''},
              {'class':'android.widget.TextView', 'bounds':'[10,100][300,300]', 'text':reply}])
        self.report['checks'][1]['bounded_app_nodes'] = 2
        self.report['conversation_submission'] = {'question':'Was kannst du?', 'response_text':reply,
            'input_evidence':'conversation-input.xml', 'response_evidence':'conversation.xml',
            'offline_evidence':'conversation-connectivity.txt'}
        (self.root/'app/conversation-connectivity.txt').write_text('Active default network: none\n')
        labels = ['Konversation öffnen','Start','Gespräch','Gedächtnis','Forschung',
                  'Aufgaben','Internet','Lernen','Geräte']
        controls = {label:[20+83*i,20,85+83*i,70] for i,label in enumerate(labels)}
        nodes('rotation.xml', [{'class':'android.widget.Button', 'text':label,
            'bounds':f'[{b[0]},{b[1]}][{b[2]},{b[3]}]', 'enabled':'true','clickable':'true'}
            for label,b in controls.items()], rotation='1')
        self.report['checks'][4]['bounded_app_nodes'] = len(labels)
        gesture = [400,340,400,80,700]
        self.report['landscape_navigation'] = {'scroll_gestures':[gesture], 'visible_controls':controls,
            'controls_evidence':'rotation.xml', 'conversation_evidence':'landscape-conversation.xml'}
        nodes('landscape-conversation.xml', [{**editor, 'text':'Schreibe eine Nachricht …'}], rotation='1')
        (self.root/'app/rotation-scroll-1-input-command.json').write_text(json.dumps({
            'returncode':0, 'args':['/sdk/platform-tools/adb','-s','emulator-5554',
                                  'shell','input','swipe',*map(str,gesture)]}))

    @staticmethod
    def png(width,height):
        def chunk(kind,data):
            return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data))
        return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))+\
            chunk(b'tEXt',b'Synthetic fixture\0'+b'x'*5000)+\
            chunk(b'IDAT',zlib.compress((b'\0'+b'\xff'*width*3)*height))+chunk(b'IEND',b'')

    def instrumentation(self,name,values):
        (self.root/name).write_text(''.join(f'INSTRUMENTATION_RESULT: {k}={v}\n' for k,v in values.items())+
                                   'INSTRUMENTATION_CODE: -1\n')

    def validate(self):
        (self.root/'app/RESULT.json').write_text(json.dumps(self.report))
        return subprocess.run([sys.executable,'-B','-c',self.validator,str(self.root),self.sha,self.tree],
            capture_output=True,text=True,timeout=10)

    def test_complete_bound_evidence_passes(self):
        result = self.validate()
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('SOURCE_BOUND_NATIVE_RUNTIME_VERIFIED',result.stdout)

    def test_wrong_source_tree_package_and_runtime_fail(self):
        for key,bad in [('source_sha','e'*40),('source_tree','f'*40),('package','another.package'),
                        ('sdk',34),('abi','arm64-v8a'),('status','FAILED'),('serial','physical-device')]:
            with self.subTest(key=key):
                old = self.report[key]
                self.report[key] = bad
                self.assertNotEqual(self.validate().returncode,0)
                self.report[key] = old

    def test_missing_failed_or_duplicate_lifecycle_check_fails(self):
        old = self.report['checks'][:]
        self.report['checks'] = old[:-1]
        self.assertNotEqual(self.validate().returncode,0)
        self.report['checks'] = old+[old[0]]
        self.assertNotEqual(self.validate().returncode,0)
        self.report['checks'] = old
        self.report['checks'][0]['status'] = 'FAILED'
        self.assertNotEqual(self.validate().returncode,0)

    def test_empty_or_missing_ui_and_screenshot_fail(self):
        path = self.root/'app/conversation.xml'
        old = path.read_text()
        path.write_text('<hierarchy/>')
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(old)
        (self.root/'app/conversation.png').unlink()
        self.assertNotEqual(self.validate().returncode,0)

    def test_online_or_permission_granted_evidence_fails(self):
        (self.root/'app/offline-connectivity.txt').write_text('Active default network: 100\n')
        self.assertNotEqual(self.validate().returncode,0)
        (self.root/'app/offline-connectivity.txt').write_text('Active default network: none\n')
        (self.root/'app/permissions-before.txt').write_text('android.permission.CAMERA: granted=true\n')
        self.assertNotEqual(self.validate().returncode,0)

    def test_crash_and_unrestarted_process_fail(self):
        (self.root/'app/crash-logcat.txt').write_text('FATAL EXCEPTION: '+self.package)
        self.assertNotEqual(self.validate().returncode,0)
        (self.root/'app/crash-logcat.txt').write_text('')
        self.report['checks'][5]['pid'] = self.report['checks'][0]['pid']
        self.assertNotEqual(self.validate().returncode,0)

    def test_mismatched_native_source_and_apk_hashes_fail(self):
        path = self.root/'filament/source-sha.txt'
        path.write_text('e'*40)
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(self.sha)
        self.report['apk_sha256']['app-debug.apk'] = 'e'*64
        self.assertNotEqual(self.validate().returncode,0)

    def test_pdf_and_filament_failures_cannot_be_hidden(self):
        path = self.root/'native-pdf.txt'
        old = path.read_text()
        path.write_text(old.replace('rendered_ink_pixels=20','rendered_ink_pixels=0'))
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(old)
        path = self.root/'filament/native-filament.txt'
        path.write_text(path.read_text().replace('engine_released=true','engine_released=false'))
        self.assertNotEqual(self.validate().returncode,0)

    def test_missing_or_fabricated_submission_fails(self):
        submission = self.report.pop('conversation_submission')
        self.assertNotEqual(self.validate().returncode,0)
        self.report['conversation_submission'] = submission
        submission['response_text'] = 'An answer not present in the actual UI'
        self.assertNotEqual(self.validate().returncode,0)

    def test_chat_requires_offline_state_and_cleared_input(self):
        path = self.root/'app/conversation-connectivity.txt'
        path.write_text('Active default network: 100\n')
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text('Active default network: none\n')
        tree = ET.parse(self.root/'app/conversation.xml')
        tree.getroot().find('node').set('text','Was kannst du?')
        tree.write(self.root/'app/conversation.xml',encoding='unicode')
        self.assertNotEqual(self.validate().returncode,0)

    def test_landscape_requires_actual_swipe_and_visible_controls(self):
        path = self.root/'app/rotation-scroll-1-input-command.json'
        before = path.read_text()
        command = json.loads(before)
        command['returncode'] = 1
        path.write_text(json.dumps(command))
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(before)
        tree = ET.parse(self.root/'app/rotation.xml')
        tree.getroot().find('node').set('bounds','[0,401][200,451]')
        tree.write(self.root/'app/rotation.xml',encoding='unicode')
        self.assertNotEqual(self.validate().returncode,0)

    def test_landscape_requires_reached_composer(self):
        (self.root/'app/landscape-conversation.xml').write_text('<hierarchy/>')
        self.assertNotEqual(self.validate().returncode,0)


if __name__ == '__main__':
    unittest.main(verbosity=2)
