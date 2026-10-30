import test from 'node:test';
import assert from 'node:assert/strict';
import {auditM13ModuleReplication} from './m13-module-replication.mjs';

const hypothesis=(id='H',direction='PREFER',status='CONFIRMED')=>({id,direction,status,kind:'BOUNDARY',strategy:'slice'});
const row=(module,kind,success,task='t')=>({module,task,kind,strategy:'slice',success});
const testInputs=(moduleCount=6,positive=moduleCount)=>{
  const trainRows=[row('training','BOUNDARY',true)];
  const devRows=[];
  for(let i=0;i<moduleCount;i++) {
    const module='m-'+i;
    devRows.push(row(module,'BOUNDARY',i<positive,'case-'+i));
    devRows.push(row(module,'OTHER',i>=positive,'reference-'+i));
  }
  return {hypotheses:[hypothesis()],trainRows,devRows};
};

test('six genuinely distinct positive modules meet one-sided research threshold',()=>{
  const x=auditM13ModuleReplication(testInputs());
  assert.equal(x.moduleReplicated,1);
  assert.ok(Math.abs(x.decisions[0].oneSidedP-1/64)<1e-12);
  assert.ok(Math.abs(x.decisions[0].holmAdjustedP-1/64)<1e-12);
  assert.equal(x.decisions[0].ownerReviewOnly,true);
  assert.equal(x.trainingAuthorized,false);
});
test('one module with thousands of green candidates is not independent replication',()=>{
  const p=testInputs(1);
  for(let i=0;i<1000;i++)p.devRows.push(row('m-0','BOUNDARY',true,'test-'+i));
  const x=auditM13ModuleReplication(p);
  assert.equal(x.moduleReplicated,0);
  assert.equal(x.decisions[0].status,'INSUFFICIENT_INDEPENDENT_MODULES');
  assert.equal(x.decisions[0].modulesCompared,1);
});
test('five modules cannot meet the preregistered minimum',()=>{
  const x=auditM13ModuleReplication(testInputs(5));
  assert.equal(x.decisions[0].oneSidedP,null);
});
test('one contradicting module blocks claimed strong replication',()=>{
  const x=auditM13ModuleReplication(testInputs(6,5));
  assert.equal(x.decisions[0].status,'NOT_REPLICATED');
  assert.ok(Math.abs(x.decisions[0].oneSidedP-7/64)<1e-12);
});
test('Holm adjustment protects against cherry-picking four simultaneous hypotheses',()=>{
  const p=testInputs(6);
  p.hypotheses=[hypothesis('H1'),hypothesis('H2'),hypothesis('H3'),hypothesis('H4')];
  const x=auditM13ModuleReplication(p);
  assert.equal(x.moduleReplicated,0);
  assert.ok(Math.abs(x.decisions[0].holmAdjustedP-4/64)<1e-12);
});
test('seven unanimous modules can survive four-way Holm adjustment',()=>{
  const p=testInputs(7);
  p.hypotheses=[hypothesis('H1'),hypothesis('H2'),hypothesis('H3'),hypothesis('H4')];
  const x=auditM13ModuleReplication(p);
  assert.equal(x.moduleReplicated,4);
  assert.ok(Math.abs(x.decisions[0].holmAdjustedP-4/128)<1e-12);
});
test('train/dev shared module blocks all apparent confirmations',()=>{
  const p=testInputs();p.devRows[0].module='training';
  assert.throws(()=>auditM13ModuleReplication(p),/M13_TRAIN_DEV_MODULE_LEAK/);
});
test('same diagnosis only, with no within-module reference, cannot support a sign comparison',()=>{
  const p=testInputs();p.devRows=p.devRows.filter(r=>r.kind==='BOUNDARY');
  assert.equal(auditM13ModuleReplication(p).decisions[0].modulesCompared,0);
});
test('DEMOTE direction finds opposite consistently bad module outcomes',()=>{
  const p=testInputs(6,0);p.hypotheses=[hypothesis('H','DEMOTE')];
  assert.equal(auditM13ModuleReplication(p).moduleReplicated,1);
});
test('false original confirmation is not reinterpreted as an accepted finding',()=>{
  const p=testInputs();p.hypotheses=[hypothesis('H','PREFER','REFUTED')];
  const x=auditM13ModuleReplication(p);
  assert.equal(x.decisions[0].status,'NOT_ORIGINALLY_CONFIRMED');
  assert.equal(x.moduleReplicated,0);
});
test('duplicate hypothesis identity and invalid rows are rejected',()=>{
  const p=testInputs();p.hypotheses=[hypothesis(),hypothesis()];
  assert.throws(()=>auditM13ModuleReplication(p),/M13_INVALID_HYPOTHESIS/);
  const q=testInputs();q.devRows[0].success=1;
  assert.throws(()=>auditM13ModuleReplication(q),/M13_INVALID_ROW/);
});
test('ties are not counted as positive observations',()=>{
  const p=testInputs();p.devRows=p.devRows.map(r=>({...r,success:true}));
  const x=auditM13ModuleReplication(p);
  assert.equal(x.decisions[0].tiedModules,6);
  assert.equal(x.decisions[0].status,'INSUFFICIENT_INDEPENDENT_MODULES');
});
