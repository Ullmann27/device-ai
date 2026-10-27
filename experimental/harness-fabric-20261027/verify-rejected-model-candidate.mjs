/** Re-run an already produced model candidate independently, without any new inference.
 * The report/fixture must be synthetic owner research, never unknown model paths.
 */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
import {createIsolatedExecutor,loadIsolationConfig} from '../../.github/continuum/isolated-executor.mjs';
import {runIndependentCcc1PatchVerification,CCC1_RESEARCH_OUTCOMES} from '../../.github/continuum/ccc1-independent-verifier.mjs';
import {taskFingerprint} from '../../.github/continuum/coding-task.mjs';
const out=path.resolve(process.argv[2]),report=JSON.parse(fs.readFileSync(path.join(out,'result.json'),'utf8'));
const file='lib/ledger.mjs',projectRoot=path.join(out,'source'),original=fs.readFileSync(path.join(projectRoot,file),'utf8');
if(report.task.allowedPaths.join(',')!==file||report.proposals.length!==1)throw Error('UNEXPECTED_RESEARCH_REPORT');
let code=original;for(const p of report.proposals[0].patches){if(p.file!==file||code.split(p.oldText).length!==2)throw Error('EXACT_SOURCE_REQUIRED');code=code.replace(p.oldText,p.newText);}
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const result=runIndependentCcc1PatchVerification({typedTask:report.task,file,projectRoot,stateDir:path.join(out,'state'),
 executor:createIsolatedExecutor(loadIsolationConfig(process.argv[3],{sourceRoot:projectRoot})),deadline:Date.now()+120000,
 candidate:{status:'CANDIDATE_UNVERIFIED',sourceCommit:report.task.sourceCommit,taskSha256:taskFingerprint(report.task),sourceSha256:sha(original),candidateSha256:sha(code),code}});
fs.writeFileSync(path.join(out,'independent-rejection.json'),JSON.stringify({modelGenerated:true,newInferenceCalls:0,result},null,2)+'\n',{flag:'wx',mode:0o600});
fs.writeFileSync(path.join(out,'REJECTED-model-candidate.mjs'),code,{flag:'wx',mode:0o600});
console.log(JSON.stringify({status:result.status,reason:result.reason,modelGenerated:true}));
if(result.status!==CCC1_RESEARCH_OUTCOMES.FAILED)process.exitCode=1;
