/** One frozen acceptance test for one candidate, in a fresh temporary directory and a fresh Node child process.
 * Shared by run.mjs (PR #243 trial) and transfer.mjs (M3 transfer trial) so both use the identical oracle.
 * Not an OS sandbox: only the lab's own authored fixtures are ever executed. */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {sha256,tapOracle,validateObservation} from './policy.mjs';

export function testCandidate({task,strategy,cohort,arm,sourceSha,evidenceDir}) {
 const cwd=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-counterexample-'));
 const runId=sha256([sourceSha,cohort,arm,task.id,strategy].join('|'));
 try {
  fs.writeFileSync(path.join(cwd,'target.mjs'),task.candidates[strategy],{mode:0o600});
  fs.writeFileSync(path.join(cwd,'helper.mjs'),task.helper,{mode:0o600});
  fs.writeFileSync(path.join(cwd,'acceptance.test.mjs'),
   "import test from 'node:test';\nimport assert from 'node:assert/strict';\n"+
   "import {amount} from './target.mjs';\n"+
   "test('frozen acceptance',()=>assert.equal(amount(),"+task.expected+"));\n",{mode:0o600});
  const result=spawnSync(process.execPath,['--test','--test-reporter=tap','acceptance.test.mjs'],{
   cwd,encoding:'utf8',timeout:8000,maxBuffer:200000,
   env:{PATH:process.env.PATH??'',HOME:cwd,TMPDIR:cwd},
   windowsHide:true,killSignal:'SIGKILL'});
  if(result.signal||result.error||![0,1].includes(result.status))
   throw Error('EXECUTION_INDETERMINATE');
  const log=(result.stdout??'')+(result.stderr??'');
  const passed=tapOracle(result.status,log);
  const record=validateObservation({task:task.id,contextKey:task.contextKey,
   feature:task.feature,strategy,sourceSha256:sha256(task.source),
   passed,exitCode:result.status,executedTests:1,processExited:true,runId});
  fs.writeFileSync(path.join(evidenceDir,runId+'.json'),
   JSON.stringify({record,fixtureHash:sha256(JSON.stringify(task)),
    testOutput:log.slice(0,16000),externalProcess:true,
    osSandbox:false,permissionGranted:false},null,2)+'\n',{mode:0o600});
  return record;
 } finally {fs.rmSync(cwd,{recursive:true,force:true});}
}
