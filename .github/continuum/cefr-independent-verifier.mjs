/**
 * Independent, bounded second-worktree execution proof for the CEFR learner.
 * This function executes code from a checked-out repository; a Git worktree is
 * NOT an OS security sandbox. Run only under the trusted local worker account.
 * Never call on a production branch or from a model-controlled tool request.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {createWorktree,removeWorktree,git,runChain,evidenceSufficient,
 CHAINS,TEST_PROFILES} from './repair-loop.mjs';
import {validateVerifiedReport} from './repair-publish.mjs';

const sha256=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const SHA=/^[0-9a-f]{40}$/;

export function verifyIndependentCefrPatch({root,report,patchText,deadline}={}){
 if(typeof root!=='string'||!path.isAbsolute(root)||!SHA.test(report?.base??'')||
    typeof patchText!=='string'||patchText.length<20||patchText.length>262144||
    !Number.isSafeInteger(deadline)||deadline<=Date.now()||
    deadline>Date.now()+30*60*1000)
  throw Error('INVALID_CEFR_EXECUTION_REQUEST');
 const repository=fs.realpathSync(root);
 const current=git(repository,['rev-parse','HEAD']).stdout.trim();
 if(current!==report.base)throw Error('CEFR_BASE_HEAD_MISMATCH');
 if(git(repository,['status','--porcelain','--untracked-files=all']).stdout.trim())
  throw Error('CEFR_DIRTY_WORKTREE');
 const original=validateVerifiedReport(report,patchText);
 const profiles=CHAINS[report.chain];
 if(!profiles?.length)throw Error('UNSUPPORTED_CEFR_TEST_CHAIN');
 if(!Array.isArray(report.baseline?.results)||
    !report.baseline.results.some(x=>x?.testProfile&&x?.tests?.executed>0))
  throw Error('MISSING_CEFR_BASELINE_EVIDENCE');
 const touched=[...new Set(report.verified.files)];
 if(touched.length===0||touched.length>3||touched.some(x=>typeof x!=='string'))
  throw Error('INVALID_CEFR_PATCH_FILES');
 const expected=new Set(touched);
 let worktree=null;
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-cefr-proof-'));
 try{
  // This is a new worktree, never the worktree used to generate the patch.
  worktree=createWorktree(repository,report.base);
  const patchFile=path.join(temp,'candidate.patch');
  fs.writeFileSync(patchFile,patchText,{flag:'wx',mode:0o600});
  if(git(worktree,['apply','--check','--binary',patchFile],{allowFailure:true}).status!==0)
   throw Error('CEFR_PATCH_APPLY_CHECK_FAILED');
  git(worktree,['apply','--binary',patchFile]);
  // A patch cannot silently introduce, remove, or change unreviewed files.
  const status=git(worktree,['status','--porcelain','-z','--untracked-files=all']).stdout;
  const entries=status.split('\0').filter(Boolean);
  if(entries.length!==expected.size||
     entries.some(e=>!e.startsWith(' M ')||!expected.has(e.slice(3))))
   throw Error('CEFR_PATCH_FILE_SET_CHANGED');
  const out=path.join(temp,'tests');
  fs.mkdirSync(out,{mode:0o700});
  const checked=runChain(report.chain,worktree,out,{deadline});
  const evidence=evidenceSufficient(report.baseline.results,checked.results);
  const resultProfiles=checked.results.map(r=>({
   profile:r.profile,passed:r.passed,failed:r.tests.failed,
   skipped:r.tests.skipped,executed:r.tests.executed
  }));
  const actual=profiles.every((name,i)=>resultProfiles[i]?.profile===name&&
   resultProfiles[i].passed&&resultProfiles[i].failed===0&&
   (!TEST_PROFILES.includes(name)||resultProfiles[i].executed>0));
  const passed=Boolean(checked.passed&&evidence.sufficient&&actual&&
   resultProfiles.length===profiles.length);
  const proof={schemaVersion:1,passed,base:report.base,chain:report.chain,
   patchSha256:sha256(patchText),profiles:resultProfiles,
   evidenceSufficient:evidence.sufficient,
   reason:passed?null:'INDEPENDENT_CHECK_OR_EVIDENCE_FAILED'};
  // Return fresh primitive evidence from actual runChain results, NOT fields
  // copied from the self-reported successful repair attempt.
  return proof;
 }finally{
  let cleanupError=null;
  if(worktree)try{removeWorktree(repository,worktree);}
    catch(error){cleanupError=error;}
  try{fs.rmSync(temp,{recursive:true,force:true});}
  catch(error){cleanupError??=error;}
  if(cleanupError)throw cleanupError;
 }
}
