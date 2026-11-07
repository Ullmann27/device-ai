// Source-level integration contracts for the Android application.
// These assertions are intentionally NOT device-runtime tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const dir='app/src/main/java/at/heinz/deviceai/';
const activity=fs.readFileSync(dir+'AgentActivity.kt','utf8');
const core=fs.readFileSync(dir+'learning/ContinuumLearningCore.kt','utf8');
const runtime=fs.readFileSync(dir+'learning/ContinuumCognitiveRuntime.kt','utf8');
const memory=fs.readFileSync(dir+'learning/ContinuumMemoryDialogue.kt','utf8');
const cre1Gate=fs.readFileSync(dir+'learning/Cre1PromotionGate.kt','utf8');
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
 ensure('single conversation entrance uses cognitive runtime',
   activity.includes('ContinuumCognitiveRuntime(filesDir,core,') &&
   activity.includes('conversationId=requestSessionId,priorScopedGroundedQuestion=priorScopedTopic') &&
   activity.includes('val turn=cognitive.dialogue') &&
   !bodyOf(activity,'private fun runContinuumDialogue(').includes('core.replyFromMemory('));
 ensure('SWB1 evidence and CRE1 reranking share one locked snapshot and persisted order',
   runtime.includes('core.replyWithEvidenceInConversation(input,conversationId,turnTrace)') &&
   core.includes('fun replyWithEvidence(') &&
   core.includes('dialogue.replyInConversation(snapshot,experiences,input,nowMillis(),conversationId,rerank)') &&
   core.includes('ContinuumMemoryDialogue.requireConversationId(conversationId)') &&
   memory.includes('saved.conversationId==conversationId') &&
   core.includes('ContinuumMemoryDialogue.rank(snapshot,turn.resolvedQuery,experiences)') &&
   core.includes('turn.memoryIds.mapNotNull(byId::get)'));
 ensure('CRE1 cannot expand the SWB1 evidence pool or alter hit provenance',
   runtime.includes('val (turn,ranked)=core.replyWithEvidenceInConversation(input,conversationId,turnTrace)') &&
   memory.includes('reordered.toSet()==hits.toSet()') &&
   memory.includes('reordered.map { it.id }.distinct().size==hits.size') &&
   cre1Gate.includes('semanticById.keys!=swb1.map { it.id }.toSet()'));
 ensure('CRE1 promotion needs a model-bound benchmark, otherwise stays shadow',
   runtime.includes('Cre1PromotionGate.readQualified(') &&
   runtime.includes('else "CRE1_SHADOW"') &&
   cre1Gate.includes('sampleCount>=200 && negativeCount>=50') &&
   cre1Gate.includes('hybridFalseAttributions==0 && negativeFalseAnswers==0') &&
   !runtime.includes('confirmOwnerFact(') &&
   !runtime.includes('trainExperimentalCre1('));
 ensure('unanswered turn alone reaches local model',
   bodyOf(activity,'private fun runContinuumDialogue(').includes('LocalFirstPolicy.afterMemory('));
 ensure('same-session bounded local-model context is separate from grounded memory-scoped followups',
   activity.includes('LocalFirstPolicy.localModelQuestion(') &&
   activity.includes('hasSameSessionLocalTurn=localConversationContext') &&
   activity.includes('.render(requestSessionId).isNotBlank()') &&
   activity.includes('grounded=grounded') &&
   activity.includes('runLocal(request,modelQuestion,turnTrace=turnTrace)') &&
   activity.includes('scopedLocalPromptSession?.takeIf { it.id==requestSessionId }?.groundedTopic') &&
   runtime.includes('val currentContext=if(followUp && scopedPrevious!=null)') &&
   !runtime.includes('Vorherige Gesprächsfrage: ') &&
   !runtime.includes('core.dialogueHistory()'));
 const newSession=bodyOf(activity,'private fun newSession(');
 ensure('conversation identity survives recreation; explicit new chat clears baseline and shadow off the UI thread',
   activity.includes('ctx3_chat_id_v1') && newSession.includes('onLearningIo(') &&
   newSession.includes('resetDialogueConversation(closingSession) { sessionResetLifetime.get() }') &&
   bodyOf(activity,'override fun onDestroy(').includes('sessionResetLifetime.set(false)'));
 ensure('owner memory synchronization',activity.includes('core.syncOwnerFacts(bootstrapMemory.facts())'));
 ensure('explicit remember re-confirms revoked facts',activity.includes('confirmOwnerFact(fact)'));
 ensure('background job wired at application startup',activity.includes('ContinuumAutonomousScheduler(this).restore()'));
 ensure('scheduled job status visible',activity.includes('ContinuumOfflineJobStatus.read(this@AgentActivity).display()'));
 ensure('developer backlog UI',activity.includes('showDevelopmentBacklog()'));
 ensure('developer agent dialog',activity.includes('showDevelopmentDialog()'));
 ensure('previous model recovery',activity.includes('rollbackToPrevious()'));
 ensure('model import',activity.includes('importSnapshot(bytes)'));
});
test('cognitive runtime owns the single chat path, privacy boundaries and background diagnostics',()=>{
 const dialogue=bodyOf(activity,'private fun runContinuumDialogue(');
 const local=bodyOf(activity,'runLocal');
 ensure('local model entry body exists',local!=null);
 const localCode=codeOnly(local);
 ensure('single shared runtime is called from chat with scoped session-only context',
   dialogue?.includes('ContinuumCognitiveRuntime(filesDir,core,') &&
   dialogue.includes('conversationId=requestSessionId,priorScopedGroundedQuestion=priorScopedTopic') &&
   dialogue.includes('val requestSessionId=sessionId') &&
   dialogue.includes('scopedLocalPromptSession?.takeIf { it.id==requestSessionId }?.groundedTopic'));
 ensure('local model context is separate from user-authorized memory input',
   dialogue?.includes('runLocal(request,modelQuestion,turnTrace=turnTrace)') &&
   dialogue.includes('runLocal(request,modelQuestion,recall,turnTrace)') &&
   activity.includes('private fun runLocal(text: String, resolvedQuestion: String = text,') &&
   /val\s+followUpFrame\s*=\s*at\.heinz\.deviceai\.cognitive\.LocalFollowUpTaskFrame\.composeFrame\(\s*text,\s*resolvedQuestion,\s*if\s*\(botTask\s*==\s*null\s*&&\s*codingPlanning\s*==\s*null\)\s*localConversationContext\.sessionEvidence\(localSessionId\)\s*else\s*null\)/.test(localCode) &&
   /val\s+agentTask\s*=\s*if\s*\(botTask\s*!=\s*null\)\s*text\s*else\s*codingPlanning\?\.ownerGoal\s*\?:\s*followUpFrame\.task\b/.test(localCode) &&
   /orchestrator\.run\(agentTask\)/.test(localCode) &&
   /AgentModel\s*\{\s*task,\s*memories,\s*reflection,\s*rawObservations\s*->/.test(localCode) &&
   /userRequest\s*=\s*task\s*,/.test(localCode) &&
   /ownerTurnsInTask\s*=\s*followUpFrame\.ownerTurnsIn\(promptBody\)/.test(localCode) &&
   !bodyOf(activity,'private fun runLocal(text:').includes('resolvedQuestion.take(') &&
   bodyOf(activity,'private fun runLocal(text:').includes('LocalChatPromptBudget.acceptsCompleteTask(resolvedQuestion)') &&
   bodyOf(activity,'private fun runLocal(text:').indexOf('LocalChatPromptBudget.acceptsCompleteTask(resolvedQuestion)') <
     bodyOf(activity,'private fun runLocal(text:').indexOf('activeLocalRequest = true') &&
   bodyOf(activity,'private fun runLocal(text:').includes('publishCognitiveTrace(turnTrace,TraceOutcome.CLARIFY)') &&
   !activity.includes('persistRequestedMemory(') && !bodyOf(activity,'private fun runLocal(text:').includes('memoryStore.put('));
 ensure('owner tool authorization uses untouched input and planning has no memory/tool authority',
   /createToolGate\(ownerTurn\s*=\s*text,\s*consentRevisionAtTurnStart\s*=\s*internetRevisionAtTurnStart/.test(codeOnly(local)) &&
   /memories\s*=\s*\{\s*if\(codingPlanning\s*==\s*null\s*&&\s*botTask\s*==\s*null\)\s*memoryStore\.all\(\)\s*\+\s*ownerFactMemories\(\)\s*else\s*emptyList\(\)\s*\}/.test(codeOnly(local)) &&
   /limits\s*=\s*if\(codingPlanning\s*!=\s*null\)\s*at\.heinz\.deviceai\.cognitive\.AgentLimits\(maxToolCalls\s*=\s*0,maxRetries\s*=\s*0\)/.test(codeOnly(local)));
 ensure('only explicit confirmed fact can retry unanswered turn',
   bodyOf(activity,'private fun rememberOwnerFact(')?.includes('core.confirmOwnerFact(fact)') &&
   bodyOf(activity,'private fun rememberOwnerFact(')?.includes('if(resolved?.grounded==true)'));
 ensure('local failure and success both recorded without model text',
   activity.includes('.recordLocalOutcome(successful)') && activity.includes('.recordLocalOutcome(false)'));
 ensure('local on-device status available from chat',activity.includes('showContinuumSelfCheck()'));
 const journal=fs.readFileSync(dir+'learning/ContinuumRuntimeEventJournal.kt','utf8');
 ensure('journal contains only fixed execution codes',journal.includes('LOCAL_MODEL_OK') &&
   journal.includes('LOCAL_MODEL_FAILED') && !journal.includes('responseText'));
 ensure('CRE1 never authorizes tool execution',!runtime.includes('ToolGate(') &&
   !runtime.includes('approveTool('));
});
test('background consolidation records failure without losing interrupted source trials',()=>{
 ensure('onStartJob',scheduler.includes('override fun onStartJob('));
 ensure('onStopJob',scheduler.includes('override fun onStopJob('));
 ensure('Android service in manifest',manifest.includes('.learning.ContinuumAutonomousJobService'));
 ensure('scheduler delegates source completion',scheduler.includes('core.learnFromSources(fresh,cancelled)') &&
   !scheduler.includes('ContinuumSourceNovelty.markCompleted('));
 ensure('interruption cannot mark source done',
   /if\(report\.completedEvaluation\s*&&\s*!ContinuumSourceNovelty\.markCompleted\(directory\.parentFile!!,batch,cancelled\)\)\s*report\.copy\(completedEvaluation=false/.test(codeOnly(core)));
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
// Match real function bodies, not the next declaration or braces in strings/comments.
function codeOnly(source) {
 const out=source.split('');
 for(let i=0;i<source.length;) {
  const start=i;
  if(source.startsWith('//',i)) { i=source.indexOf('\n',i);if(i<0)i=source.length; }
  else if(source.startsWith('/*',i)) {
   i+=2;let depth=1;
   while(i<source.length&&depth) { if(source.startsWith('/*',i)){depth++;i+=2;}else if(source.startsWith('*/',i)){depth--;i+=2;}else i++; }
  } else if(source.startsWith('"""',i)) { const end=source.indexOf('"""',i+3);i=end<0?source.length:end+3; }
  else if('"\'`'.includes(source[i])) {
   const quote=source[i++];while(i<source.length) { if(source[i]==='\\'){i+=2;continue;}if(source[i++]===quote)break; }
  } else { i++;continue; }
  for(let j=start;j<i;j++)if(source[j]!=='\n')out[j]=' ';
 }
 return out.join('');
}
function bracedBody(source,open) {
 const code=codeOnly(source);assert.equal(code[open],'{','missing body opening');let depth=0;
 for(let i=open;i<code.length;i++){if(code[i]==='{')depth++;else if(code[i]==='}'&&--depth===0)return source.slice(open,i+1);}
 assert.fail('unbalanced function body');
}
function bodyOf(source,signature) {
 const name=signature.match(/\b(?:fun|function)\s+(\w+)\s*\(/)?.[1]??signature;
 assert.match(name,/^[A-Za-z_]\w*$/);
 const code=codeOnly(source),match=new RegExp('\\b(?:fun|function)\\s+'+name+'\\s*\\(').exec(code);
 if(!match)return null;
 let i=code.indexOf('(',match.index),depth=0;
 for(;i<code.length;i++){if(code[i]==='(')depth++;else if(code[i]===')'&&--depth===0){i++;break;}}
 const open=code.indexOf('{',i),equals=code.indexOf('=',i);
 if(equals>=0&&(open<0||equals<open)) {
  // The expression-bodied motion predicate ends before the next blank line/declaration.
  const end=code.indexOf('\n\n',equals);return source.slice(equals,end<0?source.length:end);
 }
 return open<0?null:bracedBody(source,open);
}
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
   // Speech codes now scope in-process callbacks; they must never launch an external recognizer.
   if(file==='AgentActivity.kt' && ['SPEECH_REQUEST','DEV_SPEECH_REQUEST','READINESS_SPEECH_REQUEST'].includes(c.name)) {
    ensure(c.name+' uses the local recognizer', source.includes('AndroidOnDeviceSpeechInput(this)') &&
      source.includes('private fun startVoiceInput(requestCode: Int = SPEECH_REQUEST)') &&
      source.includes('startVoiceInput(DEV_SPEECH_REQUEST)') &&
      (c.name!=='READINESS_SPEECH_REQUEST' || source.includes('startVoiceInput(READINESS_SPEECH_REQUEST)')) &&
      source.includes('applyLocalSpeechText(requestCode,spoken)'));
    ensure(c.name+' never launches an Activity result', !source.includes('startActivityForResult(intent, requestCode)'));
    continue;
   }
   if(file==='AgentActivity.kt' && ['READINESS_CAMERA_PERMISSION_REQUEST','READINESS_MIC_PERMISSION_REQUEST'].includes(c.name)) {
    const permission=bodyOf(source,'onRequestPermissionsResult');
    const marker=/if\s*\(requestCode\s*==\s*READINESS_CAMERA_PERMISSION_REQUEST\s*\|\|\s*requestCode\s*==\s*READINESS_MIC_PERMISSION_REQUEST\s*\)\s*\{/.exec(codeOnly(permission));
    assert.ok(marker,'readiness permissions must have their own callback branch');
    const refreshOnly=bracedBody(permission,marker.index+marker[0].lastIndexOf('{'));
    assert.match(codeOnly(refreshOnly),/^\{\s*refreshReadinessGuide\(\)\s*return\s*\}$/);
    assert.doesNotMatch(codeOnly(refreshOnly),/\b(?:startVoiceInput|startReadinessCamera|start|openCamera|requestPermissions)\s*\(/,
      'readiness permission callback refreshes only; an explicit later gesture starts sensors');
    continue;
   }
   if(file==='AgentActivity.kt' && c.name==='LOCAL_SPEECH_PERMISSION_REQUEST') {
    const permission=bodyOf(source,'override fun onRequestPermissionsResult(');
    ensure(c.name+' is permission-only',permission?.includes('requestCode==LOCAL_SPEECH_PERMISSION_REQUEST') &&
      permission.includes('grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED') &&
      permission.includes('startVoiceInput(pending)'));
    continue;
   }
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
 assert.match(bodyOf(activity,'private fun addUserMessage('),/addUserMessage\(value, keepCockpit = false\)/);
 const inputStart=codeOnly(activity).indexOf('private fun addUserMessage(value: String, keepCockpit: Boolean)');
 assert.ok(inputStart>=0,'chat input implementation not found');
 const input=bodyOf(activity.slice(inputStart),'addUserMessage');
 ensure('learning radar scores off the UI thread',input.includes('onLearningIo(')&&
  input.includes('ContinuumLearningCore(')&&input.includes('.neuralSnapshot()')&&!heavy.test(withoutIo(input)));
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
 const stop=bodyOf(bridge,'fun stop('),reset=bodyOf(bridge,'private fun resetBinding(');
 ensure('stopped bridge drops its shell',stop?.includes('started = false') &&
   stop.includes('resetBinding()') && reset?.includes('shell = null') &&
   reset.includes('activeConnection = null'));
});
test('local language model path is reachable: model center, loading, chat escalation and reasoning chain',()=>{
 const slots=bodyOf(activity,'private fun openHomeSlot(');
 ensure('model center from the start screen',/Slot\.MODEL -> showModelCenter\(\)/.test(slots));
 ensure('model center from chat',/request\.equals\("Modellverwaltung", true\)\) \{[\s\S]{0,120}showModelCenter\(\)/.test(activity));
 const center=bodyOf(activity,'private fun showModelCenter(');
 ensure('center imports a model',center.includes('showModelImportDialog()'));
 ensure('center loads or unloads',center.includes('chooseInstalledModel()')&&center.includes('unloadModel()'));
 ensure('center binds the model card and action button',center.includes('modelCardText = card')&&center.includes('localActionButton = action'));
 ensure('center offers encoder versions and imports',center.includes('showModelVersions()')&&center.includes('confirmPackageImport()')&&center.includes('confirmWeightsImport()'));
 ensure('one runtime per process',bodyOf(activity,'override fun onCreate(').includes('LocalModelHost.get(this)'));
 ensure('rotation keeps the loaded model',bodyOf(activity,'override fun onDestroy(').includes('!isChangingConfigurations'));
 const dialogue=bodyOf(activity,'private fun runContinuumDialogue(');
 ensure('unanswered memory turn escalates to the language model',dialogue.includes('LocalFirstPolicy.afterMemory(')&&dialogue.includes('runLocal(request,turnTrace=turnTrace)'));
 const local=bodyOf(activity,'private fun runLocal(');
 ensure('reasoning chain in the local path',local.includes('ChatReasoningRouter(LocalModelReasoningAdapter(localRuntime,turnTrace=turnTrace)'));
 ensure('language model sees the confirmed owner facts',local.includes('ownerFactMemories()'));
 ensure('model list read off the UI thread',bodyOf(activity,'private fun reloadInstalledModels(').includes('onLearningIo('));
 ensure('no catalog read in the UI refresh',!bodyOf(activity,'private fun refreshLocalModelUi(').includes('modelCatalog.all()'));
});
test('every area has a center reachable from the start screen; no demo buttons; experiments marked',()=>{
 const reach=bodyOf(activity,'private fun openHomeSlot(')+bodyOf(activity,'private fun navigateWorkArea(')+
  bodyOf(activity,'private fun showMoreWorkAreas(');
 for(const fn of ['showLearningCenter','showMemoryCenter','showDevelopmentCenter','showModelCenter','showSystemCenter']){
  ensure(fn+' reachable from the start screen',reach.includes(fn+'()'));
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

test('Teamleader Android UI reports status without false success',()=>{
 const check=bodyOf(activity,'private fun runDeviceTeamleaderCheck()');
 assert.ok(check,'Teamleader check handler must be present');
 ensure('Teamleader outcome inspected as a typed snapshot',
   check.includes('result.fold(') && check.includes('snapshot.state'));
 ensure('completed only for READY',
   check.includes('"READY" -> "CONTINUUM · Teamleiterprüfung erfolgreich abgeschlossen"'));
 ensure('deferred reported without success',
   check.includes('"DEFERRED" -> "CONTINUUM · Teamleiterprüfung verschoben:'));
 ensure('ERROR reported without success',
   check.includes('else -> "CONTINUUM · Teamleiterprüfung fehlgeschlagen:'));
 ensure('exception reported without success',
   check.includes('onFailure = { error ->') &&
   check.includes('addToolActivity("CONTINUUM · Teamleiterprüfung fehlgeschlagen")'));
 ensure('previous unconditional success removed',
   !check.includes('addToolActivity("CONTINUUM · Teamleiterprüfung abgeschlossen")'));
});

test('live model status does not revert the beginner cockpit to English',()=>{
 const status=bodyOf(activity,'private fun refreshLocalModelUi()');
 const copy=fs.readFileSync(dir+'ContinuumUiCopy.kt','utf8');
 ensure('local basis is not misrepresented as imported language model',
   status?.includes('"● BASIS BEREIT"') && status.includes('localRuntime.loadedModel'));
 ensure('all dynamic model stages are understandable in German',
   ['"KEIN MODELL"','"INSTALLIERT"','"BEREIT"','"LÄDT"',
    '"ARBEITET"','"FEHLER"'].every(x=>status.includes(x)));
 ensure('model-error status offers novice guidance and bounded debug evidence',
   status.includes('ContinuumUiCopy.modelFailure(localRuntimeError)') &&
   status.includes('localRuntimeError!!.take(160)'));
 ensure('common model failures are categorized, not silently declared successful',
   copy.includes('fun modelFailure(') && copy.includes('Prüfwert') &&
   copy.includes('Modellformat') && copy.includes('Arbeitsspeicher'));
});

test('model setup stays usable for beginners while preserving SHA-256 integrity',()=>{
 const model=bodyOf(activity,'private fun showModelCenter()');
 const importer=bodyOf(activity,'private fun showModelImportDialog(');
 const developer=bodyOf(activity,'private fun showDevelopmentDialog()');
 ensure('model onboarding begins with import and load, while expert functions are collapsed',
   model?.includes('MODELLDATEI IMPORTIEREN') &&
   model.includes('MODELL LADEN') &&
   model.includes('ERWEITERT · LERNKERN UND VERSIONEN') &&
   model.includes('visibility = View.GONE'));
 ensure('advanced model functions remain reachable, not deleted',
   model.includes('showModelVersions()') &&
   model.includes('confirmPackageImport()') &&
   model.includes('confirmWeightsImport()'));
 ensure('integrity check remains mandatory, dialog stays open for corrections',
   importer?.includes('sha256.matches(Regex("[a-f0-9]{64}"))') &&
   importer.includes('setPositiveButton("DATEI AUSWÄHLEN", null)') &&
   importer.includes('dialog.getButton(AlertDialog.BUTTON_POSITIVE)') &&
   importer.includes('return@setOnClickListener') &&
   importer.includes('pendingModelImport = Triple(modelId, modelVersion, sha256)'));
 ensure('invalid import and file chooser failure are visible without losing form',
   importer.includes('warning.text = "Bitte fülle alle drei Felder') &&
   importer.includes('pendingModelImport = null'));
 ensure('developer wizard has clear model-setup route and a full-width start button',
   developer?.includes('SPRACHMODELL EINRICHTEN') &&
   developer.includes('devStartButton!!, LinearLayout.LayoutParams(') &&
   developer.includes('dp(52)'));
});

test('new visual start screen is local, accessible and honest about capabilities',()=>{
 const art=fs.readFileSync(dir+'ContinuumHologramView.kt','utf8');
 const renderer=fs.readFileSync(dir+'ui/ContinuumHologramRenderer.kt','utf8');
 const homeView=fs.readFileSync(dir+'ui/ContinuumHomeView.kt','utf8');
 const copy=fs.readFileSync(dir+'ContinuumUiCopy.kt','utf8');
 const pane=bodyOf(activity,'private fun buildConversationPane(');
 const slots=bodyOf(activity,'private fun openHomeSlot(');
 ensure('drawing is local and motion follows visibility and power policy',
   art.includes('class ContinuumHologramView') &&
   art.includes('override fun onDraw(') &&
   art.includes('canAnimateFrame()') && art.includes('isPowerSaveMode') &&
   /foregroundActive\s*&&\s*motionAllowed\s*&&\s*isAttachedToWindow\s*&&\s*isShown\s*&&\s*windowVisibility\s*==\s*VISIBLE/.test(codeOnly(bodyOf(art,'canAnimateFrame'))) &&
   !/URL\(|https?:\/\//.test(art+renderer));
 ensure('start screen uses real native drawing and named slots',
   pane.includes('ContinuumHologramView(this)') && pane.includes('ContinuumHomeView('));
 const elementView=homeView.slice(homeView.indexOf('inner class ElementView'),homeView.indexOf('/** Standalone drawn element'));
 ensure('start-screen elements are keyboard and accessibility reachable',
   elementView.includes('isClickable = true') && elementView.includes('isFocusable = true') &&
   elementView.includes('contentDescription = element.describe(state)'));
 ensure('status labels and cards open the real settings and status screens',
   /Slot\.LABEL_CONNECTED -> showInternetSettings\(\)/.test(slots) &&
   /Slot\.SYSTEM_STATUS -> showSystemCenter\(\)/.test(slots) &&
   /Slot\.NOTIFICATIONS -> showDiagnosticsCenter\(\)/.test(slots));
 ensure('no automatic cloud fallback and no fake model readiness',
   copy.includes('Es gibt keinen automatischen Cloud-Ersatz.') &&
   copy.includes('Freies Sprachmodell noch nicht geladen'));
 ensure('novice dialog explains model/source/goal without Shizuku dependency',
   activity.includes('ContinuumUiCopy.developmentRequirements(') &&
   activity.includes('Shizuku') &&
   activity.includes('SPRACHMODELL EINRICHTEN'));
 ensure('missing web sources are not presented as verified knowledge',
   activity.includes('ContinuumUiCopy.emptyResearch(dossier.partialReason)'));
});

test('neural training failures expose bounded diagnostics without claiming saved weights',()=>{
 const neural=bodyOf(activity,'private fun runNeuralCommand(command: NeuralCommand)');
 ensure('neural error preserves the existing neural snapshot and discloses failure category',
   neural?.includes('neuralMonitor.text = "NEURON · Training fehlgeschlagen ($category), vorheriger Stand erhalten"') &&
   neural.includes('error.javaClass.simpleName.take(64)') &&
   neural.includes('Neuronales Experiment ${command.kind.name} fehlgeschlagen ($category)'));
 ensure('diagnostic text is bounded and visible instead of a silent failure',
   neural.includes('.take(240)') &&
   neural.includes('addToolActivity("NEURON · FEHLER · $category")'));
});

test('Wikipedia article research uses bounded authentic extracts and original citations',()=>{
 const provider=fs.readFileSync(dir+'cognitive/WikipediaExtractResearchProvider.kt','utf8');
 ensure('Android research wraps its existing HTTPS provider in a bounded Wikipedia extractor',
   activity.includes('val researchProvider=at.heinz.deviceai.cognitive.LegalRisEvidenceProvider(') &&
   activity.includes('at.heinz.deviceai.cognitive.WikipediaExtractResearchProvider(provider))') &&
   activity.includes('ApprovedWebResearchToolRunner(session,discovery,researchProvider,'));
 ensure('only German Wikipedia articles qualify',
   provider.includes('uri.host != "de.wikipedia.org"') &&
   provider.includes('uri.scheme != "https"') &&
   provider.includes('articleTitle(request.url) ?: return downstream.retrieve(request)'));
 ensure('wiki excerpt request is byte bounded and redirects forbidden',
   provider.includes('minOf(request.maxBytes, 24_000)') &&
   provider.includes('request.timeoutMillis, 0') &&
   provider.includes('exchars=1200'));
 ensure('retrieval retains source provenance and never fabricates a citation',
   provider.includes('source.url == endpoint && source.source.domain == "de.wikipedia.org"') &&
   provider.includes('ResearchDocument(request.url, title.replace('));
 ensure('XML external entities and unrelated article titles are not accepted',
   provider.includes('doctype|entity') &&
   provider.includes('expectedTitle.replace('));
});

test('Android-first Teamleader is scheduled, observable and serialized across background and chat',()=>{
 const native=fs.readFileSync(dir+'cognitive/DeviceTeamleader.kt','utf8');
 const job=fs.readFileSync(dir+'learning/ContinuumDeviceTeamleaderScheduler.kt','utf8');
 const kotlinTests=fs.readFileSync('app/src/test/java/at/heinz/deviceai/cognitive/DeviceTeamleaderTest.kt','utf8');
 ensure('Teamleader scheduling at app start',
   activity.includes('ContinuumDeviceTeamleaderScheduler(this).restore()'));
 ensure('native JobService protected in manifest',
   manifest.includes('.learning.ContinuumDeviceTeamleaderJobService') &&
   manifest.includes('android.permission.BIND_JOB_SERVICE'));
 ensure('chat can request actual Teamleader status and a local check',
   activity.includes('showDeviceTeamleaderChatStatus()') &&
   activity.includes('runDeviceTeamleaderCheck()') &&
   activity.includes('"teamleiter prüfen"'));
 ensure('no paid services or network required in scheduler',
   job.includes('networkType = JobInfo.NETWORK_TYPE_NONE') &&
   job.includes('charging = false') && job.includes('persisted = true'));
 ensure('worker generation has own cancellation flag and rejects overlap',
   job.includes('if (activeWorker?.isAlive == true) return false') &&
   job.includes('val cancellation = AtomicBoolean(false)'));
 ensure('background and manual checks use same process-wide lock',
   native.includes('private val CYCLE_LOCK = ReentrantLock()') &&
   native.includes('CYCLE_LOCK.tryLock()') &&
   native.includes('CYCLE_LOCK.unlock()'));
 ensure('Android never claims to edit code or silently completes tasks',
   native.includes('DevelopmentBacklog.nextEligible(doc)') &&
   !native.includes('startJob(') && !native.includes('finishJob('));
 ensure('busy worker does not overwrite progress',
   kotlinTests.includes('backgroundAndManualTeamleaderCannotOverlapOrOverwriteStatus'));
 ensure('status checkpoint supports atomic move fallback',
   native.includes('AtomicMoveNotSupportedException'));
 ensure('corrupt status remains intact rather than being overwritten by later jobs',
   native.includes('storage.read()') &&
   kotlinTests.includes('aCorruptedReportFailsClosedWithoutSilentlyResettingIt') &&
   kotlinTests.includes('damaged, file.readBytes()'));
 ensure('periodic background worker does not retry permanent failures in a tight loop',
   job.includes('jobFinished(params, false)') &&
   !job.includes('reschedule = result.state == "ERROR"'));
});

test('explicit M5 project planning command reaches reviewed builder without bypassing approval',()=>{
 const router=fs.readFileSync(dir+'ContinuumWorkerChatRouter.kt','utf8');
 const lab=fs.readFileSync(dir+'ContinuumM5LabActivity.kt','utf8');
 ensure('M5 command has dedicated typed route',
   router.includes('data object ProjectPlan : ContinuumWorkerChatCommand()') &&
   router.includes('"worker projektplan"') &&
   router.includes('ContinuumWorkerChatCommand.ProjectPlan'));
 ensure('chat launches existing project-plan activity not a shell job',
   activity.includes('ContinuumWorkerChatCommand.ProjectPlan -> openM5ProjectPlan()') &&
   activity.includes('private fun openM5ProjectPlan(initialDraft: String? = null)') &&
   activity.includes('initialDraft?.takeIf { it.length in 1..400 && it.none { char -> char.code < 0x20 } }') &&
   activity.includes('Intent(this, ContinuumM5LabActivity::class.java)') &&
   activity.includes('startActivity(planIntent)') &&
   activity.includes('is ContinuumWorkerChatCommand.ProjectPlanDraft -> openM5ProjectPlan(baumeister.request)'));
 ensure('external preview retains existing builder route',
   activity.includes('if (destination.externalPackage)') &&
   activity.includes('openMobileBuilder()'));
 ensure('M5 laboratory is non-exported and respects builder feature gate',
   manifest.includes('android:name=".ContinuumM5LabActivity" android:exported="false" android:enabled="${mobileBuilderEnabled}"'));
 ensure('carried M5 text stays an unsubmitted, bounded draft',
   router.includes('data class ProjectPlanDraft(val request: String)') &&
   router.includes('projectPlanDraftPattern.matchEntire(text)') &&
   lab.includes('intent.getStringExtra("m5_initial_draft_v1")') &&
   lab.includes('input.setText(carriedDraft?.take(512).orEmpty())'));
 ensure('consumed M5 approval is removed from the SavedState replay source before Builder launch',(()=>{
   const approved=lab.indexOf('conversation.approve(p.revision,p.planSha256!!)');
   const clear=lab.indexOf('acceptedTurns.clear()',approved);
   const launch=lab.indexOf('startActivity(Intent(this,ContinuumMobileBuilderActivity::class.java)',approved);
   return approved>=0 && clear>approved && launch>clear;
 })());
 ensure('the M5 handoff selects a project once and never reverts a later owner switch after recreation',(()=>{
   const builder=fs.readFileSync(dir+'ContinuumMobileBuilderActivity.kt','utf8');
   const guard=builder.indexOf('if (savedInstanceState == null) ContinuumOwnedProject.ofId(intent.getStringExtra("m5_project"))');
   const store=builder.indexOf('preferences.edit().putString("selected_m4_project_v1", it.id).commit()',guard);
   return guard>=0 && store>guard && builder.split('intent.getStringExtra("m5_project")').length===2;
 })());
 ensure('M5 approval remains hash, revision and empty-input bound',
   lab.includes('draftVersion==shownDraftVersion') &&
   lab.includes('conversation.approve(p.revision,p.planSha256!!)') &&
   lab.includes('input.text.isEmpty()') &&
   lab.includes('startActivity(Intent(this,ContinuumMobileBuilderActivity::class.java)'));
 ensure('M5 laboratory has no direct privileged execution',
   !lab.includes('RUN_COMMAND') && !lab.includes('dispatchCcc1(') && !lab.includes('dispatchApprovedCommand('));
});
