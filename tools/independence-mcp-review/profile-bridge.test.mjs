import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath,pathToFileURL} from 'node:url';

const here=path.dirname(fileURLToPath(import.meta.url));
const candidate=path.resolve(process.env.CONTINUUM_PROFILE_CANDIDATE??process.env.CONTINUUM_MCP_CANDIDATE??'.');
const cp=process.env.GENESIS_HANDOFF_CLASSPATH;
if(!cp)throw Error('Set GENESIS_HANDOFF_CLASSPATH to compiled exact-source profile bridge');
const {profileStoreReader}=await import(pathToFileURL(path.join(candidate,'tools/genesis-workspaces/adapter-bridge.mjs')));
const PRIVATE='synthetic-working-notes-not-for-MCP-84a09';
const run=(cmd,args)=>{const r=spawnSync(cmd,args,{encoding:'utf8',shell:false,timeout:15000,maxBuffer:1048576});assert.equal(r.status,0,r.error?.message||r.stderr||r.stdout);return r.stdout;};
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

test('actual Kotlin profile bridge:20 identities, fresh rename, no private context, strict pagination',async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'mcp-profile-review-'));
  try{
    const classes=path.join(root,'classes');fs.mkdirSync(classes);
    run('javac',['-cp',cp,'-d',classes,path.join(here,'ProfileFixture.java')]);
    const directory=path.join(root,'profiles');
    const setup=(...args)=>run('java',['-Xmx128m','-cp',classes+path.delimiter+cp,'ProfileFixture',directory,...args]);
    setup('seed');
    const reader=profileStoreReader(directory,{classpath:cp});
    const original=reader.get('bot-forge');assert.equal(original.displayName.toUpperCase(),'FORGE');
    assert.equal(original.workspaceId,'workspace-bot-forge');
    assert.deepEqual(original.permissionScope,[]);assert.deepEqual(original.verifiedCapabilities,[]);
    assert.equal(original.lifecycleState,'REGISTERED');
    assert.equal(reader.get('forge'),null,'Unprefixed display role must not fabricate a second identity');
    const ids=[];let afterAgentId=null;
    for(let i=0;i<10;i++){
      const page=reader.page({afterAgentId,limit:3});assert.ok(page.items.length<=3);
      ids.push(...page.items.map(item=>item.agentId));
      if(page.nextAfterId===null)break;
      assert.equal(page.nextAfterId,page.items.at(-1).agentId);afterAgentId=page.nextAfterId;
    }
    assert.equal(ids.length,20);assert.equal(new Set(ids).size,20);assert.deepEqual(ids,ids.toSorted());
    for(const limit of [0,-1,101,1.2,'2',null])assert.throws(()=>reader.page({limit}));
    for(const id of ['../private','/tmp/data','x\u0000y'])assert.throws(()=>reader.get(id));
    setup('private-context',PRIVATE);setup('rename','Forge Review Name');
    const changed=reader.get('bot-forge');assert.equal(changed.displayName,'Forge Review Name');
    assert.equal(changed.agentId,original.agentId);assert.equal(changed.workspaceId,original.workspaceId);
    assert.equal(changed.profileVersion,original.profileVersion+2);
    assert.deepEqual(changed.permissionScope,original.permissionScope);
    assert.deepEqual(changed.verifiedCapabilities,original.verifiedCapabilities);
    const primary=path.join(directory,'bot-profiles-v1.bin'),before=hash(primary);
    const visible=JSON.stringify([changed,reader.page({limit:20})]);
    for(const forbidden of [PRIVATE,'contextObjectId','conversationObjectId','conversationVersion'])assert.ok(!visible.includes(forbidden),forbidden+' leaked');
    assert.equal(hash(primary),before,'Read bridge altered profile data');
  }finally{fs.rmSync(root,{recursive:true,force:true});}
});
