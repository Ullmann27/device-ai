// Isolated unit dependencies for exact caller source. No executor/model/CCC1 success attestation.
export {isTrustedLocalModelObservation,createCodingCandidateProvider} from './caller-fixture-runtime.mjs';
export const control={source:null,steps:[],errors:[],outcome:'FAILED',calls:0};
export const executor=Object.freeze({synthetic:true});
export const isIsolatedExecutor=value=>value===executor;
export const bindCcc1Workspace=({executor})=>executor;
export const ensureCcc1StateOutsideSource=(_project,state)=>state;
export const runtimeKey=()=>Buffer.alloc(32,17);
export const repositoryOf=()=> 'synthetic/caller';
export const git=(_project,args)=>({stdout:args[0]==='rev-parse'?control.source:''});
export const startTask=()=> 't-aaaaaaaaaaaaaaaa';
export const jobTaskId=()=> 'synthetic-model-job';
export const builderStateOf=s=>s;
export const readReceipt=()=>null;
export const createAgentProviderFromEnv=()=>{throw Error('Not used');};
export const REPAIR_CAPABILITY='node-repair';
export class Ccc1Reasoner{}
export class ExperienceLog{append(){return true;}}
export const experienceOf=()=>null;
export const localTeamleaderDriver=async({candidateProvider})=>candidateProvider;
export async function runMobileRepairJob({driver,job}){
 for(const step of control.steps){control.step=step;control.calls++;try{driver({synthetic:true});}catch(error){control.errors.push(error.message);if(['CODING_LOCAL_MODEL_CHANGED','CODING_LOCAL_MODEL_OBSERVATION_REQUIRED','BLOCKED_LOCAL_MODEL_UNAVAILABLE'].includes(error.message))break;}}
 return {job,status:'SYNTHETIC_NO_EXECUTION'};
}
export async function runTask({runRepairJob}){await runRepairJob({job:'00000000-0000-4000-8000-000000000001',chain:'node'});return {outcome:control.outcome,entries:[]};}
