import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {canonicalFixture} from './canonical-fixture.mjs';
import {WireClient,toolResult,denied} from './http-probe.mjs';

const candidate=path.resolve(process.env.CONTINUUM_SERVICE_CANDIDATE??'.');
const classpath=process.env.GENESIS_HANDOFF_CLASSPATH;
const runtimeEnvPath=process.env.CONTINUUM_REVIEW_RUNTIME_ENV;
const {startCodingMcpServer,createCodingMcpToken}=await import(pathToFileURL(path.join(candidate,'.github/continuum/coding-mcp-server.mjs')));
const {createCodingMcpReader}=await import(pathToFileURL(path.join(candidate,'.github/continuum/coding-mcp-reader.mjs')));

test('D10: real HTTP -> fixed subprocess -> canonical CCC1 and actual persisted Kotlin profiles',async()=>{
  const f=await canonicalFixture({candidate,classpath,runtimeEnvPath});let server;
  try{
    const privateDir=path.join(f.root,'credential');fs.mkdirSync(privateDir,{mode:0o700});
    const tokenFile=path.join(privateDir,'token');createCodingMcpToken({tokenFile});
    const reader=createCodingMcpReader({configPath:f.configPath});
    server=await startCodingMcpServer({reader,tokenFile,protectedRoots:[candidate,f.project,f.profiles]});
    const client=new WireClient(server.url,fs.readFileSync(tokenFile,'utf8').trim());await client.ready();
    const call=async(name,args={})=>{const r=await client.call(name,args);assert.equal(r.status,200);assert.equal(r.json?.result?.isError,false,r.text);return toolResult(r);};
    const status=await call('get_status');assert.equal(status.state,'STOPPED');assert.equal(status.deviceVerified,false);
    const agents=await call('list_agents',{limit:20});assert.equal(agents.items.length,20);
    assert.equal(agents.items.find(p=>p.agentId==='bot-forge').workspaceId,'workspace-bot-forge');
    const tasks=await call('list_tasks',{limit:1});assert.equal(tasks.items[0].state,'ABSTAINED');
    assert.equal(tasks.items[0].builderId,'bot-forge');assert.equal(tasks.items[0].reporterId,'bot-echo');
    for(const evidenceId of ['result','report']){
      const evidence=await call('get_evidence',{taskId:f.task.id,evidenceId});
      assert.equal(evidence.content.status,'ABSTAINED');assert.equal(evidence.sha256,f.sha256Hex(JSON.stringify(evidence.content)));
      assert.equal(evidence.content.approvedForMerge,false);
    }
    const before=f.sha256Hex(fs.readFileSync(f.journal.file));
    await call('get_status');assert.equal(f.sha256Hex(fs.readFileSync(f.journal.file)),before);
    f.append('OWNER','ABORT',{reason:'Synthetic review revocation',code:'OWNER'});
    assert.ok(denied(await client.call('get_evidence',{taskId:f.task.id,evidenceId:'result'})));
    const now=await call('list_tasks',{limit:1});assert.equal(now.items[0].state,'ABORTED');assert.equal(now.items[0].grantCurrent,false);
    assert.equal(server.getMetrics().activeReads,0);
  }finally{await server?.close();f.cleanup();}
});
