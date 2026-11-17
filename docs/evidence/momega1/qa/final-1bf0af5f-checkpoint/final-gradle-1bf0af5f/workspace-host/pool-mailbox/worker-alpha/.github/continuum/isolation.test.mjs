import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import net from 'node:net';
  test('Chief fixed Node workspace probe',async()=>{
   assert.equal(fs.readFileSync('/work/worker-alpha.txt','utf8'),'worker-alpha');
   assert.throws(()=>fs.readFileSync('/workspace/momega1-chief/AGENTS.md'),{code:'ENOENT'});
   assert.throws(()=>fs.writeFileSync('/work/worker-alpha.txt','escape'),e=>['EROFS','EACCES'].includes(e.code));
   assert.equal(fs.readFileSync('/sys/fs/cgroup/memory.max','utf8').trim(),'268435456');
   assert.equal(fs.readFileSync('/sys/fs/cgroup/pids.max','utf8').trim(),'64');
   await new Promise((resolve,reject)=>{const s=net.connect({host:'1.1.1.1',port:443});s.setTimeout(1000);s.once('error',()=>resolve());s.once('connect',()=>{s.destroy();reject(Error('network escape'));});s.once('timeout',()=>{s.destroy();reject(Error('unconfirmed network block'));});});
   await new Promise(resolve=>setTimeout(resolve,3000));
  });