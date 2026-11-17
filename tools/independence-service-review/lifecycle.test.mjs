import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {fixture, json, until, delay, record, localEnvironment} from './process-fixture.mjs';

function identity(pid) {
  try {
    const stat = fs.readFileSync('/proc/' + pid + '/stat', 'utf8');
    const fields = stat.slice(stat.lastIndexOf(')') + 2).trim().split(/\s+/);
    if (fields[0] === 'Z') return null;
    return {pid, startTicks: fields[19], bootId: fs.readFileSync('/proc/sys/kernel/random/boot_id', 'utf8').trim()};
  } catch (error) {if (error.code === 'ENOENT') return null; throw error;}
}
const alive = old => old && JSON.stringify(identity(old.pid)) === JSON.stringify(old);
const descriptorPath = f => path.join(path.dirname(f.config), 'service.json');
const descriptor = f => JSON.parse(fs.readFileSync(descriptorPath(f), 'utf8'));
const status = async f => json(await f.call('status'));
const taskList = async f => json(await f.call('tasks')).items;
async function reviewed(f, task) {
  const review = json(await f.call('review', ['--task', task.file]));
  assert.match(review.reviewSha256, /^[a-f0-9]{64}$/);
  return review;
}
async function submit(f, task, review) {
  return f.call('submit', ['--task', task.file, '--approve', (review ?? await reviewed(f, task)).reviewSha256]);
}
async function started(f) {
  json(await f.call('start'));
  await until(() => status(f), s => s.processVerified === true);
  const value = descriptor(f);
  assert.equal(alive(value.process), true, 'OS identity independently confirms the daemon');
  return value;
}

test('S01/S15: real idle daemon starts, stops and starts again without creating model work', {timeout: 60000}, async () => {
  const f = await fixture('start-stop', ['--max-cycles', '3']);
  try {
    assert.equal((await status(f)).processVerified, false);
    const first = await started(f);
    const live = await status(f);
    assert.equal(live.queued, 0);
    assert.equal(live.maxConcurrentInference, 1);
    assert.equal(live.cloudModelCallsAllowed, false);
    assert.equal(live.modelLoaded, false);
    await delay(150);
    assert.equal((await status(f)).activeTaskId, null);
    json(await f.call('stop'));
    await until(() => alive(first.process), value => value === false);
    assert.equal((await status(f)).processVerified, false);
    const second = await started(f);
    assert.notEqual(second.instanceId, first.instanceId);
    json(await f.call('stop'));
    await until(() => alive(second.process), value => value === false);
    const state = path.join(path.dirname(f.config), 'ccc1');
    assert.equal(fs.existsSync(path.join(state, 'coding-evidence')), false,
      'idle lifecycle cannot create a model/coding evidence tree');
  } finally {await f.close();}
});

test('S02: concurrent actual starts leave exactly one daemon ownership', {timeout: 40000}, async () => {
  const f = await fixture('duplicate-start');
  try {
    const attempts = await Promise.all([f.call('start'), f.call('start')]);
    assert.ok(attempts.some(a => a.code === 0), 'at least one start must establish service');
    const d = descriptor(f);
    assert.equal(alive(d.process), true);
    const statusAfter = await status(f);
    assert.equal(statusAfter.processVerified, true);
    const processes = fs.readdirSync('/proc').filter(x => /^\d+$/.test(x)).flatMap(x => {
      try {
        const args = fs.readFileSync('/proc/' + x + '/cmdline').toString().split('\0');
        return args.includes('run') && args.includes(f.config) && identity(Number(x)) ? [Number(x)] : [];
      } catch {return [];}
    });
    assert.deepEqual(processes, [d.process.pid], 'no hidden second foreground worker');
    record({kind: 'SINGLE_DAEMON_OBSERVED', processes, starts: attempts.map(a => a.code)});
  } finally {await f.close();}
});

test('S04/S11: SIGKILL of an idle daemon cannot leave live status and can restart safely', {timeout: 40000}, async () => {
  const f = await fixture('idle-crash');
  try {
    const d = await started(f);
    process.kill(d.process.pid, 'SIGKILL');
    await until(() => alive(d.process), value => value === false);
    const dead = await status(f);
    assert.equal(dead.processVerified, false);
    assert.notEqual(dead.state, 'RUNNING');
    assert.equal(dead.activeTaskId, null);
    const resumed = await started(f);
    assert.notEqual(resumed.instanceId, d.instanceId);
    assert.equal((await status(f)).unknown, 0);
  } finally {await f.close();}
});

test('S03: stale descriptor pointing at an unrelated live process never authorizes killing it', {timeout: 40000}, async () => {
  const f = await fixture('pid-reuse');
  let unrelated;
  try {
    const d = await started(f);
    json(await f.call('stop'));
    await until(() => alive(d.process), value => value === false);
    unrelated = spawn(process.execPath, ['-e', 'setInterval(()=>{},1000)'], {env: localEnvironment(), stdio: 'ignore'});
    await until(() => identity(unrelated.pid), Boolean);
    const unrelatedIdentity = identity(unrelated.pid);
    fs.writeFileSync(descriptorPath(f), JSON.stringify({...d, state: 'RUNNING', process: unrelatedIdentity}) + '\n', {mode: 0o600});
    const stopped = await f.call('stop');
    assert.equal(alive(unrelatedIdentity), true, 'control cannot signal an unrelated PID merely because metadata names it');
    record({kind: 'UNRELATED_PROCESS_SURVIVED', stopCode: stopped.code, identity: unrelatedIdentity});
    fs.writeFileSync(descriptorPath(f), JSON.stringify({...d, state: 'STOPPED'}) + '\n', {mode: 0o600});
  } finally {unrelated?.kill('SIGKILL'); await f.close();}
});

test('S05: last-slot admission is atomic across independent CLI processes', {timeout: 45000}, async () => {
  const f = await fixture('last-slot', ['--max-queue', '2', '--max-records', '4']);
  try {
    json(await submit(f, f.task('first-order')));
    const a = f.task('second-order'), b = f.task('third-order');
    const ra = await reviewed(f, a), rb = await reviewed(f, b);
    const raced = await Promise.all([submit(f, a, ra), submit(f, b, rb)]);
    assert.equal(raced.filter(r => r.code === 0).length, 1, 'exactly one last slot may be accepted');
    const tasks = await taskList(f);
    assert.equal(tasks.length, 2);
    assert.equal(new Set(tasks.map(t => t.taskId)).size, 2);
    assert.equal((await status(f)).queued, 2);
    const full = await submit(f, f.task('fourth-order'));
    assert.notEqual(full.code, 0);
    assert.equal((await taskList(f)).length, 2, 'rejected admission cannot leave hidden canonical work');
    for (const task of tasks) json(await f.call('cancel', ['--task-id', task.taskId]));
  } finally {await f.close();}
});

test('S06/S08: duplicate identity is durable; cancellation prevents resubmission and restart resurrection', {timeout: 45000}, async () => {
  const f = await fixture('idempotent-cancel');
  try {
    const task = f.task('one-order'), review = await reviewed(f, task);
    json(await submit(f, task, review));
    const canonicalId = (await taskList(f))[0].canonicalTaskId;
    json(await submit(f, task, review));
    assert.equal((await taskList(f)).length, 1);
    const changed = f.task('one-order', {goal: 'Changed task under the same identity.'});
    const changedReview = await reviewed(f, changed);
    assert.notEqual((await submit(f, changed, changedReview)).code, 0);
    f.task('one-order');
    json(await f.call('cancel', ['--task-id', task.data.id]));
    const cancelled = (await taskList(f))[0];
    assert.equal(cancelled.state, 'ABORTED');
    assert.equal(cancelled.canonicalTaskId, canonicalId);
    await submit(f, task, review); // A refusal or idempotent terminal view is valid.
    assert.equal((await taskList(f))[0].state, 'ABORTED');
    await started(f);
    await delay(100);
    const after = (await taskList(f))[0];
    assert.equal(after.state, 'ABORTED');
    assert.equal(after.canonicalTaskId, canonicalId);
    assert.equal((await status(f)).activeTaskId, null);
  } finally {await f.close();}
});

test('S07/S13: owner acceptance binds the exact task and source, not an arbitrary approve flag', {timeout: 40000}, async () => {
  const f = await fixture('admission-binding');
  try {
    const task = f.task('scoped-order'), review = await reviewed(f, task);
    assert.notEqual((await f.call('submit', ['--task', task.file, '--approve', '0'.repeat(64)])).code, 0);
    const mutation = f.task('scoped-order', {goal: 'A task modified after review.'});
    assert.notEqual((await submit(f, mutation, review)).code, 0);
    f.task('scoped-order');
    fs.writeFileSync(path.join(f.project, 'unreviewed.txt'), 'source changed');
    assert.notEqual((await submit(f, task, review)).code, 0);
    fs.unlinkSync(path.join(f.project, 'unreviewed.txt'));
    const forbidden = f.task('forbidden-order', {allowedPaths: ['.github/continuum/ccc1-worker.mjs']});
    assert.notEqual((await f.call('review', ['--task', forbidden.file])).code, 0);
    assert.equal((await taskList(f)).length, 0);
  } finally {await f.close();}
});

test('S14: corrupt delivery state is rejected rather than reset to a fresh accepting queue', {timeout: 35000}, async () => {
  const f = await fixture('corrupt-inbox');
  const ledger = path.join(path.dirname(f.config), 'deliveries.json');
  const original = fs.readFileSync(ledger);
  try {
    const task = f.task('corruption-order'), review = await reviewed(f, task);
    fs.writeFileSync(ledger, '{"schema":"continuum-owner-inbox",', {mode: 0o600});
    assert.notEqual((await f.call('status')).code, 0);
    assert.notEqual((await submit(f, task, review)).code, 0);
    assert.equal(fs.readFileSync(ledger, 'utf8'), '{"schema":"continuum-owner-inbox",');
  } finally {fs.writeFileSync(ledger, original, {mode: 0o600}); await f.close();}
});
