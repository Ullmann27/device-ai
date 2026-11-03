/** Opt-in real-process/real-Kotlin integration check. No inference, shell patch or test receipt is fabricated. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const serviceCli = path.join(sourceRoot, 'scripts/continuum-coding-service.mjs');
const mcpCli = path.join(sourceRoot, 'scripts/continuum-coding-mcp.mjs');
const options = {};
for (let i = 2; i < process.argv.length; i += 2) {
  const key = process.argv[i], value = process.argv[i + 1];
  if (!['--classpath-file', '--runtime-env', '--isolation-config', '--output'].includes(key) || !value || Object.hasOwn(options, key)) throw new Error('CHECK_ARGUMENTS_INVALID');
  options[key] = path.resolve(value);
}
for (const key of ['--classpath-file', '--runtime-env', '--isolation-config', '--output']) if (!options[key]) throw new Error('CHECK_ARGUMENTS_REQUIRED');
const run = (command, args, cwd = sourceRoot) => {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', timeout: 30000, maxBuffer: 65536, shell: false, env: { PATH: '/usr/bin:/bin', LANG: 'C.UTF-8' } });
  if (result.status !== 0) throw new Error(`HOST_COMMAND_FAILED:${path.basename(command)}:${result.status}:${result.stderr.slice(0,400)}`);
  return result.stdout.trim();
};
const operate = (...args) => JSON.parse(run(process.execPath, [serviceCli, ...args]));
const digestTree = directory => {
  const rows = [];
  const visit = current => {
    for (const name of fs.readdirSync(current).sort()) {
      const file = path.join(current, name), stat = fs.lstatSync(file);
      if (stat.isDirectory()) visit(file);
      else { assert.ok(stat.isFile()); rows.push([path.relative(directory, file), crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')]); }
    }
  };
  visit(directory); return JSON.stringify(rows);
};
let observer = null, fixture = null;
const timings = [], started = performance.now(), usage = process.resourceUsage();
const record = { schema: 'continuum-mcp-canonical-host-check', version: 1, sourceCommit: run('git', ['rev-parse', 'HEAD']), node: process.version, environment: 'HOST', deviceVerified: false, modelInference: 'NOT_RUN', codingRepair: 'NOT_RUN', chatgptConnection: 'NOT_CONFIGURED', checks: [] };
const checkpoint = name => record.checks.push({ name, status: 'PASS' });

async function startObserver(config, tokenFile) {
  const child = spawn(process.execPath, [mcpCli, 'serve', '--config', config, '--token-file', tokenFile, '--port', '0'], { cwd: sourceRoot, stdio: ['ignore', 'pipe', 'pipe'], shell: false, env: { PATH: '/usr/bin:/bin', LANG: 'C.UTF-8' } });
  let output = '', errors = '';
  child.stderr.on('data', chunk => { errors += chunk.toString(); });
  const url = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('MCP_START_TIMEOUT')); }, 10000);
    child.once('exit', () => { clearTimeout(timer); reject(new Error('MCP_START_FAILED:' + errors.slice(0,160))); });
    child.stdout.on('data', chunk => {
      output += chunk.toString();
      if (output.length > 4096) { clearTimeout(timer); child.kill('SIGKILL'); reject(new Error('MCP_START_OUTPUT')); return; }
      if (!output.includes('\n')) return;
      try { const value = JSON.parse(output.split('\n')[0]); if (value.status === 'LISTENING') { clearTimeout(timer); resolve(value.url); } } catch {}
    });
  });
  return { url, child, stop: () => new Promise(resolve => {
    if (child.exitCode !== null) return resolve();
    const timer = setTimeout(() => child.kill('SIGKILL'), 3000); child.once('exit', () => { clearTimeout(timer); resolve(); }); child.kill('SIGTERM');
  }) };
}

try {
  assert.equal(run('git', ['status', '--porcelain', '--untracked-files=all']), '', 'Exact integration source must be clean');
  fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-real-mcp-')); fs.chmodSync(fixture, 0o700);
  const project = path.join(fixture, 'project'), profiles = path.join(fixture, 'profiles'), auth = path.join(fixture, 'auth'), state = path.join(fixture, 'state');
  for (const directory of [project, profiles, auth]) fs.mkdirSync(directory, { mode: 0o700 });
  fs.mkdirSync(path.join(project, 'src')); fs.writeFileSync(path.join(project, 'src/sum.mjs'), 'export const sum = (a,b) => a-b;\n');
  run('git', ['init', '--quiet'], project); run('git', ['add', '.'], project);
  run('git', ['-c', 'user.name=Continuum Host Check', '-c', 'user.email=host-check@invalid', 'commit', '--quiet', '-m', 'Controlled MCP observation fixture'], project);
  const classpath = fs.readFileSync(options['--classpath-file'], 'utf8').trim();
  assert.equal(JSON.parse(run('java', ['-cp', classpath, 'at.heinz.deviceai.chief.HandoffHostChecks', 'seed', profiles])).count, 20);
  const config = path.join(state, 'config.json'), tokenFile = path.join(auth, 'token');
  const initialized = operate('init', '--config', config, '--project', project, '--profiles', profiles, '--classpath', classpath, '--runtime-env', options['--runtime-env'], '--isolation-config', options['--isolation-config'], '--max-queue', '2');
  assert.equal(initialized.status, 'INITIALIZED');
  run(process.execPath, [mcpCli, 'init-token', '--token-file', tokenFile]);
  const token = fs.readFileSync(tokenFile, 'utf8').trim();
  let nextId = 1, session = null;
  const post = async (method, params = {}, notification = false) => {
    const before = performance.now();
    const response = await fetch(observer.url, { method: 'POST', headers: { authorization: `Bearer ${token}`, accept: 'application/json, text/event-stream', 'content-type': 'application/json', ...(session ? { 'mcp-session-id': session, 'mcp-protocol-version': '2025-06-18' } : {}) }, body: JSON.stringify({ jsonrpc: '2.0', ...(notification ? {} : { id: nextId++ }), method, params }) });
    const text = await response.text(); if (method === 'tools/call') timings.push(performance.now() - before);
    return { status: response.status, headers: response.headers, body: text ? JSON.parse(text) : null };
  };
  const initialize = async () => {
    session = null;
    const result = await post('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'canonical-real-host-check', version: '1' } });
    assert.equal(result.status, 200); session = result.headers.get('mcp-session-id'); assert.ok(session);
    assert.equal((await post('notifications/initialized', {}, true)).status, 202);
  };
  const tool = async (name, args = {}, success = true) => {
    const response = await post('tools/call', { name, arguments: args }); assert.equal(response.status, 200);
    assert.equal(response.body.result.isError, !success, `${name} result availability`);
    return response.body.result.structuredContent;
  };
  observer = await startObserver(config, tokenFile); await initialize(); checkpoint('separate authenticated CLI process initialized over HTTP');
  const beforeRead = digestTree(state), profilesBefore = digestTree(profiles);
  const status = await tool('get_status');
  assert.equal(status.state, 'STOPPED'); assert.equal(status.cloudModelCallsAllowed, false); assert.equal(status.modelLoaded, false); assert.equal(status.sourceCommit, record.sourceCommit);
  const agents = []; let cursor;
  do { const page = await tool('list_agents', { limit: 7, ...(cursor ? { cursor } : {}) }); agents.push(...page.items); cursor = page.nextCursor; } while (cursor);
  assert.equal(agents.length, 20); assert.equal(new Set(agents.map(a => a.agentId)).size, 20);
  for (const id of ['bot-forge', 'bot-lint', 'bot-echo']) assert.ok(agents.some(a => a.agentId === id && a.state === 'REGISTERED' && a.modelActive === false));
  assert.deepEqual((await tool('list_tasks')).items, []); await tool('get_evidence', { taskId: 'unavailable-task', evidenceId: 'report' }, false);
  assert.equal(digestTree(state), beforeRead); assert.equal(digestTree(profiles), profilesBefore); checkpoint('real Kotlin20 profile metadata and current canonical stopped status; read-only bytes unchanged');
  const task = { schema: 'continuum-coding-task', version: 1, id: 'mcp-observe-task', sourceCommit: run('git', ['rev-parse', 'HEAD'], project), kind: 'BUGFIX', goal: 'Controlled host observation fixture: fix sum under the existing local coding gate.', allowedPaths: ['src/sum.mjs'], acceptance: ['sum(2,3) returns 5 without changing tests'], chain: 'node', limits: { maxModelCalls: 1, maxContextChars: 2048 } };
  const taskFile = path.join(fixture, 'task.json'); fs.writeFileSync(taskFile, JSON.stringify(task), { mode: 0o600 });
  const review = operate('review', '--config', config, '--task', taskFile);
  const submitted = operate('submit', '--config', config, '--task', taskFile, '--approve', review.reviewSha256); assert.equal(submitted.executed, false);
  const tasks = await tool('list_tasks'); assert.equal(tasks.items.length, 1); assert.equal(tasks.items[0].taskId, task.id); assert.equal(tasks.items[0].grantCurrent, true);
  await tool('get_evidence', { taskId: task.id, evidenceId: 'result' }, false); checkpoint('actual owner inbox task visible without invented execution evidence');
  await observer.stop(); observer = await startObserver(config, tokenFile); await initialize();
  assert.equal((await tool('list_tasks')).items[0].taskId, task.id); assert.equal((await tool('list_agents', { limit: 50 })).items.length, 20); checkpoint('new observer process rereads persisted identities and canonical task');
  operate('cancel', '--config', config, '--task-id', task.id);
  const canceled = (await tool('list_tasks')).items[0]; assert.equal(canceled.grantCurrent, false); assert.equal(canceled.state, 'ABORTED');
  await tool('get_evidence', { taskId: task.id, evidenceId: 'report' }, false); checkpoint('actual canonical owner cancellation visible and evidence denied');
  await observer.stop(); observer = null;
  const sorted = timings.toSorted((a,b) => a-b), endUsage = process.resourceUsage();
  record.status = 'PASS'; record.elapsedMs = performance.now() - started;
  record.toolReads = timings.length; record.latencyMs = { p50: sorted[Math.floor(sorted.length * 0.5)], p95: sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))], samples: timings.length };
  record.parentProcessUsage = { userCpuMicros: endUsage.userCPUTime - usage.userCPUTime, systemCpuMicros: endUsage.systemCPUTime - usage.systemCPUTime, maxRssKiB: endUsage.maxRSS, scope: 'check controller only; child Java/Node resources excluded, not energy' };
} catch (error) {
  record.status = 'FAIL'; record.failure = String(error.message).slice(0,400); process.exitCode = 1;
} finally {
  if (observer) await observer.stop(); if (fixture) fs.rmSync(fixture, { force: true, recursive: true });
  fs.writeFileSync(options['--output'], JSON.stringify(record, null, 2) + '\n');
  process.stdout.write(JSON.stringify(record, null, 2) + '\n');
}
