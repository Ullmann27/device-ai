// M16 · call-chain proof: every component built in M9-M16 is reached from an existing production or worker path,
// in the order that keeps it SHADOW. Source contracts in the style of the other *-wiring tests.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const APP='app/src/main/java/at/heinz/deviceai/';

test('Gemma path: runLocal audits a grounded answer before the trace is published, answer unchanged',()=>{
 const a=read(APP+'AgentActivity.kt');
 const audit=a.indexOf('LocalModelClaimAudit.stage(run.response, groundedRecall)');
 const publish=a.indexOf('publishCognitiveTrace(turnTrace,if(successful)TraceOutcome.MODEL_INTERPRETATION');
 assert.ok(audit>0&&publish>audit,'audit stage is recorded before the trace is published');
 assert.match(a.slice(audit-200,audit),/successful && groundedRecall != null/);
 const audits=read(APP+'cognitive/LocalModelClaimAudit.kt');
 for(const stage of ['CLAIM_AUDIT_SUPPORTED','CLAIM_AUDIT_UNSUPPORTED','CLAIM_AUDIT_SKIPPED','CLAIM_AUDIT_CONFLICT'])
  assert.ok(audits.includes('TraceStage.'+stage),stage);
 assert.match(read(APP+'cognitive/CognitiveTurnTrace.kt'),/CLAIM_AUDIT_CONFLICT/);
});

test('dialogue path: LocalConversationContext feeds the improved F1 kernel through the existing shadow',()=>{
 assert.match(read(APP+'cognitive/LocalConversationContext.kt'),/argonShadow\.observeUser\(sessionId,request\)/);
 assert.match(read(APP+'cognitive/Argon4SystemShadow.kt'),/DialogStateKernel\(it\)/);
 const k=read(APP+'research/argon4/DialogStateKernel.kt');
 for(const marker of ['NO_PERSON_REFERENT','GENDER_UNKNOWN','PERSON_WINDOW','an (dem|diesem|jenem)'])assert.ok(k.includes(marker),marker);
});

test('memory path: the EWM2 shadow comparison runs the timeline check and the sibling resolver on the same active facts',()=>{
 const b=read(APP+'learning/ContinuumBrain.kt');
 const shadow=b.slice(b.indexOf('if(mode==ContinuumBrainMode.SHADOW) {'),b.indexOf('val (answer,facts)=asked'));
 assert.match(shadow,/core\.activeOwnerFacts\(\)/);
 assert.match(shadow,/MemoryTimeline\.check\(answer\.cited,active\)/);
 assert.match(shadow,/MemoryQuestionResolver\.resolve\(/);
 assert.match(shadow,/return ContinuumBrainResult\(baseline,mode,ContinuumBrainStatus\.SHADOW\)/,'SHADOW still returns the baseline');
 assert.match(read(APP+'cognitive/MemoryQuestionResolver.kt'),/JustifiedRelationStore\(\)/);
});

test('worker path: owner lease release and host-vs-code diagnosis are reachable from existing entry points',()=>{
 const cli=read('.github/continuum/teamleader-cli.mjs');
 assert.match(cli,/'--release-dead-lease'/);
 assert.match(cli,/releaseDeadContinuumBuildLease\(root\)/);
 assert.match(read('scripts/android-kotlin-unit-check.sh'),/test-failure-environment\.mjs/);
});

test('research labs stay research: no production module imports them',()=>{
 for(const f of ['.github/continuum/repair-loop.mjs','.github/continuum/repair-ledger.mjs','.github/continuum/autonomous-teamleader.mjs'])
  assert.doesNotMatch(read(f),/repair-transfer-lab|ccc1-counterexample-lab/,f);
});
