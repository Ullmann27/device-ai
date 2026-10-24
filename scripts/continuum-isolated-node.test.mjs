import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createServer} from 'node:net';
import {fileURLToPath} from 'node:url';

const script=fileURLToPath(new URL('./continuum-isolated-node.sh',import.meta.url));
const fixture=t=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-isolation-test-'));
 const stage=path.join(temp,'stage');fs.mkdirSync(stage,{mode:0o700});
 t.after(()=>fs.rmSync(temp,{recursive:true,force:true}));
 return {temp,stage,candidate:path.join(stage,'candidate.mjs')};
};
function hasNamespaceCapabilities(t){
 if(process.platform!=='linux'){t.skip('Linux-only sandbox');return false;}
 const probe=spawnSync('unshare',['--user','--map-root-user','--mount','--net',
   '--ipc','--pid','--fork','--kill-child','/bin/true'],
   {encoding:'utf8',timeout:5000});
 if(probe.status!==0){
  t.skip('Kernel namespace permissions unavailable; no isolation proven here');
  return false;
 }
 return true;
}

const run=(dir,file,env={})=>spawnSync('bash',[script,file],{
 encoding:'utf8',timeout:30000,maxBuffer:65536,
 env:{...process.env,...env,CONTINUUM_ISOLATION_STAGE:dir}
});

test('real Linux namespaces deny host files, environment, file writes and networking',async t=>{
 if(!hasNamespaceCapabilities(t))return;
 const server=createServer(socket=>socket.end());
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const listeningPort=server.address().port;
 t.after(()=>server.close());
 const f=fixture(t);
 const secret=path.join(f.temp,'host-secret');fs.writeFileSync(secret,'owner-private');
 fs.writeFileSync(f.candidate,[
  "import fs from 'node:fs';",
  "import net from 'node:net';",
  "const state={hostFile:fs.existsSync("+JSON.stringify(secret)+"),envSecret:process.env.CONTINUUM_ISOLATION_TEST_SECRET??null,outsideRoot:fs.existsSync('/etc/passwd'),pid:process.pid};",
  "try{fs.writeFileSync('/work/candidate.mjs','corrupt');state.writable=true;}catch(e){state.writeCode=e.code;}",
  "await new Promise(resolve=>{const s=net.createConnection({host:'127.0.0.1',port:"+listeningPort+"});s.setTimeout(1000,()=>{state.network='timeout';s.destroy();resolve();});s.on('connect',()=>{state.network='CONNECTED';s.destroy();resolve();});s.on('error',e=>{state.network=e.code;resolve();});});",
  "console.log(JSON.stringify(state));"
 ].join('\n'));
 const r=run(f.stage,f.candidate,{CONTINUUM_ISOLATION_TEST_SECRET:'should-never-appear'});
 assert.equal(r.status,0,r.stderr);
 const x=JSON.parse(r.stdout.trim());
 assert.equal(x.hostFile,false);
 assert.equal(x.outsideRoot,false);
 assert.equal(x.envSecret,null);
 assert.equal(x.writeCode,'EROFS');
 assert.equal(x.pid,1);
 assert.notEqual(x.network,undefined);
 assert.notEqual(x.network,'CONNECTED','host listener must be unreachable inside the net namespace');
 assert.equal(fs.readFileSync(secret,'utf8'),'owner-private');
});

test('unsafe stage location and symlink candidates are refused before running',t=>{
 const f=fixture(t);fs.writeFileSync(f.candidate,'console.log("should-not-run")');
 const external=path.join(f.temp,'external.mjs');fs.writeFileSync(external,'console.log("bad")');
 const outside=run(f.stage,external);
 assert.equal(outside.status,2);assert.doesNotMatch(outside.stdout,/bad/);
 const linked=path.join(f.stage,'alias.mjs');fs.symlinkSync(external,linked);
 const symlink=run(f.stage,linked);
 assert.equal(symlink.status,2);assert.doesNotMatch(symlink.stdout,/bad/);
 fs.chmodSync(f.stage,0o755);
 const loose=run(f.stage,f.candidate);
 assert.equal(loose.status,2);assert.match(loose.stderr,/stage must be mode|CONTINUUM_ISOLATION_UNAVAILABLE: missing tool: chroot/);
  assert.doesNotMatch(loose.stdout,/should-not-run/);
});

test('sandbox returns candidate failures, without treating them as successful repairs',t=>{
 if(!hasNamespaceCapabilities(t))return;
 const f=fixture(t);fs.writeFileSync(f.candidate,'throw new Error("EXPECTED_CANDIDATE_FAILURE")');
 const r=run(f.stage,f.candidate);
 assert.notEqual(r.status,0);
 assert.match(r.stderr,/EXPECTED_CANDIDATE_FAILURE/);
});
