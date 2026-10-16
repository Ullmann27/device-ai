#!/usr/bin/env node
/** Continuum offline Teamleader CLI. No GitHub token or cloud endpoint. */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {runTeamleaderCycle,localTeamleaderDriver,decodeLeaderState} from './autonomous-teamleader.mjs';

export function parseTeamleaderArgs(args){
 const allowed=new Set(['--root','--state','--budget-minutes','--once','--status','--retry']);
 const opts={once:false,status:false,retry:false};
 for(let i=0;i<args.length;i++){
  const key=args[i];
  if(!allowed.has(key))throw Error('Unknown Teamleader option '+key);
  if(['--once','--status','--retry'].includes(key)){
   if(opts[key.slice(2)])throw Error('Duplicate option '+key);
   opts[key.slice(2)]=true;
  }else{
   if(opts[key.slice(2).replace(/-([a-z])/g,(_,ch)=>ch.toUpperCase())])
    throw Error('Duplicate option '+key);
   const val=args[++i];
   if(!val||val.startsWith('--'))throw Error('Missing '+key+' argument');
   opts[key.slice(2).replace(/-([a-z])/g,(_,ch)=>ch.toUpperCase())]=val;
  }
 }
 if(opts.once===opts.status)throw Error('Choose exactly one of --once or --status');
 if(opts.retry&&!opts.once)throw Error('--retry requires --once');
 if(opts.budgetMinutes!==undefined&&
   (!/^\d+$/.test(opts.budgetMinutes)||Number(opts.budgetMinutes)<1||
    Number(opts.budgetMinutes)>30))throw Error('Budget 1..30 minutes required');
 return opts;
}
export async function teamleaderMain(argv=process.argv.slice(2)){
 const opt=parseTeamleaderArgs(argv);
 const root=fs.realpathSync(path.resolve(opt.root??process.cwd()));
 const suffix=crypto.createHash('sha256').update(root).digest('hex').slice(0,12);
 const stateDir=path.resolve(opt.state??path.join(os.homedir(),'.local','state','device-ai-continuum',suffix));
 if(stateDir===root||stateDir.startsWith(root+path.sep))
  throw Error('State must remain outside the source checkout');
 if(opt.status){
  const statePath=path.join(stateDir,'teamleader-state.json');
  const state=decodeLeaderState(fs.existsSync(statePath)?fs.readFileSync(statePath):null);
  const lastCompleted=state.events.filter(x=>x.outcome!=='RUNNING').at(-1)??null;
  const output={mode:'OFFLINE_TEAMLEADER',stateDir,processedHeads:state.attemptedHeads.length,
   lastCompleted,recent:state.events.slice(-12)};
  process.stdout.write(JSON.stringify(output,null,2)+'\n');
  return output;
 }
 const driver=await localTeamleaderDriver();
 const result=runTeamleaderCycle({root,stateDir,driver,
  budgetMinutes:Number(opt.budgetMinutes??20),retry:opt.retry,log:console.log});
 process.stdout.write(JSON.stringify(result,null,2)+'\n');
 if(result.outcome==='ERROR'||result.outcome==='CHECKS_FAILED')
  process.exitCode=1;
 return result;
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href)
 teamleaderMain().catch(err=>{console.error('Teamleader stopped:',err.message);process.exitCode=1;});
