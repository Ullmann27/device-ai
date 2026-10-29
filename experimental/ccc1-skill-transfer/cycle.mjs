/** Integration supplement: existing real teamleader + ledger + OS executor, then restart. */
import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';
import {localTeamleaderDriver,runTeamleaderCycle} from '../../.github/continuum/autonomous-teamleader.mjs';
import {createIsolatedExecutor} from '../../.github/continuum/isolated-executor.mjs';
const [inputArg,outArg,phase]=process.argv.slice(2);
if(!inputArg||!outArg)throw Error('Usage: node cycle.mjs <completed-transfer-output> <fresh-external-output>');
const input=fs.realpathSync(inputArg),out=path.resolve(outArg),script=fileURLToPath(import.meta.url),source=path.resolve(path.dirname(script),'../..');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const git=(root,args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});if(r.status!==0)throw Error('GIT_REFUSED');return r.stdout.trim();};
const root=path.join(input,'scoped-B1/B1/repo'),stateDir=path.join(out,'state'),ledgerFile=path.join(stateDir,'repair-ledger.json');
if(phase){
 if(!['first','restarted'].includes(phase))throw Error('UNKNOWN_PHASE');
 const before=hash(fs.readFileSync(ledgerFile));
 const executor=createIsolatedExecutor({image:'sha256:8da84df25d28176be84164040cd45e128c6b4c6a80b7d7cbe17fe2b9f198efc2',
  nodeHome:path.dirname(path.dirname(fs.realpathSync(process.execPath))),memoryMb:512,pids:32,cpus:1,timeoutMs:30000});
 const driver=await localTeamleaderDriver({executor,contextualExperience:true});
 const result=runTeamleaderCycle({root,stateDir,driver,budgetMinutes:2});
 const report=result.artifact?JSON.parse(fs.readFileSync(result.artifact,'utf8')):null;
 const record={phase,pid:process.pid,result,firstStrategy:report?.attempts?.[0]?.strategy??null,attempts:report?.attempts?.length??0,
  independentPassed:report?.independent?.passed===true,ledgerBefore:before,ledgerAfter:hash(fs.readFileSync(ledgerFile)),
  sourceUnchanged:git(root,['status','--porcelain'])===''};
 fs.writeFileSync(path.join(out,phase+'.json'),JSON.stringify(record,null,2)+'\n');
}else{
 if(fs.existsSync(out)||out===source||out.startsWith(source+path.sep))throw Error('FRESH_EXTERNAL_OUTPUT_REQUIRED');
 if(git(source,['status','--porcelain','--untracked-files=normal']))throw Error('CLEAN_SOURCE_REQUIRED');
 const sourceCommit=git(source,['rev-parse','HEAD']);
 const frozen=JSON.parse(fs.readFileSync(path.join(input,'report.json'),'utf8'));
 if(frozen.outcome!=='SELECTED_TASK_TRANSFER_PASS'||frozen.tasksSha256!==hash(fs.readFileSync(new URL('./tasks.json',import.meta.url))))throw Error('FROZEN_TASKS_REQUIRED');
 fs.mkdirSync(stateDir,{recursive:true});
 fs.copyFileSync(path.join(input,'train-scoped/learned.json'),ledgerFile);fs.chmodSync(ledgerFile,0o600);
 const initial=fs.readFileSync(ledgerFile);
 for(const step of ['first','restarted']){
  const child=spawnSync(process.execPath,[script,input,out,step],{encoding:'utf8',timeout:120000,maxBuffer:1000000,killSignal:'SIGKILL'});
  fs.writeFileSync(path.join(out,step+'.log'),child.stdout+child.stderr);
  if(child.status!==0)throw Error('CYCLE_CHILD_FAILED '+child.stderr.slice(-1200));
 }
 const a=JSON.parse(fs.readFileSync(path.join(out,'first.json'))),b=JSON.parse(fs.readFileSync(path.join(out,'restarted.json')));
 const passed=a.result.outcome==='VERIFIED_PATCH_PENDING_REVIEW'&&a.firstStrategy==='JS_TYPO_REFERENCE'&&a.attempts===1&&a.independentPassed&&
  b.result.outcome==='IDLE'&&a.pid!==b.pid&&a.ledgerAfter===b.ledgerAfter&&a.sourceUnchanged&&b.sourceUnchanged;
 if(git(source,['rev-parse','HEAD'])!==sourceCommit||git(source,['status','--porcelain','--untracked-files=normal']))throw Error('SOURCE_CHANGED');
 const summary={sourceCommit,inputCodeCommit:frozen.sourceCommit,status:passed?'PASS':'FAIL',first:a,restarted:b,
  originalTrainingLedgerUnchanged:hash(initial)===hash(fs.readFileSync(path.join(input,'train-scoped/learned.json'))),
  releaseOrMergeAuthorized:false,modelInference:false};
 fs.writeFileSync(path.join(out,'summary.json'),JSON.stringify(summary,null,2)+'\n');console.log(JSON.stringify(summary,null,2));
 if(!passed)process.exitCode=1;
}
