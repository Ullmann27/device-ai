import test from 'node:test';
import assert from 'node:assert/strict';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import http from 'node:http';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {randomBytes} from 'node:crypto';
import {LocalBlockAdapter, sha256, sealBlock, openBlock, storageManifest, privateRoot, MAX_BLOCK_BYTES} from './storage-fabric.mjs';
import {ReplicatedStorageFabric} from './storage-fabric-catalog.mjs';

const serverScript = new URL('../../scripts/continuum-storage-fabric-node.mjs', import.meta.url);
const synthetic = (size, seed = 17) => Buffer.from(Array.from({length: size}, (_, i) => (i * 29 + seed) & 255));
async function raw(url, requestPath, {token, method = 'GET', body} = {}) {
 return new Promise((resolve, reject) => {
  const req = http.request(url, {path: requestPath, method, headers: {...(token ? {authorization: `Bearer ${token}`} : {}), ...(body ? {'content-length': body.length} : {})}}, res => {
   const chunks = []; res.on('data', c => chunks.push(c)); res.on('end', () => resolve({status: res.statusCode, bytes: Buffer.concat(chunks)}));
  }); req.on('error', reject); req.setTimeout(3000, () => req.destroy(Error('TEST_TIMEOUT'))); req.end(body);
 });
}
async function fixture(t, {quotaBytes = 524288, cacheQuotaBytes = 262144} = {}) {
 const base = await fs.mkdtemp(path.join(os.tmpdir(), 'continuum-storage-test-'));
 await fs.chmod(base, 0o700);
 const roots = [path.join(base, 'node-a'), path.join(base, 'node-b')], catalogRoot = path.join(base, 'catalog'), cacheRoot = path.join(base, 'cache');
 for (const dir of [...roots, catalogRoot, cacheRoot]) await fs.mkdir(dir, {mode: 0o700});
 const key = randomBytes(32), token = randomBytes(32).toString('base64url'), readToken = randomBytes(32).toString('base64url');
 const processes = [], adapters = [], configs = [];
 let fabric;
 async function start(index) {
  const configPath = path.join(base, `config-${index}.json`);
  await fs.writeFile(configPath, JSON.stringify({nodeId: `node-${index}`, namespace: 'research', root: roots[index], quotaBytes,
   port: configs[index]?.port ?? 0,
   tokens: [{token, namespace: 'research', operations: ['read', 'write', 'delete', 'status']}, {token: readToken, namespace: 'research', operations: ['read']}]}), {mode: 0o600});
  const child = spawn(process.execPath, [serverScript.pathname, configPath], {stdio: ['ignore', 'pipe', 'pipe']}); processes[index] = child;
  const ready = await new Promise((resolve, reject) => {
   let output = ''; const timer = setTimeout(() => { child.kill('SIGKILL'); reject(Error('NODE_READY_TIMEOUT')); }, 5000);
   child.once('exit', () => { clearTimeout(timer); reject(Error('NODE_START_FAILED')); });
   child.stdout.on('data', chunk => { output += chunk; if (output.includes('\n')) { clearTimeout(timer); try { resolve(JSON.parse(output.split('\n')[0])); } catch { reject(Error('INVALID_READY')); } } });
  });
  configs[index] = {port: Number(new URL(ready.url).port)};
  adapters[index] = new LocalBlockAdapter({nodeId: `node-${index}`, namespace: 'research', baseUrl: ready.url, token, timeoutMs: 500});
  return ready;
 }
 async function stop(index) {
  const child = processes[index]; if (!child || child.exitCode !== null) return;
  const exited = once(child, 'exit'); child.kill('SIGTERM');
  const timer = setTimeout(() => child.kill('SIGKILL'), 5000);
  await exited; clearTimeout(timer);
 }
 t.after(async () => { await fabric?.close(); await Promise.all(processes.map((_, i) => stop(i))); key.fill(0); await fs.rm(base, {recursive: true, force: true}); });
 await start(0); await start(1);
 async function open() { fabric = await ReplicatedStorageFabric.open({catalogRoot, cacheRoot, encryptionKey: key, nodes: adapters, cacheQuotaBytes}); return fabric; }
 await open();
 return {base, roots, catalogRoot, cacheRoot, key, token, readToken, adapters, start, stop, open, get fabric() { return fabric; }};
}

test('AES-GCM envelope validates ciphertext digest, plaintext digest, key and auth tag', () => {
 const key = Buffer.alloc(32, 3), data = synthetic(4096), {bytes, descriptor} = sealBlock(data, key);
 assert.deepEqual(openBlock(bytes, descriptor, key), data);
 assert.notDeepEqual(bytes.subarray(32), data);
 const damaged = Buffer.from(bytes); damaged[33] ^= 1;
 assert.throws(() => openBlock(damaged, descriptor, key), /HASH/);
 assert.throws(() => openBlock(bytes, {...descriptor, sha256: 'f'.repeat(64)}, key), /HASH/);
 assert.throws(() => openBlock(bytes, descriptor, Buffer.alloc(32, 4)));
 const tag = Buffer.from(bytes); tag[16] ^= 1;
 assert.throws(() => openBlock(tag, {...descriptor, storedSha256: sha256(tag)}, key));
 assert.throws(() => sealBlock(synthetic(MAX_BLOCK_BYTES + 1), key), /BLOCK_LIMIT/);
});

test('real two-process replication, corruption failover, repair, unavailable node, cache and restart', async t => {
 const f = await fixture(t), data = synthetic(90000);
 assert.notEqual(f.adapters[0].baseUrl, f.adapters[1].baseUrl);
 assert.notEqual(f.roots[0], f.roots[1]);
 const entry = await f.fabric.put('synthetic-model-reference', data);
 assert.equal(entry.blocks.length, 2);
 for (const node of f.adapters) for (const block of entry.blocks) assert.equal(sha256(await node.get(block.storedSha256)), block.storedSha256);
 for (const root of f.roots) assert.notDeepEqual(await fs.readFile(path.join(root, entry.blocks[0].storedSha256)), data.subarray(0, 65536));
 await fs.writeFile(path.join(f.roots[0], entry.blocks[0].storedSha256), synthetic(entry.blocks[0].storedBytes, 99));
 const failover = await f.fabric.get('synthetic-model-reference'); assert.deepEqual(failover.bytes, data); assert.equal(failover.sources[0], 'node-1');
 assert.equal((await f.fabric.repair('synthetic-model-reference')).copiesVerified, 4);
 assert.equal(sha256(await f.adapters[0].get(entry.blocks[0].storedSha256)), entry.blocks[0].storedSha256);
 await f.stop(0); assert.deepEqual((await f.fabric.get('synthetic-model-reference')).bytes, data);
 const revision = f.fabric.state.revision;
 await assert.rejects(f.fabric.put('incomplete-write', synthetic(64)), /FULL_REPLICATION_REQUIRED/);
 assert.equal(f.fabric.state.revision, revision); await assert.rejects(f.fabric.get('incomplete-write'), /NOT_FOUND/);
 await f.stop(1); const cached = await f.fabric.get('synthetic-model-reference'); assert.deepEqual(cached.bytes, data); assert.ok(cached.sources.every(s => s === 'verified-encrypted-cache'));
 await fs.writeFile(path.join(f.cacheRoot, entry.blocks[0].storedSha256), synthetic(entry.blocks[0].storedBytes, 77));
 await assert.rejects(f.fabric.get('synthetic-model-reference'), /NO_VALID_REPLICA/);
 await f.start(0); await f.start(1); await f.fabric.repair('synthetic-model-reference');
 await f.fabric.close(); await f.open(); assert.deepEqual((await f.fabric.get('synthetic-model-reference')).bytes, data);
 const manifest = await f.fabric.manifest(); assert.equal(manifest.executable, false); assert.equal(manifest.catalogHighlyAvailable, false);
 assert.equal(manifest.replicatedAvailableBytes, Math.min(...manifest.nodes.map(n => n.availableBytes)));
 for (const node of manifest.nodes) { assert.equal(node.quotaBytes, 524288); assert.ok(node.diskAvailableBytes > 0); assert.ok(node.availableBytes <= node.quotaBytes - node.usedBytes); }
 assert.equal(JSON.stringify(manifest).includes(f.token), false); assert.equal(JSON.stringify(manifest).includes(f.base), false);
});

test('versions survive restart; tombstone commits before unavailable replica deletion and reconciles recovery', async t => {
 const f = await fixture(t); const first = synthetic(240), second = synthetic(370, 31);
 const v1 = await f.fabric.put('reference', first), v2 = await f.fabric.put('reference', second);
 assert.equal(v2.version, 2); assert.deepEqual((await f.fabric.get('reference', {version: 1})).bytes, first);
 await f.fabric.close(); await f.open(); assert.deepEqual((await f.fabric.get('reference')).bytes, second);
 await f.stop(1); const deleted = await f.fabric.delete('reference'); assert.equal(deleted.pendingDeleteBlocks, 2); assert.equal(deleted.tombstonesDurable, true);
 await assert.rejects(f.fabric.get('reference'), /TOMBSTONED/); await assert.rejects(f.fabric.get('reference', {version: 1}), /TOMBSTONED/);
 await assert.rejects(f.fabric.put('reference', first), /TOMBSTONE_REUSE/);
 await f.fabric.close(); await f.open(); await assert.rejects(f.fabric.get('reference'), /TOMBSTONED/);
 await f.start(1); assert.equal((await f.fabric.reconcileDeletes()).pendingDeleteBlocks, 0);
 for (const node of f.adapters) for (const entry of [v1, v2]) await assert.rejects(node.get(entry.blocks[0].storedSha256), /404/);
 assert.deepEqual(await fs.readdir(f.cacheRoot), []);
});

test('real HTTP rejects absent/wrong/scoped tokens, traversal, wrong namespace, malformed hash and oversized body', async t => {
 const f = await fixture(t), base = f.adapters[0].baseUrl, route = '/v1/research/blocks/' + 'a'.repeat(64);
 assert.equal((await raw(base, route)).status, 401);
 assert.equal((await raw(base, route, {token: 'X'.repeat(43)})).status, 401);
 assert.equal((await raw(base, route, {token: f.readToken, method: 'PUT', body: synthetic(32)})).status, 403);
 assert.equal((await raw(base, '/v1/research/capacity', {token: f.readToken})).status, 403);
 for (const suffix of ['../../etc/passwd', '%2e%2e/%2e%2e/etc/passwd', '%2Fetc%2Fpasswd', 'a'.repeat(64) + '?redirect=http://example.invalid', 'A'.repeat(64), '$(touch-marker)']) {
  assert.equal((await raw(base, '/v1/research/blocks/' + suffix, {token: f.token})).status, 400);
 }
 assert.equal((await raw(base, '/v1/other/blocks/' + 'a'.repeat(64), {token: f.token})).status, 400);
 assert.equal((await raw(base, route, {token: f.token, method: 'PUT', body: synthetic(100)})).status, 422);
 assert.equal((await raw(base, route, {token: f.token, method: 'PUT', body: synthetic(MAX_BLOCK_BYTES + 33)})).status, 413);
 await assert.rejects(f.fabric.put('../outside', synthetic(12)), /INVALID_ID/);
 await assert.rejects(f.fabric.put('memory', synthetic(12), {classification: 'OWNER_PRIVATE'}), /EXCLUDED/);
 await assert.rejects(f.fabric.put('memory', synthetic(12), {classification: 'PERSONAL_MEMORY'}), /EXCLUDED/);
 assert.throws(() => new LocalBlockAdapter({nodeId: 'x', baseUrl: 'http://example.com', namespace: 'research', token: f.token}), /LOOPBACK/);
});

test('real disk/quota limits reject new writes without committing object or claiming synthetic capacity', async t => {
 const f = await fixture(t, {quotaBytes: 1024}); await f.fabric.put('small', synthetic(800));
 const revision = f.fabric.state.revision;
 await assert.rejects(f.fabric.put('overflow', synthetic(800)), /FULL_REPLICATION_REQUIRED/);
 assert.equal(f.fabric.state.revision, revision); assert.equal((await f.adapters[0].capacity()).usedBytes, 832);
 const manifest = await f.fabric.manifest(); assert.equal(manifest.replicatedAvailableBytes, 192);
 assert.throws(() => storageManifest({catalogRevision: 1, nodes: [{nodeId: 'fake'}, {nodeId: 'fake2'}]}), /CAPACITY/);
});

test('root/block/cache symlinks and catalog HMAC tampering fail closed; untrusted bytes are data only', async t => {
 const f = await fixture(t), outside = path.join(f.base, 'outside'); await fs.writeFile(outside, 'SYNTHETIC-UNCHANGED', {mode: 0o600});
 const hash = 'b'.repeat(64); await fs.symlink(outside, path.join(f.roots[0], hash));
 const route = '/v1/research/blocks/' + hash;
 assert.equal((await raw(f.adapters[0].baseUrl, route, {token: f.token})).status, 500);
 assert.equal((await raw(f.adapters[0].baseUrl, route, {token: f.token, method: 'DELETE'})).status, 500);
 assert.equal(await fs.readFile(outside, 'utf8'), 'SYNTHETIC-UNCHANGED'); await fs.unlink(path.join(f.roots[0], hash));
 const linked = path.join(f.base, 'linked-root'); await fs.symlink(f.roots[0], linked); await assert.rejects(privateRoot(linked), /UNSAFE_ROOT/);
 const payload = Buffer.from('$(touch NEVER_EXECUTE); require("node:child_process").execSync("touch NEVER_EXECUTE")');
 const entry = await f.fabric.put('untrusted-bytes', payload); assert.deepEqual((await f.fabric.get('untrusted-bytes')).bytes, payload);
 await assert.rejects(fs.stat(path.join(f.base, 'NEVER_EXECUTE')), {code: 'ENOENT'});
 await f.stop(0); await f.stop(1); await fs.unlink(path.join(f.cacheRoot, entry.blocks[0].storedSha256)); await fs.symlink(outside, path.join(f.cacheRoot, entry.blocks[0].storedSha256));
 await assert.rejects(f.fabric.get('untrusted-bytes'), /NO_VALID_REPLICA/); assert.equal(await fs.readFile(outside, 'utf8'), 'SYNTHETIC-UNCHANGED');
 await fs.unlink(path.join(f.cacheRoot, entry.blocks[0].storedSha256));
 await assert.rejects(ReplicatedStorageFabric.open({catalogRoot: f.catalogRoot, cacheRoot: f.cacheRoot, encryptionKey: f.key, nodes: f.adapters}), {code: 'EEXIST'});
 await f.fabric.close();
 const snapshot = path.join(f.catalogRoot, 'catalog-v1-001.json'), tampered = JSON.parse(await fs.readFile(snapshot, 'utf8')); tampered.payload.objects[0].deleted = true;
 await fs.writeFile(snapshot, JSON.stringify(tampered)); await assert.rejects(f.open(), /CATALOG_AUTH_FAILED/);
});

test('bounded encrypted cache evicts within quota and does not replace object/tombstone checks', async t => {
 const f = await fixture(t, {cacheQuotaBytes: 128}); await f.fabric.put('first', synthetic(80)); await f.fabric.put('second', synthetic(80, 61));
 const files = await fs.readdir(f.cacheRoot); let used = 0; for (const file of files) used += (await fs.stat(path.join(f.cacheRoot, file))).size;
 assert.ok(used <= 128); assert.equal(files.length, 1);
 await f.stop(0); await f.stop(1); await assert.rejects(f.fabric.get('first'), /NO_VALID_REPLICA/); assert.deepEqual((await f.fabric.get('second')).bytes, synthetic(80, 61));
 await assert.rejects(f.fabric.get('unknown'), /NOT_FOUND/);
});
