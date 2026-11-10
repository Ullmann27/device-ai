import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const source = fs.readFileSync('.github/workflows/continuum-code52-cinematic-qa.yml', 'utf8');
const testPath = '.github/continuum/code52-cinematic-qa-branch-concurrency.test.mjs';

test('separate Cinema QA source branches cannot cancel each other', () => {
  const section = source.match(/^concurrency:\s*\n([\s\S]*?)(?=^jobs:)/m);
  assert.ok(section, 'explicit concurrency configuration required');
  assert.match(section[1], /^  group: continuum-code52-cinematic-unsigned-qa-\$\{\{ github\.ref \}\}$/m);
  assert.match(section[1], /^  cancel-in-progress: true$/m);
});

test('each permitted push branch is also gated by the exact source ref', () => {
  const push = source.match(/^    branches:\s*\n([\s\S]*?)(?=^    paths:)/m);
  assert.ok(push, 'push branch allowlist required');
  const branches = [...push[1].matchAll(/^      - (.+)$/gm)].map((match) => {
    const raw = match[1];
    const quoted = raw.match(/^['"](.+)['"]$/);
    return quoted ? quoted[1] : raw;
  });
  assert.ok(branches.length >= 4);
  const gate = source.match(/^    if: (.*)$/m)?.[1] || '';
  for (const branch of branches) {
    if (branch.endsWith('/**')) {
      const prefix = branch.slice(0, -2);
      assert.ok(gate.includes("startsWith(github.ref, 'refs/heads/" + prefix + "')"), branch + ' bypasses prefix-ref gate');
    } else {
      assert.ok(gate.includes("github.ref == 'refs/heads/" + branch + "'"), branch + ' bypasses source-ref gate');
    }
  }
  assert.match(source, /permissions:\s*\n  contents: read/);
  assert.ok(source.includes('node --test') && source.includes(testPath), 'CI must run this guard');
});
