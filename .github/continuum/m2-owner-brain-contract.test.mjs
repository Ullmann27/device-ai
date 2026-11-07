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
const statusEntry=bodyOf(app,'showM2MilestoneStatus');
const smoke=bodyOf(app,'runLocalSmokeTest');
const observation=read('scripts/continuum-local-model-observation.mjs');
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
 ['14 fresh attempt revokes stale evidence',()=>assert.match(smoke,/if \(direct\) resetM2NativeProof\(\)/)],
 ['15 readiness never claims offline test from native reply',()=>assert.match(m2,/Offline-Test bei abgeschaltetem Internet: NICHT NACHGEWIESEN/)],
 ['16 readiness never claims authentic model patch by metadata',()=>assert.match(m2,/Modellgenerierter CCC1-Patch: NICHT NACHGEWIESEN/)],
 ['17 readiness requires independent RED and fresh GREEN',()=>assert.match(m2,/RED → GREEN → frischer unabhängiger Test: NICHT NACHGEWIESEN/)],
 ['18 readiness never claims long-term learning by sample',()=>assert.match(m2,/Lernen aus geprüfter Reparatur nach Neustart: NICHT NACHGEWIESEN/)],
 ['19 command decoder remains pure and non-executing',()=>assert.doesNotMatch(route,/Runtime\.getRuntime|ProcessBuilder|startActivity\(|execFileSync|fetch\(/)],
 ['20 direct model test never accepts a broad arbitrary command',()=>assert.match(route,/"meilenstein modelltest", "continuum gemma test", "gemma modelltest"/)],
 ['21 production local coding CLI imports preflight gate',()=>assert.match(cli,/import \{requireCodingModelPreflight\} from '\.\/continuum-m2-model-preflight\.mjs'/)],
 ['22 coding and legacy patch routes enforce admission before generation',()=>{
    const g=cli.indexOf('requireCodingModelPreflight({');
    const request=cli.indexOf('const result=await queryLocalBrain({',g);
    assert.ok(g>0 && request>g,'legacy patch preflight precedes generation');
    assert.match(cli,/import \{queryPinnedLocalCoding\} from '\.\/continuum-local-model-observation\.mjs'/);
    assert.match(cli,/if\(command==='coding'\) \{\s*const result=await queryPinnedLocalCoding\(\{\.\.\.request,env:process\.env\}\)/);
    const pinned=bodyOf(observation,'queryPinnedLocalCoding');
    const preflight=pinned.indexOf('requireCodingModelPreflight({');
    const manifest=pinned.indexOf('readPinnedGgufManifest(');
    const admission=pinned.indexOf('admitGgufFile(');
    const launch=pinned.indexOf('launchLocalModel(');
    assert.ok(preflight>=0 && manifest>preflight && admission>manifest && launch>admission,
      'pinned coding validates consent/provenance/model before any native generation');
    assert.match(pinned,/actualRuntimeHash!==runtimeHash/);
    assert.match(pinned,/admitted\.sha256!==pin\.sha256/);
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
 ['33 harness safely redacts arbitrary errors',()=>assert.match(harnessCli,/HARNESS_UNAVAILABLE_OR_INVALID/)],
 ['34 model load always resets stale session proof',()=>assert.match(app,/private fun loadModel\(model: ImportedLocalModel\) \{[\s\S]*?resetM2NativeProof\(\)/)],
 ['35 model unload always resets stale session proof',()=>assert.match(app,/private fun unloadModel\(\) \{[\s\S]*?resetM2NativeProof\(\)/)],
 ['36 model import always resets stale session proof',()=>assert.match(app,/private fun importModel\(uri: Uri\) \{[\s\S]*?resetM2NativeProof\(\)/)]
];
for(const [name,assertion] of expectations)test('M2 contract '+name,assertion);
