#!/usr/bin/env node
/** Optional, standalone, public-fixture-only coding gateway trial.
 * No repository access, installs, patch application or credential storage.
 * Production CCC1 and the existing health worker are deliberately untouched.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export const TRIAL_MODEL_IDS = Object.freeze([
  'claude-fable-5.1:free', 'gpt-6-astra:free'
]);
const API_URL = 'https://api.experientiallabs.ai/v1/chat/completions';
const MARKER = 'CONTINUUM_PUBLIC_SYNTHETIC_CODING_V1\n';
const SOURCE = `export function sumPositive(a, b) {
  if (!Number.isFinite(a) || !Number.isFinite(b)) throw new TypeError('finite numbers only');
  return Math.abs(a) + Math.abs(b);
}
`;
const TEST = `import test from 'node:test';
import assert from 'node:assert/strict';
import {sumPositive} from '../src/add.mjs';
test('positive integers', () => assert.equal(sumPositive(2, 3), 5));
test('mixed sign should preserve signed sum', () => assert.equal(sumPositive(-2, 3), 1));
test('reject nonfinite inputs', () => assert.throws(() => sumPositive(NaN, 1), TypeError));
`;
const SAFE_PATCH = Object.freeze({
  path:'src/add.mjs',
  old:'return Math.abs(a) + Math.abs(b);',
  replacement:'return a + b;'
});
const FILES = Object.freeze({
  'src/add.mjs':SOURCE,
  'test/add.test.mjs':TEST
});
const digest = text => crypto.createHash('sha256').update(text).digest('hex');
const isPlain = value => value !== null && typeof value === 'object' && !Array.isArray(value);

function inspectParentTree(target) {
  let cursor = target;
  while (true) {
    if (fs.existsSync(path.join(cursor, '.git'))) throw Error('GIT_CHECKOUT_REFUSED');
    const parent = path.dirname(cursor);
    if (parent === cursor) break;
    cursor = parent;
  }
}
export function inspectSyntheticWorkspace(directory) {
  const absolute = path.resolve(directory);
  if (!fs.existsSync(absolute) || !fs.lstatSync(absolute).isDirectory() ||
      fs.lstatSync(absolute).isSymbolicLink()) throw Error('WORKSPACE_UNAVAILABLE');
  const real = fs.realpathSync(absolute);
  if (real !== absolute) throw Error('WORKSPACE_SYMLINK_REFUSED');
  inspectParentTree(real);
  const entries = fs.readdirSync(real).sort();
  if (JSON.stringify(entries) !== JSON.stringify(['CONTINUUM_SYNTHETIC_ONLY.txt','src','test']))
    throw Error('UNEXPECTED_WORKSPACE_CONTENT');
  const markerPath = path.join(real,'CONTINUUM_SYNTHETIC_ONLY.txt');
  if (fs.lstatSync(markerPath).isSymbolicLink() ||
      fs.readFileSync(markerPath,'utf8') !== MARKER) throw Error('WORKSPACE_MARKER_INVALID');
  for (const [relative, content] of Object.entries(FILES)) {
    const parent = path.join(real,path.dirname(relative));
    const p = path.join(real,relative);
    if (fs.lstatSync(parent).isSymbolicLink() ||
        !fs.lstatSync(parent).isDirectory() ||
        JSON.stringify(fs.readdirSync(parent).sort()) !== JSON.stringify([path.basename(relative)]))
      throw Error('UNEXPECTED_WORKSPACE_CONTENT');
    const stat = fs.lstatSync(p);
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 16384 ||
        fs.readFileSync(p,'utf8') !== content) throw Error('SYNTHETIC_FIXTURE_DRIFT');
  }
  return Object.freeze({root:real, fixtureSha256:digest(MARKER+SOURCE+TEST)});
}
export function prepareSyntheticWorkspace(directory) {
  const absolute = path.resolve(directory);
  if (fs.existsSync(absolute)) throw Error('WORKSPACE_ALREADY_EXISTS');
  inspectParentTree(path.dirname(absolute));
  fs.mkdirSync(absolute,{recursive:false,mode:0o700});
  try {
    for (const relative of Object.keys(FILES))
      fs.mkdirSync(path.join(absolute,path.dirname(relative)),{mode:0o700});
    fs.writeFileSync(path.join(absolute,'CONTINUUM_SYNTHETIC_ONLY.txt'),MARKER,{flag:'wx',mode:0o600});
    for (const [relative,content] of Object.entries(FILES))
      fs.writeFileSync(path.join(absolute,relative),content,{flag:'wx',mode:0o600});
    return inspectSyntheticWorkspace(absolute);
  } catch (e) {
    // A failed preparation only erases its own newly-created synthetic folder.
    fs.rmSync(absolute,{recursive:true,force:true});
    throw e;
  }
}
function validateModel(model) {
  if (!TRIAL_MODEL_IDS.includes(model)) throw Error('MODEL_NOT_FREE_ALLOWLISTED');
  return model;
}
function requiredToolCall(call) {
  if (!isPlain(call) || call.type !== 'function' || !isPlain(call.function) ||
      typeof call.id !== 'string' || !/^[\w-]{1,80}$/.test(call.id)) throw Error('INVALID_TOOL_CALL');
  if (!['read_file','run_test'].includes(call.function.name)) throw Error('TOOL_NOT_ALLOWED');
  if (typeof call.function.arguments !== 'string' || call.function.arguments.length > 2048)
    throw Error('INVALID_TOOL_ARGUMENTS');
  let args;
  try { args = JSON.parse(call.function.arguments); }
  catch { throw Error('INVALID_TOOL_ARGUMENTS'); }
  if (!isPlain(args)) throw Error('INVALID_TOOL_ARGUMENTS');
  if (call.function.name === 'read_file') {
    if (Object.keys(args).join(',') !== 'path' || !Object.hasOwn(FILES,args.path))
      throw Error('FILE_NOT_ALLOWED');
  } else if (Object.keys(args).length !== 0) throw Error('TEST_ARGUMENTS_NOT_ALLOWED');
  return Object.freeze({id:call.id,name:call.function.name,args});
}
const toolDefinitions = Object.freeze([
  {type:'function',function:{name:'read_file',description:'Read one permitted public synthetic source or test file',parameters:{type:'object',properties:{path:{type:'string',enum:Object.keys(FILES)}},required:['path'],additionalProperties:false}}},
  {type:'function',function:{name:'run_test',description:'Run only the fixed public Node regression test',parameters:{type:'object',properties:{},additionalProperties:false}}}
]);

function runFixedTest(root) {
  const env = {PATH:process.env.PATH||'/usr/bin:/bin',LANG:'C',HOME:root,NODE_OPTIONS:''};
  const result = spawnSync(process.execPath,['--test','--test-reporter=tap','test/add.test.mjs'],{
    cwd:root,encoding:'utf8',env,shell:false,timeout:7000,maxBuffer:32768,
    stdio:['ignore','pipe','pipe']
  });
  const out = String(result.stdout||'');
  const numbers = Object.fromEntries([...out.matchAll(/^# (tests|pass|fail|cancelled|skipped|todo) (\d+)$/gm)].map(m=>[m[1],Number(m[2])]));
  if (result.error || result.signal || !Number.isSafeInteger(numbers.tests) || numbers.tests===0 ||
      !Number.isSafeInteger(numbers.pass) || !Number.isSafeInteger(numbers.fail) ||
      !Number.isSafeInteger(numbers.cancelled)) throw Error('TEST_RUN_UNVERIFIABLE');
  return Object.freeze({exitCode:result.status,tests:numbers.tests,pass:numbers.pass,
    fail:numbers.fail,cancelled:numbers.cancelled,stdoutSha256:digest(out),
    // The model sees only public synthetic evidence, not host paths or terminal stderr.
    summary:`tests=${numbers.tests} pass=${numbers.pass} fail=${numbers.fail} exit=${result.status}`});
}
/** Candidate code is NEVER run directly: only this exact prereviewed one-line
 * replacement is accepted. The test runs on a newly generated, disposable
 * public fixture, not on the original file, Git checkout or owner's device.
 */
export function verifySyntheticProposal(candidate) {
  if (typeof candidate !== 'string' || candidate.length > 4000) throw Error('INVALID_PATCH_PROPOSAL');
  let parsed;
  try { parsed=JSON.parse(candidate); } catch { throw Error('PATCH_PROPOSAL_NOT_JSON'); }
  if (!isPlain(parsed) || Object.keys(parsed).sort().join(',') !== 'old,path,replacement' ||
      Object.keys(SAFE_PATCH).some(k=>parsed[k]!==SAFE_PATCH[k])) throw Error('PATCH_PROPOSAL_NOT_ALLOWLISTED');
  const oldSource=FILES['src/add.mjs'];
  if (oldSource.split(SAFE_PATCH.old).length !== 2) throw Error('PATCH_BASELINE_INVALID');
  const parent=fs.mkdtempSync(path.join(os.tmpdir(),'continuum-public-proposal-'));
  try {
    const trial=path.join(parent,'fixture');
    prepareSyntheticWorkspace(trial);
    const candidateSource=oldSource.replace(SAFE_PATCH.old,SAFE_PATCH.replacement);
    fs.writeFileSync(path.join(trial,'src/add.mjs'),candidateSource,{flag:'w',mode:0o600});
    const result=runFixedTest(trial);
    return Object.freeze({candidateSha256:digest(candidateSource),testResult:result,
      accepted:result.exitCode===0&&result.tests===3&&result.pass===3&&
        result.fail===0&&result.cancelled===0});
  } finally {
    fs.rmSync(parent,{recursive:true,force:true});
  }
}
export function createExperientialTransport({key,fetcher=fetch,timeoutMs=30000}={}) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 2000 || timeoutMs > 60000)
    throw Error('INVALID_TIMEOUT');
  if (typeof key !== 'string' || !/^xpl_[a-f0-9]{40}$/.test(key))
    throw Error('MISSING_OR_INVALID_EXPERIENTIAL_KEY');
  return async ({model,messages,tools}) => {
    validateModel(model);
    const request = {model,messages,tools,tool_choice:'auto',max_tokens:1200};
    const controller = new AbortController();
    const timer = setTimeout(()=>controller.abort(),timeoutMs);
    try {
      const response = await fetcher(API_URL,{
        method:'POST',redirect:'error',signal:controller.signal,
        headers:{'Authorization':'Bearer '+key,'Content-Type':'application/json'},
        body:JSON.stringify(request)
      });
      if (!response.ok) throw Error('PROVIDER_HTTP_'+response.status);
      const raw = await response.text();
      if (raw.length > 131072) throw Error('PROVIDER_RESPONSE_OVERSIZE');
      const data = JSON.parse(raw);
      if (!isPlain(data) || !Array.isArray(data.choices)) throw Error('PROVIDER_RESPONSE_INVALID');
      return data;
    } finally {clearTimeout(timer);}
  };
}
export async function runSyntheticCodingTrial({workspace,model='claude-fable-5.1:free',transport,transportKind='INJECTED_NOT_VERIFIED'}) {
  const selected=validateModel(model);
  if (typeof transport !== 'function') throw Error('TRANSPORT_REQUIRED');
  const before=inspectSyntheticWorkspace(workspace);
  const messages=[
    {role:'system',content:'Audit only the public synthetic coding fixture. Call read_file for src/add.mjs and test/add.test.mjs, then run_test. Respond with ONLY a JSON object: {"path":"src/add.mjs","old":"return Math.abs(a) + Math.abs(b);","replacement":"return a + b;"} if you independently confirm it fixes the issue. No other code or files are allowed. Do not edit files.'},
    {role:'user',content:'Read both synthetic files using tools, execute the fixed regression test, and propose the safe, minimal one-line correction as the requested JSON. Do not edit files.'}
  ];
  const seen=new Set();let testResult=null;let proposal=null;let reportedModels=[];let usageObserved=[];let turns=0;
  for (;turns<8;turns++) {
    const response=await transport({model:selected,messages:structuredClone(messages),tools:toolDefinitions});
    const message=response?.choices?.[0]?.message;
    if (!isPlain(message) || message.role && message.role!=='assistant') throw Error('PROVIDER_MESSAGE_INVALID');
    if (typeof response?.model==='string') {
      if (response.model!==selected && response.model!==selected.slice(0,-5))
        throw Error('UNEXPECTED_PROVIDER_MODEL');
      reportedModels.push(response.model);
    }
    if (response?.usage) {
      const u=response.usage;
      if (!isPlain(u)) throw Error('PROVIDER_USAGE_INVALID');
      usageObserved.push({inputTokens:Number.isSafeInteger(u.prompt_tokens)&&u.prompt_tokens>=0?u.prompt_tokens:null,
        outputTokens:Number.isSafeInteger(u.completion_tokens)&&u.completion_tokens>=0?u.completion_tokens:null});
    }
    const reportedCost=response?.usage?.cost;
    if (reportedCost !== undefined && reportedCost !== null &&
        (!Number.isFinite(reportedCost)||reportedCost!==0)) throw Error('NONZERO_REPORTED_COST');
    if (response?.usage?.is_byok === true) throw Error('BILLABLE_BYOK_NOT_ALLOWED');
    if (response?.usage?.cost_nano_usd !== undefined &&
        response.usage.cost_nano_usd !== 0) throw Error('NONZERO_REPORTED_COST');
    const calls=message.tool_calls;
    if (!Array.isArray(calls)||calls.length===0) {
      proposal=message.content;
      break;
    }
    if (calls.length>3) throw Error('TOO_MANY_TOOL_CALLS');
    const verified=calls.map(requiredToolCall);
    messages.push({role:'assistant',content:null,tool_calls:calls});
    for (const call of verified) {
      let result;
      if (call.name==='read_file') {
        result={path:call.args.path,contents:fs.readFileSync(path.join(before.root,call.args.path),'utf8')};
        seen.add(call.args.path);
      } else {
        testResult=runFixedTest(before.root);
        result={testSummary:testResult.summary};
      }
      messages.push({role:'tool',tool_call_id:call.id,content:JSON.stringify(result)});
    }
    if (inspectSyntheticWorkspace(before.root).fixtureSha256!==before.fixtureSha256)
      throw Error('SYNTHETIC_WORKSPACE_CHANGED');
  }
  const allRead=Object.keys(FILES).every(x=>seen.has(x));
  const proposalText=typeof proposal==='string' ? proposal.trim() : '';
  if (proposalText.length > 4000) throw Error('PROPOSAL_TOO_LONG');
  const completedTools=allRead&&testResult!==null;
  const review=completedTools&&proposalText.length>20 ? verifySyntheticProposal(proposalText) : null;
  const result=Object.freeze({schema:'continuum-optional-coding-bridge-v1',
    model:selected,mode:'SYNTHETIC_ONLY',transportKind,fixtureSha256:before.fixtureSha256,
    observedReadCalls:[...seen].sort(),observedTestCall:testResult!==null,
    testResult,patchApplied:false,repositoryAccess:false,credentialLogged:false,
    completed:completedTools&&review?.accepted===true&&turns<8,
    proposalVerifiedOnIsolatedCopy:review?.accepted===true,proposalReview:review,
    providerReportedModels:[...new Set(reportedModels)],
    usageObserved,modelIdentityExternallyVerified:false,
    proposal:proposalText,usageCostVerified:false});
  inspectSyntheticWorkspace(before.root);
  return result;
}
function safeReport(result) {
  const {proposal,...rest}=result;
  return {...rest,proposalSha256:digest(proposal),proposalPreview:proposal.slice(0,1600)};
}
function mockSyntheticResponder() {
  let step=0;
  return async () => {
    const calls=step===0 ? [
      {id:'synthetic-read-a',type:'function',function:{name:'read_file',arguments:JSON.stringify({path:'src/add.mjs'})}},
      {id:'synthetic-read-test',type:'function',function:{name:'read_file',arguments:JSON.stringify({path:'test/add.test.mjs'})}}
    ] : step===1 ? [{id:'synthetic-test',type:'function',function:{name:'run_test',arguments:'{}'}}] : null;
    step++;
    return {choices:[{message:calls?{role:'assistant',content:null,tool_calls:calls}:
      {role:'assistant',content:JSON.stringify(SAFE_PATCH)}}],
      usage:{cost:0,is_byok:false}};
  };
}
async function cli() {
  const [mode,workspace,requestedModel]=process.argv.slice(2);
  if (!['--prepare','--offline-proof','--trial'].includes(mode) || !workspace || process.argv.length>5)
    throw Error('USAGE: --prepare /new/synthetic/path | --offline-proof /existing/synthetic/path | --trial /existing/synthetic/path [fable|astra]');
  if (mode==='--prepare') {
    if (requestedModel) throw Error('UNEXPECTED_PREPARE_ARGUMENT');
    const value=prepareSyntheticWorkspace(workspace);
    process.stdout.write(JSON.stringify({prepared:true,fixtureSha256:value.fixtureSha256})+'\n');return;
  }
  if (mode==='--offline-proof') {
    if (requestedModel) throw Error('UNEXPECTED_OFFLINE_PROOF_ARGUMENT');
    const report=await runSyntheticCodingTrial({workspace,transport:mockSyntheticResponder(),transportKind:'MOCKED_OFFLINE'});
    process.stdout.write(JSON.stringify(safeReport(report),null,2)+'\n');
    if (!report.completed) process.exitCode=2;
    return;
  }
  if (process.env.CONTINUUM_EXPERIENTIAL_TRIAL_APPROVED!=='SYNTHETIC_ONLY')
    throw Error('EXPLICIT_SYNTHETIC_LIVE_APPROVAL_REQUIRED');
  const model=requestedModel==='astra'?'gpt-6-astra:free':requestedModel==='fable'||!requestedModel?
    'claude-fable-5.1:free':requestedModel;
  const transport=createExperientialTransport({key:process.env.EXPLABS_API_KEY});
  const report=await runSyntheticCodingTrial({workspace,model,transport,transportKind:'EXPERIENTIAL_FREE_ONLY'});
  process.stdout.write(JSON.stringify(safeReport(report),null,2)+'\n');
  if (!report.completed) process.exitCode=2;
}
if (process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  cli().catch(e=>{
    // Deliberately never echo exception context, request bodies, headers or credentials.
    const allowed=/^[A-Z0-9_]+(?:_[0-9]{3})?$/.test(e?.message||'')?e.message:'UNEXPECTED_FAILURE';
    process.stderr.write('CONTINUUM_CODING_BRIDGE_BLOCKED='+allowed+'\n');
    process.exitCode=2;
  });
}
