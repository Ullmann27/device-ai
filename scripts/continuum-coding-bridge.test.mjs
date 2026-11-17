import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {prepareSyntheticWorkspace,inspectSyntheticWorkspace,runSyntheticCodingTrial,
  createExperientialTransport,verifySyntheticProposal,TRIAL_MODEL_IDS} from './continuum-coding-bridge.mjs';

const entry=fileURLToPath(new URL('./continuum-coding-bridge.mjs',import.meta.url));
async function isolate(action){
  const top=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-bridge-tests-'));
  const root=path.join(top,'public-fixture');
  try {prepareSyntheticWorkspace(root);return await action(root,top);} finally {fs.rmSync(top,{recursive:true,force:true});}
}
function tool(name,args,id){return {id,type:'function',function:{name,arguments:JSON.stringify(args)}};}
function mockTranscript({proposal=JSON.stringify({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'return a + b;'}),badTool=null}={}){
  let step=0;
  return async ()=>{
    step++;
    const toolCalls = step===1 ? [tool('read_file',{path:'src/add.mjs'},'a'),tool('read_file',{path:'test/add.test.mjs'},'b')]:
      step===2?[badTool??tool('run_test',{},'c')]:null;
    return {choices:[{message:toolCalls?{role:'assistant',content:null,tool_calls:toolCalls}:{role:'assistant',content:proposal}}],usage:{cost:0,is_byok:false}};
  };
}
test('free-only allowlist is precise; paid aliases cannot be selected',async()=>{
  assert.deepEqual(TRIAL_MODEL_IDS,['claude-fable-5.1:free','gpt-6-astra:free']);
  return await isolate(async root=>{
    await assert.rejects(runSyntheticCodingTrial({workspace:root,model:'claude-fable-5.1',transport:mockTranscript()}),/MODEL_NOT_FREE_ALLOWLISTED/);
    await assert.rejects(runSyntheticCodingTrial({workspace:root,model:'gpt-6-astra',transport:mockTranscript()}),/MODEL_NOT_FREE_ALLOWLISTED/);
  });
});
test('real synthetic file-read calls, fixed local Node test and proposal, no file patch',async()=>{
  await isolate(async root=>{
    const before=fs.readFileSync(path.join(root,'src/add.mjs'),'utf8');
    const report=await runSyntheticCodingTrial({workspace:root,transport:mockTranscript()});
    assert.equal(report.completed,true);
    assert.deepEqual(report.observedReadCalls,['src/add.mjs','test/add.test.mjs']);
    assert.equal(report.testResult.tests,3);
    assert.equal(report.testResult.pass,2);
    assert.equal(report.testResult.fail,1);
    assert.notEqual(report.testResult.exitCode,0);
    assert.match(report.proposal,/a \+ b/);
    assert.equal(report.proposalVerifiedOnIsolatedCopy,true);
    assert.equal(report.proposalReview.testResult.pass,3);
    assert.equal(report.proposalReview.testResult.fail,0);
    assert.equal(report.patchApplied,false);
    assert.equal(report.repositoryAccess,false);
    assert.equal(fs.readFileSync(path.join(root,'src/add.mjs'),'utf8'),before);
  });
});
test('matching Fable-style dry-run is not a verified remote inference',async()=>{
  await isolate(async root=>{
    const report=await runSyntheticCodingTrial({workspace:root,model:'gpt-6-astra:free',transport:mockTranscript()});
    assert.equal(report.mode,'SYNTHETIC_ONLY');
    assert.equal(report.model,'gpt-6-astra:free');
    assert.equal(report.usageCostVerified,false);
  });
});
test('tool path traversal and arbitrary shell are refused before execution',async()=>{
  for (const badTool of [tool('read_file',{path:'../../.ssh/id_rsa'},'x'),tool('run_shell',{command:'cat /etc/passwd'},'x'),tool('run_test',{command:'node malicious.js'},'x')]) {
    await isolate(async root=>{
      await assert.rejects(runSyntheticCodingTrial({workspace:root,transport:mockTranscript({badTool})}),
        /(?:FILE_NOT_ALLOWED|TOOL_NOT_ALLOWED|TEST_ARGUMENTS_NOT_ALLOWED)/);
    });
  }
});
test('symlinked file, mutated fixture, hidden added entries all fail closed',async()=>{
  await isolate(async root=>{
    fs.appendFileSync(path.join(root,'src/add.mjs'),'// malicious drift');
    assert.throws(()=>inspectSyntheticWorkspace(root),/SYNTHETIC_FIXTURE_DRIFT/);
  });
  await isolate(async root=>{
    const target=path.join(root,'src/add.mjs');
    fs.unlinkSync(target);fs.symlinkSync('/etc/passwd',target);
    assert.throws(()=>inspectSyntheticWorkspace(root),/SYNTHETIC_FIXTURE_DRIFT/);
  });
  await isolate(async root=>{
    fs.writeFileSync(path.join(root,'.env'),'PRIVATE=1');
    assert.throws(()=>inspectSyntheticWorkspace(root),/UNEXPECTED_WORKSPACE_CONTENT/);
  });
});
test('Git checkout cannot be processed as synthetic workspace',async()=>{
  await isolate(async (root,top)=>{
    fs.mkdirSync(path.join(top,'.git'));
    assert.throws(()=>inspectSyntheticWorkspace(root),/GIT_CHECKOUT_REFUSED/);
  });
});
test('response cannot silently charge, switch BYOK, or claim completed missing tools',async()=>{
  await isolate(async root=>{
    const paid=async()=>({choices:[{message:{role:'assistant',content:'I did it'}}],usage:{cost:0.0001}});
    await assert.rejects(runSyntheticCodingTrial({workspace:root,transport:paid}),/NONZERO_REPORTED_COST/);
    const byok=async()=>({choices:[{message:{role:'assistant',content:'I did it'}}],usage:{is_byok:true}});
    await assert.rejects(runSyntheticCodingTrial({workspace:root,transport:byok}),/BILLABLE_BYOK_NOT_ALLOWED/);
    const missing=async()=>({choices:[{message:{role:'assistant',content:'I have completed all the tests and read the file.'}}]});
    const report=await runSyntheticCodingTrial({workspace:root,transport:missing});
    assert.equal(report.completed,false);
  });
});
test('live transport refuses missing key and fixed URI is exact',async()=>{
  assert.throws(()=>createExperientialTransport({key:undefined}),/MISSING_OR_INVALID_EXPERIENTIAL_KEY/);
  assert.throws(()=>createExperientialTransport({key:'xpl_wrong'}),/MISSING_OR_INVALID_EXPERIENTIAL_KEY/);
  const fakeKey='xpl_'+'a'.repeat(40);
  let seen;
  const transport=createExperientialTransport({key:fakeKey,fetcher:async(url,opts)=>{
    seen={url,opts};return {ok:true,text:async()=>JSON.stringify({choices:[{message:{role:'assistant',content:'public synthetic only'}}]})};
  }});
  await transport({model:'claude-fable-5.1:free',messages:[],tools:[]});
  assert.equal(seen.url,'https://api.experientiallabs.ai/v1/chat/completions');
  assert.equal(seen.opts.redirect,'error');
  const body=JSON.parse(seen.opts.body);
  assert.equal(body.model,'claude-fable-5.1:free');
  assert.equal(body.max_tokens,1200);
  assert.doesNotMatch(JSON.stringify(body),new RegExp(fakeKey));
  await assert.rejects(transport({model:'claude-fable-5.1',messages:[],tools:[]}),/MODEL_NOT_FREE_ALLOWLISTED/);
});
test('real CLI denies network before explicit approval; does not print key',async()=>{
  const trial=await isolate(root=>{
    const r=spawnSync(process.execPath,[entry,'--trial',root,'fable'],{
      encoding:'utf8',env:{...process.env,CONTINUUM_EXPERIMENTIAL_TRIAL_APPROVED:'',EXPLABS_API_KEY:'xpl_'+'a'.repeat(40)},timeout:5000
    });
    assert.equal(r.status,2);
    assert.match(r.stderr,/EXPLICIT_SYNTHETIC_LIVE_APPROVAL_REQUIRED/);
    assert.doesNotMatch(r.stderr,/xpl_/);
    return r;
  });
  assert.equal(trial.stdout,'');
});
test('preparing a populated location is refused without modifying it',async()=>{
  await isolate((root)=>{
    assert.throws(()=>prepareSyntheticWorkspace(root),/WORKSPACE_ALREADY_EXISTS/);
    assert.equal(inspectSyntheticWorkspace(root).root,root);
  });
});

test('offline CLI proves fixture tooling without any provider key or internet',async()=>{
  await isolate(async root=>{
    const r=spawnSync(process.execPath,[entry,'--offline-proof',root],{
      encoding:'utf8',timeout:10000,env:{PATH:process.env.PATH||'/usr/bin:/bin',HOME:root}
    });
    assert.equal(r.status,0,r.stderr);
    const report=JSON.parse(r.stdout);
    assert.equal(report.transportKind,'MOCKED_OFFLINE');
    assert.equal(report.completed,true);
    assert.equal(report.observedTestCall,true);
    assert.equal(report.testResult.fail,1);
    assert.equal(report.repositoryAccess,false);
    assert.equal(report.usageCostVerified,false);
    assert.equal(report.patchApplied,false);
  });
});

test('only pinned one-line patch may be executed on disposable synthetic copy',async()=>{
  assert.throws(()=>verifySyntheticProposal('rm -rf /'),/PATCH_PROPOSAL_NOT_JSON/);
  assert.throws(()=>verifySyntheticProposal(JSON.stringify({path:'src/add.mjs',old:'return Math.abs(a) + Math.abs(b);',replacement:'process.exit(0);'})),/PATCH_PROPOSAL_NOT_ALLOWLISTED/);
  assert.throws(()=>verifySyntheticProposal(JSON.stringify({path:'../../app/AgentActivity.kt',old:'x',replacement:'y'})),/PATCH_PROPOSAL_NOT_ALLOWLISTED/);
  await isolate(async root=>{
    const before=fs.readFileSync(path.join(root,'src/add.mjs'),'utf8');
    await assert.rejects(runSyntheticCodingTrial({workspace:root,transport:mockTranscript({proposal:'I fixed the issue with a long prose response'})}),/PATCH_PROPOSAL_NOT_JSON/);
    assert.equal(fs.readFileSync(path.join(root,'src/add.mjs'),'utf8'),before);
  });
});
test('a model response claiming different identity is refused',async()=>{
  await isolate(async root=>{
    const transport=async()=>({model:'paid-provider/unknown',choices:[{message:{role:'assistant',content:'not allowed'}}]});
    await assert.rejects(runSyntheticCodingTrial({workspace:root,transport}),/UNEXPECTED_PROVIDER_MODEL/);
  });
});
