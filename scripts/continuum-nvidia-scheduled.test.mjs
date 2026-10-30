import test from 'node:test';
import assert from 'node:assert/strict';
import {validateLedger,decideReservation,reserveGithubBudget,executeReservedResearch,
 LEDGER_BRANCH,LEDGER_PATH,MAX_PER_UTC_DAY} from './continuum-nvidia-scheduled.mjs';
const blank=()=>({schema:'continuum-nvidia-schedule-ledger',version:1,reservations:[]});
const at=s=>Date.parse('2026-10-30T'+s+'Z');
const r=(hh,id,i)=>({runId:id,atMillis:at(hh),utcDate:'2026-10-30',sequence:i});
const token='ghs_'+'z'.repeat(50);
const json=(status,data)=>({ok:status>=200&&status<300,status,async json(){return data;}});
test('first reservation durable, same GitHub run never repeated',()=>{
 const x=decideReservation(blank(),{runId:'110',nowMs:at('00:07:00')});
 assert.equal(x.allowed,true);assert.equal(x.sequence,0);
 assert.equal(decideReservation(x.ledger,{runId:'110',nowMs:at('05:07:00')}).reason,'RUN_ALREADY_RESERVED');
});
test('cron checks each 10m, model request no sooner than 4h',()=>{
 const l=blank();l.reservations.push(r('00:07:00','110',0));
 assert.equal(decideReservation(l,{runId:'111',nowMs:at('00:17:00')}).reason,'COOLDOWN');
 assert.equal(decideReservation(l,{runId:'112',nowMs:at('04:07:00')}).allowed,true);
});
test('three NVIDIA calls max in UTC day',()=>{
 const l=blank();
 for(let i=0;i<MAX_PER_UTC_DAY;i++)l.reservations.push(r(String(i*4).padStart(2,'0')+':07:00',String(110+i),i));
 assert.equal(decideReservation(l,{runId:'119',nowMs:at('16:07:00')}).reason,'DAILY_QUOTA_EXHAUSTED');
 assert.equal(decideReservation(l,{runId:'120',nowMs:Date.parse('2026-10-31T00:07:00Z')}).allowed,true);
});
test('tampering, duplicate run, invalid provenance and time inversion fail closed',()=>{
 assert.throws(()=>validateLedger({}),/LEDGER_INVALID/);
 const l=blank();l.reservations=[r('01:07:00','101',0),r('02:07:00','101',1)];
 assert.throws(()=>validateLedger(l),/LEDGER_DUPLICATES/);
 l.reservations[1]=r('00:07:00','102',1);
 assert.throws(()=>validateLedger(l),/LEDGER_TIME_ORDER_INVALID/);
});
test('GitHub API uses only dedicated ledger path, atomic SHA and no secret persistence',async()=>{
 const calls=[];
 const fetchImpl=async(url,opts)=>{
  calls.push({url,opts});
  assert.equal(opts.headers.Authorization,'Bearer '+token);
  assert.equal(url.startsWith('https://api.github.com/repos/Ullmann27/device-ai/contents/'+LEDGER_PATH),true);
  return opts.method==='GET'?
   json(200,{sha:'a'.repeat(40),encoding:'base64',content:Buffer.from(JSON.stringify(blank())).toString('base64')}):
   json(200,{commit:{sha:'b'.repeat(40)}});
 };
 const x=await reserveGithubBudget({repoFull:'Ullmann27/device-ai',token,runId:'110',
  nowMs:at('00:07:00'),fetchImpl});
 assert.equal(x.allowed,true);assert.equal(calls.length,2);assert.equal(calls[1].opts.method,'PUT');
 const body=JSON.parse(calls[1].opts.body);
 assert.equal(body.branch,LEDGER_BRANCH);assert.equal(body.sha,'a'.repeat(40));
 const stored=JSON.parse(Buffer.from(body.content,'base64').toString('utf8'));
 assert.equal(stored.reservations.length,1);assert.equal(JSON.stringify(stored).includes(token),false);
});
test('GitHub quota ledger write denied -> zero authorized cloud calls',async()=>{
 let count=0;
 const fetchImpl=async(_u,o)=>{
  count++;
  return o.method==='GET'?
   json(200,{sha:'a'.repeat(40),encoding:'base64',content:Buffer.from(JSON.stringify(blank())).toString('base64')}):
   json(403,{message:'forbidden'});
 };
 await assert.rejects(reserveGithubBudget({repoFull:'Ullmann27/device-ai',token,runId:'110',
  nowMs:at('00:07:00'),fetchImpl}),/GITHUB_LEDGER_HTTP_403/);
 assert.equal(count,2);
});
test('during cooldown the runner cannot write or contact NVIDIA',async()=>{
 const ledger=blank();ledger.reservations=[r('00:07:00','101',0)];
 let puts=0;
 const fetchImpl=async(_u,o)=>{if(o.method==='PUT')puts++;return json(200,{sha:'a'.repeat(40),
  encoding:'base64',content:Buffer.from(JSON.stringify(ledger)).toString('base64')});};
 const x=await reserveGithubBudget({repoFull:'Ullmann27/device-ai',token,runId:'102',
  nowMs:at('00:17:00'),fetchImpl});
 assert.equal(x.allowed,false);assert.equal(x.reason,'COOLDOWN');assert.equal(puts,0);
});
test('unreserved call input invalid without contacting a model',async()=>{
 let calls=0;
 for(const args of [{sequence:-1,apiKey:'nvapi-'+'x'.repeat(44)},
  {sequence:0,apiKey:''}]){
  await assert.rejects(executeReservedResearch({...args,outputDir:'/tmp/research',
   callReview:async()=>{calls++}}));
 }
 assert.equal(calls,0);
});