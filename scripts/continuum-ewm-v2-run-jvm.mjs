#!/usr/bin/env node
/** Executes pinned production EWM1Memory via kotlinc/jvm with no Android SDK, paid service or Gradle. */
import {spawnSync} from 'node:child_process';
import {readFileSync,writeFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname,resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {gitBlobSha,parseJsonl,scoreHoldout,FROZEN_GIT_BLOB} from './continuum-ewm-v2-holdout-score.mjs';

export const CANDIDATE_SHA='3ba1ba0bba28e441d2b85ae098371901d3db7407';
const LEXICON_BLOB='8c1e6acd4a7ee2cf632b115550eb7c7d5c801df6';
const MEMORY_BLOB='cb19581af34ad177add22a89638988a74a9c7aad';

function expect(condition,code){if(!condition)throw Error(code);}
function readFrozen(frozenFile){
 const bytes=readFileSync(frozenFile);
 expect(gitBlobSha(bytes)===FROZEN_GIT_BLOB,'FROZEN_HOLDOUT_MODIFIED');
 const gold=parseJsonl(bytes.toString('utf8'),'GOLD');
 expect(gold.length===240,'EXPECTED_EXACTLY_240');
 for(let i=0;i<gold.length;i++){
  const row=gold[i];expect(row.id==='M6X-'+String(i+1).padStart(3,'0'),'INVALID_FROZEN_ID');
  expect(typeof row.before==='string'&&typeof row.after==='string'&&typeof row.unrelated==='string','INVALID_FROZEN_TEXT');
 }
 return gold;
}
export function encodeProbeInput(gold){
 const encode=s=>Buffer.from(s,'utf8').toString('base64');
 // Gold decisions are NEVER sent to the candidate Kotlin process.
 return gold.map(row=>[row.id,encode(row.before),encode(row.after),encode(row.unrelated)].join('\t')).join('\n')+'\n';
}
export function observationsToReceipts(stdout,gold,candidateSha=CANDIDATE_SHA){
 const lines=stdout.trimEnd().split('\n');
 expect(lines.length===gold.length,'JVM_RESULT_COUNT_MISMATCH');
 const receipts=[];
 for(let i=0;i<lines.length;i++){
  const fields=lines[i].split('\t');expect(fields.length===6,'JVM_INVALID_FIELD_COUNT');
  const [id,positive,before,after,unrelated,reasonB64]=fields;
  const row=gold[i];expect(id===row.id,'JVM_RESULT_ORDER_MISMATCH');
  expect(positive==='0'||positive==='1','JVM_INVALID_EDGE');
  for(const flag of [before,after,unrelated])expect(flag==='true'||flag==='false','JVM_INVALID_STATE_FLAG');
  // Node's base64 decoder silently ignores invalid characters and missing padding.
  // A receipt must preserve the exact canonical bytes emitted by the JVM probe.
  const reasonBytes=Buffer.from(reasonB64,'base64');
  expect(/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(reasonB64) &&
    reasonBytes.toString('base64')===reasonB64,'JVM_INVALID_REASON_BASE64');
  const reasons=reasonBytes.toString('utf8');
  const parts=/^PRODUCTION_GRAPH:edges_from_after=((?:before|unrelated)(?:,(?:before|unrelated))*)?;replaces_before=(true|false)$/.exec(reasons);
  expect(parts!==null,'JVM_INVALID_REASON');
  const edgeSet=new Set((parts[1]||'').split(',').filter(Boolean));
  expect((positive==='1')===(parts[2]==='true') && (positive==='1')===edgeSet.has('before'),
    'JVM_EDGE_REASON_MISMATCH');
  expect((before==='false')===(positive==='1'),'JVM_BEFORE_STATE_EDGE_CONFLICT');
  expect(after==='true','JVM_AFTER_NOT_CURRENT');
  expect(unrelated==='false'||!edgeSet.has('unrelated'),'JVM_UNRELATED_STATE_CONFLICT');
  receipts.push({
   id,candidate_sha:candidateSha,expected_label:row.expected,
   // Production graph decides only whether a fact is superseded, NOT the event/state subtype.
   // Never infer a positive subtype from the frozen gold expected label.
   predicted_label:positive==='1'?'SUPERSEDES_STATE':'COEXISTS',
   reasons,before_current:before==='true',after_current:after==='true',
   unrelated_fact_kept:unrelated==='true',positive_edge:positive==='1',
   positive_subtype:'NOT_EMITTED_BY_PRODUCT'
  });
 }
 return receipts;
}
function run(command,args,options={}){
 const out=spawnSync(command,args,{encoding:'utf8',maxBuffer:16*1024*1024,timeout:120000,...options});
 if(out.error)throw Error('EXECUTION_FAILED:'+command+':'+out.error.message);
 if(out.status!==0)throw Error('COMMAND_FAILED:'+command+':exit_'+out.status+':'+(out.stderr||'').slice(0,1800));
 return out.stdout;
}
export function runRealJvm({root,outputFile}){
 const frozen=resolve(root,'docs/research/m6-independent/ewm-v2-foreign-240.jsonl');
 const gold=readFrozen(frozen);
 const pathBase='app/src/main/java/at/heinz/deviceai/intelligence/ewm/';
 const lexicon=resolve(root,pathBase,'Ewm1Lexicon.kt');
 const memory=resolve(root,pathBase,'Ewm1EvidenceWorkspace.kt');
 expect(gitBlobSha(readFileSync(lexicon))===LEXICON_BLOB,'LEXICON_CANDIDATE_CHANGED');
 expect(gitBlobSha(readFileSync(memory))===MEMORY_BLOB,'EWM_MEMORY_CANDIDATE_CHANGED');
 const probe=resolve(root,'app/src/test/java/at/heinz/deviceai/intelligence/ewm/M6ForeignEwmProbe.kt');
 const scratch=mkdtempSync(join(tmpdir(),'continuum-ewm-v2-'));
 try{
  const jar=join(scratch,'ewm2-m6-probe.jar');
  run('kotlinc',[lexicon,memory,probe,'-include-runtime','-d',jar]);
  const observations=run('java',['-Xmx384m','-cp',jar,'at.heinz.deviceai.intelligence.ewm.M6ForeignEwmProbe'],{
   input:encodeProbeInput(gold),timeout:120000
  });
  const receipts=observationsToReceipts(observations,gold);
  const score=scoreHoldout(gold,receipts,CANDIDATE_SHA);
  const body=receipts.map(row=>JSON.stringify(row)).join('\n')+'\n';
  writeFileSync(outputFile,body,{flag:'wx',mode:0o600});
  return {...score,model_actual_jvm_run:true,model_positive_subtype:'NOT_MEASURED',receipt_path:outputFile};
 }finally{rmSync(scratch,{recursive:true,force:true});}
}
function cli(args){
 expect(args.length===2&&args[0]==='--receipts','USAGE: node scripts/continuum-ewm-v2-run-jvm.mjs --receipts <new-output.jsonl>');
 const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
 const result=runRealJvm({root,outputFile:resolve(args[1])});
 process.stdout.write(JSON.stringify(result,null,2)+'\n');
 if(result.status!=='PASS')process.exitCode=1;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{cli(process.argv.slice(2));}catch(e){process.stderr.write('M6_JVM_NOT_ACCEPTED:'+e.message+'\n');process.exitCode=2;}
}
