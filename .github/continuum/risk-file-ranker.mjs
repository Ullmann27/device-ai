/** Deterministic defensive code triage for the local Continuum coding agent.
 * Scores do not assert vulnerabilities; no source, credentials, or personal data are returned.
 * The caller must also enforce the allowlisted Git commit and readable-file policy.
 */
import path from 'node:path';

const RULES=Object.freeze([
 {id:'C_UNBOUNDED_COPY',severity:80,matcher:/\b(?:strcpy|strcat|gets)\s*\(/},
 {id:'C_UNBOUNDED_FORMAT',severity:70,matcher:/\b(?:sprintf|vsprintf)\s*\(/},
 {id:'DYNAMIC_EVAL',severity:60,matcher:/\b(?:eval|new\s+Function)\s*\(/},
 {id:'SHELL_PROCESS_EXECUTION',severity:65,matcher:/\b(?:Runtime\.getRuntime\s*\(\s*\)\.exec|subprocess\.(?:run|Popen|call)|child_process\.(?:exec|execSync))\s*\(/},
 {id:'UNSAFE_SHELL_MODE',severity:80,matcher:/\bshell\s*=\s*True\b/},
 {id:'SQL_STRING_COMPOSITION',severity:45,matcher:/\b(?:execute|rawQuery|query)\s*\([^\n]{0,160}(?:\+|\$\{|\.format\s*\()/},
 {id:'TLS_VERIFICATION_DISABLED',severity:80,matcher:/\b(?:verify\s*=\s*False|rejectUnauthorized\s*:\s*false|checkServerTrusted\s*\()/}
]);
const QUALIFIERS=Object.freeze([
 {name:'trust-boundary',re:/(?:^|\/)(?:auth|authentication|authorization|permission|secur(?:ity|e)|oauth|identity)(?:\/|\.|-|$)/i,weight:50},
 {name:'cryptography',re:/(?:^|\/)(?:crypto|cryptography|signature|signing|keystore|certificates?)(?:\/|\.|-|$)/i,weight:45},
 {name:'network-entry',re:/(?:^|\/)(?:http|network|server|socket|websocket|api|endpoint|router|gateway)(?:\/|\.|-|$)/i,weight:45},
 {name:'release-update',re:/(?:^|\/)(?:release|updater?|installer|manifest|download)(?:\/|\.|-|$)/i,weight:40},
 {name:'execution-boundary',re:/(?:^|\/)(?:sandbox|executor|process|shell|worker|agent|mcp)(?:\/|\.|-|$)/i,weight:40},
 {name:'state-integrity',re:/(?:^|\/)(?:state|memory|ewm|checkpoint|journal|persistence)(?:\/|\.|-|$)/i,weight:30}
]);
const PRIVATE=/(?:^|\/)(?:\.git|node_modules|secrets?|owner-data|credentials?|keystores?|private)(?:\/|$)|(?:^|\/)(?:\.env(?:\.|$)|local\.properties$)|\.(?:p12|jks|pem|key)$/i;
const TEXT_EXT=/\.(?:c|cc|cpp|h|hpp|mjs|cjs|js|ts|kt|kts|java|py|go|rs|sh|json|xml|ya?ml|gradle)$/i;
export function readableAuditPath(file){
 return typeof file==='string'&&file.length>0&&file.length<=240&&
 !file.startsWith('/')&&!file.includes('\\')&&!/[\x00-\x1f\x7f]/.test(file)&&
 file.split('/').every(p=>p!=='.'&&p!=='..'&&p!=='')&&
 TEXT_EXT.test(file)&&!PRIVATE.test(file);
}
function tokens(value){
 return [...new Set(String(value??'').toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)??[])].slice(0,40);
}
export function rankFilesByRisk(files,{goal='',maxResults=20}={}){
 if(!Array.isArray(files)||!Number.isSafeInteger(maxResults)||maxResults<1||maxResults>100)
 throw Error('INVALID_RISK_RANK_REQUEST');
 const terms=tokens(goal);
 const ranked=[...new Set(files)].filter(readableAuditPath).map(file=>{
  const reasons=[];let score=0;
  for(const rule of QUALIFIERS)if(rule.re.test(file)){score+=rule.weight;reasons.push(rule.name);}
  const parts=tokens(file.replace(/([a-z])([A-Z])/g,'$1 $2'));
  const matches=terms.filter(term=>parts.some(part=>part===term||part.startsWith(term)||term.startsWith(part)));
  if(matches.length){score+=Math.min(60,20*matches.length);reasons.push('goal-match');}
  if(/(?:^|\/)(?:test|tests|fixtures|examples)(?:\/|\.)|\.test\./i.test(file)){
   score=Math.max(0,score-15);reasons.push('test-or-fixture');
  }
  return {path:file,score:Math.min(200,score),reasons};
 });
 return ranked.sort((a,b)=>b.score-a.score||a.path.localeCompare(b.path,'en')).slice(0,maxResults);
}
export function scanDefensiveSinks(file,source,{maxFindings=24,maxBytes=64*1024}={}){
 if(!readableAuditPath(file)||typeof source!=='string'||
 !Number.isSafeInteger(maxFindings)||maxFindings<1||maxFindings>100||
 !Number.isSafeInteger(maxBytes)||maxBytes<256||maxBytes>262144)
 throw Error('INVALID_SINK_SCAN_REQUEST');
 const bytes=Buffer.byteLength(source,'utf8');
 if(bytes>maxBytes)return {path:file,status:'TOO_LARGE',findings:[]};
 const findings=[];
 for(const [index,line] of source.split('\n').entries()){
  if(line.length>1200)continue;
  for(const rule of RULES){
   if(!rule.matcher.test(line))continue;
   findings.push({line:index+1,rule:rule.id,severity:rule.severity});
   if(findings.length===maxFindings)return {path:file,status:'TRUNCATED',findings};
  }
 }
 return {path:file,status:'REVIEW_CANDIDATES_ONLY',findings};
}
