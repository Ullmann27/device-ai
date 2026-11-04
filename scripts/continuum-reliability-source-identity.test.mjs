import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

// Load the actual private production identity function in an isolated checkout.
// Only dependency URLs and a test-only export are added; no function body changes.
async function identityFixture(t) {
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-qa-source-'));
 t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
 fs.mkdirSync(path.join(directory,'scripts'));
 let source=fs.readFileSync(new URL('./continuum-reliability-qa.mjs',import.meta.url),'utf8');
 for(const relative of ['./continuum-owned-process.mjs','../.github/continuum/repair-diagnostics.mjs']) {
  const original="from '"+relative+"'";
  assert.equal(source.split(original).length,2,'The exact production dependency must exist once');
  source=source.replace(original,"from '"+new URL(relative,import.meta.url).href+"'");
 }
 const modulePath=path.join(directory,'scripts','identity-probe.mjs');
 fs.writeFileSync(modulePath,source+'\nexport {identity};\n');
 const loaded=await import(pathToFileURL(modulePath).href);
 const git=(...args)=>{
  const result=spawnSync('git',args,{cwd:directory,encoding:'utf8',timeout:10000,maxBuffer:16*1024*1024});
  assert.equal(result.status,0,result.stderr);return result.stdout;
 };
 return {directory,identity:loaded.identity,git};
}

test('full source identity retains every path beyond one MiB and detects tail mutations',async t=>{
 const f=await identityFixture(t);
 const prefix=Array.from({length:3},(_,i)=>'directory-'+i+'-'+'x'.repeat(220));
 const relativeDirectory=path.join(...prefix);
 fs.mkdirSync(path.join(f.directory,relativeDirectory),{recursive:true});
 const names=Array.from({length:1400},(_,i)=>path.join(relativeDirectory,
  'source-'+String(i).padStart(4,'0')+'-'+'y'.repeat(170)+'.txt'));
 for(const name of names)fs.writeFileSync(path.join(f.directory,name),'source '+name+'\n');
 f.git('init','-q','-b','source-identity-fixture');
 f.git('add','.');
 f.git('-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','full source identity fixture');
 const listing=f.git('ls-files','-z');
 assert.ok(Buffer.byteLength(listing)>1024*1024,'Regression needs the actual oversized Git output');
 const before=f.identity();
 assert.deepEqual(Object.keys(before.inputsSha256),listing.split('\0').filter(Boolean).sort());
 const tail=names.at(-1);
 const changed='changed final path beyond the previous output boundary\n';
 fs.writeFileSync(path.join(f.directory,tail),changed);
 const after=f.identity();
 assert.equal(after.commit,before.commit);
 assert.notEqual(after.treeDigest,before.treeDigest);
 assert.equal(after.inputsSha256[tail],createHash('sha256').update(changed).digest('hex'));
 const untracked=path.join(relativeDirectory,'z-untracked.txt');
 fs.writeFileSync(path.join(f.directory,untracked),'new untracked source\n');
 const extended=f.identity();
 assert.equal(Object.keys(extended.inputsSha256).length,Object.keys(before.inputsSha256).length+1);
 assert.ok(extended.inputsSha256[untracked]);
 assert.notEqual(extended.treeDigest,after.treeDigest);
});

test('full source identity still rejects missing Git metadata',async t=>{
 const f=await identityFixture(t);
 assert.throws(()=>f.identity(),/GIT_METADATA_UNAVAILABLE/);
});
