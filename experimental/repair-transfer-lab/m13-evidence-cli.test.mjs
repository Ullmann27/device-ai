import { spawnSync } from 'node:child_process';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {checkRecordedDirectory} from './m13-evidence-cli.mjs';
const root=()=>fs.mkdtempSync(path.join(os.tmpdir(),'continuum-m13-contract-'));
const fill=(dir)=>{
 const seal='a'.repeat(64),sha='b'.repeat(40);
 fs.writeFileSync(path.join(dir,'summary.json'),JSON.stringify({schema:1,seal,sourceSha:sha,executions:1,trainCandidates:0,devCandidates:0,hypotheses:0,byStatus:{CONFIRMED:0,REFUTED:0,INCONCLUSIVE:0}}));
 for(const name of ['hypotheses.json','train-rows.json','dev-rows.json'])fs.writeFileSync(path.join(dir,name),'[]');
 return {seal,sha};
};
test('reads complete regular outputs without modifying them',()=>{
 const dir=root();try{const {seal,sha}=fill(dir);const result=checkRecordedDirectory(dir,sha,seal);assert.equal(result.valid,true);assert.equal(result.moduleReplication.moduleReplicated,0);assert.equal(result.moduleReplication.trainingAuthorized,false)}finally{fs.rmSync(dir,{recursive:true,force:true})}
});
test('refuses an evidence symlink, not following outside file content',()=>{
 const dir=root();try{const {seal,sha}=fill(dir);fs.renameSync(path.join(dir,'summary.json'),path.join(dir,'real.json'));fs.symlinkSync(path.join(dir,'real.json'),path.join(dir,'summary.json'));
 assert.throws(()=>checkRecordedDirectory(dir,sha,seal));}finally{fs.rmSync(dir,{recursive:true,force:true})}
});
test('rejects named pipes before reading, without blocking',()=>{
 const dir=root();try{const {seal,sha}=fill(dir);fs.unlinkSync(path.join(dir,'summary.json'));
 const r=spawnSync('mkfifo',[path.join(dir,'summary.json')]);
 if(r.error||r.status!==0) return;
 assert.throws(()=>checkRecordedDirectory(dir,sha,seal),/INVALID_EVIDENCE_FILE/);
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});
