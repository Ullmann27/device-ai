// EXP-00/02/09: bounded project descriptions, not arbitrary code or shell.
import fs from 'node:fs';
import {createHash} from 'node:crypto';
import {canonical} from '../.github/continuum/ccc1-envelope.mjs';

/**
 * @typedef {{schema:'continuum-project-plan',version:1,id:string,
 * app:{name:string,packageName:string,minSdk:number,targetSdk:number,versionCode:number,versionName:string},
 * screens:Array<{id:string,kind:'note-editor'|'bounded-counter'|'about',title:string}>,
 * navigation:{start:string,links:Array<{from:string,to:string}>},
 * storage:{kind:'private-preferences',keys:string[],maxChars?:number,minValue?:number,maxValue?:number},
 * permissions:string[],resources:{external:string[],network:boolean},
 * tests:{unitClass:string,cases:string[],acceptance:string[]},
 * build:{artifact:string,network:'offline',install:'owner-review-only',tasks:string[]},
 * limits:{maxBuildMillis:number,maxHeapMiB:number,maxWorkers:number,maxApkBytes:number}}} ProjectPlan
 */
const freeze=value=>{
  if(value&&typeof value==='object'){Object.values(value).forEach(freeze);Object.freeze(value);}
  return value;
};
/** Only these two reviewed plans are executable in v1. No permissive JSON-to-code fallback. */
export const PROJECT_PLAN_IDS=Object.freeze(['m4-notes','m4-counter']);
let cachedPlans=null;
function loadPlans() {
  // CCC1's original repair profile must not need Android assets merely to import
  // its verifier. Load the project catalog only when this profile is requested.
  if(cachedPlans)return cachedPlans;
  const catalog=JSON.parse(fs.readFileSync(new URL('../app/src/main/assets/continuum-owned-project-plans-v1.json',import.meta.url),'utf8'));
  if(catalog.schema!=='continuum-project-catalog'||catalog.version!==1||catalog.plans.length!==2||
    JSON.stringify(catalog.plans.map(p=>p.id))!==JSON.stringify(PROJECT_PLAN_IDS))
    throw Error('INVALID_PROJECT_CATALOG');
  return cachedPlans=freeze(catalog.plans);
}
export function projectPlanProblems(plan) {
  try {
    if(!plan||plan.schema!=='continuum-project-plan'||plan.version!==1) return ['UNSUPPORTED_PROJECT_PLAN'];
    const approved=loadPlans().find(p=>p.id===plan.id);
    if(!approved||canonical(plan)!==canonical(approved)) return ['UNAPPROVED_PROJECT_PLAN'];
    return [];
  } catch { return ['INVALID_PROJECT_PLAN']; }
}
/** @returns {ProjectPlan} */
export function getProjectPlan(id='m4-notes') {
  const plan=loadPlans().find(p=>p.id===id);
  if(!plan) throw Error('INVALID_PROJECT_PLAN');
  return plan;
}
export function projectPlanSha256(plan) {
  if(projectPlanProblems(plan).length) throw Error('INVALID_PROJECT_PLAN');
  return createHash('sha256').update(canonical(plan)).digest('hex');
}
