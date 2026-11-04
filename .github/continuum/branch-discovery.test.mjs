import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {discoverBranches,proposeBranchReviews} from './branch-discovery.mjs';
import {BacklogStore} from './development-backlog.mjs';
import {isCodingPath} from './coding-task.mjs';
import {critique} from './repair-critic.mjs';

function fixture(t){
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-branches-'));
 const root=path.join(temp,'repo'),state=path.join(temp,'state');
 fs.mkdirSync(root);
 const git=(...args)=>{
  const r=spawnSync('git',['-c','core.hooksPath=/dev/null',...args],{
   cwd:root,encoding:'utf8',timeout:10000,
   env:{...process.env,GIT_CONFIG_NOSYSTEM:'1',GIT_CONFIG_GLOBAL:'/dev/null'}
  });
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 const write=(file,text)=>{const p=path.join(root,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,text);};
 const commit=message=>{git('add','.');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm',message);return git('rev-parse','HEAD');};
 git('init','-q','-b','target');write('lib/value.mjs','export const value=1;\n');const base=commit('base');
 t.after(()=>fs.rmSync(temp,{recursive:true,force:true}));
 return {root,state,git,write,commit,base};
}

function feature(f,name='ready'){
 f.git('switch','-qc',name);f.write('lib/ready.mjs','export const ready=true;\n');
 const sha=f.commit(name);f.git('switch','-q','target');return sha;
}

test('finds unconnected remote, archived and pull refs once per exact source commit',t=>{
 const f=fixture(t),source=feature(f);
 f.git('update-ref','refs/remotes/origin/ready',source);
 f.git('update-ref','refs/archive/finished',source);
 f.git('update-ref','refs/pull/123/head',source);
 f.git('symbolic-ref','refs/remotes/origin/HEAD','refs/remotes/origin/ready');
 f.git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','tag','-am','finished','finished',source);
 const before=f.git('status','--porcelain=v1'),report=discoverBranches({root:f.root});
 const candidate=report.branches.find(x=>x.sourceCommit===source);
 assert.equal(candidate.status,'UNINTEGRATED_CHANGES');
 assert.deepEqual(candidate.refs,['refs/archive/finished','refs/heads/ready','refs/pull/123/head','refs/remotes/origin/ready','refs/tags/finished']);
 assert.equal(report.branches.filter(x=>x.sourceCommit===source).length,1);
 assert.deepEqual(candidate.changes.map(x=>[x.path,x.status]),[['lib/ready.mjs','MISSING_FROM_TARGET']]);
 assert.equal(candidate.quality,'NOT_BENCHMARKED');assert.equal(candidate.apk,'NOT_RUN');
 assert.equal(report.executedTests,0);assert.equal(report.approvedForMerge,false);
 assert.equal(f.git('rev-parse','HEAD'),f.base);assert.equal(f.git('status','--porcelain=v1'),before);
 assert.equal(fs.existsSync(path.join(f.root,'lib/ready.mjs')),false);
});

test('distinguishes ancestry from copied changes without using branch names as quality evidence',t=>{
 const f=fixture(t),source=feature(f,'best-finished-technology');
 f.write('lib/ready.mjs','export const ready=true;\n');f.write('lib/target-only.mjs','export const target=true;\n');f.commit('independent copy');
 const report=discoverBranches({root:f.root}),candidate=report.branches.find(x=>x.sourceCommit===source);
 assert.equal(candidate.status,'CHANGES_ALREADY_PRESENT');assert.equal(candidate.alreadyPresentCount,1);
 assert.deepEqual(candidate.changes,[]);
 f.git('branch','old-ancestor',f.base);
 assert.equal(discoverBranches({root:f.root}).branches.find(x=>x.sourceCommit===f.base).status,'ALREADY_ANCESTOR');
});

test('reports identical source trees even when commits differ',t=>{
 const f=fixture(t),source=feature(f);f.write('lib/ready.mjs','export const ready=true;\n');f.commit('other history');
 const found=discoverBranches({root:f.root}).branches.find(x=>x.sourceCommit===source);
 assert.equal(found.status,'TREE_IDENTICAL');assert.deepEqual(found.changes,[]);
});

test('detects divergent modifications and deletions without overwriting target files',t=>{
 const f=fixture(t);f.write('lib/remove.mjs','export const keep=true;\n');f.commit('removable baseline');
 f.git('switch','-qc','diverged');f.write('lib/value.mjs','export const value=2;\n');fs.unlinkSync(path.join(f.root,'lib/remove.mjs'));const source=f.commit('branch edits');
 f.git('switch','-q','target');f.write('lib/value.mjs','export const value=3;\n');f.commit('target edit');
 const found=discoverBranches({root:f.root}).branches.find(x=>x.sourceCommit===source);
 assert.deepEqual(found.changes.map(x=>[x.path,x.status]),[['lib/remove.mjs','SOURCE_DELETION'],['lib/value.mjs','DIVERGED']]);
 assert.equal(found.divergedCount,1);assert.equal(found.deletionCount,1);
 assert.equal(fs.readFileSync(path.join(f.root,'lib/value.mjs'),'utf8'),'export const value=3;\n');
 assert.equal(fs.existsSync(path.join(f.root,'lib/remove.mjs')),true);
});

test('keeps tests, build files, symlinks and control plane work protected',t=>{
 const f=fixture(t);f.git('switch','-qc','protected');
 f.write('app/build.gradle.kts','throw Error("must never execute")\n');
 f.write('.github/continuum/coding-new.mjs','throw Error("must never execute")\n');
 f.write('lib/value.test.mjs','throw Error("must never execute")\n');
 fs.symlinkSync('/nonexistent',path.join(f.root,'lib/link.mjs'));
 const source=f.commit('protected work');f.git('switch','-q','target');
 const found=discoverBranches({root:f.root}).branches.find(x=>x.sourceCommit===source);
 assert.equal(found.changes.length,4);assert.ok(found.changes.every(x=>x.scope==='PROTECTED_REVIEW'));
});

test('labels unrelated history and never queues it as an integration candidate',t=>{
 const f=fixture(t);f.git('switch','--orphan','unrelated');f.git('rm','-rf','--ignore-unmatch','.');
 f.write('lib/other.mjs','export const other=true;\n');const source=f.commit('unrelated');f.git('switch','-q','target');
 const report=discoverBranches({root:f.root});assert.equal(report.branches.find(x=>x.sourceCommit===source).status,'UNRELATED_HISTORY');
 assert.equal(proposeBranchReviews({report,stateDir:f.state}).proposed,0);
});

test('canonical backlog proposals are idempotent, source bound and never automatically accepted',t=>{
 const f=fixture(t),source=feature(f),report=discoverBranches({root:f.root});
 const first=proposeBranchReviews({report,stateDir:f.state});assert.equal(first.proposed,1);
 const second=proposeBranchReviews({report:discoverBranches({root:f.root}),stateDir:f.state});assert.equal(second.proposed,0);
 const doc=new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc;
 assert.equal(doc.tasks.length,1);const task=doc.tasks[0];
 assert.equal(task.kind,'BRANCH_REVIEW');assert.equal(task.state,'PROPOSED');assert.equal(task.source,'branch-discovery');
 assert.equal(task.subject.base,f.base);assert.equal(task.subject.sourceCommit,source);
 assert.equal(task.claim,null);assert.equal(task.submission,null);assert.deepEqual(task.evidence,[]);
 f.write('lib/new-target.mjs','export const extra=true;\n');const target=f.commit('advance target');
 assert.equal(proposeBranchReviews({report:discoverBranches({root:f.root}),stateDir:f.state}).proposed,1);
 assert.equal(new BacklogStore(path.join(f.state,'development-backlog.json')).read().doc.tasks.filter(x=>x.subject.base===target).length,1);
});

test('serialized or stale discovery output cannot enqueue fresh review authority',t=>{
 const f=fixture(t);feature(f);const report=discoverBranches({root:f.root});
 assert.throws(()=>proposeBranchReviews({report:JSON.parse(JSON.stringify(report)),stateDir:f.state}),/DISCOVERY_ORIGIN_REQUIRED/);
 f.git('update-ref','refs/archive/new',f.base);
 assert.throws(()=>proposeBranchReviews({report,stateDir:f.state}),/DISCOVERY_STALE/);
 assert.equal(fs.existsSync(path.join(f.state,'development-backlog.json')),false);
});

test('state inside the source or redirected into it is refused before writes',t=>{
 const f=fixture(t);feature(f);const report=discoverBranches({root:f.root});
 assert.throws(()=>proposeBranchReviews({report,stateDir:path.join(f.root,'new-state')}),/STATE_INSIDE_SOURCE/);
 const link=path.join(path.dirname(f.root),'redirect');fs.symlinkSync(f.root,link);
 assert.throws(()=>proposeBranchReviews({report,stateDir:path.join(link,'nested')}),/STATE_INSIDE_SOURCE/);
 assert.equal(fs.existsSync(path.join(f.root,'new-state')),false);assert.equal(fs.existsSync(path.join(f.root,'nested')),false);
});

test('bounded discovery exposes incomplete source and path coverage',t=>{
 const f=fixture(t);feature(f,'a-ready');f.git('switch','-qc','b-ready');
 f.write('lib/a.mjs','export const a=1;\n');f.write('lib/b.mjs','export const b=2;\n');f.commit('two additions');f.git('switch','-q','target');
 const report=discoverBranches({root:f.root,maxSources:1,maxChanges:1});
 assert.equal(report.coverage.complete,false);assert.ok(report.coverage.totalSources>report.coverage.scannedSources);
 assert.equal(report.coverage.scannedSources,1);
 const full=discoverBranches({root:f.root,maxChanges:1});
 const candidate=full.branches.find(x=>x.refs.includes('refs/heads/b-ready'));
 assert.equal(candidate.changesTruncated,true);assert.equal(candidate.changeCount,2);
});

test('invalid limits and option-like target references fail closed',t=>{
 const f=fixture(t);
 for(const options of [{maxSources:0},{maxSources:5001},{maxChanges:-1},{timeoutMs:0},{targetRef:'--upload-pack=anything'}])
  assert.throws(()=>discoverBranches({root:f.root,...options}),/INVALID_/);
});

test('dirty checkouts and planted backlog symlinks are refused',t=>{
 const f=fixture(t);feature(f);f.write('untracked.txt','not committed');
 assert.throws(()=>discoverBranches({root:f.root}),/SOURCE_DIRTY/);fs.unlinkSync(path.join(f.root,'untracked.txt'));
 const report=discoverBranches({root:f.root});fs.mkdirSync(f.state);const outside=path.join(path.dirname(f.root),'outside.json');fs.writeFileSync(outside,'untouched');
 fs.symlinkSync(outside,path.join(f.state,'development-backlog.json'));
 assert.throws(()=>proposeBranchReviews({report,stateDir:f.state}),/UNSAFE_STATE_FILE/);
 assert.equal(fs.readFileSync(outside,'utf8'),'untouched');
});

test('branch bot control files are protected by the real coding and repair gates',()=>{
 for(const file of ['.github/continuum/branch-discovery.mjs','.github/continuum/branch-bots-cli.mjs']){
  assert.equal(isCodingPath(file),false);
  assert.equal(critique({strategy:'LOCAL_SCOPE_CHECK',edits:[{file,oldText:'a',newText:'b'}]}).approved,false);
 }
});

test('configured clean filters cannot execute during the cleanliness check',t=>{
 const f=fixture(t);feature(f);f.write('.gitattributes','lib/value.mjs filter=trap\n');f.commit('attributes');
 const marker=path.join(path.dirname(f.root),'filter-executed');
 f.git('config','filter.trap.clean',`sh -c 'printf ran > "${marker}"; cat'`);
 const file=path.join(f.root,'lib/value.mjs'),future=new Date(Date.now()+2000);fs.utimesSync(file,future,future);
 assert.throws(()=>discoverBranches({root:f.root}),/DISCOVERY_CLEAN_FILTER_UNSUPPORTED/);
 assert.equal(fs.existsSync(marker),false);
});

test('preflight rejects fsmonitor hooks and partial clones before inspecting worktree state',t=>{
 const f=fixture(t);f.git('config','core.fsmonitor','/nonexistent/hook');
 assert.throws(()=>discoverBranches({root:f.root}),/DISCOVERY_FSMONITOR_UNSUPPORTED/);
 f.git('config','--unset','core.fsmonitor');f.git('config','remote.origin.promisor','true');
 assert.throws(()=>discoverBranches({root:f.root}),/DISCOVERY_PARTIAL_CLONE_UNSUPPORTED/);
});

test('configuration mask cannot hide executable clean filters from discovery',t=>{
 const f=fixture(t);f.write('.gitattributes','lib/value.mjs filter=trap\n');f.commit('attributes');
 const marker=path.join(path.dirname(f.root),'masked-filter-executed');
 f.git('config','filter.trap.clean',`sh -c 'printf ran > "${marker}"; cat'`);
 const file=path.join(f.root,'lib/value.mjs'),future=new Date(Date.now()+2000);fs.utimesSync(file,future,future);
 const prior=process.env.GIT_CONFIG;process.env.GIT_CONFIG='/dev/null';
 try{assert.throws(()=>discoverBranches({root:f.root}),/DISCOVERY_CLEAN_FILTER_UNSUPPORTED/);}
 finally{if(prior===undefined)delete process.env.GIT_CONFIG;else process.env.GIT_CONFIG=prior;}
 assert.equal(fs.existsSync(marker),false);
});

test('current worktree gitlinks are rejected before nested worktree status',t=>{
 const f=fixture(t);
 f.git('update-index','--add','--cacheinfo',`160000,${f.base},vendor`);
 f.git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','gitlink');
 assert.throws(()=>discoverBranches({root:f.root}),/DISCOVERY_SUBMODULE_WORKTREE_UNSUPPORTED/);
});
