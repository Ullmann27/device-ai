#!/usr/bin/env node
// Evidence harness only: all proposals, gates, checks and publication use the existing worker.
// This creates its own throw-away Git repository; it never applies a patch to this source checkout.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {submitRepair, submitOnce, runCcc1Cycle} from '../../.github/continuum/ccc1-worker.mjs';
import {DurableTaskJournal, runtimeKey} from '../../.github/continuum/ccc1-journal.mjs';
import {builderStateOf, jobIdFor} from '../../.github/continuum/ccc1-recovery.mjs';
import {readReceipt} from '../../scripts/continuum-mobile-coding.mjs';
import {verifyRepairReceipt} from '../../.github/continuum/ccc1-verifier.mjs';
import {envelopeProblems} from '../../.github/continuum/ccc1-envelope.mjs';
import {PROFILES, CHAINS} from '../../.github/continuum/repair-loop.mjs';
import {publishRepair, validateVerifiedReport} from '../../.github/continuum/repair-publish.mjs';

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.resolve(process.argv[2] || path.join(sourceRoot, 'docs/evidence/synapse-ii-20261102/m3'));
assert(!fs.existsSync(path.join(out, 'receipt.json')), 'Refusing to overwrite an existing proof; choose a fresh output path');
fs.mkdirSync(out, {recursive:true});
const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'continuum-synapse-m3-'));
const root = path.join(sandbox, 'fixture');
const stateDir = path.join(sandbox, 'ccc1-state');
fs.mkdirSync(path.join(root, '.github/continuum'), {recursive:true});
const commands=[];
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const write=(name, data)=>fs.writeFileSync(path.join(out,name), typeof data==='string'?data:JSON.stringify(data,null,2)+'\n');
function command(label, cmd, args, cwd=root, expected=0) {
  const env={...process.env}; delete env.NODE_TEST_CONTEXT;
  const r=spawnSync(cmd,args,{cwd,encoding:'utf8',timeout:60000,shell:false,env});
  commands.push({label,cmd,args,cwd,exitCode:r.status,signal:r.signal,error:r.error?.code??null});
  if(r.stdout)write(label+'.stdout.txt',r.stdout);
  if(r.stderr)write(label+'.stderr.txt',r.stderr);
  write('commands.json',commands);
  assert.equal(r.status,expected,(r.stdout??'')+(r.stderr??''));
  return r.stdout.trim();
}
const g=(label,args)=>command(label,'git',args);
const sourceCommit=command('source-head','git',['rev-parse','HEAD'],sourceRoot);
const sourceBranch=command('source-branch','git',['branch','--show-current'],sourceRoot);
assert(['research/continuum-synapse-ii-20261102','research/continuum-synapse-iii-20261102'].includes(sourceBranch));
const good='export function square(x){return x*x;}\nexport function quad(x){return square(square(x));}\n';
const bad=good.replace('return square(square(x))','return sqare(square(x))');
const tests="import test from 'node:test';\nimport assert from 'node:assert/strict';\nimport {square,quad} from './math.mjs';\n"+
  "test('square positive control',()=>assert.equal(square(3),9));\n"+
  "test('quad positive regression',()=>assert.equal(quad(2),16));\n"+
  "test('quad zero regression',()=>assert.equal(quad(0),0));\n"+
  "test('quad negative regression',()=>assert.equal(quad(-3),81));\n";
fs.writeFileSync(path.join(root,'.github/continuum/math.mjs'),good);
fs.writeFileSync(path.join(root,'.github/continuum/math.test.mjs'),tests);
g('fixture-init',['init','-q','-b','synapse-m3-fixture']);
g('fixture-add-seed',['add','.']);
const author=['-c','user.name=continuum-m3-fixture','-c','user.email=m3-fixture@example.invalid'];
g('fixture-commit-seed',[...author,'commit','-qm','seed: bounded arithmetic fixture']);
const seedCommit=g('fixture-seed-head',['rev-parse','HEAD']);
command('seed-test',process.execPath,['--test','.github/continuum/math.test.mjs']);
fs.writeFileSync(path.join(root,'.github/continuum/math.mjs'),bad);
g('fixture-commit-fault',[...author,'commit','-qam','fault: unresolved numeric helper']);
const fixtureSourceCommit=g('fixture-fault-head',['rev-parse','HEAD']);
command('baseline-raw',process.execPath,['--test','.github/continuum/math.test.mjs'],root,1);
const profile=PROFILES['node-continuum'](root,out);
write('fixed-profile.json',{chain:'node',profiles:CHAINS.node,command:profile});
write('fixture-source.json',{good,bad,tests,seedCommit,fixtureSourceCommit});
// Owner authorization here is limited to the user-requested disposable fixture experiment.
// Existing Ccc1Reasoner requests a fixed 25-minute repair budget; the goal must cover it.
const taskId=submitRepair({project:root,stateDir,chain:'node',repoFullName:'local/synapse-m3-fixture',maxRounds:1,deadlineMinutes:30});
const order=crypto.randomUUID();
const boundOrder=submitOnce({project:root,stateDir,order,chain:'node',sourceCommit:fixtureSourceCommit});
assert.deepEqual(boundOrder,{taskId,duplicate:true});
write('bound-order.json',{order,...boundOrder,sourceCommit:fixtureSourceCommit});
const cycle=await runCcc1Cycle({project:root,stateDir});
write('ccc1-cycle.json',cycle);
assert.equal(cycle.ccc1,'COMPLETED',JSON.stringify(cycle));
const entries=DurableTaskJournal.forTask(stateDir,taskId,runtimeKey(stateDir)).entries();
write('ccc1-envelopes.json',entries);
assert(entries.every(({prevHash,hash,mac,...e})=>envelopeProblems(e).length===0));
assert.deepEqual(entries.map(e=>e.kind),['GOAL','HYPOTHESIS','PLAN','TEST_REQUEST','RECEIPT','CRITIQUE']);
const job=jobIdFor(taskId,entries.find(e=>e.kind==='TEST_REQUEST').messageId);
const receipt=readReceipt(builderStateOf(stateDir),job);
write('receipt.json',receipt);
const verification=verifyRepairReceipt(receipt,{job,commit:fixtureSourceCommit,chain:'node'});
write('ccc1-verification.json',verification);
assert.equal(verification.verdict,'CONFIRMED',JSON.stringify(verification));
assert.equal(receipt.status,'REPAIR_PATCH_VERIFIED_PENDING_REVIEW');
assert.equal(receipt.approvedForMerge,false);
assert.equal(receipt.installed,false);
assert.equal(receipt.deviceVerified,false);
const [baseline,candidate,independent]=receipt.commands;
assert(baseline.exitCode!==0 && baseline.failed>0);
assert(candidate.exitCode===0 && candidate.executed===4 && candidate.failed===0);
assert(independent.exitCode===0 && independent.executed===4 && independent.failed===0);
assert.equal(receipt.commit,fixtureSourceCommit);
assert(/^[a-f0-9]{40}$/.test(receipt.commit));
assert(/^[a-f0-9]{64}$/.test(receipt.patch.sha256));
assert.equal(g('fixture-after-worker-head',['rev-parse','HEAD']),fixtureSourceCommit);
assert.equal(g('fixture-after-worker-status',['status','--porcelain']),'');
assert.equal(fs.readFileSync(path.join(root,'.github/continuum/math.mjs'),'utf8'),bad);
const reportDir=path.dirname(receipt.patch.path);
for(const name of ['report.json','repair.patch','task-result.json','node-junit.xml'])
  fs.copyFileSync(path.join(reportDir,name),path.join(out,name));
fs.cpSync(path.join(reportDir,'independent-check'),path.join(out,'independent-check'),{recursive:true});
const report=JSON.parse(fs.readFileSync(path.join(reportDir,'report.json'),'utf8'));
const patch=fs.readFileSync(receipt.patch.path,'utf8');
assert.equal(hash(patch),receipt.patch.sha256);
validateVerifiedReport(report,patch);
assert.equal(report.inference.mode,'DETERMINISTIC_ONLY');
assert.equal(report.inference.modelInvocations,0);
const mainError=await publishRepair({root,outDir:reportDir,targetBranch:'main',push:false}).then(()=>null,e=>e.message);
assert.equal(mainError,'Invalid target branch main');
write('main-blocked.json',{target:'main',blocked:true,message:mainError});
// The canonical publisher applies and commits the worker's exact verified patch in another throw-away worktree.
const published=await publishRepair({root,outDir:reportDir,targetBranch:'synapse-m3-fixture',push:false});
write('disposable-publish.json',published);
assert(/^[a-f0-9]{40}$/.test(published.commit));
assert.equal(g('proposal-parent',['rev-parse',published.commit+'^']),fixtureSourceCommit);
const commitPatch=command('proposal-commit-patch','git',['diff','--binary',fixtureSourceCommit,published.commit]);
assert.equal(commitPatch.trim(),patch.trim());
assert.equal(g('fixture-after-publish-head',['rev-parse','HEAD']),fixtureSourceCommit);
assert.equal(g('fixture-after-publish-status',['status','--porcelain']),'');
const reviewRoot=path.join(sandbox,'committed-control');
g('committed-control-worktree',['worktree','add','--detach',reviewRoot,published.commit]);
command('committed-control-raw',process.execPath,['--test','.github/continuum/math.test.mjs'],reviewRoot);
g('fixture-bundle',['bundle','create',path.join(out,'fixture.bundle'),'HEAD',published.commit]);
// A restarted owner retry stays bound to the same task; no second execution.
assert.deepEqual(submitOnce({project:root,stateDir,order,chain:'node',sourceCommit:fixtureSourceCommit}),{taskId,duplicate:true});
// A second scheduler cycle must not repeat the completed task.
const replay=await runCcc1Cycle({project:root,stateDir});
write('replay.json',replay); assert.equal(replay.outcome,'IDLE');
const files=['.github/continuum/repair-loop.mjs','.github/continuum/repair-publish.mjs',
 '.github/continuum/repair-strategies.mjs','.github/continuum/backlog-repair-worker.mjs',
 '.github/continuum/ccc1-worker.mjs','.github/continuum/ccc1-envelope.mjs',
 '.github/continuum/ccc1-verifier.mjs','scripts/continuum-mobile-coding.mjs',
 'tools/continuum-worker-proof/run-m3.mjs'];
write('summary.json',{
 schema:'continuum-synapse-ii-m3-host-proof',version:1,status:'HOST_REPAIR_VERIFIED_PENDING_REVIEW',
 sourceCommit,sourceBranch,fixtureSourceCommit,fixtureSeedCommit:seedCommit,fixtureCandidateCommit:published.commit,
 patchSha256:receipt.patch.sha256,taskId,job,chain:'node',strategy:report.verified.strategy,
 node:process.version,platform:process.platform,arch:process.arch,startedFixtureAt:sandbox,
 fixtureExecution:report.execution,baseline,candidate,independent,committedControlExitCode:0,
 mainBlocked:true,approvalState:'FIXTURE_EXECUTION_AUTHORIZED; MERGE_NOT_APPROVED',
 sourceCheckoutPatched:false,ownerDataWritten:false,paidApiCalls:0,modelInvocations:0,
 device:'DEVICE_NOT_RUN',androidBuild:'NOT_RUN',release:'NOT_RUN',push:'NOT_PUSHED_BY_M3_AGENT',
 sourceFiles:Object.fromEntries(files.map(f=>[f,hash(fs.readFileSync(path.join(sourceRoot,f)))]))
});
console.log(JSON.stringify({out,sourceCommit,fixtureSourceCommit,candidateCommit:published.commit,
 patchSha256:receipt.patch.sha256,baseline:baseline.failed,candidate:candidate.executed,
 independent:independent.executed,strategy:report.verified.strategy,mainBlocked:true,device:'DEVICE_NOT_RUN'}));
