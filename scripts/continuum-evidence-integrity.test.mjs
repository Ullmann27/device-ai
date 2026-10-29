import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {verifyEvidenceManifest} from './continuum-evidence-integrity.mjs';
const sha=x=>createHash('sha256').update(x).digest('hex');
const files=['qa-summary.json','tested-source-manifest.json','final-qa/summary.json'];
function fixture(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-proof-'));
 fs.mkdirSync(path.join(root,'final-qa'));
 const manifest={};
 for(const f of files){
  const data='payload of '+f;
  fs.writeFileSync(path.join(root,f),data);
  manifest[f]=sha(data);
 }
 fs.writeFileSync(path.join(root,'sha256-manifest.json'),JSON.stringify(manifest));
 return {root,manifest};
}
test('checks exact evidence bytes, no false external attestation',()=>{
 const f=fixture();try{
  const r=verifyEvidenceManifest(f.root,{minFiles:3});
  assert.equal(r.count,3);assert.equal(r.externallyAttested,false);
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});
test('fails when original test evidence changes',()=>{
 const f=fixture();try{
  fs.appendFileSync(path.join(f.root,files[0]),'TAMPER');
  assert.throws(()=>verifyEvidenceManifest(f.root,{minFiles:3}),/HASH_MISMATCH/);
 }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});
test('rejects symlinks and parent traversal',()=>{
 const f=fixture(),outside=path.join(os.tmpdir(),'continuum-proof-outside-'+process.pid);
 try{
  fs.writeFileSync(outside,'external');
  fs.rmSync(path.join(f.root,files[0]));
  fs.symlinkSync(outside,path.join(f.root,files[0]));
  assert.throws(()=>verifyEvidenceManifest(f.root,{minFiles:3}),/UNSAFE_EVIDENCE_PATH/);
  fs.rmSync(path.join(f.root,files[0]));
  fs.writeFileSync(path.join(f.root,files[0]),'payload of '+files[0]);
  f.manifest['../outside']=sha('external');
  fs.writeFileSync(path.join(f.root,'sha256-manifest.json'),JSON.stringify(f.manifest));
  assert.throws(()=>verifyEvidenceManifest(f.root,{minFiles:3}),/INVALID_EVIDENCE_ENTRY/);
 }finally{
  fs.rmSync(outside,{force:true});
  fs.rmSync(f.root,{recursive:true,force:true});
 }
});

test('FIFO masquerading as a log is rejected without blocking',
 {skip:process.platform!=='linux'},t=>{
  const f=fixture();
  try{
   fs.rmSync(path.join(f.root,files[0]));
   const status=spawnSync('mkfifo',[path.join(f.root,files[0])],{timeout:1500});
   if(status.status!==0)return t.skip('mkfifo unavailable');
   assert.throws(()=>verifyEvidenceManifest(f.root,{minFiles:3}),/UNSAFE_EVIDENCE_FILE/);
  }finally{fs.rmSync(f.root,{recursive:true,force:true});}
});
