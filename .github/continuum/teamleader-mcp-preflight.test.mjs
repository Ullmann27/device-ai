import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {mcpPreflight,verifyToolEvidence,GIT_READONLY_SERVER} from './teamleader-mcp-preflight.mjs';
import {runTeamleaderCycle,decodeLeaderState} from './autonomous-teamleader.mjs';

function fixture(){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-tl-mcp-'));const root=path.join(dir,'repo'),state=path.join(dir,'state');
  fs.mkdirSync(root);fs.writeFileSync(path.join(root,'a.mjs'),'export const a=1;\n');
  const git=(...a)=>spawnSync('git',a,{cwd:root,encoding:'utf8'}).stdout.trim();
  git('init','-q');git('add','.');git('-c','user.name=T','-c','user.email=t@e.invalid','commit','-qm','x');
  return {dir,root,state,head:git('rev-parse','HEAD')};
}
const grant={ownerAuthenticated:true,sessionActive:true,devicePermissionGranted:true,allowedToolNames:['git_head','git_status']};
const approvedServers=[{command:fs.realpathSync(process.execPath),args:[GIT_READONLY_SERVER]}];
const driverFor=(root,calls)=>({head:r=>spawnSync('git',['rev-parse','HEAD'],{cwd:r,encoding:'utf8'}).stdout.trim(),
  repair:()=>{calls.repair++;throw Error('repair chain not configured in this test');}});

test('a real Teamleader cycle uses the MCP git tool result, records its receipt, and the next cycle does not repeat the work',async()=>{
  const f=fixture();const calls={repair:0};
  try{
    const evidence=await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers});
    assert.equal(evidence.head,f.head,'actual HEAD read through MCP');assert.equal(evidence.clean,true);
    assert.equal(verifyToolEvidence(evidence,f.head),true);
    const r1=runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),toolEvidence:evidence});
    assert.equal(r1.kind,'BASELINE_REPAIR');assert.equal(calls.repair,1,'the unchanged repair mechanism ran');
    const st=decodeLeaderState(fs.readFileSync(path.join(f.state,'teamleader-state.json')));
    const ev=st.events.at(-1);assert.equal(ev.head,f.head);assert.deepEqual(ev.tools.receipts,evidence.receipts.map(x=>x.resultSha256));
    const r2=runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),
      toolEvidence:await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers})});
    assert.equal(r2.outcome,'IDLE');assert.equal(calls.repair,1,'same HEAD is not repaired twice');
    assert.equal(fs.readFileSync(path.join(f.state,'mcp-preflight.jsonl'),'utf8').trim().split('\n').length,2,'journalled outside the repo');
  }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});

test('fail closed: tampered or foreign evidence starts no work; unapproved server or revoked grant yields no evidence',async()=>{
  const f=fixture();const calls={repair:0};
  try{
    const evidence=await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers});
    for(const bad of [{...evidence,head:'f'.repeat(40)},{...evidence,clean:false},{...evidence,receipts:evidence.receipts.slice(0,1)}])
      assert.throws(()=>runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),toolEvidence:bad}),/MCP_TOOL_EVIDENCE_INVALID/);
    const swapped={...evidence,receipts:[evidence.receipts[1],evidence.receipts[0]]};
    assert.throws(()=>runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),toolEvidence:swapped}),/MCP_TOOL_EVIDENCE_INVALID/);
    fs.writeFileSync(path.join(f.root,'dirty.txt'),'x');
    const dirty=await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers});
    assert.equal(dirty.clean,false);
    assert.throws(()=>runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),toolEvidence:dirty}),/MCP_TOOL_EVIDENCE_INVALID/,'dirty tree is no approved base');
    fs.rmSync(path.join(f.root,'dirty.txt'));
    assert.equal(calls.repair,0);
    await assert.rejects(mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers:[]}),/MCP_SERVER_NOT_APPROVED/);
    await assert.rejects(mcpPreflight({root:f.root,stateDir:f.state,grant:{...grant,allowedToolNames:['git_head']},approvedServers}),/MCP_TOOL_DENIED/);
    await assert.rejects(mcpPreflight({root:f.root,stateDir:path.join(f.root,'state'),grant,approvedServers}),/outside the repository/);
  }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});

test('resume: a cycle that died while RUNNING is marked INTERRUPTED on the next cycle and not reported as success',async()=>{
  const f=fixture();const calls={repair:0};
  try{
    const crash={head:r=>spawnSync('git',['rev-parse','HEAD'],{cwd:r,encoding:'utf8'}).stdout.trim(),
      repair:()=>{const st=decodeLeaderState(fs.readFileSync(path.join(f.state,'teamleader-state.json')));
        assert.equal(st.events.at(-1).outcome,'RUNNING','RUNNING persisted before work');
        fs.copyFileSync(path.join(f.state,'teamleader-state.json'),path.join(f.dir,'snapshot.json'));throw Error('x');}};
    runTeamleaderCycle({root:f.root,stateDir:f.state,driver:crash,toolEvidence:await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers})});
    // Simulate process death during work: restore the state as it was while RUNNING.
    fs.copyFileSync(path.join(f.dir,'snapshot.json'),path.join(f.state,'teamleader-state.json'));
    runTeamleaderCycle({root:f.root,stateDir:f.state,driver:driverFor(f.root,calls),toolEvidence:await mcpPreflight({root:f.root,stateDir:f.state,grant,approvedServers})});
    const st=decodeLeaderState(fs.readFileSync(path.join(f.state,'teamleader-state.json')));
    assert.ok(st.events.some(e=>e.outcome==='INTERRUPTED'));assert.equal(calls.repair,0,'interrupted head is not silently re-run');
  }finally{fs.rmSync(f.dir,{recursive:true,force:true});}
});
