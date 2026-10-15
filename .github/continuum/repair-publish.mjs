import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {git,BRANCH_PREFIX} from './repair-loop.mjs';
import {critique} from './repair-critic.mjs';
import {sha256} from './repair-diagnostics.mjs';
import {emptyLedger,recordSession,verifyLedger} from './repair-ledger.mjs';

/**
 * Publisher (runs in a job WITHOUT build execution, holding the write token).
 * It re-checks the verified patch with the critic, applies it with `git apply`
 * onto the exact base commit, commits it on a branch under continuum-agent/
 * and opens a DRAFT pull request. It never pushes to the target branch itself.
 */
const BRANCH_RE=/^continuum-agent\/[a-z0-9][a-z0-9._/-]{0,110}$/;
const TARGET_RE=/^[A-Za-z0-9][\w./-]{0,120}$/;
const AUTHOR=['-c','user.name=continuum-developer-agent','-c','user.email=continuum-developer-agent@users.noreply.github.com'];

export function assertProposalBranch(branch){
  if(!BRANCH_RE.test(branch)||branch.includes('..')||!branch.startsWith(BRANCH_PREFIX))throw Error('Refusing branch outside '+BRANCH_PREFIX+': '+branch);
  return branch;
}

/** Defense in depth: the publishing job trusts nothing but the critic and hashes. */
export function validateVerifiedReport(report,patch){
  if(!report||report.outcome!=='VERIFIED'||!report.verified)throw Error('Report has no verified repair');
  if(!/^[0-9a-f]{40}$/.test(report.base??''))throw Error('Report base must be a full commit hash');
  if(sha256(patch)!==report.verified.patchSha256)throw Error('Patch hash does not match the verified report');
  const attempt=(report.attempts??[]).find(a=>a.outcome==='VERIFIED'&&a.candidate===report.verified.candidate);
  if(!attempt)throw Error('Verified attempt missing');
  const verdict=critique({strategy:attempt.strategy,prior:attempt.score,edits:attempt.edits});
  if(!verdict.approved)throw Error('Critic rejects the verified patch: '+verdict.reasons.join('; '));
  const touched=[...patch.matchAll(/^diff --git a\/(\S+) b\/(\S+)$/gm)].map(m=>m[2]);
  const declared=new Set(report.verified.files);
  if(!touched.length||touched.some(f=>!declared.has(f)))throw Error('Patch touches files outside the verified edit set');
  if((report.attempts??[]).some(a=>a.outcome==='VERIFIED'&&!a.tests?.every(t=>t.passed)))throw Error('Verified attempt has failing checks');
  return attempt;
}

async function withWorktree(root,base,fn){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-publish-'));
  git(root,['worktree','add','--detach','--force',dir,base]);
  try{return await fn(dir);}finally{git(root,['worktree','remove','--force',dir],{allowFailure:true});fs.rmSync(dir,{recursive:true,force:true});}
}

export function applyPatchCommit(wt,patch,message){
  const file=path.join(wt,'.git-continuum-proposal.patch');
  fs.writeFileSync(file,patch);
  try{
    git(wt,['apply','--check','--whitespace=nowarn',file]);
    git(wt,['apply','--whitespace=nowarn',file]);
  }finally{fs.rmSync(file,{force:true});}
  git(wt,['add','-A']);
  git(wt,[...AUTHOR,'commit','-q','-m',message]);
  return git(wt,['rev-parse','HEAD']).stdout.trim();
}

export function pullRequestBody(report,attempt,extra=''){
  const tests=(report.verified.tests??[]).map(t=>`- \`${t.profile}\`: ${t.passed?'passed':'FAILED'}${t.tests?.total?` (${t.tests.total-t.tests.failed}/${t.tests.total} tests)`:''}, exit ${t.exitCode}, ${Math.round((t.ms??0)/1000)} s`).join('\n');
  const diags=(report.baseline?.diagnostics??[]).slice(0,5).map(d=>`- \`${d.kind}\` ${d.file??''}${d.line?':'+d.line:''} — ${String(d.message).replace(/[`\r\n]/g,' ').slice(0,160)}`).join('\n');
  return [
    '## Continuum Developer Agent · verified repair proposal','',
    '**Draft. Human review required. Nothing was merged.**','',
    `Base commit: \`${report.base}\`  ·  Check chain: \`${report.chain}\``,'',
    '### Reproduced failure (agent\'s own run)',diags||'- (none recorded)','',
    `### Repair\nStrategy \`${attempt.strategy}\` — ${attempt.description}\nCritic risk ${attempt.critic?.risk} · changed lines ${attempt.critic?.changedLines} · review required: ${report.verified.reviewRequired?'yes':'no'} · confidence ${report.verified.confidence}`,'',
    '### Verification in an isolated worktree',tests,'',
    `Patch SHA-256: \`${report.verified.patchSha256}\``,'',
    'Generated deterministically (no LLM, no paid API). The agent may be wrong about intent; tests only prove the checks pass.',
    extra
  ].join('\n');
}

async function github(method,url,token,body){
  const response=await fetch('https://api.github.com'+url,{method,signal:AbortSignal.timeout(15000),
    headers:{Authorization:'Bearer '+token,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','Content-Type':'application/json'},
    body:body?JSON.stringify(body):undefined});
  const text=await response.text();
  let json=null;try{json=JSON.parse(text);}catch{}
  return {ok:response.ok,status:response.status,json,message:(json?.message??text).slice(0,300)};
}

export async function openDraftPullRequest({repository,token,head,base,title,body}){
  if(!/^[-\w.]+\/[-\w.]+$/.test(repository??''))throw Error('Invalid repository');
  if(!TARGET_RE.test(base))throw Error('Invalid base branch');
  assertProposalBranch(head);
  const r=await github('POST',`/repos/${repository}/pulls`,token,{title:title.slice(0,200),head,base,body:body.slice(0,60000),draft:true,maintainer_can_modify:true});
  if(r.ok)return {status:'OPENED',number:r.json.number,url:r.json.html_url};
  // e.g. 403 when "Allow GitHub Actions to create and approve pull requests" is off
  return {status:'NOT_PERMITTED',httpStatus:r.status,message:r.message,
    compare:`https://github.com/${repository}/compare/${encodeURIComponent(base)}...${encodeURIComponent(head)}?expand=1`};
}

export function pushBranch(wt,branch){
  assertProposalBranch(branch);
  git(wt,['push','--quiet','origin','HEAD:refs/heads/'+branch],{timeoutMs:120000});
}

/** Publish a verified repair of a real failing commit as branch + draft PR. */
export async function publishRepair({root,outDir,targetBranch,repository,token,push=true}){
  if(!TARGET_RE.test(targetBranch)||targetBranch==='main'||targetBranch.startsWith(BRANCH_PREFIX))throw Error('Invalid target branch '+targetBranch);
  const report=JSON.parse(fs.readFileSync(path.join(outDir,'report.json'),'utf8'));
  const patch=fs.readFileSync(path.join(outDir,'repair.patch'),'utf8');
  const attempt=validateVerifiedReport(report,patch);
  const branch=assertProposalBranch(`${BRANCH_PREFIX}repair-${report.base.slice(0,10)}-${attempt.diagnostic.slice(0,8)}`);
  const message=`fix(agent): ${attempt.strategy} for ${attempt.diagnosticKind}\n\n${attempt.description}\nBase: ${report.base}\nPatch-SHA256: ${report.verified.patchSha256}\nVerified by: ${report.chain} check chain (isolated worktree)`;
  return withWorktree(root,report.base,async wt=>{
    const commit=applyPatchCommit(wt,patch,message);
    const result={branch,commit,base:report.base,targetBranch,pullRequest:null};
    if(!push)return result;
    pushBranch(wt,branch);
    result.pullRequest=await openDraftPullRequest({repository,token,head:branch,base:targetBranch,
      title:`[Continuum agent] ${attempt.strategy}: ${attempt.description}`.slice(0,180),body:pullRequestBody(report,attempt)});
    return result;
  });
}

/** Benchmark demonstration: push the injected fault and its verified repair as two
 * branches and open a draft PR repair -> fault. Never targets a real branch. */
export async function publishBenchmarkDemo({root,head,faultDir,runId,repository,token,push=true}){
  const report=JSON.parse(fs.readFileSync(path.join(faultDir,'report.json'),'utf8'));
  const patch=fs.readFileSync(path.join(faultDir,'repair.patch'),'utf8');
  const faultPatch=fs.readFileSync(path.join(faultDir,'fault.patch'),'utf8');
  const attempt=validateVerifiedReport(report,patch);
  const id=path.basename(faultDir).toLowerCase().replace(/[^a-z0-9-]/g,'-').slice(0,48);
  const run=String(runId).replace(/[^0-9a-z]/gi,'').slice(0,20)||'local';
  const faultBranch=assertProposalBranch(`${BRANCH_PREFIX}demo/${run}/fault-${id}`);
  const repairBranch=assertProposalBranch(`${BRANCH_PREFIX}demo/${run}/repair-${id}`);
  return withWorktree(root,head,async wt=>{
    const faultCommit=applyPatchCommit(wt,faultPatch,`test(agent): inject benchmark fault ${id}\n\nDeliberate, reproducible fault for the repair benchmark.`);
    if(push)pushBranch(wt,faultBranch);
    const repairCommit=applyPatchCommit(wt,patch,`fix(agent): ${attempt.strategy} repairs benchmark fault ${id}\n\nPatch-SHA256: ${report.verified.patchSha256}`);
    const result={faultBranch,repairBranch,faultCommit,repairCommit,pullRequest:null};
    if(!push)return result;
    pushBranch(wt,repairBranch);
    result.pullRequest=await openDraftPullRequest({repository,token,head:repairBranch,base:faultBranch,
      title:`[Continuum agent demo] ${attempt.strategy} repairs injected fault ${id}`,
      body:pullRequestBody(report,attempt,`\n\nThis PR targets the demo fault branch \`${faultBranch}\`, not a development branch.`)});
    return result;
  });
}

/** Ledger persistence on a dedicated state branch with stale-write protection. */
export function loadLedgerFromBranch(root,branch){
  const fetched=git(root,['fetch','--quiet','--depth=1','origin',`+refs/heads/${branch}:refs/remotes/origin/${branch}`],{allowFailure:true});
  if(fetched.status!==0)return {ledger:emptyLedger(),remoteHead:null};
  const head=git(root,['rev-parse',`refs/remotes/origin/${branch}`]).stdout.trim();
  const shown=git(root,['show',`${head}:repair-ledger.json`],{allowFailure:true});
  return {ledger:shown.status===0?verifyLedger(JSON.parse(shown.stdout)):emptyLedger(),remoteHead:head};
}

export function saveLedgerToBranch(root,branch,reports,meta={}){
  if(!/^continuum-agent-state$/.test(branch))throw Error('Ledger branch must be continuum-agent-state');
  for(let attempt=0;attempt<2;attempt++){
    const {ledger,remoteHead}=loadLedgerFromBranch(root,branch);
    let next=ledger;
    for(const {report,meta:m} of reports){
      if(next.sessions.some(s=>s.base===report.base&&s.at===(report.finishedAt??null)&&s.chain===report.chain))continue;
      next=recordSession(next,report,{...meta,...m});
    }
    const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-ledger-'));
    try{
      if(remoteHead)git(root,['worktree','add','--detach','--force',dir,remoteHead]);
      else{git(root,['worktree','add','--detach','--force',dir,'HEAD']);git(dir,['checkout','-q','--orphan','continuum-ledger-'+process.pid+'-'+Date.now()]);git(dir,['rm','-rq','--cached','.'],{allowFailure:true});
        for(const e of fs.readdirSync(dir))if(e!=='.git')fs.rmSync(path.join(dir,e),{recursive:true,force:true});}
      fs.writeFileSync(path.join(dir,'repair-ledger.json'),JSON.stringify(next,null,1)+'\n');
      fs.writeFileSync(path.join(dir,'README.md'),'# Continuum developer-agent state\n\nAppend-only repair ledger written by CI. Contains code hunks and test outcomes only, no device or personal data.\n');
      git(dir,['add','repair-ledger.json','README.md']);
      if(git(dir,['diff','--cached','--quiet'],{allowFailure:true}).status===0)return {status:'UNCHANGED',sessions:next.sessions.length};
      git(dir,[...AUTHOR,'commit','-q','-m',`chore(agent): record ${reports.length} repair session(s)`]);
      const pushed=git(dir,['push','--quiet','origin',`HEAD:refs/heads/${branch}`],{allowFailure:true,timeoutMs:120000});
      if(pushed.status===0)return {status:'SAVED',sessions:next.sessions.length,attempts:next.attempts.length};
    }finally{git(root,['worktree','remove','--force',dir],{allowFailure:true});fs.rmSync(dir,{recursive:true,force:true});}
  }
  throw Error('Ledger branch moved twice during save; refusing a stale write');
}
