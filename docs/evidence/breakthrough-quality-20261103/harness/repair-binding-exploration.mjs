// Post-freeze exploratory consistency checks. These are not new blind-score cases.
import fs from 'node:fs';import crypto from 'node:crypto';import {spawnSync} from 'node:child_process';import {pathToFileURL} from 'node:url';
const [resultFile,outFile]=process.argv.slice(2);const prior=JSON.parse(fs.readFileSync(resultFile));const repo=prior.cases.find(x=>x.scenario==='UNCHANGED_POSITIVE').repo;
const g=(...args)=>{const r=spawnSync('git',['-C',repo,...args],{encoding:'utf8'});if(r.status)throw Error(r.stderr);return r.stdout.trim();};
const base=g('rev-parse','HEAD^^'),red=g('rev-parse','HEAD^'),green=g('rev-parse','HEAD');const patchPath=repo+'/valid.patch';const hash=crypto.createHash('sha256').update(fs.readFileSync(patchPath)).digest('hex');const rows=[];
for(const label of ['baseline','candidate']){
 const api=await import(pathToFileURL('/workspace/qa-autonomy-consumed-followup-fresh/'+label+'.mjs'));const r=api.runNodeProfile(repo,red),v=api.runNodeProfile(repo,green);const targets=[...r.cases].filter(([,x])=>x.failed).map(([id])=>id);
 const run=(o,sha,greenSide)=>({sha,command:'node-redgreen',executed:o.executed,failed:o.failed,skipped:0,failingTests:greenSide?[]:targets,passedTargets:greenSide?targets:[],exitCode:o.exitCode,timedOut:false,cancelled:false});
 const receipt={schema:'continuum-ccc1-patch-receipt',version:1,baseSha:base,redSha:red,greenSha:green,patchSha256:hash,targetTests:targets,red:run(r,red,false),green:run(v,green,true),approvedForMerge:false,installed:false,deviceVerified:false};
 for(const kind of ['positive','embedded_patch_hash','embedded_red_sha','embedded_green_sha']){
  const c=structuredClone(receipt);if(kind==='embedded_patch_hash')c.patchSha256='0'.repeat(64);if(kind==='embedded_red_sha')c.red.sha='f'.repeat(40);if(kind==='embedded_green_sha')c.green.sha='f'.repeat(40);
  const a=api.verifyRepairPatch({repo,baseCommit:red,patchPath,patchSha256:hash,receiptBytes:Buffer.from(JSON.stringify(c))});rows.push({label,kind,attestation:a});
 }
}
fs.writeFileSync(outFile,JSON.stringify({scope:'Post-freeze exploratory, genuine patch executes; examines embedded receipt field consistency only, not false execution or authentication',cases:rows},null,2)+'\n');console.log(rows.map(x=>[x.label,x.kind,x.attestation.verdict]));
