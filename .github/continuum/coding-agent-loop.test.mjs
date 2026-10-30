import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createAgentCandidateProvider,createScriptedBrain,createChatCompletionsBrain,createScratchTestRunner,
  createAgentProviderFromEnv,minimalPatch,isReadablePath} from './coding-agent-loop.mjs';
import {createWorktree,removeWorktree,repairSession} from './repair-loop.mjs';

const SECRET='TOP-SECRET-VALUE-4711';
function fixture(){
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-agent-loop-'));
  const root=path.join(dir,'repo');
  const write=(file,text)=>{fs.mkdirSync(path.dirname(path.join(root,file)),{recursive:true});fs.writeFileSync(path.join(root,file),text);};
  // One function per file: the host may execute only the reviewed numeric grammar without an isolated executor.
  write('lib/geometry.mjs','export function distance(a,b){return a-b;}\n');
  write('lib/other.mjs','export const unrelated=1;\n');
  write('secrets/key.mjs',`export const key='${SECRET}';\n`);
  write('.github/continuum/geometry.test.mjs',"import test from 'node:test';import assert from 'node:assert/strict';\n"+
    "import {distance} from '../../lib/geometry.mjs';\n"+
    "test('distance is symmetric',()=>{assert.equal(distance(8,3),5);assert.equal(distance(3,8),5);});\n"+
    "test('distance of equal points',()=>assert.equal(distance(4,4),0));\n");
  const git=(...args)=>{const p=spawnSync('git',args,{cwd:root,encoding:'utf8',shell:false});assert.equal(p.status,0,p.stderr);return p.stdout.trim();};
  git('init','-q');git('add','.');git('-c','user.name=Test','-c','user.email=test@example.invalid','commit','-qm','baseline with bug');
  const head=git('rev-parse','HEAD');
  const task={schema:'continuum-coding-task',version:1,id:'agent-distance',sourceCommit:head,kind:'BUGFIX',
    goal:'distance(a,b) must be the non-negative distance for any order of a and b',allowedPaths:['lib/geometry.mjs'],
    acceptance:['distance(3,8) equals 5'],chain:'node',limits:{maxModelCalls:3,maxContextChars:6000}};
  return {dir,root,head,task};
}
const cleanup=f=>fs.rmSync(f.dir,{recursive:true,force:true});

// A scripted run that behaves like the method in AGENT_SYSTEM_PROMPT: locate, read test and source, edit, check, test, finish.
const competent=[
  {tool:'search',args:{pattern:'distance',path:''}},
  {tool:'read_file',args:{path:'.github/continuum/geometry.test.mjs'}},
  {tool:'read_file',args:{path:'lib/geometry.mjs',start_line:1,end_line:3}},
  {tool:'edit_file',args:{path:'lib/geometry.mjs',old_text:'return a-b;',new_text:'return a>b?a-b:b-a;'}},
  {tool:'check',args:{}},
  {tool:'run_tests',args:{}},
  {tool:'finish',args:{plan:['Read the symmetric test','Return the absolute difference'],hypothesis:'a-b is negative when b>a'}}
];

test('minimalPatch yields the smallest unique whole-line span and refuses what the session could not apply',()=>{
  const before='a\nx=1\nb\nx=1\nc\n';
  const changed=minimalPatch(before,'a\nx=1\nb\nx=2\nc\n');
  assert.equal(before.split(changed.oldText).length,2,'widened until unique');
  assert.equal(before.replace(changed.oldText,()=>changed.newText),'a\nx=1\nb\nx=2\nc\n');
  assert.equal(minimalPatch(before,before),null);
  const inserted=minimalPatch('one\ntwo\n','one\nnew\ntwo\n');
  assert.equal('one\ntwo\n'.replace(inserted.oldText,()=>inserted.newText),'one\nnew\ntwo\n');
  assert.equal(minimalPatch('a\n'.repeat(10),'b\n'.repeat(10),8).error,'PATCH_TOO_LARGE');
  assert.equal(minimalPatch('keep\n','\f\n').error,'PATCH_NOT_EXPRESSIBLE');
});

test('read access excludes VCS internals, build output, secrets and owner data',()=>{
  for(const bad of ['.git/config','../x.mjs','/etc/passwd','secrets/key.mjs','app/build/x.kt','local.properties','owner-data/a.json','a/./b.mjs','x\\y.mjs'])
    assert.equal(isReadablePath(bad),false,bad);
  for(const ok of ['lib/geometry.mjs','.github/continuum/geometry.test.mjs','app/src/main/java/A.kt','README.md'])
    assert.equal(isReadablePath(ok),true,ok);
});

test('the agent loop inspects, edits, checks and tests, and the unchanged repair session verifies its proposal',()=>{
  const f=fixture(),wt=createWorktree(f.root,f.head);
  const seen=[];
  try{
    const runner=createScratchTestRunner({wt,base:f.head,chain:'node'});
    try{
      const provider=createAgentCandidateProvider({task:f.task,brain:createScriptedBrain(competent),runTests:runner.runTests,
        onStep:s=>seen.push(s)});
      const report=repairSession({wt,base:f.head,chain:'node',out:path.join(f.dir,'report'),candidateProvider:provider});
      assert.equal(report.outcome,'VERIFIED');
      const tests=seen.find(s=>s.tool==='run_tests').observation;
      assert.match(tests,/ALL PASSED\nnode-continuum: PASSED executed=2 failed=0/);
      assert.match(seen.find(s=>s.tool==='check').observation,/syntax OK/);
      assert.match(seen.find(s=>s.tool==='search').observation,/lib\/geometry\.mjs:1:/);
      assert.ok(!seen.some(s=>String(s.observation??'').includes(SECRET)));
    }finally{runner.close();}
  }finally{removeWorktree(f.root,wt);cleanup(f);}
});

test('tools refuse escapes, secrets, foreign files and ambiguous edits without leaking content',()=>{
  const f=fixture(),wt=createWorktree(f.root,f.head);
  const seen=[];
  try{
    const brain=createScriptedBrain([
      {tool:'read_file',args:{path:'secrets/key.mjs'}},
      {tool:'read_file',args:{path:'../repo/secrets/key.mjs'}},
      {tool:'read_file',args:{path:'.git/config'}},
      {tool:'search',args:{pattern:'TOP-SECRET'}},
      {tool:'list_files',args:{dir:'secrets'}},
      {tool:'edit_file',args:{path:'lib/other.mjs',old_text:'1',new_text:'2'}},
      {tool:'edit_file',args:{path:'lib/geometry.mjs',old_text:'a',new_text:'x'}},
      {tool:'search',args:{pattern:'x'.repeat(300)}},
      {tool:'finish',args:{plan:['nothing'],hypothesis:'none'}},
      {tool:'edit_file',args:{path:'lib/geometry.mjs',old_text:'return a-b;',new_text:'return Math.abs(a-b);'}},
      {tool:'finish',args:{plan:['abs'],hypothesis:'sign'}}]);
    const provider=createAgentCandidateProvider({task:f.task,brain,onStep:s=>seen.push(s)});
    const raw=provider({wt,base:f.head,deadline:Date.now()+60000,diagnostics:[],history:[]});
    const obs=seen.map(s=>String(s.observation??''));
    assert.ok(!obs.some(o=>o.includes(SECRET)),'no observation reveals the secret');
    assert.match(obs[0],/Not a readable/);assert.match(obs[1],/Not a readable/);assert.match(obs[2],/Not a readable/);
    assert.match(obs[3],/No matches/);assert.match(obs[4],/not readable|No readable/);
    assert.match(obs[5],/Edit refused: only lib\/geometry\.mjs/);
    assert.match(obs[6],/occurs 3 times/);
    assert.match(obs[7],/Invalid pattern/);
    assert.match(obs[8],/Finish refused:\nNo change yet/);
    assert.deepEqual(raw.patches,[{file:'lib/geometry.mjs',oldText:'export function distance(a,b){return a-b;}',newText:'export function distance(a,b){return Math.abs(a-b);}'}]);
    assert.equal(raw.mode,'LOCAL_CODING_PATCH_PROPOSAL_V1');assert.equal(raw.trusted,false);assert.equal(raw.autoApply,false);
    assert.equal(raw.context.commit,f.head);assert.match(raw.context.contextSha256,/^[a-f0-9]{64}$/);
    assert.equal(raw.agent.stopReason,'FINISH');assert.equal(raw.agent.brainCalls,11);
  }finally{removeWorktree(f.root,wt);cleanup(f);}
});

test('budgets, invalid replies, deadlines and task binding stop the loop with known session codes',()=>{
  const f=fixture(),wt=createWorktree(f.root,f.head);
  const call=(brain,extra={},input={})=>createAgentCandidateProvider({task:f.task,brain,...extra})(
    {wt,base:f.head,deadline:Date.now()+60000,diagnostics:[],history:[],...input});
  const code=fn=>{try{fn();return 'NO_ERROR';}catch(e){return e.code??e.message;}};
  try{
    assert.equal(code(()=>call(()=>({tool:'check',args:{}}),{limits:{maxSteps:4}})),'CODING_MODEL_BUDGET_EXHAUSTED');
    assert.equal(code(()=>call(()=>'not json')),'CODING_RESPONSE_INVALID');
    assert.equal(code(()=>call(()=>({tool:'rm -rf',args:{}}))),'CODING_RESPONSE_INVALID');
    assert.equal(code(()=>call(()=>({tool:'check',args:{}}),{},{deadline:Date.now()-1})),'CODING_DEADLINE_EXHAUSTED');
    assert.equal(code(()=>call(()=>({tool:'check',args:{}}),{},{base:'b'.repeat(40)})),'CODING_TASK_BINDING_MISMATCH');
    let testCalls=0;
    const seen=[];
    call(createScriptedBrain([competent[3],{tool:'run_tests',args:{}},{tool:'run_tests',args:{}},{tool:'run_tests',args:{}},competent[6]]),
      {runTests:()=>{testCalls++;return {passed:false,summary:'node-continuum: FAILED'};},onStep:s=>seen.push(s)});
    assert.equal(testCalls,2,'run_tests is bounded');
    assert.match(seen[3].observation,/Test budget exhausted/);
  }finally{removeWorktree(f.root,wt);cleanup(f);}
});

test('the chat brain is local by default, needs consent for remote endpoints and maps failures to session codes',()=>{
  assert.throws(()=>createChatCompletionsBrain({endpoint:'https://example.invalid/v1/chat/completions'}),/CODING_REMOTE_BRAIN_NOT_CONSENTED/);
  let sent;
  const fake=(cmd,args,opts)=>{sent=JSON.parse(opts.input);return {stdout:JSON.stringify({status:200,
    text:JSON.stringify({choices:[{message:{content:'{"tool":"check","args":{}}'}}]})})};};
  const brain=createChatCompletionsBrain({run:fake});
  assert.equal(brain({system:'S',messages:[{role:'user',content:'U'}]}),'{"tool":"check","args":{}}');
  assert.equal(sent.url,'http://127.0.0.1:8089/v1/chat/completions');
  assert.equal(sent.body.messages[0].role,'system');assert.equal(sent.body.response_format.type,'json_object');
  const failing=status=>createChatCompletionsBrain({run:()=>({stdout:JSON.stringify({status,text:status===0?'TimeoutError':'busy'})})});
  assert.throws(()=>failing(0)({system:'',messages:[]}),/CODING_MODEL_TIMEOUT/);
  assert.throws(()=>failing(503)({system:'',messages:[]}),/CODING_MODEL_UNAVAILABLE/);
});

test('run_tests keeps the session execution policy: a general edit does not run on the host without isolation',()=>{
  const f=fixture(),wt=createWorktree(f.root,f.head);
  const seen=[];
  try{
    const runner=createScratchTestRunner({wt,base:f.head,chain:'node'});
    try{
      const general=[competent[2],{tool:'edit_file',args:{path:'lib/geometry.mjs',old_text:'return a-b;',new_text:'return Math.abs(a-b);'}},
        {tool:'run_tests',args:{}},competent[6]];
      const wrong=[{tool:'edit_file',args:{path:'lib/geometry.mjs',old_text:'return a-b;',new_text:'return b-a;'}},{tool:'run_tests',args:{}},competent[6]];
      const provider=createAgentCandidateProvider({task:f.task,brain:createScriptedBrain(general),runTests:runner.runTests,onStep:s=>seen.push(s)});
      const report=repairSession({wt,base:f.head,chain:'node',out:path.join(f.dir,'report'),candidateProvider:provider});
      assert.match(seen.find(s=>s.tool==='run_tests').observation,/NOT RUN\nBLOCKED/);
      assert.equal(report.attempts[0].outcome,'REJECTED_BY_EXECUTION_POLICY','the session blocks the same edit');
      seen.length=0;
      createAgentCandidateProvider({task:f.task,brain:createScriptedBrain(wrong),runTests:runner.runTests,onStep:s=>seen.push(s)})(
        {wt,base:f.head,deadline:Date.now()+60000,diagnostics:[],history:[]});
      const failed=seen.find(s=>s.tool==='run_tests').observation;
      assert.match(failed,/^FAILED\nnode-continuum: FAILED executed=2 failed=1/);
      assert.match(failed,/geometry\.test\.mjs/,'failing test location reaches the brain');
    }finally{runner.close();}
  }finally{removeWorktree(f.root,wt);cleanup(f);}
});

test('the orchestrator factory needs the local brain key, passes it on stdin only and records each step',()=>{
  const f=fixture(),wt=createWorktree(f.root,f.head);
  try{
    assert.throws(()=>createAgentProviderFromEnv({task:f.task,env:{}}),/LOCAL_AUTH_REQUIRED/);
    const key='k'.repeat(40);
    assert.equal(createAgentProviderFromEnv({task:f.task,env:{CONTINUUM_LOCAL_BRAIN_API_KEY:key}}).taskSpec.id,f.task.id);
    let seenArgs,seenInput;
    const brain=createChatCompletionsBrain({apiKey:key,run:(cmd,args,opts)=>{seenArgs=args;seenInput=opts.input;
      return {stdout:JSON.stringify({status:200,text:JSON.stringify({choices:[{message:{content:'{"tool":"check","args":{}}'}}]})})};}});
    brain({system:'',messages:[]});
    assert.ok(!seenArgs.join(' ').includes(key),'key never in argv');assert.equal(JSON.parse(seenInput).key,key);
    const evidenceDir=path.join(f.dir,'evidence');
    createAgentCandidateProvider({task:f.task,brain:createScriptedBrain([competent[3],competent[6]]),evidenceDir})(
      {wt,base:f.head,deadline:Date.now()+60000,diagnostics:[],history:[]});
    const lines=fs.readFileSync(path.join(evidenceDir,'agent-run-1.jsonl'),'utf8').trim().split('\n').map(l=>JSON.parse(l));
    assert.deepEqual(lines.map(l=>l.tool),['edit_file','finish']);
    assert.ok(!fs.readFileSync(path.join(evidenceDir,'agent-run-1.jsonl'),'utf8').includes(key));
  }finally{removeWorktree(f.root,wt);cleanup(f);}
});
