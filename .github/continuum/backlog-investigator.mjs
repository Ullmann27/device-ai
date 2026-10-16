// Bounded, evidence-backed autonomous INVESTIGATE worker. It does not modify
// application code or claim that static reachability proves runtime absence.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import * as B from './development-backlog.mjs';
import {analyzeRepository} from './code-analyzer.mjs';
import {git,createWorktree,removeWorktree,runProfile} from './repair-loop.mjs';

const normalizedPath=s=>String(s??'').replace(/^app\/src\//,'');
const matches=(finding,subject)=>finding.symbol===subject.symbol &&
  normalizedPath(finding.file)===normalizedPath(subject.file);
const checksum=text=>crypto.createHash('sha256').update(text).digest('hex');

export function investigateFinding(analysis,task){
  if(task?.kind!=='INVESTIGATE'||!task.subject ||
    !['TEST_ONLY','DEAD_CLUSTER','UNREFERENCED'].includes(task.subject.finding) ||
    typeof task.subject.symbol!=='string'||typeof task.subject.file!=='string')
    throw Error('Expected a structural INVESTIGATE task with bounded subject');
  if(!analysis?.index||!Array.isArray(analysis.findings))throw Error('Missing static analysis');
  const found=analysis.findings.find(f=>matches(f,task.subject)&&f.kind===task.subject.finding)??null;
  const other=analysis.findings.find(f=>matches(f,task.subject))??null;
  const declaration=analysis.index.byQualified.get(task.subject.symbol);
  const incoming=[...(analysis.index.incoming.get(task.subject.symbol)??[])].sort().slice(0,12);
  const outgoing=[...(analysis.index.edges.get(task.subject.symbol)??[])].sort().slice(0,12);
  const kind=found?'REPRODUCED_STATIC_FINDING':other?'RECLASSIFIED_STATIC_FINDING':
    declaration?'NOT_A_STATIC_FINDING':'DECLARATION_NOT_FOUND';
  return {
    schema:1,taskId:task.id,kind,original:task.subject,
    currentKind:found?.kind??other?.kind??null,
    declaration:declaration?{symbol:declaration.qualified,file:declaration.file,
      line:declaration.line,kind:declaration.kind}:null,
    incoming,outgoing,
    assessment:kind==='REPRODUCED_STATIC_FINDING'
      ? 'Static reachability is incomplete; check DI, reflection and Android runtime routes before wiring.'
      : 'Do not modify code based on this stale or nonreproducible finding.',
    proposedAction:kind==='REPRODUCED_STATIC_FINDING'?'REVIEW_WIRING_AND_ADD_RUNTIME_TEST':'NO_AUTOMATIC_CHANGE'
  };
}

/** Verify by recomputing the same finding from the same immutable tree. */
export function verifyInvestigation(report,analysis,task){
  return JSON.stringify(report)===JSON.stringify(investigateFinding(analysis,task));
}

/** Execute a READY task on the specified immutable Git commit. Reports are
 * submitted as REPORT artifacts. Only real JUnit-backed fixed Node test checks
 * can complete the task. A failed check records failure instead of DONE. */
/** A green process exit without executed and passing tests is not a completed
 * investigation. Both the backlog mutation and returned status use this gate. */
export function investigationChecksPassed(check) {
  return check?.passed===true &&
    Number.isSafeInteger(check.tests?.executed) && check.tests.executed>0 &&
    Number.isSafeInteger(check.tests?.failed) && check.tests.failed===0;
}

export function executeInvestigation({root=process.cwd(),file,out,base,actor='continuum-agent'}){
  if(!/^[0-9a-f]{40}$/.test(base??''))throw Error('Full immutable --base SHA required');
  if(typeof file!=='string'||!file||typeof out!=='string'||!out)throw Error('File/output paths required');
  const docPath=path.resolve(file),outputPath=path.resolve(out);
  if(outputPath===docPath||outputPath.startsWith(docPath+path.sep))throw Error('Output overlaps backlog');
  const store=new B.BacklogStore(docPath,{actor});
  const ready=store.read().doc.tasks.filter(t=>t.kind==='INVESTIGATE'&&t.state==='READY')
    .sort((a,b)=>b.priority-a.priority||a.id.localeCompare(b.id));
  if(!ready.length)return {status:'NO_READY_INVESTIGATION'};
  const task=ready[0],token=B.newToken(),at=Date.now();
  const claimed=store.apply([{op:'claim',actor,at,id:task.id,owner:actor,token,leaseMs:1800000}]);
  if(!claimed.results[0]?.ok)throw Error('Investigation claim rejected');
  let wt=null;
  try {
    wt=createWorktree(root,base);
    const analysis=analyzeRepository(wt),report=investigateFinding(analysis,task);
    if(!verifyInvestigation(report,analyzeRepository(wt),task))
      throw Error('Independent static verification mismatch');
    const bytes=Buffer.from(JSON.stringify({...report,base},null,2)+'\n');
    fs.mkdirSync(path.dirname(outputPath),{recursive:true});
    fs.writeFileSync(outputPath,bytes);
    const sha=checksum(bytes);
    const checked=runProfile('node-continuum',wt,path.dirname(outputPath));
    const submitAt=Date.now();
    const submission=store.apply([{op:'submit',actor,at:submitAt,id:task.id,token,
      artifact:{type:'REPORT',sha256:sha}}]);
    if(!submission.results[0]?.ok)throw Error('Investigation report submission rejected');
    const evidence={
      type:'TEST_RUN',taskId:task.id,submissionToken:token,artifactSha256:sha,
      chain:'node',commit:base,at:Date.now(),by:actor,repair:null,
      profiles:[{profile:'node-continuum',passed:checked.passed,
        executed:checked.tests.executed,failed:checked.tests.failed,skipped:checked.tests.skipped}]
    };
    const verified=investigationChecksPassed(checked);
    const op=verified
      ?{op:'complete',actor,at:evidence.at,id:task.id,evidence}
      :{op:'fail',actor,at:evidence.at,id:task.id,token,evidence,
        error:{code:'CHECKS_FAILED',message:'Independent Node regression checks failed or no tests executed'}};
    const saved=store.apply([op],{stopOnError:false});
    if(!saved.results[0]?.ok)throw Error('Investigation evidence rejected');
    return {status:verified?'DONE':'CHECKS_FAILED',taskId:task.id,
      base,reportSha256:sha,staticResult:report.kind,
      executed:checked.tests.executed,failed:checked.tests.failed};
  }catch(error){
    const current=store.read().doc.tasks.find(t=>t.id===task.id);
    if(current&&['IN_PROGRESS','VERIFYING'].includes(current.state)){
      store.apply([{op:'fail',actor,at:Date.now(),id:task.id,token,
        error:{code:'INVESTIGATION_FAILED',message:String(error.message).slice(0,300)}}],
        {stopOnError:false});
    }
    throw error;
  }finally{
    if(wt)removeWorktree(root,wt);
  }
}
