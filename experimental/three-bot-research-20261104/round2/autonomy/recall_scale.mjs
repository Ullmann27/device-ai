// CONTRACT_TEST ONLY. Synthetic canonical grants, receipts and technical proofs
// use disposable fixture keys. No real model observation, repair or training.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash, createHmac} from 'node:crypto';
import {performance} from 'node:perf_hooks';
import {pathToFileURL} from 'node:url';

const source = path.resolve(process.argv[2]), tmp = path.resolve(process.argv[3]);
const imp = file => import(pathToFileURL(path.join(source, file)).href);
const {canonical, envelope} = await imp('.github/continuum/ccc1-envelope.mjs');
const {ExperienceLog, experienceOf, EXPERIENCE_LIMITS} = await imp('.github/continuum/ccc1-experience.mjs');
const {DurableTaskJournal} = await imp('.github/continuum/ccc1-journal.mjs');
const {taskFingerprint} = await imp('.github/continuum/coding-task.mjs');
const {jobTaskId} = await imp('scripts/continuum-mobile-coding.mjs');
const {recallTechnicalExperiences, TECHNICAL_RECALL_LIMITS} = await imp('.github/continuum/ccc1-technical-recall.mjs');
const digest = value => createHash('sha256').update(value).digest('hex');
const hash = value => digest(canonical(value));
const uuid = label => { const h = digest(label); return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-8${h.slice(17,20)}-${h.slice(20,32)}`; };
const A = 'e101e393dd6c80803ac9d9ac91fccd9812c4a18d', B = 'b'.repeat(40), C = 'c'.repeat(40);
const NOW = Date.UTC(2026,10,4,22), REPO = 'synthetic/recall-scale';
const FILE_A = 'src/math.mjs', FILE_B = 'src/text.mjs', FILE_C = 'src/unseen.mjs';
const SIGN_DOMAIN = 'continuum-momega1-technical-proof-v1\0';
const sign = (proof, key) => ({...proof, mac: createHmac('sha256', key).update(SIGN_DOMAIN + canonical(proof)).digest('hex')});
const samplesPerCondition = 20, warmupPerCondition = 3;
const work = fs.mkdtempSync(path.join(tmp, 'recall-scale-'));

function createTask(dataset, id, sourceCommit, allowedPaths, complete) {
  const task = {schema: 'continuum-coding-task', version: 1, id, sourceCommit, kind: 'BUGFIX',
    goal: 'Synthetic recall contract; no real repair', allowedPaths,
    acceptance: ['Synthetic acceptance only'], chain: 'node', limits: {maxModelCalls: 2, maxContextChars: 12000}};
  const taskId = 't-' + digest(id).slice(0,16), fingerprint = taskFingerprint(task);
  fs.writeFileSync(path.join(dataset.stateDir, 'coding-grants', id + '.json'), JSON.stringify({task, ccc1TaskId: taskId, fingerprint}));
  const binding = {sessionId: uuid(id + '-session'), taskId, repoFullName: REPO, baseCommitSha: sourceCommit,
    worktreeId: 'synthetic-scale', deadline: NOW + 3600000, maxRounds: 2,
    maxCpuMillis: 100000, maxMemoryBytes: 100000000, permissions: ['node-repair:node']};
  const journal = DurableTaskJournal.forTask(dataset.stateDir, taskId, dataset.key);
  let sequence = 0;
  function add(sender, kind, payload, extra = {}) {
    const env = envelope({binding, sender, kind, payload, sequence, now: new Date(NOW), ...extra});
    env.messageId = uuid(id + '-message-' + sequence++);
    return journal.append(env).entry;
  }
  add('OWNER', 'GOAL', {text: task.goal + ' [task ' + fingerprint + ']', repair: {chain: 'node'}});
  if (!complete) return {task, taskId};
  const job = uuid(id + '-job'), modelTask = {...task, id: jobTaskId(job)}, modelFingerprint = taskFingerprint(modelTask);
  const H = digest(id + '-synthetic-identity');
  const hypothesis = add('REASONER', 'HYPOTHESIS', {claim: 'Synthetic arithmetic repair', repair: {chain: 'node'}, basis: 'Contract fixture only'});
  const request = add('REASONER', 'TEST_REQUEST', {hypothesis: hypothesis.messageId, repair: {chain: 'node'}, budgetMinutes: 1});
  const receipt = add('VERIFIER', 'RECEIPT', {request: request.messageId, job, status: 'PATCH_AVAILABLE',
    verdict: 'CONFIRMED', reasons: [], receiptSha256: H, patchSha256: H, execution: 'EXECUTED', cost: {cpuMillis: 0}},
    {verificationReceiptId: H});
  add('REASONER', 'CRITIQUE', {receipt: receipt.messageId, verdict: 'CONFIRMED', reason: 'Synthetic contract fixture only'});
  const entries = journal.entries();
  const modelObservation = {schema: 'momega1-local-process-observation-v1', evidenceClass: 'LOCAL_MODEL_HOST',
    executionId: 'synthetic-' + id, taskId: modelTask.id, sourceCommit, taskFingerprint: modelFingerprint,
    requestIndex: 1, contextSha256: H, promptSha256: H, requestSha256: H, modelSha256: H, runtimeSha256: H,
    outputSha256: H, proposalSha256: H, outputTokens: 0, pid: 1, exitCode: 0,
    verifiedModelExecution: true, isolation: {kind: 'SYNTHETIC_CONTRACT_FIXTURE_NOT_INFERENCE'}};
  const verification = {schema: 'momega1-coding-verification', version: 1, evidenceClass: 'HOST_INTEGRATION',
    taskFingerprint: modelFingerprint, sourceCommit, chain: 'node', patchSha256: H, proposalSha256: H,
    verdict: 'VERIFIED', reasons: [], red: {profiles: [{identities: [H], exitCode: 1}]},
    green: {profiles: [{identities: [H], exitCode: 0}]}, diagnostics: [{identity: H, message: 'Synthetic failure'}]};
  const observedAt = NOW - 10000 + dataset.records.length;
  const reflection = {summary: 'Synthetic verified-scope fixture.', limits: ['Contract evidence only; no inference.'], rejectedAlternatives: []};
  const proof = {schema: 'momega1-technical-proof', version: 1, kind: 'TECHNICAL_EXPERIENCE_REVIEW_ONLY', evidenceClass: 'LOCAL_MODEL_HOST',
    taskId, task, modelTask, taskFingerprint: fingerprint, repoFullName: REPO, journalHead: entries.at(-1).hash,
    requestMessageId: request.messageId, receiptMessageId: receipt.messageId, receiptSha256: H,
    modelObservation, verification, reflection, observedAt, expiresAt: observedAt + TECHNICAL_RECALL_LIMITS.maxAgeMs,
    resources: {cpuMillis: 0, rounds: 1}};
  dataset.log.append({...experienceOf(taskId, entries), technicalProof: sign(proof, dataset.key)});
  const info = {taskId, sourceCommit, file: allowedPaths[0], observedAt};
  dataset.records.push(info);
  return info;
}

function datasetOf(size) {
  const started = performance.now(), stateDir = path.join(work, 'n-' + size);
  fs.mkdirSync(path.join(stateDir, 'coding-grants'), {recursive: true});
  const dataset = {size, stateDir, key: Buffer.from(digest('public-disposable-contract-key-' + size), 'hex'),
    records: [], log: new ExperienceLog(path.join(stateDir, 'experience.jsonl'))};
  for (let i=0; i<size; i++) {
    const group = i % 4;
    createTask(dataset, `synthetic-n${size}-history-${String(i).padStart(3,'0')}`,
      group < 2 ? A : B, [group % 2 === 0 ? FILE_A : FILE_B], true);
  }
  dataset.preparation_ms = performance.now() - started;
  dataset.ledger_bytes = size ? fs.statSync(dataset.log.file).size : 0;
  dataset.ledger_sha256 = size ? digest(fs.readFileSync(dataset.log.file)) : digest('');
  assert.ok(dataset.ledger_bytes <= EXPERIENCE_LIMITS.maxBytes);
  return dataset;
}

function conditionOf(dataset, label, sourceCommit, files, maxChars=4096, limit=3) {
  const id = `synthetic-n${dataset.size}-query-${label}`;
  const {task} = createTask(dataset, id, sourceCommit, files, false);
  const eligible = dataset.records.filter(r => r.sourceCommit === sourceCommit && files.includes(r.file)).reverse().map(r => r.taskId);
  return {name: `${label}/n=${dataset.size}`, dataset, task, eligible, maxChars, limit, samples: [],
    expected_nonempty: eligible.length > 0 && maxChars === 4096};
}

function observe(condition, retain) {
  const cpu = process.cpuUsage(), started = performance.now();
  const result = recallTechnicalExperiences({stateDir: condition.dataset.stateDir, task: condition.task,
    repoFullName: REPO, key: condition.dataset.key, now: NOW, maxChars: condition.maxChars, limit: condition.limit, project: null});
  const wall_ms = performance.now() - started, usage = process.cpuUsage(cpu);
  assert.equal(result.status, 'OK', condition.name);
  assert.ok(result.items.length <= condition.limit && result.items.length <= 3);
  assert.ok(result.text.length <= condition.maxChars && result.text.length <= condition.task.limits.maxContextChars);
  const returned = result.items.map(item => item.taskId);
  assert.deepEqual(returned, condition.eligible.slice(0, returned.length), condition.name + ': newest eligible prefix');
  if (condition.expected_nonempty) assert.ok(returned.length > 0, condition.name + ': positive control');
  else assert.equal(returned.length, 0, condition.name + ': negative/context control');
  for (const item of result.items) {
    assert.equal(item.kind, 'TECHNICAL_EXPERIENCE_REVIEW_ONLY');
    assert.equal(item.sourceLineage.kind, 'EXACT_SOURCE');
  }
  const sample = {wall_ms, cpu_ms: (usage.user + usage.system) / 1000,
    returned_ids: returned, output_chars: result.text.length, output_sha256: digest(result.text), status: result.status};
  if (retain) condition.samples.push(sample);
  return sample;
}

function stats(values) {
  const sorted = [...values].sort((a,b) => a-b), n = sorted.length;
  return {count: n, min: sorted[0], median: (sorted[Math.floor((n-1)/2)] + sorted[Math.ceil((n-1)/2)]) / 2,
    p95: sorted[Math.ceil(.95*n)-1], max: sorted.at(-1), mean: values.reduce((a,b)=>a+b,0)/n};
}

const start = performance.now(), startedAt = new Date().toISOString();
let output;
try {
  const datasets = [0, 1, 32, 128].map(datasetOf);
  const single = conditionOf(datasets[1], 'single-positive', A, [FILE_A]);
  const positiveControl = observe(single, false);
  assert.equal(positiveControl.returned_ids.length, 1);
  const conditions = [conditionOf(datasets[0], 'empty', A, [FILE_A])];
  for (const dataset of datasets.slice(2)) {
    conditions.push(conditionOf(dataset, 'matching-one-file', A, [FILE_A]));
    conditions.push(conditionOf(dataset, 'matching-one-file-limit2', A, [FILE_A], 4096, 2));
    conditions.push(conditionOf(dataset, 'matching-two-files', A, [FILE_A, FILE_B]));
    conditions.push(conditionOf(dataset, 'unseen-file', A, [FILE_C]));
    conditions.push(conditionOf(dataset, 'unseen-source', C, [FILE_A]));
    conditions.push(conditionOf(dataset, 'context-128', A, [FILE_A], 128));
  }
  for (let round=0; round<warmupPerCondition; round++) for (const c of conditions) observe(c, false);
  const measuredStart = performance.now();
  for (let round=0; round<samplesPerCondition; round++) {
    // Rotate the condition order so the larger corpus is not always measured last.
    for (let j=0; j<conditions.length; j++) observe(conditions[(j+round)%conditions.length], true);
  }
  const measured_ms = performance.now() - measuredStart;
  const pairs = [32,128].map(size => {
    const original = conditions.find(c=>c.name===`matching-one-file/n=${size}`);
    const limited = conditions.find(c=>c.name===`matching-one-file-limit2/n=${size}`);
    for (let i=0;i<samplesPerCondition;i++) {
      assert.deepEqual(limited.samples[i].returned_ids, original.samples[i].returned_ids);
      assert.equal(limited.samples[i].output_sha256, original.samples[i].output_sha256);
    }
    const originalMedian = stats(original.samples.map(s=>s.wall_ms)).median;
    const limitedMedian = stats(limited.samples.map(s=>s.wall_ms)).median;
    return {corpus_records: size, output_identical_all_pairs: true,
      default_limit: 3, compared_limit: 2, unchanged_max_chars: 4096,
      default_wall_median_ms: originalMedian, limit2_wall_median_ms: limitedMedian,
      default_over_limit2_median_ratio: originalMedian/limitedMedian,
      paired_calls: samplesPerCondition};
  });
  for (const d of datasets) {
    assert.equal(d.log.entries().length, d.size);
    assert.equal(d.size ? digest(fs.readFileSync(d.log.file)) : digest(''), d.ledger_sha256);
  }
  output = {schema: 'ccc1-recall-scale-contract-v1', evidence_class: 'CONTRACT_TEST_SYNTHETIC_TECHNICAL_PROOFS',
    baseline_sha: A, node: process.version, platform: process.platform, arch: process.arch,
    started_at: startedAt, finished_at: new Date().toISOString(), total_wall_ms: performance.now()-start,
    measured_ms, measured_calls: conditions.length*samplesPerCondition, warmup_calls: conditions.length*warmupPerCondition,
    samples_per_condition: samplesPerCondition, order: 'rotated_each_measured_round', passed: true,
    model_invocations: 0, owner_job_executions: 0, neural_training: 'NOT_RUN', repair_quality: 'NOT_MEASURED',
    synthetic_proof_admission: 'Manually signed disposable fixture proofs; recordTechnicalExperience live producer admission NOT_RUN',
    source_scope_mode: 'project:null; exact SHA equality only; Git ancestry NOT_RUN',
    limits: {experience: EXPERIENCE_LIMITS, recall: TECHNICAL_RECALL_LIMITS},
    positive_control: positiveControl,
    existing_api_limit_comparisons: pairs,
    datasets: datasets.map(d=>({records: d.size, preparation_ms: d.preparation_ms,
      ledger_bytes: d.ledger_bytes, ledger_sha256: d.ledger_sha256, records_metadata: d.records})),
    conditions: conditions.map(c=>({name: c.name, corpus_records: c.dataset.size, eligible_records: c.eligible.length,
      query_source: c.task.sourceCommit, query_paths: c.task.allowedPaths, max_chars: c.maxChars, limit: c.limit,
      wall_ms: stats(c.samples.map(s=>s.wall_ms)), cpu_ms: stats(c.samples.map(s=>s.cpu_ms)), samples: c.samples}))};
} catch (error) {
  output = {schema: 'ccc1-recall-scale-contract-v1', passed: false, error: error.message, stack: error.stack,
    evidence_class: 'CONTRACT_TEST_SYNTHETIC_TECHNICAL_PROOFS', wall_ms: performance.now()-start};
  process.exitCode = 1;
} finally {
  fs.rmSync(work, {recursive: true, force: true});
}
process.stdout.write(JSON.stringify(output, null, 2)+'\n');
