#!/usr/bin/env node
/**
 * One plain command for the M8 worker, e.g. in Termux:
 *   node scripts/continuum-agent.mjs "Behebe den Fehler in distance: Abstand muss positiv sein"
 * 1. Survey (read-only): the brain lists, searches and reads the repository at HEAD and proposes 1-3 files to change.
 * 2. Owner grant: the proposed files are shown and must be confirmed (j/N); nothing is granted by the model.
 * 3. Repair: the tool-calling agent loop runs inside the unchanged repairSession (baseline must fail, chain must pass,
 *    general edits execute only in the isolated executor). The patch is printed for review; nothing is applied,
 *    committed or pushed.
 * Brain: the owner's local OpenAI-compatible server (CONTINUUM_LOCAL_BRAIN_ENDPOINT/_MODEL/_API_KEY), or
 * --script <actions.json> (a SIMULATED brain for tests and demos). Options: --repo <dir> --yes --executor <config>.
 * Without a request text the command is interactive: type one plain request per line ("Auftrag> ..."), confirm the
 * proposed files with j, finish with an empty line or "ende".
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import readline from 'node:readline';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {scoutRequest,createAgentCandidateProvider,createScriptedBrain,createChatCompletionsBrain,createScratchTestRunner} from '../.github/continuum/coding-agent-loop.mjs';
import {validateCodingTask} from '../.github/continuum/coding-task.mjs';
import {createWorktree,removeWorktree,repairSession} from '../.github/continuum/repair-loop.mjs';
import {loadIsolationConfig,createIsolatedExecutor} from '../.github/continuum/isolated-executor.mjs';

export function runAgentCommand({repo,request,brain,confirm,executor=null,log=()=>{}}){
  const git=args=>{const r=spawnSync('git',args,{cwd:repo,encoding:'utf8',shell:false});if(r.status!==0)throw Error('GIT_FAILED '+args[0]);return r.stdout.trim();};
  const head=git(['rev-parse','HEAD']);
  if(git(['status','--porcelain']))throw Error('CODING_TASK_DIRTY_SOURCE');
  const scope=scoutRequest({wt:repo,base:head,request,brain,onStep:s=>log(`  [Sichtung] ${s.tool}`)});
  log(`Vorschlag (${scope.brainKind}): Dateien ${scope.files.join(', ')}\nPrüfkriterien:\n${scope.acceptance.map(a=>'  - '+a).join('\n')}`);
  if(!confirm(scope))return {status:'NOT_GRANTED',scope};
  const task=validateCodingTask({schema:'continuum-coding-task',version:1,
    id:'cli-'+createHash('sha256').update(head+'\0'+request).digest('hex').slice(0,16),sourceCommit:head,kind:scope.kind,
    goal:request.trim().slice(0,600),allowedPaths:scope.files,acceptance:scope.acceptance,chain:scope.chain,
    limits:{maxModelCalls:3,maxContextChars:12000}});
  const out=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-agent-run-'));
  const wt=createWorktree(repo,head);
  // In-loop run_tests: same chain and execution policy as the session (general edits only with the isolated executor).
  const runner=createScratchTestRunner({wt,base:head,chain:task.chain,executor});
  let last=null;
  try{
    const provider=createAgentCandidateProvider({task,brain,runTests:runner.runTests,evidenceDir:path.join(out,'evidence'),
      onStep:s=>log(`  [Reparatur] ${s.tool}${s.tool==='run_tests'?': '+String(s.observation).split('\n')[0]:''}`)});
    const wrapped=Object.assign(input=>(last=provider(input)),{taskSpec:provider.taskSpec,allowedPaths:provider.allowedPaths});
    const report=repairSession({wt,base:head,chain:task.chain,out:path.join(out,'report'),candidateProvider:wrapped,executor});
    return {status:report.outcome,verified:report.outcome==='VERIFIED'&&Boolean(report.verified),scope,
      brainKind:last?.agent?.brainKind??scope.brainKind,patches:last?.patches??[],
      attempts:(report.attempts??[]).map(a=>a.outcome),evidence:out};
  }finally{try{runner.close();}finally{removeWorktree(repo,wt);}}
}

/** Blocking line read from stdin (TTY or pipe); bytes are decoded at the end so umlauts survive. */
function readLineSync(){
  const one=Buffer.alloc(1),bytes=[];
  for(;;){
    let n;
    try{n=fs.readSync(0,one,0,1,null);}
    catch(e){if(e.code==='EAGAIN'){Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,50);continue;}if(e.code==='EOF')n=0;else throw e;}
    if(n===0)return bytes.length?Buffer.from(bytes).toString('utf8'):null;
    if(one[0]===10)return Buffer.from(bytes).toString('utf8').replace(/\r$/,'');
    bytes.push(one[0]);
  }
}

if(process.argv[1]===fileURLToPath(import.meta.url)){
  const argv=process.argv.slice(2),opt=n=>{const i=argv.indexOf(n);return i>=0?argv.splice(i,2)[1]:null;};
  const repo=path.resolve(opt('--repo')??'.'),script=opt('--script'),config=opt('--executor');
  const yes=argv.includes('--yes');const request=argv.filter(a=>a!=='--yes').join(' ').trim();
  const makeBrain=()=>script?createScriptedBrain(JSON.parse(fs.readFileSync(script,'utf8'))):createChatCompletionsBrain({
    endpoint:process.env.CONTINUUM_LOCAL_BRAIN_ENDPOINT||undefined,model:process.env.CONTINUUM_LOCAL_BRAIN_MODEL||'local',
    apiKey:process.env.CONTINUUM_LOCAL_BRAIN_API_KEY||null});
  const executor=config?createIsolatedExecutor(loadIsolationConfig(config,{sourceRoot:repo})):null;
  const answer=()=>{if(yes)return true;process.stdout.write('Diese Dateien zur Änderung freigeben? [j/N] ');return /^[jy]/i.test((readLineSync()??'').trim());};
  const show=result=>{
    console.log(`\nErgebnis: ${result.status} (${result.brainKind??result.scope?.brainKind})${result.attempts?' Versuche: '+result.attempts.join(', '):''}`);
    for(const p of result.patches??[])console.log(`\n--- ${p.file} (alt)\n${p.oldText}\n+++ ${p.file} (neu)\n${p.newText}`);
    if(result.verified)console.log('\nGeprüft durch die feste Testkette. Nichts wurde angewendet: Patch zur Freigabe.');
    else if(result.attempts?.includes('REJECTED_BY_EXECUTION_POLICY'))console.log('\nNicht ausgeführt: allgemeine Änderungen laufen nur im isolierten Executor (--executor).');
    if(result.evidence)console.log('Protokoll: '+result.evidence);
  };
  const once=text=>{try{show(runAgentCommand({repo,request:text,brain:makeBrain(),confirm:answer,executor,log:m=>console.log(m)}));}
    catch(e){console.log('Abgebrochen: '+(e.code??e.message));process.exitCode=1;}};
  if(request)once(request);
  else{
    console.log('Continuum Coding-Agent. Ein Auftrag pro Zeile; leere Zeile oder "ende" beendet.');
    for(;;){
      process.stdout.write('\nAuftrag> ');
      const line=readLineSync();
      if(line===null||!line.trim()||/^(ende|exit|quit)$/i.test(line.trim()))break;
      once(line.trim());
    }
  }
}
