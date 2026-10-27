/** Explicit localhost-only research block node. Config/credentials must be private files outside Git. */
import http from 'node:http';
import * as fs from 'node:fs/promises';
import path from 'node:path';
import {timingSafeEqual} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {MAX_BLOCK_BYTES, SAFE_ID, SHA256, integer, privateRoot, readBounded, atomicWrite, removeSafe, sha256} from '../.github/continuum/storage-fabric.mjs';
const reject = (code, status = 400) => { throw Object.assign(Error(code), {status}); };

export async function startStorageNode(config) {
 const {root, nodeId, namespace, quotaBytes, tokens, port = 0, reserveBytes = 65536} = config ?? {};
 if (!SAFE_ID.test(nodeId ?? '') || !SAFE_ID.test(namespace ?? '') || !integer(quotaBytes, 1024, 67108864) || !integer(reserveBytes, 0, 1048576) || !integer(port, 0, 65535) || !Array.isArray(tokens) || !tokens.length || tokens.length > 8) reject('CONFIG_INVALID');
 const allowed = new Set(['read', 'write', 'delete', 'status']);
 for (const t of tokens) if (!/^[A-Za-z0-9_-]{32,128}$/.test(t?.token ?? '') || t.namespace !== namespace || !Array.isArray(t.operations) || !t.operations.length || t.operations.some(x => !allowed.has(x))) reject('TOKEN_CONFIG_INVALID');
 if (new Set(tokens.map(t => t.token)).size !== tokens.length) reject('DUPLICATE_TOKEN');
 await privateRoot(root);
 const lock = await fs.open(path.join(root, '.server.lock'), 'wx', 0o600);
 await lock.writeFile(JSON.stringify({pid: process.pid, nodeId})); await lock.sync();
 let usedBytes = 0, blockCount = 0;
 try {
  const names = await fs.readdir(root); if (names.length > 1025) reject('ROOT_ENTRY_LIMIT');
  for (const name of names) {
   if (name === '.server.lock') continue;
   if (!SHA256.test(name)) reject('UNEXPECTED_ROOT_ENTRY');
   const st = await fs.lstat(path.join(root, name));
   if (!st.isFile() || st.nlink !== 1 || st.size > MAX_BLOCK_BYTES + 32) reject('UNSAFE_BLOCK');
   usedBytes += st.size; blockCount++;
  }
  if (usedBytes > quotaBytes) reject('QUOTA_ALREADY_EXCEEDED');
 } catch (error) { await lock.close(); await fs.unlink(path.join(root, '.server.lock')); throw error; }
 const capacity = async () => {
  await privateRoot(root);
  const stat = await fs.statfs(root); const diskAvailableBytes = Math.floor(stat.bsize * stat.bavail);
  if (!Number.isSafeInteger(diskAvailableBytes)) reject('CAPACITY_UNAVAILABLE', 503);
  return {nodeId, quotaBytes, usedBytes, diskAvailableBytes,
   availableBytes: Math.max(0, Math.min(quotaBytes - usedBytes, diskAvailableBytes - reserveBytes)), measuredAtMillis: Date.now()};
 };
 const authorize = (header, op) => {
  if (typeof header !== 'string' || !/^Bearer [A-Za-z0-9_-]{32,128}$/.test(header)) reject('AUTH_REQUIRED', 401);
  const supplied = Buffer.from(header.slice(7));
  const match = tokens.find(t => { const candidate = Buffer.from(t.token); return candidate.length === supplied.length && timingSafeEqual(candidate, supplied); });
  if (!match) reject('AUTH_REQUIRED', 401);
  if (!match.operations.includes(op)) reject('SCOPE_DENIED', 403);
 };
 let queue = Promise.resolve(), active = 0;
 const handle = async (req, res) => {
  try {
   const match = /^\/v1\/([A-Za-z0-9_-]{1,64})\/(capacity|blocks\/([a-f0-9]{64}))$/.exec(req.url ?? '');
   if (!match || match[1] !== namespace) reject('PATH_DENIED', 400);
   const isCapacity = match[2] === 'capacity';
   const operation = isCapacity && req.method === 'GET' ? 'status' : {GET: 'read', PUT: 'write', DELETE: 'delete'}[req.method];
   if (!operation || (isCapacity && req.method !== 'GET')) reject('METHOD_DENIED', 405);
   authorize(req.headers.authorization, operation);
   res.setHeader('content-type', 'application/octet-stream');
   if (isCapacity) { res.end(JSON.stringify(await capacity())); return; }
   const hash = match[3], target = path.join(root, hash); await privateRoot(root);
   if (req.method === 'GET') {
    const bytes = await readBounded(target, MAX_BLOCK_BYTES + 32);
    if (sha256(bytes) !== hash) reject('CORRUPT_BLOCK', 422); res.end(bytes); return;
   }
   if (req.method === 'DELETE') {
    let size = 0; try { size = (await readBounded(target, MAX_BLOCK_BYTES + 32)).length; } catch (e) { if (e.code !== 'ENOENT') throw e; }
    await removeSafe(root, hash); usedBytes -= size; if (size) blockCount--; res.end('{}'); return;
   }
   if (!/^\d+$/.test(req.headers['content-length'] ?? '') || !integer(Number(req.headers['content-length']), 32, MAX_BLOCK_BYTES + 32)) reject('BODY_LIMIT', 413);
   let total = 0; const chunks = [];
   for await (const bytes of req) { total += bytes.length; if (total > MAX_BLOCK_BYTES + 32) reject('BODY_LIMIT', 413); chunks.push(bytes); }
   const bytes = Buffer.concat(chunks); if (bytes.length < 32 || sha256(bytes) !== hash) reject('BLOCK_HASH_MISMATCH', 422);
   let previous = 0; try { previous = (await readBounded(target, MAX_BLOCK_BYTES + 32)).length; } catch (e) { if (e.code !== 'ENOENT') throw e; }
   if ((!previous && blockCount >= 1024) || bytes.length - previous > (await capacity()).availableBytes) reject('QUOTA_EXCEEDED', 507);
   await atomicWrite(root, hash, bytes); usedBytes += bytes.length - previous; if (!previous) blockCount++; res.end('{}');
  } catch (error) {
   res.statusCode = error.status ?? (error.code === 'ENOENT' ? 404 : 500);
   res.end(JSON.stringify({error: error.status ? error.message : error.code === 'ENOENT' ? 'BLOCK_MISSING' : 'STORAGE_REJECTED'}));
  }
 };
 const server = http.createServer((req, res) => {
  if (++active > 8) { active--; res.writeHead(503); res.end('{}'); return; }
  req.pause(); const run = queue.then(() => handle(req, res));
  queue = run.catch(() => {}).finally(() => { active--; });
 });
 server.requestTimeout = 3000; server.headersTimeout = 2000; server.keepAliveTimeout = 1000; server.maxConnections = 16;
 await new Promise((resolve, rejectListen) => { server.once('error', rejectListen); server.listen(port, '127.0.0.1', resolve); }).catch(async error => { await lock.close(); await fs.unlink(path.join(root, '.server.lock')); throw error; });
 let closed = false;
 return {url: `http://127.0.0.1:${server.address().port}`, nodeId, async close() {
  if (closed) return; closed = true;
  await new Promise(resolve => { server.close(resolve); server.closeIdleConnections(); }); await queue;
  await lock.close(); await fs.unlink(path.join(root, '.server.lock'));
 }};
}

async function main() {
 if (process.argv.length !== 3) throw Error('USAGE: node continuum-storage-fabric-node.mjs /absolute/private-config.json');
 const file = process.argv[2]; if (!path.isAbsolute(file)) throw Error('CONFIG_PATH_INVALID');
 await privateRoot(path.dirname(file)); const st = await fs.lstat(file);
 if ((st.mode & 0o077) || st.nlink !== 1) throw Error('CONFIG_NOT_PRIVATE');
 const config = JSON.parse((await readBounded(file, 8192)).toString());
 const node = await startStorageNode(config); process.stdout.write(JSON.stringify({ready: true, nodeId: node.nodeId, url: node.url}) + '\n');
 let stopping = false;
 const stop = async () => { if (stopping) return; stopping = true; await node.close(); process.exitCode = 0; };
 process.on('SIGTERM', stop); process.on('SIGINT', stop);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main().catch(() => { process.stderr.write('STORAGE_NODE_START_REJECTED\n'); process.exitCode = 1; });
