import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const app=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const selected=read('app/src/main/java/at/heinz/deviceai/ContinuumSelectedModels.kt');
const m2=read('app/src/main/java/at/heinz/deviceai/ContinuumM2Status.kt');
const route=read('app/src/main/java/at/heinz/deviceai/ContinuumWorkerChatRouter.kt');
const cli=read('scripts/continuum-local-brain-cli.mjs');
const harnessCli=read('scripts/continuum-harness-coding-cli.mjs');
const guard=read('scripts/continuum-m2-model-preflight.mjs');
const verifier=read('.github/continuum/ccc1-independent-verifier.mjs');
const agent=read('.github/continuum/ccc1-worker.mjs');
const statusEntry=app.split('private fun showM2MilestoneStatus()')[1]?.split('private fun showDeviceTeamleaderChatStatus()')[0];
const smoke=app.split('private fun runLocalSmokeTest(')[1]?.split('private fun runLocal(')[0];
const expectations=[
 ['01 pinned owner Gemma ID',()=>assert.match(selected,/LOCAL_ID = "gemma4-e4b-it"/)],
 ['02 pinned owner Gemma digest',()=>assert.match(selected,/LOCAL_SHA256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"/)],
 ['03 original Gemma file is LiteRT, not GGUF',()=>assert.match(selected,/LOCAL_FILENAME = "gemma-4-E4B-it\.litertlm"/)],
 ['04 main app exposes safe milestone status',()=>assert.match(app,/private fun showM2MilestoneStatus\(\)/)],
 ['05 status binds real imported model SHA',()=>assert.match(statusEntry,/model\.sha256 == ContinuumSelectedModels\.LOCAL_SHA256/)],
 ['06 status binds real loaded model SHA',()=>assert.match(statusEntry,/loaded\.sha256 == ContinuumSelectedModels\.LOCAL_SHA256/)],
 ['07 status checks native runtime readiness',()=>assert.match(statusEntry,/LocalModelState\.READY/)],
 ['08 status checks same model session receipt',()=>assert.match(statusEntry,/m2DirectProofModelSha == loaded\?\.sha256/)],
 ['09 native diagnostic starts only on explicit chat command',()=>assert.match(app,/ContinuumWorkerChatCommand\.MilestoneModelTest -> runLocalSmokeTest\(direct = true\)/)],
 ['10 native diagnostic calls actual localRuntime.stream',()=>assert.match(smoke,/if\(direct\)localRuntime\.stream\(question,callback\)/)],
 ['11 diagnostic rejects blank generated text',()=>assert.match(smoke,/check\(generated\.response\.isNotBlank\(\)\)/)],
 ['12 diagnostic never executes tool proposals',()=>assert.match(smoke,/generated\.proposal == null && generated\.rejectedToolResponses\.isEmpty\(\)/)],
 ['13 measured response tied to chosen original SHA',()=>assert.match(smoke,/testedModel\.sha256 == ContinuumSelectedModels\.LOCAL_SHA256/)],
 ['14 fresh attempt revokes stale evidence',()=>assert.match(smoke,/m2DirectProofModelSha = null[\s\S]*m2DirectProofElapsedMs = null/)],
 ['15 readiness never claims offline test from native reply',()=>assert.match(m2,/Offline-Test bei abgeschaltetem Internet: NICHT NACHGEWIESEN/)],
 ['16 readiness never claims authentic model patch by metadata',()=>assert.match(m2,/Modellgenerierter CCC1-Patch: NICHT NACHGEWIESEN/)],
 ['17 readiness requires independent RED and fresh GREEN',()=>assert.match(m2,/RED → GREEN → frischer unabhängiger Test: NICHT NACHGEWIESEN/)],
 ['18 readiness never claims long-term learning by sample',()=>assert.match(m2,/Lernen aus geprüfter Reparatur nach Neustart: NICHT NACHGEWIESEN/)],
 ['19 command decoder remains pure and non-executing',()=>assert.doesNotMatch(route,/Runtime\.getRuntime|ProcessBuilder|startActivity\(|execFileSync|fetch\(/)],
 ['20 direct model test never accepts a broad arbitrary command',()=>assert.match(route,/"meilenstein modelltest", "continuum gemma test", "gemma modelltest"/)],
 ['21 production local coding CLI imports preflight gate',()=>assert.match(cli,/import \{requireCodingModelPreflight\} from '\.\/continuum-m2-model-preflight\.mjs'/)],
 ['22 coding CLI checks runtime before queryLocalCodingBrain',()=>{
    const g=cli.indexOf('requireCodingModelPreflight({');
    const request=cli.indexOf("const result=await (command==='coding'?queryLocalCodingBrain:queryLocalBrain)");
    assert.ok(g>0 && request>g);
 }],
 ['23 local original LiteRT is rejected on llama CPP boundary',()=>assert.match(guard,/GEMMA_LITERTLM_NOT_GGUF/)],
 ['24 alternative model always needs explicit owner consent',()=>assert.match(guard,/ALTERNATE_MODEL_CONSENT_REQUIRED/)],
 ['25 configuration decision never asserts runtime or repair success',()=>assert.match(guard,/actualInference:false,ownerGemmaExecuted:false,verifiedRepair:false/)],
 ['26 no model caller can edit Git in the preflight',()=>assert.doesNotMatch(guard,/from 'node:child_process'|from 'node:fs'|fetch\(|spawnSync\(|execSync\(/)],
 ['27 original CCC1 still protects test identities',()=>assert.match(verifier,/EXECUTED_TEST_IDENTITY_MISSING/)],
 ['28 original CCC1 still protects source patch scope',()=>assert.match(verifier,/PATCH_SCOPE_INVALID/)],
 ['29 CCC1 task keeps explicit owner permission',()=>assert.ok(agent.includes("permissions:[REPAIR_CAPABILITY+':'+chain]"))],
 ['30 model status is exposed as text without training',()=>assert.doesNotMatch(m2,/\.train\(|trainStep\(|saveExperienceFile\(|writeFileSync\(/)],
 ['31 harness coding CLI enforces same owner model preflight',()=>assert.match(harnessCli,/requireCodingModelPreflight\(\{/)],
 ['32 harness CLI rejects format mismatch before generation',()=>{
    const guardPos=harnessCli.indexOf('requireCodingModelPreflight({');
    const modelPos=harnessCli.indexOf('const reply=await queryHarnessCodingBrain(');
    assert.ok(guardPos>0 && modelPos>guardPos);
 }],
 ['33 harness safely redacts arbitrary errors',()=>assert.match(harnessCli,/HARNESS_UNAVAILABLE_OR_INVALID/)]

];
for(const [name,assertion] of expectations)test('M2 contract '+name,assertion);
