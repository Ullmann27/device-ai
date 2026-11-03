import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fixture, json, until, delay, record} from './process-fixture.mjs';

const state = f => path.dirname(f.config);
const deliveries = f => JSON.parse(fs.readFileSync(path.join(state(f), 'deliveries.json'), 'utf8')).items;
const descriptor = f => JSON.parse(fs.readFileSync(path.join(state(f), 'service.json'), 'utf8'));
function observe(pid) {
  try {
    const raw = fs.readFileSync('/proc/' + pid + '/stat', 'utf8');
    const fields = raw.slice(raw.lastIndexOf(')') + 2).trim().split(/\s+/);
    return {pid, state: fields[0], startTicks: fields[19], bootId: fs.readFileSync('/proc/sys/kernel/random/boot_id', 'utf8').trim()};
  } catch (error) {if (error.code === 'ENOENT') return null; throw error;}
}
const alive = old => {
  const current = old && observe(old.pid);
  return current && current.state !== 'Z' && current.startTicks === old.startTicks && current.bootId === old.bootId;
};
function signal(old, name) {
  if (alive(old)) process.kill(old.pid, name);
}
const publicResult = f => path.join(state(f), 'public-evidence');

async function dispatchWithoutExecutableRuntime(f, id) {
  // The service verifies this private copy against the genuine approved hash at
  // startup. Removing ONLY that copy before submitting makes generation
  // impossible even if the process observer loses a scheduling race.
  json(await f.call('start'));
  const daemon = descriptor(f);
  assert.equal(alive(daemon.process), true);
  fs.unlinkSync(f.runtimeCopy);
  assert.equal(fs.existsSync(f.runtimeCopy), false);
  const task = f.task(id);
  const review = json(await f.call('review', ['--task', task.file]));
  json(await f.call('submit', ['--task', task.file, '--approve', review.reviewSha256]));
  const active = await until(() => deliveries(f).find(d => d.id === id),
    d => d?.delivery === 'DISPATCHED' && d.workerProcess && alive(d.workerProcess), 6000);
  signal(active.workerProcess, 'SIGSTOP');
  await until(() => observe(active.workerProcess.pid), value => value?.state === 'T');
  assert.equal(fs.existsSync(f.runtimeCopy), false, 'native execution is unavailable before worker GO');
  assert.equal(fs.readdirSync(publicResult(f)).length, 0);
  record({kind: 'REAL_WORKER_FROZEN_BEFORE_UNAVAILABLE_RUNTIME', taskId: id,
    worker: active.workerProcess, daemon: daemon.process, nativeExecutableExists: false});
  return {daemon, active};
}

function restoreRuntime(f) {
  fs.copyFileSync(f.runtimeOriginal, f.runtimeCopy, fs.constants.COPYFILE_EXCL);
  fs.chmodSync(f.runtimeCopy, 0o500);
}

test('S09: cancellation of a real dispatched child reaches the canonical task and cannot become late success', {timeout: 45000}, async () => {
  const f = await fixture('active-cancel', ['--max-cycles', '4'], {isolatedRuntimeCopy: true});
  let active;
  try {
    ({active} = await dispatchWithoutExecutableRuntime(f, 'active-cancel-order'));
    const cancelled = json(await f.call('cancel', ['--task-id', active.id]));
    assert.equal(cancelled.delivery, 'CANCEL_REQUESTED');
    assert.equal(json(await f.call('tasks')).items[0].state, 'ABORTED');
    signal(active.workerProcess, 'SIGCONT');
    await until(() => alive(active.workerProcess), value => !value);
    await until(async () => json(await f.call('status')), value => value.activeTaskId === null);
    const after = json(await f.call('tasks')).items[0];
    assert.equal(after.state, 'ABORTED');
    assert.equal(after.delivery, 'RETIRED');
    assert.equal(fs.readdirSync(publicResult(f)).length, 0, 'a failed/cancelled child cannot publish verified evidence');
    const daemon = descriptor(f);
    json(await f.call('stop'));
    await until(() => alive(daemon.process), value => !value);
    restoreRuntime(f);
    json(await f.call('start'));
    await delay(100);
    assert.equal(json(await f.call('tasks')).items[0].state, 'ABORTED');
    assert.equal(json(await f.call('status')).activeTaskId, null);
  } finally {
    signal(active?.workerProcess, 'SIGKILL');
    await f.close();
  }
});

test('S10: actual daemon crash after dispatch preserves UNKNOWN and never restarts the claimed work', {timeout: 45000}, async () => {
  const f = await fixture('active-crash', ['--max-cycles', '4'], {isolatedRuntimeCopy: true});
  let active;
  try {
    const running = await dispatchWithoutExecutableRuntime(f, 'active-crash-order');
    active = running.active;
    signal(running.daemon.process, 'SIGKILL');
    await until(() => alive(running.daemon.process), value => !value);
    const orphan = json(await f.call('status'));
    assert.equal(orphan.processVerified, false);
    assert.equal(orphan.state, 'ORPHAN_WORKER');
    assert.equal(orphan.unknown, 1);
    // This is the test operator cleaning up its own frozen child, never an
    // automatic replay or a PID inferred from untrusted presentation metadata.
    signal(active.workerProcess, 'SIGKILL');
    await until(() => alive(active.workerProcess), value => !value);
    restoreRuntime(f);
    json(await f.call('start'));
    await delay(200);
    const after = json(await f.call('tasks')).items[0];
    assert.equal(after.taskId, active.id);
    assert.equal(after.canonicalTaskId, active.ccc1TaskId);
    assert.equal(after.delivery, 'UNKNOWN');
    assert.equal(json(await f.call('status')).activeTaskId, null);
    assert.equal(fs.readdirSync(publicResult(f)).length, 0);
    assert.equal(deliveries(f)[0].workerProcess.pid, active.workerProcess.pid,
      'restart cannot replace the crashed attempt with a new worker identity');
  } finally {
    signal(active?.workerProcess, 'SIGKILL');
    await f.close();
  }
});

test('S02 supplement: per-UID admission excludes a second different project and state directory until verified stop', {timeout: 45000}, async () => {
  const first = await fixture('global-first');
  const second = await fixture('global-second');
  try {
    assert.notEqual(first.project, second.project);
    assert.notEqual(first.config, second.config);
    json(await first.call('start'));
    const owner = descriptor(first);
    assert.equal(alive(owner.process), true);
    const rejected = await second.call('start');
    assert.notEqual(rejected.code, 0, 'another state directory cannot multiply active local admission');
    assert.equal(json(await second.call('status')).processVerified, false);
    assert.equal(alive(owner.process), true, 'losing contender cannot stop the active service');
    json(await first.call('stop'));
    await until(() => alive(owner.process), value => !value);
    json(await second.call('start'));
    assert.equal(json(await second.call('status')).processVerified, true);
    record({kind: 'CROSS_CONFIG_SINGLE_ADMISSION', firstState: state(first), secondState: state(second),
      secondRejectedWhileFirstAlive: true, secondStartedAfterVerifiedStop: true});
  } finally {await second.close(); await first.close();}
});

test('S10 supplement: per-UID admission retains a live orphan across different project and state directories', {timeout: 45000}, async () => {
  const first = await fixture('global-orphan', ['--max-cycles', '4'], {isolatedRuntimeCopy: true});
  const second = await fixture('global-orphan-contender');
  let active;
  try {
    const running = await dispatchWithoutExecutableRuntime(first, 'global-orphan-order');
    active = running.active;
    signal(running.daemon.process, 'SIGKILL');
    await until(() => alive(running.daemon.process), value => !value);
    assert.equal(json(await first.call('status')).state, 'ORPHAN_WORKER');
    const rejected = await second.call('start');
    assert.notEqual(rejected.code, 0, 'dead parent does not free admission while its worker remains alive');
    assert.equal(alive(active.workerProcess), true, 'contender must not kill an orphan to claim success');
    assert.equal(observe(active.workerProcess.pid).state, 'T');
    assert.equal(json(await second.call('status')).processVerified, false);
    signal(active.workerProcess, 'SIGKILL');
    await until(() => alive(active.workerProcess), value => !value);
    json(await second.call('start'));
    assert.equal(json(await second.call('status')).processVerified, true);
    assert.equal(fs.readdirSync(publicResult(first)).length, 0);
    record({kind: 'CROSS_CONFIG_ORPHAN_ADMISSION', orphan: active.workerProcess,
      secondRejectedWhileOrphanAlive: true, secondStartedAfterVerifiedChildTeardown: true});
  } finally {
    signal(active?.workerProcess, 'SIGKILL');
    await second.close(); await first.close();
  }
});
