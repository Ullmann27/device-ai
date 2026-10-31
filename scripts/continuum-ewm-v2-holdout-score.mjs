#!/usr/bin/env node
/** Read-only assessment of actual predictions against the frozen M6 EWM-v2 foreign holdout. */
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export const LABELS=Object.freeze(['SUPERSEDES_STATE','CORRECTS_EVENT','COEXISTS']);
export const FROZEN_GIT_BLOB='622370c291ead9ab11ec8917c992037a2fcf9e17';
export const EXPECTED_ROWS=240;
const positive=new Set(LABELS.slice(0,2));
function fail(code,description){const error=new Error(code+': '+description);error.code=code;throw error;}
export function gitBlobSha(bytes){
 return createHash('sha1').update(Buffer.from('blob '+bytes.length+'\0')).update(bytes).digest('hex');
}
export function parseJsonl(input,kind){
 if(typeof input!=='string'||!input.trim())fail('EMPTY_'+kind,'No JSONL rows');
 return input.trimEnd().split('\n').map((line,i)=>{
  if(!line.trim())fail('BLANK_LINE',kind+':'+(i+1));
  try{
   const row=JSON.parse(line);
   if(!row||typeof row!=='object'||Array.isArray(row))fail('INVALID_ROW',kind+':'+(i+1));
   return row;
  }catch(error){if(error.code)throw error;fail('INVALID_JSON',kind+':'+(i+1));}
 });
}
const fraction=(n,d)=>d===0?null:n/d;
function classMetrics(matrix,label){
 const tp=matrix[label][label];
 const predicted=LABELS.reduce((n,actual)=>n+matrix[actual][label],0);
 const actual=LABELS.reduce((n,pred)=>n+matrix[label][pred],0);
 const precision=fraction(tp,predicted),recall=fraction(tp,actual);
 const f1=precision===null||recall===null?null:precision+recall===0?0:2*precision*recall/(precision+recall);
 return {tp,predicted,actual,precision,recall,f1};
}
export function scoreHoldout(gold,receipts,candidateSha){
 if(!/^[a-f\d]{40}$/.test(candidateSha))fail('INVALID_CANDIDATE_SHA','Require full 40-digit Git commit SHA');
 if(!Array.isArray(gold)||gold.length!==EXPECTED_ROWS)fail('GOLD_COUNT_MISMATCH','Expected 240 frozen cases');
 if(!Array.isArray(receipts)||receipts.length!==EXPECTED_ROWS)
  fail('RECEIPT_COUNT_MISMATCH','Expected 240 predictions; got '+(receipts?.length??'null'));
 const goldById=new Map();
 for(const [i,row] of gold.entries()){
  const id='M6X-'+String(i+1).padStart(3,'0');
  if(row?.id!==id||!LABELS.includes(row.expected))fail('INVALID_GOLD',id);
  if(goldById.has(id))fail('DUPLICATE_GOLD',id);
  goldById.set(id,row);
 }
 const matrix=Object.fromEntries(LABELS.map(l=>[l,Object.fromEntries(LABELS.map(p=>[p,0]))]));
 const seen=new Set();let unrelatedViolations=0;
 for(const [index,row] of receipts.entries()){
  const id=row?.id;
  if(typeof id!=='string'||!goldById.has(id))fail('UNKNOWN_RECEIPT_ID',(index+1)+': '+id);
  if(seen.has(id))fail('DUPLICATE_RECEIPT_ID',id);
  seen.add(id);
  if(row.candidate_sha!==candidateSha)fail('CANDIDATE_MISMATCH',id);
  if(row.expected_label!==goldById.get(id).expected)fail('EXPECTED_LABEL_MISMATCH',id);
  if(!LABELS.includes(row.predicted_label))fail('UNKNOWN_PREDICTION',id);
  if(typeof row.reasons!=='string'||!row.reasons.trim())fail('MISSING_EXPLANATION',id);
  for(const flag of ['before_current','after_current','unrelated_fact_kept']){
   if(typeof row[flag]!=='boolean')fail('INVALID_FLAG',id+'.'+flag);
  }
  if(!row.unrelated_fact_kept)unrelatedViolations++;
  matrix[goldById.get(id).expected][row.predicted_label]++;
 }
 const perLabel=Object.fromEntries(LABELS.map(l=>[l,classMetrics(matrix,l)]));
 const tp=LABELS.filter(l=>positive.has(l)).reduce((s,actual)=>
  s+LABELS.filter(p=>positive.has(p)).reduce((n,p)=>n+matrix[actual][p],0),0);
 const predictedPositive=LABELS.reduce((s,actual)=>
  s+LABELS.filter(p=>positive.has(p)).reduce((n,p)=>n+matrix[actual][p],0),0);
 const actualPositive=gold.filter(row=>positive.has(row.expected)).length;
 const positivePrecision=fraction(tp,predictedPositive),positiveRecall=fraction(tp,actualPositive);
 const f1s=LABELS.map(l=>perLabel[l].f1);
 const macroF1=f1s.every(v=>v!==null)?f1s.reduce((a,b)=>a+b,0)/f1s.length:null;
 const gates={
  complete:seen.size===EXPECTED_ROWS,
  positive_precision_at_least_095:positivePrecision!==null&&positivePrecision>=0.95,
  all_unrelated_facts_preserved:unrelatedViolations===0
 };
 return {
  status:Object.values(gates).every(Boolean)?'PASS':'FAIL',
  evaluation:'FOREIGN_EWM_V2_EXTERNAL_RECEIPTS_ONLY',
  candidate_sha:candidateSha,cases:EXPECTED_ROWS,
  matrix,per_label:perLabel,
  positive:{tp,predicted:predictedPositive,actual:actualPositive,precision:positivePrecision,recall:positiveRecall},
  macro_f1:macroF1,unrelated_fact_violations:unrelatedViolations,gates
 };
}
export function scoreFrozenFile({frozenFile,receiptFile,candidateSha}){
 const bytes=readFileSync(frozenFile),blob=gitBlobSha(bytes);
 if(blob!==FROZEN_GIT_BLOB)fail('FROZEN_DATA_TAMPERED',blob);
 return {
  ...scoreHoldout(parseJsonl(bytes.toString('utf8'),'GOLD'),
    parseJsonl(readFileSync(receiptFile,'utf8'),'RECEIPTS'),candidateSha),
  frozen_git_blob:blob
 };
}
function cli(args){
 if(args.length!==4||args[0]!=='--receipts'||args[2]!=='--candidate-sha')
  fail('USAGE','node scripts/continuum-ewm-v2-holdout-score.mjs --receipts <external.jsonl> --candidate-sha <full40sha>');
 const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
 const r=scoreFrozenFile({
  frozenFile:resolve(root,'docs/research/m6-independent/ewm-v2-foreign-240.jsonl'),
  receiptFile:resolve(args[1]),candidateSha:args[3]
 });
 process.stdout.write(JSON.stringify(r,null,2)+'\n');
 if(r.status!=='PASS')process.exitCode=1;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{cli(process.argv.slice(2));}
 catch(err){
  process.stderr.write(JSON.stringify({status:'NOT_ACCEPTED',code:err.code??'SCORE_ERROR',error:err.message})+'\n');
  process.exitCode=2;
 }
}
