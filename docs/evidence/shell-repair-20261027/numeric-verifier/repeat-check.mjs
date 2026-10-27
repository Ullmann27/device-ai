import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=process.argv[2],out=process.argv[3];
const {createNumericVerifier}=await import(pathToFileURL(path.join(root,'experimental/omega/repair/numeric-verifier.mjs')));
const {runCandidateVerifier}=await import(pathToFileURL(path.join(root,'.github/continuum/repair-research-verifier.mjs')));
const base='a'.repeat(40),source='export function distance(a,b){return a>b?a-b:b-a;}';
const suite={schema:'omega-numeric-suite',version:1,sourceCommit:base,file:'lib/math.mjs',exportName:'distance',arity:2,
 cases:Array.from({length:256},(_,i)=>({id:'c-'+i,args:[i,i+i%2],expected:i%2})),
 relations:Array.from({length:128},(_,i)=>({id:'r-'+i,left:[i,i+1],right:[i+10,i+11],relation:'equal'}))};
const verifier=createNumericVerifier(suite),runs=[];
for(let i=0;i<10;i++){
 const start=performance.now();const r=runCandidateVerifier(verifier,{base,deadline:Date.now()+5000,sources:[{file:suite.file,source}]});
 runs.push({index:i,elapsedMillis:performance.now()-start,status:r.status,checks:r.checks});
}
const report={sourceSha:execFileSync('git',['-C',root,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),
 description:'Ten repeated runs of the SAME384frozen checks while the configured worker/scripts execute; not3840novel tests or neural generalization.',runs};
fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report));if(runs.some(r=>r.status!=='PASS'||r.checks!==384))process.exitCode=1;
