import fs from 'node:fs';
import crypto from 'node:crypto';

/** Deterministic, no-LLM developer agent. Reads CI metadata, emits bounded triage.
 * It NEVER executes arbitrary log text, writes source files, or pushes commits.
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
  const branch='copilot/pr-12-working-first-vertical-slice';
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
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href)main().catch(e=>{console.error(e.message);process.exitCode=1;});
