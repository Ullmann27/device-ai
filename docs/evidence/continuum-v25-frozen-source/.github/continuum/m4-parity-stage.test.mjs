import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {runAndroidParityStage} from './m4-local-gate.mjs';

const KEYS=['CONTINUUM_BACKLOG_ROUNDTRIP_DIR','CONTINUUM_CONTRASTIVE_PARITY_DIR','CONTINUUM_CRE1_PARITY_DIR'];
function sandbox(fn){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-parity-gate-'));
 try{return fn(dir);}finally{fs.rmSync(dir,{recursive:true,force:true});}
}

test('Android M4 stage prepares three parity contracts, supplies env and verifies outputs',()=>sandbox(dir=>{
 const prior=Object.fromEntries(KEYS.map(k=>[k,process.env[k]]));
 const steps=[];
 const executor=(cmd,args,options)=>{
  assert.equal(cmd,process.execPath);
  assert.equal(options.cwd,dir);
  assert.equal(options.shell,false);
  steps.push({script:path.basename(args[0]),phase:args[1],output:args[2]});
  return {status:0,stdout:'ok',stderr:''};
 };
 const android={passed:true,results:[{profile:'android-compile'},{profile:'android-unit'}]};
 const result=runAndroidParityStage({wt:dir,out:path.join(dir,'proofs'),deadline:Date.now()+60000,
  executor,runAndroid:()=>{
   for(const key of KEYS){
    assert.ok(process.env[key]?.startsWith(path.join(dir,'proofs','parity')),
      key+' must be visible to the real Kotlin child process');
   }
   return android;
  }});
 assert.equal(result,android);
 assert.deepEqual(steps.map(s=>s.phase),[
  'roundtrip-prepare','prepare','prepare',
  'roundtrip-verify','verify','verify'
 ]);
 assert.ok(steps.every(s=>s.output.startsWith(path.join(dir,'proofs','parity'))));
 for(const key of KEYS)assert.equal(process.env[key],prior[key],
  'restore process env after stage');
}));

test('parity verification failure vetoes Android certification and restores prior env',()=>sandbox(dir=>{
 const prev=process.env.CONTINUUM_CRE1_PARITY_DIR;
 process.env.CONTINUUM_CRE1_PARITY_DIR='existing-user-config';
 let calls=0;
 try {
  assert.throws(()=>runAndroidParityStage({
   wt:dir,out:path.join(dir,'proofs'),deadline:Date.now()+60000,
   executor:(_cmd,args)=>{
    calls++;
    return args[1]==='verify'&&args[0].endsWith('cre-parity.mjs')
      ?{status:1,stderr:'CRE1 mismatch'}:{status:0,stderr:''};
   },
   runAndroid:()=>({passed:true,results:[]})
  }),/PARITY_VERIFY.*cre-parity\.mjs/);
  assert.equal(calls,6);
  assert.equal(process.env.CONTINUUM_CRE1_PARITY_DIR,'existing-user-config');
 }finally{
  if(prev===undefined)delete process.env.CONTINUUM_CRE1_PARITY_DIR;
  else process.env.CONTINUUM_CRE1_PARITY_DIR=prev;
 }
}));

test('a failed Android profile never advertises successful parity verification',()=>sandbox(dir=>{
 let verifications=0;
 const result=runAndroidParityStage({
  wt:dir,out:path.join(dir,'proofs'),deadline:Date.now()+60000,
  executor:(_cmd,args)=>{if(args[1].includes('verify'))verifications++;return{status:0};},
  runAndroid:()=>({passed:false,results:[]})
 });
 assert.equal(result.passed,false);
 assert.equal(verifications,0);
}));

test('missing parity preparation blocks the Kotlin runner entirely',()=>sandbox(dir=>{
 let androidRan=false;
 assert.throws(()=>runAndroidParityStage({
  wt:dir,out:path.join(dir,'proofs'),deadline:Date.now()+60000,
  executor:()=>({status:1,stderr:'data preparation failed'}),
  runAndroid:()=>{androidRan=true;return {passed:true};}
 }),/PARITY_PREPARE/);
 assert.equal(androidRan,false);
}));
