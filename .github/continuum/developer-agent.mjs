import fs from 'node:fs';
import path from 'node:path';

/** Deterministic, no-LLM developer agent.
 * `triage` reads CI metadata only. The repair commands reproduce failures with
 * fixed check profiles, never execute log text, change sources only inside an
 * isolated git worktree and publish only to continuum-agent/* branches as
 * draft pull requests. They never push to development or default branches.
 */
export function triageRun(run, jobs=[]) {
  const id=Number(run.id);
  if(!Number.isSafeInteger(id)||id<=0)throw Error('Invalid run ID');
  const failed=jobs.flatMap(job=>(job.steps??[])
    .filter(step=>step.conclusion==='failure')
    .map(step=>({job:String(job.name??'').slice(0,80),step:String(step.name??'').slice(0,100)})));
  const conclusion=String(run.conclusion??'pending');
  const category=failed.some(x=>/test/i.test(x.step))?'TEST_FAILURE':
    failed.some(x=>/build|compile|gradle/i.test(x.step))?'BUILD_FAILURE':
    conclusion==='failure'?'WORKFLOW_FAILURE':conclusion==='success'?'PASS':'PENDING';
  return {runId:id,headSha:String(run.head_sha??'').slice(0,40),
    workflow:String(run.name??'').slice(0,120),conclusion,category,
    failedSteps:failed.slice(0,12),url:String(run.html_url??'').slice(0,300)};
}
export function summarize(runs,jobsByRun={}) {
  const items=runs.slice(0,30).map(r=>triageRun(r,jobsByRun[String(r.id)]??[]));
  return {schemaVersion:1,createdAt:new Date().toISOString(),
    failures:items.filter(x=>x.category.endsWith('FAILURE')),
    successes:items.filter(x=>x.category==='PASS').length,
    pending:items.filter(x=>x.category==='PENDING').length};
}
async function main() {
  const token=process.env.GITHUB_TOKEN;
  const repository=process.env.GITHUB_REPOSITORY;
  if(!token||!repository||!/^[-\w.]+\/[-\w.]+$/.test(repository))throw Error('GitHub environment required');
  const headers={Authorization:'Bearer '+token,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28'};
  async function api(path) {
    const response=await fetch('https://api.github.com/repos/'+repository+path,{headers,signal:AbortSignal.timeout(12000)});
    if(!response.ok)throw Error('GitHub API HTTP '+response.status);
    return response.json();
  }
  const branch=process.env.CONTINUUM_BRANCH||'copilot/pr-12-working-first-vertical-slice';
  const data=await api('/actions/runs?branch='+encodeURIComponent(branch)+'&per_page=12');
  const runs=(data.workflow_runs??[]).filter(x=>x.name!=='Continuum Developer Agent').slice(0,12);
  const jobs={};
  for(const run of runs.filter(x=>x.conclusion==='failure').slice(0,5)){
    jobs[String(run.id)]=(await api('/actions/runs/'+run.id+'/jobs?per_page=30')).jobs??[];
  }
  const report=summarize(runs,jobs);
  fs.mkdirSync('developer-agent-report',{recursive:true});
  fs.writeFileSync('developer-agent-report/triage.json',JSON.stringify(report,null,2)+'\n');
  const lines=['# Continuum Developer Agent','',`Analyzed ${runs.length} CI runs; ${report.failures.length} failures, ${report.successes} successes, ${report.pending} pending.`,''];
  for(const item of report.failures)lines.push(`- [${item.category}] ${item.workflow} run ${item.runId}: ${item.failedSteps.map(x=>x.step).join(', ')||'Unknown step'} (${item.url})`);
  if(!report.failures.length)lines.push('No failed runs found in this window.');
  fs.writeFileSync('developer-agent-report/summary.md',lines.join('\n')+'\n');
  console.log(lines.join('\n'));
}


// ---------------------------------------------------------------------------
// Repair commands. `triage` (above) only reads CI metadata; the commands below
// drive the verified repair loop. Build execution and publishing are separate
// commands so CI can run them in jobs with different token permissions.
//   repair     --chain android|node|jvm --base <sha> --out <dir> [--trigger t]
//   benchmark  --chains node=node,kotlin=android --out <dir> [--faults a,b]
//   publish    --out <dir> --target-branch <branch>            (write token)
//   publish-demo --out <dir> --fault <id> --run <id>           (write token)
//   ledger-save --out <dir> [--branch continuum-agent-state]   (write token)
//   investigate --file <backlog.json> --base <sha> --out <report.json> (READY INVESTIGATE only)
//   backlog <command> --file <backlog.json> ...  (see backlog-cli.mjs)
//   backlog-save [--plan] [--repairs <dir,...>] [--branch continuum-agent-state] (write token)
//   watch --branch <dev branch> [--state-branch continuum-agent-state]
// ---------------------------------------------------------------------------
function options(argv){
  const o={_:[]};
  for(let i=0;i<argv.length;i++){
    if(argv[i].startsWith('--')){const k=argv[i].slice(2);const v=argv[i+1]&&!argv[i+1].startsWith('--')?argv[++i]:'true';o[k]=v;}
    else o._.push(argv[i]);
  }
  return o;
}
const SAFE_OUT=/^[\w./-]{1,200}$/;
// Relative to the invoking checkout, but returned absolute: check commands run
// with the isolated worktree as cwd and must not resolve report paths there.
function outDir(o){if(!SAFE_OUT.test(o.out??'')||o.out.includes('..'))throw Error('--out must be a relative directory');
  const dir=path.resolve(o.out);fs.mkdirSync(dir,{recursive:true});return dir;}

export async function command(argv,{root=process.cwd(),env=process.env,log=console.log}={}){
  const o=options(argv);const cmd=o._[0]??'triage';
  const loop=await import('./repair-loop.mjs');
  const ledgerMod=await import('./repair-ledger.mjs');
  const publish=await import('./repair-publish.mjs');
  const ledgerFile=o.ledger;
  const ledger=ledgerFile?ledgerMod.loadLedger(ledgerFile):(o['ledger-branch']?publish.loadLedgerFromBranch(root,o['ledger-branch']).ledger:ledgerMod.emptyLedger());
  if(cmd==='triage')return main();
  if(cmd==='investigate'){
    const {executeInvestigation}=await import('./backlog-investigator.mjs');
    if(!o.file||!o.out||!/^[0-9a-f]{40}$/.test(o.base??''))
      throw Error('investigate requires --file, --out and immutable --base SHA');
    const result=executeInvestigation({root,file:o.file,out:o.out,base:o.base,
      actor:env.CONTINUUM_ACTOR??'continuum-agent'});
    log('[investigate] '+JSON.stringify(result));
    return result;
  }
  if(cmd==='backlog'){
    const {backlogCommand}=await import('./backlog-cli.mjs');
    return backlogCommand(o._[1],o,{root,log});
  }
  if(cmd==='backlog-save'){
    // Persist the shared development backlog on the state branch: recover expired
    // claims, record repair sessions, sync analyzer findings. Every rejected
    // operation is reported; the push is a compare-and-swap on the branch head.
    const B=await import('./development-backlog.mjs');
    const planner=await import('./development-planner.mjs');
    const reports=[];
    for(const dir of String(o.repairs??'').split(',').filter(Boolean)){
      if(!SAFE_OUT.test(dir)||dir.includes('..'))throw Error('--repairs entries must be relative directories');
      const file=path.join(path.resolve(dir),'report.json');
      if(fs.existsSync(file))reports.push(JSON.parse(fs.readFileSync(file,'utf8')));
    }
    let analysis=null;
    if(o.plan==='true'){const {analyzeRepository}=await import('./code-analyzer.mjs');const {index,...report}=analyzeRepository(root);analysis=report;}
    const result=publish.saveBacklogToBranch(root,o.branch??publish.STATE_BRANCH,doc=>{
      const at=Date.now(),results=[],skipped=[];
      let current=doc;
      const run=(ops,source)=>{const out=B.applyOps(current,ops,{stopOnError:false});current=out.doc;results.push(...out.results.map(r=>({...r,source})));};
      run([{op:'expire',actor:'continuum-agent',at}],'expire');
      for(const report of reports){
        const {id,ops,skipped:why}=planner.repairSessionOps(current,report);
        if(why)skipped.push(`${id??report.base.slice(0,12)}: ${why}`);
        run(ops,'repair '+id);
      }
      if(analysis)run(planner.syncBacklogOps(current,analysis,{at,limit:Number(o.limit??12)}),'plan');
      return {doc:current,results,skipped};
    });
    const rejected=result.results.filter(r=>!r.ok).map(r=>`${r.source} op ${r.index}: ${r.code}`);
    log('[backlog-save] '+JSON.stringify({status:result.status,revision:result.revision,applied:result.results.filter(r=>r.ok&&!r.duplicate).length,
      duplicates:result.results.filter(r=>r.duplicate).length,rejected,skipped:result.skipped}));
    return result;
  }
  if(cmd==='owner-repair'){
    // No GitHub token or cloud service: only explicitly accepted REPAIR tasks
    // in the canonical local backlog may drive this bounded worker.
    if(!o.file||!o.out)throw Error('owner-repair requires --file and --out');
    const {executeOwnerRepairTask}=await import('./backlog-repair-worker.mjs');
    const result=executeOwnerRepairTask({root,file:o.file,out:o.out,id:o.id??null,
      budgetMinutes:Number(o.minutes??20),
      actor:env.CONTINUUM_ACTOR??'continuum-task-worker'});
    log('[owner-repair] '+JSON.stringify(result));
    if(result.status==='FAILED')throw Error('Owner repair task failed: '+result.reason);
    return result;
  }
  if(cmd==='repair'){
    const out=outDir(o);
    if(!/^[0-9a-f]{40}$/.test(o.base??''))throw Error('--base must be a full commit hash');
    if(!loop.CHAINS[o.chain])throw Error('Unknown --chain');
    const wt=loop.createWorktree(root,o.base);
    let report;
    try{report=loop.repairSession({wt,base:o.base,chain:o.chain,out,ledger,log:m=>log('[repair] '+m),
      deadline:Date.now()+Number(o['minutes']??30)*60000});}
    finally{loop.removeWorktree(root,wt);}
    report.trigger=o.trigger??null;
    fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,1)+'\n');
    if(ledgerFile)ledgerMod.saveLedger(ledgerFile,ledgerMod.recordSession(ledger,report,{trigger:o.trigger}));
    log(`[repair] outcome ${report.outcome}${report.verified?' via '+report.verified.strategy:''}`);
    return report;
  }
  if(cmd==='benchmark'){
    const out=outDir(o);
    const {runBenchmark,FAULTS}=await import('./repair-benchmark.mjs');
    const chains=Object.fromEntries(String(o.chains??'node=node').split(',').map(x=>x.split('=')));
    for(const [family,chain] of Object.entries(chains))if(!['node','kotlin'].includes(family)||!loop.CHAINS[chain])throw Error('Invalid --chains entry '+family+'='+chain);
    const wanted=o.faults?new Set(o.faults.split(',')):null;
    const head=loop.git(root,['rev-parse','HEAD']).stdout.trim();
    let current=ledger;
    const result=runBenchmark({root,head,chains,out,ledger:current,faults:FAULTS.filter(f=>!wanted||wanted.has(f.id)),
      onSession:report=>{current=ledgerMod.recordSession(current,report,{trigger:'benchmark'});return current;},log:m=>log('[benchmark] '+m)});
    const {ledger:_,...summary}=result;
    fs.writeFileSync(path.join(out,'benchmark.json'),JSON.stringify(summary,null,1)+'\n');
    if(ledgerFile)ledgerMod.saveLedger(ledgerFile,current);
    const lines=['# Continuum repair benchmark','',`Head \`${head}\` · repaired **${summary.repaired}/${summary.attempted}** injected faults`,'',
      '| Fault | Outcome | Strategy | Expected | Attempts | Exact restore | Seconds |','|---|---|---|---|---|---|---|',
      ...summary.results.map(r=>`| ${r.fault} | ${r.outcome} | ${r.strategy??'—'} | ${r.expectedStrategy??'any'} | ${r.attempts??0} | ${r.restoredExactly?'yes':'no'} | ${Math.round((r.ms??0)/1000)} |`)];
    fs.writeFileSync(path.join(out,'benchmark.md'),lines.join('\n')+'\n');
    log(lines.join('\n'));
    return summary;
  }
  const token=env.GITHUB_TOKEN,repository=env.GITHUB_REPOSITORY;
  if(cmd==='publish'){
    const out=outDir(o);
    if(!fs.existsSync(path.join(out,'report.json'))){log('[publish] no report');return null;}
    const report=JSON.parse(fs.readFileSync(path.join(out,'report.json'),'utf8'));
    if(report.outcome!=='VERIFIED'){log('[publish] nothing to publish: '+report.outcome);return null;}
    // No duplicate proposals: the backlog knows whether this failure was already
    // handled (DONE), dismissed (REJECTED/ABORTED) or ran out of budget (FAILED).
    const {repairTaskId}=await import('./development-planner.mjs');
    const task=publish.loadBacklogFromBranch(root).doc?.tasks.find(t=>t.id===repairTaskId(report));
    if(task&&['DONE','REJECTED','ABORTED','FAILED'].includes(task.state)){
      log(`[publish] skipped: backlog task ${task.id} is ${task.state}`+(task.state==='FAILED'?' (attempt budget exhausted; `backlog retry` allows new attempts)':''));
      fs.writeFileSync(path.join(out,'publish.json'),JSON.stringify({skipped:true,task:task.id,state:task.state},null,1)+'\n');
      return null;
    }
    const result=await publish.publishRepair({root,outDir:out,targetBranch:o['target-branch'],repository,token,push:o['dry-run']!=='true'});
    fs.writeFileSync(path.join(out,'publish.json'),JSON.stringify(result,null,1)+'\n');
    log('[publish] '+JSON.stringify(result));
    return result;
  }
  if(cmd==='publish-demo'){
    const out=outDir(o);
    const dir=path.join(out,String(o.fault??'').replace(/[^\w.-]/g,''));
    // the fault patch applies to the exact commit the benchmark ran on
    const head=JSON.parse(fs.readFileSync(path.join(out,'benchmark.json'),'utf8')).head;
    if(!/^[0-9a-f]{40}$/.test(head??''))throw Error('benchmark.json has no head commit');
    loop.git(root,['cat-file','-e',head+'^{commit}'],{allowFailure:true}).status===0||loop.git(root,['fetch','--quiet','--depth=1','origin',head]);
    const result=await publish.publishBenchmarkDemo({root,head,faultDir:dir,runId:o.run??'local',repository,token,push:o['dry-run']!=='true'});
    fs.writeFileSync(path.join(out,'publish-demo.json'),JSON.stringify(result,null,1)+'\n');
    log('[publish-demo] '+JSON.stringify(result));
    return result;
  }
  if(cmd==='ledger-save'){
    const out=outDir(o);
    const reports=[];
    for(const dirent of fs.readdirSync(out,{withFileTypes:true})){
      const file=dirent.isDirectory()?path.join(out,dirent.name,'report.json'):dirent.name==='report.json'?path.join(out,'report.json'):null;
      if(file&&fs.existsSync(file)){const report=JSON.parse(fs.readFileSync(file,'utf8'));
        const pub=path.join(path.dirname(file),'publish.json');
        reports.push({report,meta:{trigger:report.trigger??(dirent.isDirectory()?'benchmark':null),
          ...(fs.existsSync(pub)?(({branch,commit,pullRequest})=>({branch,proposalCommit:commit,pullRequest:pullRequest?.url??pullRequest?.compare??null}))(JSON.parse(fs.readFileSync(pub,'utf8'))):{})}});}
    }
    const result=publish.saveLedgerToBranch(root,o.branch??'continuum-agent-state',reports);
    log('[ledger] '+JSON.stringify(result));
    return result;
  }
  if(cmd==='watch'){
    // Cheap scheduled check: API calls only. Requests a repair job only when the
    // development head has a failed Android build that neither the ledger nor
    // the backlog on the state branch knows. State is read through the API
    // (this job has no git credentials); unreadable state fails the job instead
    // of looking empty, which would repeat repairs every hour.
    const branch=o.branch??env.CONTINUUM_BRANCH??'copilot/pr-12-working-first-vertical-slice';
    const stateBranch=o['state-branch']??publish.STATE_BRANCH;
    if(!/^[\w./-]{1,120}$/.test(branch)||!/^[\w.-]{1,80}$/.test(stateBranch))throw Error('Invalid --branch or --state-branch');
    const headers={Authorization:'Bearer '+token,'X-GitHub-Api-Version':'2022-11-28'};
    const api=async p=>{const r=await fetch('https://api.github.com/repos/'+repository+p,{signal:AbortSignal.timeout(12000),
      headers:{...headers,Accept:'application/vnd.github+json'}});
      if(!r.ok)throw Error('GitHub API HTTP '+r.status);return r.json();};
    const stateFile=async name=>{const r=await fetch(`https://api.github.com/repos/${repository}/contents/${name}?ref=${encodeURIComponent(stateBranch)}`,
      {signal:AbortSignal.timeout(12000),headers:{...headers,Accept:'application/vnd.github.raw+json'}});
      if(r.status===404)return null;
      if(!r.ok)throw Error('GitHub API HTTP '+r.status+' reading '+name);
      return Buffer.from(await r.arrayBuffer());};
    const ledgerBytes=await stateFile('repair-ledger.json');
    const state=ledgerBytes?ledgerMod.verifyLedger(JSON.parse(ledgerBytes.toString('utf8'))):ledgerMod.emptyLedger();
    const backlogBytes=await stateFile(publish.BACKLOG_FILE);
    const B=await import('./development-backlog.mjs');
    const backlog=backlogBytes?B.decodeDocument(backlogBytes,{migrate:{at:Date.now(),actor:'continuum-agent'}}).doc:null;
    const head=(await api('/branches/'+encodeURIComponent(branch))).commit.sha;
    const runs=(await api('/actions/runs?head_sha='+head+'&per_page=30')).workflow_runs??[];
    const build=runs.find(r=>r.path==='.github/workflows/android.yml');
    const repairs=(backlog?.tasks??[]).filter(t=>t.kind==='REPAIR');
    const known=repairs.find(t=>t.subject?.base===head)??null;
    const running=repairs.find(t=>t.state==='IN_PROGRESS'&&t.claim.leaseUntil>Date.now())??null;
    const latestRetry=known?.history?.filter(h=>h.op==='retry').at(-1)?.at??null;
        const retriedSessionRecorded=latestRetry!==null&&state.sessions.some(x=>
          x.base===head&&Number.isFinite(Date.parse(x.at))&&Date.parse(x.at)>=latestRetry);
        const explicitlyRetried=Boolean(known&&known.state==='READY'&&known.attempts===0&&
          known.history?.at(-1)?.op==='retry'&&!retriedSessionRecorded);
    const attempted=!explicitlyRetried&&(state.sessions.some(x=>x.base===head)||Boolean(known));
    const decision={branch,head,buildConclusion:build?.conclusion??build?.status??'NOT_RUN',alreadyAttempted:attempted,
      backlogTask:known?.id??null,backlogTaskState:known?.state??null,runningRepair:running?.id??null,
      needsRepair:build?.conclusion==='failure'&&!attempted&&!running};
    log('[watch] '+JSON.stringify(decision));
    if(env.GITHUB_OUTPUT)fs.appendFileSync(env.GITHUB_OUTPUT,`needs_repair=${decision.needsRepair}\nbase=${head}\nbranch=${branch}\n`);
    return decision;
  }
  throw Error('Unknown command '+cmd);
}

if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href)
  command(process.argv.slice(2)).catch(e=>{console.error(e.message);process.exitCode=1;});
