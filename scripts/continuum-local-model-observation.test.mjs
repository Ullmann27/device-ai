import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import * as provider from '../.github/continuum/coding-provider.mjs';

test('normal caller cannot certify a serialized runtime observation',()=>{
 assert.equal(typeof provider.isTrustedLocalModelObservation,'function');
 assert.equal(provider.isTrustedLocalModelObservation({schema:'momega1-local-process-observation-v1',evidenceClass:'LOCAL_MODEL_HOST'}),false);
});

test('coding CLI blocks endpoint-only configuration before any network/model call',()=>{
 const result=spawnSync(process.execPath,['scripts/continuum-local-brain-cli.mjs','coding'],{
  cwd:path.resolve(import.meta.dirname,'..'),encoding:'utf8',input:JSON.stringify({userPrompt:'repair fixture',allowedTrackedPaths:['lib/pages.mjs'],timeoutMs:1000}),
  env:{CONTINUUM_LOCAL_BRAIN_API_KEY:'a'.repeat(40),CONTINUUM_LOCAL_BRAIN_MODEL:'approved-fixture',CONTINUUM_LOCAL_BRAIN_RUNTIME:'llama.cpp',
   CONTINUUM_LOCAL_BRAIN_MODEL_FORMAT:'gguf',CONTINUUM_LOCAL_BRAIN_MODEL_SHA256:'b'.repeat(64),CONTINUUM_ALLOW_ALTERNATE_CODING_MODEL:'OWNER_APPROVED_ALTERNATE_GGUF'},timeout:3000});
 assert.equal(result.status,1);
 assert.match(result.stderr,/BLOCKED_LOCAL_MODEL_UNAVAILABLE/);
 assert.equal(result.stdout,'');
});

test('unapproved or absent local weights never produce runtime observations',async()=>{
 const {queryPinnedLocalCoding}=await import('./continuum-local-model-observation.mjs');
 await assert.rejects(queryPinnedLocalCoding({userPrompt:'repair',allowedTrackedPaths:['lib/a.mjs'],timeoutMs:1000}),/BLOCKED_LOCAL_MODEL_UNAVAILABLE/);
});
