import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const leader=fs.readFileSync('.github/continuum/autonomous-teamleader.mjs','utf8');
const repair=fs.readFileSync('.github/continuum/repair-loop.mjs','utf8');
const planner=fs.readFileSync('.github/continuum/development-planner.mjs','utf8');
const gate=fs.readFileSync('.github/continuum/m4-local-gate.mjs','utf8');
const runtime=fs.readFileSync('app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt','utf8');
const launch=fs.readFileSync('scripts/astra-max-execute.sh','utf8');

test('baseline repair writes independently reviewed evidence to the persistent backlog',()=>{
 assert.match(leader,/if\(\['BASELINE_REPAIR','OWNER_REPAIR'\]\.includes\(chosen\.kind\)\)/);
 assert.doesNotMatch(leader,/\['BASELINE_REPAIR','OWNER_REPAIR','OWNER_TEST'\]\.includes\(chosen\.kind\)/);
 assert.match(leader,/\{syncBacklogOps,repairSessionOps\}/);
 assert.match(leader,/repairSessionOps\(backlog\.read\(\)\.doc,publicReport/);
 assert.match(leader,/publicReport\.outcome==='VERIFIED'&&publicReport\.independent\?\.passed!==true/);
 assert.match(leader,/backlog\.apply\(session\.ops,\{stopOnError:true\}\)/);
 assert.match(leader,/review-summary\.md/);
 assert.match(planner,/report\.independent\?\.passed===true/);
 assert.match(planner,/evidenceProfiles\.map/);
});

test('repair metadata cannot advertise local generative inference that did not run',()=>{
 assert.match(repair,/inference:\{mode:'DETERMINISTIC_ONLY',modelId:null,modelInvocations:0/);
 assert.doesNotMatch(repair,/mode:modelStatus\.available\?'LOCAL_MODEL_ASSISTED'/);
});

test('local model keeps isolated thinking resource until close and rejects cross-chunk tools',()=>{
 assert.match(runtime,/val toolTurnGate = LocalToolTurnGate\(\)/);
 assert.match(runtime,/!toolTurnGate\.accept\(message\.toolCalls\.size\)/);
 assert.match(runtime,/finish\(LocalInferenceEvent\.Failed\(/);
 assert.match(runtime,/val closed = runCatching/);
 assert.match(runtime,/if \(activeConversation\.isAlive\) activeConversation\.close\(\)/);
 assert.match(runtime,/if \(inferenceConversation === activeConversation\) inferenceConversation = null/);
 assert.match(runtime,/Triple\(conversation, inferenceConversation, engine\)/);
});

test('mandatory Android parity and separate repair E2E gate remain enforced',()=>{
 assert.match(gate,/r\.profile!=='android-unit'\|\|\(r\.tests\?\.skipped\?\?0\)===0/);
 assert.match(gate,/validRepairE2eRecordShape\(repairE2e\)/);
 assert.match(launch,/\[\[ "\$branch" == "continuum\/argon-safety-review-20261017" \]\]/);
 assert.match(launch,/\[\[ -z "\$\(git status --porcelain\)" \]\]/);
 assert.match(launch,/node \.github\/continuum\/teamleader-cli\.mjs --loop/);
 assert.match(launch,/bash scripts\/local-m4-android\.sh "\$head"/);
});
