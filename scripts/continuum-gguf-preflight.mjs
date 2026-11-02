/**
 * EXP-05/12: explicit offline owner model-pin preflight before Termux llama-server.
 * This is an admission check, not proof of publisher authenticity or that
 * llama-server eventually loaded the same inode. No network, launch, or writes.
 */
import {constants} from 'node:fs';
import {open, lstat, realpath} from 'node:fs/promises';
import {basename, isAbsolute, normalize, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const REQUIRED_PIN_KEYS = Object.freeze(['repository', 'revision', 'filename', 'license', 'sizeBytes', 'sha256']);
const PIN_LIMIT_BYTES = 8192;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const REVISION_PATTERN = /^[a-f0-9]{40}$/;
const REPO_PATTERN = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const FILE_PATTERN = /^[A-Za-z0-9_.-]{1,180}\.gguf$/;
const LICENSE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$/;

function fail(code) { throw new Error(code); }
function sameStat(a, b) {
  return a.dev === b.dev && a.ino === b.ino && a.size === b.size &&
    a.mtimeNs === b.mtimeNs && a.ctimeNs === b.ctimeNs;
}
function safePinnedManifestFile(s) {
  return s.isFile() && s.nlink === 1n && s.size > 0n &&
    s.size <= BigInt(PIN_LIMIT_BYTES) && (s.mode & 0o022n) === 0n &&
    (typeof process.getuid !== 'function' || s.uid === BigInt(process.getuid()));
}
function validatePinShape(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).sort().join('|') !== [...REQUIRED_PIN_KEYS].sort().join('|') ||
      !REPO_PATTERN.test(value.repository) || !REVISION_PATTERN.test(value.revision) ||
      !FILE_PATTERN.test(value.filename) || !LICENSE_PATTERN.test(value.license) ||
      !SHA256_PATTERN.test(value.sha256) ||
      !Number.isSafeInteger(value.sizeBytes) || value.sizeBytes < 24 || value.sizeBytes > 2_147_483_648) {
    fail('GGUF_PIN_SCHEMA');
  }
  return Object.freeze({...value});
}

/** Only a single, non-symlink, owner-owned regular file may supply a pin. */
export async function readPinnedGgufManifest(path) {
  if (typeof path !== 'string' || !isAbsolute(path) || normalize(path) !== path)
    fail('GGUF_PIN_PATH');
  let before;
  try {
    before = await lstat(path, {bigint: true});
    if (!safePinnedManifestFile(before) || (await realpath(path)) !== path)
      fail('GGUF_PIN_UNSAFE_FILE');
  } catch (error) {
    if (error?.message?.startsWith('GGUF_')) throw error;
    fail('GGUF_PIN_UNAVAILABLE');
  }
  let file;
  try {
    file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
    const during = await file.stat({bigint: true});
    if (!safePinnedManifestFile(during) || !sameStat(before, during))
      fail('GGUF_PIN_CHANGED');
    const bytes = await file.readFile();
    const after = await file.stat({bigint: true});
    if (!sameStat(during, after) || bytes.length !== Number(during.size) ||
        !sameStat(after, await lstat(path, {bigint: true})))
      fail('GGUF_PIN_CHANGED');
    let value;
    try { value = JSON.parse(new TextDecoder('utf-8', {fatal: true}).decode(bytes)); }
    catch { fail('GGUF_PIN_JSON'); }
    return validatePinShape(value);
  } catch (error) {
    if (error?.message?.startsWith('GGUF_')) throw error;
    fail('GGUF_PIN_UNAVAILABLE');
  } finally {
    if (file) await file.close();
  }
}

/** Explicit dependency injection for unit tests; production always uses admitGgufFile. */
export async function preflightPinnedGguf({modelPath, manifestPath, admit}) {
  if (typeof admit !== 'function') fail('GGUF_PIN_VERIFIER_UNAVAILABLE');
  const pin = await readPinnedGgufManifest(manifestPath);
  if (typeof modelPath !== 'string' || !isAbsolute(modelPath) ||
      normalize(modelPath) !== modelPath || basename(modelPath) !== pin.filename)
    fail('GGUF_PIN_FILENAME_MISMATCH');
  const admission = await admit({path: modelPath, provenance: pin});
  if (!admission || admission.status !== 'GGUF_IDENTITY_MATCHED' ||
      admission.sha256 !== pin.sha256 || admission.sizeBytes !== pin.sizeBytes ||
      admission.trusted !== false || admission.executionAllowed !== false ||
      !admission.inspection || typeof admission.inspection.architecture !== 'string')
    fail('GGUF_PIN_ADMISSION_UNCONFIRMED');
  return Object.freeze({schemaVersion: 1, status: 'GGUF_PIN_VERIFIED',
    modelSha256: pin.sha256, modelBytes: pin.sizeBytes,
    architecture: admission.inspection.architecture, offline: true,
    modelPublisherAuthenticated: false, serverModelIdentityVerified: false,
    androidDeviceVerified: false});
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.length !== 3 || process.argv[2] !== '--verify') {
    process.stderr.write('GGUF_PIN_USAGE\n'); process.exitCode = 2;
  } else {
    try {
      // Keep official existing admission implementation as the ONLY GGUF parser.
      const {admitGgufFile} = await import('./continuum-gguf.mjs');
      const receipt = await preflightPinnedGguf({
        modelPath: process.env.CONTINUUM_GGUF_PATH,
        manifestPath: process.env.CONTINUUM_GGUF_MANIFEST,
        admit: admitGgufFile
      });
      process.stdout.write(JSON.stringify(receipt) + '\n');
    } catch (error) {
      const code = String(error?.message || '');
      process.stderr.write(/^GGUF_[A-Z0-9_]+$/.test(code) ? code + '\n' : 'GGUF_PIN_FAILED\n');
      process.exitCode = 1;
    }
  }
}
