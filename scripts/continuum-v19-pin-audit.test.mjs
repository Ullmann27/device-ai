import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync, symlinkSync, renameSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const source = fileURLToPath(new URL('./continuum-v19-pin-audit.mjs', import.meta.url));
function fixture(action) {
 const dir = mkdtempSync(join(tmpdir(), 'continuum-pin-'));
 try {
  execFileSync('git', ['init', '-q', dir]);
  execFileSync('git', ['-C', dir, 'config', 'user.email', 'unit@example.invalid']);
  execFileSync('git', ['-C', dir, 'config', 'user.name', 'test']);
  mkdirSync(join(dir, 'scripts'));
  for(let i=0;i<22;i++) writeFileSync(join(dir, `file-${i}.kt`), `version-${i}\n`);
  const sha = i => execFileSync('git', ['hash-object', `file-${i}.kt`], {cwd:dir,encoding:'utf8'}).trim();
  const pins = Array.from({length:22},(_,i)=>`${sha(i)} file-${i}.kt`).join('\n');
  writeFileSync(join(dir,'scripts','render-mentor-owner-update-v19.sh'), `done <<'APPROVED_RELEASE_SOURCES'\n${pins}\nAPPROVED_RELEASE_SOURCES\n`);
  execFileSync('git',['-C',dir,'add','.']);
  execFileSync('git',['-C',dir,'commit','-q','-m','fixture']);
  action(dir);
 } finally {rmSync(dir,{recursive:true,force:true})}
}
function run(dir,...args){const r=spawnSync(process.execPath,[source,...args],{cwd:dir,encoding:'utf8'});return {...r,report:r.stdout?JSON.parse(r.stdout):null};}
test('unchanged 22 source pins pass',()=>fixture(dir=>{const r=run(dir);assert.equal(r.status,0);assert.equal(r.report.matched,22)}));
test('changed protected source fails closed',()=>fixture(dir=>{writeFileSync(join(dir,'file-3.kt'),'tampered\n');const r=run(dir);assert.equal(r.status,2);assert.equal(r.report.reviewRequired,1);assert.equal(r.report.files[0].path,'file-3.kt')}));
test('report-only does not turn a mismatch into approval',()=>fixture(dir=>{writeFileSync(join(dir,'file-1.kt'),'tampered');const r=run(dir,'--report-only');assert.equal(r.status,0);assert.equal(r.report.status,'OWNER_REVIEW_REQUIRED')}));
test('symlink is never accepted as an attested regular source',()=>fixture(dir=>{rmSync(join(dir,'file-0.kt'));symlinkSync('file-1.kt',join(dir,'file-0.kt'));const r=run(dir);assert.equal(r.status,2);assert.equal(r.report.reviewRequired,1)}));
test('duplicate release pins fail before reporting',()=>fixture(dir=>{let p=join(dir,'scripts','render-mentor-owner-update-v19.sh');let s=readFileSync(p,'utf8');s=s.replace(/file-1.kt/,'file-0.kt');writeFileSync(p,s);const r=run(dir);assert.notEqual(r.status,0);assert.match(r.stderr,/duplicate/)}));

test('symlinked ancestor cannot launder an unchanged file hash into a trusted path',()=>fixture(dir=>{
  mkdirSync(join(dir,'real'));
  renameSync(join(dir,'file-0.kt'),join(dir,'real','file-0.kt'));
  symlinkSync('real',join(dir,'alias'),'dir');
  const pinFile=join(dir,'scripts','render-mentor-owner-update-v19.sh');
  const pinned=readFileSync(pinFile,'utf8').replace(/file-0\.kt/, 'alias/file-0.kt');
  writeFileSync(pinFile,pinned);
  // The content still has the exact expected SHA, so only directory validation detects this.
  const actual=execFileSync('git',['hash-object','--','alias/file-0.kt'],{cwd:dir,encoding:'utf8'}).trim();
  assert.ok(pinned.includes(actual+' alias/file-0.kt'));
  const r=run(dir);
  assert.equal(r.status,2);
  assert.equal(r.report.reviewRequired,1);
  assert.equal(r.report.files[0].path,'alias/file-0.kt');
}));
