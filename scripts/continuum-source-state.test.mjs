import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const guard=path.join(root,'scripts/continuum-source-state.sh');
function fixture(){
 const repo=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-source-state-'));
 const git=args=>{const r=spawnSync('git',args,{cwd:repo,encoding:'utf8'});assert.equal(r.status,0,r.stderr);return r.stdout.trim();};
 git(['init','-q','-b','test/source-state']);
 fs.writeFileSync(path.join(repo,'source.txt'),'public fixture');
 fs.writeFileSync(path.join(repo,'.gitignore'),'build/\n');
 git(['add','.']);git(['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture']);
 return {repo,git,sha:git(['rev-parse','HEAD'])};
}
function run(f,sha=f.sha,repo=f.repo){
 return spawnSync('bash',['-c','source "$1"; continuum_require_source_state "$2" "$3"','source-test',guard,repo,sha],{encoding:'utf8',timeout:10000});
}
test('unchanged exact source with ignored build output is accepted',()=>{
 const f=fixture();try{fs.mkdirSync(path.join(f.repo,'build'));fs.writeFileSync(path.join(f.repo,'build','generated'),'fixture');assert.equal(run(f).status,0);}
 finally{fs.rmSync(f.repo,{recursive:true,force:true});}
});
for(const kind of ['tracked','staged','untracked'])test(kind+' source drift is refused without diff output',()=>{
 const f=fixture();try{
  fs.writeFileSync(path.join(f.repo,kind==='untracked'?'new.txt':'source.txt'),'synthetic private marker');
  if(kind==='staged')f.git(['add','source.txt']);
  const r=run(f);assert.equal(r.status,69);assert.equal(r.stdout,'');assert.equal(r.stderr.trim(),'CONTINUUM_SOURCE_STATE_REFUSED');
 }finally{fs.rmSync(f.repo,{recursive:true,force:true});}
});
test('a different clean HEAD is refused',()=>{
 const f=fixture();try{f.git(['-c','user.name=fixture','-c','user.email=fixture@example.invalid','commit','--allow-empty','-qm','changed']);assert.equal(run(f).status,69);}
 finally{fs.rmSync(f.repo,{recursive:true,force:true});}
});
test('invalid identity and missing Git checkout fail closed',()=>{
 const f=fixture();try{assert.equal(run(f,'not-a-sha').status,69);assert.equal(run(f,f.sha,path.join(f.repo,'absent')).status,69);}
 finally{fs.rmSync(f.repo,{recursive:true,force:true});}
});
test('standalone QA uses the same pinned Gradle and native aapt2',()=>{
 const release=fs.readFileSync(path.join(root,'scripts/continuum-fold7-standalone-apk.sh'),'utf8');
 assert.match(release,/CONTINUUM_GRADLE_PATH="\$gradle"/);
 assert.match(release,/CONTINUUM_AAPT2_PATH="\$aapt2"/);
 const qa=release.indexOf('bash scripts/continuum-fusion-full-regression.sh');
 const check=release.indexOf('continuum_require_source_state "$root" "$sha"',qa);
 assert.ok(check>qa && check<release.indexOf('Lokales Signierpasswort:'));
});
test('full QA checks exact source again before publishing its report',()=>{
 const script=fs.readFileSync(path.join(root,'scripts/continuum-fusion-full-regression.sh'),'utf8');
 const after=script.lastIndexOf('continuum_require_source_state "$root" "$head"');
 assert.ok(after>script.indexOf('gradle_exit=$?'));
 assert.ok(after>script.indexOf('parity-verify.log'));
 assert.ok(after<script.indexOf('node scripts/continuum-fusion-regression-report.mjs'));
});
