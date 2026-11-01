import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { closeSync, existsSync, lstatSync, openSync, readFileSync, readSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Architecture proof is a local QA receipt, NOT a signature or proof of AGI.
 * It verifies concrete build/test artifacts and explicitly leaves unsupported
 * capabilities unverified. No owner data, model weights or network access.
 */
export const REQUIRED_LOGS = Object.freeze([
  'ARCHITECTURE-PROOF-TESTS.txt',
  'TERMUX-RECOVERY-TESTS.txt',
  'NODE-TESTS.txt',
  'FRONTIER-NODE-TESTS.txt',
  'RELEASE-CONTRACT-TESTS.txt',
  'JARVIS-READBACK-TESTS.txt',
  'BACKEND-TESTS.txt',
  'GRADLE-QA.txt',
  'ASSEMBLY.txt',
  'APK-BADGING.txt'
]);

export const REQUIRED_NODE_LOGS = Object.freeze([
  'ARCHITECTURE-PROOF-TESTS.txt',
  'TERMUX-RECOVERY-TESTS.txt',
  'NODE-TESTS.txt',
  'FRONTIER-NODE-TESTS.txt',
  'RELEASE-CONTRACT-TESTS.txt',
  'JARVIS-READBACK-TESTS.txt',
  'BACKEND-TESTS.txt'
]);

export const REQUIRED_JUNIT = Object.freeze([
  'at.heinz.deviceai.cognitive.NetworkTurnConstraintTest',
  'at.heinz.deviceai.cognitive.Cdx1EvidenceSlotBinderTest',
  'at.heinz.deviceai.intelligence.ewm.Ewm2ProductEquivalenceTest',
  'at.heinz.deviceai.learning.ContinuumEvidenceWindowTest',
  'at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbeTest',
  'at.heinz.deviceai.learning.span.NaturalLocationVerificationTest',
  'at.heinz.deviceai.learning.span.NaturalEntitySpanCoreTest',
  'at.heinz.deviceai.learning.span.LocationEvidenceSnapshotTest',
  'at.heinz.deviceai.learning.span.NaturalLocationLabComparisonTest',
  'at.heinz.deviceai.learning.span.RoleAwareShadowTest',
  'at.heinz.deviceai.learning.span.DualEvidenceReadContractTest',
  'at.heinz.deviceai.cognitive.ResearchDialogueHandoffTest',
  'at.heinz.deviceai.cognitive.TurnScopedInternetModeTest',
  'at.heinz.deviceai.cognitive.InternetConsentPolicyTest',
  'at.heinz.deviceai.cognitive.JarvisUnifiedEvidenceContractTest',
  'at.heinz.deviceai.learning.OwnerMemoryAgeSpecialistTest',
]);

function regular(path, maxBytes) {
  const st = lstatSync(path);
  if (!st.isFile() || st.isSymbolicLink() || st.size <= 0 || st.size > maxBytes)
    throw Error('Invalid regular QA artifact: ' + basename(path));
  return st;
}

function digestFile(path, maxBytes) {
  const before = regular(path, maxBytes);
  const hash = createHash('sha256');
  const fd = openSync(path, 'r');
  try {
    const buffer = Buffer.allocUnsafe(1024 * 1024);
    let length = 0;
    while (true) {
      const n = readSync(fd, buffer, 0, buffer.length, null);
      if (n === 0) break;
      length += n;
      if (length > maxBytes) throw Error('QA artifact exceeded size bound');
      hash.update(buffer.subarray(0, n));
    }
    const after = regular(path, maxBytes);
    if (length !== before.size || after.size !== before.size ||
        after.mtimeMs !== before.mtimeMs || after.ino !== before.ino)
      throw Error('QA artifact changed while hashing');
    return { bytes: length, sha256: hash.digest('hex') };
  } finally {
    closeSync(fd);
  }
}

/**
 * TAP summaries come from executed Node tests and are also guarded by shell exit
 * codes. Hashes alone are not evidence that a test process passed.
 */
export function parseNodeTestSummary(report) {
  const lines = report.trimEnd().split(/\r?\n/);
  const values = new Map();
  for (const line of lines) {
    const m = /^# (tests|pass|fail|cancelled|skipped|todo) (\d+)$/.exec(line);
    if (!m) continue;
    if (values.has(m[1])) throw Error('Duplicate TAP summary: ' + m[1]);
    values.set(m[1], Number(m[2]));
  }
  const keys = ['tests','pass','fail','cancelled','skipped','todo'];
  if (keys.some(k => !Number.isSafeInteger(values.get(k))))
    throw Error('Missing machine-readable TAP summary');
  if (values.get('tests') <= 0 || values.get('pass') !== values.get('tests') ||
      values.get('fail') !== 0 || values.get('cancelled') !== 0 ||
      values.get('skipped') !== 0 || values.get('todo') !== 0)
    throw Error('Node TAP failed, skipped or incomplete');
  if (lines.filter(x => /^\s*ok \d+\b/.test(x)).length !== values.get('tests'))
    throw Error('Node TAP testcase count mismatches summary');
  return Object.fromEntries(keys.map(k => [k,values.get(k)]));
}

/** ElementTree parses exact attribute names and all actual testcase elements. */
function junitProof(junitDir, klass) {
  const file = join(junitDir,'TEST-' + klass + '.xml');
  regular(file, 1024 * 1024);
  const validator = join(dirname(fileURLToPath(import.meta.url)),
    'continuum-junit-validator.py');
  let parsed;
  try {
    parsed = JSON.parse(execFileSync('python3',[validator,file,klass],{
      encoding:'utf8',maxBuffer:32768,timeout:10000,stdio:['ignore','pipe','pipe']
    }));
  } catch (e) {
    throw Error('Structured JUnit rejected ' + klass + ': ' +
      String(e.stderr || e.message).slice(0,300));
  }
  if (!Number.isSafeInteger(parsed.executed) || parsed.executed <= 0 ||
      parsed.skipped !== 0 || parsed.failures !== 0 || parsed.errors !== 0)
    throw Error('JUnit parser returned invalid counts: ' + klass);
  return { tests:parsed.executed, ...digestFile(file,1024 * 1024) };
}

export function collectArchitectureProof({ root, sha, versionCode, runId }) {
  if (!/^[a-f0-9]{40}$/.test(sha) ||
      !Number.isInteger(versionCode) || versionCode < 36 || versionCode > 9999 ||
      !/^[0-9a-f]{8}-[0-9a-f-]{27,36}$/.test(runId))
    throw Error('Invalid pinned source or version');
  const qaDir = join(root, 'continuum-m8-render-qa');
  const statusPath = join(qaDir, 'STATUS.txt');
  regular(statusPath, 4096);
  const status = readFileSync(statusPath, 'utf8');
  const expectedLines = [
    'status=BUILD_AND_TEST_GREEN_UNSIGNED_NOT_INSTALLABLE',
    'source_commit=' + sha,
    'run_id=' + runId,
    'package=at.heinz.deviceai.continuumlocal',
    'version_code=' + versionCode,
    'apk_signed=FALSE',
    'apk_published=FALSE'
  ];
  const statusLines = status.trim().split('\n');
  if (expectedLines.length !== statusLines.length ||
      expectedLines.some((line, i) => line !== statusLines[i]))
    throw Error('QA STATUS mismatch, incomplete checks or false release claim');

  const logs = {};
  for (const name of REQUIRED_LOGS)
    logs[name] = digestFile(join(qaDir, name), 16 * 1024 * 1024);
  const nodeTestSummaries = {};
  for (const name of REQUIRED_NODE_LOGS) {
    try {
      nodeTestSummaries[name] = parseNodeTestSummary(readFileSync(join(qaDir,name),'utf8'));
    } catch (error) {
      throw Error('Node/TAP receipt rejected: ' + name + ': ' + error.message);
    }
  }
  for (const name of ['GRADLE-QA.txt', 'ASSEMBLY.txt']) {
    const contents = readFileSync(join(qaDir, name), 'utf8');
    if (!/BUILD SUCCESSFUL in \d/.test(contents))
      throw Error('Missing successful Gradle transcript: ' + name);
  }
  const badging = readFileSync(join(qaDir, 'APK-BADGING.txt'),'utf8');
  const packageLines = badging.split(/\r?\n/).filter(line => line.startsWith('package: '));
  if (packageLines.length !== 1) throw Error('APK identity: missing or ambiguous binary package line');
  const attributes = [...packageLines[0].matchAll(/(?:^|\s)([A-Za-z][A-Za-z0-9]*)='([^']*)'(?=\s|$)/g)];
  const fields = Object.fromEntries(attributes.map(match => [match[1],match[2]]));
  if (new Set(attributes.map(match => match[1])).size !== attributes.length ||
      fields.name !== 'at.heinz.deviceai.continuumlocal' || fields.versionCode !== String(versionCode) ||
      fields.versionName !== '0.3.' + (versionCode - 1))
    throw Error('APK identity mismatch: binary package/version differs from pinned receipt');
  const apkIdentity = {packageName:fields.name,versionCode:Number(fields.versionCode),versionName:fields.versionName};
  const suites = {};
  const junitDir = join(root, 'app/build/test-results/testDebugUnitTest');
  for (const klass of REQUIRED_JUNIT)
    suites[klass] = junitProof(junitDir, klass);
  const apk = digestFile(join(root, 'app/build/outputs/apk/release/app-release-unsigned.apk'), 2_000_000_000);
  const aab = digestFile(join(root, 'app/build/outputs/bundle/release/app-release.aab'), 2_000_000_000);
  if (apk.bytes < 1_000_000 || aab.bytes < 1_000_000)
    throw Error('Unsigned Android artifacts too small');

  // This is test provenance only. Recorded source texts and model outputs
  // have not been independently established as true.
  return {
    schema: 3,
    kind: 'CONTINUUM_ARCHITECTURE_QA_RECEIPT',
    sourceCommit: sha,
    runId,
    packageName: 'at.heinz.deviceai.continuumlocal',
    versionCode,
    scope: 'HOST_QA_UNSIGNED_ONLY',
    proofMethod: 'LOCAL_PROCESS_EXIT_CODES_AND_REPORT_HASHES_NOT_INDEPENDENT_ATTESTATION',
    qa: {
      termuxRecovery: 'AUTOMATED_TEST_RUN_PASS',
      toolRouting: 'AUTOMATED_TEST_RUN_PASS',
      evidenceBinding: 'AUTOMATED_JUNIT_PASS_NOT_FACTUAL_TRUTH',
      memoryRegression: 'AUTOMATED_JUNIT_PASS',
      androidBuildAndLint: 'AUTOMATED_TEST_RUN_PASS',
      codingAgentContracts: 'AUTOMATED_TEST_RUN_PASS',
      backendMockTests: 'AUTOMATED_TEST_RUN_PASS',
      unsignedOwnerApkAndAab: 'ASSEMBLED_NOT_INSTALLABLE',
      logs, nodeTestSummaries, junitSuites: suites, unsignedApkIdentity: apkIdentity, unsignedApk: apk, unsignedAab: aab
    },
    notYetProven: [
      'CDX1_20_OF_20_BLIND_SUPPORTED_ANSWERS',
      'CDX1_5_OF_5_MISSING_EVIDENCE_ABSTENTIONS',
      'SUSTAINED_LOCAL_REASONING_BENCHMARK',
      'FOLD7_REAL_DEVICE_SMOKE_TEST',
      'CLOSED_REPAIR_BUILD_PROOF_ON_DEVICE',
      'ORIGINAL_OWNER_SIGNED_AND_PUBLISHED_APK',
      'LONG_TERM_LEARNING_WITHOUT_DRIFT',
      'SUPERINTELLIGENCE_OR_MODEL_PARITY'
    ]
  };
}

function main() {
  const root = resolve(process.cwd());
  const sha = execFileSync('git', ['rev-parse', '--verify', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();
  const versionCode = Number(process.env.CONTINUUM_PROOF_VERSION);
  const pinned = process.env.CONTINUUM_PROOF_SHA;
  if (sha !== pinned) throw Error('Checkout SHA differs from owner-proof pin');
  const result = collectArchitectureProof({ root, sha, versionCode,
    runId: process.env.CONTINUUM_PROOF_RUN_ID });
  const out = join(root, 'continuum-m8-render-qa/ARCHITECTURE-PROOF.json');
  if (existsSync(out)) throw Error('Proof already exists: refusing overwrite');
  writeFileSync(out, JSON.stringify(result, null, 2) + '\n', {flag:'wx', mode:0o600});
  console.log('CONTINUUM_ARCHITECTURE_PROOF=PASS SHA=' + sha + ' UNSIGNED_QA_ONLY');
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(import.meta.filename))
  main();
