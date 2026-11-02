import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const installer=fileURLToPath(new URL('../../scripts/install-continuum-teamleader.sh',import.meta.url));

test('activation failure rolls back units and a following attempt can succeed',
 {skip:process.platform!=='linux'||!fs.existsSync('/bin/bash')},()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-rollback-'));
 try{
  const repo=path.join(base,'repo'),home=path.join(base,'home'),bin=path.join(base,'bin');
  fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});
  fs.mkdirSync(home);fs.mkdirSync(bin);
  fs.writeFileSync(path.join(repo,'.git'),'gitdir: /offline/worktree\n');
  const script=path.join(repo,'scripts','install-continuum-teamleader.sh');
  fs.copyFileSync(installer,script);
  fs.writeFileSync(path.join(bin,'systemctl'),
   '#!/bin/sh\ncase "$*" in *"enable --now"*) test "$FAKE_FAIL_ENABLE" != "1";; *) exit 0;; esac\n',
   {mode:0o700});
  const env={...process.env,HOME:home,
   PATH:bin+path.delimiter+path.dirname(process.execPath)+path.delimiter+(process.env.PATH??'')};
  const dir=path.join(home,'.config/systemd/user'),
   service=path.join(dir,'continuum-teamleader.service'),
   timer=path.join(dir,'continuum-teamleader.timer');
  const fail=spawnSync('bash',[script],{
   env:{...env,FAKE_FAIL_ENABLE:'1'},encoding:'utf8',timeout:15000
  });
  assert.notEqual(fail.status,0,fail.stderr);
  assert.match(fail.stderr,/rolled back/);
  assert.equal(fs.existsSync(service),false);
  assert.equal(fs.existsSync(timer),false);
  const retry=spawnSync('bash',[script],{env,encoding:'utf8',timeout:15000});
  assert.equal(retry.status,0,retry.stderr);
  assert.ok(fs.existsSync(service));
  assert.ok(fs.existsSync(timer));
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});

test('dangling systemd unit symlink is preserved and rejected',
 {skip:process.platform!=='linux'||!fs.existsSync('/bin/bash')},()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-symlink-'));
 try{
  const repo=path.join(base,'repo'),home=path.join(base,'home'),bin=path.join(base,'bin');
  fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});
  fs.mkdirSync(home);fs.mkdirSync(bin);
  fs.writeFileSync(path.join(repo,'.git'),'gitdir: /offline/worktree\n');
  const script=path.join(repo,'scripts','install-continuum-teamleader.sh');
  fs.copyFileSync(installer,script);
  fs.writeFileSync(path.join(bin,'systemctl'),'#!/bin/sh\nexit 0\n',{mode:0o700});
  const dir=path.join(home,'.config/systemd/user');
  fs.mkdirSync(dir,{recursive:true});
  const link=path.join(dir,'continuum-teamleader.service');
  fs.symlinkSync('/nonexistent/dangling',link);
  const env={...process.env,HOME:home,
   PATH:bin+path.delimiter+path.dirname(process.execPath)+path.delimiter+(process.env.PATH??'')};
  const result=spawnSync('bash',[script],{env,encoding:'utf8',timeout:15000});
  assert.notEqual(result.status,0);
  assert.equal(fs.readlinkSync(link),'/nonexistent/dangling');
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});
