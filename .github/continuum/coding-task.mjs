/** Owner-provided task data. A task never grants shell, merge, signing or install authority. */
import {canonical,parseStrict,sha256Hex} from './development-backlog.mjs';
import {critique} from './repair-critic.mjs';

const KINDS=new Set(['BUGFIX','FEATURE','REFACTOR','TEST','COMPILER','DEPENDENCY']);
const exact=(v,keys)=>v&&typeof v==='object'&&!Array.isArray(v)&&Object.keys(v).sort().join(',')===[...keys].sort().join(',');
const text=(v,max)=>typeof v==='string'&&v.trim().length>0&&v.length<=max&&!/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(v);
const reject=()=>{throw Error('CODING_TASK_INVALID');};
// Keep the autonomous repair scope bounded: Gradle scripts, manifests, tests,
// general assets and control-plane files remain forbidden.
const LEGACY_SOURCE=/^(?:lib\/|src\/|backend\/src\/|app\/src\/main\/java\/|\.github\/continuum\/)[A-Za-z0-9_./-]+\.(?:mjs|js|kt)$/;
const ANDROID_JAVA=/^app\/src\/main\/java\/[A-Za-z0-9_./-]+\.java$/;
const ANDROID_LAYOUT=/^app\/src\/main\/res\/layout\/[A-Za-z0-9_./-]+\.xml$/;
const androidCandidate=file=>file.endsWith('.kt')||ANDROID_JAVA.test(file)||ANDROID_LAYOUT.test(file);
export function isCodingPath(file){
 return typeof file==='string'&&file.length<=180&&
  (LEGACY_SOURCE.test(file)||ANDROID_JAVA.test(file)||ANDROID_LAYOUT.test(file))&&
  !file.split('/').some(p=>!p||p==='.'||p==='..')&&
  !/(^|\/)(?:node_modules|build|dist|secrets?|credentials?|private|owner-data)(\/|\.)/i.test(file)&&
  !/^\.github\/continuum\/(?:harness-coding-|storage-fabric|coding-|ccc1-|isolated-|local-brain|local-numeric|step-watchdog|behaviour-probe|contract-|teamleader-|owner-|backlog-|development-|autonomous-)/.test(file)&&
  critique({strategy:'LOCAL_SCOPE_CHECK',edits:[{file,oldText:'a',newText:'b'}]}).approved;
}
export function validateCodingTask(input){
 if(!exact(input,['schema','version','id','sourceCommit','kind','goal','allowedPaths','acceptance','chain','limits'])||
  input.schema!=='continuum-coding-task'||input.version!==1||!/^[a-z][a-z0-9-]{2,63}$/.test(input.id??'')||
  !/^[a-f0-9]{40}$/.test(input.sourceCommit??'')||!KINDS.has(input.kind)||!text(input.goal,600)||
  !Array.isArray(input.allowedPaths)||input.allowedPaths.length<1||input.allowedPaths.length>3||
  new Set(input.allowedPaths).size!==input.allowedPaths.length||input.allowedPaths.some(f=>!isCodingPath(f))||
  !Array.isArray(input.acceptance)||input.acceptance.length<1||input.acceptance.length>8||input.acceptance.some(v=>!text(v,300))||
  !['node','android'].includes(input.chain)||input.allowedPaths.some(f=>androidCandidate(f)?input.chain!=='android':input.chain!=='node')||
  !exact(input.limits,['maxModelCalls','maxContextChars'])||
  !Number.isSafeInteger(input.limits.maxModelCalls)||input.limits.maxModelCalls<1||input.limits.maxModelCalls>3||
  !Number.isSafeInteger(input.limits.maxContextChars)||input.limits.maxContextChars<2048||input.limits.maxContextChars>24000)reject();
 const task=parseStrict(canonical(input));
 Object.freeze(task.allowedPaths);Object.freeze(task.acceptance);Object.freeze(task.limits);return Object.freeze(task);
}
export const taskFingerprint=task=>sha256Hex(canonical(validateCodingTask(task)));

export function validateCodingProposal(input,task){
 const spec=validateCodingTask(task);
 if(!exact(input,['plan','hypothesis','patches'])||!Array.isArray(input.plan)||input.plan.length<1||input.plan.length>8||
  input.plan.some(s=>!text(s,300))||!text(input.hypothesis,600)||!Array.isArray(input.patches)||
  input.patches.length<1||input.patches.length>3||new Set(input.patches.map(e=>e?.file)).size!==input.patches.length||
  input.patches.some(p=>!exact(p,['file','oldText','newText'])||!spec.allowedPaths.includes(p.file)||
   !text(p.oldText,3200)||!text(p.newText,3200)||p.oldText===p.newText))
  throw Error('CODING_PROPOSAL_INVALID');
 return structuredClone(input);
}
