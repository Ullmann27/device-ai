import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const helperSource = fs.readFileSync(fileURLToPath(new URL('./continuum-generation16-rescue.sh', import.meta.url)), 'utf8');
const PACKAGE = 'at.heinz.deviceai.mentorpaired';
const SOURCE = 'files/learning-core-v2/experiences-v2.bin';
const SECRET = 'SYNTHETIC_PRIVATE_CONTENT_DO_NOT_LOG';
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const int = value => { const b = Buffer.alloc(4); b.writeInt32BE(value); return b; };
const long = value => { const b = Buffer.alloc(8); b.writeBigInt64BE(BigInt(value)); return b; };
const ascii = value => { const b = Buffer.from(value); const n = Buffer.alloc(2); n.writeUInt16BE(b.length); return Buffer.concat([n, b]); };
const text = value => { const b = Buffer.from(value); return Buffer.concat([int(b.length), b]); };
function ledger(count = 256, mutateRecord = false, mixed = false, leadingBom = false) {
  const chunks = [int(0x4c455832), int(count)];
  for (let index = 0; index < count; index++) {
    const tags = mixed && index === 1 ? ['USER_FACT', 'MENTOR_WIKIPEDIA', 'SOURCE_OBSERVATION', 'CONFIRMED'] :
      mixed && index === 2 ? ['TRAINING_AUDIT', 'LOCAL_TRAINING', 'SYSTEM_MEASUREMENT', 'LEARNED'] :
      mixed && index === 3 ? ['INTENT_LABEL', 'USER_CONFIRMED', 'OWNER_CONFIRMED', 'CONFIRMED'] :
      ['USER_FACT', 'USER_CONFIRMED', 'OWNER_CONFIRMED', index === 0 ? 'REVOKED' : 'CONFIRMED'];
    const content = (leadingBom && index === 0 ? '\uFEFF' : '') + SECRET + ' ' + index + ' Ü 😃';
    const annotation = mixed && index === 4 ? 'noncanonical annotation' : null;
    const digest = crypto.createHash('sha256');
    for (const value of [...tags.slice(0, 3), content, annotation ?? '', '']) digest.update(value).update(Buffer.from([0]));
    const hash = digest.digest('hex');
    chunks.push(ascii('lx-' + hash.slice(0, 24)), ...tags.map(ascii),
      text(mutateRecord && index === 0 ? content + ' changed' : content),
      ...(annotation === null ? [Buffer.from([0])] : [Buffer.from([1]), text(annotation)]),
      Buffer.from([0]), ascii(hash), long(100), long(101));
  }
  const payload = Buffer.concat(chunks);
  return Buffer.concat([payload, crypto.createHash('sha256').update(payload).digest()]);
}
const fakeAdb = `#!${process.execPath}
'use strict';
const fs = require('node:fs');
const cfg = JSON.parse(fs.readFileSync(process.env.RESCUE_TEST_CONFIG, 'utf8'));
const args = process.argv.slice(2);
fs.appendFileSync(cfg.log, JSON.stringify(args) + '\\n');
if (args[0] !== '-H' || args[1] !== '127.0.0.1' || args[2] !== '-P' ||
    args[3] !== String(cfg.port)) process.exit(90);
let rest = args.slice(4);
let serial = null;
if (rest[0] === '-s') { serial = rest[1]; rest = rest.slice(2); }
if (JSON.stringify(rest) === JSON.stringify(['devices', '-l']) && serial === null) {
  process.stdout.write('List of devices attached\\n' + cfg.devices); process.exit(0);
}
if (serial !== cfg.expectedSerial) process.exit(91);
const equal = expected => JSON.stringify(rest) === JSON.stringify(expected);
if (equal(['shell', 'run-as', cfg.package, 'id', '-u'])) {
  if (cfg.mode === 'run-as-denied') {
    process.stderr.write(cfg.secret); process.exit(1);
  }
  process.stdout.write(cfg.mode === 'root-uid' ? '0\\n' : '10123\\n'); process.exit(0);
}
if (equal(['shell', 'run-as', cfg.package, 'pwd'])) {
  process.stdout.write(cfg.mode === 'wrong-directory' ? '/sdcard\\n' :
    '/data/user/0/' + cfg.package + '\\n'); process.exit(0);
}
if (equal(['shell', 'run-as', cfg.package, 'stat', '-c', '%F',
  'files', 'files/learning-core-v2', cfg.source])) {
  process.stdout.write('directory\\ndirectory\\n' +
    (cfg.mode === 'symlink-source' ? 'symbolic link' : 'regular file') + '\\n'); process.exit(0);
}
if (equal(['exec-out', 'run-as', cfg.package, 'cat', cfg.source])) {
  let n = 0;
  try { n = Number(fs.readFileSync(cfg.counter, 'utf8')); } catch {}
  fs.writeFileSync(cfg.counter, String(n + 1));
  let bytes = fs.readFileSync(cfg.primary);
  if (cfg.mode === 'capture-failed') { process.stdout.write(bytes.subarray(0, 23)); process.stderr.write(cfg.secret); process.exit(1); }
  if (cfg.mode === 'captures-differ' && n === 1) { bytes = Buffer.from(bytes); bytes[20] ^= 1; }
  if (cfg.mode === 'truncated') bytes = bytes.subarray(0, 20);
  if (cfg.mode === 'oversized') bytes = Buffer.alloc(600000, 65);
  fs.writeFileSync(1, bytes); process.exit(0);
}
// This fake is deliberately a command allowlist: an added write/install/pair/restore is rejected.
process.exit(92);
`;
async function runCase(options = {}) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-rescue-test-'));
  const home = path.join(root, 'home'), bin = path.join(root, 'bin');
  fs.mkdirSync(home, { mode: 0o700 }); fs.mkdirSync(bin, { mode: 0o700 });
  const primary = path.join(root, 'synthetic-primary.bin'), backup = path.join(root, 'synthetic.lkg');
  const bytes = options.bytes ?? ledger();
  fs.writeFileSync(primary, bytes, { mode: 0o600 });
  fs.writeFileSync(backup, 'SYNTHETIC_OLDER_REVOKED_GENERATION', { mode: 0o600 });
  const primaryBefore = sha(fs.readFileSync(primary)), backupBefore = sha(fs.readFileSync(backup));
  fs.writeFileSync(path.join(root, 'helper.sh'), helperSource, { mode: 0o600 });
  fs.writeFileSync(path.join(bin, 'adb'), fakeAdb, { mode: 0o700 });
  const server = net.createServer(socket => socket.destroy());
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  const log = path.join(root, 'adb.jsonl');
  const config = { package: PACKAGE, source: SOURCE, primary, log, port, secret: SECRET,
    counter: path.join(root, 'counter'), mode: options.mode,
    devices: options.devices ?? 'FAKE_USB device product:synthetic model:synthetic transport_id:1\n',
    expectedSerial: options.expectedSerial ?? 'FAKE_USB' };
  fs.writeFileSync(path.join(root, 'config.json'), JSON.stringify(config), { mode: 0o600 });
  if (options.symlinkOutput) fs.symlinkSync(root, path.join(home, '.continuum-generation16-rescue'));
  if (options.insecureHome) fs.chmodSync(home, 0o755);
  // A root-running CI must execute the helper as an unprivileged uid; no helper bypass flag exists.
  const dropRoot = typeof process.getuid === 'function' && process.getuid() === 0;
  if (dropRoot) {
    function chownTree(location) {
      fs.lchownSync(location, 65534, 65534);
      if (fs.lstatSync(location).isDirectory()) for (const entry of fs.readdirSync(location)) chownTree(path.join(location, entry));
    }
    chownTree(root);
  }
  let result;
  try {
    result = await new Promise((resolve, reject) => {
      const child = spawn('bash', [path.join(root, 'helper.sh'), '--adb-server-port', String(port), ...(options.args ?? [])],
        { cwd: root, env: { ...process.env, HOME: home, PATH: bin + path.delimiter + process.env.PATH,
          RESCUE_TEST_CONFIG: path.join(root, 'config.json'), ADB_SERVER_SOCKET: 'tcp:remote.invalid:5555',
          ANDROID_SERIAL: 'UNSELECTED_REMOTE' },
          ...(dropRoot ? { uid: 65534, gid: 65534 } : {}), stdio: ['ignore', 'pipe', 'pipe'] });
      let stdout = '', stderr = '';
      child.stdout.on('data', chunk => { stdout += chunk; });
      child.stderr.on('data', chunk => { stderr += chunk; });
      child.once('error', reject);
      child.once('close', (status, signal) => resolve({ status, signal, stdout, stderr }));
    });
    assert.equal(sha(fs.readFileSync(primary)), primaryBefore, 'synthetic primary must be unchanged');
    assert.equal(sha(fs.readFileSync(backup)), backupBefore, 'older backup must be unchanged');
    assert.ok(!result.stdout.includes(SECRET) && !result.stderr.includes(SECRET), 'private contents and adb stderr must not leak');
    const parent = path.join(home, '.continuum-generation16-rescue');
    const outputs = fs.existsSync(parent) && !fs.lstatSync(parent).isSymbolicLink() ?
      fs.readdirSync(parent).filter(name => name.startsWith('capture-')).map(name => path.join(parent, name)) : [];
    const output = outputs[0];
    const metadata = output ? JSON.parse(fs.readFileSync(path.join(output, 'metadata.json'), 'utf8')) : null;
    const calls = fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse) : [];
    assert.ok(calls.every(args => !args.some(arg => ['install', 'install-multiple', 'root', 'remount',
      'push', 'connect', 'pair', 'start-server', 'kill-server', 'restore', 'grant', 'chmod', 'rm', 'mv'].includes(arg))));
    if (output) {
      assert.equal(fs.statSync(output).mode & 0o777, 0o700);
      for (const name of fs.readdirSync(output)) assert.equal(fs.statSync(path.join(output, name)).mode & 0o777, 0o600);
    }
    return { ...result, metadata, calls, captures: output ?
      fs.readdirSync(output).filter(name => name.endsWith('.bin')).map(name => fs.readFileSync(path.join(output, name))) : [] };
  } finally {
    await new Promise(resolve => server.close(resolve));
    fs.rmSync(root, { recursive: true, force: true });
  }
}
function failed(result, code) {
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stderr, new RegExp('FAILED ' + code));
  assert.ok(!result.stdout.includes('CAPTURED_VALIDATED'));
  if (result.metadata) assert.equal(result.metadata.status, 'FAILED');
}

test('two process captures validate all 256 records without exposing contents or changing originals', async () => {
  const result = await runCase();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.metadata.status, 'CAPTURED_VALIDATED');
  assert.equal(result.metadata.recordCount, 256);
  assert.deepEqual(result.metadata.auditCounts.ownerUserFacts, { total: 256, confirmed: 255, revoked: 1 });
  assert.equal(result.metadata.auditCounts.byType.USER_FACT, 256);
  assert.equal(result.metadata.auditCounts.byType.TRAINING_AUDIT, 0);
  assert.equal(result.metadata.auditCounts.byStatus.CONFIRMED, 255);
  assert.equal(result.metadata.auditCounts.byStatus.REVOKED, 1);
  assert.match(result.stdout, /owner_user_facts_confirmed=255 owner_user_facts_revoked=1 revoked_records=1/);
  assert.equal(result.metadata.capturesByteEqual, true);
  assert.equal(result.metadata.ledgerChecksumValid, true);
  assert.equal(result.metadata.sha256, sha(ledger()));
  assert.equal(result.captures.length, 2);
  assert.deepEqual(result.captures[0], result.captures[1]);
  assert.equal(result.calls.filter(args => args.includes('exec-out')).length, 2);
});
test('safe counts distinguish canonical owner facts from source observations and other record types', async () => {
  const result = await runCase({ bytes: ledger(256, false, true) });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.metadata.auditCounts.ownerUserFacts, { total: 252, confirmed: 251, revoked: 1 });
  assert.equal(result.metadata.auditCounts.byType.USER_FACT, 254);
  assert.equal(result.metadata.auditCounts.byType.TRAINING_AUDIT, 1);
  assert.equal(result.metadata.auditCounts.byType.INTENT_LABEL, 1);
  assert.equal(result.metadata.auditCounts.byStatus.CONFIRMED, 254);
  assert.equal(result.metadata.auditCounts.byStatus.LEARNED, 1);
  assert.equal(result.metadata.auditCounts.byStatus.REVOKED, 1);
  assert.equal(Object.values(result.metadata.auditCounts.byType).reduce((a, b) => a + b, 0), 256);
  assert.equal(Object.values(result.metadata.auditCounts.byStatus).reduce((a, b) => a + b, 0), 256);
});
test('a valid Kotlin ledger retains a leading UTF8 BOM in its record identity', async () => {
  const bytes = ledger(256, false, false, true);
  const result = await runCase({ bytes });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.metadata.status, 'CAPTURED_VALIDATED');
  assert.equal(result.metadata.recordCount, 256);
  assert.equal(result.metadata.sha256, sha(bytes));
  assert.deepEqual(result.captures[0], bytes);
  assert.deepEqual(result.captures[1], bytes);
});
test('preserving BOM data retains strict malformed UTF8 refusal', async () => {
  const original = ledger();
  const payload = Buffer.from(original.subarray(0, -32));
  const contentStart = payload.indexOf(Buffer.from(SECRET));
  assert.ok(contentStart >= 0);
  payload[contentStart] = 0xff;
  const bytes = Buffer.concat([payload, crypto.createHash('sha256').update(payload).digest()]);
  const result = await runCase({ bytes });
  failed(result, 'LEDGER_TEXT_ENCODING');
  assert.equal(result.captures.length, 2);
});
test('multiple devices require explicit selection', async () => {
  failed(await runCase({ devices: 'FAKE_USB device\nSECOND_USB device\n' }), 'DEVICE_NOT_UNIQUE');
});
test('explicit selection stays bound to one listed local device', async () => {
  const result = await runCase({ devices: 'OTHER_USB device\nSECOND_USB device\n',
    expectedSerial: 'SECOND_USB', args: ['--serial', 'SECOND_USB'] });
  assert.equal(result.status, 0, result.stderr);
  assert.ok(result.calls.slice(1).every(args => args[5] === 'SECOND_USB'));
});
test('no device is failure', async () => { failed(await runCase({ devices: '' }), 'DEVICE_NOT_UNIQUE'); });
test('unauthorized device is rejected before run-as', async () => {
  const result = await runCase({ devices: 'FAKE_USB unauthorized\n' });
  failed(result, 'DEVICE_NOT_AUTHORIZED'); assert.equal(result.calls.length, 1);
});
test('offline device is rejected before run-as', async () => {
  failed(await runCase({ devices: 'FAKE_USB offline\n' }), 'DEVICE_NOT_AUTHORIZED');
});
test('nonlocal wireless device is rejected', async () => {
  failed(await runCase({ devices: '192.0.2.1:5555 device\n' }), 'NON_LOCAL_DEVICE');
});
test('owner-selected remote or injected serial never reaches adb', async () => {
  for (const serial of ['192.0.2.1:5555', 'USB;id', '-s', 'USB\nOTHER']) {
    const result = await runCase({ args: ['--serial', serial] });
    failed(result, 'NON_LOCAL_DEVICE'); assert.equal(result.calls.length, 0);
  }
});
test('owner-connected loopback transport is supported without connect/pair', async () => {
  const result = await runCase({ devices: '127.0.0.1:45678 device\n', expectedSerial: '127.0.0.1:45678' });
  assert.equal(result.status, 0, result.stderr);
});
test('nondebuggable package fails without trying root or permissions', async () => {
  failed(await runCase({ mode: 'run-as-denied' }), 'RUN_AS_UNAVAILABLE');
});
test('run-as must return an application uid', async () => { failed(await runCase({ mode: 'root-uid' }), 'RUN_AS_IDENTITY'); });
test('run-as must start inside the historical private package', async () => {
  failed(await runCase({ mode: 'wrong-directory' }), 'RUN_AS_DIRECTORY');
});
test('symlink source is rejected before captures', async () => {
  const result = await runCase({ mode: 'symlink-source' }); failed(result, 'SOURCE_NOT_REGULAR');
  assert.equal(result.captures.length, 0);
});
test('failed partial capture is retained and never reported as rescued', async () => {
  const result = await runCase({ mode: 'capture-failed' }); failed(result, 'CAPTURE_1_FAILED');
  assert.equal(result.captures.length, 1); assert.equal(result.captures[0].length, 23);
});
test('changing source across captures fails and retains both copies', async () => {
  const result = await runCase({ mode: 'captures-differ' }); failed(result, 'CAPTURES_DIFFER');
  assert.equal(result.captures.length, 2);
});
test('truncated identical captures fail schema validation', async () => {
  failed(await runCase({ mode: 'truncated' }), 'LEDGER_SIZE');
});
test('oversized subprocess output fails with bounded capture', async () => {
  const result = await runCase({ mode: 'oversized' });
  failed(result, 'CAPTURE_1_FAILED');
  assert.equal(result.captures[0].length, 512 * 1024 + 1);
  assert.ok(result.captures[0].every(byte => byte === 65));
  assert.equal(result.captures.length, 1);
  assert.equal(result.metadata.status, 'FAILED');
});
test('identical corrupt checksums never give success', async () => {
  const bytes = ledger(); bytes[bytes.length - 1] ^= 1;
  failed(await runCase({ bytes }), 'LEDGER_CHECKSUM');
});
test('valid archive checksum cannot hide a forged record identity', async () => {
  failed(await runCase({ bytes: ledger(256, true) }), 'LEDGER_IDENTITY');
});
test('count metadata cannot silently accept a different generation', async () => {
  const result = await runCase({ bytes: ledger(255) });
  failed(result, 'EXPECTED_256_RECORDS'); assert.equal(result.metadata.recordCount, 255);
  assert.equal(result.captures.length, 2);
});
test('output-parent symlink is rejected', async () => {
  const result = await runCase({ symlinkOutput: true }); failed(result, 'OUTPUT_NOT_PRIVATE');
  assert.equal(result.calls.length, 0);
});
test('shared home permissions are rejected before reading device data', async () => {
  const result = await runCase({ insecureHome: true }); failed(result, 'OUTPUT_NOT_PRIVATE');
  assert.equal(result.calls.length, 0);
});
test('unknown arguments are rejected before touching adb', async () => {
  const result = await runCase({ args: ['--restore'] }); failed(result, 'INVALID_ARGUMENT');
  assert.equal(result.calls.length, 0);
});
