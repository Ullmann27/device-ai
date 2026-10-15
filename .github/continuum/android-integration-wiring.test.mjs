// Source-level integration contracts for the Android application.
// These assertions are intentionally NOT device-runtime tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const dir='app/src/main/java/at/heinz/deviceai/';
const activity=fs.readFileSync(dir+'AgentActivity.kt','utf8');
const core=fs.readFileSync(dir+'learning/ContinuumLearningCore.kt','utf8');
const scheduler=fs.readFileSync(dir+'learning/ContinuumAutonomousScheduler.kt','utf8');
const status=fs.readFileSync(dir+'learning/ContinuumOfflineJobStatus.kt','utf8');
const probe=fs.readFileSync(dir+'learning/ContinuumLocalContrastiveProbe.kt','utf8');
const manifest=fs.readFileSync('app/src/main/AndroidManifest.xml','utf8');
const ensure=(name,pass)=>assert.ok(pass,'Disconnected or unsupported code path: '+name);
test('learning core, retained sources, developer backlog, dialogue and diagnostic are reachable from Android',()=>{
 ensure('learning dashboard',activity.includes('showContinuumLearningDashboard()'));
 ensure('neural research action button',activity.includes('KONTRASTIV · EXPERIMENT PRÜFEN'));
 ensure('neural experiment invokes real module',activity.includes('ContinuumLocalContrastiveProbe.run('));
 ensure('existing local model source',activity.includes('ContinuumLearningCore(filesDir).modelSnapshot()'));
 ensure('existing local conversation routing',activity.includes('core.replyFromMemory('));
 ensure('owner memory synchronization',activity.includes('bootstrapMemory.facts().forEach(core::recordOwnerFact)'));
 ensure('background job wired at application startup',activity.includes('ContinuumAutonomousScheduler(this).restore()'));
 ensure('scheduled job status visible',activity.includes('ContinuumOfflineJobStatus.read(this@AgentActivity).display()'));
 ensure('developer backlog UI',activity.includes('showDevelopmentBacklog()'));
 ensure('developer agent dialog',activity.includes('showDevelopmentDialog()'));
 ensure('previous model recovery',activity.includes('rollbackToPrevious()'));
 ensure('model import',activity.includes('importSnapshot(bytes)'));
});
test('background consolidation records failure without losing interrupted source trials',()=>{
 ensure('onStartJob',scheduler.includes('override fun onStartJob('));
 ensure('onStopJob',scheduler.includes('override fun onStopJob('));
 ensure('Android service in manifest',manifest.includes('.learning.ContinuumAutonomousJobService'));
 ensure('interruption cannot mark source done',scheduler.includes('report?.completedEvaluation==true'));
 ensure('report has evaluation completion field',core.includes('val completedEvaluation:Boolean=true'));
 ensure('incomplete training requires retry',core.includes('completedEvaluation=!cancelled() && !train.stoppedEarly'));
 ensure('started observed',scheduler.includes('ContinuumOfflineJobStatus.started('));
 ensure('finished observed',scheduler.includes('ContinuumOfflineJobStatus.finished('));
 ensure('exceptions visible',scheduler.includes('ContinuumOfflineJobStatus.failed('));
 ensure('scheduler status persisted',status.includes('getSharedPreferences('));
 ensure('errors omit exception message',!status.includes('error.message'));
});
test('experimental module is inspectable and not on any automatic model promotion path',()=>{
 ensure('synthetic only',probe.includes('private val TRAIN=')&&probe.includes('private val HOLDOUT='));
 ensure('candidate on copied active snapshot',probe.includes('TrainableSubwordEncoder.restore(snapshot)'));
 ensure('bounded steps',probe.includes('steps in 1..150'));
 ensure('no save or importing weights',!/\b(saveModel|importSnapshot|importPublicPackage|ModelCheckpointStore)\s*\(/.test(probe));
 ensure('candidate training is actually invoked',probe.includes('ContinuumContrastiveViews.trainCandidate('));
});
// onActivityResult dispatches on the first matching `when` branch, so two equal
// request codes silently route one picker's result into another handler (the
// backlog export once landed in the model-weight import this way).
const bodyOf=(source,signature)=>{
 const start=source.indexOf(signature);
 if(start<0)return null;
 const open=source.indexOf('{',start);
 let depth=0;
 for(let i=open;i<source.length;i++){
  if(source[i]==='{')depth++;
  else if(source[i]==='}'&&--depth===0)return source.slice(open,i+1);
 }
 return null;
};
test('activity request codes are unique and every one has a result handler',()=>{
 for(const file of ['AgentActivity.kt','MainActivity.kt']){
  const source=fs.readFileSync(dir+file,'utf8');
  const codes=[...source.matchAll(/const val (\w+_REQUEST)\s*=\s*(\d+)/g)].map(m=>({name:m[1],value:Number(m[2])}));
  assert.ok(codes.length>0,file+': no request codes found (pattern out of date?)');
  const byValue=new Map();
  for(const c of codes){
   assert.ok(!byValue.has(c.value),`${file}: ${c.name} and ${byValue.get(c.value)} share request code ${c.value}`);
   byValue.set(c.value,c.name);
  }
  const handler=bodyOf(source,'override fun onActivityResult(');
  assert.ok(handler,file+': onActivityResult not found');
  for(const c of codes){
   ensure(`${file}: ${c.name} handled in onActivityResult`,new RegExp(`\\b${c.name}\\b\\s*(->|,)|==\\s*${c.name}\\b`).test(handler));
   const uses=source.split(new RegExp(`\\b${c.name}\\b`)).length-1;
   ensure(`${file}: ${c.name} is requested somewhere`,uses>=3);
  }
 }
});
