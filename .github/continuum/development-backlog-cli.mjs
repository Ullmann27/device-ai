import fs from 'node:fs';
import path from 'node:path';
import * as b from './development-backlog.mjs';
import * as loop from './repair-loop.mjs';
import {planBacklog} from './development-planner.mjs';
export const BACKLOG_HELP=`Persistent developer backlog (no implicit execution or publication).
backlog-list [--status STATE] | backlog-status | backlog-select [--id ID]
backlog-add --title TEXT [--kind KIND --file PATH --base SHA --fingerprint HEX --chain node|android|jvm]
backlog-plan [--limit 12] | backlog-merge --input PLANNER_JSON
backlog-prioritize --id ID --benefit 0..100 --risk 0..100 --effort 0..100
backlog-claim --id ID --owner NAME --approve true
backlog-start --id ID --owner NAME --base SHA --chain CHAIN --approve true
backlog-reject --id ID | backlog-reopen --id ID | backlog-recover
backlog-export | backlog-import --input FILE [--resolve local|incoming --generation N]
All commands use --out DIRECTORY. Imports are data, never execution consent.
DONE requires a matching diagnostic goal and actual tests in an isolated Git worktree.
Exit 0: success; exit 1: rejected, conflict, invalid data or execution error.`;
export async function backlogCommand(cmd,o,{root,out,ledger,log}){
 const file=path.join(out,'development-backlog.json');
 const options={expectedGeneration:o.generation===undefined?undefined:Number(o.generation)};
 if(o.generation!==undefined&&(!Number.isSafeInteger(options.expectedGeneration)||options.expectedGeneration<0))throw Error('Invalid generation');
 const tx=fn=>b.transaction(file,fn,options);
 if(cmd==='backlog-list')return b.listTasks(b.readBacklog(file),o.status);
 if(cmd==='backlog-status'){const s=b.readBacklog(file);return {generation:s.generation,counts:Object.fromEntries(['OPEN','CLAIMED','BLOCKED','DONE','REJECTED'].map(x=>[x,b.listTasks(s,x).length])),tasks:s.tasks};}
 if(cmd==='backlog-select'){const s=b.readBacklog(file),t=o.id?s.tasks.find(x=>x.id===o.id):b.listTasks(s,'OPEN')[0];if(!t)throw Error('No matching task');return t;}
 if(cmd==='backlog-recover')return b.recoverBacklog(file);
 if(cmd==='backlog-export'){const destination=path.join(out,'development-backlog-bridge.json');fs.writeFileSync(destination,b.exportBridge(b.readBacklog(file)),{mode:0o600});return {destination};}
 const input=()=>{if(!o.input||fs.statSync(o.input).size>3_000_000)throw Error('Bounded --input required');return fs.readFileSync(o.input,'utf8');};
 if(cmd==='backlog-import'){
  if(o.resolve&&(!['local','incoming'].includes(o.resolve)||o.generation===undefined))throw Error('Explicit resolution requires --resolve local|incoming and --generation');
  const incoming=b.importBridge(input());return tx(s=>b.mergeBacklog(s,incoming,{resolve:o.resolve}));
 }
 if(cmd==='backlog-plan'||cmd==='backlog-merge')return tx(s=>b.mergePlanner(s,cmd==='backlog-plan'?planBacklog(root,Number(o.limit??12)):JSON.parse(input())));
 if(cmd==='backlog-add'||cmd==='backlog-record')return tx(s=>b.addTask(s,{title:o.title,file:o.file,kind:o.kind??'MANUAL',symbol:o.symbol,source:cmd==='backlog-add'?'MANUAL':'AGENT',benefit:o.benefit,risk:o.risk,effort:o.effort,baseCommit:o.base??'',criteria:o.fingerprint?{type:'REPAIR_DIAGNOSTIC',fingerprint:o.fingerprint,chain:o.chain}:undefined}));
 if(cmd==='backlog-prioritize')return tx(s=>b.prioritize(s,o.id,o));
 if(cmd==='backlog-reject'||cmd==='backlog-reopen')return tx(s=>b.changeStatus(s,o.id,cmd==='backlog-reject'?'REJECTED':'OPEN',{note:o.note}));
 if(cmd==='backlog-claim'){
  if(o.approve!=='true'||!o.owner)throw Error('Explicit --approve true and --owner required');
  return tx(s=>b.changeStatus(s,o.id??b.listTasks(s,'OPEN')[0]?.id,'CLAIMED',{owner:o.owner}));
 }
 if(cmd==='backlog-update'){
  if(['CLAIMED','DONE'].includes(o.status))throw Error('Direct status escalation prohibited; use claim/start');
  return tx(s=>b.changeStatus(s,o.id,o.status,{note:o.note}));
 }
 if(cmd==='backlog-start'){
  if(o.approve!=='true')throw Error('Explicit --approve true required');
  // Claim validation and run ownership are committed together before any execution.
  const started=tx(s=>{
   const t=s.tasks.find(x=>x.id===o.id);
   if(!t||t.status!=='CLAIMED'||!o.owner||t.owner!==o.owner)throw Error('Claim owner mismatch');
   if(!['BUILD_FAILURE','TEST_FAILURE','WORKFLOW_FAILURE'].includes(t.kind)||t.criteria.type!=='REPAIR_DIAGNOSTIC')throw Error('No task-specific execution strategy or goal');
   if(t.baseCommit!==o.base||t.criteria.chain!==o.chain||!loop.CHAINS[o.chain])throw Error('Task base/chain mismatch');
   // Bump the task revision to bind the receipt and detect changes during the run.
   b.prioritize(s,t.id,{});return s;
  });
  const claimed=started.tasks.find(x=>x.id===o.id), claimHash=b.taskHash(claimed);
  let report,failure;
  const runDir=path.join(out,'runs',claimed.id+'-r'+claimed.revision);
  try{const wt=loop.createWorktree(root,o.base);try{
    report=loop.repairSession({wt,base:o.base,chain:o.chain,out:runDir,ledger,log,deadline:Date.now()+15*60000});
   }finally{loop.removeWorktree(root,wt);}}
  catch(e){failure=e;}
  if(report)fs.writeFileSync(path.join(runDir,'report.json'),JSON.stringify(report,null,2)+'\n');
  const result=b.transaction(file,s=>{
   const t=s.tasks.find(x=>x.id===o.id);if(!t||b.taskHash(t)!==claimHash)throw Error('Task changed during execution; report retained, status not overwritten');
   if(!failure&&report?.outcome==='VERIFIED'){
    try{return b.completeRepair(s,o.id,report);}catch(e){failure=e;}
   }
   return b.changeStatus(s,o.id,'BLOCKED',{note:failure?'Repair failed: '+failure.message:'Goal not verified: '+report?.outcome});
  });
  if(failure)throw failure;return {task:result.tasks.find(x=>x.id===o.id),reportDirectory:runDir,outcome:report.outcome};
 }
 throw Error('Unknown backlog command '+cmd);
}
