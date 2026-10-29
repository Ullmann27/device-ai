import fs from 'node:fs';import path from 'node:path';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';
const script=fileURLToPath(new URL('./worker.mjs',import.meta.url));
const source=path.resolve(path.dirname(script),'../..');
function sourceState(){
 const head=spawnSync('git',['rev-parse','HEAD'],{cwd:source,encoding:'utf8'});
 const dirty=spawnSync('git',['status','--porcelain','--untracked-files=normal'],{cwd:source,encoding:'utf8'});
 if(head.status!==0||dirty.status!==0||dirty.stdout.trim())throw Error('CLEAN_COMMITTED_EXPERIMENT_SOURCE_REQUIRED');
 return head.stdout.trim();
}
const sourceCommit=sourceState();
const out=path.resolve(process.argv[2]??'');if(process.argv.length!==3||fs.existsSync(out))throw Error('FRESH_EXTERNAL_OUTPUT_REQUIRED');
if(out===source||out.startsWith(source+path.sep))throw Error('EXTERNAL_OUTPUT_REQUIRED');
fs.mkdirSync(out,{recursive:true});const deadline=Date.now()+600_000,children=[];
function child(mode,id='',ledger=''){
 const target=path.join(out,mode+(id?'-'+id:''));
 const result=spawnSync(process.execPath,[script,target,mode,id,ledger],{encoding:'utf8',timeout:Math.min(120_000,deadline-Date.now()),maxBuffer:2_000_000,killSignal:'SIGKILL'});
 fs.writeFileSync(path.join(out,mode+(id?'-'+id:'')+'.log'),result.stdout+result.stderr);
 if(result.status!==0)throw Error('EXPERIMENT_CHILD_FAILED '+mode+' '+id+' '+result.stderr.slice(-1200));
 const summary=JSON.parse(fs.readFileSync(path.join(target,'summary.json'),'utf8'));children.push(summary);return target;
}
const broad=child('train-broad'),scoped=child('train-scoped');
for(const id of ['B1','B2','C1','C2'])for(const mode of ['baseline','broad','scoped','misleading'])
 child(mode,id,mode==='broad'?path.join(broad,'learned.json'):mode==='baseline'?'':path.join(scoped,'learned.json'));
child('forged-matching','B1',path.join(scoped,'learned.json'));
const table=children.flatMap(c=>c.rows.map(r=>({mode:c.mode,task:r.task,pid:r.pid,verified:r.verified,attempts:r.attempts,firstStrategy:r.firstStrategy,elapsedMs:r.elapsedMs,driverCpuMicros:r.driverCpuMicros,patchSha256:r.patchSha256})));
const count=(mode,prefix)=>table.filter(r=>r.mode===mode&&r.task.startsWith(prefix)&&r.verified).length;
if(sourceState()!==sourceCommit)throw Error('EXPERIMENT_SOURCE_CHANGED');
const report={schema:1,sourceCommit,
 tasksSha256:createHash('sha256').update(fs.readFileSync(new URL('./tasks.json',import.meta.url))).digest('hex'),
 table,childProcesses:children.map(c=>({pid:c.pid,mode:c.mode,rankingStateBytes:c.rankingStateBytes,rankingStateHash:c.rankingStateHash,
  storedLedgerBytes:c.storedLedgerBytes,storedLedgerSha256:c.storedLedgerSha256})),
 outcome:count('scoped','B')===2&&count('scoped','B')>count('baseline','B')&&
  count('scoped','C')===2&&count('baseline','C')===2?'SELECTED_TASK_TRANSFER_PASS':'SELECTED_TASK_TRANSFER_NOT_PROVEN',
 limitations:['Deterministic strategy preference, no neural or model training','Two selected similar tasks, not broad generalization','Driver CPU excludes container CPU; no speedup claim','Forged matching-context statistics can still misrank; fixed independent tests remain mandatory','Default runtime unchanged; explicit research opt-in only']};
fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
