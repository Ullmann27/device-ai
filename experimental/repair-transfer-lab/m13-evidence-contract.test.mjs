import test from 'node:test';
import assert from 'node:assert/strict';
import {auditRecordedM13} from './m13-evidence-contract.mjs';
const seal='a'.repeat(64),sha='b'.repeat(40);
const t=(module,task,kind='K',success=true)=>({module,task,kind,strategy:'S',success});
const pack=()=>{
 const trainRows=[...Array.from({length:10},(_,i)=>t('trainA','ta'+i,'K',true)),
                  ...Array.from({length:10},(_,i)=>t('trainA','tb'+i,'OTHER',false))];
 const devRows=[...Array.from({length:12},(_,i)=>t('devB','da'+i,'K',true)),
                ...Array.from({length:48},(_,i)=>t('devB','db'+i,'OTHER',false))];
 const hypothesis={id:'H-PREFER-K-S',direction:'PREFER',kind:'K',strategy:'S',status:'CONFIRMED',
  training:{n:10,successes:10,rate:1,baseRate:0.5},
  dev:{n:12,successes:12,rate:1,baseRate:0.2,pOneSided:0}};
 return {summary:{schema:1,sourceSha:sha,seal,executions:100,trainCandidates:20,devCandidates:60,
   hypotheses:1,byStatus:{CONFIRMED:1,REFUTED:0,INCONCLUSIVE:0}},
   hypotheses:[hypothesis],trainRows,devRows,expectedSeal:seal,expectedSourceSha:sha};
};
test('accepts structurally corroborated recorded evidence only',()=>{
 const r=auditRecordedM13(pack());assert.equal(r.valid,true);assert.equal(r.confirmed,1);
});
test('missing/mismatched source identity cannot be confirmed',()=>{
 const p=pack();delete p.expectedSourceSha;assert.equal(auditRecordedM13(p).valid,false);
 assert.equal(auditRecordedM13({...p,expectedSourceSha:'0'.repeat(40)}).valid,false);
});
test('a fabricated summary counter is rejected',()=>{
 const p=pack();p.summary.byStatus.CONFIRMED=2;
 assert.equal(auditRecordedM13(p).reason,'STATUS_COUNTER_MISMATCH');
});
test('reusing training code as dev evidence is disallowed',()=>{
 const p=pack();p.devRows[0].module='trainA';
 assert.equal(auditRecordedM13(p).reason,'TRAIN_DEV_MODULE_OVERLAP');
});
test('missing dev trials are rejected',()=>{
 const p=pack();p.devRows.pop();p.summary.devCandidates--;
 assert.equal(auditRecordedM13(p).valid,false);
});
test('claimed p-value cannot substitute for actual recorded outcomes',()=>{
 const p=pack();for(let i=0;i<11;i++)p.devRows[i].success=false;
 p.hypotheses[0].dev.successes=1;p.hypotheses[0].dev.rate=1/12;p.hypotheses[0].dev.baseRate=1/60;
 assert.equal(auditRecordedM13(p).reason,'CONFIRMATION_NOT_STATISTICALLY_REPRODUCED');
});
test('zero executed runs cannot be called verified',()=>{
 const p=pack();p.summary.executions=0;
 assert.equal(auditRecordedM13(p).valid,false);
});
test('valid counts cannot repair a forged hypothesis test count',()=>{
 const p=pack();p.hypotheses[0].dev.n=100;
 assert.equal(auditRecordedM13(p).reason,'HYPOTHESIS_ROW_COUNT_MISMATCH');
});
