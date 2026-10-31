import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {DatabaseSync} from 'node:sqlite';
import {createHandlers} from '../worker.mjs';
const schema=readFileSync(new URL('../schema.sql',import.meta.url),'utf8');
function fixture({on='true',client=async()=>good(),vars={},broken=false,fresh=false}={}){
 const sqlite=new DatabaseSync(':memory:');if(!fresh)sqlite.exec(schema);
 const DB={prepare(sql){return {
  bind(...args){return {
   async run(){return {meta:{changes:sqlite.prepare(sql).run(...args).changes}};},
   async first(){return sqlite.prepare(sql).get(...args)??null;}
  };},
  async first(){return sqlite.prepare(sql).get()??null;},
  async run(){return {meta:{changes:sqlite.prepare(sql).run().changes}};}
 };}};
 const queueItems=[],RESEARCH_QUEUE={async send(body){if(broken)throw Error('failed');queueItems.push({body});}};
 let time=Date.UTC(2026,9,31,12,0,0),calls=0;
 const worker=createHandlers({now:()=>time,uuid:()=>crypto.randomUUID(),httpFetch:async(...args)=>{calls++;return client(...args);}});
 const env={DB,RESEARCH_QUEUE,NVIDIA_ENABLED:on,NVIDIA_API_KEY:'fake-test-key',
  NVIDIA_FREE_TIER_CONFIRMED:'true',
  CONTINUUM_OWNER_GRANT_AT_UTC:new Date(time-60_000).toISOString(),
  CONTINUUM_OWNER_GRANT_UNTIL_UTC:new Date(time+3_600_000).toISOString(),
  ...vars};
 return {worker,env,sqlite,queueItems,callCount:()=>calls,advance:n=>{time+=n;},
  tick:()=>worker.scheduled({scheduledTime:time},env),
  consume:async()=>{const msg=queueItems.shift();assert.ok(msg);await worker.queue({messages:[msg]},env);return msg;},
  rows:sql=>sqlite.prepare(sql).all()};
}
const good=()=>new Response(JSON.stringify({choices:[{message:{content:'A testable hypothesis.'}}]}),{status:200});
const records=f=>f.rows('SELECT status,topic FROM continuum_nvidia_jobs ORDER BY sequence');
test('offline default logs cron without provider requests',async()=>{
 const f=fixture({on:'false'});await f.tick();
 assert.equal(f.callCount(),0);assert.equal(f.queueItems.length,0);
 assert.equal(f.rows('SELECT * FROM jarvis_cron_log').length,1);
});
test('completed NVIDIA answer immediately enqueues next question',async()=>{
 const f=fixture();await f.tick();await f.consume();
 assert.equal(f.callCount(),1);assert.equal(f.queueItems.length,1);
 await f.consume();assert.equal(f.callCount(),2);assert.equal(f.queueItems.length,1);
 assert.deepEqual(records(f).slice(0,2).map(x=>x.status),['proposal_unreviewed','proposal_unreviewed']);
});
test('daily request cap limits a burst rather than forcing hourly waits',async()=>{
 const f=fixture({vars:{NVIDIA_MAX_CALLS_24H:'3'}});
 await f.tick();await f.consume();await f.consume();await f.consume();
 assert.equal(f.callCount(),3);assert.equal(f.queueItems.length,0);
});
test('daily reserved-output-token limit blocks additional jobs',async()=>{
 const f=fixture({vars:{NVIDIA_MAX_RESERVED_TOKENS_24H:'8200',NVIDIA_MAX_TOKENS_PER_CALL:'4096'}});
 await f.tick();await f.consume();await f.consume();assert.equal(f.queueItems.length,0);
});
test('429 honors provider retry-after and stops immediate chaining',async()=>{
 const f=fixture({client:async()=>new Response('private',{status:429,headers:{'retry-after':'90'}})});
 await f.tick();await f.consume();assert.equal(f.queueItems.length,0);
 f.advance(89_000);await f.tick();assert.equal(f.queueItems.length,0);
 f.advance(2000);await f.tick();assert.equal(f.queueItems.length,1);
});
test('402 exhausted credits automatically disable future requests',async()=>{
 const f=fixture({client:async()=>new Response('private',{status:402})});
 await f.tick();await f.consume();await f.tick();
 assert.equal(f.queueItems.length,0);
 assert.equal(f.rows('SELECT state FROM continuum_nvidia_control')[0].state,'disabled');
});
test('already consumed message cannot call NVIDIA twice',async()=>{
 const f=fixture();await f.tick();const old=await f.consume();
 await f.worker.queue({messages:[old]},f.env);assert.equal(f.callCount(),1);
});
test('interrupted message is marked unknown and replaced by cron',async()=>{
 const f=fixture();await f.tick();const old=f.queueItems.shift();
 f.advance(17*60000);await f.tick();assert.equal(records(f)[0].status,'abandoned_unknown_provider_result');
 await f.worker.queue({messages:[old]},f.env);assert.equal(f.callCount(),0);
 await f.consume();assert.equal(f.callCount(),1);
});
test('network errors halt chaining and do not expose exception details',async()=>{
 const f=fixture({client:async()=>{throw Error('secret-body');}});
 await f.tick();await f.consume();assert.equal(f.queueItems.length,0);
 assert.equal(records(f)[0].status,'network_error');
 assert.ok(!JSON.stringify(f.rows('SELECT * FROM continuum_nvidia_jobs')).includes('secret-body'));
});
test('queue publish failure records failure without provider call',async()=>{
 const f=fixture({broken:true});await f.tick();
 assert.equal(records(f)[0].status,'queue_send_failed');assert.equal(f.callCount(),0);
});
test('HTTP routes remain non-mutating and never reveal research',async()=>{
 const f=fixture();const req=(p,m='GET')=>new Request('https://example.workers.dev'+p,{method:m});
 assert.equal((await f.worker.fetch(req('/health'),f.env)).status,200);
 assert.equal((await f.worker.fetch(req('/db-status'),f.env)).status,200);
 assert.equal((await f.worker.fetch(req('/db-test'),f.env)).status,403);
 assert.equal((await f.worker.fetch(req('/research/latest'),f.env)).status,404);
 assert.equal((await f.worker.fetch(req('/health','POST'),f.env)).status,405);
});
test('additive schema migration is idempotent and preserves old rows',()=>{
 const f=fixture();f.sqlite.exec("INSERT INTO jarvis_cron_log(cron_time,action) VALUES('past','legacy')");
 f.sqlite.exec(schema);assert.equal(f.rows('SELECT * FROM jarvis_cron_log').length,1);
});


test('RED/GREEN: owner disables after enqueue; queued message must not call NVIDIA',async()=>{
 const f=fixture();await f.tick();assert.equal(f.queueItems.length,1);
 f.sqlite.exec("UPDATE continuum_nvidia_control SET state='disabled' WHERE id=1");
 await f.consume();
 assert.equal(f.callCount(),0);
 assert.equal(records(f)[0].status,'queued');
});
test('RED/GREEN: cooldown introduced after enqueue blocks already queued NVIDIA request',async()=>{
 const f=fixture();await f.tick();assert.equal(f.queueItems.length,1);
 f.sqlite.exec('UPDATE continuum_nvidia_control SET blocked_until_ms=9999999999999 WHERE id=1');
 await f.consume();
 assert.equal(f.callCount(),0);
 assert.equal(records(f)[0].status,'queued');
});

test('empty database self-initializes from cron through existing DB binding',async()=>{
 const f=fixture({on:'false',fresh:true});await f.tick();
 assert.equal(f.rows("SELECT name FROM sqlite_master WHERE type='table' AND name='continuum_nvidia_jobs'").length,1);
 assert.equal(f.rows('SELECT * FROM jarvis_cron_log').length,1);
 assert.equal(f.callCount(),0);
});
test('self-initialization preserves old D1 tables and rows',async()=>{
 const f=fixture({on:'false'});
 f.sqlite.exec('CREATE TABLE IF NOT EXISTS jarvis_health_log (id INTEGER PRIMARY KEY, note TEXT)');
 f.sqlite.exec("INSERT INTO jarvis_health_log(id,note) VALUES(1,'keep')");
 f.sqlite.exec("INSERT INTO jarvis_cron_log(cron_time,action) VALUES('yesterday','legacy')");
 await f.tick();
 assert.equal(f.rows('SELECT note FROM jarvis_health_log')[0].note,'keep');
 assert.equal(f.rows('SELECT * FROM jarvis_cron_log').length,2);
});

test('RED: enabled provider key without explicit owner grant cannot reserve or contact NVIDIA',async()=>{
 for(const vars of [
  {CONTINUUM_OWNER_GRANT_AT_UTC:undefined},
  {CONTINUUM_OWNER_GRANT_UNTIL_UTC:undefined},
  {NVIDIA_FREE_TIER_CONFIRMED:undefined},
  {NVIDIA_FREE_TIER_CONFIRMED:'false'},
  {CONTINUUM_OWNER_GRANT_AT_UTC:'not-a-date'},
  {CONTINUUM_OWNER_GRANT_UNTIL_UTC:'2026-02-31T12:00:00Z'},
  {CONTINUUM_OWNER_GRANT_UNTIL_UTC:'2026-10-31T11:59:00.000Z'},
  {CONTINUUM_OWNER_GRANT_AT_UTC:'2026-10-30T10:00:00.000Z'},
  {CONTINUUM_OWNER_GRANT_UNTIL_UTC:'2026-11-02T12:00:00.000Z'}
 ]){
  const f=fixture({vars});await f.tick();
  assert.equal(f.queueItems.length,0,JSON.stringify(vars));
  assert.equal(f.callCount(),0);
 }
});
test('RED: owner grant revoked after enqueue blocks already queued provider call',async()=>{
 const f=fixture();await f.tick();assert.equal(f.queueItems.length,1);
 f.env.NVIDIA_FREE_TIER_CONFIRMED='false';
 await f.consume();assert.equal(f.callCount(),0);
 assert.equal(records(f)[0].status,'queued');
});
test('RED: consent expires while queued, even with valid API key',async()=>{
 const f=fixture();await f.tick();f.advance(3_600_001);
 await f.consume();assert.equal(f.callCount(),0);
 assert.equal(records(f)[0].status,'queued');
});
test('RED: grant revoked during D1 claim is rechecked before provider request',async()=>{
 const f=fixture();await f.tick();
 const original=f.env.DB.prepare.bind(f.env.DB);
 f.env.DB.prepare=(sql)=>{
  if(sql.startsWith('SELECT topic,sequence'))f.env.NVIDIA_FREE_TIER_CONFIRMED='false';
  return original(sql);
 };
 await f.consume();assert.equal(f.callCount(),0);
 assert.equal(records(f)[0].status,'authorization_expired');
});
test('GREEN: valid <=24h owner grant still permits immediate chained research',async()=>{
 const f=fixture({vars:{NVIDIA_MAX_CALLS_24H:'2'}});
 await f.tick();await f.consume();await f.consume();
 assert.equal(f.callCount(),2);
 assert.equal(f.queueItems.length,0);
 assert.deepEqual(records(f).map(x=>x.status),['proposal_unreviewed','proposal_unreviewed']);
});
