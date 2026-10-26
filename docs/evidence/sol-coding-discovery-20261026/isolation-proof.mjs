import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
const implementation=path.resolve(process.argv[2]),out=path.resolve(process.argv[3]);
fs.mkdirSync(out,{recursive:false,mode:0o700});
const load=relative=>import(pathToFileURL(path.join(implementation,relative)));
const {createIsolatedExecutor}=await load('.github/continuum/isolated-executor.mjs');
const {prepareCodingTask,runCodingTask}=await load('.github/continuum/coding-orchestrator.mjs');
const {sha256Hex}=await load('.github/continuum/development-backlog.mjs');
const root=path.join(out,'source'),state=path.join(out,'state');
fs.mkdirSync(path.join(root,'lib'),{recursive:true});
fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
const hostMarker=path.join(out,'host-only-private-marker');
fs.writeFileSync(hostMarker,'OWNER_MARKER_NEVER_MOUNTED');
const original="export function canonicalTags(items){return items.map(x=>x.trim()).filter(Boolean).sort();}\n";
fs.writeFileSync(path.join(root,'lib/tags.mjs'),original);
fs.writeFileSync(path.join(root,'.github/continuum/tags.test.mjs'),`import test from 'node:test';
import assert from 'node:assert/strict';import fs from 'node:fs';import os from 'node:os';
import {canonicalTags} from '../../lib/tags.mjs';
test('mixed case',()=>assert.deepEqual(canonicalTags([' BETA ','alpha']),['alpha','beta']));
test('duplicate canonical values',()=>assert.deepEqual(canonicalTags(['B','b','A']),['a','b']));
test('empty values',()=>assert.deepEqual(canonicalTags([' ','']),[]));
test('sandbox is unprivileged',()=>{assert.equal(process.getuid(),1000);assert.match(fs.readFileSync('/proc/self/status','utf8'),/^CapEff:\\s+0+$/m);assert.match(fs.readFileSync('/proc/self/status','utf8'),/^NoNewPrivs:\\s+1$/m);});
test('host private state is not mounted',()=>assert.throws(()=>fs.readFileSync(${JSON.stringify(hostMarker)}),{code:'ENOENT'}));
test('source is read-only',()=>assert.throws(()=>fs.writeFileSync('/work/probe-owner-write','must not persist'),{code:'EROFS'}));
test('network has only loopback',()=>assert.ok(Object.values(os.networkInterfaces()).flat().every(x=>x.internal)));
`);
const git=(...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
git('init','-q','-b','experiment/coding-sandbox');git('add','.');git('-c','user.name=Continuum Experiment','-c','user.email=continuum@example.invalid','commit','-qm','Freeze isolation and tag acceptance');
const commit=git('rev-parse','HEAD');
const image='sha256:635ac4bd326bd4059588042faeae80654dbe1f950894f3ce4005e4380d3e3b4b';
const executor=createIsolatedExecutor({image,nodeHome:path.dirname(path.dirname(fs.realpathSync(process.execPath))),
 timeoutMs:30000,memoryMb:512,pids:32,cpus:1});
const task={schema:'continuum-coding-task',version:1,id:'coding-isolation-proof-20261026',sourceCommit:commit,
 kind:'BUGFIX',goal:'Normalize tags to lower case, remove empty entries and deduplicate after normalization.',
 allowedPaths:['lib/tags.mjs'],acceptance:['Case variants produce one canonical lower-case tag.','Whitespace-only entries are omitted.'],
 chain:'node',limits:{maxModelCalls:2,maxContextChars:6000}};
fs.writeFileSync(path.join(out,'task.json'),JSON.stringify(task,null,2)+'\n');
const prepared=prepareCodingTask({project:root,stateDir:state,task});let providerCalls=0;
const result=await runCodingTask({project:root,stateDir:state,id:task.id,executor,
 executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,expectedTaskFingerprint:prepared.fingerprint,
 providerFactory:({task:spec})=>Object.assign(()=>{
  providerCalls++;
  return {mode:'LOCAL_CODING_PATCH_PROPOSAL_V1',trusted:false,autoApply:false,localRequests:1,
   context:{commit,contextSha256:sha256Hex(original),ranges:[]},
   plan:['Normalize before deduplicating'],hypothesis:'Case normalization must precede uniqueness.',
   patches:[{file:'lib/tags.mjs',oldText:'items.map(x=>x.trim()).filter(Boolean).sort()',
    newText:'[...new Set(items.map(x=>x.trim().toLowerCase()).filter(Boolean))].sort()'}]};
 },{taskSpec:spec,allowedPaths:spec.allowedPaths})});
fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(result,null,2)+'\n');
assert.equal(result.status,'PATCH_PENDING_OWNER_REVIEW');assert.equal(providerCalls,1);
assert.equal(result.approvedForMerge,false);assert.equal(result.deviceVerified,false);
const receipt=result.receipts[0];assert.equal(receipt.backlog.state,'DONE');
assert.equal(receipt.evidence.baselineFailed,2);
assert.equal(receipt.evidence.independentExecuted,7);
assert.equal(fs.readFileSync(hostMarker,'utf8'),'OWNER_MARKER_NEVER_MOUNTED');
assert.equal(fs.existsSync(path.join(root,'probe-owner-write')),false);
assert.equal(fs.readFileSync(path.join(root,'lib/tags.mjs'),'utf8'),original);assert.equal(git('status','--porcelain'),'');
const replay=await runCodingTask({project:root,stateDir:state,id:task.id,executor,
 executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,expectedTaskFingerprint:prepared.fingerprint,
 providerFactory:()=>{throw Error('must replay without another candidate call');}});
assert.equal(replay.status,result.status);assert.equal(providerCalls,1);
const summary={schema:'continuum-coding-discovery-isolation-proof',version:1,
 implementationCommit:spawnSync('git',['rev-parse','HEAD'],{cwd:implementation,encoding:'utf8'}).stdout.trim(),
 fixtureCommit:commit,image,actualModelUsed:false,injectedCandidate:true,
 status:result.status,providerCalls,baselineFailed:receipt.evidence.baselineFailed,
 candidateExecuted:receipt.evidence.candidateExecuted,independentExecuted:receipt.evidence.independentExecuted,
 sandboxAssertions:['nonroot uid1000','zero effective capabilities','no-new-privileges','read-only source','host private state unavailable','network loopback only'],
 sameTaskReplay:true,canonicalBacklogState:receipt.backlog.state,ownerSourceUnchanged:true,
 patchSha256:receipt.patch.sha256,approvedForMerge:false,deviceVerified:false,
 neuralGeneration:'NOT_RUN',apkBuild:'NOT_RUN',installation:'NOT_RUN'};
fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(summary,null,2)+'\n');
process.stdout.write(JSON.stringify(summary,null,2)+'\n');
