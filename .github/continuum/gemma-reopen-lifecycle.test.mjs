import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=rel=>fs.readFileSync(path.join(root,rel),'utf8');
const activity=read('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
const policy=read('app/src/main/java/at/heinz/deviceai/ContinuumGemmaRestorePolicy.kt');
const catalog=read('app/src/main/java/at/heinz/deviceai/localmodel/LocalModelCatalog.kt');
const runtime=read('app/src/main/java/at/heinz/deviceai/localmodel/LiteRtLmLocalModelRuntime.kt');
const host=read('app/src/main/java/at/heinz/deviceai/localmodel/LocalModelHost.kt');
const tests=read('app/src/test/java/at/heinz/deviceai/ContinuumGemmaRestorePolicyTest.kt');

function block(start,end){const i=activity.indexOf(start),j=activity.indexOf(end,i+start.length);
 assert.ok(i>=0&&j>i, 'Lifecycle anchor missing: '+start);
 return activity.slice(i,j);
}
const startup=block('override fun onCreate(savedInstanceState: Bundle?)','override fun onResume()');
const resume=block('override fun onResume()','override fun onPause()');
const destroy=block('override fun onDestroy()','private fun buildUi()');
const restore=block('private fun restoreOriginalGemmaOnOpen()','private fun loadModel(model: ImportedLocalModel)');
const load=block('private fun loadModel(model: ImportedLocalModel)','private fun unloadModel()');
const unload=block('private fun unloadModel()','private fun importModel(uri: Uri)');
const modelImport=block('private fun importModel(uri: Uri)','private fun reloadInstalledModels()');

test('Gemma restoration is scheduled once on Activity creation, not on every onResume',()=>{
 assert.match(startup,/reloadInstalledModels\(\)[\s\S]*restoreOriginalGemmaOnOpen\(\)/);
 assert.doesNotMatch(resume,/restoreOriginalGemmaOnOpen\(/);
});
test('restore is not a separate worker, timer or background service',()=>{
 assert.match(restore,/onLearningIo\(\{ modelCatalog\.all\(\) \}\)/);
 assert.doesNotMatch(restore,/Timer|Thread\(|startForeground|startService|download|https?:/i);
});
test('only owner-selected original SHA and version can be restored',()=>{
 assert.match(policy,/model\.id == ContinuumSelectedModels\.LOCAL_ID/);
 assert.match(policy,/model\.version == ContinuumSelectedModels\.LOCAL_VERSION/);
 assert.match(policy,/model\.sha256 == ContinuumSelectedModels\.LOCAL_SHA256/);
 assert.match(restore,/ContinuumGemmaRestorePolicy\.candidate\(/);
});
test('restart cannot start a second engine while loading, generating or unloading',()=>{
 assert.match(policy,/state != LocalModelState\.EMPTY/);
 assert.match(policy,/loaded != null/);
 assert.match(restore,/state = localRuntime\.state/);
 assert.match(restore,/loaded = localRuntime\.loadedModel/);
});
test('owner manual unload disables restoration immediately',()=>{
 assert.match(unload,/putBoolean\(ContinuumGemmaRestorePolicy\.KEY_ENABLED, false\)/);
 assert.match(unload,/localRuntime\.unload/);
});
test('a verified successful original load alone enables future restore',()=>{
 assert.match(load,/result\.onSuccess \{ loaded ->/);
 assert.match(load,/ContinuumGemmaRestorePolicy\.isSelectedOriginal\(loaded\)/);
 assert.match(load,/KEY_ENABLED/);
});
test('a failed load stops repeated startup retries',()=>{
 assert.match(load,/result\.onFailure/);
 assert.match(load,/ContinuumGemmaRestorePolicy\.isSelectedOriginal\(model\)/);
 assert.match(load,/putBoolean\(ContinuumGemmaRestorePolicy\.KEY_ENABLED, false\)/);
});
test('process-wide loaded Gemma is intentionally retained at Activity finish',()=>{
 assert.match(host,/One model catalog and one LiteRT-LM runtime per process/);
 assert.match(destroy,/val retainGemma/);
 assert.match(destroy,/!retainGemma/);
 assert.match(destroy,/localRuntime\.unload\(\)/);
});
test('a cold restart verifies the stored file SHA-256 before initializing the engine',()=>{
 assert.match(runtime,/catalog\.get\(id, version\)/);
 assert.match(catalog,/if \(verifyBytes && sha256\(file\) != record\.sha256\) return null/);
 assert.match(runtime,/newEngine\.initialize\(\)/);
});
test('the old model download is never required on restart',()=>{
 assert.doesNotMatch(policy,/\b(import|fetch|download|network|http)\s*\(/);
 assert.doesNotMatch(restore,/importModel\(|showModelImportDialog\(|startActivityForResult\(/);
});
test('a fresh restoration does not invent a successful local answer',()=>{
 assert.doesNotMatch(restore,/m2DirectProofModelSha|m2DirectProofElapsedMs|addAssistantMessage\("MODELLTEST/);
 assert.match(load,/resetM2NativeProof\(\)/);
});
test('original 3.66 GB model import remains separate and SHA-verified',()=>{
 assert.match(modelImport,/modelCatalog\.import\(/);
 assert.match(catalog,/MessageDigest\.getInstance\("SHA-256"\)/);
});
test('regression suite covers correct original, opt-out, foreign SHA, busy engine',()=>{
 for(const name of ['reopenOriginalOnlyWhenEnabledEmptyAndNotYetLoaded',
 'explicitUnloadPreferenceWinsOverImportedOriginal','doNotUseDifferentModelDespiteMatchingDisplayName',
 'neverCreateSecondNativeEngineDuringActiveLifecycle'])
 assert.match(tests,new RegExp(name));
});
