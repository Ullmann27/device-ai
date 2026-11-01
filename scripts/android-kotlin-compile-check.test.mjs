import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

test('actual host classifier preserves membership under pipefail and an early grep match',()=>{
  const source=fs.readFileSync(new URL('./android-kotlin-compile-check.sh',import.meta.url),'utf8');
  const condition=source.match(/^  (if .+; then) external\+=/m)?.[1];
  assert.ok(condition,'the actual classifier condition must be exercised');
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-host-membership-'));
  try {
    const input=path.join(dir,'unavailable-files.txt');
    fs.writeFileSync(input,'target.kt\n'+'other.kt\n'.repeat(20000)+'last.kt\n');
    const body='set -o pipefail; unavailable=$(cat "$1"); file="$2"; '+condition+' exit 0; else exit 1; fi';
    for(const [file,expected] of [['target.kt',0],['last.kt',0],['unknown.kt',1]]) {
      const run=spawnSync('bash',['-c',body,'classifier',input,file],{encoding:'utf8',timeout:5000});
      assert.equal(run.status,expected,file+': '+run.stderr);
    }
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});
