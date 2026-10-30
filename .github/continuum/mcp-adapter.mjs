/**
 * Smallest MCP client for the Continuum worker: one local MCP server over stdio (no network transport), an explicit
 * owner allowlist, the dual-control decision of ContinuumDualControlPolicy.kt (same table, mirrored here), workspace
 * scope checks, call/time/output budgets and a receipt per call. The server gets no inherited environment (no keys).
 * Nothing here grants Android permissions, signs, merges or bypasses ToolGate/OwnerGate; it only refuses or forwards.
 * NOT an OS sandbox: the server runs as the same user. Bounds here are program/argument approval, an empty environment,
 * its own process group (killed as a whole), session/call/output limits and argument scope checks.
 */
import {spawn} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';

const sha256=t=>createHash('sha256').update(t).digest('hex');
const fail=code=>{const e=Error(code);e.code=code;throw e;};
export const MCP_LIMITS=Object.freeze({maxCalls:20,callTimeoutMs:30000,startTimeoutMs:15000,maxResultChars:20000,maxLineChars:2_000_000,maxSessionMs:300000});
const RISKS=new Set(['READ_ONLY','WRITE','DESTRUCTIVE']);

/** Mirror of ContinuumDualControlPolicy.evaluate: DENY, REQUIRE_OWNER_REVIEW or ALLOW (REVIEW is not authorization). */
export function decideToolCall({profile,name,spec,grant}){
  if(!spec||!RISKS.has(spec.risk))return 'DENY';
  if(!grant?.ownerAuthenticated||!grant.sessionActive||!grant.devicePermissionGranted||!grant.allowedToolNames?.includes(name))return 'DENY';
  if(grant.protectedBoundary||spec.risk==='DESTRUCTIVE'||spec.externalSideEffect)return 'REQUIRE_OWNER_REVIEW';
  if(spec.risk==='READ_ONLY')return 'ALLOW';
  if(profile==='OWNER_AUTONOMOUS'&&grant.reusableConsentToolNames?.includes(name))return 'ALLOW';
  return 'REQUIRE_OWNER_REVIEW';
}

/** Real path of p, or of its nearest existing parent plus the rest (symlinks resolved where they exist). */
function realish(p){
  let head=p,tail=[];
  while(!fs.existsSync(head)){const up=path.dirname(head);if(up===head)break;tail.unshift(path.basename(head));head=up;}
  return path.join(fs.realpathSync(head),...tail);
}
/** Path-like arguments, also nested in objects/arrays, must stay inside the granted workspace after symlink resolution. */
export function withinScope(args,workspace,depth=0){
  if(depth>8)return false;
  if(Array.isArray(args))return args.every(v=>v===null||typeof v!=='object'||withinScope(v,workspace,depth+1));
  for(const [key,value] of Object.entries(args??{})){
    if(value&&typeof value==='object'){if(!withinScope(value,workspace,depth+1))return false;continue;}
    if(/^(?:path|paths|file|files|dir|directory|cwd|filename|root|target|source|dest|destination)$/i.test(key)){
      if(typeof value!=='string'||!workspace)return false;
      let root,full;try{root=fs.realpathSync(workspace);full=realish(path.resolve(root,value));}catch{return false;}
      if(full!==root&&!full.startsWith(root+path.sep))return false;
    }
  }
  return true;
}

/** Only an owner-approved server: absolute regular executable file and exactly the approved arguments. */
export function approvedServer(command,args,approvedServers){
  if(typeof command!=='string'||!path.isAbsolute(command)||!Array.isArray(args)||!args.every(a=>typeof a==='string'))return false;
  let real;try{real=fs.realpathSync(command);}catch{return false;}
  if(!fs.statSync(real).isFile())return false;
  return (approvedServers??[]).some(s=>s&&s.command===real&&Array.isArray(s.args)&&s.args.length===args.length&&s.args.every((a,i)=>a===args[i]));
}

/**
 * allowlist: {toolName:{risk:'READ_ONLY'|'WRITE'|'DESTRUCTIVE',externalSideEffect?:bool}} set by the owner.
 * confirm({name,args}) -> Promise<boolean> is asked for REQUIRE_OWNER_REVIEW; a missing confirm declines.
 */
export async function createMcpSession({command,args=[],cwd,env={},allowlist,profile='PROTECTED',grant,workspace=null,
  confirm=null,limits={},approvedServers=[]}){
  const L={...MCP_LIMITS,...limits};
  if(!['PROTECTED','OWNER_AUTONOMOUS'].includes(profile))fail('MCP_PROFILE_INVALID');
  if(!allowlist||typeof allowlist!=='object')fail('MCP_ALLOWLIST_REQUIRED');
  if(!approvedServer(command,args,approvedServers))fail('MCP_SERVER_NOT_APPROVED');
  // Empty environment except a fixed PATH; explicit env entries must be owner-provided and never secrets.
  const child=spawn(fs.realpathSync(command),args,{cwd:workspace??cwd,env:{PATH:'/usr/bin:/bin',...env},stdio:['pipe','pipe','ignore'],
    shell:false,detached:true});
  let buffer='',nextId=1,calls=0,closed=false;const pending=new Map(),receipts=[];
  const stop=()=>{if(!closed){closed=true;clearTimeout(sessionTimer);
      try{process.kill(-child.pid,'SIGKILL');}catch{try{child.kill('SIGKILL');}catch{}}}for(const p of pending.values())p.reject(Object.assign(Error('MCP_SERVER_CLOSED'),{code:'MCP_SERVER_CLOSED'}));pending.clear();};
  const sessionTimer=setTimeout(()=>stop(),L.maxSessionMs);sessionTimer.unref?.();
  child.on('exit',stop);child.on('error',stop);
  child.stdout.setEncoding('utf8');
  child.stdout.on('data',chunk=>{
    buffer+=chunk;
    if(buffer.length>L.maxLineChars){stop();return;}
    let i;while((i=buffer.indexOf('\n'))>=0){
      const line=buffer.slice(0,i);buffer=buffer.slice(i+1);
      let msg;try{msg=JSON.parse(line);}catch{continue;}
      const p=pending.get(msg.id);if(p){pending.delete(msg.id);msg.error?p.reject(Object.assign(Error('MCP_TOOL_ERROR'),{code:'MCP_TOOL_ERROR'})):p.resolve(msg.result);}
    }
  });
  const request=(method,params,timeoutMs)=>new Promise((resolve,reject)=>{
    if(closed)return reject(Object.assign(Error('MCP_SERVER_CLOSED'),{code:'MCP_SERVER_CLOSED'}));
    const id=nextId++;
    const timer=setTimeout(()=>{pending.delete(id);stop();reject(Object.assign(Error('MCP_TIMEOUT'),{code:'MCP_TIMEOUT'}));},timeoutMs);
    pending.set(id,{resolve:v=>{clearTimeout(timer);resolve(v);},reject:e=>{clearTimeout(timer);reject(e);}});
    child.stdin.write(JSON.stringify({jsonrpc:'2.0',id,method,params})+'\n');
  });
  try{
    await request('initialize',{protocolVersion:'2025-06-18',capabilities:{},clientInfo:{name:'continuum-worker',version:'1'}},L.startTimeoutMs);
    child.stdin.write(JSON.stringify({jsonrpc:'2.0',method:'notifications/initialized'})+'\n');
  }catch(e){stop();throw e;}
  const listed=(await request('tools/list',{},L.startTimeoutMs))?.tools??[];
  // Only tools that the server offers AND the owner allowlisted are visible; everything else stays unknown.
  const tools=listed.filter(t=>typeof t?.name==='string'&&Object.hasOwn(allowlist,t.name)).map(t=>({name:t.name,risk:allowlist[t.name].risk}));
  const call=async(name,callArgs={})=>{
    const spec=tools.some(t=>t.name===name)?allowlist[name]:null;
    const decision=spec?decideToolCall({profile,name,spec,grant}):'DENY';
    const receipt={tool:name,profile,decision,argsSha256:sha256(JSON.stringify(callArgs)),resultSha256:null,outcome:null};
    receipts.push(receipt);
    if(!spec){receipt.outcome='UNKNOWN_TOOL';fail('MCP_TOOL_UNKNOWN');}
    if(decision==='DENY'){receipt.outcome='DENIED';fail('MCP_TOOL_DENIED');}
    if(!withinScope(callArgs,workspace)){receipt.outcome='OUT_OF_SCOPE';fail('MCP_SCOPE_EXCEEDED');}
    if(calls>=L.maxCalls){receipt.outcome='BUDGET';fail('MCP_BUDGET_EXHAUSTED');}
    if(decision==='REQUIRE_OWNER_REVIEW'&&!(typeof confirm==='function'&&await confirm({name,args:structuredClone(callArgs)}))){
      receipt.outcome='DECLINED';fail('MCP_OWNER_DECLINED');}
    calls++;
    const result=await request('tools/call',{name,arguments:callArgs},L.callTimeoutMs);
    const text=JSON.stringify(result??null);
    if(text.length>L.maxResultChars){receipt.outcome='RESULT_TOO_LARGE';fail('MCP_RESULT_TOO_LARGE');}
    receipt.resultSha256=sha256(text);receipt.outcome='OK';
    return result;
  };
  return {tools:()=>structuredClone(tools),call,receipts:()=>structuredClone(receipts),
    close:()=>new Promise(resolve=>{if(closed&&child.exitCode!==null)return resolve();child.once('exit',()=>resolve());stop();})};
}
