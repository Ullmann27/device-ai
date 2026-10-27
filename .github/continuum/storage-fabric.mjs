/** Bounded research storage. Authority stays with existing owner/ComputeFabric gates. */
import {constants as C} from 'node:fs';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import {createHash, createHmac, randomBytes, createCipheriv, createDecipheriv, timingSafeEqual} from 'node:crypto';

export const MAX_BLOCK_BYTES = 65536;
export const MAX_OBJECT_BYTES = 1048576;
export const SHA256 = /^[a-f0-9]{64}$/;
export const SAFE_ID = /^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$/;
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const fail = code => { throw Error(code); };
export const requireId = value => { if (!SAFE_ID.test(value ?? '')) fail('INVALID_ID'); return value; };
export const requireHash = value => { if (!SHA256.test(value ?? '')) fail('INVALID_HASH'); return value; };
export const integer = (n, min, max) => Number.isSafeInteger(n) && n >= min && n <= max;

/** Reject symlinks in every existing ancestor; roots must already exist and be owner-private. */
export async function privateRoot(root) {
 if (typeof root !== 'string' || !path.isAbsolute(root) || path.normalize(root) !== root) fail('ROOT_INVALID');
 let current = path.parse(root).root;
 for (const segment of root.slice(current.length).split('/').filter(Boolean)) {
  current = path.join(current, segment);
  const st = await fs.lstat(current);
  if (!st.isDirectory() || st.isSymbolicLink()) fail('UNSAFE_ROOT');
 }
 const st = await fs.lstat(root);
 if ((st.mode & 0o077) || (process.getuid && st.uid !== process.getuid())) fail('ROOT_NOT_PRIVATE');
 return root;
}
export async function readBounded(file, maxBytes) {
 const handle = await fs.open(file, C.O_RDONLY | C.O_NOFOLLOW);
 try {
  const st = await handle.stat();
  if (!st.isFile() || st.size > maxBytes || st.nlink !== 1) fail('UNSAFE_FILE');
  const bytes = Buffer.alloc(st.size); let offset = 0;
  while (offset < bytes.length) {
   const result = await handle.read(bytes, offset, bytes.length - offset, offset);
   if (!result.bytesRead) fail('TRUNCATED_FILE'); offset += result.bytesRead;
  }
  if ((await handle.stat()).size !== st.size) fail('FILE_CHANGED');
  return bytes;
 } finally { await handle.close(); }
}
export async function syncDirectory(root) {
 const h = await fs.open(root, C.O_RDONLY | C.O_DIRECTORY | C.O_NOFOLLOW);
 try { await h.sync(); } finally { await h.close(); }
}
export async function atomicWrite(root, filename, bytes) {
 await privateRoot(root);
 if (!/^[a-zA-Z0-9_.-]{1,100}$/.test(filename)) fail('UNSAFE_FILENAME');
 const temporary = `.tmp-${randomBytes(12).toString('hex')}`;
 const tempPath = path.join(root, temporary), destination = path.join(root, filename);
 try {
  try { const st = await fs.lstat(destination); if (!st.isFile() || st.nlink !== 1) fail('UNSAFE_FILE'); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const handle = await fs.open(tempPath, C.O_WRONLY | C.O_CREAT | C.O_EXCL | C.O_NOFOLLOW, 0o600);
  try { await handle.writeFile(bytes); await handle.sync(); } finally { await handle.close(); }
  await fs.rename(tempPath, destination); await syncDirectory(root);
 } finally { await fs.unlink(tempPath).catch(error => { if (error.code !== 'ENOENT') throw error; }); }
}
export async function removeSafe(root, filename) {
 await privateRoot(root); const target = path.join(root, filename);
 try { const st = await fs.lstat(target); if (!st.isFile() || st.nlink !== 1) fail('UNSAFE_FILE'); await fs.unlink(target); await syncDirectory(root); }
 catch (error) { if (error.code !== 'ENOENT') throw error; }
}
function keyBytes(key) {
 if (!Buffer.isBuffer(key) || key.length !== 32) fail('AES_KEY_INVALID'); return key;
}
export function sealBlock(plaintext, key) {
 keyBytes(key); if (!Buffer.isBuffer(plaintext) || !integer(plaintext.length, 0, MAX_BLOCK_BYTES)) fail('BLOCK_LIMIT');
 const nonce = randomBytes(12), cipher = createCipheriv('aes-256-gcm', key, nonce);
 cipher.setAAD(Buffer.from('continuum-storage-block-v1'));
 const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final()]);
 const bytes = Buffer.concat([Buffer.from('CF01'), nonce, cipher.getAuthTag(), ciphertext]);
 return {bytes, descriptor: {sha256: sha256(plaintext), storedSha256: sha256(bytes), bytes: plaintext.length, storedBytes: bytes.length}};
}
export function openBlock(bytes, descriptor, key) {
 keyBytes(key);
 if (!Buffer.isBuffer(bytes) || bytes.length !== descriptor.storedBytes || sha256(bytes) !== descriptor.storedSha256 || bytes.subarray(0, 4).toString() !== 'CF01') fail('BLOCK_HASH_MISMATCH');
 const cipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(4, 16));
 cipher.setAAD(Buffer.from('continuum-storage-block-v1')); cipher.setAuthTag(bytes.subarray(16, 32));
 const plaintext = Buffer.concat([cipher.update(bytes.subarray(32)), cipher.final()]);
 if (plaintext.length !== descriptor.bytes || sha256(plaintext) !== descriptor.sha256) fail('PLAINTEXT_HASH_MISMATCH');
 return plaintext;
}
export function signCatalog(payload, key) {
 const bytes = JSON.stringify(payload);
 return JSON.stringify({payload, hmacSha256: createHmac('sha256', keyBytes(key)).update('continuum-storage-catalog-v1\n' + bytes).digest('hex')}) + '\n';
}
export function verifyCatalog(bytes, key) {
 const envelope = JSON.parse(bytes.toString());
 if (!SHA256.test(envelope.hmacSha256 ?? '')) fail('CATALOG_AUTH_FAILED');
 const expected = createHmac('sha256', keyBytes(key)).update('continuum-storage-catalog-v1\n' + JSON.stringify(envelope.payload)).digest();
 if (!timingSafeEqual(expected, Buffer.from(envelope.hmacSha256, 'hex'))) fail('CATALOG_AUTH_FAILED');
 return envelope.payload;
}

/** Strict localhost reference adapter. A future SeaweedFS filer adapter can implement the same methods. */
export class LocalBlockAdapter {
 constructor({nodeId, baseUrl, namespace, token, timeoutMs = 1500}) {
  this.nodeId = requireId(nodeId); this.namespace = requireId(namespace);
  const url = new URL(baseUrl);
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password || url.search || url.hash || url.pathname !== '/') fail('LOOPBACK_ENDPOINT_REQUIRED');
  if (!/^[A-Za-z0-9_-]{32,128}$/.test(token ?? '') || !integer(timeoutMs, 50, 5000)) fail('ADAPTER_CONFIG_INVALID');
  this.baseUrl = url.origin; this.token = token; this.timeoutMs = timeoutMs;
 }
 async request(method, suffix, body, maxBytes = MAX_BLOCK_BYTES + 32) {
  const response = await fetch(`${this.baseUrl}/v1/${this.namespace}/${suffix}`, {method, body,
   headers: {authorization: `Bearer ${this.token}`}, signal: AbortSignal.timeout(this.timeoutMs), redirect: 'error'});
  if (!response.ok) { await response.body?.cancel(); fail(`NODE_HTTP_${response.status}`); }
  const reader = response.body.getReader(); let total = 0; const chunks = [];
  try { while (true) { const {done, value} = await reader.read(); if (done) break; total += value.length; if (total > maxBytes) fail('NODE_RESPONSE_LIMIT'); chunks.push(value); } }
  finally { await reader.cancel(); }
  return Buffer.concat(chunks);
 }
 async put(hash, bytes) { requireHash(hash); if (sha256(bytes) !== hash) fail('BLOCK_HASH_MISMATCH'); await this.request('PUT', `blocks/${hash}`, bytes, 2048); }
 async get(hash) { return this.request('GET', `blocks/${requireHash(hash)}`); }
 async delete(hash) { await this.request('DELETE', `blocks/${requireHash(hash)}`, undefined, 2048); }
 async capacity() { return JSON.parse((await this.request('GET', 'capacity', undefined, 2048)).toString()); }
}

/** No endpoints, tokens, keys, permissions or execution authority are exported. */
export function storageManifest({catalogRevision, nodes}) {
 if (!integer(catalogRevision, 0, 128) || !Array.isArray(nodes) || nodes.length < 2 || nodes.length > 4 || new Set(nodes.map(n => n.nodeId)).size !== nodes.length) fail('MANIFEST_INVALID');
 const entries = nodes.map(n => {
  requireId(n.nodeId);
  for (const field of ['quotaBytes', 'usedBytes', 'diskAvailableBytes', 'availableBytes', 'measuredAtMillis']) if (!integer(n[field], 0, Number.MAX_SAFE_INTEGER)) fail('CAPACITY_INVALID');
  if (n.usedBytes > n.quotaBytes || n.availableBytes > Math.min(n.quotaBytes - n.usedBytes, n.diskAvailableBytes)) fail('CAPACITY_INVALID');
  return Object.freeze({nodeId: n.nodeId, quotaBytes: n.quotaBytes, usedBytes: n.usedBytes, diskAvailableBytes: n.diskAvailableBytes, availableBytes: n.availableBytes, measuredAtMillis: n.measuredAtMillis});
 });
 return Object.freeze({schema: 'continuum-storage-manifest', version: 1, catalogRevision,
  backend: 'node-stdlib-localhost-research', catalogHighlyAvailable: false,
  nodes: Object.freeze(entries), replicatedAvailableBytes: Math.min(...entries.map(n => n.availableBytes)),
  physicalFailureDomainsVerified: false, availableBytesAreReservations: false,
  personalMemoryIncluded: false, performsInference: false, grantsPermission: false, executable: false});
}
