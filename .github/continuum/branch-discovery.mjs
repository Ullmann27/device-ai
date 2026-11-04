/** Read-only branch discovery. Findings are review proposals, never execution grants. */
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {BacklogStore,LIMITS,canonical,sha256Hex} from './development-backlog.mjs';
import {isCodingPath} from './coding-task.mjs';
import {resolveOutputDestination} from './backlog-repair-worker.mjs';

const COMMIT=/^[0-9a-f]{40}$/;
const ZERO='0'.repeat(40);
const admitted=new WeakMap();
const ACTOR='branch-discovery-bot';
const MAX_OUTPUT=8*1024*1024;
const freeze=value=>{
 if(value&&typeof value==='object'&&!Object.isFrozen(value)){
  for(const child of Object.values(value))freeze(child);
  Object.freeze(value);
 }
 return value;
};

function integer(value,min,max,name){
 if(!Number.isSafeInteger(value)||value<min||value>max)throw Error('INVALID_'+name);
 return value;
}

function reader(root,deadline){
 return (args,allowed=[0])=>{
  const remaining=deadline-Date.now();
  if(remaining<=0)throw Error('DISCOVERY_DEADLINE');
  const env={...process.env,GIT_TERMINAL_PROMPT:'0',GIT_OPTIONAL_LOCKS:'0',GIT_NO_LAZY_FETCH:'1'};
  for(const key of ['GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_COMMON_DIR','GIT_OBJECT_DIRECTORY',
   'GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_SHALLOW_FILE','GIT_NAMESPACE','GIT_CONFIG'])delete env[key];
  // Config reads must see the effective caller config, including fsmonitor,
  // before the protected reader overrides it for repository operations.
  const overrides=args[0]==='config'?[]:['-c','core.fsmonitor=false','-c','core.hooksPath=/dev/null','-c','diff.external='];
  const result=spawnSync('git',['--no-pager','--no-replace-objects',...overrides,...args],{
   cwd:root,encoding:'utf8',timeout:Math.min(10000,remaining),maxBuffer:MAX_OUTPUT,
   shell:false,env
  });
  if(result.error||result.signal||!allowed.includes(result.status))
   throw Error('DISCOVERY_GIT_FAILED: '+args[0]);
  if(result.stdout.includes('\ufffd'))throw Error('DISCOVERY_UNSUPPORTED_FILENAME_ENCODING');
  return {stdout:result.stdout,status:result.status};
 };
}

function resolveCommit(git,ref){
 const value=git(['rev-parse','--verify','--end-of-options',ref+'^{commit}']).stdout.trim();
 if(!COMMIT.test(value))throw Error('DISCOVERY_UNSUPPORTED_COMMIT_FORMAT');
 return value;
}

function readRefs(git){
 const bytes=git(['for-each-ref','--format=%(refname)%00%(objectname)%00%(symref)%00%(objecttype)','refs/']).stdout;
 const rows=bytes.trimEnd()?bytes.trimEnd().split('\n'):[];
 if(rows.length>10000)throw Error('DISCOVERY_REF_LIMIT');
 const refs=[],ignored=[];
 for(const row of rows){
  const fields=row.split('\0');
  if(fields.length!==4||!fields[0].startsWith('refs/')||!COMMIT.test(fields[1]))throw Error('DISCOVERY_REF_INVALID');
  const [ref,object,symref,type]=fields;
  if(symref||ref.startsWith('refs/notes/')||ref.startsWith('refs/replace/')){
   ignored.push({ref,object,reason:symref?'SYMBOLIC_ALIAS':ref.startsWith('refs/notes/')?'GIT_NOTES':'REPLACEMENT_REF'});continue;
  }
  let commit=object;
  if(type==='tag'){
   const peeled=git(['rev-parse','--verify','--end-of-options',ref+'^{commit}'],[0,128]);
   if(peeled.status!==0){ignored.push({ref,object,reason:'NOT_A_COMMIT'});continue;}
   commit=peeled.stdout.trim();
  }else if(type!=='commit'){
   ignored.push({ref,object,reason:'NOT_A_COMMIT'});continue;
  }
  if(!COMMIT.test(commit))throw Error('DISCOVERY_REF_INVALID');
  refs.push({ref,object,commit});
 }
 refs.sort((a,b)=>a.ref.localeCompare(b.ref));
 ignored.sort((a,b)=>a.ref.localeCompare(b.ref));
 return {refs,ignored,digest:sha256Hex(canonical({refs,ignored}))};
}

function readTree(git,commit){
 const tree=new Map(),bytes=git(['ls-tree','-r','-z',commit]).stdout;
 for(const row of bytes.split('\0')){
  if(!row)continue;
  const tab=row.indexOf('\t'),header=row.slice(0,tab).split(' '),file=row.slice(tab+1);
  if(tab<0||header.length!==3||!COMMIT.test(header[2])||!file)throw Error('DISCOVERY_TREE_INVALID');
  tree.set(file,{mode:header[0],sha:header[2]});
 }
 return tree;
}

function sourceChanges(git,base,source){
 const bytes=git(['diff','--raw','-z','--no-abbrev','--no-renames','--no-ext-diff',
  '--no-textconv','--ignore-submodules=none',base,source,'--']).stdout;
 const fields=bytes.split('\0');
 if(fields.at(-1)==='')fields.pop();
 if(fields.length%2)throw Error('DISCOVERY_DIFF_INVALID');
 const changes=[];
 for(let i=0;i<fields.length;i+=2){
  const match=/^:(\d{6}) (\d{6}) ([a-f0-9]{40}) ([a-f0-9]{40}) ([AMDT])$/.exec(fields[i]);
  if(!match||!fields[i+1])throw Error('DISCOVERY_DIFF_INVALID');
  changes.push({path:fields[i+1],baseMode:match[1],sourceMode:match[2],baseBlob:match[3],sourceBlob:match[4]});
 }
 return changes;
}

function scopeOf(change){
 const file=change.path;
 if(!['000000','100644','100755'].includes(change.sourceMode)||
    !['000000','100644','100755'].includes(change.baseMode)||
    /(?:^|\/)(?:tests?|androidTest)(?:\/|\.)|\.(?:test|spec)\.|(?:^|\/)(?:build|settings)\.gradle|AndroidManifest\.xml$/.test(file))
  return 'PROTECTED_REVIEW';
 if(isCodingPath(file))return 'SCOPED_SOURCE_REVIEW';
 if(/\.(?:mjs|js|kt|java|xml|kts|gradle|sh|ya?ml)$/.test(file)||file.startsWith('.github/'))return 'PROTECTED_REVIEW';
 return 'ASSET_OR_DOC_REVIEW';
}

function cleanSource(git,sourceRoot){
 safeGitConfiguration(git,sourceRoot);
 if(git(['status','--porcelain=v1','--untracked-files=all','-z']).stdout)throw Error('DISCOVERY_SOURCE_DIRTY');
}

function safeGitConfiguration(git,sourceRoot){
 if(git(['config','--get-regexp','^filter\\..*\\.(clean|process)$'],[0,1]).status===0)
  throw Error('DISCOVERY_CLEAN_FILTER_UNSUPPORTED');
 const monitor=git(['config','--get','core.fsmonitor'],[0,1]);
 if(monitor.status===0&&!['','false','no','off','0'].includes(monitor.stdout.trim().toLowerCase()))
  throw Error('DISCOVERY_FSMONITOR_UNSUPPORTED');
 const promisor=git(['config','--get-regexp','^(extensions\\.partialclone|remote\\..*\\.promisor)$'],[0,1]);
 if(promisor.status===0&&promisor.stdout.trim().split('\n').some(row=>{
  const split=row.indexOf(' '),key=row.slice(0,split),value=row.slice(split+1).trim().toLowerCase();
  return key==='extensions.partialclone'||!['','false','no','off','0'].includes(value);
 }))throw Error('DISCOVERY_PARTIAL_CLONE_UNSUPPORTED');
 const grafts=git(['rev-parse','--git-path','info/grafts']).stdout.trim();
 if(fs.existsSync(path.resolve(sourceRoot,grafts)))throw Error('DISCOVERY_GRAFTS_UNSUPPORTED');
 // Git status can invoke an initialized submodule's own filters/fsmonitor.
 // Candidate gitlinks remain inspectable data; current worktree gitlinks do not.
 if(/(?:^|\0)160000 /.test(git(['ls-files','--stage','-z']).stdout))
  throw Error('DISCOVERY_SUBMODULE_WORKTREE_UNSUPPORTED');
}

/** Call before the existing scheduler, which performs its own source status checks. */
export function assertBranchSourceSafe(root){
 const sourceRoot=fs.realpathSync(path.resolve(root)),git=reader(sourceRoot,Date.now()+30000);
 if(fs.realpathSync(git(['rev-parse','--show-toplevel']).stdout.trim())!==sourceRoot)throw Error('INVALID_REPOSITORY_ROOT');
 cleanSource(git,sourceRoot);
 return {sourceRoot,checkoutCommit:resolveCommit(git,'HEAD')};
}

/** Reads only locally available refs and committed trees; never fetches or checks out a branch. */
export function discoverBranches({root,targetRef='HEAD',maxSources=1000,maxChanges=200,timeoutMs=120000}={}){
 if(typeof root!=='string'||!root||typeof targetRef!=='string'||
    !/^[A-Za-z0-9][A-Za-z0-9._/-]{0,254}$/.test(targetRef))throw Error('INVALID_SOURCE_OR_TARGET');
 integer(maxSources,1,5000,'SOURCE_LIMIT');integer(maxChanges,1,2000,'CHANGE_LIMIT');integer(timeoutMs,1,300000,'TIMEOUT');
 const sourceRoot=fs.realpathSync(path.resolve(root)),deadline=Date.now()+timeoutMs,git=reader(sourceRoot,deadline);
 if(fs.realpathSync(git(['rev-parse','--show-toplevel']).stdout.trim())!==sourceRoot)throw Error('INVALID_REPOSITORY_ROOT');
 cleanSource(git,sourceRoot);
 const checkoutCommit=resolveCommit(git,'HEAD'),targetCommit=resolveCommit(git,targetRef),snapshot=readRefs(git);
 const historyComplete=git(['rev-parse','--is-shallow-repository']).stdout.trim()==='false';
 const targetTree=readTree(git,targetCommit),targetTreeSha=git(['rev-parse',targetCommit+'^{tree}']).stdout.trim();
 const grouped=new Map();
 for(const entry of snapshot.refs){
  if(entry.commit===targetCommit)continue;
  if(!grouped.has(entry.commit))grouped.set(entry.commit,[]);
  grouped.get(entry.commit).push(entry.ref);
 }
 const sources=[...grouped.entries()].sort((a,b)=>a[1][0].localeCompare(b[1][0]));
 const branches=[];
 for(const [sourceCommit,refs] of sources.slice(0,maxSources)){
  const branch={sourceCommit,refs,mergeBase:null,status:null,changes:[],changeCount:0,
   alreadyPresentCount:0,missingCount:0,divergedCount:0,deletionCount:0,changesTruncated:false,
   quality:'NOT_BENCHMARKED',apk:'NOT_RUN'};
  if(git(['merge-base','--is-ancestor',sourceCommit,targetCommit],[0,1]).status===0){
   branch.status='ALREADY_ANCESTOR';branches.push(branch);continue;
  }
  if(git(['rev-parse',sourceCommit+'^{tree}']).stdout.trim()===targetTreeSha){
   branch.status='TREE_IDENTICAL';branches.push(branch);continue;
  }
  const merge=git(['merge-base','--all',targetCommit,sourceCommit],[0,1]);
  const bases=merge.stdout.trim()?merge.stdout.trim().split('\n'):[];
  if(bases.length!==1){
   branch.status=bases.length>1?'AMBIGUOUS_MERGE_BASE':historyComplete?'UNRELATED_HISTORY':'INCOMPLETE_HISTORY';
   branches.push(branch);continue;
  }
  if(!COMMIT.test(bases[0]))throw Error('DISCOVERY_MERGE_BASE_INVALID');
  branch.mergeBase=bases[0];
  for(const change of sourceChanges(git,branch.mergeBase,sourceCommit)){
   const target=targetTree.get(change.path)??{sha:ZERO,mode:'000000'};
   if(target.sha===change.sourceBlob&&target.mode===change.sourceMode){branch.alreadyPresentCount++;continue;}
   const status=change.sourceBlob===ZERO?'SOURCE_DELETION':
    target.sha===change.baseBlob&&target.mode===change.baseMode?'MISSING_FROM_TARGET':'DIVERGED';
   branch.changeCount++;
   if(status==='MISSING_FROM_TARGET')branch.missingCount++;
   if(status==='DIVERGED')branch.divergedCount++;
   if(status==='SOURCE_DELETION')branch.deletionCount++;
   if(branch.changes.length<maxChanges)branch.changes.push({...change,targetBlob:target.sha,targetMode:target.mode,status,scope:scopeOf(change)});
  }
  branch.changesTruncated=branch.changeCount>branch.changes.length;
  branch.status=branch.changeCount?'UNINTEGRATED_CHANGES':'CHANGES_ALREADY_PRESENT';
  branches.push(branch);
 }
 // Moving refs or source edits cannot turn a mixed scan into a pinned observation.
 if(resolveCommit(git,'HEAD')!==checkoutCommit||resolveCommit(git,targetRef)!==targetCommit||readRefs(git).digest!==snapshot.digest)
  throw Error('DISCOVERY_REFS_CHANGED_DURING_SCAN');
 cleanSource(git,sourceRoot);
 const report=freeze({schema:'continuum-branch-discovery',version:1,sourceRoot,checkoutCommit,targetRef,targetCommit,
  refSnapshotSha256:snapshot.digest,scope:'LOCAL_AVAILABLE_REFS_ONLY',networkFetch:false,historyComplete,
  coverage:{availableRefs:snapshot.refs.length,ignoredRefs:snapshot.ignored,totalSources:sources.length,
   scannedSources:branches.length,complete:branches.length===sources.length&&!branches.some(x=>x.changesTruncated),maxSources,maxChanges},
  branches,executedTests:0,approvedForMerge:false,deviceVerified:false,apk:'NOT_RUN'});
 admitted.set(report,{sourceRoot,checkoutCommit,targetRef,targetCommit,refDigest:snapshot.digest});
 return report;
}

function assertFresh(report){
 const origin=admitted.get(report);
 if(!origin)throw Error('DISCOVERY_ORIGIN_REQUIRED');
 const git=reader(origin.sourceRoot,Date.now()+30000);
 if(resolveCommit(git,'HEAD')!==origin.checkoutCommit||resolveCommit(git,origin.targetRef)!==origin.targetCommit||
    readRefs(git).digest!==origin.refDigest)throw Error('DISCOVERY_STALE');
 cleanSource(git,origin.sourceRoot);return origin;
}

/** Canonical external state directory; no path may redirect a write into source. */
export function branchStateDirectory(root,stateDir){
 if(typeof stateDir!=='string'||!stateDir)throw Error('INVALID_STATE_DIRECTORY');
 const source=fs.realpathSync(root),state=resolveOutputDestination(stateDir);
 if(state===source||state.startsWith(source+path.sep))throw Error('STATE_INSIDE_SOURCE');
 fs.mkdirSync(state,{recursive:true,mode:0o700});
 if(!fs.lstatSync(state).isDirectory()||fs.realpathSync(state)!==state)throw Error('UNSAFE_STATE_DIRECTORY');
 return state;
}

export function assertBranchStateFile(file){
 let stat;try{stat=fs.lstatSync(file);}catch(error){if(error.code==='ENOENT')return;throw error;}
 if(!stat.isFile()||stat.isSymbolicLink()||stat.nlink!==1)throw Error('UNSAFE_STATE_FILE');
}

/** Only propose. Existing owner/integration review must decide whether work may execute. */
export function proposeBranchReviews({report,stateDir,maxProposals=20,at=Date.now()}={}){
 integer(maxProposals,1,100,'PROPOSAL_LIMIT');integer(at,0,Number.MAX_SAFE_INTEGER,'TIMESTAMP');
 const origin=assertFresh(report),state=branchStateDirectory(origin.sourceRoot,stateDir);
 const file=path.join(state,'development-backlog.json');
 for(const suffix of ['', '.bak','.lock'])assertBranchStateFile(file+suffix);
 const store=new BacklogStore(file,{actor:ACTOR}),doc=store.read().doc;
 const existing=new Set(doc.tasks.map(task=>task.id));
 const candidates=report.branches.filter(x=>x.status==='UNINTEGRATED_CHANGES').map(branch=>({branch,
  id:'branch-'+sha256Hex(canonical({target:report.targetCommit,source:branch.sourceCommit})).slice(0,24)}));
 const pending=candidates.filter(x=>!existing.has(x.id));
 const selected=pending.slice(0,Math.min(maxProposals,Math.max(0,LIMITS.tasks-doc.tasks.length)));
 const ops=selected.map(({branch,id})=>({op:'propose',actor:ACTOR,at,ifAbsent:true,
  reason:'Pinned source-tree difference only; quality, wiring and APK execution require independent review.',
  task:{id,title:'Review '+branch.sourceCommit.slice(0,12)+' into '+report.targetCommit.slice(0,12),
   kind:'BRANCH_REVIEW',source:'branch-discovery',priority:branch.divergedCount||branch.deletionCount?400:550,maxAttempts:1,
   subject:{base:report.targetCommit,sourceCommit:branch.sourceCommit,sourceRef:branch.refs[0].slice(0,300),
    changeCount:branch.changeCount,missingCount:branch.missingCount,divergedCount:branch.divergedCount,
    deletionCount:branch.deletionCount,quality:'NOT_BENCHMARKED',apk:'NOT_RUN',automaticExecution:false}}}));
 let proposed=0;
 if(ops.length){
  assertFresh(report);
  const applied=store.apply(ops,{expectedRevision:doc.revision,stopOnError:true});
  if(applied.results.length!==ops.length||applied.results.some(x=>x.ok!==true))throw Error('BRANCH_BACKLOG_REJECTED');
  proposed=applied.results.filter(x=>x.changed===true).length;
 }
 return {proposed,existing:candidates.length-pending.length,deferred:pending.length-selected.length,
  backlog:file,taskIds:selected.map(x=>x.id),state:'PROPOSED_ONLY',automaticExecution:false};
}
