import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {parseJunit,runNodeProfile,verifyRepairPatch} from './ccc1-redgreen-verifier.mjs';
const hash=b=>createHash('sha256').update(b).digest('hex');
const git=(root,...args)=>{const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
function fixture(t){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'momega-rg-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const root=path.join(dir,'repo');fs.mkdirSync(path.join(root,'src'),{recursive:true});fs.mkdirSync(path.join(root,'tests'));
 git(root,'init','-q');fs.writeFileSync(path.join(root,'src/math.mjs'),'export const add=(a,b)=>a-b;\n');
 fs.writeFileSync(path.join(root,'tests/math.test.mjs'),"import test from 'node:test';import assert from 'node:assert/strict';import {add} from '../src/math.mjs';test('addition',()=>assert.equal(add(2,3),5));\n");
 const commit=()=>{git(root,'add','.');git(root,'-c','user.name=t','-c','user.email=t@invalid','commit','-qm','fixture');return git(root,'rev-parse','HEAD');};
 const red=commit();fs.writeFileSync(path.join(root,'src/math.mjs'),'export const add=(a,b)=>a+b;\n');const green=commit();
 const patch=spawnSync('git',['diff','--binary','--full-index',red,green],{cwd:root}).stdout,patchPath=path.join(dir,'fix.patch');fs.writeFileSync(patchPath,patch);
 const receipt={schema:'continuum-ccc1-patch-receipt',version:1,redSha:red,greenSha:green,patchSha256:hash(patch),targetTests:['math.addition'],red:{sha:red,executed:1,failed:1,failingTests:['math.addition']},green:{sha:green,executed:1,failed:0,failingTests:[],passedTargets:['math.addition']}};
 return {dir,root,red,green,patchPath,patchSha256:hash(patch),receipt};
}
test('MΩ PR400 self-closing suite does not capture following actual Node cases',t=>{
 const f=fixture(t),run=runNodeProfile(f.root,f.green);assert.equal(run.ok,true);
 // This prefix is valid JUnit and can be emitted for an empty suite by reporters.
 const raw=spawnSync(process.execPath,['--test','--test-reporter=junit','tests/math.test.mjs'],{cwd:f.root,encoding:'utf8',env:{PATH:process.env.PATH,HOME:f.dir}});assert.equal(raw.status,0);
 const original=parseJunit(raw.stdout,{root:f.root}),prefixed=parseJunit('<testsuite name="empty"/>'+raw.stdout,{root:f.root});
 assert.deepEqual([...prefixed.values()].map(c=>c.identity),[...original.values()].map(c=>c.identity));
});
test('MΩ PR400 actual Node identities survive a symlinked TMPDIR',t=>{
 const f=fixture(t),real=path.join(f.dir,'real-temp'),alias=path.join(f.dir,'temp-alias');fs.mkdirSync(real);fs.symlinkSync(real,alias);
 const old=process.env.TMPDIR;process.env.TMPDIR=alias;t.after(()=>{if(old===undefined)delete process.env.TMPDIR;else process.env.TMPDIR=old;});
 const receiptBytes=Buffer.from(JSON.stringify(f.receipt));
 const r=verifyRepairPatch({repo:f.root,baseCommit:f.red,patchPath:f.patchPath,patchSha256:f.patchSha256,receiptBytes});
 assert.equal(r.verdict,'VERIFIED',r.reasons.join(','));
});
for(const field of ['patchSha256','red.sha','green.sha'])test('MΩ quality embedded patch receipt rejects '+field,t=>{
 const f=fixture(t),receipt=structuredClone(f.receipt);
 if(field==='patchSha256')receipt.patchSha256='f'.repeat(64);else receipt[field.split('.')[0]].sha='f'.repeat(40);
 const result=verifyRepairPatch({repo:f.root,baseCommit:f.red,patchPath:f.patchPath,patchSha256:f.patchSha256,receiptBytes:Buffer.from(JSON.stringify(receipt))});
 assert.equal(result.verdict,'REJECTED',JSON.stringify(result));
});
