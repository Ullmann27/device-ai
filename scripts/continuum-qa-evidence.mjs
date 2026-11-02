import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {readJUnitDirectory} from '../.github/continuum/repair-diagnostics.mjs';

export const REQUIRED_ANDROID_SUITES=Object.freeze([
  'at.heinz.deviceai.ToolExecutionStartLatchTest',
  'at.heinz.deviceai.ReadOnlyToolSizeRegressionTest',
  'at.heinz.deviceai.cognitive.LocalPromptFrameTest',
  'at.heinz.deviceai.ToolDurableOutcomeTest',
  'at.heinz.deviceai.localmodel.LocalToolContinuationTest',
  'at.heinz.deviceai.cognitive.ModelNeutralBrainIntegrationTest',
  'at.heinz.deviceai.cognitive.ReadOnlyMultiStepTest',
  'at.heinz.deviceai.learning.interactive.ResearchFeedbackMultiprocessTest'
]);
const validator=fileURLToPath(new URL('./continuum-junit-validator.py',import.meta.url));

/** Validate actual testcase execution, not a filename or Gradle's exit code. */
export function androidEvidence({root,startedAtMillis,finishedAtMillis=Date.now()}) {
  const dir=path.join(root,'app/build/test-results/testDebugUnitTest');
  const failures=[],mandatory=[];
  const reject=reason=>failures.push(reason);
  if(!Number.isSafeInteger(startedAtMillis)||startedAtMillis<1||
      !Number.isSafeInteger(finishedAtMillis)||finishedAtMillis<startedAtMillis)
    reject('INVALID_TEST_TIME_BOUNDARY');
  const stat=fs.lstatSync(dir,{throwIfNoEntry:false});
  const names=stat?.isDirectory()&&!stat.isSymbolicLink()?fs.readdirSync(dir):[];
  const files=names.filter(n=>n.endsWith('.xml'));
  if(files.length===0||files.length>2000)reject('EMPTY_OR_OVERSIZED_JUNIT');
  for(const name of files) {
    const s=fs.lstatSync(path.join(dir,name));
    if(!s.isFile()||s.isSymbolicLink()||s.size<1||s.size>2_000_000||
        s.mtimeMs<startedAtMillis-1000||s.mtimeMs>finishedAtMillis+1000)
      reject('STALE_OR_INVALID_JUNIT:'+name);
  }
  for(const klass of REQUIRED_ANDROID_SUITES) {
    const name='TEST-'+klass+'.xml';
    if(!files.includes(name)){reject('MISSING_REQUIRED_SUITE:'+klass);continue;}
    if(failures.some(x=>x==='STALE_OR_INVALID_JUNIT:'+name))continue;
    try {
      const counts=JSON.parse(execFileSync('python3',[validator,path.join(dir,name),klass],
        {encoding:'utf8',timeout:10000,maxBuffer:32768,stdio:['ignore','pipe','pipe']}));
      if(!Number.isSafeInteger(counts.executed)||counts.executed<1||counts.skipped!==0||
          counts.failures!==0||counts.errors!==0)throw Error('Invalid execution counts');
      mandatory.push({name:klass,...counts});
    } catch {reject('REQUIRED_SUITE_NOT_EXECUTED:'+klass);}
  }
  // Never follow symlinked XML into the aggregate parser.
  const aggregate=failures.some(x=>x.startsWith('STALE_OR_INVALID_JUNIT:'))?null:
    readJUnitDirectory(dir,root);
  if(!aggregate?.evidenceComplete||aggregate.executed<1||aggregate.failed!==0||
      aggregate.placeholders!==0)reject('INCOMPLETE_OR_FAILED_JUNIT');
  return {status:failures.length?'FAIL':'PASS',startedAtMillis,finishedAtMillis,
    xmlFiles:files.length,mandatory,counts:aggregate&&{
      total:aggregate.total,executed:aggregate.executed,passed:aggregate.passed,
      failed:aggregate.failed,skipped:aggregate.skipped},failures};
}

export function nodeReceiptEvidence(receipt,expectedHead,profile,startedAtMillis) {
  const selected=profile==='critical'?
    ['process','storage-agent','android-source-contracts','host-checker']:['full-node'];
  const from=Date.parse(receipt.startedAt),to=Date.parse(receipt.finishedAt);
  const checks=Array.isArray(receipt.checks)?receipt.checks:[];
  const valid=/^[a-f0-9]{40}$/.test(expectedHead)&&['critical','full-node'].includes(profile)&&
    receipt.status==='PASS'&&receipt.sourceUnchanged===true&&receipt.source?.commit===expectedHead&&
    receipt.profile===profile&&Number.isSafeInteger(startedAtMillis)&&
    from>=startedAtMillis-1000&&to>=from&&to<=Date.now()+1000&&
    selected.every(id=>{
      const gate=checks.filter(c=>c.id===id);
      return gate.length===1&&gate[0].status==='PASS'&&gate[0].code===0&&
        gate[0].terminationConfirmed===true&&gate[0].tests?.executed>0&&
        gate[0].tests.evidenceComplete===true&&gate[0].tests.failed===0;
    });
  return {status:valid?'PASS':'FAIL',expectedHead,profile,
    failures:valid?[]:['MISSING_STALE_OR_MISMATCHED_NODE_RECEIPT']};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    const [kind,rootOrFile,sourceOrStart,profile,start]=process.argv.slice(2);
    const result=kind==='android'?androidEvidence({root:rootOrFile,startedAtMillis:Number(sourceOrStart)}):
      kind==='node'?nodeReceiptEvidence(JSON.parse(fs.readFileSync(rootOrFile,'utf8')),
        sourceOrStart,profile,Number(start)):null;
    if(!result)throw Error('Usage: continuum-qa-evidence.mjs android ROOT START | node RECEIPT SHA PROFILE START');
    process.stdout.write(JSON.stringify(result,null,2)+'\n');
    process.exitCode=result.status==='PASS'?0:1;
  } catch(error) {process.stderr.write('QA_EVIDENCE_REJECTED: '+error.message+'\n');process.exitCode=1;}
}
