#!/usr/bin/env node
// Evaluate the frozen independent 60-case fixture against exact Git objects.
// Never edits training data, branch refs, owner memory or existing reports.
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export const PINS=Object.freeze({
  baseline:'c391b83244bd2cc945a1b37bcdd829da27e15ba6',
  candidate:'4bf74b1b827aab56bbe03e032c21c03c508484c8',
  fixtureHash:'6a1a414786b22eec0573e25dd7f8aaf1972f5f2a5916181173fc612265672c12',
  fixture:'docs/evidence/sol-breakthroughs-20261026/grounding-independent-not-run/grounding-decision-fixtures-v1.json',
  module:'.github/continuum/dialogue-retrieval.mjs'
});
const unique=a=>[...new Set(a.map(String))].sort();
const same=(a,b)=>JSON.stringify(unique(a))===JSON.stringify(unique(b));

export function summarize(cases,rows) {
  if(cases.length!==rows.length)throw Error('Case count mismatch');
  const groups=new Map();
  for(let i=0;i<cases.length;i++) {
    const c=cases[i],r=rows[i];
    if(c.id!==r.caseId)throw Error('Case order mismatch: '+i);
    const expected=unique(c.expectedIds);
    const found=r.status==='PASS'?unique(r.returnedIds):[];
    for(const key of ['all','scope:'+c.scope,'kind:'+c.kind,'category:'+c.category]){
      if(!groups.has(key))groups.set(key,{cases:0,exact:0,errors:0,falseHits:0,missed:0,trueHits:0,expectedIds:0});
      const m=groups.get(key);m.cases++;
      if(r.status==='PASS'&&same(expected,found))m.exact++;
      if(r.status!=='PASS')m.errors++;
      m.falseHits+=found.filter(x=>!expected.includes(x)).length;
      m.missed+=expected.filter(x=>!found.includes(x)).length;
      m.trueHits+=found.filter(x=>expected.includes(x)).length;
      m.expectedIds+=expected.length;
    }
  }
  return Object.fromEntries([...groups].sort(([a],[b])=>a.localeCompare(b)));
}
function gitFile(cwd,sha){
  return execFileSync('git',['show',sha+':'+PINS.module],{
    cwd,timeout:15000,maxBuffer:1048576
  }).toString('utf8');
}
async function load(cwd,sha){
  const code=gitFile(cwd,sha);
  if(/^\s*import\s/mu.test(code))throw Error('Unexpected import in pinned retrieval source');
  const module=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
  if(typeof module.rankMemories!=='function'||typeof module.sourceTermEvidence!=='function')
    throw Error('Retrieval exports missing at '+sha);
  return module;
}
function evaluateCase(c,m){
  try {
    const hits=m.rankMemories(c.query,c.memories,{semantic:null,limit:c.memories.length});
    if(!Array.isArray(hits))throw Error('rankMemories must return an array');
    const sourceTermEvidence=c.memories.map(x=>({
      id:x.id,expected:c.expectedSourceTermEvidenceById?.[x.id]??null,
      actual:Boolean(m.sourceTermEvidence(c.query,x.text))
    }));
    return {caseId:c.id,status:'PASS',returnedIds:unique(hits.map(x=>x.id)),
      rawHits:hits,sourceTermEvidence};
  }catch(e){
    return {caseId:c.id,status:'ERROR',returnedIds:null,rawHits:null,sourceTermEvidence:null,
      error:{name:String(e?.name??'Error'),message:String(e?.message??e).slice(0,1000)}};
  }
}
export async function execute(cwd,fixtureRaw){
  const checksum=createHash('sha256').update(Buffer.from(fixtureRaw,'utf8')).digest('hex');
  if(checksum!==PINS.fixtureHash)throw Error('Frozen fixture SHA-256 mismatch: '+checksum);
  const fixture=JSON.parse(fixtureRaw);
  if(fixture.cases?.length!==60||new Set(fixture.cases.map(x=>x.id)).size!==60)
    throw Error('Frozen 60-case fixture changed');
  const baseline=await load(cwd,PINS.baseline),candidate=await load(cwd,PINS.candidate);
  const records=fixture.cases.map(c=>({id:c.id,scope:c.scope,category:c.category,
    kind:c.kind,polarity:c.polarity,expectedIds:unique(c.expectedIds),
    baseline:evaluateCase(c,baseline),candidate:evaluateCase(c,candidate)}));
  const before=summarize(fixture.cases,records.map(x=>x.baseline));
  const after=summarize(fixture.cases,records.map(x=>x.candidate));
  const retained=records.reduce((n,r)=>
    n+(r.baseline.returnedIds??[]).filter(id=>
      r.expectedIds.includes(id)&&(r.candidate.returnedIds??[]).includes(id)).length,0);
  return {schema:'CONTINUUM_INDEPENDENT_GROUNDING_EVAL_V1',status:'EXECUTED',
    source:{...PINS,fixtureSha256:checksum,fixtureId:fixture.fixtureId},
    metrics:{baseline:before,candidate:after,retainedBaselineTruePositives:retained,
      baselineTruePositives:before.all.trueHits},records};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{
    const args=process.argv.slice(2);
    if(args.length!==2||args[0]!=='--output')
      throw Error('Usage: node scripts/continuum-grounding-independent-eval.mjs --output /new/report.json');
    const cwd=execFileSync('git',['rev-parse','--show-toplevel'],{encoding:'utf8'}).trim();
    const raw=readFileSync(resolve(cwd,PINS.fixture),'utf8');
    const report=await execute(cwd,raw);
    const output=resolve(args[1]);
    writeFileSync(output,JSON.stringify(report,null,2)+'\n',{encoding:'utf8',flag:'wx',mode:0o600});
    const b=report.metrics.baseline['scope:in_scope'],a=report.metrics.candidate['scope:in_scope'];
    console.log('INDEPENDENT_GROUNDING_60: baseline '+b.exact+'/'+b.cases+
      ', candidate '+a.exact+'/'+a.cases+', false hits '+b.falseHits+' -> '+a.falseHits+
      ', errors '+b.errors+' -> '+a.errors);
    console.log('RAW_REPORT: '+output);
    if(report.metrics.candidate.all.errors)process.exitCode=1;
  }catch(e){
    console.error('INDEPENDENT_GROUNDING_EVAL_ERROR: '+e.message);process.exitCode=1;
  }
}
