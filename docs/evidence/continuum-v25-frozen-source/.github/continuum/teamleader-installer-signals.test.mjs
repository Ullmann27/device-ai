import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const installer=fileURLToPath(new URL('../../scripts/install-continuum-teamleader.sh',import.meta.url));

async function untilExists(file,child){
 const until=Date.now()+3000;
 while(!fs.existsSync(file)){
  if(child.exitCode!==null)throw Error('Installer exited before entering activation');
  if(Date.now()>until)throw Error('Installer activation marker timeout');
  await new Promise(resolve=>setTimeout(resolve,20));
 }
}

test('SIGTERM during hourly timer activation removes only installer-owned units',
 {skip:process.platform!=='linux'||!fs.existsSync('/bin/bash'),timeout:10000},
 async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-signal-test-'));
  let child=null;
  try{
   const repo=path.join(root,'checkout'),home=path.join(root,'home');
   const bin=path.join(root,'bin'),marker=path.join(root,'activation-started');
   const log=path.join(root,'systemctl.log');
   fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});
   fs.mkdirSync(home);fs.mkdirSync(bin);
   fs.writeFileSync(path.join(repo,'.git'),'gitdir: /offline/worktree\n');
   fs.copyFileSync(installer,path.join(repo,'scripts/install-continuum-teamleader.sh'));
   const fake=path.join(bin,'systemctl');
   fs.writeFileSync(fake,
    '#!/bin/sh\n'+
    'echo "$*" >> "$FAKE_LOG"\n'+
    'case "$*" in *"enable --now"*) echo READY > "$FAKE_MARKER"; exec sleep 8;; esac\n'+
    'exit 0\n',{mode:0o700});
   const env={...process.env,HOME:home,
    PATH:bin+path.delimiter+(process.env.PATH??''),
    FAKE_LOG:log,FAKE_MARKER:marker};
   child=spawn('bash',[path.join(repo,'scripts/install-continuum-teamleader.sh')],
    {env,stdio:'ignore'});
   const exited=new Promise((resolve,reject)=>{
    child.once('error',reject);
    child.once('exit',(code,signal)=>resolve({code,signal}));
   });
   await untilExists(marker,child);
   assert.equal(child.kill('SIGTERM'),true);
   const result=await exited;
   assert.equal(result.code,143);
   const units=path.join(home,'.config/systemd/user');
   assert.equal(fs.existsSync(path.join(units,'continuum-teamleader.service')),false);
   assert.equal(fs.existsSync(path.join(units,'continuum-teamleader.timer')),false);
   const calls=fs.readFileSync(log,'utf8');
   assert.match(calls,/disable --now continuum-teamleader\.timer/);
  }finally{
   if(child?.exitCode===null)child.kill('SIGKILL');
   fs.rmSync(root,{recursive:true,force:true});
  }
 });

test('activation rollback never deletes a foreign replacement service',
 {skip:process.platform!=='linux'||!fs.existsSync('/bin/bash'),timeout:10000},
 ()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-unit-owner-test-'));
  try{
   const repo=path.join(root,'checkout'),home=path.join(root,'home');
   const bin=path.join(root,'bin');
   fs.mkdirSync(path.join(repo,'scripts'),{recursive:true});
   fs.mkdirSync(home);fs.mkdirSync(bin);
   fs.writeFileSync(path.join(repo,'.git'),'gitdir: /offline/worktree\n');
   const script=path.join(repo,'scripts/install-continuum-teamleader.sh');
   fs.copyFileSync(installer,script);
   const fake=path.join(bin,'systemctl');
   fs.writeFileSync(fake,
    '#!/bin/sh\n'+
    'case "$*" in *"enable --now"*)\n'+
    '  rm -f -- "$HOME/.config/systemd/user/continuum-teamleader.service"\n'+
    '  printf "foreign service\\n" > "$HOME/.config/systemd/user/continuum-teamleader.service"\n'+
    '  exit 23;; esac\n'+
    'exit 0\n',{mode:0o700});
   const env={...process.env,HOME:home,PATH:bin+path.delimiter+(process.env.PATH??'')};
   const run=spawnSync('bash',[script],{env,encoding:'utf8',timeout:5000});
   assert.notEqual(run.status,0);
   const units=path.join(home,'.config/systemd/user');
   assert.equal(fs.readFileSync(path.join(units,'continuum-teamleader.service'),'utf8'),
    'foreign service\n');
   assert.equal(fs.existsSync(path.join(units,'continuum-teamleader.timer')),false);
  }finally{fs.rmSync(root,{recursive:true,force:true});}
 });
