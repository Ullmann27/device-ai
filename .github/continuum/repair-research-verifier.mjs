/** Trusted opt-in verifier capability. Model proposals cannot supply callbacks.
 * A hash binds bytes; it does not authenticate an untrusted verifier. This gate
 * can only add a veto after existing tests; it cannot replace any test or grant. */
import {canonical,sha256Hex} from './development-backlog.mjs';
import {isCodingPath} from './coding-task.mjs';

const SHA=/^[a-f0-9]{64}$/;
const exact=(v,keys)=>v&&typeof v==='object'&&!Array.isArray(v)&&
 Object.keys(v).sort().join(',')===[...keys].sort().join(',');
const finite=v=>typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e12;
function frozen(v){
 if(v&&typeof v==='object'){for(const x of Object.values(v))frozen(x);Object.freeze(v);}
 return v;
}
export function runCandidateVerifier(verifier,{base,sources,deadline}={}){
 if(verifier===null||verifier===undefined)return null;
 const abstain=reason=>({status:'ABSTAIN',checks:0,counterexample:null,reason});
 try{
  if(typeof verifier!=='function'||!SHA.test(verifier.suiteSha256??'')||
     !Number.isSafeInteger(verifier.expectedChecks)||verifier.expectedChecks<1||verifier.expectedChecks>512||
     !/^[a-f0-9]{40}$/.test(base??'')||!Number.isSafeInteger(deadline)||
     !Array.isArray(sources)||sources.length<1||sources.length>3||
     new Set(sources.map(s=>s?.file)).size!==sources.length||
     sources.some(s=>!exact(s,['file','source'])||!isCodingPath(s.file)||
      typeof s.source!=='string'||Buffer.byteLength(s.source)>750000))return abstain('INVALID_REQUEST');
  if(Date.now()>=deadline)return abstain('DEADLINE');
  const suiteSha256=verifier.suiteSha256,expectedChecks=verifier.expectedChecks;
  const copied=structuredClone(sources).sort((a,b)=>a.file.localeCompare(b.file));
  const candidateSha256=sha256Hex(canonical({base,sources:copied}));
  const result=verifier(frozen({base,sources:copied,candidateSha256,deadline}));
  if(Date.now()>=deadline)return abstain('DEADLINE');
  if(!exact(result,['schema','version','base','candidateSha256','suiteSha256','status','checks','counterexample'])||
     result.schema!=='continuum-candidate-verification'||result.version!==1||result.base!==base||
     result.candidateSha256!==candidateSha256||result.suiteSha256!==suiteSha256||
     !['PASS','FAIL','ABSTAIN'].includes(result.status)||
     !Number.isSafeInteger(result.checks)||result.checks<0||result.checks>expectedChecks||
     result.status==='PASS'&&(result.checks!==expectedChecks||result.counterexample!==null)||
     result.status==='FAIL'&&(result.checks<1||result.counterexample===null)||
     result.status==='ABSTAIN'&&result.counterexample!==null)return abstain('INVALID_EVIDENCE');
  const c=result.counterexample;
  if(c!==null&&(!exact(c,['id','file','inputs','actual','expected'])||
     !/^[a-z0-9-]{1,48}$/.test(c.id??'')||!copied.some(s=>s.file===c.file)||
     !Array.isArray(c.inputs)||c.inputs.length<1||c.inputs.length>8||c.inputs.some(v=>!finite(v))||
     !(c.actual===null||finite(c.actual))||!(c.expected===null||finite(c.expected))))
   return abstain('INVALID_COUNTEREXAMPLE');
  return structuredClone(result);
 }catch{return abstain('VERIFIER_ERROR');}
}
