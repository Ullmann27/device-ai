/** Reproducible host research using the existing typed CCC1 path. No solution code. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
import {prepareCodingTask,runCodingTask} from '../.github/continuum/coding-orchestrator.mjs';
import {createCodingCandidateProvider} from '../.github/continuum/coding-provider.mjs';
import {createIsolatedExecutor,loadIsolationConfig} from '../.github/continuum/isolated-executor.mjs';
import {runChain,git} from '../.github/continuum/repair-loop.mjs';
import {taskFingerprint} from '../.github/continuum/coding-task.mjs';
const implementation=fileURLToPath(new URL('..',import.meta.url));
const experimentDir=path.join(implementation,'experimental/autonomous-cycle-20261027');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const [operation,outArg,configFile]=process.argv.slice(2);
if(!['prepare','run','verify'].includes(operation)||!outArg||!configFile)throw Error('Usage: prepare|run|verify /external/new-run /external/isolation.json');
const out=path.resolve(outArg);
if(out===implementation||out.startsWith(implementation+path.sep))throw Error('EXTERNAL_EVIDENCE_REQUIRED');
const root=path.join(out,'source'),stateDir=path.join(out,'state');
const executor=createIsolatedExecutor(loadIsolationConfig(path.resolve(configFile),{sourceRoot:implementation}));
const write=(name,value)=>fs.writeFileSync(path.join(out,name),JSON.stringify(value,null,2)+'\n',{flag:'wx',mode:0o600});
if(operation==='prepare'){
 fs.mkdirSync(out,{mode:0o700});
 const spec=JSON.parse(fs.readFileSync(path.join(experimentDir,'experiment.json'),'utf8'));
 const source=git(implementation,['show',spec.sourceCommit+':'+spec.sourceFile]).stdout;
 fs.mkdirSync(path.join(root,path.dirname(spec.sourceFile)),{recursive:true});
 fs.mkdirSync(path.join(root,'.github/continuum'),{recursive:true});
 fs.writeFileSync(path.join(root,spec.sourceFile),source);
 fs.writeFileSync(path.join(root,'package.json'),'{"type":"module"}\n');
 fs.copyFileSync(path.join(experimentDir,'fenced-code.test.mjs'),path.join(root,'.github/continuum/acceptance.test.mjs'));
 // Freeze the holdout bytes before inference, outside both model context and normal test chain.
 fs.copyFileSync(path.join(experimentDir,'fenced-code.holdout.mjs'),path.join(out,'sealed-holdout.mjs'));
 git(root,['init','-q','-b','research/mentor-fenced-code']);git(root,['add','.']);
 git(root,['-c','user.name=Continuum research','-c','user.email=research@example.invalid','commit','-qm','Freeze unchanged production formatter and acceptance tests']);
 const task={schema:'continuum-coding-task',version:1,id:spec.caseId,sourceCommit:git(root,['rev-parse','HEAD']).stdout.trim(),kind:'BUGFIX',
  goal:spec.goal,allowedPaths:[spec.sourceFile],acceptance:spec.acceptance,chain:'node',limits:{maxModelCalls:spec.maxModelCalls,maxContextChars:spec.maxContextChars}};
 write('task.json',task);write('freeze.json',{spec,sourceSha256:sha(source),holdoutSha256:sha(fs.readFileSync(path.join(out,'sealed-holdout.mjs'))),
  testSha256:sha(fs.readFileSync(path.join(root,'.github/continuum/acceptance.test.mjs'))),implementationCommit:git(implementation,['rev-parse','HEAD']).stdout.trim()});
 git(root,['bundle','create',path.join(out,'source.bundle'),'HEAD']);
 const checks=path.join(out,'baseline');fs.mkdirSync(checks);
 const baseline=runChain('node',root,checks,{executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',deadline:Date.now()+60000});
 write('baseline.json',baseline);console.log(JSON.stringify({phase:'BASELINE',passed:baseline.passed,tests:baseline.results.map(r=>r.tests)}));
 if(baseline.passed)throw Error('NO_REPRODUCED_DEFECT');
 write('prepared.json',prepareCodingTask({project:root,stateDir,task}));
}else if(operation==='run'){
 const task=JSON.parse(fs.readFileSync(path.join(out,'task.json'),'utf8'));let calls=0;
 const started=Date.now();
 const result=await runCodingTask({project:root,stateDir,id:task.id,expectedTaskFingerprint:taskFingerprint(task),executor,
  executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',requireIsolation:true,providerFactory:options=>createCodingCandidateProvider({...options,
   modelTimeoutMs:JSON.parse(fs.readFileSync(path.join(out,'freeze.json'),'utf8')).spec.limits.modelTimeoutMs,
   run:(binary,args,opts)=>{
    // Research-only explicit capture: this source has no personal data or secrets. Never record env/auth headers.
    const n=++calls;fs.writeFileSync(path.join(out,`model-${n}-input.json`),opts.input,{flag:'wx',mode:0o600});
    const response=spawnSync(binary,args,opts);
    fs.writeFileSync(path.join(out,`model-${n}-output.json`),response.stdout??'',{flag:'wx',mode:0o600});
    write(`model-${n}-transport.json`,{binary,args,elapsedBoundMs:opts.timeout,status:response.status,signal:response.signal,error:response.error?.code??null,
     inputSha256:sha(opts.input),outputSha256:sha(response.stdout??'')});
    return response;
   }})});
 write('result.json',{implementationCommit:git(implementation,['rev-parse','HEAD']).stdout.trim(),elapsedMs:Date.now()-started,calls,result});
 for(const receipt of result.receipts??[])if(receipt.patch?.path)fs.copyFileSync(receipt.patch.path,path.join(out,'model-generated.patch'));
 console.log(JSON.stringify({status:result.status,calls,elapsedMs:Date.now()-started}));
 if(result.status!=='PATCH_PENDING_OWNER_REVIEW')process.exitCode=1;
}else{
 const task=JSON.parse(fs.readFileSync(path.join(out,'task.json'),'utf8'));
 const freeze=JSON.parse(fs.readFileSync(path.join(out,'freeze.json'),'utf8'));
 const holdout=fs.readFileSync(path.join(out,'sealed-holdout.mjs'));
 if(sha(holdout)!==freeze.holdoutSha256)throw Error('HOLDOUT_CHANGED');
 const wt=path.join(out,'independent-fresh');git(root,['worktree','add','--detach',wt,task.sourceCommit]);
 try{
  git(wt,['apply','--check',path.join(out,'model-generated.patch')]);git(wt,['apply',path.join(out,'model-generated.patch')]);
  const changed=git(wt,['diff','--name-only']).stdout.trim().split('\n');
  if(changed.length!==1||changed[0]!==task.allowedPaths[0])throw Error('PATCH_SCOPE_CHANGED');
  fs.writeFileSync(path.join(wt,'.github/continuum/sealed.test.mjs'),holdout);
  git(wt,['add','.github/continuum/sealed.test.mjs']);
  const reports=path.join(out,'fresh-reports');fs.mkdirSync(reports);
  const result=runChain('node',wt,reports,{executor,executorAbortMode:'CHECKPOINT_BOUNDARY_ONLY',deadline:Date.now()+60000});
  write('fresh-verification.json',{...result,holdoutSha256:sha(holdout),patchSha256:sha(fs.readFileSync(path.join(out,'model-generated.patch'))),
   sourceUnchanged:git(root,['status','--porcelain']).stdout.trim()===''});
  console.log(JSON.stringify({status:result.passed?'FRESH_HOLDOUT_PASS':'FRESH_HOLDOUT_FAIL',tests:result.results.map(r=>r.tests)}));
  if(!result.passed)process.exitCode=1;
 }finally{git(root,['worktree','remove','--force',wt]);}
}
