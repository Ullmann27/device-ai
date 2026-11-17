/** Independent process observer. All repositories, profiles and tasks are synthetic.
 * No model response, executor receipt or successful task result is injected. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn, spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';

export const reviewerRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const candidateRoot = path.resolve(process.env.SERVICE_REVIEW_CANDIDATE ?? reviewerRoot);
export const cli = path.join(candidateRoot, 'scripts/continuum-coding-service.mjs');
const evidence = process.env.SERVICE_REVIEW_EVIDENCE;
const transcript = [];
export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
export const digest = value => createHash('sha256').update(value).digest('hex');

export function record(event) {
  const item = {at: new Date().toISOString(), ...event};
  transcript.push(item);
  if (evidence) {
    fs.mkdirSync(evidence, {recursive: true});
    fs.appendFileSync(path.join(evidence, 'process-observations.jsonl'), JSON.stringify(item) + '\n');
  }
}

/** Deliberately excludes all cloud/model/connector credentials and inherited
 * provider-selection environment. File/config arguments carry approved local paths. */
export function localEnvironment() {
  return Object.fromEntries(['PATH', 'LANG', 'LC_ALL', 'TMPDIR', 'TZ']
    .filter(key => process.env[key] !== undefined).map(key => [key, process.env[key]]));
}

export async function command(args, {timeoutMs = 20000, cwd = candidateRoot, env = localEnvironment()} = {}) {
  const started = performance.now();
  const child = spawn(process.execPath, [cli, ...args], {cwd, env, stdio: ['ignore', 'pipe', 'pipe']});
  let stdout = '', stderr = '', limited = false, timedOut = false;
  const append = (name, chunk) => {
    if (stdout.length + stderr.length + chunk.length > 1024 * 1024) {
      limited = true; child.kill('SIGKILL'); return;
    }
    if (name === 'stdout') stdout += chunk; else stderr += chunk;
  };
  child.stdout.on('data', chunk => append('stdout', chunk.toString()));
  child.stderr.on('data', chunk => append('stderr', chunk.toString()));
  const timer = setTimeout(() => {timedOut = true; child.kill('SIGKILL');}, timeoutMs);
  let result;
  try {
    result = await new Promise((resolve, reject) => {
      child.once('error', reject);
      child.once('close', (code, signal) => resolve({code, signal}));
    });
  } finally {clearTimeout(timer);}
  const value = {...result, stdout, stderr, timedOut, limited, pid: child.pid,
    elapsedMs: Math.round((performance.now() - started) * 1000) / 1000};
  record({kind: 'CLI_PROCESS', args, ...value});
  return value;
}

export function json(result) {
  if (result.code !== 0 || result.timedOut || result.limited)
    throw Error('CLI failed: ' + JSON.stringify(result));
  const lines = result.stdout.trim().split('\n').filter(Boolean);
  if (!lines.length) throw Error('CLI returned no structured output');
  return JSON.parse(lines.at(-1));
}

export function git(project, args) {
  const result = spawnSync('git', args, {cwd: project, encoding: 'utf8', timeout: 15000, env: localEnvironment()});
  if (result.status !== 0) throw Error('Fixture git failed: ' + result.stderr);
  return result.stdout.trim();
}

export async function until(observe, accept, timeoutMs = 6000) {
  const deadline = performance.now() + timeoutMs;
  let last;
  do {
    last = await observe();
    if (accept(last)) return last;
    await delay(40);
  } while (performance.now() < deadline);
  throw Error('Observation deadline exceeded: ' + JSON.stringify(last));
}

export async function fixture(name, initExtra = [], {isolatedRuntimeCopy = false} = {}) {
  for (const key of ['SERVICE_REVIEW_CLASSPATH_FILE', 'SERVICE_REVIEW_RUNTIME_ENV', 'SERVICE_REVIEW_ISOLATION'])
    if (!process.env[key]) throw Error(key + ' required: absent prerequisites are not a test PASS');
  const top = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-service-review-'));
  fs.chmodSync(top, 0o700);
  const project = path.join(top, 'project');
  const profiles = path.join(top, 'profiles');
  const config = path.join(top, 'service', 'config.json');
  let runtimeEnvPath = process.env.SERVICE_REVIEW_RUNTIME_ENV;
  let runtimeCopy = null, runtimeOriginal = null;
  if (isolatedRuntimeCopy) {
    const runtime = JSON.parse(fs.readFileSync(runtimeEnvPath, 'utf8'));
    runtimeOriginal = runtime.CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH;
    fs.mkdirSync(path.join(top, 'private-runtime'), {mode: 0o700});
    runtimeCopy = path.join(top, 'private-runtime', 'llama-completion');
    fs.copyFileSync(runtimeOriginal, runtimeCopy, fs.constants.COPYFILE_EXCL);
    fs.chmodSync(runtimeCopy, 0o500);
    runtime.CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH = runtimeCopy;
    runtimeEnvPath = path.join(top, 'runtime-env.json');
    fs.writeFileSync(runtimeEnvPath, JSON.stringify(runtime) + '\n', {mode: 0o600});
    record({kind: 'PRIVATE_APPROVED_RUNTIME_COPY', name,
      runtimeSha256: digest(fs.readFileSync(runtimeCopy)), path: runtimeCopy});
  }
  // The older compiler artifact ends in ':'; remove empty Java classpath entries
  // so the working directory cannot silently become an executable class source.
  const cp = fs.readFileSync(process.env.SERVICE_REVIEW_CLASSPATH_FILE, 'utf8').trim()
    .split(path.delimiter).filter(Boolean).join(path.delimiter);
  fs.mkdirSync(path.join(project, '.github/continuum'), {recursive: true});
  fs.mkdirSync(path.join(project, 'lib'));
  fs.writeFileSync(path.join(project, 'lib/count.mjs'), 'export const count = values => values.length;\n');
  fs.writeFileSync(path.join(project, '.github/continuum/count.test.mjs'),
    "import test from 'node:test';import assert from 'node:assert/strict';import {count} from '../../lib/count.mjs';test('fixture already correct',()=>assert.equal(count([1,2]),2));\n");
  for (const name of ['ccc1-cli.mjs', 'ccc1-worker.mjs', 'ccc1-path-policy.mjs', 'local-teamleader-loop.mjs'])
    fs.copyFileSync(path.join(candidateRoot, '.github/continuum', name), path.join(project, '.github/continuum', name));
  git(project, ['init', '-q', '-b', 'review/service']);
  git(project, ['add', '.']);
  git(project, ['-c', 'user.name=Service reviewer fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'Synthetic lifecycle fixture']);
  const sourceCommit = git(project, ['rev-parse', 'HEAD']);
  const seeded = spawnSync('java', ['-Xmx128m', '-cp', cp,
    'at.heinz.deviceai.chief.HandoffHostChecks', 'seed', profiles],
    {encoding: 'utf8', timeout: 15000, env: localEnvironment()});
  record({kind: 'REAL_PROFILE_STORE_SEED', name, code: seeded.status,
    stdout: seeded.stdout, stderr: seeded.stderr, classpathSha256: digest(cp)});
  if (seeded.status !== 0) throw Error('Actual profile-store seeding failed: ' + seeded.stderr);
  const initialized = await command(['init', '--config', config, '--project', project,
    '--profiles', profiles, '--classpath', cp,
    '--runtime-env', runtimeEnvPath,
    '--isolation-config', process.env.SERVICE_REVIEW_ISOLATION, ...initExtra]);
  json(initialized);
  const call = (verb, args = [], options) => command([verb, '--config', config, ...args], options);
  return {name, top, project, profiles, config, sourceCommit, call, runtimeCopy, runtimeOriginal,
    task(id, overrides = {}) {
      const data = {schema: 'continuum-coding-task', version: 1, id, sourceCommit,
        kind: 'BUGFIX', goal: 'Check count semantics within the approved file.',
        allowedPaths: ['lib/count.mjs'], acceptance: ['An array with two values has count two.'],
        chain: 'node', limits: {maxModelCalls: 1, maxContextChars: 4096}, ...overrides};
      const file = path.join(top, id + '.json');
      fs.writeFileSync(file, JSON.stringify(data) + '\n', {mode: 0o600});
      return {data, file};
    },
    async close() {
      const stopped = await call('stop').catch(error => {
        record({kind: 'TEARDOWN_STOP_ERROR', message: String(error)}); return null;
      });
      let shutdownVerified = false;
      if (stopped?.code === 0) {
        try {
          await until(async () => json(await call('status')),
            s => s.processVerified === false && s.state === 'STOPPED', 6000);
          shutdownVerified = true;
        } catch (error) {record({kind: 'TEARDOWN_UNKNOWN', message: String(error)});}
      }
      // Keep evidence paths when explicitly requested for forensic inspection.
      // A failed stop must not delete files from under a possibly live daemon.
      if (shutdownVerified && process.env.SERVICE_REVIEW_KEEP_FIXTURES !== '1')
        fs.rmSync(top, {recursive: true, force: true});
      else record({kind: 'RETAINED_FIXTURE', path: top, stopCode: stopped?.code ?? null, shutdownVerified});
    }};
}
