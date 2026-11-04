// Independent contract probe against unchanged CCC1 source. All task receipts
// are synthetic fixture data. No model, repair, training, or device is executed.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {performance} from 'node:perf_hooks';

const child = process.argv[2] === '--child';
const source = path.resolve(process.argv[child ? 3 : 2]);
const moduleOf = name => import(pathToFileURL(path.join(source, '.github/continuum', name)).href);
const {ExperienceLog, experienceOf} = await moduleOf('ccc1-experience.mjs');
const {DurableTaskJournal, runtimeKey} = await moduleOf('ccc1-journal.mjs');
const {envelope, canonical, sha256} = await moduleOf('ccc1-envelope.mjs');
const {taskFingerprint} = await moduleOf('coding-task.mjs');
const {recallTechnicalExperiences, recordTechnicalExperience} = await moduleOf('ccc1-technical-recall.mjs');
const hash = value => createHash('sha256').update(value).digest('hex');

if (child) {
  const stateDir = process.argv[4];
  const request = JSON.parse(fs.readFileSync(path.join(stateDir, 'probe-request.json')));
  const key = fs.readFileSync(path.join(stateDir, 'ccc1-runtime.key'));
  const entries = DurableTaskJournal.forTask(stateDir, request.taskId, key).entries();
  const log = new ExperienceLog(path.join(stateDir, 'experience.jsonl'));
  const recall = recallTechnicalExperiences({...request, stateDir, key});
  process.stdout.write(JSON.stringify({pid: process.pid, entries: entries.length,
    summary: experienceOf(request.taskId, entries), records: log.entries().length,
    eligible_count: recall.items.length, recall_status: recall.status,
    ledger_sha256: hash(fs.readFileSync(log.file))}));
} else {
  const temporary = path.resolve(process.argv[3]);
  const stateDir = fs.mkdtempSync(path.join(temporary, 'independent-experience-'));
  const key = runtimeKey(stateDir), taskId = 't-1111222233334444';
  const sourceCommit = 'e101e393dd6c80803ac9d9ac91fccd9812c4a18d';
  const now = Date.UTC(2026, 10, 4), repoFullName = 'synthetic/autonomy';
  const task = {schema: 'continuum-coding-task', version: 1, id: 'independent-synthetic-task',
    sourceCommit, kind: 'BUGFIX', goal: 'Synthetic contract exercise; no real repair',
    allowedPaths: ['src/math.mjs'], acceptance: ['Synthetic check only'], chain: 'node',
    limits: {maxModelCalls: 2, maxContextChars: 12000}};
  const binding = {sessionId: randomUUID(), taskId, repoFullName, baseCommitSha: sourceCommit,
    worktreeId: 'independent-synthetic', deadline: now + 60000, maxRounds: 2,
    maxCpuMillis: 100000, maxMemoryBytes: 100000000, permissions: ['node-repair:node']};
  const journal = DurableTaskJournal.forTask(stateDir, taskId, key);
  const add = (sender, kind, payload, extra = {}) => journal.append(envelope({binding,
    sender, kind, payload, sequence: journal.entries().length, now: new Date(now), ...extra})).entry;
  const grantDir = path.join(stateDir, 'coding-grants');
  fs.mkdirSync(grantDir);
  fs.writeFileSync(path.join(grantDir, task.id + '.json'), JSON.stringify({task,
    ccc1TaskId: taskId, fingerprint: taskFingerprint(task)}));
  const requestOptions = {taskId, task, repoFullName, now: now + 1};
  fs.writeFileSync(path.join(stateDir, 'probe-request.json'), JSON.stringify(requestOptions));
  const log = new ExperienceLog(path.join(stateDir, 'experience.jsonl'));
  const cases = [];
  function check(name, action) {
    const start = performance.now();
    try { cases.push({name, expected_behavior_observed: true,
                     ...action(), wall_ms: performance.now() - start}); }
    catch (error) { cases.push({name, expected_behavior_observed: false,
                               error: error.message, wall_ms: performance.now() - start}); }
  }
  const started = new Date().toISOString(), start = performance.now();
  try {
    add('OWNER', 'GOAL', {text: task.goal + ' [task ' + taskFingerprint(task) + ']', repair: {chain: 'node'}});
    check('open_task_is_not_an_experience', () => {
      assert.equal(experienceOf(taskId, journal.entries()), null);
      return {experience: null};
    });
    const hypothesis = add('REASONER', 'HYPOTHESIS', {claim: 'Synthetic proposed repair',
      repair: {chain: 'node'}, basis: 'Synthetic contract fixture, not a model result'});
    const request = add('REASONER', 'TEST_REQUEST', {hypothesis: hypothesis.messageId,
      repair: {chain: 'node'}, budgetMinutes: 1});
    const receipt = add('VERIFIER', 'RECEIPT', {request: request.messageId, job: randomUUID(),
      status: 'PATCH_AVAILABLE', verdict: 'CONFIRMED', reasons: [], receiptSha256: 'a'.repeat(64),
      patchSha256: 'a'.repeat(64), execution: 'EXECUTED', cost: {cpuMillis: 0}},
      {verificationReceiptId: 'a'.repeat(64)});
    add('REASONER', 'CRITIQUE', {receipt: receipt.messageId, verdict: 'CONFIRMED',
      reason: 'Synthetic contract receipt, not an independently executed repair'});
    const summary = experienceOf(taskId, journal.entries());
    check('terminal_summary_is_durable_and_idempotent', () => {
      assert.equal(summary.outcome, 'COMPLETED');
      assert.equal(log.append(summary), true);
      assert.equal(log.append(summary), false);
      assert.equal(log.entries().length, 1);
      return {outcome: summary.outcome, stored_records: 1, duplicate_appended: false};
    });
    const ledgerBytes = fs.readFileSync(log.file);
    const journalBytes = fs.readFileSync(journal.file);
    check('fresh_node_process_replays_same_summary_but_no_strategy', () => {
      const run = spawnSync(process.execPath, [process.argv[1], '--child', source, stateDir],
        {encoding: 'utf8', timeout: 10000, env: process.env});
      assert.equal(run.status, 0, run.stderr);
      const result = JSON.parse(run.stdout);
      assert.notEqual(result.pid, process.pid);
      assert.deepEqual(result.summary, summary);
      assert.equal(result.ledger_sha256, hash(ledgerBytes));
      assert.equal(result.eligible_count, 0);
      assert.equal(result.recall_status, 'OK');
      return {...result, summary: undefined, independent_process: true,
        inference: 'NOT_RUN', note: 'Legacy success summary is deliberately ineligible for technical recall'};
    });
    check('same_task_conflicting_summary_does_not_upgrade_record', () => {
      assert.equal(log.append({...summary, outcome: 'FORGED_SUCCESS'}), false);
      assert.equal(hash(fs.readFileSync(log.file)), hash(ledgerBytes));
      return {append_returned: false, stored_bytes_unchanged: true,
        limitation: 'Task-id deduplication is not semantic conflict reporting'};
    });
    check('unrehashed_ledger_edit_is_detected', () => {
      const row = JSON.parse(ledgerBytes); row.record.outcome = 'FORGED_SUCCESS';
      fs.writeFileSync(log.file, JSON.stringify(row) + '\n');
      let error; try { log.entries(); } catch (caught) { error = caught.message; }
      assert.match(error ?? '', /damaged/);
      return {rejected: true, error};
    });
    fs.writeFileSync(log.file, ledgerBytes);
    check('rehashed_ledger_forgery_is_readable_but_not_recall_authority', () => {
      const row = JSON.parse(ledgerBytes);
      row.record.confirmed.patchSha256 = 'd'.repeat(64);
      const {hash: oldHash, ...data} = row;
      fs.writeFileSync(log.file, JSON.stringify({...data, hash: sha256(canonical(data))}) + '\n');
      const accepted = log.entries()[0].record.confirmed.patchSha256;
      assert.equal(accepted, 'd'.repeat(64));
      const recall = recallTechnicalExperiences({...requestOptions, stateDir, key});
      assert.equal(recall.status, 'OK'); assert.equal(recall.items.length, 0);
      return {ledger_accepts_recomputed_hash: true, forged_patch_sha256: accepted,
        recall_status: recall.status, eligible_count: recall.items.length,
        limitation: 'The ledger hash chain authenticates neither the writer nor semantic truth'};
    });
    fs.writeFileSync(log.file, ledgerBytes);
    check('self_reported_model_success_cannot_enter_technical_proof', () => {
      let error;
      try { recordTechnicalExperience({...requestOptions, stateDir, key, modelTask: task,
        modelObservation: Object.freeze({verifiedModelExecution: true, evidenceClass: 'LOCAL_MODEL_HOST'}),
        verification: {verdict: 'VERIFIED'}, reflection: {summary: 'Fabricated success',
          limits: ['Synthetic rejection probe only'], rejectedAlternatives: []}}); }
      catch (caught) { error = caught.message; }
      assert.match(error ?? '', /TECHNICAL_RUNTIME_OBSERVATION_REQUIRED/);
      assert.equal(hash(fs.readFileSync(log.file)), hash(ledgerBytes));
      return {rejected: true, error, ledger_unchanged: true};
    });
    check('rehashed_trusted_receipt_edit_fails_runtime_mac', () => {
      const rows = journalBytes.toString().trimEnd().split('\n').map(JSON.parse);
      let prevHash = '0'.repeat(64);
      for (const row of rows) {
        if (row.kind === 'RECEIPT') row.payload.patchSha256 = 'd'.repeat(64);
        const {hash: oldHash, mac, prevHash: oldPrev, ...env} = row;
        row.prevHash = prevHash; row.hash = sha256(canonical({...env, prevHash}));
        prevHash = row.hash;
      }
      fs.writeFileSync(journal.file, rows.map(JSON.stringify).join('\n') + '\n');
      let error; try { journal.entries(); } catch (caught) { error = caught.message; }
      assert.match(error ?? '', /valid runtime MAC/);
      return {rejected: true, error};
    });
    fs.writeFileSync(journal.file, journalBytes);
    check('torn_ledger_tail_blocks_recall_without_automatic_recovery', () => {
      fs.writeFileSync(log.file, ledgerBytes.toString().trimEnd());
      const recall = recallTechnicalExperiences({...requestOptions, stateDir, key});
      assert.equal(recall.status, 'BLOCKED_LEDGER_DAMAGED');
      assert.equal(recall.items.length, 0);
      return {status: recall.status, eligible_count: 0};
    });
    fs.writeFileSync(log.file, ledgerBytes);
    const passed = cases.every(c => c.expected_behavior_observed);
    process.stdout.write(JSON.stringify({schema: 'independent-ccc1-experience-probe-v1',
      evidence_class: 'CONTRACT_TEST_SYNTHETIC_RECEIPTS', started_at: started,
      finished_at: new Date().toISOString(), wall_ms: performance.now() - start,
      model_inference: 'NOT_RUN', repair_execution: 'NOT_RUN', neural_training: 'NOT_RUN',
      parent_pid: process.pid, cases, cases_count: cases.length, passed,
      fixture_ledger_sha256: hash(ledgerBytes), fixture_journal_sha256: hash(journalBytes)}, null, 2) + '\n');
    if (!passed) process.exitCode = 1;
  } finally {
    fs.rmSync(stateDir, {recursive: true, force: true});
  }
}
