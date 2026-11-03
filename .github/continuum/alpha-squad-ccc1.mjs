#!/usr/bin/env node
/**
 * Continuum Alpha Squad: an operational entrance to the EXISTING CCC1 worker.
 *
 * Four virtual job roles (architect, RED-test, coding, independent verifier) are
 * checkpoints of one authorized CCC1 task, NOT four separate loaded LLMs.
 * Never executes arbitrary model-provided commands, creates a branch, merges a
 * patch or touches production. The underlying CCC1 worker owns real execution.
 */
import {fileURLToPath} from 'node:url';
import {assessCcc1Preflight} from './ccc1-local-preflight.mjs';
import {compileRepair,listTasks,runCcc1Cycle} from './ccc1-worker.mjs';

const HEX40=/^[0-9a-f]{40}$/;
const CHAINS=new Set(['node','android','jvm']);
export const ALPHA_SQUAD_ROLES=Object.freeze([
 Object.freeze({id:'bot-01-architect',stage:'PLAN',execution:'CCC1 preflight + fixed plan'}),
 Object.freeze({id:'bot-11-unit-test-forger',stage:'RED',execution:'CCC1 baseline test; not a separately generated acceptance test'}),
 Object.freeze({id:'bot-04-kotlin-core',stage:'PATCH',execution:'Existing authorized CCC1 repair pipeline'}),
 Object.freeze({id:'bot-12-static-guard',stage:'VERIFY',execution:'CCC1 independent candidate checks and review receipt'})
]);

function validParams({project,stateDir,chain}){
 if(typeof project!=='string'||!project.trim()||
    typeof stateDir!=='string'||!stateDir.trim()||
    !CHAINS.has(chain))throw Error('ALPHA_INVALID_INPUT');
}

export function alphaPlan({project,stateDir,chain},{
 compile=compileRepair
}={}){
 validParams({project,stateDir,chain});
 const result=compile({project,stateDir,chain});
 return Object.freeze({
  schema:'continuum-alpha-squad',version:1,mode:'PLAN_ONLY',
  state:result.status==='READY'?'READY_FOR_OWNER_SUBMISSION':'BLOCKED',
  sourceCommit:result.preflight?.sourceCommit??null,
  existingCcc1Status:result.status,
  findings:result.preflight?.findings??[],
  roles:ALPHA_SQUAD_ROLES,
  physicalCodingSandboxesStarted:0,
  modelCalls:0,notes:'These are roles on the existing CCC1 worker; not yet four independently running agents.'
 });
}

/**
 * Execute one bounded cycle of a task ALREADY submitted by the owner through ccc1-cli.
 * This wrapper does NOT submit a goal, grant permissions or accept a tool command from LLM text.
 */
export async function alphaRunOne({
 project,stateDir,chain,expectedSourceCommit,ownerConfirmed=false
},{
 preflight=assessCcc1Preflight,
 tasks=listTasks,
 run=runCcc1Cycle
}={}){
 validParams({project,stateDir,chain});
 if(!ownerConfirmed)return {schema:'continuum-alpha-squad',version:1,
  status:'OWNER_CONFIRMATION_REQUIRED',workerCycles:0};
 if(!HEX40.test(expectedSourceCommit||''))throw Error('ALPHA_EXPECTED_SOURCE_REQUIRED');
 const pf=preflight({project,stateDir});
 if(pf?.result!=='READY_TO_REQUEST_OWNER_REVIEW'||
    pf.sourceCommit!==expectedSourceCommit)
  return {schema:'continuum-alpha-squad',version:1,status:'PREFLIGHT_BLOCKED',
   reason:'Source identity, checkout safety or state location mismatch',workerCycles:0};
 const open=tasks(stateDir).filter(t=>t&&t.terminal===false);
 // The current canonical CCC1 runCycle selects the oldest ready authorized task.
 // Require exactly one open task, so a "run once" cannot silently drive an unrelated one.
 if(open.length!==1)return {schema:'continuum-alpha-squad',version:1,
  status:open.length===0?'NO_OWNER_TASK':'MULTIPLE_OPEN_TASKS',workerCycles:0};
 if(open[0].repair!==chain)return {schema:'continuum-alpha-squad',version:1,
  status:'TASK_CHAIN_MISMATCH',workerCycles:0};
 const result=await run({project,stateDir,core:'disambiguate'});
 const outcome=result?.outcome;
 // Even an apparently successful CCC1 response is not a deployment or a release.
 return {schema:'continuum-alpha-squad',version:1,
  status:outcome==='VERIFIED_PATCH_PENDING_REVIEW'?'PATCH_PENDING_OWNER_REVIEW':
   'CYCLE_FINISHED_NOT_VERIFIED',
  workerCycles:1,taskId:open[0].taskId,outcome:typeof outcome==='string'?outcome:'UNKNOWN',
  patchMerged:false,apkBuilt:false,deviceVerified:false};
}

function parse(argv){
 const [action,...rest]=argv;
 if(!['plan','run-once'].includes(action))throw Error('ALPHA_COMMAND_INVALID');
 const args={};
 for(let i=0;i<rest.length;i+=2){
  const key=rest[i],value=rest[i+1];
  if(!['--project','--state','--repair','--expected-head','--owner-confirmed'].includes(key)||
     value===undefined||Object.hasOwn(args,key))throw Error('ALPHA_ARGS_INVALID');
  args[key]=value;
 }
 if(!args['--project']||!args['--state']||!args['--repair'])throw Error('ALPHA_ARGS_MISSING');
 return {action,project:args['--project'],stateDir:args['--state'],chain:args['--repair'],
  expectedSourceCommit:args['--expected-head'],
  ownerConfirmed:args['--owner-confirmed']==='true'};
}

export async function main(argv=process.argv.slice(2),print=console.log){
 const args=parse(argv);
 const result=args.action==='plan'?alphaPlan(args):await alphaRunOne(args);
 print(JSON.stringify(result));
 return result;
}
if(process.argv[1]===fileURLToPath(import.meta.url))
 main().catch(e=>{console.error(String(e?.message??e));process.exitCode=1;});
