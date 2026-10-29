#!/usr/bin/env bash
# Owner-invoked local capture only. Read the handoff before a real device run.
set +x
set -eu
exec node - "$@" <<'GENERATION16_NODE'
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const net = require('node:net');
const { spawnSync } = require('node:child_process');
const PACKAGE = 'at.heinz.deviceai.mentorpaired';
const SOURCE = 'files/learning-core-v2/experiences-v2.bin';
const MAX_BYTES = 512 * 1024;
const EXPECTED_RECORDS = 256;
let output;
let metadata;
function fail(code) { const error = new Error(code); error.code = code; throw error; }
function sha(bytes) { return crypto.createHash('sha256').update(bytes).digest('hex'); }
function save(name, bytes) {
  const fd = fs.openSync(path.join(output, name), 'wx', 0o600);
  try { fs.writeFileSync(fd, bytes); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
}
function writeMetadata(value) {
  const temp = path.join(output, 'metadata.next.json');
  const fd = fs.openSync(temp, 'wx', 0o600);
  try { fs.writeFileSync(fd, JSON.stringify(value, null, 2) + '\n'); fs.fsyncSync(fd); }
  finally { fs.closeSync(fd); }
  fs.renameSync(temp, path.join(output, 'metadata.json'));
}
function privateDirectory(directory) {
  const stat = fs.lstatSync(directory);
  if (!stat.isDirectory() || stat.isSymbolicLink() || stat.uid !== process.getuid() ||
      (stat.mode & 0o077) !== 0) fail('OUTPUT_NOT_PRIVATE');
}
function parseArgs() {
  const args = process.argv.slice(2);
  const options = { serial: null, port: 5037 };
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--help') {
      process.stdout.write('Usage: bash scripts/continuum-generation16-rescue.sh [--serial SERIAL] [--adb-server-port PORT]\n' +
        'Uses an already authorized local ADB server; never connects or pairs devices.\n' +
        'Captures only mentorpaired experiences-v2.bin twice into private HOME output.\n');
      process.exit(0);
    }
    if (args[i] === '--serial' && options.serial === null && args[i + 1]) {
      options.serial = args[++i]; continue;
    }
    if (args[i] === '--adb-server-port' && args[i + 1] && /^\d{1,5}$/.test(args[i + 1])) {
      options.port = Number(args[++i]);
      if (options.port < 1 || options.port > 65535) fail('INVALID_ARGUMENT');
      continue;
    }
    fail('INVALID_ARGUMENT');
  }
  return options;
}
// No host/address option: the ADB server is always loopback, never a remote daemon.
function serverAvailable(port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    const finish = available => {
      socket.destroy();
      if (available) resolve(); else reject(Object.assign(new Error('ADB_SERVER_UNAVAILABLE'),
        { code: 'ADB_SERVER_UNAVAILABLE' }));
    };
    socket.setTimeout(1000);
    socket.once('connect', () => finish(true));
    socket.once('error', () => finish(false));
    socket.once('timeout', () => finish(false));
  });
}
function localSerial(serial) {
  // USB serials/emulators or an owner-connected loopback wireless transport only.
  return /^[A-Za-z0-9_-]{1,200}$/.test(serial) ||
    /^(127\.0\.0\.1|localhost):[0-9]{1,5}$/.test(serial) ||
    /^\[::1\]:[0-9]{1,5}$/.test(serial);
}
function adb(options, serial, args, maxBuffer = 64 * 1024) {
  const env = { ...process.env };
  // Ignore environment overrides for a remote/custom server or implicit device.
  for (const key of ['ADB_SERVER_SOCKET', 'ANDROID_ADB_SERVER_ADDRESS',
    'ANDROID_ADB_SERVER_PORT', 'ANDROID_SERIAL', 'ADB_TRACE']) delete env[key];
  return spawnSync('adb', ['-H', '127.0.0.1', '-P', String(options.port),
    ...(serial ? ['-s', serial] : []), ...args],
    { env, timeout: 15000, maxBuffer, encoding: null, input: Buffer.alloc(0), windowsHide: true });
}
function checked(result, code) {
  if (result.error || result.signal || result.status !== 0 || !Buffer.isBuffer(result.stdout)) fail(code);
  return result.stdout;
}
function readLedger(bytes) {
  if (bytes.length < 40 || bytes.length > MAX_BYTES) fail('LEDGER_SIZE');
  const payload = bytes.subarray(0, -32);
  const digest = crypto.createHash('sha256').update(payload).digest();
  if (!crypto.timingSafeEqual(digest, bytes.subarray(-32))) fail('LEDGER_CHECKSUM');
  let cursor = 0;
  function take(length) {
    if (!Number.isInteger(length) || length < 0 || cursor + length > payload.length) fail('LEDGER_FRAMING');
    const value = payload.subarray(cursor, cursor + length); cursor += length; return value;
  }
  function int() { return take(4).readInt32BE(); }
  function ascii() {
    const size = take(2).readUInt16BE();
    const value = take(size);
    if ([...value].some(byte => byte < 0x20 || byte > 0x7e)) fail('LEDGER_ASCII_FIELD');
    return value.toString('ascii');
  }
  // Kotlin's UTF8 String decoder preserves U+FEFF at the start of a field.
  // In TextDecoder, ignoreBOM:true keeps those bytes as field data for identity hashing.
  const decoder = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true });
  function text() {
    const size = int();
    if (size < 0 || size > 8192) fail('LEDGER_TEXT_SIZE');
    try { return decoder.decode(take(size)); } catch { fail('LEDGER_TEXT_ENCODING'); }
  }
  function nullable() {
    const flag = take(1)[0];
    if (flag !== 0 && flag !== 1) fail('LEDGER_NULL_FLAG');
    return flag === 1 ? text() : null;
  }
  const allowed = [
    new Set(['INTENT_LABEL', 'SOURCE_TEXT', 'SEMANTIC_PAIR', 'TRAINING_AUDIT', 'USER_FACT', 'COMMAND_MAPPING', 'USER_FEEDBACK']),
    new Set(['USER_CONFIRMED', 'MENTOR_WIKIPEDIA', 'SYNTHETIC_FIXTURE', 'LOCAL_TRAINING', 'REVIEWED_WEB_SOURCE']),
    new Set(['OWNER_CONFIRMED', 'SOURCE_OBSERVATION', 'SYNTHETIC', 'SYSTEM_MEASUREMENT']),
    new Set(['OBSERVED', 'CONFIRMED', 'LEARNED', 'REPLAY_PENDING', 'REJECTED', 'REVOKED'])
  ];
  if (int() !== 0x4c455832) fail('LEDGER_MAGIC');
  const count = int();
  if (count < 0 || count > 256) fail('LEDGER_RECORD_COUNT');
  const ids = new Set();
  const counts = {
    byType: Object.fromEntries([...allowed[0]].map(value => [value, 0])),
    byStatus: Object.fromEntries([...allowed[3]].map(value => [value, 0])),
    ownerUserFacts: { total: 0, confirmed: 0, revoked: 0 }
  };
  for (let index = 0; index < count; index++) {
    const id = ascii();
    const tags = allowed.map(values => {
      const value = ascii(); if (!values.has(value)) fail('LEDGER_ENUM'); return value;
    });
    const content = text(), annotation = nullable(), sourceRef = nullable(), contentHash = ascii();
    const createdAt = take(8).readBigInt64BE(), updatedAt = take(8).readBigInt64BE();
    if (!/^lx-[a-f0-9]{24}$/.test(id) || ids.has(id) ||
        content.length < 1 || content.length > 2048 ||
        (annotation !== null && (annotation.length < 1 || annotation.length > 160)) ||
        (sourceRef !== null && (sourceRef.length < 1 || sourceRef.length > 500)) ||
        !/^[a-f0-9]{64}$/.test(contentHash) || createdAt < 0n || updatedAt < createdAt) fail('LEDGER_RECORD');
    const hash = crypto.createHash('sha256');
    for (const value of [...tags.slice(0, 3), content, annotation ?? '', sourceRef ?? '']) {
      hash.update(value, 'utf8'); hash.update(Buffer.from([0]));
    }
    if (hash.digest('hex') !== contentHash || id !== 'lx-' + contentHash.slice(0, 24)) fail('LEDGER_IDENTITY');
    ids.add(id);
    counts.byType[tags[0]]++;
    counts.byStatus[tags[3]]++;
    if (tags[0] === 'USER_FACT' && tags[1] === 'USER_CONFIRMED' && tags[2] === 'OWNER_CONFIRMED' &&
        annotation === null && sourceRef === null &&
        ['CONFIRMED', 'LEARNED', 'REPLAY_PENDING', 'REVOKED'].includes(tags[3])) {
      counts.ownerUserFacts.total++;
      if (tags[3] === 'CONFIRMED') counts.ownerUserFacts.confirmed++;
      if (tags[3] === 'REVOKED') counts.ownerUserFacts.revoked++;
    }
  }
  if (cursor !== payload.length) fail('LEDGER_TRAILING_BYTES');
  return { recordCount: count, counts };
}
async function main() {
  const options = parseArgs();
  if (typeof process.getuid !== 'function' || process.getuid() === 0) fail('NON_ROOT_REQUIRED');
  if (options.serial !== null && !localSerial(options.serial)) fail('NON_LOCAL_DEVICE');
  const home = fs.realpathSync(os.homedir());
  if (/^\/(storage|sdcard|mnt\/media_rw)(\/|$)/.test(home)) fail('OUTPUT_NOT_PRIVATE');
  privateDirectory(home);
  const parent = path.join(home, '.continuum-generation16-rescue');
  try { fs.mkdirSync(parent, { mode: 0o700 }); } catch (error) { if (error.code !== 'EEXIST') throw error; }
  privateDirectory(parent);
  output = fs.mkdtempSync(path.join(parent, 'capture-'));
  fs.chmodSync(output, 0o700);
  metadata = { schemaVersion: 1, status: 'UNVERIFIED', package: PACKAGE, sourcePath: SOURCE,
    expectedRecordCount: EXPECTED_RECORDS, createdAt: new Date().toISOString(), sourceWrites: false,
    captureFiles: [] };
  writeMetadata(metadata);
  await serverAvailable(options.port);
  const devices = checked(adb(options, null, ['devices', '-l']), 'ADB_LIST_FAILED').toString('utf8');
  const lines = devices.trim().split(/\r?\n/);
  if (lines.shift() !== 'List of devices attached') fail('ADB_LIST_INVALID');
  const entries = lines.filter(line => line.trim()).map(line => line.trim().split(/\s+/));
  let selected;
  if (options.serial !== null) {
    const matches = entries.filter(fields => fields[0] === options.serial);
    if (matches.length !== 1) fail('DEVICE_NOT_UNIQUE');
    selected = matches[0];
  } else {
    if (entries.length !== 1) fail('DEVICE_NOT_UNIQUE');
    selected = entries[0];
  }
  if (selected[1] !== 'device') fail('DEVICE_NOT_AUTHORIZED');
  const serial = selected[0];
  if (!localSerial(serial)) fail('NON_LOCAL_DEVICE');
  metadata.deviceSerialSha256 = sha(Buffer.from(serial));
  const uid = checked(adb(options, serial, ['shell', 'run-as', PACKAGE, 'id', '-u']),
    'RUN_AS_UNAVAILABLE').toString('utf8').trim();
  if (!/^[0-9]{5,10}$/.test(uid) || Number(uid) < 10000) fail('RUN_AS_IDENTITY');
  const cwd = checked(adb(options, serial, ['shell', 'run-as', PACKAGE, 'pwd']),
    'RUN_AS_PROBE_FAILED').toString('utf8').trim();
  if (!/^\/data\/(?:user\/[0-9]+|data)\/at\.heinz\.deviceai\.mentorpaired$/.test(cwd)) fail('RUN_AS_DIRECTORY');
  const kinds = checked(adb(options, serial, ['shell', 'run-as', PACKAGE, 'stat', '-c', '%F',
    'files', 'files/learning-core-v2', SOURCE]), 'SOURCE_PROBE_FAILED').toString('utf8').trim();
  if (kinds.replace(/\r/g, '') !== 'directory\ndirectory\nregular file') fail('SOURCE_NOT_REGULAR');
  const captures = [];
  for (let index = 1; index <= 2; index++) {
    const result = adb(options, serial, ['exec-out', 'run-as', PACKAGE, 'cat', SOURCE], MAX_BYTES + 1);
    const name = 'capture-' + index + '.unverified.bin';
    // Keep even a failed/partial local capture; nothing is removed or written on the source device.
    if (Buffer.isBuffer(result.stdout)) {
      // spawnSync checks maxBuffer after appending a chunk: bound the retained prefix explicitly.
      save(name, result.stdout.subarray(0, MAX_BYTES + 1));
      metadata.captureFiles.push(name);
      writeMetadata(metadata);
    }
    captures.push(checked(result, 'CAPTURE_' + index + '_FAILED'));
  }
  if (!captures[0].equals(captures[1])) fail('CAPTURES_DIFFER');
  const audit = readLedger(captures[0]);
  const recordCount = audit.recordCount;
  metadata.recordCount = recordCount;
  metadata.auditCounts = audit.counts;
  metadata.bytes = captures[0].length;
  metadata.sha256 = sha(captures[0]);
  if (recordCount !== EXPECTED_RECORDS) fail('EXPECTED_256_RECORDS');
  metadata = { ...metadata, status: 'CAPTURED_VALIDATED', capturesByteEqual: true,
    ledgerChecksumValid: true, completedAt: new Date().toISOString() };
  writeMetadata(metadata);
  process.stdout.write('CAPTURED_VALIDATED records=' + recordCount + ' bytes=' + metadata.bytes +
    ' sha256=' + metadata.sha256 + '\nowner_user_facts_confirmed=' + audit.counts.ownerUserFacts.confirmed +
    ' owner_user_facts_revoked=' + audit.counts.ownerUserFacts.revoked +
    ' revoked_records=' + audit.counts.byStatus.REVOKED + '\nPrivate output: ' + output + '\n');
}
main().catch(error => {
  const code = /^[A-Z][A-Z0-9_]+$/.test(error.code || '') ? error.code : 'LOCAL_IO_FAILED';
  if (output && metadata) {
    try { writeMetadata({ ...metadata, status: 'FAILED', failureCode: code }); } catch {}
  }
  process.stderr.write('FAILED ' + code + (output ? '\nPrivate partial output retained: ' + output : '') +
    '\nSource package was not modified. No rescue success is claimed.\n');
  process.exitCode = 1;
});
GENERATION16_NODE
