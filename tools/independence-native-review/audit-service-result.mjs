// Fresh-process independent read audit. Does not submit, execute, repair, or invoke a model.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
const [source, expectedSha, configPath] = process.argv.slice(2);
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const moduleAt = file => import(pathToFileURL(path.join(source, file)));
const git = (root, ...args) => {
  const r = spawnSync('git', ['-C', root, ...args], { encoding: 'utf8', shell: false });
  assert.equal(r.status, 0); return r.stdout.trim();
};
assert.equal(git(source, 'rev-parse', 'HEAD'), expectedSha);
assert.equal(git(source, 'status', '--porcelain'), '');
const { loadCodingServiceConfig, readDeliveries } = await moduleAt('.github/continuum/coding-service-store.mjs');
const { DurableTaskJournal } = await moduleAt('.github/continuum/ccc1-journal.mjs');
const { taskState } = await moduleAt('.github/continuum/ccc1-orchestrator.mjs');
const { taskFingerprint } = await moduleAt('.github/continuum/coding-task.mjs');
const { canonical, sha256Hex } = await moduleAt('.github/continuum/development-backlog.mjs');
const { builderStateOf, jobIdFor } = await moduleAt('.github/continuum/ccc1-recovery.mjs');
const { readReceipt, jobTaskId } = await moduleAt('scripts/continuum-mobile-coding.mjs');
const { verifyRepairReceipt } = await moduleAt('.github/continuum/ccc1-verifier.mjs');
const { validWorkspaceEvidence } = await moduleAt('.github/continuum/isolated-executor.mjs');
const { profileStoreReader } = await moduleAt('tools/genesis-workspaces/adapter-bridge.mjs');
const config = loadCodingServiceConfig(configPath);
assert.equal(config.sourceCommit, expectedSha);
const state = path.join(config.stateDir, 'ccc1');
const grant = JSON.parse(fs.readFileSync(path.join(state, 'coding-grants/independence-s01.json')));
const task = grant.task, delivery = readDeliveries(config).find(item => item.id === task.id);
assert.equal(task.id, 'independence-s01');
assert.equal(task.limits.maxModelCalls, 1);
assert.equal(git(config.project, 'rev-parse', 'HEAD'), task.sourceCommit);
assert.equal(git(config.project, 'status', '--porcelain'), '');
assert.equal(grant.fingerprint, taskFingerprint(task));
assert.equal(delivery.fingerprint, grant.fingerprint);
assert.equal(delivery.ccc1TaskId, grant.ccc1TaskId);
const key = fs.readFileSync(path.join(state, 'ccc1-runtime.key')); assert.equal(key.length, 32);
const entries = new DurableTaskJournal(path.join(state, 'tasks', grant.ccc1TaskId + '.jsonl'), key).entries();
const status = taskState(entries), goal = entries[0];
assert.equal(goal.baseCommitSha, task.sourceCommit);
assert.equal(goal.taskId, grant.ccc1TaskId);
assert.ok(goal.permissions.includes('node-repair:node'));
const currentGrant = goal.deadline > Date.now() && !entries.some(entry => entry.kind === 'ABORT');
const snapshot = JSON.parse(fs.readFileSync(path.join(config.stateDir, 'public-evidence', task.id + '.json')));
const { metricsMac, ...metrics } = snapshot;
assert.equal(metricsMac, createHmac('sha256', key).update(canonical(metrics)).digest('hex'));
assert.equal(snapshot.journalHead, entries.at(-1).hash);
assert.equal(snapshot.taskFingerprint, taskFingerprint(task));
assert.equal(snapshot.sourceCommit, task.sourceCommit);
assert.equal(snapshot.ccc1TaskId, grant.ccc1TaskId);
const receipts = entries.filter(entry => entry.kind === 'RECEIPT').map(entry => {
  const request = entries.find(item => item.messageId === entry.payload.request);
  assert.equal(request.kind, 'TEST_REQUEST');
  assert.equal(jobIdFor(grant.ccc1TaskId, request.messageId), entry.payload.job);
  const receipt = readReceipt(builderStateOf(state), entry.payload.job);
  assert.ok(receipt);
  assert.equal(sha256Hex(JSON.stringify(receipt)), entry.payload.receiptSha256);
  const verdict = verifyRepairReceipt(receipt, { job: entry.payload.job, commit: task.sourceCommit, chain: task.chain });
  if (receipt.patch) assert.equal(digest(fs.readFileSync(receipt.patch.path)), receipt.patch.sha256);
  return { job: entry.payload.job, request: request.messageId, receiptSha256: entry.payload.receiptSha256,
    verdict: verdict.verdict, reasons: verdict.reasons, patch: receipt.patch, commands: receipt.commands };
});
const observations = [], modelObservations = new Map();
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [name, item] of Object.entries(value)) {
    if (name === 'isolation' && item?.schema === 'continuum-isolation-v1') {
      assert.ok(validWorkspaceEvidence(item, item.profile));
      assert.equal(item.workspaceBinding.taskId, grant.ccc1TaskId);
      assert.equal(item.workspaceBinding.sourceCommit, task.sourceCommit);
      assert.ok(receipts.some(receipt => receipt.job === item.workspaceBinding.jobId));
      assert.ok(['bot-forge', 'bot-lint'].includes(item.workspaceBinding.workerId));
      observations.push(item);
    } else if (name === 'modelObservation' && item) {
      assert.equal(item.sourceCommit, task.sourceCommit);
      assert.equal(item.verifiedModelExecution, true);
      assert.equal(item.modelSha256, config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_MODEL_SHA256);
      assert.equal(item.runtimeSha256, config.runtimeEnv.CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256);
      assert.equal(item.exitCode, 0);
      assert.equal(item.isolation, 'BWRAP_UNSHARE_ALL');
      assert.ok(receipts.some(receipt => jobTaskId(receipt.job) === item.taskId));
      const modelTask = { ...task, id: item.taskId };
      assert.equal(item.taskFingerprint, taskFingerprint(modelTask));
      modelObservations.set(item.executionId, item);
    } else inspect(item);
  }
}
function walk(directory) {
  if (!fs.existsSync(directory)) return;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(file);
    else if (entry.name === 'report.json') inspect(JSON.parse(fs.readFileSync(file)));
  }
}
walk(path.join(state, 'teamleader/reports'));
assert.ok(modelObservations.size <= 1);
assert.ok(snapshot.modelExecution.verifiedCalls <= 1);
const reportFile = path.join(state, 'coding-evidence', task.id, snapshot.reportFile);
const bytes = fs.readFileSync(reportFile); assert.equal(digest(bytes), snapshot.reportSha256);
const echo = JSON.parse(bytes);
assert.equal(echo.reporterId, 'bot-echo'); assert.equal(echo.canonicalTaskId, grant.ccc1TaskId);
assert.equal(echo.taskFingerprint, taskFingerprint(task)); assert.equal(echo.sourceCommit, task.sourceCommit);
assert.equal(echo.journalHead, entries.at(-1).hash); assert.equal(echo.status, status.state);
assert.equal(echo.modelCalls, 0); assert.equal(echo.independentVerifier, false);
for (const receipt of echo.receipts) {
  assert.equal(receipt.verdict, receipts.find(item => item.job === receipt.jobId)?.verdict);
  assert.equal(receipt.artifactMatched, true);
}
const store = profileStoreReader(config.profileDirectory, { classpath: config.profileClasspath });
const profiles = ['bot-forge', 'bot-lint', 'bot-echo'].map(id => {
  const profile = store.get(id); assert.equal(profile.agentId, id);
  assert.equal(profile.workspaceId, 'workspace-' + id);
  assert.equal(profile.resourceBudget.maxModelCalls, 1);
  return profile;
});
const confirmed = receipts.some(receipt => receipt.verdict === 'CONFIRMED');
let recall = { status: 'NOT_RUN', reason: 'No independently confirmed repair' };
if (confirmed) {
  assert.equal(status.state, 'COMPLETED'); assert.equal(modelObservations.size, 1);
  assert.ok(observations.some(item => item.workspaceBinding.workerId === 'bot-lint'));
  assert.equal(snapshot.technicalExperienceStatus, 'RECORDED_TECHNICAL_REVIEW_ONLY');
  assert.equal(currentGrant, true);
  const { recallTechnicalExperiences } = await moduleAt('.github/continuum/ccc1-technical-recall.mjs');
  const { repositoryOf } = await moduleAt('.github/continuum/ccc1-worker.mjs');
  recall = recallTechnicalExperiences({ stateDir: state, task, project: config.project,
    repoFullName: repositoryOf(config.project), key });
  assert.equal(recall.status, 'OK'); assert.ok(recall.items.length > 0);
}
console.log(JSON.stringify({ schema: 'independence-s01-independent-audit-v1', sourceSha: expectedSha,
  taskFingerprint: taskFingerprint(task), canonicalTaskId: grant.ccc1TaskId,
  state: status.state, confirmedCanonicalRepair: confirmed, journalVerified: true,
  journalEntries: entries.length, currentGrant, receipts, observations,
  modelObservations: [...modelObservations.values()], profiles,
  echo: { ...echo, artifactSha256: digest(bytes) }, freshProcessTechnicalRecall: recall,
  sourceUnchanged: true, extraIndependentTests: 'SEPARATE_GATE', deviceVerified: false }, null, 2));
