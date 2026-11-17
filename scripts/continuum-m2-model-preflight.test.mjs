import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';
import {inspectCodingModelPreflight,requireCodingModelPreflight,
  OWNER_GEMMA_ID,OWNER_GEMMA_SHA256,OWNER_ALTERNATE_CONSENT} from './continuum-m2-model-preflight.mjs';

const approved={
  model:'owner-reviewed-local-coder',
  runtime:'llama.cpp',
  format:'gguf',
  sha256:'a'.repeat(64),
  alternateConsent:OWNER_ALTERNATE_CONSENT
};
const invalid=[
  ['01: no model', {model:undefined},'MODEL_ID_REQUIRED'],
  ['02: empty model', {model:''},'MODEL_ID_REQUIRED'],
  ['03: whitespace model', {model:' '},'MODEL_ID_REQUIRED'],
  ['04: NUL model', {model:'coder\u0000'},'MODEL_ID_REQUIRED'],
  ['05: linebreak model', {model:'coder\nunsafe'},'MODEL_ID_REQUIRED'],
  ['06: oversized name', {model:'x'.repeat(129)},'MODEL_ID_REQUIRED'],
  ['07: unspecified local fallback', {model:'local',format:undefined},'MODEL_ID_UNSPECIFIED'],
  ['07a: implicit default alias', {model:'default'},'MODEL_ID_UNSPECIFIED'],
  ['07b: automatic model alias', {model:'auto'},'MODEL_ID_UNSPECIFIED'],
  ['08: missing format', {format:undefined},'EXPLICIT_GGUF_FORMAT_REQUIRED'],
  ['09: unexpected format spelling', {format:'GGUF'},'EXPLICIT_GGUF_FORMAT_REQUIRED'],
  ['10: selected original Gemma name', {model:OWNER_GEMMA_ID},'GEMMA_LITERTLM_NOT_GGUF'],
  ['11: original selected file', {model:'gemma-4-E4B-it.litertlm'},'GEMMA_LITERTLM_NOT_GGUF'],
  ['12: selected native LiteRT format', {format:'litertlm'},'GEMMA_LITERTLM_NOT_GGUF'],
  ['13: unsupported native runtime', {runtime:'litertlm'},'UNSUPPORTED_CODING_TRANSPORT'],
  ['14: remote runtime', {runtime:'remote-api'},'UNSUPPORTED_CODING_TRANSPORT'],
  ['15: omitted checkpoint digest', {sha256:undefined},'EXPLICIT_GGUF_DIGEST_REQUIRED'],
  ['16: malformed digest', {sha256:'f'.repeat(63)},'EXPLICIT_GGUF_DIGEST_REQUIRED'],
  ['17: uppercase digest', {sha256:'A'.repeat(64)},'EXPLICIT_GGUF_DIGEST_REQUIRED'],
  ['18: digest with junk', {sha256:'f'.repeat(63)+'x'},'EXPLICIT_GGUF_DIGEST_REQUIRED'],
  ['19: Gemma original digest wrongly asserted as GGUF', {sha256:OWNER_GEMMA_SHA256},'GEMMA_DIGEST_FORMAT_MISMATCH'],
  ['20: consent absent', {alternateConsent:undefined},'ALTERNATE_MODEL_CONSENT_REQUIRED'],
  ['21: consent not explicit', {alternateConsent:'true'},'ALTERNATE_MODEL_CONSENT_REQUIRED'],
  ['22: consent mismatched', {alternateConsent:'QWEN_DEFAULT'},'ALTERNATE_MODEL_CONSENT_REQUIRED']
];
for(const [name,override,expected] of invalid) {
  test('M2 model admission denies '+name,()=>{
    const config={...approved,...override};
    const result=inspectCodingModelPreflight(config);
    assert.equal(result.ready,false);
    assert.equal(result.status,expected);
    assert.equal(result.actualInference,false);
    assert.equal(result.ownerGemmaExecuted,false);
    assert.equal(result.verifiedRepair,false);
    assert.equal(Object.isFrozen(result),true);
    assert.throws(()=>requireCodingModelPreflight(config),error=>error.code===expected);
  });
}

test('explicit approved alternate GGUF is configuration ONLY, not inference proof',()=>{
  const result=requireCodingModelPreflight(approved);
  assert.equal(result.ready,true);
  assert.equal(result.configuredModel,approved.model);
  assert.equal(result.configuredSha256,approved.sha256);
  assert.equal(result.status,'CONFIGURATION_ONLY_PENDING_RUNTIME_PROOF');
  assert.equal(result.actualInference,false);
  assert.equal(result.ownerGemmaExecuted,false);
  assert.equal(result.verifiedRepair,false);
  assert.equal(Object.isFrozen(result),true);
});

test('a Qwen name NEVER enters absent explicit consent and remains only configuration with consent',()=>{
  const denied=inspectCodingModelPreflight({...approved,model:'qwen2.5-coder-1.5b',alternateConsent:undefined});
  assert.equal(denied.status,'ALTERNATE_MODEL_CONSENT_REQUIRED');
  const allowed=inspectCodingModelPreflight({...approved,model:'qwen2.5-coder-1.5b'});
  assert.equal(allowed.ready,true);
  assert.equal(allowed.actualInference,false);
  assert.equal(allowed.ownerGemmaExecuted,false);
});

test('production coding CLI blocks missing pinned runtime before legacy Gemma configuration',()=>{
  const cli=resolve(dirname(fileURLToPath(import.meta.url)),'continuum-local-brain-cli.mjs');
  const req={allowedTrackedPaths:['lib/foo.mjs'],timeoutMs:1500,userPrompt:'Fix broken foo'};
  const env={...process.env,CONTINUUM_LOCAL_BRAIN_API_KEY:'x'.repeat(40),
    CONTINUUM_LOCAL_BRAIN_MODEL:OWNER_GEMMA_ID,
    CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',
    CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'a'.repeat(64),
    CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:OWNER_ALTERNATE_CONSENT
  };
  for(const key of ['MODEL_PATH','RUNTIME_PATH','MODEL_MANIFEST','RUNTIME_SHA256','RUNTIME_KIND'])delete env['CONTINUUM_LOCAL_BRAIN_'+key];
  const child=spawnSync(process.execPath,[cli,'coding'],{input:JSON.stringify(req),
    encoding:'utf8',timeout:10000,env});
  assert.equal(child.status,1,child.stderr);
  assert.equal(child.error,undefined);assert.equal(child.signal,null);
  assert.equal(child.stderr,'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: BLOCKED_LOCAL_MODEL_UNAVAILABLE\n');
  assert.equal(child.stdout,'');
  assert.doesNotMatch(child.stderr,/Fix broken foo|x{40}/);
});

test('production coding CLI blocks missing pinned runtime despite legacy unspecified-model credentials',()=>{
  const cli=resolve(dirname(fileURLToPath(import.meta.url)),'continuum-local-brain-cli.mjs');
  const env={...process.env,CONTINUUM_LOCAL_BRAIN_API_KEY:'x'.repeat(40),
    CONTINUUM_LOCAL_BRAIN_MODEL:'local',
    CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',
    CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'a'.repeat(64)};
  delete env.CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL;
  for(const key of ['MODEL_PATH','RUNTIME_PATH','MODEL_MANIFEST','RUNTIME_SHA256','RUNTIME_KIND'])delete env['CONTINUUM_LOCAL_BRAIN_'+key];
  const child=spawnSync(process.execPath,[cli,'coding'],{
    input:JSON.stringify({userPrompt:'Fix',allowedTrackedPaths:['lib/foo.mjs'],timeoutMs:1500}),
    encoding:'utf8',timeout:10000,env});
  assert.equal(child.status,1,child.stderr);
  assert.equal(child.error,undefined);assert.equal(child.signal,null);
  assert.equal(child.stderr,'LOCAL_MODEL_UNAVAILABLE_OR_INVALID: BLOCKED_LOCAL_MODEL_UNAVAILABLE\n');
  assert.equal(child.stdout,'');
});

test('harness coding CLI cannot bypass the selected Gemma format gate',()=>{
  const harness=resolve(dirname(fileURLToPath(import.meta.url)),'continuum-harness-coding-cli.mjs');
  const env={...process.env,
    CONTINUUM_LOCAL_BRAIN_MODEL:OWNER_GEMMA_ID,
    CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',
    CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'a'.repeat(64),
    CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:OWNER_ALTERNATE_CONSENT
  };
  const child=spawnSync(process.execPath,[harness],{input:'{}',encoding:'utf8',
    timeout:10000,env});
  assert.equal(child.status,1,child.stderr);
  assert.match(child.stderr,/LOCAL_MODEL_UNAVAILABLE_OR_INVALID: GEMMA_LITERTLM_NOT_GGUF/);
  assert.equal(child.stdout,'');
});
