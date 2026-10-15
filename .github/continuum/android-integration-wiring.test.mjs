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
 ensure('owner memory synchronization',activity.includes('core.syncOwnerFacts(bootstrapMemory.facts())'));
 ensure('explicit remember re-confirms revoked facts',activity.includes('confirmOwnerFact(fact)'));
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
test('scheduled offline job names the JobService declared in the manifest',()=>{
 const m=scheduler.match(/const val SERVICE_CLASS="([\w.]+)"/);
 assert.ok(m,'SERVICE_CLASS constant not found');
 const name=m[1],cls=name.slice(name.lastIndexOf('.')+1);
 ensure('service class lives in the scheduler package',name==='at.heinz.deviceai.learning.'+cls&&scheduler.includes('package at.heinz.deviceai.learning'));
 ensure('service class is a JobService',new RegExp('class '+cls+'\\s*:\\s*JobService\\(').test(scheduler));
 ensure('service declared in manifest',manifest.includes('android:name="'+name.replace('at.heinz.deviceai','')+'"'));
});
test('user-visible texts contain line breaks, not a literal backslash-n',()=>{
 // "\\n" inside a Kotlin string literal is shown as the two characters \ and n.
 for(const file of ['AgentActivity.kt','BootstrapBrain.kt','MainActivity.kt']){
  const source=fs.readFileSync(dir+file,'utf8');
  const hits=source.split('\n').map((line,i)=>[i+1,line]).filter(([,line])=>/"[^"]*\\\\n[^"]*"/.test(line));
  assert.deepEqual(hits.map(([n])=>file+':'+n),[],'literal \\\\n in '+file);
 }
});
test('learning-core and research store I/O stays off the UI thread in dialogs and chat input',()=>{
 // A training round holds the learning-core lock for seconds; these functions run on the UI thread.
 const heavy=/ContinuumLearningCore\(|\.neuralSnapshot\(\)|FileResearchDossierStore\(|openInputStream\(/;
 const uiOnly=['showBootstrapTrainingDialog','showContinuumLearningDashboardDialog','showModelVersionsDialog',
  'showWebLearningInboxDialog','offerResearchResume','reportNeuralPracticeEnabled'];
 // drops the argument list of every onLearningIo( ... ) call (string literals skipped while matching)
 const withoutIo=body=>{
  let out='',i=0;
  while(i<body.length){
   const at=body.indexOf('onLearningIo(',i);
   if(at<0){out+=body.slice(i);break;}
   out+=body.slice(i,at);
   let depth=0,j=at+'onLearningIo'.length,inString=false;
   for(;j<body.length;j++){
    const c=body[j];
    if(inString){if(c==='\\')j++;else if(c==='"')inString=false;continue;}
    if(c==='"')inString=true;
    else if(c==='(')depth++;
    else if(c===')'&&--depth===0)break;
   }
   i=j+1;
  }
  return out;
 };
 for(const name of uiOnly){
  const body=bodyOf(activity,'private fun '+name+'(');
  assert.ok(body,name+' not found');
  ensure(name+' has no file or learning-core I/O',!heavy.test(withoutIo(body)));
 }
 for(const name of ['showBootstrapTraining','showContinuumLearningDashboard','showModelVersions','showWebLearningInbox',
  'rememberOwnerFact','reviseOwnerFact','forgetOwnerFacts','confirmBootstrapTraining']){
  const body=bodyOf(activity,'private fun '+name+'(');
  assert.ok(body,name+' not found');
  const io=body.indexOf('onLearningIo(');
  const first=body.search(heavy);
  ensure(name+' does its I/O inside onLearningIo',first<0||(io>=0&&io<first));
 }
 const input=bodyOf(activity,'private fun addUserMessage(');
 ensure('learning radar scores off the UI thread',input.indexOf('onLearningIo(')>=0&&input.indexOf('onLearningIo(')<input.search(heavy));
 const result=bodyOf(activity,'override fun onActivityResult(');
 const pkg=result.slice(result.indexOf('CONTINUUM_PACKAGE_MODEL_REQUEST ->'),result.indexOf('CONTINUUM_PACKAGE_MANIFEST_REQUEST ->'));
 ensure('package model read off the UI thread',pkg.indexOf('onLearningIo(')>=0&&pkg.indexOf('onLearningIo(')<pkg.indexOf('openInputStream('));
});
test('neural weights are saved by compare-and-swap and foreground practice can be stopped',()=>{
 const run=bodyOf(activity,'private fun runNeuralCommand(');
 ensure('experiment saves with compare-and-swap',run.includes('compareAndSaveNeuralSnapshot(original, bytes, measurement)'));
 ensure('experiment never overwrites unconditionally',!run.includes('saveNeuralSnapshot(bytes'));
 ensure('save happens on the worker, not in the result callback',run.indexOf('compareAndSaveNeuralSnapshot')<run.lastIndexOf('runOnUiThread'));
 ensure('foreground practice is cancellable',/practiceOnce\(cancelled = \{/.test(activity));
});
test('startup bootstrap training stops with the activity and is retried later',()=>{
 const create=bodyOf(activity,'override fun onCreate(');
 ensure('startup stages get a cancel callback',create.includes('bootstrapOfflineCurriculum(stopBootstrap::get)')&&create.includes('bootstrapSemanticCurriculum(stopBootstrap::get)'));
 const destroy=bodyOf(activity,'override fun onDestroy(');
 ensure('onDestroy stops the startup training',destroy.includes('bootstrapCancelled.set(true)'));
 ensure('onDestroy cancels a running read-only tool gate',destroy.includes('activeBootstrapGate?.cancel()'));
 ensure('interrupted stage writes no run-once audit',core.includes('if(interrupted(cancelled))return@synchronized interruptedReport("offline-bootstrap"')&&core.includes('interruptedReport("offline-semantic-bootstrap"'));
});
test('startup I/O off the UI thread and shell commands stop with their activity',()=>{
 const create=bodyOf(activity,'override fun onCreate(');
 ensure('continuum store opens on the agent executor',bodyOf(activity,'private fun initializeContinuum(').includes('localAgentExecutor.execute'));
 ensure('no Keystore decrypt for the cloud badge',!bodyOf(activity,'private fun updateCloudBadge(').includes('isConfigured()'));
 ensure('receipt review decodes on a worker',bodyOf(activity,'private fun showPendingReceiptReview()').includes('onLearningIo('));
 ensure('onCreate itself opens no store',!/AndroidKeystoreIdentitySigner\(|receiptStore\.all\(\)/.test(create));
 const main=fs.readFileSync(dir+'MainActivity.kt','utf8');
 ensure('plan step chain stops when the activity is gone',/fun runStep\(index: Int\) \{\s*\/\/[^\n]*\n\s*if \(isFinishing \|\| isDestroyed\) return/.test(main));
 const bridge=fs.readFileSync(dir+'ShizukuBridge.kt','utf8');
 ensure('stopped bridge drops its shell',/fun stop\(\)[\s\S]*?shell = null\s*\n\s*\}/.test(bridge));
});
test('local language model path is reachable: model center, loading, chat escalation and reasoning chain',()=>{
 ensure('model center from the core card',activity.includes('Triple("MODELLE", false) { showModelCenter() }'));
 ensure('model center from chat',/request\.equals\("Modellverwaltung", true\)\) \{[\s\S]{0,120}showModelCenter\(\)/.test(activity));
 const center=bodyOf(activity,'private fun showModelCenter(');
 ensure('center imports a model',center.includes('showModelImportDialog()'));
 ensure('center loads or unloads',center.includes('chooseInstalledModel()')&&center.includes('unloadModel()'));
 ensure('center binds the model card and action button',center.includes('modelCardText = card')&&center.includes('localActionButton = action'));
 ensure('center offers encoder versions and imports',center.includes('showModelVersions()')&&center.includes('confirmPackageImport()')&&center.includes('confirmWeightsImport()'));
 ensure('one runtime per process',bodyOf(activity,'override fun onCreate(').includes('LocalModelHost.get(this)'));
 ensure('rotation keeps the loaded model',bodyOf(activity,'override fun onDestroy(').includes('!isChangingConfigurations'));
 const dialogue=bodyOf(activity,'private fun runContinuumDialogue(');
 ensure('unanswered memory turn escalates to the language model',dialogue.includes('LocalFirstPolicy.afterMemory(')&&dialogue.includes('runLocal(request)'));
 const local=bodyOf(activity,'private fun runLocal(');
 ensure('reasoning chain in the local path',local.includes('ChatReasoningRouter(LocalModelReasoningAdapter(localRuntime)'));
 ensure('language model sees the confirmed owner facts',local.includes('ownerFactMemories()'));
 ensure('model list read off the UI thread',bodyOf(activity,'private fun reloadInstalledModels(').includes('onLearningIo('));
 ensure('no catalog read in the UI refresh',!bodyOf(activity,'private fun refreshLocalModelUi(').includes('modelCatalog.all()'));
});
test('every area has a center reachable from the core card; no demo buttons; experiments marked',()=>{
 const card=bodyOf(activity,'private fun buildBootstrapCoreCard(');
 for(const [label,fn] of [['LERNEN','showLearningCenter'],['GEDÄCHTNIS','showMemoryCenter'],['ENTWICKLUNG','showDevelopmentCenter'],
  ['MODELLE','showModelCenter'],['SYSTEM','showSystemCenter']]){
  ensure(label+' center on the core card',card.includes('Triple("'+label+'"')&&card.includes(fn+'()'));
  assert.ok(activity.includes('private fun '+fn+'('),fn+' not found');
 }
 const labels=[...activity.matchAll(/actionButton\("([^"]+)"/g),...activity.matchAll(/CenterAction\("([^"]+)"/g)].map(m=>m[1]);
 ensure('no demo button',labels.every(l=>!/DEMO|JETZT TRAINIEREN/i.test(l)));
 // expression-bodied: everything up to the next member declaration
 const declOf=(source,signature)=>{
  const start=source.indexOf(signature);
  if(start<0)return null;
  const next=source.slice(start+signature.length).search(/\n    (private |override |internal |public )?(fun|val|var|class) /);
  return next<0?source.slice(start):source.slice(start,start+signature.length+next);
 };
 const learning=declOf(activity,'private fun showLearningCenter(');
 const experimental=learning.slice(learning.indexOf('experimental = listOf('));
 ensure('contrastive experiment only in the marked experimental section',experimental.includes('showContinuumContrastiveExperiment()')&&
  !learning.slice(0,learning.indexOf('experimental = listOf(')).includes('showContinuumContrastiveExperiment()'));
 ensure('experimental section is labelled',bodyOf(activity,'private fun showCenter(').includes('EXPERIMENTELL'));
 for(const fn of ['learningCenterStatus','memoryCenterStatus','systemCenterStatus']){
  ensure(fn+' runs on the learning I/O thread',activity.includes('learningIoStatus { '+fn+'() }'));
 }
 ensure('development status reads the backlog on its executor',activity.includes('onBacklog({ developmentCenterStatus() })'));
});


test('explicit chat feedback reaches revocable ledger without becoming unverified neural training',()=>{
 const legacy=fs.readFileSync(dir+'BootstrapMemoryStore.kt','utf8');
 const feedback=bodyOf(activity,'private fun recordContinuumFeedback(');
 const revoke=bodyOf(activity,'private fun confirmRevokeFeedback(');
 ensure('positive rating invokes ledger path',activity.includes('BootstrapKind.FEEDBACK_POSITIVE -> recordContinuumFeedback(true)'));
 ensure('negative rating invokes ledger path',activity.includes('BootstrapKind.FEEDBACK_NEGATIVE -> recordContinuumFeedback(false)'));
 ensure('feedback uses learning IO executor',feedback?.includes('onLearningIo('));
 ensure('feedback persists as typed local experience',core.includes('type=LearningExperienceType.USER_FEEDBACK'));
 ensure('feedback never stores generated chat answers',core.includes('annotation="rating-only-no-training"'));
 ensure('feedback never directly trains model',!feedback?.includes('trainIntent(')&&!feedback?.includes('trainSemantic('));
 ensure('Learning Center surfaces counts',activity.includes('Lernkern-Feedback: '));
 ensure('Learning Center offers revocation',activity.includes('RÜCKMELDUNGEN · WIDERRUFEN'));
 ensure('revocation is confirmed by owner',revoke?.includes('.setPositiveButton("Widerrufen")'));
 ensure('revocation clears core entries',revoke?.includes('revokeExplicitFeedback()'));
 ensure('revocation also clears old counters',revoke?.includes('bootstrapMemory.clearFeedback()')&&legacy.includes('fun clearFeedback()'));
});
