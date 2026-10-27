import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const root=new URL('../../app/src/main/java/at/heinz/deviceai/',import.meta.url);
const read=name=>fs.readFileSync(new URL(name,root),'utf8');
const activity=read('AgentActivity.kt');

test('one correlation follows actual main-chat memory, runtime and tool paths (source wiring)',()=>{
 assert.match(activity,/priorScopedGroundedQuestion=priorScopedTopic,turnTrace=turnTrace/);
 assert.match(activity,/runLocal\(request,modelQuestion,recall,turnTrace\)/);
 assert.match(activity,/generateLocal\(cancellation,turnTrace\)/);
 assert.match(activity,/LocalModelReasoningAdapter\(localRuntime,turnTrace=turnTrace\)/);
 assert.match(activity,/recordTrace\(snapshot\)/);
 assert.match(activity,/reviewTrace\(previous.second/);
 assert.match(read('cognitive/BoundedAgent.kt'),/turnTrace\?\.reference\(TraceStage.CALL_REF/);
 assert.match(activity,/evaluationConfirmsOutcome = false/);
});

test('model reply quality never authorizes an owner-memory write (source boundary)',()=>{
 const start=activity.indexOf('private fun runLocal(text:');
 const end=activity.indexOf('private fun initializeContinuum()',start);
 const modelPath=activity.slice(start,end);
 assert.doesNotMatch(modelPath,/persistRequestedMemory\(|memoryStore\.put\(/);
 assert.match(activity,/BootstrapKind.REMEMBER -> rememberOwnerFact/);
 assert.match(read('cognitive/CognitiveDecisionBridge.kt'),/private val reasoningAdapter: ContinuumReasoningAdapter\?/);
});

test('bounded multi-step request keeps the existing gate and never promotes legacy shell strings',()=>{
 assert.match(activity,/ReadOnlyToolPlan\.forRequest\(request, ToolProposal\(name, decision.arguments\)\)/);
 assert.match(activity,/maxToolCalls = plan.stepCount, maxRetries = 0/);
 assert.match(activity,/evaluationConfirmsOutcome = name == "get_storage_info"/);
 assert.match(activity,/decision.tool != "get_storage_info" && !bridge.isReady\(\)/);
});
