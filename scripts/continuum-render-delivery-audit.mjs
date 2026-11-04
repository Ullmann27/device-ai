#!/usr/bin/env node
/**
 * Read-only verification of a published, content-addressed Continuum APK.
 * Does not build, install, sign, merge, download to disk or change owner data.
 * The expected hash and source commit MUST come from an independent release record.
 */
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const ORIGINS = new Set([
  'https://device-ai-mentor-apk-preview.onrender.com',
  'https://device-ai-continuum-pr50-preview.onrender.com',
]);
const MAX_METADATA_BYTES = 32 * 1024;
const MAX_APK_BYTES = 200 * 1024 * 1024;
const MAX_DURATION_MS = 180_000;

export function validateOptions(options) {
  if (!options || typeof options !== 'object') throw new Error('Options required');
  const { origin, sha256, version, packageName, commit, artifact, branch } = options;
  if (!ORIGINS.has(origin)) throw new Error('Unapproved HTTPS release origin');
  if (typeof sha256 !== 'string' || !/^[0-9a-f]{64}$/.test(sha256)) {
    throw new Error('Expected SHA-256 must be 64 lowercase hex characters');
  }
  if (typeof commit !== 'string' || !/^[0-9a-f]{40}$/.test(commit)) {
    throw new Error('Expected Git commit must be 40 lowercase hex characters');
  }
  if (typeof version !== 'string' || !/^\d+(?:\.\d+){1,3}$/.test(version)) {
    throw new Error('Invalid version');
  }
  if (typeof packageName !== 'string' || !/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(packageName)) {
    throw new Error('Invalid package name');
  }
  if (typeof artifact !== 'string' ||
      !/^[A-Za-z0-9][A-Za-z0-9._-]{0,180}\.apk$/.test(artifact) ||
      !artifact.endsWith(`-v${version}-${sha256}.apk`)) {
    throw new Error('Artifact must contain the independently expected version and SHA-256');
  }
  if (branch !== undefined &&
      (typeof branch !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9._/-]{0,120}$/.test(branch) || branch.includes('..'))) {
    throw new Error('Invalid expected Git branch');
  }
  return { origin, sha256, version, packageName, commit, artifact, branch };
}

function parseMetadata(content) {
  const values = new Map();
  for (const line of content.split(/\r?\n/)) {
    if (!line) continue;
    const separator = line.indexOf('=');
    if (separator <= 0) throw new Error('Invalid BUILD-INFO record');
    const key = line.slice(0, separator);
    const value = line.slice(separator + 1);
    if (!/^[a-z][a-z_0-9]*$/.test(key) || value.length > 1024 || values.has(key)) {
      throw new Error('Invalid or duplicate BUILD-INFO key');
    }
    values.set(key, value);
  }
  return values;
}

async function readAsset(url, maximum, fetchImpl, collectText) {
  // No redirects: otherwise a compromised release page could send bytes elsewhere.
  const response = await fetchImpl(url, { redirect: 'manual', signal: AbortSignal.timeout(MAX_DURATION_MS) });
  if (response.status !== 200 || !response.body) {
    try { await response.body?.cancel(); } catch { /* The status remains authoritative. */ }
    throw new Error(`Release response rejected: HTTP ${response.status}`);
  }
  const claimedLength = response.headers.get('content-length');
  if (claimedLength !== null && (!/^\d+$/.test(claimedLength) || Number(claimedLength) > maximum)) {
    try { await response.body.cancel(); } catch { /* Already rejected. */ }
    throw new Error('Release content-length is invalid or over budget');
  }
  const hash = createHash('sha256');
  let bytes = 0;
  const chunks = [];
  for await (const chunk of response.body) {
    bytes += chunk.byteLength;
    if (bytes > maximum) {
      try { await response.body.cancel(); } catch { /* Streaming iterator may already own the lock. */ }
      throw new Error('Release body exceeds byte budget');
    }
    hash.update(chunk);
    if (collectText) chunks.push(Buffer.from(chunk));
  }
  if (bytes === 0 || (claimedLength !== null && bytes !== Number(claimedLength))) {
    throw new Error('Empty or truncated release response');
  }
  return {
    bytes,
    sha256: hash.digest('hex'),
    text: collectText ? new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks)) : undefined,
  };
}

export async function auditPublishedApk(rawOptions, { fetchImpl = fetch } = {}) {
  const o = validateOptions(rawOptions);
  const base = `${o.origin}/`;
  const sums = await readAsset(new URL(`SHA256SUMS-${o.sha256}.txt`, base), MAX_METADATA_BYTES, fetchImpl, true);
  // An exact singleton record prevents a stale or differently named artifact from being silently accepted.
  if (sums.text.trimEnd() !== `${o.sha256}  ${o.artifact}`) {
    throw new Error('Published SHA256SUMS does not match the independently expected artifact');
  }
  const info = await readAsset(new URL(`BUILD-INFO-${o.sha256}.txt`, base), MAX_METADATA_BYTES, fetchImpl, true);
  const fields = parseMetadata(info.text);
  for (const [key, value] of [
    ['apk_sha256', o.sha256], ['artifact', o.artifact], ['package', o.packageName], ['commit', o.commit],
  ]) {
    if (fields.get(key) !== value) throw new Error(`Published BUILD-INFO mismatch: ${key}`);
  }
  if (o.branch !== undefined && fields.get('branch') !== o.branch) {
    throw new Error('Published BUILD-INFO mismatch: branch');
  }
  const apk = await readAsset(new URL(o.artifact, base), MAX_APK_BYTES, fetchImpl, false);
  if (apk.sha256 !== o.sha256) throw new Error('Published APK bytes do not match expected SHA-256');
  return Object.freeze({
    status: 'DELIVERY_SHA256_VERIFIED',
    origin: o.origin,
    artifact: o.artifact,
    apkSha256: apk.sha256,
    apkBytes: apk.bytes,
    packageName: o.packageName,
    version: o.version,
    commit: o.commit,
    signatureVerified: false,
    installedOnDevice: false,
    playStoreReady: false,
  });
}

function parseCli(argv) {
  const expected = new Set(['origin', 'sha256', 'version', 'packageName', 'commit', 'artifact', 'branch']);
  const opts = {};
  for (let i = 0; i < argv.length; i += 2) {
    const flag = argv[i];
    if (!flag?.startsWith('--') || !expected.has(flag.slice(2)) || !argv[i + 1] ||
        Object.hasOwn(opts, flag.slice(2))) {
      throw new Error(`Invalid or duplicate CLI flag: ${flag ?? '<missing>'}`);
    }
    opts[flag.slice(2)] = argv[i + 1];
  }
  return validateOptions(opts);
}

const invokedAsMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (invokedAsMain) {
  try {
    const result = await auditPublishedApk(parseCli(process.argv.slice(2)));
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } catch (error) {
    process.stderr.write(`DELIVERY_UNVERIFIED: ${error instanceof Error ? error.message : 'Unknown error'}\n`);
    process.exitCode = 1;
  }
}
