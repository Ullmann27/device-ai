/** Read-only bridge from Android's registry snapshot to the existing fabric planner.
 * JSON transport cannot authenticate a registry or model; this never issues a grant.
 */
import crypto from 'node:crypto';
import {parseStrict} from './development-backlog.mjs';
import {planVirtualModelRoute} from './continuum-virtual-model-fabric.mjs';

const TASK_TYPES=new Set(['REASONING','CRITIQUE','VERIFY','RESEARCH_SYNTHESIS','EMBEDDING','BENCHMARK']);
const BACKENDS=new Set(['CPU','GPU','NPU']);
const ID=/^[A-Za-z0-9._:-]{1,128}$/;
const SHA=/^[a-f0-9]{64}$/;
const integer=(v,min,max)=>Number.isSafeInteger(v)&&v>=min&&v<=max;
const exact=(v,keys)=>v&&typeof v==='object'&&!Array.isArray(v)&&
 Object.keys(v).sort().join(',')===[...keys].sort().join(',');
const names=(v,allowed)=>Array.isArray(v)&&v.length>0&&v.length<=allowed.size&&
 v.every(x=>allowed.has(x))&&new Set(v).size===v.length;
const invalid=()=>{throw Error('REGISTRY_SNAPSHOT_INVALID');};

function snapshotFrom(json){
 if(typeof json!=='string'||Buffer.byteLength(json,'utf8')>65536)invalid();
 let s;try{s=parseStrict(json);}catch{invalid();}
 if(!exact(s,['schema','version','createdAtMillis','expiresAtMillis','executable','task','nodes'])||
  s.schema!=='continuum-compute-planning-snapshot'||s.version!==1||s.executable!==false||
  !integer(s.createdAtMillis,0,Number.MAX_SAFE_INTEGER)||
  !integer(s.expiresAtMillis,s.createdAtMillis+1,Number.MAX_SAFE_INTEGER)||
  s.expiresAtMillis-s.createdAtMillis>30000||!Array.isArray(s.nodes)||s.nodes.length>32)invalid();
 const t=s.task;
 if(!exact(t,['id','sha256','type','requiredRamMb','requiredBackend','maxRuntimeMillis',
  'maxOutputBytes','inputBytes','deadlineMillis','assignedNodeId'])||
  !ID.test(t.id??'')||!SHA.test(t.sha256??'')||!TASK_TYPES.has(t.type)||
  !integer(t.requiredRamMb,0,1048576)||!(t.requiredBackend===null||BACKENDS.has(t.requiredBackend))||
  !integer(t.maxRuntimeMillis,100,3600000)||!integer(t.maxOutputBytes,1,4194304)||
  !integer(t.inputBytes,0,16000)||!integer(t.deadlineMillis,s.expiresAtMillis,Number.MAX_SAFE_INTEGER)||
  !(t.assignedNodeId===null||typeof t.assignedNodeId==='string'&&ID.test(t.assignedNodeId)))invalid();
 for(const n of s.nodes){
  if(!exact(n,['nodeId','manifestSha256','taskTypes','backends','maxRamMb',
   'maxRuntimeMillis','maxOutputBytes','issuedAtMillis','expiresAtMillis'])||
   !ID.test(n.nodeId??'')||!SHA.test(n.manifestSha256??'')||
   !names(n.taskTypes,TASK_TYPES)||!names(n.backends,BACKENDS)||
   !integer(n.maxRamMb,128,1048576)||!integer(n.maxRuntimeMillis,100,3600000)||
   !integer(n.maxOutputBytes,1,4194304)||!integer(n.issuedAtMillis,0,Number.MAX_SAFE_INTEGER)||
   !integer(n.expiresAtMillis,n.issuedAtMillis+1,Number.MAX_SAFE_INTEGER))invalid();
 }
 if(new Set(s.nodes.map(n=>n.nodeId)).size!==s.nodes.length)invalid();
 return s;
}

export function planVirtualModelRouteFromRegistry({snapshotJson,request,modelOffers,nowMillis}={}){
 const s=snapshotFrom(snapshotJson),t=s.task;
 if(!integer(nowMillis,0,Number.MAX_SAFE_INTEGER)||nowMillis<s.createdAtMillis||
  nowMillis>=s.expiresAtMillis)throw Error('REGISTRY_SNAPSHOT_STALE');
 if(request?.taskId!==t.id||request?.taskSha256!==t.sha256||
  !integer(request?.maxInputBytes,t.inputBytes,16000)||
  !integer(request?.maxOutputBytes,1,t.maxOutputBytes)||
  !integer(request?.deadlineMillis,nowMillis+1,t.deadlineMillis))
  throw Error('REGISTRY_TASK_BINDING_INVALID');
 // Validate every offer using the existing planner before filtering it; malformed
 // declarations must not disappear merely because a registry node is absent.
 planVirtualModelRoute({request,offers:modelOffers,nowMillis});
 const compatible=new Map(s.nodes.filter(n=>
  n.issuedAtMillis<=nowMillis&&n.expiresAtMillis>=nowMillis+t.maxRuntimeMillis&&
  n.taskTypes.includes(t.type)&&(t.requiredBackend===null||n.backends.includes(t.requiredBackend))&&
  n.maxRamMb>=t.requiredRamMb&&n.maxRuntimeMillis>=t.maxRuntimeMillis&&
  n.maxOutputBytes>=t.maxOutputBytes&&(t.assignedNodeId===null||t.assignedNodeId===n.nodeId)
 ).map(n=>[n.nodeId,n]));
 const offers=modelOffers.filter(o=>compatible.has(o.nodeId)).map(o=>({...o,
  // Do not elevate a declaration saying ownerEnrolled:false.
  maxOutputBytes:Math.min(o.maxOutputBytes,compatible.get(o.nodeId).maxOutputBytes),
  expiresAtMillis:Math.min(o.expiresAtMillis,compatible.get(o.nodeId).expiresAtMillis,s.expiresAtMillis)
 }));
 const plan=planVirtualModelRoute({request,offers,nowMillis});
 return Object.freeze({...plan,
  registrySnapshotSha256:crypto.createHash('sha256').update(snapshotJson,'utf8').digest('hex'),
  registrySnapshotExpiresAtMillis:s.expiresAtMillis,
  registryTransportAuthenticated:false,resourceAndModelDeclarationsOnly:true,
  requiresLiveRegistryRecheckBeforeExecution:true
 });
}
