import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, chmodSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const qa = readFileSync(join(root, 'scripts/render-continuum-owner-continuous-qa.sh'), 'utf8');
const begin = '# CONTINUUM_OWNER_BACKEND_GATE_START';
const end = '# CONTINUUM_OWNER_BACKEND_GATE_END';
const from = qa.indexOf(begin);
const to = qa.indexOf(end);

test('original owner QA requires backend exact-lock gate before Android and signing', () => {
  assert.ok(from > 0 && to > from, 'mandatory backend gate missing');
  assert.equal(qa.indexOf(begin, from + 1), -1, 'duplicate backend gate');
  assert.ok(to < qa.indexOf("node -p 'Date.now()' > continuum-all-systems-logs/GRADLE-START.txt"));
  assert.match(qa, /continuum-owner-backend-gate\.test\.mjs/);
  const gate = qa.slice(from, to);
  assert.match(gate, /npm ci --ignore-scripts --no-audit --no-fund/);
  assert.match(gate, /npm run check/);
  assert.match(gate, /npm test/);
  assert.match(gate, /gateFailures=\$\(\(gateFailures\+1\)\)/);
  assert.match(qa, /if \(\( gateFailures > 0 \)\); then[\s\S]*?fail 'At least one genuine QA gate is red/);
});

test('real shell refuses failed npm ci, check, or test, and accepts all-green protocol', () => {
  assert.ok(from > 0 && to > from, 'missing backend shell source');
  const gate = qa.slice(from, to + end.length);
  for (const failing of ['', 'ci', 'check', 'test']) {
    const dir = mkdtempSync(join(tmpdir(), 'continuum-owner-backend-'));
    try {
      mkdirSync(join(dir, 'backend'));
      mkdirSync(join(dir, 'continuum-all-systems-logs'));
      mkdirSync(join(dir, 'bin'));
      const npm = join(dir, 'bin/npm');
      writeFileSync(npm, `#!/usr/bin/env bash
set -euo pipefail
case "\$1" in
  ci) stage=ci; [[ "\$2" == "--ignore-scripts" ]] ;;
  run) [[ "\$2" == "check" ]]; stage=check ;;
  test) stage=test ;;
  *) exit 97 ;;
esac
printf '%s\\n' "\$stage" >> "\$CALLS_FILE"
[[ "\$stage" != "\$FAIL_STAGE" ]] || exit 23
`, { mode: 0o700 });
      chmodSync(npm, 0o700);
      const script = `set -Eeuo pipefail
gateFailures=0
sha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
${gate}
printf 'gateFailures=%s\\n' "\$gateFailures"
`;
      const p = spawnSync('bash', ['-c', script], {
        cwd: dir, encoding: 'utf8', timeout: 20000,
        env: { ...process.env, PATH: join(dir, 'bin') + ':' + process.env.PATH,
          CALLS_FILE: join(dir, 'calls.txt'), FAIL_STAGE: failing }
      });
      assert.equal(p.status, 0, `${failing}: ${p.stderr}`);
      assert.match(p.stdout, failing ? /gateFailures=1/ : /gateFailures=0/);
      assert.match(p.stdout, failing ? /QA_RED backend-lockfile-check-and-tests/ :
        /QA_GREEN backend-lockfile-check-and-tests/);
      const calls = readFileSync(join(dir, 'calls.txt'), 'utf8').trim().split('\n');
      const expected = failing === 'ci' ? ['ci'] :
        failing === 'check' ? ['ci', 'check'] : ['ci', 'check', 'test'];
      assert.deepEqual(calls, expected, `stages after ${failing || 'success'}`);
      assert.match(readFileSync(join(dir, 'continuum-all-systems-logs/BACKEND-SOURCE.txt'), 'utf8'),
        /aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }
});
