import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {writeImmutableAnskRecipe} from './ansk-recipe-store.mjs';
import {verifyAnskExperiment, verifyAnskRecipeReceipt} from './ansk-lab-contract.mjs';

const SOURCE = 'app/src/main/java/at/heinz/deviceai/learning/MutationProbe.kt';
const IMPORT = 'java.nio.file.Files';
const HASH = character => character.repeat(64);

function profile(name, passed = true) {
  return {profile:name,passed,testProfile:name==='android-unit',
    exitCode:passed?0:1,timedOut:false,
    tests:{executed:name==='android-unit'?632:0,failed:0,skipped:0}};
}
function goodChain() {
  return {passed:true,results:[profile('android-compile'),profile('android-unit')]};
}
function recipeFixture() {
  const diagnosis={kind:'UNRESOLVED_REFERENCE',symbol:'Files',file:SOURCE};
  const observation=verifyAnskExperiment({
    head:'a'.repeat(40),sourceFile:SOURCE,importFqn:IMPORT,
    baselineSha256:HASH('b'),mutatedSha256:HASH('c'),
    primarySha256:HASH('d'),independentSha256:HASH('d'),
    patchSha256:HASH('e'),
    baselineRun:goodChain(),
    mutatedRun:{passed:false,results:[profile('android-compile',false)]},
    primaryRun:goodChain(),independentRun:goodChain(),
    diagnostic:diagnosis,
    strategiesAttempted:['KOTLIN_MISSING_IMPORT'],
    successfulStrategy:'KOTLIN_MISSING_IMPORT'
  });
  assert.equal(observation.verified,true);
  assert.equal(verifyAnskRecipeReceipt(observation.recipe).valid,true);
  return observation.recipe;
}
function scratch(fn) {
  const store=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-recipe-store-'));
  try{return fn(store);} finally{fs.rmSync(store,{recursive:true,force:true});}
}

test('Android-compatible exclusive copy saves verified receipt without hardlink()',()=>scratch(store=>{
  const oldLink=fs.linkSync;
  fs.linkSync=()=>{const err=Error('hardlinks blocked on Android');err.code='EACCES';throw err;};
  try{
    const recipe=recipeFixture();
    const filename=writeImmutableAnskRecipe(store,recipe);
    assert.equal(path.basename(filename),recipe.recipeSha256+'.json');
    assert.equal(fs.readFileSync(filename,'utf8'),JSON.stringify(recipe,null,2)+'\n');
    assert.equal(fs.statSync(filename).mode&0o077,0);
    assert.deepEqual(fs.readdirSync(path.dirname(filename)),[recipe.recipeSha256+'.json']);
  }finally{fs.linkSync=oldLink;}
}));

test('writing a second identical receipt is idempotent, preserves inode and content',()=>scratch(store=>{
 const recipe=recipeFixture();
 const file=writeImmutableAnskRecipe(store,recipe);
 const prior=fs.statSync(file);
 assert.equal(writeImmutableAnskRecipe(store,recipe),file);
 const after=fs.statSync(file);
 assert.equal(after.ino,prior.ino);
 assert.equal(fs.readFileSync(file,'utf8'),JSON.stringify(recipe,null,2)+'\n');
 assert.equal(fs.readdirSync(path.dirname(file)).length,1);
}));

test('a poisoned receipt at the hash path is not silently overwritten',()=>scratch(store=>{
 const recipe=recipeFixture();
 const file=writeImmutableAnskRecipe(store,recipe);
 fs.writeFileSync(file,'DO_NOT_OVERWRITE');
 assert.throws(()=>writeImmutableAnskRecipe(store,recipe),
   /ANSK_RECIPE_HASH_COLLISION_OR_TAMPERING/);
 assert.equal(fs.readFileSync(file,'utf8'),'DO_NOT_OVERWRITE');
 assert.equal(fs.readdirSync(path.dirname(file)).length,1);
}));

test('a symlinked recipe destination cannot be followed or overwrite its target',()=>scratch(store=>{
 const recipe=recipeFixture();
 const directory=path.join(store,'shadow-recipes');
 fs.mkdirSync(directory);
 const external=path.join(store,'outside');
 fs.writeFileSync(external,'SENSITIVE');
 fs.symlinkSync(external,path.join(directory,recipe.recipeSha256+'.json'));
 assert.throws(()=>writeImmutableAnskRecipe(store,recipe));
 assert.equal(fs.readFileSync(external,'utf8'),'SENSITIVE');
 assert.equal(fs.readdirSync(directory).length,1);
}));

test('a symlinked shadow-recipes directory is rejected before storing any bytes',()=>scratch(store=>{
 const other=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ansktarget-'));
 try{
  fs.symlinkSync(other,path.join(store,'shadow-recipes'),'dir');
  assert.throws(()=>writeImmutableAnskRecipe(store,recipeFixture()),
    /ANSK_UNSAFE_RECIPE_DIRECTORY/);
  assert.deepEqual(fs.readdirSync(other),[]);
 } finally {fs.rmSync(other,{recursive:true,force:true});}
}));

test('invalid or modified shadow receipts cannot be stored',()=>scratch(store=>{
 const recipe=recipeFixture();
 assert.throws(()=>writeImmutableAnskRecipe(store,{...recipe,importFqn:'java.nio.file.Paths'}),
   /ANSK_INVALID_RECIPE_RECEIPT/);
 assert.equal(fs.existsSync(path.join(store,'shadow-recipes')),false);
}));

test('non-absolute state path is rejected and checkout files remain untouched',()=>{
 assert.throws(()=>writeImmutableAnskRecipe('relative/state',recipeFixture()),
   /ANSK_INVALID_RECIPE_STORE/);
});
