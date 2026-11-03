// Read-only failure audit after the finite native request. No replay or model call.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';
const source = '/workspace/independence-final-source';
const expected = '8fd58d00c53c6c2257a102922243f413afb6bac1';
const base = '/workspace/independence-s01-experiment', state = path.join(base, 'service/ccc1');
const read = file => JSON.parse(fs.readFileSync(file));
const moduleAt = file => import(pathToFileURL(path.join(source, file)));
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const git = (root, ...args) => { const r = spawnSync('git', ['-C', root, ...args], { encoding: 'utf8' }); assert.equal(r.status, 0); return r.stdout.trim(); };
assert.equal(git(source, 'rev-parse', 'HEAD'), expected); assert.equal(git(source, 'status', '--porcelain'), '');
const { DurableTaskJournal } = await moduleAt('.github/continuum/ccc1-journal.mjs');
const { taskState } = await moduleAt('.github/continuum/ccc1-orchestrator.mjs');
const { taskFingerprint } = await moduleAt('.github/continuum/coding-task.mjs');
const { builderStateOf, jobIdFor } = await moduleAt('.github/continuum/ccc1-recovery.mjs');
const { readReceipt, jobTaskId } = await moduleAt('scripts/continuum-mobile-coding.mjs');
const { verifyRepairReceipt } = await moduleAt('.github/continuum/ccc1-verifier.mjs');
const { validWorkspaceEvidence } = await moduleAt('.github/continuum/isolated-executor.mjs');
const { recallTechnicalExperiences } = await moduleAt('.github/continuum/ccc1-technical-recall.mjs');
const { repositoryOf } = await moduleAt('.github/continuum/ccc1-worker.mjs');
const config = read(path.join(base, 'service/config.json'));
const grant = read(path.join(state, 'coding-grants/independence-s01.json')), task = grant.task;
assert.equal(task.id, 'independence-s01'); assert.equal(task.limits.maxModelCalls, 1);
assert.equal(config.sourceCommit, expected); assert.equal(taskFingerprint(task), grant.fingerprint);
assert.equal(git(config.project, 'rev-parse', 'HEAD'), task.sourceCommit); assert.equal(git(config.project, 'status', '--porcelain'), '');
const key = fs.readFileSync(path.join(state, 'ccc1-runtime.key')); assert.equal(key.length, 32);
const entries = new DurableTaskJournal(path.join(state, 'tasks', grant.ccc1TaskId + '.jsonl'), key).entries();
assert.equal(entries[0].taskId, grant.ccc1TaskId); assert.equal(entries[0].baseCommitSha, task.sourceCommit);
assert.ok(entries[0].permissions.includes('node-repair:node'));
assert.equal(taskState(entries).state, 'ABSTAINED');
const resultEntries = entries.filter(e => e.kind === 'RECEIPT'); assert.equal(resultEntries.length, 1);
const entry = resultEntries[0], request = entries.find(e => e.messageId === entry.payload.request);
assert.equal(request.kind, 'TEST_REQUEST'); assert.equal(jobIdFor(grant.ccc1TaskId, request.messageId), entry.payload.job);
const receipt = readReceipt(builderStateOf(state), entry.payload.job);
assert.equal(hash(JSON.stringify(receipt)), entry.payload.receiptSha256);
const verdict = verifyRepairReceipt(receipt, { job: entry.payload.job, commit: task.sourceCommit, chain: 'node' });
assert.equal(verdict.verdict, 'REFUTED'); assert.equal(receipt.patch, null);
assert.deepEqual(receipt.commands.map(r => [r.phase, r.exitCode, r.executed, r.failed]), [['baseline', 1, 6, 4]]);
const requestDir = path.join(state, 'coding-evidence', task.id, entry.payload.job);
assert.deepEqual(fs.readdirSync(requestDir), ['request-1.json']);
const modelRequest = read(path.join(requestDir, 'request-1.json'));
assert.equal(modelRequest.request, 1); assert.equal(modelRequest.taskId, jobTaskId(entry.payload.job));
assert.equal(modelRequest.taskFingerprint, taskFingerprint({ ...task, id: modelRequest.taskId }));
assert.equal(modelRequest.sourceCommit, task.sourceCommit); assert.equal(modelRequest.verifiedModelExecution, false);
assert.equal(modelRequest.status, 'FAILED'); assert.equal(modelRequest.failureKind, 'CHILD_EXIT_NONZERO');
assert.equal(modelRequest.exitCode, 1); assert.equal(modelRequest.responseBytes, 0);
const observations = [];
function inspect(value) { if (!value || typeof value !== 'object') return;
  for (const [name, item] of Object.entries(value)) {
    if (name === 'isolation' && item?.schema === 'continuum-isolation-v1') {
      assert.ok(validWorkspaceEvidence(item, item.profile));
      assert.equal(item.workspaceBinding.workerId, 'bot-forge');
      assert.equal(item.workspaceBinding.taskId, grant.ccc1TaskId);
      assert.equal(item.workspaceBinding.jobId, entry.payload.job);
      assert.equal(item.workspaceBinding.sourceCommit, task.sourceCommit); observations.push(item);
    } else inspect(item);
  }
}
function walk(dir) { for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
  const file = path.join(dir, item.name); if (item.isDirectory()) walk(file);
  else if (item.name === 'report.json') inspect(read(file));
} }
walk(path.join(state, 'teamleader/reports')); assert.equal(observations.length, 1);
const recalls = recallTechnicalExperiences({ stateDir: state, task, project: config.project,
  repoFullName: repositoryOf(config.project), key });
assert.equal(recalls.items.length, 0);
assert.equal(fs.existsSync(path.join(base, 'service/public-evidence/independence-s01.json')), false);
assert.equal(fs.existsSync(path.join(state, 'experience.jsonl')), false);
const sampled = read(path.join(base, 'native-resources.json')); assert.equal(sampled.processes.length, 1);
const material = read('/workspace/independence-native-review/docs/research/omega/independence/native-review/materialization.json');
assert.equal(hash(fs.readFileSync(path.join(config.project, 'lib/subject.mjs'))), material.sourceSha256);
assert.equal(hash(fs.readFileSync(path.join(config.project, '.github/continuum/owner.test.mjs'))), material.ownerTestSha256);
console.log(JSON.stringify({ schema: 'independence-s01-failure-audit-v1', source: expected,
  taskId: task.id, canonicalTaskId: grant.ccc1TaskId, taskFingerprint: taskFingerprint(task),
  canonicalState: taskState(entries).state, journalVerified: true, journalEntries: entries.length,
  verdict: verdict.verdict, receiptSha256: entry.payload.receiptSha256, modelRequest,
  reservedCalls: 1, admittedGenuineCalls: 0, sampledNativeProcesses: 1,
  observedRoles: ['bot-forge'], observations, lint: 'NOT_RUN', echo: 'NOT_RUN',
  technicalExperience: 'NOT_RECORDED', freshProcessRecall: recalls,
  extraIndependentTests: 'NOT_RUN_NO_CANDIDATE', sourceAndTestsUnchanged: true,
  failureCause: 'CHILD_EXIT_NONZERO; exact child signal/code unavailable in retained typed trace',
  deviceVerified: false, retryAllowed: false }, null, 2));
