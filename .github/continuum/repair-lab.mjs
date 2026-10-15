import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {collectKotlin} from './code-analyzer.mjs';
import {knownFixes} from './repair-engine.mjs';

/** Offline deterministic repair laboratory. No network, LLM, or code execution from logs.
 * Tries a known fix in an isolated temporary source tree, then checks compilation.
 * Original repository remains untouched. */
export function chooseCandidates(files) {
 const result=[];
 for(const file of files){
  for(const fix of knownFixes(file.content)){
   if(file.content.split(fix.from).length!==2)continue;
   result.push({file:file.path,kind:fix.kind,from:fix.from,to:fix.to});
  }
 }
 return result.slice(0,10);
}
export function applyCandidate(source,candidate){
 if(source.split(candidate.from).length!==2)throw Error('Ambiguous replacement');
 return source.replace(candidate.from,candidate.to);
}
export function runLab(root,{testCommand=['./gradlew',':app:compileDebugUnitTestKotlin','--offline','--no-daemon'],execute=true}={}){
 const files=collectKotlin(path.join(root,'app/src'));
 const candidates=chooseCandidates(files);
 const report={schemaVersion:1,mode:execute?'test':'dry-run',candidates:[],appliedToRepository:false};
 if(!execute)return {...report,candidates:candidates.map(x=>({...x,status:'PROPOSED'}))};
 if(!candidates.length)return report;
 const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-repair-'));
 try{
  const copy=spawnSync('cp',['-a',root+'/.',tmp+'/'],{timeout:60000,encoding:'utf8',shell:false});
  if(copy.status!==0)throw Error('Isolated source copy failed');
  for(const candidate of candidates){
   const target=path.resolve(tmp,'app/src',candidate.file);
   if(!target.startsWith(path.resolve(tmp,'app/src')+path.sep))throw Error('Unsafe candidate path');
   const original=fs.readFileSync(target,'utf8');
   fs.writeFileSync(target,applyCandidate(original,candidate));
   const run=spawnSync(testCommand[0],testCommand.slice(1),{cwd:tmp,timeout:180000,encoding:'utf8',maxBuffer:1000000,shell:false,
     env:{...process.env,GRADLE_USER_HOME:path.join(tmp,'.gradle-agent')}});
   report.candidates.push({...candidate,status:run.status===0?'COMPILES':'REJECTED',
     exitCode:run.status,diagnostic:(run.stderr??'').slice(-1000)});
   fs.writeFileSync(target,original);
  }
 }finally{fs.rmSync(tmp,{recursive:true,force:true,maxRetries:2})}
 return report;
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
 const report=runLab(process.cwd(),{execute:process.argv.includes('--execute')});
 fs.mkdirSync('developer-agent-report',{recursive:true});
 fs.writeFileSync('developer-agent-report/repair-lab.json',JSON.stringify(report,null,2)+'\n');
 console.log('Repair lab:',report.candidates.length,'candidates;',report.candidates.filter(x=>x.status==='COMPILES').length,'compiled');
}
