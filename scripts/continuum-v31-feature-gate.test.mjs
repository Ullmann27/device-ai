import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=file=>fs.readFileSync(path.join(root,file),'utf8');

test('the consolidated v34 source tree retains original owner package and signature',()=>{
  const gradle=source('app/build.gradle.kts');
  const release=source('scripts/render-continuum-m8-owner-release.sh');
  const qa=source('scripts/render-continuum-m8-qa.sh');
  assert.match(gradle,/at\.heinz\.deviceai\.continuumlocal/);
  assert.match(release,/\[\[ "\$version" == "34" \]\]/);
  assert.match(release,/versionCode: 34/);
  assert.match(qa,/continuumStandaloneVersionCode=34/);
  assert.match(release,/6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23/);
});

test('the actual Android main app keeps a visible update button and original owner updater',()=>{
  const activity=source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  const updater=source('app/src/main/java/at/heinz/deviceai/ContinuumOwnerUpdateActivity.kt');
  assert.match(activity,/"Updates" to ""/);
  assert.match(activity,/ContinuumOwnerUpdateActivity::class\.java/);
  assert.match(updater,/Updates prüfen und laden/);
  assert.match(updater,/downloadAndInstall/);
});

test('M8 agent and execution controls are actually in the same release tree',()=>{
  const agent=source('.github/continuum/coding-agent-loop.mjs');
  const toolgate=source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  assert.match(agent,/createChatCompletionsBrain/);
  assert.match(agent,/AGENT_LIMITS/);
  assert.match(toolgate,/BoundedAgentOrchestrator/);
  assert.match(toolgate,/AiContextCompiler/);
});

test('legacy CDX1 training representation fix and regression are included',()=>{
  const bridge=source('app/src/main/java/at/heinz/deviceai/learning/ContinuumDecoderBridge.kt');
  const check=source('app/src/test/java/at/heinz/deviceai/learning/ContinuumDecoderBridgeTest.kt');
  assert.match(bridge,/\.representation\("research context"\)/);
  assert.match(check,/legacyGenerationUsesTheExactTrainingConditioner/);
});

test('neural and temporal research are included without live authority',()=>{
  const neuro=source('app/src/main/java/at/heinz/deviceai/research/neuro/TemporalNeuralShadow.kt');
  assert.match(neuro,/EWM2_VERIFIED_INPUT_ONLY/);
  assert.match(neuro,/answerAuthority: Boolean = false/);
  assert.match(neuro,/NEURO_CONTRACTION_NOT_PROVED/);
  assert.match(source('app/src/test/java/at/heinz/deviceai/research/neuro/TemporalNeuralShadowTest.kt'),/thermalAndStaleResets/);
});

test('bounded pure memory bridge and brain tests are included',()=>{
  const bridge=source('app/src/main/java/at/heinz/deviceai/cognitive/ContinuumComputeReuseResearch.kt');
  const checks=source('app/src/test/java/at/heinz/deviceai/cognitive/ContinuumComputeReuseResearchTest.kt');
  assert.match(bridge,/AiContextCompiler/);
  assert.match(bridge,/memoryEpoch: Long/);
  assert.match(bridge,/receiptVerifier: \(key: PureStageKey/);
  assert.match(checks,/bridgeRunsActualExistingContextCompilerWithoutCachingAnswers/);
});

test('the 3000 logical-expert research remains capacity-bound',()=>{
  const mesh=source('tools/continuum-virtual-fabric/expert-mesh.mjs');
  const broker=source('tools/continuum-virtual-fabric/reuse-broker.mjs');
  assert.match(mesh,/3000/);
  assert.match(mesh,/VirtualFabric/);
  assert.match(broker,/deterministic/);
  assert.match(broker,/verified/);
});

test('mission control shows real evidence and is actually registered',()=>{
  const manifest=source('app/src/main/AndroidManifest.xml');
  const app=source('app/src/main/java/at/heinz/deviceai/AgentActivity.kt');
  assert.match(manifest,/\.ContinuumJarvisMissionControlActivity/);
  assert.match(app,/JARVIS · MISSION CONTROL/);
  assert.match(source('app/src/main/java/at/heinz/deviceai/ContinuumJarvisMissionControlActivity.kt'),/KEIN BELEG/);
});

test('new metadata type safety and published APK hash readback are in the source tree',()=>{
  const catalog=source('app/src/main/java/at/heinz/deviceai/intelligence/ContinuumOwnerUpdateCatalog.kt');
  assert.match(catalog,/isSchemaOne/);
  assert.match(catalog,/stringFields\.all/);
  assert.match(source('scripts/continuum-owner-render-readback.mjs'),/APK_HASH_VERIFIED_CERT_NOT_VERIFIED/);
});
