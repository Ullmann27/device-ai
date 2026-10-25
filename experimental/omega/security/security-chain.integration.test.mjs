import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {readOwnedNpmLockAtCommit} from './owned-lock-provenance.mjs';
import {inventoryFromOwnedNpmLock} from './npm-lock-inventory.mjs';
import {evaluateOwnedOsvSnapshot} from './advisory-intake.mjs';
import {draftOwnedSecurityReview,verifyOwnedSecurityProposal} from './security-review-proposal.mjs';

test('real Git lock -> exact inventory -> OSV -> no-authority review proposal',t=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'omega-security-chain-'));
 t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 fs.mkdirSync(path.join(root,'backend'));
 const lock={lockfileVersion:3,packages:{'':{dependencies:{example:'1.2.3'}},'node_modules/example':{version:'1.2.3'}}};
 fs.writeFileSync(path.join(root,'backend/package-lock.json'),JSON.stringify(lock));
 const git=(...args)=>{
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);return r.stdout.trim();
 };
 git('init','-q','-b','research-chain');git('add','.');
 git('-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','fixture');
 const sourceCommit=git('rev-parse','HEAD');
 const snapshot=readOwnedNpmLockAtCommit({repoRoot:root,sourceCommit});
 assert.equal(snapshot.provenance,'CLEAN_LOCAL_GIT_HEAD_BLOB');
 const {inventory,coverage,unresolvedEntries}=inventoryFromOwnedNpmLock(snapshot.lock,{project:'owned-app',sourceCommit});
 assert.equal(coverage,'NPM_LOCK_PACKAGES_ENUMERATED');assert.equal(unresolvedEntries,0);
 const advisories=[{id:'OSV-2026-5001',affected:[{package:{ecosystem:'npm',name:'example'},versions:['1.2.3']}]}];
 const report=evaluateOwnedOsvSnapshot({inventory,advisories});
 const selection={advisoryId:'OSV-2026-5001',ecosystem:'npm',packageName:'example',installedVersion:'1.2.3'};
 const proposal=draftOwnedSecurityReview({report,finding:selection});
 assert.equal(proposal.reportSha256,report.reportSha256);
 assert.equal(proposal.finding.match,'EXACT_LISTED_VERSION');
 assert.equal(verifyOwnedSecurityProposal(proposal),true);
 assert.equal(proposal.ccc1Ready,false);assert.equal(proposal.authorized,false);
 assert.equal(proposal.executionAllowed,false);assert.equal(proposal.patchAllowed,false);
});
