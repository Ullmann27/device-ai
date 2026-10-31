import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {parseTeamleaderArgs,readOnlyMcpCycleEvidence} from './teamleader-cli.mjs';
import {verifyToolEvidence} from './teamleader-mcp-preflight.mjs';
import {runTeamleaderCycle} from './autonomous-teamleader.mjs';

function fixture(t) {
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-cli-mcp-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'repo'),stateDir=path.join(dir,'state');
 fs.mkdirSync(root);
 const git=(...args)=>{
  const result=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});
  assert.equal(result.status,0,result.stderr);
  return result.stdout.trim();
 };
 git('init','-q');
 fs.writeFileSync(path.join(root,'source.mjs'),'export const correct=true;\n');
 git('add','.');
 git('-c','user.name=Continuum Test','-c','user.email=continuum@example.invalid',
  'commit','-qm','fixed test fixture');
 return {dir,root,stateDir,head:git('rev-parse','HEAD'),git};
}

test('MCP git check is explicit opt-in for single and bounded scheduled loop',()=>{
 assert.equal(parseTeamleaderArgs(['--once']).mcpReadonly,undefined);
 assert.equal(parseTeamleaderArgs(['--once','--mcp-readonly']).mcpReadonly,true);
 const args=parseTeamleaderArgs(['--loop','--durable','--max-cycles','2','--mcp-readonly']);
 assert.equal(args.mcpReadonly,true);
 assert.equal(args.maxCycles,'2');
 for(const bad of [
  ['--status','--mcp-readonly'],
  ['--release-dead-lease','--mcp-readonly'],
  ['--mcp-readonly'],
  ['--once','--mcp-readonly','--mcp-readonly'],
  ['--loop','--mcp-readonly','--retry']
 ])assert.throws(()=>parseTeamleaderArgs(bad));
});

test('opted-in CLI uses real local MCP git server to validate HEAD and clean status',async t=>{
 const f=fixture(t);
 const evidence=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 assert.equal(evidence.head,f.head);
 assert.equal(evidence.clean,true);
 assert.equal(verifyToolEvidence(evidence,f.head),true);
 assert.deepEqual(evidence.receipts.map(r=>r.tool),['git_head','git_status']);
 assert.ok(evidence.receipts.every(r=>r.decision==='ALLOW'&&r.outcome==='OK'));
 const journal=fs.readFileSync(path.join(f.stateDir,'mcp-preflight.jsonl'),'utf8')
  .trim().split('\n').map(JSON.parse);
 assert.equal(journal.length,1);
 assert.equal(journal[0].head,f.head);
});

test('dirty git checkout cannot authorize an autonomous cycle',async t=>{
 const f=fixture(t),calls={repairs:0};
 fs.writeFileSync(path.join(f.root,'untracked.txt'),'NOT_AN_APPROVED_SOURCE');
 const evidence=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 assert.equal(evidence.clean,false);
 assert.equal(verifyToolEvidence(evidence,f.head),false);
 const driver={
  head:()=>f.head,
  repair:()=>{calls.repairs++;return {outcome:'NO_FAILURE'};}
 };
 assert.throws(()=>runTeamleaderCycle({
  root:f.root,stateDir:f.stateDir,driver,toolEvidence:evidence
 }),/MCP_TOOL_EVIDENCE_INVALID/);
 assert.equal(calls.repairs,0);
});

test('actual MCP read-only receipts permit only a single baseline per HEAD',async t=>{
 const f=fixture(t),calls={repairs:0};
 const driver={
  head:()=>f.git('rev-parse','HEAD'),
  repair:()=>{calls.repairs++;return {outcome:'NO_FAILURE',independent:{passed:true}};}
 };
 const e1=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 const first=runTeamleaderCycle({
  root:f.root,stateDir:f.stateDir,driver,toolEvidence:e1
 });
 assert.equal(first.kind,'BASELINE_REPAIR');
 assert.equal(first.outcome,'NO_FAILURE');
 assert.equal(calls.repairs,1);
 const e2=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 const second=runTeamleaderCycle({
  root:f.root,stateDir:f.stateDir,driver,toolEvidence:e2
 });
 assert.equal(second.outcome,'IDLE');
 assert.equal(calls.repairs,1);
});

test('previous MCP evidence cannot authorize a changed source commit',async t=>{
 const f=fixture(t),calls={repairs:0};
 const evidence=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 fs.writeFileSync(path.join(f.root,'source.mjs'),'export const correct=false;\n');
 f.git('add','.');
 f.git('-c','user.name=Continuum Test','-c','user.email=continuum@example.invalid',
  'commit','-qm','changed source');
 assert.notEqual(f.git('rev-parse','HEAD'),evidence.head);
 const driver={
  head:()=>f.git('rev-parse','HEAD'),
  repair:()=>{calls.repairs++;return {outcome:'NO_FAILURE'};}
 };
 assert.throws(()=>runTeamleaderCycle({
  root:f.root,stateDir:f.stateDir,driver,toolEvidence:evidence
 }),/MCP_TOOL_EVIDENCE_INVALID/);
 assert.equal(calls.repairs,0);
});

test('MCP tool evidence is read-only and contains no source code or secret values',async t=>{
 const f=fixture(t);
 const secret='secret-value-not-for-logs-029153';
 fs.writeFileSync(path.join(f.root,'private.txt'),secret);
 const evidence=await readOnlyMcpCycleEvidence({root:f.root,stateDir:f.stateDir});
 const text=fs.readFileSync(path.join(f.stateDir,'mcp-preflight.jsonl'),'utf8');
 assert.equal(text.includes(secret),false);
 assert.equal(text.includes('source.mjs'),false);
 assert.equal(evidence.clean,false);
});
