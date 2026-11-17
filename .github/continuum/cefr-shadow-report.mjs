/** Read-only offline report over actual local Teamleader shadow receipts.
 * Never approves model promotion, merging or any worktree mutation.
 * A JSON audit can be altered by anyone able to write the state directory;
 * integrity is NOT authentication. The worker must be isolated by OS policy.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {auditCefrShadowWinner,summarizeCefrShadowAudits} from './cefr-shadow-benchmark.mjs';

const DIRECTORY=/^repair-[a-f0-9]{12}$/;
const MAX_REPORT_BYTES=2*1024*1024;
function ordinaryDir(folder){
 const stat=fs.lstatSync(folder);
 if(!stat.isDirectory()||stat.isSymbolicLink())
  throw Error('UNSAFE_CEFR_REPORT_DIRECTORY');
 return folder;
}
function readReport(file){
 const st=fs.lstatSync(file);
 if(!st.isFile()||st.isSymbolicLink()||
    st.size<2||st.size>MAX_REPORT_BYTES)
  throw Error('INVALID_CEFR_REPORT_FILE');
 const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
 try{
  const actual=fs.fstatSync(fd);
  if(!actual.isFile()||actual.dev!==st.dev||actual.ino!==st.ino||
     actual.size!==st.size)
   throw Error('CEFR_REPORT_RACE');
  const buf=fs.readFileSync(fd);
  if(buf.length!==actual.size)throw Error('CEFR_REPORT_TRUNCATED');
  return JSON.parse(buf.toString('utf8'));
 }finally{fs.closeSync(fd);}
}

/** There are no network requests; invalid receipts are fatal, not omitted. */
export function collectCefrShadowReports({stateDir,minimum=20}={}){
 if(typeof stateDir!=='string'||!path.isAbsolute(stateDir))
  throw Error('ABSOLUTE_CEFR_STATE_DIR_REQUIRED');
 ordinaryDir(stateDir);
 const reports=ordinaryDir(path.join(fs.realpathSync(stateDir),'reports'));
 const entries=fs.readdirSync(reports).filter(name=>DIRECTORY.test(name)).sort();
 if(entries.length>500)throw Error('CEFR_REPORT_LIMIT_EXCEEDED');
 const byModel=new Map();
 for(const name of entries){
  const folder=ordinaryDir(path.join(reports,name));
  const file=path.join(folder,'report.json');
  let exists;
  try{fs.lstatSync(file);exists=true;}
  catch(e){if(e?.code==='ENOENT')exists=false;else throw e;}
  if(!exists)continue;
  const report=readReport(file);
  if(!Object.hasOwn(report,'cefrShadowAudit'))continue;
  const recomputed=auditCefrShadowWinner({report,independent:report.independent});
  if(recomputed.status!=='RANKING_PROXY_ONLY'||
     JSON.stringify(recomputed)!==JSON.stringify(report.cefrShadowAudit))
   throw Error('INVALID_OR_STALE_CEFR_SHADOW_AUDIT: '+name);
  const cohort=byModel.get(recomputed.modelSha256)??[];
  cohort.push(recomputed);
  byModel.set(recomputed.modelSha256,cohort);
 }
 return {schema:'continuum-cefr-offline-shadow-report',version:1,
  advisoryOnly:true,source:'LOCAL_UNAUTHENTICATED_WORKER_RECEIPTS',
  totalReports:entries.length,totalAudits:[...byModel.values()].reduce((n,v)=>n+v.length,0),
  cohorts:[...byModel.entries()].sort(([a],[b])=>a.localeCompare(b))
   .map(([modelSha256,audits])=>summarizeCefrShadowAudits(audits,{minimum}))};
}
export function parseCefrShadowReportArgs(argv){
 if(!Array.isArray(argv)||argv.length!==2||argv[0]!=='--state-dir'||
    !path.isAbsolute(argv[1]))
  throw Error('Usage: node cefr-shadow-report.mjs --state-dir /absolute/offline/worker-state');
 return {stateDir:argv[1]};
}

if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  const result=collectCefrShadowReports(parseCefrShadowReportArgs(process.argv.slice(2)));
  process.stdout.write(JSON.stringify(result,null,2)+'\n');
 }catch(error){
  process.stderr.write('CEFR_SHADOW_REPORT_FAILED: '+String(error.message)+'\n');
  process.exitCode=1;
 }
}
