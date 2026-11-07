import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {ensureCcc1StateOutsideSource,canonicalProspectiveState} from './ccc1-path-policy.mjs';

test('CCC1 rejects repo-local state, even when path hides behind symlinked ancestors', t => {
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ccc1-state-'));
  t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
  const repo=path.join(home,'repo'); fs.mkdirSync(repo);
  const alias=path.join(home,'outside-looking-link'); fs.symlinkSync(repo,alias,'dir');
  assert.throws(()=>ensureCcc1StateOutsideSource(repo,path.join(repo,'state')),/CCC1_STATE_INSIDE_SOURCE/);
  assert.throws(()=>ensureCcc1StateOutsideSource(repo,alias),/CCC1_STATE_INSIDE_SOURCE/);
  assert.throws(()=>ensureCcc1StateOutsideSource(repo,path.join(alias,'new','nested')),/CCC1_STATE_INSIDE_SOURCE/);
  assert.equal(ensureCcc1StateOutsideSource(repo,path.join(home,'state','nested')),path.join(home,'state','nested'));
});

test('CCC1 refuses file targets and malformed state paths', t=>{
  const home=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ccc1-invalid-'));
  t.after(()=>fs.rmSync(home,{recursive:true,force:true}));
  const project=path.join(home,'project');fs.mkdirSync(project);
  const file=path.join(home,'ordinary-file');fs.writeFileSync(file,'x');
  assert.throws(()=>canonicalProspectiveState(''),/CCC1_STATE_PATH_INVALID/);
  assert.throws(()=>canonicalProspectiveState('\0'),/CCC1_STATE_PATH_INVALID/);
  assert.throws(()=>ensureCcc1StateOutsideSource(project,file),/CCC1_STATE_NOT_DIRECTORY/);
  assert.throws(()=>ensureCcc1StateOutsideSource(project,path.join(file,'child')),/CCC1_STATE_NOT_DIRECTORY/);
});
