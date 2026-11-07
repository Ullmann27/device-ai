import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {RelationalEncoder} from './cre-encoder.mjs';
import {resolveCefrCheckpoint,loadCefrCheckpoint,promoteCefrCheckpoint,restoreCefrLastKnownGood} from './cefr-dev-checkpoint.mjs';
const sha256=x=>crypto.createHash('sha256').update(x).digest('hex');
function fixture(fn){
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-cefr-store-'));
 const repoRoot=path.join(base,'repo'),stateDir=path.join(base,'state');
 fs.mkdirSync(repoRoot);fs.mkdirSync(stateDir);
 try{return fn({repoRoot,stateDir,base});}
 finally{fs.rmSync(base,{recursive:true,force:true});}
}
function promotion(seed){
 const bytes=new RelationalEncoder(seed).save();
 return {accepted:true,activeBytes:bytes,activeSha256:sha256(bytes)};
}
test('developer checkpoint is outside repo, initially absent and created by CAS only',()=>fixture(o=>{
 const filename=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 assert.ok(filename.startsWith(o.stateDir+path.sep));
 assert.equal(loadCefrCheckpoint(o).bytes,null);
 const promoted=promotion(27);
 const stored=promoteCefrCheckpoint({...o,promotion:promoted,expectedSha256:null});
 assert.equal(stored.sha256,promoted.activeSha256);
 const reread=loadCefrCheckpoint(o);
 assert.ok(reread.bytes.equals(promoted.activeBytes));
 assert.equal(reread.sha256,promoted.activeSha256);
 assert.equal(fs.statSync(filename).mode&0o077,0);
}));
test('CAS refuses overwrites with obsolete SHA and preserves original checkpoint',()=>fixture(o=>{
 const first=promotion(27);
 promoteCefrCheckpoint({...o,promotion:first,expectedSha256:null});
 assert.throws(()=>promoteCefrCheckpoint({...o,promotion:promotion(28),
  expectedSha256:null}),/changed concurrently/);
 assert.equal(loadCefrCheckpoint(o).sha256,first.activeSha256);
 const second=promotion(28);
 promoteCefrCheckpoint({...o,promotion:second,expectedSha256:first.activeSha256});
 assert.equal(loadCefrCheckpoint(o).sha256,second.activeSha256);
}));
test('refuses unapproved promotions, mismatched bytes and corrupt checkpoint',()=>fixture(o=>{
 const first=promotion(42);
 assert.throws(()=>promoteCefrCheckpoint({...o,promotion:{...first,accepted:false}}));
 assert.throws(()=>promoteCefrCheckpoint({...o,promotion:{...first,activeSha256:'a'.repeat(64)}}));
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 fs.writeFileSync(file,'not-a-model');
 assert.throws(()=>loadCefrCheckpoint(o));
}));
test('rejects checkpoint path inside the working Git repository and symlinked target',()=>fixture(o=>{
 assert.throws(()=>resolveCefrCheckpoint(o.repoRoot,o.repoRoot),/outside/);
 const inside=path.join(o.repoRoot,'internal');fs.mkdirSync(inside);
 assert.throws(()=>resolveCefrCheckpoint(inside,o.repoRoot),/outside/);
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 fs.symlinkSync(path.join(o.repoRoot,'missing'),file);
 assert.throws(()=>loadCefrCheckpoint(o));
}));
test('concurrent lock rejects writes and does not overwrite trusted weights',()=>fixture(o=>{
 const first=promotion(71);
 promoteCefrCheckpoint({...o,promotion:first});
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 fs.writeFileSync(file+'.lock','held');
 assert.throws(()=>promoteCefrCheckpoint({...o,promotion:promotion(72),
  expectedSha256:first.activeSha256}),/EEXIST/);
 assert.equal(loadCefrCheckpoint(o).sha256,first.activeSha256);
}));

test('promoting a new developer model keeps the previous verified bytes as last-known-good',
 ()=>fixture(o=>{
 const first=promotion(17),second=promotion(18);
 promoteCefrCheckpoint({...o,promotion:first});
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 assert.equal(fs.existsSync(file+'.lkg'),false);
 promoteCefrCheckpoint({...o,promotion:second,expectedSha256:first.activeSha256});
 assert.ok(fs.readFileSync(file+'.lkg').equals(first.activeBytes));
 assert.ok(fs.readFileSync(file).equals(second.activeBytes));
 assert.equal(fs.statSync(file+'.lkg').mode&0o077,0);
 const reverted=restoreCefrLastKnownGood({...o,expectedSha256:second.activeSha256});
 assert.equal(reverted.sha256,first.activeSha256);
 assert.ok(loadCefrCheckpoint(o).bytes.equals(first.activeBytes));
}));

test('rollback requires the exact active checkpoint SHA, and refuses tampered LKG',
 ()=>fixture(o=>{
 const first=promotion(19),second=promotion(20);
 promoteCefrCheckpoint({...o,promotion:first});
 promoteCefrCheckpoint({...o,promotion:second,expectedSha256:first.activeSha256});
 assert.throws(()=>restoreCefrLastKnownGood({...o,
  expectedSha256:first.activeSha256}),/ROLLBACK_EXPECTED_SHA_MISMATCH/);
 assert.equal(loadCefrCheckpoint(o).sha256,second.activeSha256);
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 fs.writeFileSync(file+'.lkg','corrupt');
 assert.throws(()=>restoreCefrLastKnownGood({...o,
  expectedSha256:second.activeSha256}));
 assert.equal(loadCefrCheckpoint(o).sha256,second.activeSha256);
}));

test('an explicitly SHA-matched corrupted active generation restores valid LKG',
 ()=>fixture(o=>{
 const first=promotion(23),second=promotion(24);
 promoteCefrCheckpoint({...o,promotion:first});
 promoteCefrCheckpoint({...o,promotion:second,expectedSha256:first.activeSha256});
 const file=resolveCefrCheckpoint(o.stateDir,o.repoRoot);
 const damaged=Buffer.from('corrupt active neural weights');
 fs.writeFileSync(file,damaged);
 assert.throws(()=>loadCefrCheckpoint(o));
 assert.throws(()=>restoreCefrLastKnownGood({...o,
  expectedSha256:second.activeSha256}),/SHA_MISMATCH/);
 const restored=restoreCefrLastKnownGood({...o,
  expectedSha256:sha256(damaged)});
 assert.equal(restored.sha256,first.activeSha256);
 assert.ok(loadCefrCheckpoint(o).bytes.equals(first.activeBytes));
}));
