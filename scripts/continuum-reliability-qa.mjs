import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {runOwnedCommand} from './continuum-owned-process.mjs';
import {parseJUnit} from '../.github/continuum/repair-diagnostics.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const hash=text=>crypto.createHash('sha256').update(text).digest('hex');
const contracts=['android-always-on-wiring','android-integration-wiring','autonomous-topic-frontier',
  'coding-agent-speech-wiring','gemma-reopen-lifecycle','groq-mentor-ui-wiring','model-import-identity',
  'nexus-beginner-ui','research-job-permission'].map(n=>'.github/continuum/'+n+'.test.mjs');
export const GATES=Object.freeze([
  {id:'process',severity:'P0',files:['scripts/continuum-owned-process.test.mjs',
    '.github/continuum/step-watchdog.test.mjs','.github/continuum/step-watchdog-reliability.test.mjs',
    '.github/continuum/research-verifier-integration.test.mjs','.github/continuum/teamleader-end-to-end.test.mjs']},
  {id:'storage-agent',severity:'P0',files:['.github/continuum/storage-fabric.test.mjs',
    '.github/continuum/coding-agent-loop.test.mjs','scripts/continuum-architecture-proof.test.mjs']},
  {id:'android-source-contracts',severity:'P1',files:contracts},
  {id:'host-checker',severity:'P0',files:['scripts/android-kotlin-compile-check.test.mjs']}
]);

/** Exit zero alone is never evidence, including when all tests skipped or output is incomplete. */
export function assessNodeOutcome(result,xml,project=root) {
  const tests=parseJUnit(xml,project);
  const document=xml.replace(/^\s*<\?xml[^?]*\?>/,'').trim();
  const documentRoot=document.match(/^<(testsuites?)\b[^>]*>/)?.[1];
  const closed=documentRoot&&document.endsWith('</'+documentRoot+'>');
  if(!result.ok||result.code!==0||result.terminationConfirmed!==true||result.error||result.exitSignal)
    return {status:'FAIL',reason:result.error||'PROCESS_FAILED_OR_UNCONFIRMED',tests};
  if(!closed||!tests.evidenceComplete||tests.failed>0||tests.placeholders>0||tests.total===0)
    return {status:'FAIL',reason:'MISSING_FAILED_OR_INCOMPLETE_TEST_EVIDENCE',tests};
  if(tests.executed===0)return {status:'SKIP',reason:'NO_EXECUTED_TESTS',tests};
  return {status:'PASS',reason:null,tests};
}

function git(args) {
  const r=spawnSync('git',args,{cwd:root,encoding:'utf8',timeout:10000});
  if(r.status!==0)throw Error('GIT_METADATA_UNAVAILABLE');
  return r.stdout.trim();
}
function identity() {
  // Include every tracked file and nonignored untracked source, never dump environment values.
  const names=git(['ls-files','-z','--cached','--others','--exclude-standard']).split('\0').filter(Boolean).sort();
  const inputs={};
  for(const name of names) {
    const file=path.join(root,name),s=fs.lstatSync(file,{throwIfNoEntry:false});
    inputs[name]=!s?'DELETED':s.isSymbolicLink()?hash(fs.readlinkSync(file)):s.isFile()?hash(fs.readFileSync(file)):'NOT_FILE';
  }
  return {commit:git(['rev-parse','HEAD']),branch:git(['branch','--show-current']),inputsSha256:inputs,
    treeDigest:hash(JSON.stringify(inputs))};
}
export function renderReport(report) {
  const rows=report.checks.map(c=>`| ${c.id} | ${c.severity} | ${c.status} | ${c.code??'—'} | ${c.tests?.passed??'—'} / ${c.tests?.failed??'—'} / ${c.tests?.skipped??'—'} |`).join('\n');
  return '<!-- coderabbit-ai-generated:v1 -->\n\n# Continuum reliability QA\n\n'+
    `Scope: ${report.profile}. Status: **${report.status}**. Commit: ${report.source.commit}.\n\n`+
    `Node ${report.environment.node}; ${report.environment.platform}/${report.environment.arch}. Source unchanged during run: ${report.sourceUnchanged}.\n\n`+
    '| Check | Severity | Status | Exit | PASS / FAIL / SKIP |\n|---|---|---|---|---|\n'+rows+
    '\n\nPASS applies only to executed tests in the selected scope. JSON contains commands, case identities, diagnostics, source hashes and original process outcomes. Gradle/lint/APK/device/model inference remain NOT_RUN. No signing or deployment.\n\n<!-- /coderabbit-ai-generated:v1 -->\n';
}

async function main(args) {
  if(args.length===1&&args[0]==='--list') {
    process.stdout.write(JSON.stringify({profiles:['critical','full-node'],checks:GATES},null,2)+'\n');return;
  }
  const profile=args[0];
  if(!['critical','full-node'].includes(profile)||args[1]!=='--out'||args.length!==3)
    throw Error('Usage: node scripts/continuum-reliability-qa.mjs <critical|full-node> --out <new-output-directory> (or --list)');
  if(process.env.CONTINUUM_QA_DEPTH||process.env.CONTINUUM_REPAIR_DEPTH)
    throw Error('NESTED_QA_REFUSED');
  const out=path.resolve(args[2]);
  // Report files must not mutate the source identity being attested.
  if(out===root.slice(0,-1)||out.startsWith(root))throw Error('OUTPUT_MUST_BE_OUTSIDE_CHECKOUT');
  fs.mkdirSync(path.dirname(out),{recursive:true});fs.mkdirSync(out,{mode:0o700});
  const source=identity(),startedAt=new Date().toISOString();
  const full={id:'full-node',severity:'P0',files:['.github/continuum','scripts'].flatMap(dir=>
    fs.readdirSync(path.join(root,dir)).filter(n=>n.endsWith('.test.mjs')).sort().map(n=>dir+'/'+n))};
  const selected=profile==='critical'?GATES:[full];
  const report={schemaVersion:1,profile,source,startedAt,environment:{node:process.versions.node,
    platform:process.platform,arch:process.arch,release:os.release()},checks:[],status:'FAIL',sourceUnchanged:false};
  const controller=new AbortController();
  const cancel=()=>controller.abort();
  process.on('SIGINT',cancel);process.on('SIGTERM',cancel);
  try {
    for(const gate of selected) {
      if(controller.signal.aborted){report.checks.push({...gate,status:'NOT_RUN',reason:'CANCELLED'});continue;}
      const junit=path.join(out,gate.id+'.xml');
      const command=[process.execPath,'--test','--test-concurrency=2','--test-reporter=junit',
        '--test-reporter-destination='+junit,...gate.files];
      const result=await runOwnedCommand(command[0],command.slice(1),{cwd:root,timeout:30*60*1000,
        env:{...process.env,CONTINUUM_QA_DEPTH:'1'},signal:controller.signal,maxOutputBytes:2*1024*1024});
      fs.writeFileSync(path.join(out,gate.id+'.log'),result.output,{flag:'wx',mode:0o600});
      let xml='';try{xml=fs.readFileSync(junit,'utf8');}catch{}
      const assessment=assessNodeOutcome(result,xml);
      report.checks.push({id:gate.id,severity:gate.severity,command,cwd:root,...assessment,
        code:result.code,exitSignal:result.exitSignal,error:result.error,
        terminationConfirmed:result.terminationConfirmed===true,junitSha256:hash(xml),
        regressionFingerprint:hash(gate.id+'\0'+assessment.tests.diagnostics.map(d=>d.fingerprint).sort().join('\0'))});
      // Checkpoint after each gate, before another potentially long-running command.
      fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n',{mode:0o600});
    }
  } finally {
    process.removeListener('SIGINT',cancel);process.removeListener('SIGTERM',cancel);
    const after=identity();report.sourceUnchanged=after.commit===source.commit&&after.treeDigest===source.treeDigest;
    report.finishedAt=new Date().toISOString();
    report.status=report.sourceUnchanged&&report.checks.length===selected.length&&report.checks.every(c=>c.status==='PASS')?'PASS':'FAIL';
    for(const id of ['gradle','lint','apk','fold7','model-inference'])report.checks.push({id,severity:'P0',status:'NOT_RUN',reason:'OUTSIDE_SELECTED_HOST_SCOPE'});
    fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n',{mode:0o600});
    fs.writeFileSync(path.join(out,'report.md'),renderReport(report),{flag:'wx',mode:0o600});
  }
  process.stdout.write(`CONTINUUM_QA_${report.status} ${out}\n`);
  process.exitCode=report.status==='PASS'?0:1;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))
  main(process.argv.slice(2)).catch(error=>{process.stderr.write(error.message+'\n');process.exitCode=1;});
