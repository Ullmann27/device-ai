import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {assessNodeOutcome, GATES} from '../../scripts/continuum-reliability-qa.mjs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

// Funktionale Prüfung der Preflight-Gates von scripts/continuum-code50-github-signer.sh
// mit einem Fake-`gh` (keine Netzwerkzugriffe, keine Geheimnisse, kein Signiervorgang).
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const script = path.join(root, 'scripts/continuum-code50-github-signer.sh');
const SHA = 'a'.repeat(40);
const RUN = '123456789';
const BRANCH = 'copilot/code51-master-integrator-20261108';
const hasTools = ['bash', 'jq', 'base64', 'sha256sum', 'curl', 'stat'].every(t => spawnSync('sh', ['-c', `command -v ${t}`]).status === 0);

const good = () => ({
  env: {
    deployment_branch_policy: {custom_branch_policies: true, protected_branches: false},
    protection_rules: [{type: 'required_reviewers', reviewers: [{type: 'User', reviewer: {login: 'Ullmann27'}}]}, {type: 'branch_policy'}]
  },
  policies: {branch_policies: [{name: BRANCH, type: 'branch'}]},
  run: {head_sha: SHA, head_branch: BRANCH, event: 'workflow_dispatch', status: 'completed', conclusion: 'success', path: '.github/workflows/continuum-code50-github-qa.yml'},
  jobs: {jobs: [{name: 'node-backend', conclusion: 'success'}, {name: 'android-host', conclusion: 'success'}]},
  releaseExists: false,
  latestTag: 'continuum-local-code50-473f79727e4b52509cc7d1af71e59b80ab9b6be5'
});

function runPreflight(mutate, {mode = 'preflight', overrides = {}} = {}) {
  const fx = good();
  mutate?.(fx);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'code50-signer-'));
  try {
    fs.writeFileSync(path.join(dir, 'fx.json'), JSON.stringify(fx));
    const fake = `#!/usr/bin/env node
const fs=require('fs');const fx=JSON.parse(fs.readFileSync(${JSON.stringify(path.join(dir, 'fx.json'))},'utf8'));
const a=process.argv.slice(2).join(' ');
const out=v=>{process.stdout.write(JSON.stringify(v));process.exit(0)};
if(a.includes('release view')) process.exit(fx.releaseExists?0:1);
if(a.includes('releases/latest')){ if(!fx.latestTag) process.exit(1); process.stdout.write(fx.latestTag+'\\n'); process.exit(0);}
if(a.endsWith('/deployment-branch-policies')) out(fx.policies);
if(/\\/environments\\/continuum-original-signing$/.test(a)) out(fx.env);
if(/\\/actions\\/runs\\/\\d+\\/jobs/.test(a)) out(fx.jobs);
if(/\\/actions\\/runs\\/\\d+$/.test(a)) out(fx.run);
process.exit(7);
`;
    fs.mkdirSync(path.join(dir, 'bin'));
    fs.writeFileSync(path.join(dir, 'bin/gh'), fake, {mode: 0o755});
    const env = {
      PATH: `${path.join(dir, 'bin')}:${process.env.PATH}`, HOME: dir, TMPDIR: dir,
      GITHUB_REPOSITORY: 'Ullmann27/device-ai', GITHUB_REF: `refs/heads/${BRANCH}`, GITHUB_EVENT_NAME: 'workflow_dispatch',
      GITHUB_SHA: SHA, SOURCE_SHA: SHA, QA_RUN_ID: RUN, OWNER_APPROVED_SHA: SHA, ...overrides
    };
    const r = spawnSync('bash', [script, mode], {env, encoding: 'utf8'});
    return {status: r.status, out: `${r.stdout}${r.stderr}`};
  } finally { fs.rmSync(dir, {recursive: true, force: true}); }
}

const failsWith = (res, code) => { assert.equal(res.status, 69, res.out); assert.match(res.out, new RegExp(`CODE51_SIGN_${code}\\b`), res.out); };

test('Preflight besteht nur mit allen Gates', {skip: !hasTools}, () => {
  const res = runPreflight();
  assert.equal(res.status, 0, res.out);
  assert.match(res.out, /CHECKPOINT=ENVIRONMENT_OWNER_REVIEWER_AND_EXACT_BRANCH_POLICY_VERIFIED/);
  assert.match(res.out, /CHECKPOINT=EXACT_SOURCE_QA_RUN_GREEN/);
  assert.match(res.out, /STATUS=PREFLIGHT_OK/);
  assert.match(res.out, /INFO=lifecycle_jdk21_job=PASS_REQUIRED_IN_ANDROID_HOST/);
});

test('Path-Suffix @ref des QA-Laufs wird akzeptiert, fremder Workflow nicht', {skip: !hasTools}, () => {
  assert.equal(runPreflight(f => { f.run.path += `@refs/heads/${BRANCH}`; }).status, 0);
  failsWith(runPreflight(f => { f.run.path = '.github/workflows/other.yml'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
});

test('Environment ohne Pflicht-Reviewer oder mit falscher Branch-Regel bricht VOR Geheimnissen ab', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.env.protection_rules = [{type: 'branch_policy'}]; }), 'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING');
  failsWith(runPreflight(f => { f.env.protection_rules[0].reviewers = [{type: 'User', reviewer: {login: 'someone-else'}}]; }), 'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING');
  failsWith(runPreflight(f => { f.env.deployment_branch_policy = null; }), 'ENVIRONMENT_BRANCH_POLICY_NOT_CUSTOM');
  failsWith(runPreflight(f => { f.policies.branch_policies = [{name: 'codex/continuum-code48-github-owner-20261107', type: 'branch'}]; }), 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT');
  failsWith(runPreflight(f => { f.policies.branch_policies.push({name: '*', type: 'branch'}); }), 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT');
});

test('QA-Lauf muss exakt, manuell, abgeschlossen und grün sein', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.run.head_sha = 'b'.repeat(40); }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.event = 'push'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.conclusion = 'failure'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.run.head_branch = 'main'; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  failsWith(runPreflight(f => { f.jobs.jobs[0].conclusion = 'failure'; }), 'NODE_BACKEND_NOT_GREEN');
  failsWith(runPreflight(f => { f.jobs.jobs = f.jobs.jobs.filter(j => j.name !== 'android-host'); }), 'ANDROID_HOST_NOT_GREEN');
});

test('Unreadable or different upgrade baseline cannot silently permit publication', {skip: !hasTools}, () => {
  failsWith(runPreflight(f => { f.latestTag = null; }), 'LATEST_RELEASE_UNREADABLE');
  failsWith(runPreflight(f => { f.latestTag = 'unrelated'; }), 'LATEST_RELEASE_IDENTITY_INVALID');
  failsWith(runPreflight(f => { f.latestTag = 'continuum-local-code50-' + SHA; }), 'CODE50_BASELINE_CHANGED');
});

test('Freigabe-SHA, Commit-Bindung, Branch, Event und Release-Zustand sind erzwungen', {skip: !hasTools}, () => {
  failsWith(runPreflight(null, {overrides: {OWNER_APPROVED_SHA: 'b'.repeat(40)}}), 'APPROVED_SHA_DIFFERS_FROM_SOURCE');
  failsWith(runPreflight(null, {overrides: {GITHUB_SHA: 'b'.repeat(40)}}), 'SOURCE_DIFFERS_FROM_RUN_COMMIT');
  failsWith(runPreflight(null, {overrides: {SOURCE_SHA: 'xyz', OWNER_APPROVED_SHA: 'xyz'}}), 'SOURCE_SHA_FORMAT');
  failsWith(runPreflight(null, {overrides: {GITHUB_REF: 'refs/heads/main'}}), 'WRONG_BRANCH');
  failsWith(runPreflight(null, {overrides: {GITHUB_EVENT_NAME: 'push'}}), 'WRONG_EVENT');
  failsWith(runPreflight(null, {overrides: {QA_RUN_ID: '12; rm -rf /'}}), 'QA_RUN_FORMAT');
  failsWith(runPreflight(f => { f.releaseExists = true; }), 'RELEASE_ALREADY_EXISTS');
  failsWith(runPreflight(f => { f.latestTag = 'continuum-local-code51-' + SHA; }), 'NOT_HIGHER_THAN_LATEST_RELEASE');
  failsWith(runPreflight(null, {mode: 'unbekannt'}), 'BAD_MODE');
});

test('Sign-Modus ohne Android-Werkzeuge und ohne Schlüssel bricht ab, ohne etwas zu veröffentlichen', {skip: !hasTools}, () => {
  failsWith(runPreflight(null, {mode: 'sign', overrides: {ANDROID_HOME: ''}}), 'ANDROID_HOME_MISSING');
});

const CODE55_BRANCH = 'integrate/code55-apk-fable-20261112';
const APK_RUN = '123456790';
const TREE = 'b'.repeat(40);
const AVATAR = 'c'.repeat(40);
const BASELINE_TAG = 'continuum-local-code51-6c23d295e2fca2d4e5950a866547733ec5ff22b0';
const BASELINE_DIGEST = '1928f58609eeb71a674f75cd66faaa6b4c4ad44f693d060223c664ac63cadfc1';
const ORIGINAL_CERT = '6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23';
const has55Tools = hasTools && ['python3', 'git', 'node'].every(tool =>
  spawnSync('sh', ['-c', 'command -v ' + tool]).status === 0);
const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const xmlCases = count => '<testsuite name="synthetic.fixture" tests="' + count +
  '" failures="0" errors="0" skipped="0">' + Array.from({length: count},
  (_, index) => '<testcase classname="synthetic.fixture" name="case' + index + '"/>').join('') + '</testsuite>';
const encoded = value => JSON.stringify(value) + '\n';

// Synthetic receipts exercise gates only.
function code55Fixture() {
  const fixture = {...good(), sha: SHA, tree: TREE, avatar: AVATAR, baselineTag: BASELINE_TAG,
    baselineDigest: BASELINE_DIGEST, baselineHash: BASELINE_DIGEST, baselineCert: ORIGINAL_CERT,
    latestTag: BASELINE_TAG, files: {node: [], host: [], native: [], unsigned: []},
    runs: {}, jobsByRun: {}};
  fixture.policies.branch_policies = [{name: CODE55_BRANCH, type: 'branch'}];
  for (const [run, workflow, names] of [
    [RUN, 'continuum-code55-full-qa.yml', ['node-backend', 'full-android', 'native-pdf', 'complete-qa']],
    [APK_RUN, 'continuum-code55-owner-apk.yml', ['scoped-android', 'unsigned-arm64']]
  ]) {
    fixture.runs[run] = {id: Number(run), head_sha: SHA, head_branch: CODE55_BRANCH,
      event: 'push', status: 'completed', conclusion: 'success', run_attempt: 1,
      run_started_at: '2026-11-13T00:00:00Z', repository: {id: 123, full_name: 'Ullmann27/device-ai'},
      head_repository: {id: 123}, path: '.github/workflows/' + workflow};
    fixture.jobsByRun[run] = {total_count: names.length,
      jobs: names.map(name => ({name, status: 'completed', conclusion: 'success'}))};
  }
  for (const profile of ['critical', 'full-node']) {
    const directory = profile === 'critical' ? 'code55-critical' : 'code55-full';
    const report = {schemaVersion: 1, profile, status: 'PASS', sourceUnchanged: true, cancelled: false,
      source: {commit: SHA, branch: CODE55_BRANCH}, checks: []};
    for (const id of profile === 'critical' ? GATES.map(gate => gate.id) : ['full-node']) {
      const xml = xmlCases(profile === 'critical' ? 1 : 2498);
      const outcome = {ok: true, code: 0, error: null, exitSignal: null, terminationConfirmed: true};
      const assessment = assessNodeOutcome(outcome, xml, root);
      assert.equal(assessment.status, 'PASS');
      report.checks.push({id, ...outcome, ...assessment, junitSha256: sha256(xml)});
      fixture.files.node.push([directory + '/' + id + '.xml', xml]);
    }
    fixture.files.node.push([directory + '/report.json', encoded(report)]);
  }
  fixture.files.host.push(['code55-source.txt', 'source_sha=' + SHA + '\nsource_tree=' + TREE + '\n'],
    ['junit/TEST-synthetic.xml', xmlCases(5321)]);
  const native = (directory, filename, values) => {
    fixture.files.native.push([directory + 'source-sha.txt', SHA + '\n'],
      [directory + 'source-tree.txt', TREE + '\n'],
      [directory + filename, Object.entries(values).map(([key, value]) =>
        'INSTRUMENTATION_RESULT: ' + key + '=' + value).join('\n') + '\nINSTRUMENTATION_CODE: -1\n']);
  };
  native('', 'native-pdf.txt', {status: 'ok', sdk: 35, provider_queries: 1, provider_opens: 1, page_count: 1, rendered_ink_pixels: 506});
  native('filament/', 'native-filament.txt', {status: 'ok', sdk: 35, last_phase: 'finished', context: 'SYNTHETIC FILAMENT TEST; fixture',
    native_quads: 2, native_materials: 2, native_textures: 2, upload_completions: 1,
    rendered_frames: 1, presented_frames: 1, output_frames: 1, content_cleared: true, panels_released: true, engine_released: true});
  fixture.files.native.push(['filament/surfaceflinger.txt', 'GLES: SwiftShader synthetic fixture\n']);
  const apk = 'SYNTHETIC_NOT_AN_APK';
  const source = {commit: SHA, tree: TREE, status: '', original_avatar_git_blob: AVATAR};
  const receipt = {status: 'PASS', artifactStatus: 'UNSIGNED_NOT_INSTALLABLE', expectedSource: SHA,
    expectedTree: TREE, signing: 'NOT_USED', sourceBefore: source, sourceAfter: source,
    validatorSha256: sha256(fs.readFileSync(path.join(root, 'tools/code55-apk/validator.py'))),
    zipalign16KiB: 'PASS', unsignedDiagnostic: 'EXPECTED_MISSING_SIGNATURE',
    bytes: Buffer.byteLength(apk), sha256: sha256(apk),
    identity: {package: 'at.heinz.deviceai.continuumlocal', versionCode: 55, versionName: '0.3.54',
      abi: 'arm64-v8a', minSdk: 26, targetSdk: 36, debuggable: false},
    archive: {zipCrc: 'PASS', jarSignatureFiles: [], apkSigningBlock: false, assetFilesCompared: 1,
      nativeLibraries: ['libfilament-jni.so', 'libgltfio-jni.so', 'libopencv_java4.so', 'libc++_shared.so',
        'liblitertlm_jni.so'].map(name => 'lib/arm64-v8a/' + name)}};
  fixture.files.unsigned.push(['app-release-unsigned.apk', apk], ['audit/RESULT.json', encoded(receipt)],
    ['audit/SHA256SUMS.txt', sha256(apk) + '  app-release-unsigned.apk\n'],
    ['CODE55-SOURCE.txt', 'source=' + SHA + '\ntree=' + TREE +
      '\npackage=at.heinz.deviceai.continuumlocal\nversionCode=55\nversionName=0.3.54\noriginalSigner=NOT_USED\nFold7=NOT_RUN\n']);
  return fixture;
}

function change(fixture, group, suffix, transform) {
  const entry = fixture.files[group].find(([name]) => name.endsWith(suffix));
  assert.ok(entry, suffix);
  entry[1] = transform(entry[1]);
}
function jsonChange(fixture, group, suffix, transform) {
  change(fixture, group, suffix, value => {
    const parsed = JSON.parse(value); transform(parsed); return encoded(parsed);
  });
}

function runCode55(mutate = () => {}, metadata = () => {}, {mode = 'preflight', overrides = {}} = {}) {
  const fixture = code55Fixture();
  mutate(fixture);
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'code55-signer-fixture-'));
  const fixtureFile = path.join(directory, 'fixture.json');
  const callsFile = path.join(directory, 'calls.jsonl');
  try {
    fixture.artifactsByRun = {[RUN]: {total_count: 3, artifacts: []}, [APK_RUN]: {total_count: 1, artifacts: []}};
    const groups = [
      ['node', RUN, 'code55-github-node-evidence'], ['host', RUN, 'Code55-full-Android-source-QA'],
      ['native', RUN, 'Code55-native-PDF-emulator-QA'], ['unsigned', APK_RUN, 'Code55-UNSIGNED-NOT-INSTALLABLE']
    ];
    const buildZip = 'import json,sys,zipfile\n' +
      'with zipfile.ZipFile(sys.argv[1],"w",compression=zipfile.ZIP_DEFLATED) as z:\n' +
      ' for name,content in json.loads(sys.stdin.read()): z.writestr(name,content)\n';
    for (const [index, [group, run, name]] of groups.entries()) {
      const id = 1000 + index;
      const destination = path.join(directory, id + '.zip');
      const result = spawnSync('python3', ['-c', buildZip, destination],
        {input: JSON.stringify(fixture.files[group]), encoding: 'utf8'});
      assert.equal(result.status, 0, result.stderr);
      const bytes = fs.readFileSync(destination);
      fixture.artifactsByRun[run].artifacts.push({id, name, expired: false,
        created_at: '2026-11-13T00:10:00Z', size_in_bytes: bytes.length, digest: 'sha256:' + sha256(bytes),
        workflow_run: {id: Number(run), head_sha: SHA, head_branch: CODE55_BRANCH,
          repository_id: 123, head_repository_id: 123}});
    }
    metadata(fixture);
    fs.writeFileSync(fixtureFile, JSON.stringify(fixture));
    const bin = path.join(directory, 'bin');
    const androidTools = path.join(directory, 'android/build-tools/35.0.0');
    fs.mkdirSync(bin); fs.mkdirSync(androidTools, {recursive: true});
    const driver = "#!/usr/bin/env node\nconst fs = require('node:fs');\nconst path = require('node:path');\nconst {spawnSync} = require('node:child_process');\nconst fx = JSON.parse(fs.readFileSync(process.env.SIGNER_FIXTURE, 'utf8'));\nconst command = path.basename(process.argv[1]);\nconst args = process.argv.slice(2);\nconst history = fs.existsSync(process.env.SIGNER_CALLS)\n  ? fs.readFileSync(process.env.SIGNER_CALLS, 'utf8').trim().split('\\n').filter(Boolean).map(JSON.parse) : [];\nfs.appendFileSync(process.env.SIGNER_CALLS, JSON.stringify([command, ...args]) + '\\n');\nconst out = value => { process.stdout.write(JSON.stringify(value)); process.exit(0); };\nconst line = value => { process.stdout.write(value + '\\n'); process.exit(0); };\nconst baseline = args.some(arg => arg.endsWith('/Continuum-Lokal-' + fx.baselineDigest + '.apk'));\nif (command === 'git') {\n  if (args[0] === 'status') process.exit(0);\n  if (args[1] === 'HEAD') line(fx.sha);\n  if (args[1] === 'HEAD^{tree}') line(fx.tree);\n  if (args[1] === 'HEAD:app/src/main/assets/avatar3d/continuum-avatar.glb') line(fx.avatar);\n}\nif (command === 'sha256sum') {\n  if (baseline) line(fx.baselineHash + '  ' + args[0]);\n  const result = spawnSync(process.env.REAL_SHA256SUM, args, {encoding: 'buffer'});\n  process.stdout.write(result.stdout); process.stderr.write(result.stderr); process.exit(result.status);\n}\nif (command === 'aapt2') line(\"package: name='at.heinz.deviceai.continuumlocal' versionCode='\" +\n  (baseline ? '51' : '55') + \"' versionName='\" + (baseline ? '0.3.50' : '0.3.54') +\n  \"'\\nnative-code: 'arm64-v8a'\\nsdkVersion:'26'\\ntargetSdkVersion:'36'\");\nif (command === 'zipalign' && args[0] === '-c') process.exit(fx.alignmentFailure ? 1 : 0);\nif (command === 'apksigner' && args[0] === 'verify') {\n  if (baseline) line('Verified using v2 scheme (APK Signature Scheme v2): true\\nSigner #1 certificate SHA-256 digest: ' + fx.baselineCert);\n  process.stderr.write('DOES NOT VERIFY\\nERROR: Missing META-INF/MANIFEST.MF\\n'); process.exit(1);\n}\nif (command === 'gh') {\n  if (args[0] === 'release' && args[1] === 'view') process.exit(fx.releaseExists ? 0 : 1);\n  if (args[0] === 'release' && args[1] === 'download' && args[2] === fx.baselineTag) {\n    const destination = args[args.indexOf('--dir') + 1];\n    const target = path.join(destination, 'Continuum-Lokal-' + fx.baselineDigest + '.apk');\n    const fd = fs.openSync(target, 'wx'); fs.ftruncateSync(fd, 102197134); fs.closeSync(fd); process.exit(0);\n  }\n  if (args[0] === 'api') {\n    const uri = args[1];\n    if (uri.endsWith('/deployment-branch-policies')) out(fx.policies);\n    if (uri.endsWith('/environments/continuum-original-signing')) out(fx.env);\n    if (uri.endsWith('/releases/latest')) {\n      if (!fx.latestTag) process.exit(1); line(fx.latestTag);\n    }\n    let match = uri.match(/\\/actions\\/runs\\/(\\d+)\\/attempts\\/\\d+\\/jobs\\?per_page=100$/);\n    if (match) out(fx.jobsByRun[match[1]]);\n    match = uri.match(/\\/actions\\/runs\\/(\\d+)\\/artifacts\\?per_page=100$/);\n    if (match) out(fx.artifactsByRun[match[1]]);\n    match = uri.match(/\\/actions\\/artifacts\\/(\\d+)\\/zip$/);\n    if (match) { process.stdout.write(fs.readFileSync(path.join(process.env.SIGNER_FIXTURE_DIR, match[1] + '.zip'))); process.exit(0); }\n    match = uri.match(/\\/actions\\/runs\\/(\\d+)$/);\n    if (match) {\n      const previous = history.filter(call => call[0] === 'gh' && call[1] === 'api' && call[2] === uri).length;\n      out(previous && fx.afterRun?.[match[1]] ? fx.afterRun[match[1]] : fx.runs[match[1]]);\n    }\n  }\n}\nprocess.stderr.write('Unexpected fixture command: ' + JSON.stringify([command, ...args]) + '\\n');\nprocess.exit(7);\n";
    for (const command of ['gh', 'git', 'sha256sum'])
      fs.writeFileSync(path.join(bin, command), driver, {mode: 0o755});
    for (const command of ['aapt2', 'apksigner', 'zipalign'])
      fs.writeFileSync(path.join(androidTools, command), driver, {mode: 0o755});
    const realSha = spawnSync('sh', ['-c', 'command -v sha256sum'], {encoding: 'utf8'}).stdout.trim();
    const environment = {PATH: bin + path.delimiter + process.env.PATH, HOME: directory, TMPDIR: directory,
      GITHUB_REPOSITORY: 'Ullmann27/device-ai', GITHUB_REF: 'refs/heads/' + CODE55_BRANCH,
      GITHUB_EVENT_NAME: 'workflow_dispatch', GITHUB_SHA: SHA, SOURCE_SHA: SHA, QA_RUN_ID: RUN,
      APK_RUN_ID: APK_RUN, OWNER_APPROVED_SHA: SHA, ANDROID_HOME: path.join(directory, 'android'),
      SIGNER_FIXTURE: fixtureFile, SIGNER_FIXTURE_DIR: directory, SIGNER_CALLS: callsFile,
      REAL_SHA256SUM: realSha, ...overrides};
    const result = spawnSync('bash', [script, mode], {cwd: root, env: environment, encoding: 'utf8', timeout: 60_000});
    const calls = fs.existsSync(callsFile) ? fs.readFileSync(callsFile, 'utf8') : '';
    assert.doesNotMatch(calls, /"(sign|keytool|create|edit)"/);
    return {status: result.status, out: String(result.stdout) + String(result.stderr), calls};
  } finally { fs.rmSync(directory, {recursive: true, force: true}); }
}
const fails55 = (result, code) => {
  assert.equal(result.status, 69, result.out);
  assert.match(result.out, new RegExp('CODE55_SIGN_' + code + '\\b'));
};

test('Code55 preflight recomputes complete proofs; sign still stops before absent keys', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  const result = runCode55();
  assert.equal(result.status, 0, result.out);
  for (const checkpoint of ['CODE55_SOURCE_BOUND_NODE_JUNIT_NATIVE_UNSIGNED_PROOFS_VERIFIED',
    'PINNED_CODE51_BASELINE_VERIFIED', 'PREFLIGHT_OK']) assert.ok(result.out.includes(checkpoint), result.out);
  fails55(runCode55(undefined, undefined, {mode: 'sign'}), 'ORIGINAL_SECRETS_MISSING');
});

test('Code55 rejects mismatched run identity, workflow, branch, approval and unfinished results', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  for (const run of [RUN, APK_RUN]) for (const [key, value] of [
    ['id', 9], ['head_sha', TREE], ['head_branch', 'main'], ['path', '.github/workflows/other.yml'],
    ['status', 'in_progress'], ['conclusion', 'failure'], ['event', 'pull_request']
  ]) fails55(runCode55(f => { f.runs[run][key] = value; }), 'QA_IDENTITY_OR_RESULT_MISMATCH');
  fails55(runCode55(undefined, undefined, {overrides: {APK_RUN_ID: RUN}}), 'APK_RUN_FORMAT');
  fails55(runCode55(undefined, undefined, {overrides: {OWNER_APPROVED_SHA: TREE}}), 'APPROVED_SHA_DIFFERS_FROM_SOURCE');
  fails55(runCode55(f => { f.env.protection_rules = []; }), 'ENVIRONMENT_REQUIRED_OWNER_REVIEWER_MISSING');
  fails55(runCode55(f => { f.policies.branch_policies.push({name: '*', type: 'branch'}); }), 'ENVIRONMENT_BRANCH_POLICY_NOT_EXACT');
});

test('Code55 requires every full and APK job exactly once and successful', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  for (const run of [RUN, APK_RUN]) {
    fails55(runCode55(f => { f.jobsByRun[run].jobs.pop(); f.jobsByRun[run].total_count--; }), 'REQUIRED_JOBS_NOT_GREEN');
    fails55(runCode55(f => { f.jobsByRun[run].jobs[0].conclusion = 'failure'; }), 'REQUIRED_JOBS_NOT_GREEN');
    fails55(runCode55(f => { f.jobsByRun[run].jobs[1] = f.jobsByRun[run].jobs[0]; }), 'REQUIRED_JOBS_NOT_GREEN');
  }
});

test('Code55 rejects expired, renamed, stale, duplicated and mixed-run artifacts', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  for (const [key, value] of [['expired', true], ['name', 'renamed'], ['created_at', '2020-01-01T00:00:00Z']])
    fails55(runCode55(undefined, f => { f.artifactsByRun[RUN].artifacts[0][key] = value; }), 'ARTIFACT_IDENTITY_OR_EXPIRY');
  fails55(runCode55(undefined, f => { f.artifactsByRun[RUN].artifacts[0].workflow_run.head_sha = TREE; }), 'ARTIFACT_IDENTITY_OR_EXPIRY');
  fails55(runCode55(undefined, f => { f.artifactsByRun[RUN].artifacts[0].workflow_run.id = Number(APK_RUN); }), 'ARTIFACT_IDENTITY_OR_EXPIRY');
  fails55(runCode55(undefined, f => { const list = f.artifactsByRun[RUN]; list.artifacts.push(list.artifacts[0]); list.total_count++; }), 'ARTIFACT_IDENTITY_OR_EXPIRY');
});

test('Code55 verifies downloaded archive hash, size, traversal and duplicate paths', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(undefined, f => { f.artifactsByRun[RUN].artifacts[0].digest = 'sha256:' + '0'.repeat(64); }), 'ARTIFACT_DIGEST_MISMATCH');
  fails55(runCode55(undefined, f => { f.artifactsByRun[RUN].artifacts[0].size_in_bytes++; }), 'ARTIFACT_SIZE_MISMATCH');
  fails55(runCode55(f => { f.files.node.push(['../escaped', 'unsafe']); }), 'ARTIFACT_ARCHIVE_UNSAFE');
  fails55(runCode55(f => { f.files.node.push(f.files.node[0]); }), 'ARTIFACT_ARCHIVE_UNSAFE');
});

test('Code55 rejects malformed JSON, duplicate fields and mismatched source receipts', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(f => change(f, 'unsigned', 'RESULT.json', () => '{broken')), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'unsigned', 'RESULT.json', value => '{"status":"PASS",' + value.slice(1))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => jsonChange(f, 'unsigned', 'RESULT.json', value => { value.expectedSource = TREE; })), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'unsigned', 'CODE55-SOURCE.txt', value => value + 'source=' + SHA + '\n')), 'CODE55_RECEIPT_OR_APK_INVALID');
});

test('Code55 requires actual unsigned hash and the preserved 16 KiB audit', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(f => change(f, 'unsigned', 'app-release-unsigned.apk', value => value + 'tampered')), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'unsigned', 'SHA256SUMS.txt', value => '0'.repeat(64) + value.slice(64))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => jsonChange(f, 'unsigned', 'RESULT.json', value => { value.zipalign16KiB = 'FAIL'; })), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => { f.alignmentFailure = true; }), 'CODE55_RECEIPT_OR_APK_INVALID');
});

test('Code55 recomputes complete JUnit and refuses hidden failures, unknown skips or sparse coverage', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(f => change(f, 'host', 'TEST-synthetic.xml', () => xmlCases(1))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'host', 'TEST-synthetic.xml', value => value.replace('failures="0"', 'failures="1"'))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'host', 'TEST-synthetic.xml', value => value.replace('name="case0"/>', 'name="case0"><failure/></testcase>'))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'host', 'TEST-synthetic.xml', value => value.replace('skipped="0"', 'skipped="1"')
    .replace('name="case0"/>', 'name="case0"><skipped/></testcase>'))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'host', 'TEST-synthetic.xml', value => value.replace('name="case1"', 'name="case0"'))), 'CODE55_RECEIPT_OR_APK_INVALID');
});

test('Code55 requires both successful native completions and their exact source', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  for (const filename of ['native-pdf.txt', 'native-filament.txt']) {
    fails55(runCode55(f => { f.files.native = f.files.native.filter(([name]) => !name.endsWith(filename)); }), 'CODE55_RECEIPT_OR_APK_INVALID');
    fails55(runCode55(f => change(f, 'native', filename, value => value.replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'))), 'CODE55_RECEIPT_OR_APK_INVALID');
  }
  fails55(runCode55(f => change(f, 'native', 'filament/source-sha.txt', () => TREE)), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'native', 'native-pdf.txt', value => value.replace('sdk=35', 'sdk=34'))), 'CODE55_RECEIPT_OR_APK_INVALID');
  fails55(runCode55(f => change(f, 'native', 'native-filament.txt', value => value.replace('last_phase=finished', 'last_phase=rendering'))), 'CODE55_RECEIPT_OR_APK_INVALID');
});

test('Code55 independently reassesses Node XML and rejects tampered or failed reports', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(f => change(f, 'node', 'full-node.xml', value => value.replace('name="case0"', 'name="changed"'))), 'CODE55_NODE_PROOF_INVALID');
  fails55(runCode55(f => jsonChange(f, 'node', 'code55-full/report.json', value => { value.status = 'FAIL'; })), 'CODE55_NODE_PROOF_INVALID');
  fails55(runCode55(f => change(f, 'node', 'code55-full/report.json', value => '{"status":"PASS",' + value.slice(1))), 'CODE55_RECEIPT_OR_APK_INVALID');
});

test('Code55 refuses a rerun racing artifact validation or an unverified upgrade baseline', () => {
  assert.ok(has55Tools, 'Code55 signer tests require bash, jq, Python and Node');
  fails55(runCode55(f => { f.afterRun = {[RUN]: {...f.runs[RUN], run_attempt: 2}}; }), 'QA_ATTEMPT_CHANGED');
  fails55(runCode55(f => { f.latestTag = 'continuum-local-code51-' + SHA; }), 'CODE51_BASELINE_CHANGED');
  fails55(runCode55(f => { f.baselineHash = '0'.repeat(64); }), 'BASELINE_SHA_MISMATCH');
  fails55(runCode55(f => { f.baselineCert = '0'.repeat(64); }), 'BASELINE_CERTIFICATE_MISMATCH');
});
