import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const installer=fileURLToPath(new URL('../../scripts/install-continuum-teamleader.sh',import.meta.url));

test('user-owned hourly timer supports a linked Git worktree and refuses overwrites',
 {skip:process.platform!=='linux'||!fs.existsSync('/bin/bash')},()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-worktree-timer-'));
 try{
  const repo=path.join(base,'checkout'),home=path.join(base,'home');
  const bin=path.join(base,'bin'),log=path.join(base,'systemctl.log');
  fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});
  fs.mkdirSync(home);
  fs.mkdirSync(bin);
  const script=path.join(repo,'scripts','install-continuum-teamleader.sh');
  fs.copyFileSync(installer,script);
  // Linked Git worktrees contain a .git file, unlike a normal clone.
  fs.writeFileSync(path.join(repo,'.git'),'gitdir: /offline/test/worktree\n');
  fs.writeFileSync(path.join(bin,'systemctl'),
   '#!/bin/sh\nprintf "%s\\n" "$*" >> "$FAKE_SYSTEMCTL_CALLS"\n',{mode:0o700});
  const env={...process.env,HOME:home,
   PATH:bin+path.delimiter+path.dirname(process.execPath)+path.delimiter+(process.env.PATH??''),
   FAKE_SYSTEMCTL_CALLS:log};
  const once=spawnSync('bash',[script],{env,encoding:'utf8',timeout:15000});
  assert.equal(once.status,0,once.stderr);
  const service=path.join(home,'.config','systemd','user','continuum-teamleader.service');
  const timer=path.join(home,'.config','systemd','user','continuum-teamleader.timer');
  const original=fs.readFileSync(service,'utf8');
  assert.ok(original.includes('ConditionPathExists='+repo+'/.git'),
   'systemd must accept both .git directories and linked-worktree .git files');
  assert.ok(original.includes('ExecStart=')&&original.includes('--once --root '+repo));
  assert.ok(fs.readFileSync(timer,'utf8').includes('OnCalendar=hourly'));
  assert.ok(fs.readFileSync(log,'utf8').includes('--user enable --now continuum-teamleader.timer'));
  const again=spawnSync('bash',[script],{env,encoding:'utf8',timeout:15000});
  assert.notEqual(again.status,0,'existing units must never be overwritten');
  assert.equal(fs.readFileSync(service,'utf8'),original);
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});
