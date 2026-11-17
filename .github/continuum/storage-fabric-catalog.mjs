/** Single-writer research catalog; immutable HMAC snapshots are durable but NOT HA. */
import * as fs from 'node:fs/promises';
import path from 'node:path';
import {MAX_BLOCK_BYTES, MAX_OBJECT_BYTES, integer, requireId, requireHash, privateRoot, readBounded, atomicWrite, removeSafe, sealBlock, openBlock, signCatalog, verifyCatalog, sha256, storageManifest} from './storage-fabric.mjs';
const reject = code => { throw Error(code); };
const SNAPSHOT = /^catalog-v1-(\d{3})\.json$/;
const MAX_METADATA = 524288;
const clone = value => structuredClone(value);

function validateState(state, revision) {
 if (state?.schema !== 'continuum-storage-catalog' || state.version !== 1 || state.revision !== revision || !integer(revision, 1, 128) || !Array.isArray(state.objects) || state.objects.length > 64 || !Array.isArray(state.pendingDeletes) || state.pendingDeletes.length > 1024) reject('CATALOG_INVALID');
 const keys = new Set();
 for (const object of state.objects) {
  requireId(object.key); if (keys.has(object.key) || typeof object.deleted !== 'boolean' || !Array.isArray(object.versions) || object.versions.length < 1 || object.versions.length > 8) reject('CATALOG_INVALID'); keys.add(object.key);
  if (object.deleted && (!integer(object.tombstoneRevision, 1, revision))) reject('CATALOG_INVALID');
  for (const [index, v] of object.versions.entries()) {
   if (v.version !== index + 1 || v.classification !== 'PUBLIC_RESEARCH' || !integer(v.bytes, 0, MAX_OBJECT_BYTES) || !Array.isArray(v.blocks) || v.blocks.length < 1 || v.blocks.length > 16) reject('CATALOG_INVALID');
   requireHash(v.sha256); let total = 0;
   for (const block of v.blocks) {
    requireHash(block.sha256); requireHash(block.storedSha256);
    if (!integer(block.bytes, 0, MAX_BLOCK_BYTES) || block.storedBytes !== block.bytes + 32) reject('CATALOG_INVALID'); total += block.bytes;
   }
   if (v.bytes !== total) reject('CATALOG_INVALID');
  }
 }
 for (const hash of state.pendingDeletes) requireHash(hash);
 if (new Set(state.pendingDeletes).size !== state.pendingDeletes.length) reject('CATALOG_INVALID');
}

export class ReplicatedStorageFabric {
 static async open({catalogRoot, cacheRoot, encryptionKey, nodes, cacheQuotaBytes = 262144}) {
  await privateRoot(catalogRoot); await privateRoot(cacheRoot);
  if (catalogRoot === cacheRoot || !Buffer.isBuffer(encryptionKey) || encryptionKey.length !== 32 || !Array.isArray(nodes) || nodes.length < 2 || nodes.length > 4 || new Set(nodes.map(n => n.nodeId)).size !== nodes.length || !integer(cacheQuotaBytes, 0, 4194304)) reject('FABRIC_CONFIG_INVALID');
  const lockPath = path.join(catalogRoot, '.catalog.lock'), lock = await fs.open(lockPath, 'wx', 0o600);
  try {
   await lock.writeFile(JSON.stringify({pid: process.pid})); await lock.sync();
   const names = await fs.readdir(catalogRoot);
   if (names.length > 129 || names.some(n => n !== '.catalog.lock' && !SNAPSHOT.test(n))) reject('CATALOG_DIRECTORY_INVALID');
   const snapshots = names.filter(n => SNAPSHOT.test(n)).sort(); let state = {schema: 'continuum-storage-catalog', version: 1, revision: 0, objects: [], pendingDeletes: []};
   // Validate every retained snapshot and gaps; deletion of a signed history suffix needs an external monotonic anchor to detect.
   for (const [index, name] of snapshots.entries()) {
    const revision = Number(SNAPSHOT.exec(name)[1]); if (revision !== index + 1) reject('CATALOG_HISTORY_GAP');
    state = verifyCatalog(await readBounded(path.join(catalogRoot, name), MAX_METADATA), encryptionKey); validateState(state, revision);
   }
   const fabric = new ReplicatedStorageFabric(); Object.assign(fabric, {catalogRoot, cacheRoot, key: Buffer.from(encryptionKey), nodes: [...nodes], cacheQuotaBytes, state, lock, lockPath, closed: false, queue: Promise.resolve()});
   await fabric.trimCache(); return fabric;
  } catch (error) { await lock.close(); await fs.unlink(lockPath); throw error; }
 }
 run(action) {
  if (this.closed) return Promise.reject(Error('CATALOG_CLOSED'));
  const result = this.queue.then(action); this.queue = result.catch(() => {}); return result;
 }
 async commit(state) {
  state.revision = this.state.revision + 1; validateState(state, state.revision);
  const bytes = Buffer.from(signCatalog(state, this.key)); if (bytes.length > MAX_METADATA) reject('CATALOG_LIMIT');
  await atomicWrite(this.catalogRoot, `catalog-v1-${String(state.revision).padStart(3, '0')}.json`, bytes); this.state = state;
 }
 async trimCache() {
  const names = await fs.readdir(this.cacheRoot); if (names.length > 128) reject('CACHE_ENTRY_LIMIT');
  const entries = []; let used = 0;
  for (const hash of names) {
   requireHash(hash); const st = await fs.lstat(path.join(this.cacheRoot, hash));
   if (!st.isFile() || st.nlink !== 1 || st.size > MAX_BLOCK_BYTES + 32) reject('UNSAFE_CACHE');
   entries.push({hash, size: st.size, mtime: st.mtimeMs}); used += st.size;
  }
  entries.sort((a, b) => a.mtime - b.mtime || a.hash.localeCompare(b.hash));
  while (used > this.cacheQuotaBytes || entries.length >= 128) { const entry = entries.shift(); await removeSafe(this.cacheRoot, entry.hash); used -= entry.size; }
 }
 async cache(bytes, descriptor) {
  if (bytes.length > this.cacheQuotaBytes) return;
  await atomicWrite(this.cacheRoot, descriptor.storedSha256, bytes); await this.trimCache();
 }
 async readBlock(descriptor) {
  for (const node of this.nodes) {
   try { const bytes = await node.get(descriptor.storedSha256); const plaintext = openBlock(bytes, descriptor, this.key); await this.cache(bytes, descriptor); return {bytes, plaintext, source: node.nodeId}; }
   catch { /* An unavailable/corrupt replica is never returned. Try the next explicit replica. */ }
  }
  try {
   const bytes = await readBounded(path.join(this.cacheRoot, descriptor.storedSha256), MAX_BLOCK_BYTES + 32);
   return {bytes, plaintext: openBlock(bytes, descriptor, this.key), source: 'verified-encrypted-cache'};
  } catch {
   // A symlink is rejected without unlinking an outside target.
   await removeSafe(this.cacheRoot, descriptor.storedSha256).catch(() => {}); reject('NO_VALID_REPLICA_OR_CACHE');
  }
 }
 objectVersion(key, version) {
  requireId(key); const object = this.state.objects.find(o => o.key === key);
  if (!object || object.deleted) reject('OBJECT_NOT_FOUND_OR_TOMBSTONED');
  const entry = version === undefined ? object.versions.at(-1) : object.versions.find(v => v.version === version);
  if (!entry) reject('OBJECT_VERSION_NOT_FOUND'); return entry;
 }
 put(key, plaintext, {classification = 'PUBLIC_RESEARCH'} = {}) {
  return this.run(async () => {
   requireId(key); if (classification !== 'PUBLIC_RESEARCH') reject('PERSONAL_MEMORY_OR_PRIVATE_DATA_EXCLUDED');
   if (!Buffer.isBuffer(plaintext) || !integer(plaintext.length, 0, MAX_OBJECT_BYTES)) reject('OBJECT_LIMIT');
   const state = clone(this.state), existing = state.objects.find(o => o.key === key);
   if (existing?.deleted) reject('TOMBSTONE_REUSE_DENIED');
   if ((!existing && state.objects.length >= 64) || (existing?.versions.length ?? 0) >= 8 || state.revision >= 128) reject('CATALOG_LIMIT');
   const blocks = [];
   for (let offset = 0; offset < plaintext.length || (!offset && !plaintext.length); offset += MAX_BLOCK_BYTES) {
    const {bytes, descriptor} = sealBlock(plaintext.subarray(offset, offset + MAX_BLOCK_BYTES), this.key);
    const results = await Promise.allSettled(this.nodes.map(node => node.put(descriptor.storedSha256, bytes)));
    if (results.some(r => r.status === 'rejected')) reject('FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED');
    await this.cache(bytes, descriptor); blocks.push(descriptor);
   }
   const entry = {version: (existing?.versions.length ?? 0) + 1, classification, bytes: plaintext.length, sha256: sha256(plaintext), blocks};
   if (existing) existing.versions.push(entry); else state.objects.push({key, deleted: false, versions: [entry]});
   await this.commit(state); return clone(entry);
  });
 }
 get(key, {version} = {}) {
  return this.run(async () => {
   const entry = this.objectVersion(key, version), chunks = [], sources = [];
   for (const descriptor of entry.blocks) { const result = await this.readBlock(descriptor); chunks.push(result.plaintext); sources.push(result.source); }
   const bytes = Buffer.concat(chunks); if (bytes.length !== entry.bytes || sha256(bytes) !== entry.sha256) reject('OBJECT_HASH_MISMATCH');
   return {bytes, version: entry.version, sources};
  });
 }
 repair(key) {
  return this.run(async () => {
   const object = this.state.objects.find(o => o.key === requireId(key));
   if (!object || object.deleted) reject('OBJECT_NOT_FOUND_OR_TOMBSTONED');
   let copiesVerified = 0;
   for (const entry of object.versions) for (const descriptor of entry.blocks) {
    const {bytes} = await this.readBlock(descriptor);
    for (const node of this.nodes) { await node.put(descriptor.storedSha256, bytes); openBlock(await node.get(descriptor.storedSha256), descriptor, this.key); copiesVerified++; }
   }
   return {copiesVerified};
  });
 }
 delete(key) {
  return this.run(async () => {
   requireId(key); const state = clone(this.state), object = state.objects.find(o => o.key === key);
   if (!object) reject('OBJECT_NOT_FOUND_OR_TOMBSTONED');
   if (!object.deleted) {
    object.deleted = true; object.tombstoneRevision = state.revision + 1;
    const referenced = new Set(state.objects.filter(o => !o.deleted).flatMap(o => o.versions.flatMap(v => v.blocks.map(b => b.storedSha256))));
    state.pendingDeletes = [...new Set([...state.pendingDeletes, ...object.versions.flatMap(v => v.blocks.map(b => b.storedSha256))])].filter(hash => !referenced.has(hash));
    await this.commit(state); // Tombstone must be durable before any replica or cache removal.
   }
   return this.reconcileInternal();
  });
 }
 async reconcileInternal() {
  const state = clone(this.state), remaining = [];
  for (const hash of state.pendingDeletes) {
   const results = await Promise.allSettled(this.nodes.map(node => node.delete(hash)));
   await removeSafe(this.cacheRoot, hash);
   if (results.some(r => r.status === 'rejected')) remaining.push(hash);
  }
  if (remaining.length !== state.pendingDeletes.length) { state.pendingDeletes = remaining; await this.commit(state); }
  return {pendingDeleteBlocks: remaining.length, tombstonesDurable: true};
 }
 reconcileDeletes() { return this.run(() => this.reconcileInternal()); }
 manifest() { return this.run(async () => storageManifest({catalogRevision: this.state.revision, nodes: await Promise.all(this.nodes.map(n => n.capacity()))})); }
 async close() {
  if (this.closed) return; this.closed = true; await this.queue; this.key.fill(0); await this.lock.close(); await fs.unlink(this.lockPath);
 }
}
