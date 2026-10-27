import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const a=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const shadow=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumCdx1ChatShadow.kt');
const core=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumLearningCore.kt');
const bridge=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumDecoderBridge.kt');
const cognitive=read('app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt');
const jUnit=read('app/src/test/java/at/heinz/deviceai/learning/ContinuumCdx1ChatShadowTest.kt');
const chat=a.split('private fun runContinuumDialogue(')[1]?.split('private fun beginNeuralCoach()')[0];
const input=a.split('private fun sendCurrentInput()')[1]?.split('private fun ')[0];

test('actual main Android chat dispatches opt-in CDX1 to existing conversation',()=>{
 assert.match(input,/ContinuumCdx1ChatShadow\.questionOrNull\(request\)/);
 assert.match(input,/runContinuumDialogue\(cdxQuestion, shadowResearch=true\)/);
 assert.match(chat,/CognitiveDecisionBridge\(/);
 assert.match(chat,/ContinuumCognitiveRuntime\(filesDir,core/);
});
test('one cognitive turn and one CDX1 shadow; no duplicate model call',()=>{
 assert.equal((chat.match(/\.respond\(request\.take\(512\)\)/g)||[]).length,1);
 assert.match(chat,/if\(shadowResearch\)\s+ContinuumCdx1ChatShadow\.observe\(/);
 assert.match(chat,/currentStatus=\{ core\.pairedDecoderStatus\(\) \}/);
 assert.match(chat,/experimentalPairedDecoderContinuation\(evidence,query,maxBytes=48\)/);
 assert.match(chat,/if\(!shadowResearch && LocalFirstPolicy\.afterMemory/);
});
test('approved conversation evidence is taken from exactly this cognitive turn',()=>{
 assert.match(chat,/memoryIds=turn\.memoryIds\.toSet\(\)/);
 assert.match(chat,/authorizedHits=decision\.evidenceHits/);
 assert.match(cognitive,/val evidenceHits:List<ContinuumMemoryHit>/);
 assert.match(shadow,/authorizedHits\.map \{ it\.id \}\.toSet\(\) != memoryIds/);
});
test('experimental output is appended as UNVERIFIED to canonical answer',()=>{
 assert.match(chat,/decision\.finalAnswer\+"\\n\\n"\+shadowObservation!!\.display\(\)/);
 assert.match(shadow,/SHADOW-DENKHELFER \(keine Chatfreigabe\)/);
 assert.match(shadow,/Experimentelle Ausgabe \(unbestätigt\)/);
 assert.match(shadow,/CDX1-Ausgaben werden weder an Gemma noch an Gerätewerkzeuge übergeben/);
});
test('no unapproved knowledge sources, checkpoints or weights can be used',()=>{
 assert.match(shadow,/it\.kind == "Bestätigte Nutzererinnerung"/);
 assert.match(shadow,/before\.conditioningState !in setOf\("PINNED_CURRENT", "PINNED_PREVIOUS"\)/);
 assert.match(shadow,/after\.sha256 != before\.sha256/);
 assert.match(shadow,/after\.updates != before\.updates/);
 assert.match(shadow,/toByteArray\(Charsets\.UTF_8\)\.size <= 160/);
 assert.match(core,/fun experimentalPairedDecoderContinuation/);
 assert.match(bridge,/fun experimentalPairedContinuation/);
 assert.doesNotMatch(shadow,/(?:trainExperimentalPairedDecoder|promote|grant|executeTool|syncOwnerFacts|confirmOwnerFact|requestTool)\(/);
});
test('bad/missing checkpoint returns non-success and does not manufacture output',()=>{
 assert.match(shadow,/NO_CURRENT_CHECKPOINT/);
 assert.match(shadow,/NO_GENERATION/);
 assert.match(shadow,/MODEL_CHANGED/);
 assert.match(jUnit,/changedWeightsDiscardUnverifiedGeneration/);
 assert.match(jUnit,/noMemoryAndMismatchNeverRunDecoder/);
 assert.match(jUnit,/unconfirmedSourceAndOversizedEvidenceRemainBlocked/);
});
