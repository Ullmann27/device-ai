import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {canonicalFixture} from './canonical-fixture.mjs';

const candidate=path.resolve(process.env.CONTINUUM_SERVICE_CANDIDATE??'.');
const classpath=process.env.GENESIS_HANDOFF_CLASSPATH;
const runtimeEnvPath=process.env.CONTINUUM_REVIEW_RUNTIME_ENV;
if(!classpath||!runtimeEnvPath)throw Error('Set exact-source GENESIS_HANDOFF_CLASSPATH and existing approved CONTINUUM_REVIEW_RUNTIME_ENV');
const PRIVATE='synthetic-secret-not-for-projection-700194';
const fixture=async(fn,options={})=>{const f=await canonicalFixture({candidate,classpath,runtimeEnvPath,...options});try{await fn(f);}finally{f.cleanup();}};
const result=f=>f.reader.getEvidence({taskId:f.task.id,evidenceId:'result'});
const report=f=>f.reader.getEvidence({taskId:f.task.id,evidenceId:'report'});
function digests(root){
  const out={};for(const name of fs.readdirSync(root).sort()){
    const file=path.join(root,name),stat=fs.lstatSync(file);
    if(stat.isDirectory())for(const [key,value]of Object.entries(digests(file)))out[name+'/'+key]=value;
    else if(stat.isFile())out[name]=crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  }return out;
}

test('D01/D08/D09/D10: real service canonical reader uses actual20 profiles and immutable negative evidence',async()=>fixture(async f=>{
  const before=digests(f.state),status=f.reader.getStatus();assert.equal(status.state,'STOPPED');assert.equal(status.cloudModelCallsAllowed,false);
  const ids=[];let cursor=null;do{
    const page=f.reader.listAgents({cursor,limit:3});ids.push(...page.items.map(p=>p.agentId));cursor=page.nextCursor;
    for(const p of page.items){assert.equal(p.state,'REGISTERED');assert.equal(p.modelActive,false);assert.deepEqual(p.permissionScope,[]);}
  }while(cursor);
  assert.equal(ids.length,20);assert.equal(new Set(ids).size,20);assert.ok(ids.includes('bot-forge'));
  const tasks=f.reader.listTasks();assert.equal(tasks.items[0].state,'ABSTAINED');assert.equal(tasks.items[0].builderId,'bot-forge');
  assert.equal(tasks.items[0].sourceCurrent,true);assert.equal(tasks.items[0].grantCurrent,true);
  for(const evidence of [result(f),report(f)]){
    assert.equal(evidence.content.status,'ABSTAINED');assert.equal(evidence.deviceVerified,false);
    assert.equal(evidence.sha256,f.sha256Hex(JSON.stringify(evidence.content)));
    assert.equal(evidence.content.approvedForMerge,false);
  }
  assert.equal(result(f).content.modelExecution.verifiedCalls,0);assert.equal(report(f).content.interpretation,'NO_CONFIRMED_REPAIR');
  assert.deepEqual(digests(f.state),before,'Read calls modified canonical control-plane bytes');
}));

test('D03/D07: snapshot status/private nested fields cannot override canonical abstention or leak',async()=>fixture(async f=>{
  f.metrics.resultStatus='INDEPENDENTLY_VERIFIED';f.metrics.apiKey=PRIVATE;
  f.metrics.modelExecution.privateKey=PRIVATE;f.metrics.modelExecution.identity.credential=PRIVATE;
  f.metrics.resourceUsage.secret=PRIVATE;f.sign();
  const value=result(f);assert.equal(value.content.resultStatus,'ABSTAINED');assert.ok(!JSON.stringify(value).includes(PRIVATE));
  f.report.interpretation=PRIVATE;f.report.status='COMPLETED';f.report.apiKey=PRIVATE;f.write(f.reportPath,f.report);
  f.metrics.reportSha256=f.sha256Hex(fs.readFileSync(f.reportPath));f.sign();
  const view=report(f);assert.equal(view.content.status,'ABSTAINED');assert.equal(view.content.interpretation,'NO_CONFIRMED_REPAIR');assert.ok(!JSON.stringify(view).includes(PRIVATE));
  f.metrics.modelExecution.verifiedCalls={privateKey:PRIVATE};f.sign();assert.throws(()=>result(f));
}));

test('D02/D03: revoked owner grant and changed source never reuse prior successful read',async()=>fixture(async f=>{
  result(f);
  fs.appendFileSync(path.join(f.project,'lib/value.mjs'),'// changed source\n');
  assert.equal(f.reader.listTasks().items[0].sourceCurrent,false);assert.throws(()=>result(f),/CURRENT_SOURCE/);
  f.run('git',['-C',f.project,'checkout','--','lib/value.mjs']);result(f);
  f.append('OWNER','ABORT',{reason:'Synthetic owner revocation',code:'OWNER'});
  assert.equal(f.reader.listTasks().items[0].grantCurrent,false);assert.throws(()=>result(f),/CURRENT_GRANT/);
  assert.equal(f.reader.listTasks().items[0].state,'ABORTED');
}));

test('D03/D08: tampered metrics/hash/journal binding are refused',async()=>fixture(async f=>{
  const bytes=fs.readFileSync(f.resultPath);const invalid=JSON.parse(bytes);invalid.modelExecution.verifiedCalls=1;f.write(f.resultPath,invalid);
  assert.throws(()=>result(f),/METRICS_AUTHENTICATION/);fs.writeFileSync(f.resultPath,bytes);
  f.metrics.journalHead='0'.repeat(64);f.sign();assert.throws(()=>result(f),/RESULT_STALE/);
  f.metrics.journalHead=f.journal.entries().at(-1).hash;f.metrics.taskFingerprint='0'.repeat(64);f.sign();assert.throws(()=>result(f),/RESULT_STALE/);
}));

test('D04/D05/D06: evidence, grant, key, journal and parent links never become arbitrary reads',async()=>fixture(async f=>{
  for(const evidenceId of ['../../ccc1-runtime.key','/etc/passwd','..%2fsecret','unknown'])assert.throws(()=>f.reader.getEvidence({taskId:f.task.id,evidenceId}));
  for(const taskId of ['../other','/tmp/private','bot-forge/secret'])assert.throws(()=>f.reader.getEvidence({taskId,evidenceId:'result'}));
  const files=[f.resultPath,f.reportPath,path.join(f.ccc1,'coding-grants',f.task.id+'.json'),
    path.join(f.ccc1,'ccc1-runtime.key'),f.journal.file];
  for(const file of files){
    const backup=file+'.independent-backup';fs.renameSync(file,backup);fs.symlinkSync(backup,file);
    assert.throws(()=>file===f.reportPath?report(f):result(f),'symlink '+path.basename(file));
    fs.unlinkSync(file);fs.renameSync(backup,file);fs.linkSync(file,backup);
    assert.throws(()=>file===f.reportPath?report(f):result(f),'hardlink '+path.basename(file));fs.unlinkSync(backup);
  }
  const directory=path.join(f.state,'public-evidence'),moved=directory+'-private';fs.renameSync(directory,moved);fs.symlinkSync(moved,directory);
  assert.throws(()=>result(f),'symlink parent');fs.unlinkSync(directory);fs.renameSync(moved,directory);result(f);
}));

test('D03: fabricated confirmed journal verdict lacks an independent receipt and is refused',async()=>fixture(async f=>{
  const hypothesis=f.append('REASONER','HYPOTHESIS',{claim:'Synthetic forgery counterexample',basis:'No actual execution',repair:{chain:'node'}});
  const request=f.append('REASONER','TEST_REQUEST',{hypothesis:hypothesis.messageId,repair:{chain:'node'},budgetMinutes:1});
  const {jobIdFor}=await import(path.join(candidate,'.github/continuum/ccc1-recovery.mjs'));
  const {issueVerificationId}=await import(path.join(candidate,'.github/continuum/ccc1-verifier.mjs'));
  const receipt=f.append('VERIFIER','RECEIPT',{request:request.messageId,job:jobIdFor(f.delivery.ccc1TaskId,request.messageId),
    status:'COMPLETED',verdict:'CONFIRMED',receiptSha256:'1'.repeat(64),execution:'NOT_EXECUTED'},
    {verificationReceiptId:issueVerificationId(f.key,{request:request.messageId,receiptSha256:'1'.repeat(64),verdict:'CONFIRMED'})});
  f.append('REASONER','CRITIQUE',{receipt:receipt.messageId,verdict:'CONFIRMED',reason:'A verdict string must not replace execution evidence'});
  f.metrics.journalHead=f.journal.entries().at(-1).hash;f.metrics.resultStatus='COMPLETED';f.sign();
  assert.throws(()=>result(f));
}));

test('D01/B02: corrupted delivery/descriptor and invalid page bounds fail closed',async()=>fixture(async f=>{
  for(const method of ['listTasks','listAgents'])for(const limit of [0,-1,1.5,51,'1',null])assert.throws(()=>f.reader[method]({limit}));
  f.write(path.join(f.state,'service.json'),{schema:'continuum-coding-service-process',version:1,instanceId:'wrong',configSha256:f.config.configSha256});
  assert.throws(()=>f.reader.getStatus(),/DESCRIPTOR_INVALID/);fs.unlinkSync(path.join(f.state,'service.json'));
  const file=path.join(f.state,'deliveries.json');f.write(file,{schema:'continuum-owner-inbox',version:1,items:[{...f.delivery,fingerprint:'wrong'}]});
  assert.throws(()=>f.reader.listTasks(),/INBOX_CORRUPT/);
}));

test('D10: changed pinned profile classpath is refused before Java metadata execution',async()=>fixture(async f=>{
  assert.equal(f.reader.listAgents().items.length,20);
  // A harmless extra file changes the signed classpath bytes while existing Java
  // classes remain valid. This detects actual pin enforcement, not class-loader failure.
  fs.writeFileSync(path.join(f.classpathDirectory,'independent-modification.txt'),'controlled dependency change\n');
  assert.throws(()=>f.reader.listAgents(),/CLASSPATH_CHANGED/);
  assert.throws(()=>result(f),/CLASSPATH_CHANGED/);
},{copyProfileClasspath:true}));
