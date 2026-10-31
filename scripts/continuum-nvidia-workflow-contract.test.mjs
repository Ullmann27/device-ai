import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const reviewedSha = '22d6b27a959831d87ce9707eb6296f34c293af38';
const workflowPath = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../.github/workflows/continuum-nvidia-ten-minute.yml');
const workflow = fs.readFileSync(workflowPath, 'utf8');

function stepBlock(source, name) {
  const lines = source.split(/\r?\n/);
  const marker = `      - name: ${name}`;
  const start = lines.findIndex(line => line === marker);
  if (start < 0) throw Error(`WORKFLOW_STEP_MISSING: ${name}`);
  let end = lines.findIndex((line, i) => i > start && line.startsWith('      - name: '));
  if (end < 0) end = lines.length;
  return lines.slice(start, end).join('\n');
}

function requireEnv(source, step, key, value) {
  const block = stepBlock(source, step);
  const lines = block.split('\n');
  const envIndex = lines.findIndex(line => line === '        env:');
  if (envIndex < 0) throw Error('WORKFLOW_EXECUTE_ENV_MISSING');
  const envLines = [];
  for (const line of lines.slice(envIndex + 1)) {
    if (!line.startsWith('          ')) break;
    envLines.push(line);
  }
  if (!envLines.includes(`          ${key}: ${value}`)) {
    throw Error(`WORKFLOW_EXECUTE_${key}_MISSING`);
  }
}

function validateContract(source) {
  const checkout = stepBlock(source, 'Checkout immutable reviewed research sources');
  if (!checkout.split('\n').includes(`          ref: ${reviewedSha}`)) {
    throw Error('WORKFLOW_UNREVIEWED_EXECUTOR_REF');
  }
  const name = 'One NVIDIA Ultra 550B technical research request';
  requireEnv(source, name, 'GITHUB_TOKEN', '${{ github.token }}');
  requireEnv(source, name, 'GITHUB_REPOSITORY', '${{ github.repository }}');
  requireEnv(source, name, 'GITHUB_RUN_ID', '${{ github.run_id }}');
  const exec = stepBlock(source, name);
  if (!exec.includes('node scripts/continuum-nvidia-scheduled.mjs --execute')) {
    throw Error('WORKFLOW_EXECUTOR_MISSING');
  }
  const tests = stepBlock(source, 'Offline tests must pass before scheduling NVIDIA');
  if (!tests.includes('scripts/continuum-nvidia-execution-claim.test.mjs')) {
    throw Error('WORKFLOW_CLAIM_TESTS_NOT_GATED');
  }
  return true;
}

test('RED: an executor step without GitHub token must be rejected', () => {
  const withoutToken = workflow.replace('      - name: One NVIDIA Ultra 550B technical research request\n        if: steps.reserve.outputs.allowed == \'true\'\n        shell: bash\n        env:\n          GITHUB_TOKEN: ${{ github.token }}\n', '      - name: One NVIDIA Ultra 550B technical research request\n        if: steps.reserve.outputs.allowed == \'true\'\n        shell: bash\n        env:\n');
  assert.notEqual(withoutToken, workflow);
  assert.throws(() => validateContract(withoutToken), /WORKFLOW_EXECUTE_GITHUB_TOKEN_MISSING/);
});

test('RED: an old unreviewed executor pin must be rejected', () => {
  const old = workflow.replace(`ref: ${reviewedSha}`, 'ref: fd2e5de1acf678ae4d6222bb433762ea8bb3d57f');
  assert.notEqual(old, workflow);
  assert.throws(() => validateContract(old), /WORKFLOW_UNREVIEWED_EXECUTOR_REF/);
});

test('RED: missing independent claim tests must be rejected', () => {
  const withoutTests = workflow.replace('            scripts/continuum-nvidia-execution-claim.test.mjs \\\n', '');
  assert.notEqual(withoutTests, workflow);
  assert.throws(() => validateContract(withoutTests), /WORKFLOW_CLAIM_TESTS_NOT_GATED/);
});

test('GREEN: workflow has reviewed immutable source, scoped credentials and offline claim gate', () => {
  assert.equal(validateContract(workflow), true);
});
