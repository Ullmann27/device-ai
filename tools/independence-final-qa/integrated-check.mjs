/** Independent integration acceptance: real CLI/HTTP/Kotlin; no model/repair result invented. */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {spawn,spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const opt={};
for(let i=2;i<process.argv.length;i+=2){
 const k=process.argv[i],v=process.argv[i+1];
 assert.ok(['--source','--classpath-file','--runtime-env','--isolation-config','--output'].includes(k)&&v&&!opt[k]);
 opt[k]=path.resolve(v);
}
for(const k of ['--source','--classpath-file','--runtime-env','--isolation-config','--output'])assert.ok(opt[k]);
const root=opt['--source'],self=path.dirname(fileURLToPath(import.meta.url));
const cli=path.join(root,'scripts/continuum-coding-service.mjs'),mcpCli=path.join(root,'scripts/continuum-coding-mcp.mjs');
const env={PATH:'/usr/bin:/bin',LANG:'C.UTF-8'};
const run=(exe,args,cwd=root,ok=true)=>{
 const r=spawnSync(exe,args,{cwd,env,encoding:'utf8',timeout:45000,maxBuffer:128*1024,shell:false});
 if(ok)assert.equal(r.status,0,`${path.basename(exe)} failed: ${r.stderr.slice(0,500)}`);
 else assert.notEqual(r.status,0,'Expected rejection');
 return r;
};
const git=(args,cwd=root)=>run('git',args,cwd).stdout.trim();
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const tree=dir=>{
 const rows=[];const walk=p=>{for(const name of fs.readdirSync(p).sort()){
 const f=path.join(p,name),s=fs.lstatSync(f);if(s.isDirectory())walk(f);else if(s.isFile())rows.push([path.relative(dir,f),sha(fs.readFileSync(f))]);
 else rows.push([path.relative(dir,f),'NON_FILE']);}};walk(dir);return sha(JSON.stringify(rows));
};
const operate=(command,args=[],ok=true)=>{
 const r=run(process.execPath,[cli,command,'--config',config,...args],root,ok);
 return ok?JSON.parse(r.stdout):r;
};
let fixture,config,observer=null,serviceStarted=false,token='',canary='PRIVATE-'+crypto.randomUUID();
const report={schema:'continuum-independent-service-mcp-check',version:1,sourceCommit:git(['rev-parse','HEAD']),
 environment:'HOST',deviceVerified:false,modelInference:'NOT_RUN',repair:'NOT_RUN',chatgptConnection:'NOT_CONFIGURED',node:process.version,checks:[],timings:[]};
const check=(name,fn)=>{const started=performance.now();return Promise.resolve().then(fn).then(()=>{
 report.checks.push({name,status:'PASS',elapsedMs:performance.now()-started});
 }).catch(e=>{report.checks.push({name,status:'FAIL',message:String(e.message).slice(0,1000)});throw e;});};
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function observerStart(tokenFile){
 const child=spawn(process.execPath,[mcpCli,'serve','--config',config,'--token-file',tokenFile,'--port','0'],{cwd:root,env,stdio:['ignore','pipe','pipe'],shell:false});
 let output='',errors='';child.stderr.on('data',x=>errors+=x);
 const first=await new Promise((resolve,reject)=>{
 const t=setTimeout(()=>{child.kill('SIGKILL');reject(Error('MCP_TIMEOUT'));},10000);
 child.once('exit',()=>{clearTimeout(t);reject(Error('MCP_START_FAILED:'+errors.slice(0,250)));});
 child.stdout.on('data',x=>{output+=x;if(output.length>8192){child.kill('SIGKILL');clearTimeout(t);reject(Error('MCP_OUTPUT_LIMIT'));}
 if(output.includes('\n')){try{const v=JSON.parse(output.split('\n')[0]);clearTimeout(t);resolve(v);}catch(e){reject(e);}}});
 });
 assert.equal(first.status,'LISTENING');assert.equal(new URL(first.url).hostname,'127.0.0.1');assert.equal(first.chatgptConnected,false);
 assert.ok(!output.includes(token)&&!output.includes(canary));
 return {child,url:first.url,async stop(){if(child.exitCode!==null)return;await new Promise(resolve=>{const t=setTimeout(()=>child.kill('SIGKILL'),3000);child.once('exit',()=>{clearTimeout(t);resolve();});child.kill('SIGTERM');});}};
}
let session=null,requestId=1;
async function post(method,params={},options={}){
 const headers={accept:'application/json, text/event-stream','content-type':'application/json',authorization:'Bearer '+token,
 ...(session?{'mcp-session-id':session,'mcp-protocol-version':'2025-06-18'}:{}),...options.headers};
 if(options.noAuth)delete headers.authorization;
 const before=performance.now();
 const r=await fetch(observer.url,{method:'POST',headers,body:JSON.stringify({jsonrpc:'2.0',...(!options.notification?{id:requestId++}:{}),method,params}),signal:AbortSignal.timeout(15000)});
 const raw=await r.text();report.timings.push(performance.now()-before);
 assert.ok(!raw.includes(canary)&&!raw.includes(token),'Private canary/token disclosed');
 let body;try{body=raw?JSON.parse(raw):null;}catch{body={raw};}
 return {status:r.status,headers:r.headers,body};
}
async function initialize(){
 session=null;const r=await post('initialize',{protocolVersion:'2025-06-18',capabilities:{},clientInfo:{name:'third-integration-review',version:'1'}});
 assert.equal(r.status,200);session=r.headers.get('mcp-session-id');assert.ok(session);
 assert.equal((await post('notifications/initialized',{}, {notification:true})).status,202);
}
async function tool(name,args={},ok=true){
 const r=await post('tools/call',{name,arguments:args});assert.equal(r.status,200);
 if(ok){assert.ok(r.body.result&&!r.body.result.isError,JSON.stringify(r.body));return r.body.result.structuredContent;}
 assert.ok(r.body.error||r.body.result?.isError,'Tool should reject');return r.body;
}
try {
 assert.equal(git(['status','--porcelain','--untracked-files=all']),'');
 fixture=fs.mkdtempSync(path.join(os.tmpdir(),'independent-service-'));fs.chmodSync(fixture,0o700);
 const project=path.join(fixture,'project'),profiles=path.join(fixture,'profiles'),auth=path.join(fixture,'auth'),classes=path.join(fixture,'classes');
 for(const d of [project,profiles,auth,classes])fs.mkdirSync(d,{mode:0o700});
 fs.mkdirSync(path.join(project,'src'));fs.writeFileSync(path.join(project,'src/value.mjs'),'export const value = 2;\n');
 git(['init','--quiet'],project);git(['add','.'],project);git(['-c','user.name=Independent QA','-c','user.email=qa@invalid','commit','--quiet','-m','Isolated metadata fixture; never executed'],project);
 const classpath=fs.readFileSync(opt['--classpath-file'],'utf8').trim();
 await check('real Kotlin20 seed, stable rename, private context retained on new JVM',()=>{
 run('javac',['-cp',classpath,'-d',classes,path.join(self,'ProfileFixture.java')]);
 for(const command of ['seed','verify'])run('java',['-Xmx128m','-cp',classes+path.delimiter+classpath,'ProfileFixture',profiles,command,canary]);
 });
 config=path.join(fixture,'state','config.json');
 await check('documented owner init and stopped no-cloud source-pinned status',()=>{
 const r=operate('init',['--project',project,'--profiles',profiles,'--classpath',classpath,'--runtime-env',opt['--runtime-env'],'--isolation-config',opt['--isolation-config'],'--max-queue','2','--max-cycles','2']);
 assert.equal(r.status,'INITIALIZED');const s=operate('status');assert.equal(s.state,'STOPPED');assert.equal(s.sourceCommit,report.sourceCommit);
 assert.equal(s.modelLoaded,false);assert.equal(s.cloudModelCallsAllowed,false);assert.equal(s.maxConcurrentInference,1);
 assert.equal(s.modelSha256,'cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046');
 assert.equal(s.runtimeSha256,'c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a');
 });
 const task={schema:'continuum-coding-task',version:1,id:'independent-cancel-only',sourceCommit:git(['rev-parse','HEAD'],project),kind:'BUGFIX',goal:'Synthetic cancellation-only admission fixture; this task must never execute.',allowedPaths:['src/value.mjs'],acceptance:['Controlled fixture value is 3'],chain:'node',limits:{maxModelCalls:1,maxContextChars:2048}};
 const taskFile=path.join(fixture,'task.json');fs.writeFileSync(taskFile,JSON.stringify(task),{mode:0o600});let review;
 await check('wrong owner review cannot grant, correct review deduplicates canonical task',()=>{
 review=operate('review',['--task',taskFile]);const before=tree(path.dirname(config));
 operate('submit',['--task',taskFile,'--approve','0'.repeat(64)],false);assert.equal(tree(path.dirname(config)),before);
 const submitted=operate('submit',['--task',taskFile,'--approve',review.reviewSha256]);assert.equal(submitted.executed,false);
 const twice=operate('submit',['--task',taskFile,'--approve',review.reviewSha256]);assert.equal(twice.duplicate,true);
 assert.equal(operate('tasks').items.length,1);assert.equal(operate('status').modelLoaded,false);
 });
 const tokenFile=path.join(auth,'token');
 await check('actual MCP init-token owner modes and no token stdout',()=>{
 const r=run(process.execPath,[mcpCli,'init-token','--token-file',tokenFile]);token=fs.readFileSync(tokenFile,'utf8').trim();
 assert.ok(token.length>=32&&!r.stdout.includes(token));assert.equal(fs.statSync(tokenFile).mode&0o777,0o600);
 });
 observer=await observerStart(tokenFile);
 await check('actual loopback MCP authentication and protocol session',async()=>{
 assert.equal((await post('initialize',{}, {noAuth:true})).status,401);
 assert.equal((await post('initialize',{}, {headers:{authorization:'Bearer wrong'}})).status,401);
 await initialize();const listing=await post('tools/list');assert.deepEqual(listing.body.result.tools.map(x=>x.name).sort(),['get_evidence','get_status','list_agents','list_tasks']);
 });
 await check('real paginated20 renamed metadata excludes private context and fabricated capability',async()=>{
 const all=[];let cursor;do{const p=await tool('list_agents',{limit:3,...(cursor?{cursor}:{})});all.push(...p.items);cursor=p.nextCursor;}while(cursor);
 assert.equal(all.length,20);assert.equal(new Set(all.map(x=>x.agentId)).size,20);
 const p=all.find(x=>x.agentId==='bot-forge');assert.equal(p.displayName,'Prüfwerkstatt');assert.equal(p.workspaceId,'workspace-bot-forge');
 for(const a of all){assert.equal(a.state,'REGISTERED');assert.equal(a.modelActive,false);assert.deepEqual(a.verifiedCapabilities,[]);assert.deepEqual(a.permissionScope,[]);assert.ok(!('contextObjectId'in a)&&!('conversationObjectId'in a));}
 });
 await check('readonly valid/negative MCP requests leave canonical state and profiles byte-identical',async()=>{
 const before=tree(path.dirname(config)),p=tree(profiles);
 const s=await tool('get_status');assert.equal(s.queued,1);assert.equal(s.modelLoaded,false);
 assert.equal((await tool('list_tasks')).items[0].taskId,task.id);
 for(const [name,args] of [['submit_task',{task}],['approve_task',{taskId:task.id}],['get_evidence',{taskId:task.id,evidenceId:'result'}],['get_evidence',{taskId:'../../config',evidenceId:'report'}],['list_agents',{limit:null}],['list_tasks',{limit:999}]])await tool(name,args,false);
 assert.equal(tree(path.dirname(config)),before);assert.equal(tree(profiles),p);
 });
 await check('owner cancellation persists through new observer and evidence never becomes success',async()=>{
 operate('cancel',['--task-id',task.id]);await observer.stop();observer=await observerStart(tokenFile);await initialize();
 const item=(await tool('list_tasks')).items[0];assert.equal(item.state,'ABORTED');assert.equal(item.grantCurrent,false);
 await tool('get_evidence',{taskId:task.id,evidenceId:'report'},false);assert.equal((await tool('get_status')).queued,0);
 });
 await observer.stop();observer=null;
 await check('real idle daemon start and duplicate admission retain one process',async()=>{
 const s=operate('start');serviceStarted=true;assert.equal(s.status,'STARTED');assert.equal(s.activeTaskId,null);assert.equal(s.modelLoaded,false);
 const a=JSON.parse(fs.readFileSync(path.join(path.dirname(config),'service.json')));
 const twice=operate('start');assert.equal(twice.status,'ALREADY_RUNNING');
 const b=JSON.parse(fs.readFileSync(path.join(path.dirname(config),'service.json')));assert.deepEqual(b.process,a.process);
 assert.equal(operate('tasks').items[0].state,'ABORTED');
 });
 await check('stop and restart preserve cancellation without model or receipt execution',async()=>{
 operate('stop');for(let i=0;i<50&&operate('status').state!=='STOPPED';i++)await sleep(100);
 assert.equal(operate('status').state,'STOPPED');serviceStarted=false;
 operate('start');serviceStarted=true;assert.equal(operate('tasks').items[0].state,'ABORTED');assert.equal(operate('status').queued,0);
 operate('stop');for(let i=0;i<50&&operate('status').state!=='STOPPED';i++)await sleep(100);
 assert.equal(operate('status').state,'STOPPED');serviceStarted=false;
 assert.ok(!fs.existsSync(path.join(path.dirname(config),'public-evidence',task.id+'.json')));
 const deliveries=JSON.parse(fs.readFileSync(path.join(path.dirname(config),'deliveries.json')));
 assert.ok(deliveries.items.every(d=>!d.workerProcess),'Cancelled-only service must not dispatch a model worker');
 const tasksDir=path.join(path.dirname(config),'ccc1','tasks');
 for(const name of fs.readdirSync(tasksDir)){
  const entries=fs.readFileSync(path.join(tasksDir,name),'utf8').trim().split('\n').map(JSON.parse);
  assert.ok(entries.every(e=>!['TEST_REQUEST','RECEIPT'].includes(e.kind)),'No executor receipt may be created in an idle-only acceptance');
 }
 });
 assert.equal(git(['status','--porcelain','--untracked-files=all']),'');report.status='PASS';
} catch(e){report.status='FAIL';report.failure=String(e.message).slice(0,1500);process.exitCode=1;}
finally{
 if(observer)await observer.stop();
 if(serviceStarted&&config){try{operate('stop');}catch{report.cleanup='STOP_FAILED';}}
 // Retain a failing fixture privately for investigation; never publish its token/config.
 if(fixture&&report.status==='PASS')fs.rmSync(fixture,{recursive:true,force:true});
 else if(fixture)report.privateFixturePath=fixture;
 const t=report.timings.toSorted((a,b)=>a-b);report.latencyMs={samples:t.length,p50:t[Math.floor(t.length*.5)]??null,p95:t[Math.min(t.length-1,Math.floor(t.length*.95))]??null,scope:'sequential HTTP calls; child JVM startup included; no inference'};delete report.timings;
 fs.writeFileSync(opt['--output'],JSON.stringify(report,null,2)+'\n');process.stdout.write(JSON.stringify(report,null,2)+'\n');
}
