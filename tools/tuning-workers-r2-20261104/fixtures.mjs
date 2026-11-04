import * as B from '../../.github/continuum/development-backlog.mjs';

export const sizes=[0,1,20,100,500];
export const modes=['none','none-no-match','one-first','one-middle','one-last',
  'sixteen-spread','sixteen-first-blocked','sixteen-last-blocked'];
const token='a'.repeat(32),digest='b'.repeat(64);

export function setTaskState(task,state){
  task.state=state;
  task.claim=state==='IN_PROGRESS'?{owner:'test',token,at:10,leaseUntil:60_010}:null;
  task.submission=['VERIFYING','DONE'].includes(state)?{
    token,by:'test',at:20,artifact:{type:'REPORT',sha256:digest}
  }:null;
  task.evidence=state==='DONE'?[{
    type:'TEST_RUN',taskId:task.id,submissionToken:token,artifactSha256:digest,
    chain:'test',commit:'c'.repeat(40),at:30,by:'test',repair:null,
    profiles:[{profile:'node-continuum',passed:true,executed:1,failed:0,skipped:0}]
  }]:[];
  return task;
}

export function makeDependencyDocument(size,mode,seed=7){
  let state=seed>>>0;
  const random=()=>{state=(Math.imul(state,1664525)+1013904223)>>>0;return state;};
  const tasks=Array.from({length:size},(_,i)=>({
    id:'task-'+String(i).padStart(3,'0'),title:'Synthetic dependency input',
    kind:['TEST','REPAIR','INVESTIGATE'][i%3],source:i%4===0?'cli':'owner',
    state:'READY',priority:random()%1001,dependsOn:[],attempts:0,maxAttempts:3,
    claim:null,submission:null,evidence:[],evidenceDropped:0,
    history:[],historyDropped:0,error:null,createdAt:1+random()%7,updatedAt:100,
    subject:i%7===0?null:{base:i%4===0?'old':'head',operation:i%2===0?'OTHER':'ASSERT_NODE_EXPORT'}
  }));
  let dependencyIndexes=[];
  if(size>1&&!mode.startsWith('none')){
    if(mode==='one-first')dependencyIndexes=[0];
    else if(mode==='one-middle')dependencyIndexes=[Math.floor((size-1)/2)];
    else if(mode==='one-last')dependencyIndexes=[size-1];
    else{
      const count=Math.min(16,size-1);
      dependencyIndexes=Array.from({length:count},(_,i)=>Math.floor((i+1)*size/count)-1);
    }
  }
  const dependencies=dependencyIndexes.map(i=>tasks[i]);
  const ids=dependencies.map(t=>t.id);
  for(const task of tasks){
    if(ids.includes(task.id))setTaskState(task,'DONE');
    else task.dependsOn=[...ids];
  }
  if(mode==='sixteen-first-blocked'&&dependencies.length)setTaskState(dependencies[0],'PROPOSED');
  if(mode==='sixteen-last-blocked'&&dependencies.length)setTaskState(dependencies.at(-1),'PROPOSED');
  const doc={...B.emptyDocument(100,'test'),tasks};
  B.validateDocument(doc);
  return doc;
}

export function deepFreeze(value){
  if(value!==null&&typeof value==='object'){
    for(const child of Object.values(value))deepFreeze(child);
    Object.freeze(value);
  }
  return value;
}

export function completeTaskOperations(id,at=10){
  return [
    {op:'claim',actor:'test',at,id,owner:'test',token,leaseMs:60_000},
    {op:'submit',actor:'test',at:at+1,id,token,artifact:{type:'REPORT',sha256:digest}},
    {op:'complete',actor:'test',at:at+2,id,evidence:{
      type:'TEST_RUN',taskId:id,submissionToken:token,artifactSha256:digest,
      chain:'test',commit:'c'.repeat(40),at:at+2,by:'test',repair:null,
      profiles:[{profile:'node-continuum',passed:true,executed:1,failed:0,skipped:0}]
    }}
  ];
}
