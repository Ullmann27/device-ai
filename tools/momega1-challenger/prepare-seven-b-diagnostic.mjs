/** Reconstruct the exact consumed tiny provider prompt without persisting credentials. */
import fs from 'node:fs';import path from 'node:path';import os from 'node:os';import assert from 'node:assert/strict';import {spawnSync} from 'node:child_process';import {createHash} from 'node:crypto';
import {createCodingCandidateProvider} from '/workspace/momega1-challenger-7b-candidate/.github/continuum/coding-provider.mjs';
const out='/workspace/momega1-local-model/challenger-7b-diagnostic';fs.mkdirSync(out,{recursive:true,mode:0o700});fs.chmodSync(out,0o700);
const frozen=JSON.parse(fs.readFileSync(new URL('./seven-b-small-frozen-prompt.json',import.meta.url),'utf8'));
const prior=JSON.parse(fs.readFileSync(new URL('../../docs/evidence/momega1/challenger/seven-b-small-provider-candidate.json',import.meta.url),'utf8')).evidence[0].data;
const root=fs.mkdtempSync('/tmp/momega1-reconstruct-');fs.mkdirSync(path.join(root,'lib'));fs.writeFileSync(path.join(root,'lib/batches.mjs'),frozen.source);
function git(args,env={}){let r=spawnSync('git',args,{cwd:root,encoding:'utf8',env:{...process.env,...env}});assert.equal(r.status,0,r.stderr);return r.stdout.trim();}
try{git(['init','-q']);git(['add','.']);const tree=git(['write-tree']);let matched=null;const time=Math.floor(Date.parse(prior.startedAt)/1000);
for(let d=-10;d<=2;d++){let date=(time+d)+' +0000';let sha=git(['-c','user.name=Independent fixture','-c','user.email=fixture@example.invalid','commit-tree',tree,'-m','reviewer frozen synthetic source'],{GIT_AUTHOR_DATE:date,GIT_COMMITTER_DATE:date});if(sha===prior.sourceCommit){matched=sha;break;}}
assert.equal(matched,prior.sourceCommit,'exact source commit must reconstruct');git(['update-ref','HEAD',matched]);
const task={schema:'continuum-coding-task',version:1,id:'independent-real-batches',sourceCommit:matched,kind:'BUGFIX',goal:frozen.userPrompt.split('Source:\n')[0].trim(),allowedPaths:frozen.allowedTrackedPaths,acceptance:frozen.acceptance,chain:'node',limits:{maxModelCalls:1,maxContextChars:4096}};
let captured=null;const p=createCodingCandidateProvider({root,task,env:{CONTINUUM_LOCAL_BRAIN_MODEL:'qwen2.5-coder-7b-instruct-q4_k_m'},modelTimeoutMs:180000,run:(_c,_a,o)=>{let input=JSON.parse(o.input);captured={userPrompt:input.userPrompt,allowedTrackedPaths:input.allowedTrackedPaths};return {status:1,stdout:'',stderr:''};}});
assert.throws(()=>p({base:matched,chain:'node',deadline:Date.now()+210000}),/CODING_MODEL_UNAVAILABLE/);assert.ok(captured);const sha=createHash('sha256').update(captured.userPrompt).digest('hex');assert.equal(sha,prior.promptSha256);assert.equal(JSON.parse(captured.userPrompt).context.contextSha256,prior.contextSha256);
fs.writeFileSync(path.join(out,'exact-provider-prompt.json'),JSON.stringify(captured,null,2)+'\n',{mode:0o600});console.log(JSON.stringify({status:'EXACT_PROMPT_RECONSTRUCTED',sourceCommit:matched,promptSha256:sha,contextSha256:prior.contextSha256,privateDirectory:out,secretFieldsPersisted:false}));
}finally{fs.rmSync(root,{recursive:true,force:true});}
