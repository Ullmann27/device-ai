import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {spawnSync} from 'node:child_process';
import os from 'node:os';
import path from 'node:path';

const script='scripts/continuum-teamleader-termux.sh';
const source=fs.readFileSync(script,'utf8');

test('Android Termux development-worker wrapper is valid Bash',()=>{
 const result=spawnSync('bash',['-n',script],{encoding:'utf8'});
 assert.equal(result.status,0,result.stderr);
});

test('Termux uses same Node Teamleader, never a competing agent implementation',()=>{
 assert.match(source,/\.github\/continuum\/teamleader-cli\.mjs/);
 assert.match(source,/--once --root/);
 assert.match(source,/--status --root/);
 assert.match(source,/--budget-minutes 10/);
 assert.match(source,/git -C "\$ROOT" rev-parse/);
 assert.doesNotMatch(source,/\b(?:curl|wget)\b/);
 assert.doesNotMatch(source,/^\s*pkg install /m);
 assert.doesNotMatch(source,/--force|git push|git reset --hard/);
});

test('unattended Termux loop has real battery and heat gates and no implicit wake-lock',()=>{
 assert.match(source,/termux-battery-status/);
 assert.match(source,/status\.percentage <= 25/);
 assert.match(source,/status\.temperature >= 42/);
 assert.match(source,/sleep 3600/);
 assert.doesNotMatch(source,/termux-wake-lock|termux-boot|nohup|setsid/);
});

test('Termux CCC1 commands use the app bridge and CLI, and never fetch, reset, delete or apply',()=>{
 assert.match(source,/BUILDER="\$ROOT\/scripts\/continuum-mobile-builder\.mjs"/);
 assert.match(source,/CCC1_STATE="\$HOME\/\.local\/state\/device-ai-continuum\/ccc1"/);
 assert.match(source,/node "\$BUILDER" ccc1-compile-repair --chain node --project "\$ROOT"/);
 assert.match(source,/exec node "\$BUILDER" ccc1-run-once --project "\$ROOT"/);
 assert.match(source,/CONTINUUM_REPAIR_DEPTH=1 node --test \.github\/continuum\/\*\.test\.mjs/);
 // the only state change is a NEW local branch at the same commit, and only from a clean tree
 assert.match(source,/git -C "\$ROOT" switch -q -c "\$work"/);
 assert.match(source,/status --porcelain --untracked-files=normal/);
 assert.doesNotMatch(source,/git (?:-C "\$ROOT" )?(?:fetch|pull|apply|am|reset|clean|rebase|merge|stash|branch -[dDfm])\b/);
});

test('Termux CCC1 check and prepare behave as documented on a throw-away checkout',{skip:Boolean(process.env.CONTINUUM_REPAIR_DEPTH),timeout:240000},()=>{
 const base=fs.mkdtempSync(path.join(os.tmpdir(),'ccc1-termux-'));
 const root=path.join(base,'device-ai');
 try{
  fs.mkdirSync(root);
  for(const dir of ['scripts','.github'])fs.cpSync(dir,path.join(root,dir),{recursive:true});
  const git=(...a)=>spawnSync('git',a,{cwd:root,encoding:'utf8'});
  git('init','-q','-b','main');git('add','-A');git('-c','user.name=f','-c','user.email=f@x.invalid','commit','-qm','seed');
  const env={...process.env,HOME:base};delete env.NODE_TEST_CONTEXT;
  const run=(...a)=>spawnSync('bash',['scripts/continuum-teamleader-termux.sh',...a],{cwd:root,encoding:'utf8',env,timeout:120000});
  const head=git('rev-parse','HEAD').stdout.trim();
  let r=run('ccc1-check');
  assert.equal(r.status,1);
  assert.match(r.stdout,/NICHT BEREIT: CCC1_BRANCH_NOT_ISOLATED/);
  assert.match(r.stdout,/ccc1-prepare/);
  r=run('ccc1-prepare');
  assert.equal(r.status,0,r.stdout+r.stderr);
  assert.match(r.stdout,/BEREIT: CCC1-Vorprüfung bestanden/);
  const branch=git('branch','--show-current').stdout.trim();
  assert.match(branch,/^ccc1-arbeit-\d{8}-\d{6}$/);
  assert.equal(git('rev-parse','HEAD').stdout.trim(),head,'same commit, files unchanged');
  fs.writeFileSync(path.join(root,'stray.txt'),'x');
  git('switch','-q','main');
  r=run('ccc1-prepare');
  assert.equal(r.status,1);
  assert.match(r.stderr,/Abbruch, nichts verändert/);
  assert.equal(git('branch','--show-current').stdout.trim(),'main','a dirty checkout is never switched');
  assert.match(run('ccc1-status').stdout,/0 task\(s\), 0 experience record\(s\)/);
  assert.match(run('ccc1-review').stdout,/Kein geprüfter Reparatur-Patch vorhanden/);
 }finally{fs.rmSync(base,{recursive:true,force:true});}
});
