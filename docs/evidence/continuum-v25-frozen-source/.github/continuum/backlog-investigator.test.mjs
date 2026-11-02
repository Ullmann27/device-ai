import test from 'node:test';
import assert from 'node:assert/strict';
import {buildIndex,integrationFindings} from './code-analyzer.mjs';
import {investigateFinding,verifyInvestigation,investigationChecksPassed,chooseReadyInvestigation} from './backlog-investigator.mjs';
import * as B from './development-backlog.mjs';

const files=[
 {path:'main/java/p/App.kt',content:'package p\nclass App { fun live() { Connected().go() } }\nclass Connected { fun go()=1 }'},
 {path:'main/java/p/Lab.kt',content:'package p\nclass Orphan\nclass Isolated'}
];
const index=buildIndex(files);
const findings=integrationFindings(index,{productionRoots:['p.App']}).findings;
const analysis={index,findings};
test('INVESTIGATE worker reproduces findings but never invents wiring patches',()=>{
 const orphan=findings.find(f=>f.symbol==='p.Orphan');
 assert.ok(orphan);
 const task={id:'inv-0000000000000001',kind:'INVESTIGATE',subject:{
  finding:orphan.kind,file:'app/src/'+orphan.file,symbol:orphan.symbol,confidence:orphan.confidence
 }};
 const report=investigateFinding(analysis,task);
 assert.equal(report.kind,'REPRODUCED_STATIC_FINDING');
 assert.equal(report.proposedAction,'REVIEW_WIRING_AND_ADD_RUNTIME_TEST');
 assert.ok(verifyInvestigation(report,analysis,task));
 assert.ok(!verifyInvestigation({...report,kind:'VERIFIED_WIRING'},analysis,task));
});
test('INVESTIGATE declines changes for findings no longer present',()=>{
 const task={id:'inv-0000000000000002',kind:'INVESTIGATE',subject:{
  finding:'UNREFERENCED',file:'app/src/main/java/p/App.kt',symbol:'p.App',confidence:'high'
 }};
 const report=investigateFinding(analysis,task);
 assert.equal(report.kind,'NOT_A_STATIC_FINDING');
 assert.equal(report.proposedAction,'NO_AUTOMATIC_CHANGE');
});
test('only accepted structural INVESTIGATE tasks are eligible',()=>{
 assert.throws(()=>investigateFinding(analysis,{id:'x',kind:'TEST',subject:{}}),/structural/);
});

test('empty or failed test chain cannot complete backlog investigation',()=>{
 assert.equal(investigationChecksPassed({passed:true,tests:{executed:3,failed:0}}),true);
 assert.equal(investigationChecksPassed({passed:true,tests:{executed:0,failed:0}}),false);
 assert.equal(investigationChecksPassed({passed:false,tests:{executed:3,failed:1}}),false);
 assert.equal(investigationChecksPassed({passed:true,tests:{executed:3,failed:1}}),false);
 assert.equal(investigationChecksPassed({passed:true,tests:{}}),false);
 assert.equal(investigationChecksPassed(null),false);
});

test('worker honours Teamleader-selected task and never skips backlog dependencies',()=>{
 let doc=B.emptyDocument(0,'investigator');
 const apply=op=>{doc=B.applyOp(doc,{actor:'investigator',...op}).doc};
 apply({op:'propose',at:1,task:{id:'task-parent',title:'Parent',
   kind:'TEST',source:'cli',priority:900}});
 apply({op:'propose',at:2,task:{id:'inv-high',title:'High',
   kind:'INVESTIGATE',source:'cli',priority:500}});
 apply({op:'propose',at:3,task:{id:'inv-low',title:'Low',
   kind:'INVESTIGATE',source:'cli',priority:100}});
 apply({op:'propose',at:4,task:{id:'inv-blocked',title:'Blocked',
   kind:'INVESTIGATE',source:'cli',priority:1000,
   dependsOn:['task-parent']}});
 for(const id of ['task-parent','inv-high','inv-low','inv-blocked'])
   apply({op:'accept',at:5,id});
 assert.equal(chooseReadyInvestigation(doc).id,'inv-high');
 assert.equal(chooseReadyInvestigation(doc,'inv-high').id,'inv-high');
 assert.throws(()=>chooseReadyInvestigation(doc,'inv-low'),/next eligible task/);
 assert.throws(()=>chooseReadyInvestigation(doc,'inv-blocked'),/next eligible task/);
 assert.throws(()=>chooseReadyInvestigation(doc,'inv-unknown'),/next eligible task/);
 assert.throws(()=>chooseReadyInvestigation(doc,''),/next eligible task/);
});
