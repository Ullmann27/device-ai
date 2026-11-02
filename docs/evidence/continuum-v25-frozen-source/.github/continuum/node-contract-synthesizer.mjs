/**
 * Owner-approved, deterministic Node contract-test synthesis. No model-supplied
 * JavaScript is executed: all arguments and expected outputs are encoded as
 * bounded JSON. Candidates still require real worktree tests to become DONE.
 */
import crypto from 'node:crypto';
import {critique} from './repair-critic.mjs';
import {renderOwnerNodeContract} from './contract-test-template.mjs';

const MODULE=/^\.github\/continuum\/([A-Za-z][A-Za-z0-9_-]{0,63})\.mjs$/;
const IDENT=/^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
const TASK=/^[a-z][a-z0-9-]{2,63}$/;
export function proposeNodeContractTest({files,task}={}){
 if(!task||task.kind!=='TEST'||task.source!=='owner'||task.state!=='READY'||
    !TASK.test(task.id??''))return {candidate:null,reason:'OWNER_ACCEPTANCE_REQUIRED'};
 const s=task.subject;
 const match=MODULE.exec(s?.modulePath??'');
 // Backlog v2 accepts only scalar subject fields (maximum 300 characters).
 // Nested case arrays are not valid Kotlin/Node-parity backlog data.
 if(s?.operation!=='ASSERT_NODE_EXPORT'||!match||!IDENT.test(s.exportName??'')||
    typeof s.casesJson!=='string'||s.casesJson.length>300||
    !/^[0-9a-f]{40}$/.test(s.base??'')||s.chain!=='node')
  return {candidate:null,reason:'INVALID_CONTRACT_SPEC'};
 let caseSpecs;
 try{
  caseSpecs=JSON.parse(s.casesJson);
  if(!Array.isArray(caseSpecs)||caseSpecs.length<1||caseSpecs.length>4)
   throw Error('Invalid case count');
 }catch{return {candidate:null,reason:'INVALID_CASES_JSON'};}
 const modulePath=s.modulePath,testPath='.github/continuum/'+match[1]+'.contract.test.mjs';
 if(!Array.isArray(files)||files.length<1||files.length>3000)
  return {candidate:null,reason:'INVALID_SOURCE_SET'};
 const modules=files.filter(f=>f.path===modulePath&&typeof f.content==='string');
 if(modules.length!==1||modules[0].content.length>200_000||
    files.some(f=>f.path===testPath))return {candidate:null,reason:'AMBIGUOUS_OR_EXISTING_TEST'};
 const exported=new RegExp('^export\\s+(?:(?:async\\s+)?function\\s+'+s.exportName+
   '\\s*\\(|const\\s+'+s.exportName+'\\s*=)','m');
 if(!exported.test(modules[0].content))return {candidate:null,reason:'EXPORT_NOT_LOCATED'};
 const contractSpec={file:testPath,exportName:s.exportName,casesJson:s.casesJson};
 const newText=renderOwnerNodeContract(testPath,s.exportName,s.casesJson);
 if(newText===null)return {candidate:null,reason:'UNSAFE_OR_UNSUPPORTED_JSON_CASE'};
 const proposal={
  id:crypto.createHash('sha256').update(task.id+'\0'+JSON.stringify(s)).digest('hex').slice(0,16),
  strategy:'SYNTHESIZE_NODE_CONTRACT_TEST',
  description:'Owner-specified concrete input/output contract for a Node export',
  diagnostic:'owner:'+task.id,
  prior:0.5,reviewRequired:true,
  contractSpec,edits:[{file:testPath,oldText:'',newText}]
 };
 const review=critique(proposal);
 if(!review.approved)return {candidate:null,reason:'CRITIC_REJECTED: '+review.reasons.join('; ')};
 return {candidate:{...proposal,critic:review},reason:null};
}
