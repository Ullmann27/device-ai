/** A tiny, sequential, offline specialist front-end for the EXISTING
 * Continuum tests, CCC1 and repair gates. These are deterministic workers,
 * not an LLM, a model host, a parallel agent farm or a new job queue.
 * No model prompts, shell interpolation, network access, APK installation,
 * signing keys, owner memories or source edits are performed.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export const MICRO_AGENT_JOBS=Object.freeze({
  gemma:Object.freeze({
    role:'GEMMA_IMPORT_AUDITOR',
    tests:Object.freeze(['scripts/continuum-gemma-picker-lifecycle.test.mjs',
      '.github/continuum/gemma-reopen-lifecycle.test.mjs'])
  }),
  deepseek:Object.freeze({
    role:'DEEPSEEK_PROTOCOL_AUDITOR',
    tests:Object.freeze(['scripts/continuum-deepseek-bridge.test.mjs',
      'scripts/continuum-deepseek-tier-plan.test.mjs'])
  }),
  coding:Object.freeze({
    role:'CCC1_CODING_AUDITOR',
    tests:Object.freeze(['.github/continuum/ccc1-local-preflight.test.mjs',
      '.github/continuum/coding-provider.test.mjs'])
  }),
  transfer:Object.freeze({
    role:'EXPERIENCE_TRANSFER_AUDITOR',
    tests:Object.freeze(['.github/continuum/ccc1-transfer-research.test.mjs'])
  }),
  update:Object.freeze({
    role:'FOLD7_UPDATE_AUDITOR',
    tests:Object.freeze(['scripts/continuum-fold7-update.test.mjs'])
  })
});
const names=Object.keys(MICRO_AGENT_JOBS);
const SHA40=/^[a-f0-9]{40}$/;
const sha256=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function runGit(root,args){
 const r=spawnSync('git',args,{cwd:root,shell:false,encoding:'utf8',
   timeout:10000,maxBuffer:8192});
 if(r.error||r.status!==0||r.signal)throw Error('LOCAL_AGENT_GIT_BLOCKED');
 return r.stdout.trim();
}
export function checkLocalMicroAgentCheckout(root=repo) {
 const full=fs.realpathSync(root);
 if(runGit(full,['rev-parse','--show-toplevel'])!==full)throw Error('LOCAL_AGENT_ROOT_MISMATCH');
 const branch=runGit(full,['symbolic-ref','--quiet','--short','HEAD']);
 if(!branch||/^(?:main|master|production|prod|release)(?:$|[/-])/i.test(branch))
   throw Error('LOCAL_AGENT_PRODUCTION_BRANCH');
 const origin=runGit(full,['remote','get-url','origin']);
 if(!new Set(['https://github.com/Ullmann27/device-ai',
   'https://github.com/Ullmann27/device-ai.git',
   'git@github.com:Ullmann27/device-ai.git',
   'ssh://git@github.com/Ullmann27/device-ai.git']).has(origin))
   throw Error('LOCAL_AGENT_WRONG_REPO');
 if(runGit(full,['status','--porcelain','--untracked-files=normal']))
   throw Error('LOCAL_AGENT_DIRTY_CHECKOUT');
 const commit=runGit(full,['rev-parse','HEAD']);
 if(!SHA40.test(commit))throw Error('LOCAL_AGENT_INVALID_HEAD');
 return Object.freeze({branch,commit});
}
export function microAgentPlan(selection='all') {
 if(selection!=='all'&&!Object.hasOwn(MICRO_AGENT_JOBS,selection))
   throw Error('LOCAL_AGENT_UNKNOWN_ROLE');
 const chosen=selection==='all'?names:[selection];
 return Object.freeze(chosen.map(id=>Object.freeze({id,role:MICRO_AGENT_JOBS[id].role,
   tests:MICRO_AGENT_JOBS[id].tests})));
}
function minimalEnv() {
 const env={};
 for(const k of ['PATH','HOME','PREFIX','TMPDIR','LANG','LC_ALL','ANDROID_HOME','JAVA_HOME'])
   if(typeof process.env[k]==='string')env[k]=process.env[k];
 env.NODE_OPTIONS='';
 env.NODE_USE_ENV_PROXY='0';
 return env;
}
/** Injected runner is used solely by tests; never accepts a model-provided
 * executable or a model-provided filename. Fixed test source files only.
 */
export function runMicroAgentChecks({root=repo,selection='all',
  runner=spawnSync,limitMs=60000}={}) {
 if(!Number.isSafeInteger(limitMs)||limitMs<1000||limitMs>90000)
   throw Error('LOCAL_AGENT_INVALID_BUDGET');
 const plans=microAgentPlan(selection);
 const results=[];
 for(const job of plans){
   const files=job.tests.map(f=>{
     const full=path.resolve(root,f);
     if(!full.startsWith(path.resolve(root)+path.sep))throw Error('LOCAL_AGENT_UNSAFE_PATH');
     const stat=fs.lstatSync(full);
     if(!stat.isFile()||stat.isSymbolicLink()||stat.size>500000||
       fs.realpathSync(full)!==full)throw Error('LOCAL_AGENT_UNSAFE_TEST');
     return f;
   });
   const start=Date.now();
   const r=runner(process.execPath,['--test',...files],{
     cwd:root,env:minimalEnv(),shell:false,encoding:'utf8',
     timeout:limitMs,maxBuffer:160000});
   const durationMs=Date.now()-start;
   const status=r.error||r.signal?'INTERRUPTED':r.status===0?'CONTRACT_TESTS_PASS':'CONTRACT_TESTS_FAIL';
   results.push(Object.freeze({id:job.id,role:job.role,status,exitCode:r.status??null,
     durationMs,outputSha256:sha256(String(r.stdout??'')+'\n'+String(r.stderr??'')),
     executedNativeModelInference:false,verifiedCodingRepair:false,
     androidDeviceTestsExecuted:false}));
   if(status!=='CONTRACT_TESTS_PASS')break;
 }
 return Object.freeze({schema:'continuum-local-microagents',version:1,
   mode:'DETERMINISTIC_OFFLINE_CONTRACT_CHECKS',
   deepSeekCodingConnected:false,gemmaNativeInferenceVerified:false,
   noApiCostRequired:true,codeGenerationAvailable:false,
   ownerPrivilegesGranted:false,
   completed:results.length===plans.length&&results.every(r=>r.status==='CONTRACT_TESTS_PASS'),
   results:Object.freeze(results)});
}
function main(argv){
 if(argv.length===1&&argv[0]==='--list'){
   process.stdout.write(JSON.stringify({roles:microAgentPlan(),deepSeekCodingConnected:false})+'\n');
   return;
 }
 if(argv.length!==2||argv[0]!=='--run')throw Error('Usage: node scripts/continuum-microagents.mjs --list|--run <gemma|deepseek|coding|transfer|update|all>');
 const profile=checkLocalMicroAgentCheckout();
 const report=runMicroAgentChecks({selection:argv[1]});
 process.stdout.write(JSON.stringify({...report,sourceCommit:profile.commit,branch:profile.branch},null,2)+'\n');
 if(!report.completed)process.exitCode=2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{main(process.argv.slice(2));}
 catch(e){process.stderr.write('CONTINUUM_MICRO_AGENT_BLOCKED='+String(e.message)+'\n');process.exitCode=2;}
}
