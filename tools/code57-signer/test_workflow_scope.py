"""Static contracts for the protected signing workflow."""
from pathlib import Path
import ast
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/code55-apk'))
from test_native_hologram_evidence import HologramFixture
import test_native_routines
import test_native_web_offline

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


class JunitSkipContractTest(unittest.TestCase):
    """Synthetic identities exercise skip validation."""
    expected = [
        ('at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest',
         'actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations'),
        ('at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest',
         'cancellationOfRealContainerClosesRunnerAndReportsToChiefMailbox'),
    ]

    @classmethod
    def setUpClass(cls):
        match = re.search(r"<<'PY' \|\| fail JUNIT_LINT_EVIDENCE\n(.*?)\nPY", SCRIPT.read_text(), re.S)
        assert match is not None
        cls.validator = match.group(1)
        tree = ast.parse(cls.validator)
        definitions = [node for node in tree.body
                       if isinstance(node, ast.FunctionDef) and node.name == 'verify_skip_contract']
        assert len(definitions) == 1
        scope = {}
        exec(compile(ast.Module(body=definitions, type_ignores=[]), str(SCRIPT), 'exec'), scope)
        cls.verify = staticmethod(scope['verify_skip_contract'])

    def test_exact_container_identities_pass_in_both_orders(self):
        self.verify(self.expected)
        self.verify([list(case) for case in reversed(self.expected)])

    def test_matching_count_cannot_hide_an_unknown_method(self):
        for index in range(2):
            with self.subTest(index=index):
                changed = list(self.expected)
                changed[index] = (changed[index][0], 'unexpectedSkippedTest')
                with self.assertRaises(AssertionError):
                    self.verify(changed)

    def test_matching_method_cannot_hide_a_different_class(self):
        changed = [('at.heinz.deviceai.OtherHostTest', case[1]) for case in self.expected]
        with self.assertRaises(AssertionError):
            self.verify(changed)

    def test_duplicate_identity_is_not_two_container_contracts(self):
        for case in self.expected:
            with self.subTest(case=case):
                with self.assertRaises(AssertionError):
                    self.verify([case, case])

    def test_missing_extra_and_legacy_seventeen_skips_fail(self):
        for count in (0, 1, 3, 17):
            with self.subTest(count=count):
                observed = (self.expected * 9)[:count]
                with self.assertRaises(AssertionError):
                    self.verify(observed)

    def test_original_xml_and_fresh_source_summary_are_both_checked(self):
        self.assertIn("verify_skip_contract([(c.get('classname'),c.get('name')) for c in cases if c.find('skipped') is not None])", self.validator)
        self.assertIn("verify_skip_contract(data['skipped_cases'])", self.validator)
        self.assertIn("str(source/'tools/code55-apk/junit-summary.py')", self.validator)
        self.assertIn("'--strict-skips'", self.validator)
        self.assertIn("data['source_sha']==sha", self.validator)
        self.assertIn("data['errors']==0 and data['failures']==0 and data['skipped']==2", self.validator)
        self.assertIn("not any(c.find('failure') is not None or c.find('error') is not None for c in cases)", self.validator)


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
        for name in labels+['conversation-input','landscape-conversation']:
            capture={'status':'ok','elapsed_ms':'100','capture_started_ms':'20',
                     'hierarchy_finished_ms':'30','screenshot_finished_ms':'40','capture_finished_ms':'50'}
            (self.root/'app'/f'{name}-window.json').write_text(json.dumps(capture))
            self.instrumentation(f'app/{name}-ui-automation-stdout.txt',capture)
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
        self.backend_fixture()
        self.hologram = HologramFixture(self.root/'hologram')
        self.routine_fixture()
        self.offline_web_fixture()

    def offline_web_fixture(self):
        fixture = test_native_web_offline.OfflineWebEvidenceTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        for path in fixture.directory.iterdir():
            shutil.copyfile(path, self.root/'app'/path.name)
        self.report['offline_web'] = test_native_web_offline.proof.verify(
            self.root/'app', self.report, self.sha, self.tree)

    def routine_fixture(self):
        fixture = test_native_routines.RoutineEvidenceTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        fixture.complete()
        directory = self.root/'routines'
        shutil.copytree(fixture.directory, directory)
        report = dict(fixture.report, status='PASS', serial=self.report['serial'],
                      apk_sha256=dict(self.report['apk_sha256']))
        report['routine_checks'] = test_native_routines.proof.verify(directory, report, self.sha, self.tree)
        (directory/'RESULT.json').write_text(json.dumps(report))
        (self.root/'probe-exits.json').write_text(json.dumps({
            'source_sha': self.sha, 'hologram': 0, 'routines': 0, 'app': 0}))

    def backend_fixture(self):
        executable = '/sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless'
        adapter = 'Android Emulator OpenGL ES Translator (llvmpipe (LLVM 21.0.0, 256 bits))'
        (self.root/'filament/surfaceflinger.txt').write_text(
            f'GLES: Google (Mesa), {adapter}, OpenGL ES 3.1 (OpenGL ES 3.2 Mesa 25.2.4)\n')
        (self.root/'emulator.log').write_text(
            "INFO | Forcing 'llvmpipe' mode for GLES\n"
            'INFO | vulkan_mode_selected:lavapipe gles_mode_selected:llvmpipe\n'
            'INFO | Graphics Adapter Vendor Google (Mesa)\n'
            f'INFO | Graphics Adapter {adapter}\n')
        (self.root/'host-crash').mkdir(exist_ok=True)
        for name, value in [('executable', executable), ('pid', '42'), ('uid', '1001')]:
            (self.root/'host-crash'/f'emulator-{name}.txt').write_text(value+'\n')
        (self.root/'host-crash/emulator-maps.txt').write_text('\n'.join(
            f'1000-2000 r-xp 00000000 00:01 1 /sdk/emulator/lib64/gles_llvmpipe/{name}'
            for name in ('libEGL.so', 'libGLESv2.so', 'libgallium-25.2.4.so'))+'\n')
        (self.root/'host-live-identity.json').write_text(json.dumps({
            'pid': 42, 'uid': 1001, 'source_sha': self.sha, 'serial': self.report['serial'],
            'executable': executable}))

    def test_current_llvmpipe_backend_contract_passes(self):
        self.backend_fixture()
        result = self.validate()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_unexpected_or_ambiguous_backend_selection_fails(self):
        path = self.root/'emulator.log'
        original = path.read_text()
        invalid = [original.replace('gles_mode_selected:llvmpipe', 'gles_mode_selected:swangle'),
                   original.replace('vulkan_mode_selected:lavapipe', 'vulkan_mode_selected:swiftshader'),
                   original.replace("Forcing 'llvmpipe' mode for GLES", 'No explicit GLES mode'),
                   original+'INFO | gles_mode_selected:llvmpipe\n',
                   original+'INFO | vulkan_mode_selected:lavapipe\n',
                   original+'INFO | Graphics Adapter llvmpipe\n']
        for text in invalid:
            with self.subTest(log=text):
                path.write_text(text)
                self.assertNotEqual(self.validate().returncode, 0)

    def test_actual_gles_must_match_selected_mesa_adapter(self):
        path = self.root/'filament/surfaceflinger.txt'
        original = path.read_text()
        for text in ('', original*2, 'GLES: Synthetic SwiftShader fixture\n',
                     original.replace('llvmpipe', 'ANGLE (Vulkan)'),
                     original.replace('Google (Mesa)', 'Unknown vendor'),
                     original.replace('LLVM 21.0.0', 'different adapter'),
                     original.replace('OpenGL ES 3.1', 'OpenGL ES 2.0')):
            with self.subTest(gles=text):
                path.write_text(text)
                self.assertNotEqual(self.validate().returncode, 0)

    def test_backend_maps_require_exact_sdk_library_paths(self):
        path = self.root/'host-crash/emulator-maps.txt'
        original = path.read_text()
        invalid = [original.replace('/sdk/emulator/', '/different-sdk/emulator/'),
                   original.replace('gles_llvmpipe', 'gles_swiftshader'),
                   original.replace('libgallium-25.2.4.so', 'libgallium-unknown.so'),
                   original.replace('libEGL.so', 'libEGL.so.backup')]
        invalid += ['\n'.join(line for line in original.splitlines() if name not in line)
                    for name in ('libEGL.so', 'libGLESv2.so', 'libgallium-25.2.4.so')]
        for text in invalid:
            with self.subTest(maps=text):
                path.write_text(text)
                self.assertNotEqual(self.validate().returncode, 0)

    def test_backend_identity_is_bound_to_source_serial_and_process(self):
        path = self.root/'host-live-identity.json'
        original = json.loads(path.read_text())
        for key, value in [('source_sha', 'e'*40), ('serial', 'emulator-5556'),
                           ('pid', 43), ('uid', 1002), ('pid', True),
                           ('executable', '/different-sdk/emulator/qemu/linux-x86_64/qemu-system-x86_64-headless')]:
            with self.subTest(key=key, value=value):
                path.write_text(json.dumps(dict(original, **{key: value})))
                self.assertNotEqual(self.validate().returncode, 0)

    def test_missing_backend_evidence_is_rejected(self):
        for name in ['emulator.log', 'host-live-identity.json', 'host-crash/emulator-maps.txt',
                     'host-crash/emulator-executable.txt', 'host-crash/emulator-pid.txt',
                     'host-crash/emulator-uid.txt']:
            with self.subTest(name=name):
                path = self.root/name
                original = path.read_bytes()
                path.unlink()
                self.assertNotEqual(self.validate().returncode, 0)
                path.write_bytes(original)

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
        return subprocess.run([sys.executable,'-B','-c',self.validator,str(self.root),self.sha,self.tree,str(ROOT)],
            capture_output=True,text=True,timeout=10)

    def test_complete_bound_evidence_passes(self):
        result = self.validate()
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('SOURCE_BOUND_NATIVE_RUNTIME_VERIFIED',result.stdout)

    def test_missing_routine_evidence_fails(self):
        (self.root/'routines/RESULT.json').unlink()
        self.assertNotEqual(self.validate().returncode, 0)

    def test_routines_require_same_source_runtime_and_apks(self):
        path = self.root/'routines/RESULT.json'
        original = json.loads(path.read_text())
        for key, value in (('source_sha', 'e'*40), ('source_tree', 'f'*40),
                           ('serial', 'emulator-5556'), ('status', 'FAILED'),
                           ('apk_sha256', {'app-debug.apk': 'e'*64, 'app-debug-androidTest.apk': 'd'*64}),
                           ('cleanup_errors', ['Restore failed'])):
            with self.subTest(key=key):
                path.write_text(json.dumps(dict(original, **{key: value})))
                self.assertNotEqual(self.validate().returncode, 0)

    def test_routine_worker_and_chat_bytes_are_recomputed(self):
        receipt = self.root/'routines/after-start-receipts-stdout.txt'
        original = receipt.read_bytes()
        receipt.write_bytes(original[:-1] + bytes([original[-1] ^ 1]))
        self.assertNotEqual(self.validate().returncode, 0)
        receipt.write_bytes(original)
        chat = self.root/'routines/after-start-chat-stdout.txt'
        original = chat.read_bytes()
        chat.write_bytes(original * 2)
        self.assertNotEqual(self.validate().returncode, 0)
        chat.write_bytes(original)
        path = self.root/'routines/RESULT.json'
        report = json.loads(path.read_text())
        report['routine_checks']['periodic_due_worker_execution'] = 'NOT_RUN'
        path.write_text(json.dumps(report))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_every_original_probe_exit_is_required(self):
        path = self.root/'probe-exits.json'
        original = json.loads(path.read_text())
        for key, value in (('hologram', 1), ('routines', 1), ('app', 1),
                           ('source_sha', 'e'*40), ('hologram', False)):
            with self.subTest(key=key, value=value):
                path.write_text(json.dumps(dict(original, **{key: value})))
                self.assertNotEqual(self.validate().returncode, 0)
        path.unlink()
        self.assertNotEqual(self.validate().returncode, 0)

    def test_due_periodic_receipt_and_history_are_revalidated(self):
        receipt = self.root/'routines/periodic-complete-receipts-stdout.txt'
        original = receipt.read_bytes()
        receipt.write_bytes((self.root/'routines/periodic-first-receipts-stdout.txt').read_bytes())
        self.assertNotEqual(self.validate().returncode, 0)
        receipt.write_bytes(original)
        history = self.root/'routines/periodic-complete-job-stdout.txt'
        original = history.read_text()
        history.write_text(original.replace('app called jobFinished', 'canceled'))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_offline_web_requires_actual_new_receipt(self):
        (self.root/'app/web-after-receipts-stdout.txt').unlink()
        self.assertNotEqual(self.validate().returncode, 0)

    def test_offline_web_requires_one_time_owner_action(self):
        path = self.root/'app/web-observation.json'
        value = json.loads(path.read_text())
        value['actions'] = [action for action in value['actions'] if action['name'] != 'web-allow-once']
        path.write_text(json.dumps(value))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_offline_web_requires_final_off_and_raw_dossier(self):
        path = self.root/'app/web-mode-final-stdout.txt'
        original = path.read_bytes()
        path.write_bytes(original.replace(b'OFF', b'ASK'))
        self.assertNotEqual(self.validate().returncode, 0)
        path.write_bytes(original)
        (self.root/'app/web-after-dossier-stdout.txt').unlink()
        self.assertNotEqual(self.validate().returncode, 0)

    def test_periodic_pass_requires_real_minimum_wait(self):
        path = self.root/'routines/periodic-wait-1-host-time.json'
        value = json.loads(path.read_text())
        value['started_ns'] -= 1_000_000_000
        value['finished_ns'] -= 1_000_000_000
        path.write_text(json.dumps(value))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_periodic_unchanged_result_cannot_duplicate_chat(self):
        path = self.root/'routines/periodic-complete-chat-stdout.txt'
        path.write_bytes(path.read_bytes() * 2)
        self.assertNotEqual(self.validate().returncode, 0)

    def test_missing_hologram_evidence_fails(self):
        (self.root/'hologram/native-hologram.txt').unlink()
        self.assertNotEqual(self.validate().returncode,0)

    def test_native_quads_cannot_replace_consumed_hologram_frames(self):
        for sample in self.hologram.report['samples']:
            sample['surface_texture_timestamp'] = 0
        self.hologram.save()
        self.assertNotEqual(self.validate().returncode,0)

    def test_hologram_must_use_the_same_source_and_apks(self):
        path = self.root/'hologram/source-sha.txt'
        path.write_text('e'*40)
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(self.sha)
        path = self.root/'hologram/apk-sha256.txt'
        path.write_text(path.read_text().replace('c'*64,'e'*64))
        self.assertNotEqual(self.validate().returncode,0)

    def test_matching_hashes_do_not_make_empty_surface_pixels_valid(self):
        self.hologram.report['pixel_copy']['image'] = self.hologram.image('hologram-surface-copy.png',b'\0'*16)
        self.hologram.save()
        self.assertNotEqual(self.validate().returncode,0)

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

    def test_capture_deadline_rejects_boundary_and_real_overrun(self):
        path=self.root/'app/conversation-window.json'
        capture=json.loads(path.read_text())
        for elapsed in ('45000','48113'):
            with self.subTest(elapsed=elapsed):
                capture['elapsed_ms']=elapsed
                path.write_text(json.dumps(capture))
                self.instrumentation('app/conversation-ui-automation-stdout.txt',capture)
                self.assertNotEqual(self.validate().returncode,0)
        capture['elapsed_ms']='44999'
        path.write_text(json.dumps(capture))
        self.instrumentation('app/conversation-ui-automation-stdout.txt',capture)
        result=self.validate()
        self.assertEqual(result.returncode,0,result.stderr)

    def test_missing_or_inconsistent_capture_receipt_fails(self):
        path=self.root/'app/conversation-window.json'
        original=path.read_text()
        capture=json.loads(original)
        capture['elapsed_ms']='99'
        path.write_text(json.dumps(capture))
        self.assertNotEqual(self.validate().returncode,0)
        path.write_text(original)
        capture=json.loads(original)
        capture['capture_finished_ms']='121'
        path.write_text(json.dumps(capture))
        self.instrumentation('app/conversation-ui-automation-stdout.txt',capture)
        self.assertNotEqual(self.validate().returncode,0)
        path.unlink()
        self.assertNotEqual(self.validate().returncode,0)

    def test_capture_fields_require_matching_original_results(self):
        path=self.root/'app/conversation-window.json'
        capture=json.loads(path.read_text())
        for key in capture:
            with self.subTest(key=key):
                changed={**capture,key:'failed' if key=='status' else '999999'}
                self.instrumentation('app/conversation-ui-automation-stdout.txt',changed)
                self.assertNotEqual(self.validate().returncode,0)
        self.instrumentation('app/conversation-ui-automation-stdout.txt',capture)
        result=self.validate()
        self.assertEqual(result.returncode,0,result.stderr)

    def test_capture_fields_require_unique_decimal_results(self):
        path=self.root/'app/conversation-window.json'
        raw=self.root/'app/conversation-ui-automation-stdout.txt'
        capture=json.loads(path.read_text())
        original=raw.read_text()
        for key,value in capture.items():
            with self.subTest(key=key):
                line=f'INSTRUMENTATION_RESULT: {key}={value}\n'
                raw.write_text(original+line)
                self.assertNotEqual(self.validate().returncode,0)
                raw.write_text(original.replace(line,''))
                self.assertNotEqual(self.validate().returncode,0)
        for value in ('+20','020','2e1','20.0',20):
            with self.subTest(value=value):
                changed={**capture,'capture_started_ms':value}
                path.write_text(json.dumps(changed))
                self.instrumentation('app/conversation-ui-automation-stdout.txt',changed)
                self.assertNotEqual(self.validate().returncode,0)


if __name__ == '__main__':
    unittest.main(verbosity=2)
