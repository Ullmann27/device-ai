import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {createHash} from 'node:crypto';
import {verifiedPreview, createChromeDownloadServer} from './continuum-preview-chrome-download.mjs';

function fixture() {
  const state=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-chrome-'));
  const content=Buffer.alloc(500, 42);
  const digest=createHash('sha256').update(content).digest('hex');
  const file=path.join(state,`apk-${digest}.apk`);
  fs.writeFileSync(file,content);
  const report={schemaVersion:1,status:'EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED',
    commit:'a'.repeat(40),apk:file,apkSha256:digest,apkBytes:content.length,
    signerSha256:'b'.repeat(64),productionReady:false,deviceInstallationVerified:false};
  const write=()=>fs.writeFileSync(path.join(state,'latest-build.json'),JSON.stringify(report));
  write();
  return {state,file,content,digest,report,write,clean:()=>fs.rmSync(state,{recursive:true,force:true})};
}
function get(port, pathname='/', method='GET', host) {
  return new Promise((resolve,reject)=>{
    const req=http.request({host:'127.0.0.1',port,path:pathname,method,
      headers:host?{Host:host}:{}},res=>{
        const chunks=[];res.on('data',x=>chunks.push(x));
        res.on('end',()=>resolve({status:res.statusCode,headers:res.headers,body:Buffer.concat(chunks)}));
      });
    req.on('error',reject);req.end();
  });
}

test('verified state produces a local APK with exact digest',async t=>{
  const f=fixture();t.after(f.clean);
  const r=await verifiedPreview(f.state);
  assert.equal(r.file,f.file);assert.equal(r.digest,f.digest);
  assert.match(r.filename,/^DeviceAI-Continuum-Preview-aaaaaaa-[a-f0-9]{12}\.apk$/);
});
test('all failed or unverified status reports fail closed',async t=>{
  const f=fixture();t.after(f.clean);
  for(const status of ['FAILED_BUILD','NO_BUILD_REPORT','EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED']){
    f.report.status=status;f.report.deviceInstallationVerified=status.includes('EXPERIMENTAL');f.write();
    await assert.rejects(verifiedPreview(f.state));
  }
});
test('unrelated path and replaced APK bytes cannot be served',async t=>{
  const f=fixture();t.after(f.clean);
  f.report.apk='/etc/passwd';f.write();await assert.rejects(verifiedPreview(f.state));
  f.report.apk=f.file;f.write();fs.writeFileSync(f.file,Buffer.alloc(500,10));
  await assert.rejects(verifiedPreview(f.state),/SHA256/);
});
test('symlinks and wrong signer format are refused',async t=>{
  const f=fixture();t.after(f.clean);
  f.report.signerSha256='bad';f.write();await assert.rejects(verifiedPreview(f.state));
  f.report.signerSha256='b'.repeat(64);f.write();
  fs.renameSync(f.file,f.file+'.original');fs.symlinkSync(f.file+'.original',f.file);
  await assert.rejects(verifiedPreview(f.state));
});
test('loopback Chrome downloads actual APK with content-disposition and no cache',async t=>{
  const f=fixture();t.after(f.clean);
  const server=await createChromeDownloadServer({stateDir:f.state,port:0});t.after(()=>server.close());
  const port=server.address().port;
  const page=await get(port,'/');assert.equal(page.status,200);assert.match(page.body.toString(),/APK in Chrome herunterladen/);
  const apk=await get(port,'/DeviceAI-Continuum-Preview.apk');
  assert.equal(apk.status,200);assert.deepEqual(apk.body,f.content);
  assert.equal(apk.headers['content-type'],'application/vnd.android.package-archive');
  assert.equal(apk.headers['cache-control'],'no-store');
  assert.match(apk.headers['content-disposition'],/attachment; filename=/);
  const head=await get(port,'/DeviceAI-Continuum-Preview.apk','HEAD');
  assert.equal(head.status,200);assert.equal(head.body.length,0);
});
test('server rejects non-loopback Host, unrelated routes and POST',async t=>{
  const f=fixture();t.after(f.clean);
  const server=await createChromeDownloadServer({stateDir:f.state,port:0});t.after(()=>server.close());
  const port=server.address().port;
  assert.equal((await get(port,'/','GET','evil.example')).status,403);
  assert.equal((await get(port,'/secret')).status,404);
  assert.equal((await get(port,'/','POST')).status,405);
});
test('tampering after startup blocks subsequent download',async t=>{
  const f=fixture();t.after(f.clean);
  const server=await createChromeDownloadServer({stateDir:f.state,port:0});t.after(()=>server.close());
  const port=server.address().port;
  fs.writeFileSync(f.file,Buffer.alloc(500,10));
  const res=await get(port,'/DeviceAI-Continuum-Preview.apk');
  assert.equal(res.status,503);assert.match(res.body.toString(),/SHA256/);
});
test('full multi-megabyte APK survives request lifecycle without truncation',async t=>{
  const f=fixture();t.after(f.clean);
  f.content=Buffer.alloc(6*1024*1024,37);
  f.digest=createHash('sha256').update(f.content).digest('hex');
  fs.unlinkSync(f.file);f.file=path.join(f.state,`apk-${f.digest}.apk`);
  fs.writeFileSync(f.file,f.content);
  f.report.apk=f.file;f.report.apkSha256=f.digest;f.report.apkBytes=f.content.length;f.write();
  const server=await createChromeDownloadServer({stateDir:f.state,port:0});t.after(()=>server.close());
  const response=await get(server.address().port,'/DeviceAI-Continuum-Preview.apk');
  assert.equal(response.status,200);
  assert.equal(response.body.length,f.content.length);
  assert.equal(createHash('sha256').update(response.body).digest('hex'),f.digest);
});
