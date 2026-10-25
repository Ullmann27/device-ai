import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {readOwnedNpmLockAtCommit} from './owned-lock-provenance.mjs';
const good={lockfileVersion:3,packages:{'':{dependencies:{demo:'1.0.0'}},'node_modules/demo':{version:'1.0.0'}}};
function setup(t){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'omega-provenance-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 fs.mkdirSync(path.join(dir,'backend'));
 fs.writeFileSync(path.join(dir,'backend/package-lock.json'),JSON.stringify(good));
 const run=(...args)=>{const r=spawnSync('git',args,{cwd:dir,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 run('init','-q','-b','research-provenance');run('add','.');run('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','baseline');
 const sourceCommit=run('rev-parse','HEAD');
 return {dir,sourceCommit,run,read:(options={})=>readOwnedNpmLockAtCommit({repoRoot:dir,sourceCommit,...options})};
}
test('reads an exact committed lockfile with Git SHA and SHA-256',t=>{
 const f=setup(t),x=f.read();
 assert.equal(x.sourceCommit,f.sourceCommit);assert.equal(x.lockfilePath,'backend/package-lock.json');
 assert.match(x.gitBlobSha1,/^[a-f0-9]{40}$/);assert.match(x.lockfileSha256,/^[a-f0-9]{64}$/);
 assert.equal(x.lock.packages['node_modules/demo'].version,'1.0.0');
});
test('uncommitted tracked changes do not silently become inventory',t=>{
 const f=setup(t);
 fs.writeFileSync(path.join(f.dir,'backend/package-lock.json'),'{"lockfileVersion":3,"packages":{}}');
 assert.throws(()=>f.read(),/OMEGA_GIT_PROVENANCE_DIRTY_SOURCE/);
});
test('stale and invalid source pin fails closed',t=>{
 const f=setup(t);
 assert.throws(()=>f.read({sourceCommit:'a'.repeat(40)}),/OMEGA_GIT_PROVENANCE_STALE_HEAD/);
 assert.throws(()=>f.read({sourceCommit:'main'}),/OMEGA_GIT_PROVENANCE_INVALID/);
});
test('path escapes and arbitrary file paths are forbidden',t=>{
 const f=setup(t);
 for(const lockfilePath of ['../secret','backend/../package-lock.json','.git/config','backend/package.json','/etc/passwd'])
  assert.throws(()=>f.read({lockfilePath}),/OMEGA_GIT_PROVENANCE_INVALID/);
});
test('Git symlink entries are refused',t=>{
 const f=setup(t);
 fs.unlinkSync(path.join(f.dir,'backend/package-lock.json'));
 fs.symlinkSync('../secret',path.join(f.dir,'backend/package-lock.json'));
 f.run('add','-A');f.run('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','symlink');
 assert.throws(()=>f.read({sourceCommit:f.run('rev-parse','HEAD')}),/OMEGA_GIT_PROVENANCE_NOT_BLOB/);
});
test('source checkout must be the exact repository root',t=>{
 const f=setup(t);
 assert.throws(()=>readOwnedNpmLockAtCommit({repoRoot:path.join(f.dir,'backend'),sourceCommit:f.sourceCommit}),/OMEGA_GIT_PROVENANCE_WRONG_ROOT/);
});
test('invalid committed lock does not become a trusted inventory',t=>{
 const f=setup(t);
 fs.writeFileSync(path.join(f.dir,'backend/package-lock.json'),'not-json');f.run('add','-A');f.run('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','invalid');
 assert.throws(()=>f.read({sourceCommit:f.run('rev-parse','HEAD')}),/OMEGA_GIT_PROVENANCE_NOT_JSON/);
});
