/**
 * Bounded async sidecar before the synchronous Teamleader cycle: read HEAD and status of the source through the owner-
 * approved local MCP git server (mcp-adapter.mjs, same decision table), then hand a sealed evidence record to
 * runTeamleaderCycle({toolEvidence}). The record is journalled outside the repository. No OS isolation is claimed.
 */
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {createMcpSession} from './mcp-adapter.mjs';

const sha256=t=>createHash('sha256').update(t).digest('hex');
export const GIT_READONLY_SERVER=fileURLToPath(new URL('./mcp-git-readonly-server.mjs',import.meta.url));
const ALLOWLIST=Object.freeze({git_head:{risk:'READ_ONLY'},git_status:{risk:'READ_ONLY'}});
const seal=e=>({...e,integrity:sha256(JSON.stringify(e))});

export async function mcpPreflight({root,stateDir,grant,profile='PROTECTED',approvedServers,limits={},now=Date.now}){
  const source=fs.realpathSync(root),store=path.resolve(stateDir);
  if(store===source||store.startsWith(source+path.sep))throw Error('Store tool evidence outside the repository');
  const session=await createMcpSession({command:process.execPath,args:[GIT_READONLY_SERVER],workspace:source,
    allowlist:ALLOWLIST,grant,profile,approvedServers,limits:{maxCalls:2,callTimeoutMs:10000,...limits}});
  let head,status;
  try{
    head=(await session.call('git_head')).content[0].text.trim();
    status=(await session.call('git_status')).content[0].text;
  }finally{await session.close();}
  const evidence=seal({schema:'continuum-teamleader-tool-evidence',version:1,at:now(),head,
    statusSha256:sha256(status),branch:status.split('\n')[0].slice(0,120),clean:status.split('\n').length===1,
    receipts:session.receipts().map(({tool,decision,outcome,resultSha256})=>({tool,decision,outcome,resultSha256}))});
  fs.mkdirSync(store,{recursive:true,mode:0o700});
  fs.appendFileSync(path.join(store,'mcp-preflight.jsonl'),JSON.stringify(evidence)+'\n',{mode:0o600});
  return evidence;
}

/**
 * The cycle accepts tool evidence only if: sealed (a consistency check, NOT authenticated provenance - anyone with
 * write access to the state directory can recompute it), the MCP HEAD equals its own HEAD, the working tree was clean
 * (a dirty tree is never an approved base), and the receipts are exactly git_head then git_status, both allowed and OK.
 */
export function verifyToolEvidence(e,head){
  if(!e||e.schema!=='continuum-teamleader-tool-evidence'||e.version!==1||e.head!==head||e.clean!==true)return false;
  const {integrity,...data}=e;
  return integrity===sha256(JSON.stringify(data))&&Array.isArray(e.receipts)&&e.receipts.length===2&&
    e.receipts.map(r=>r.tool).join(',')==='git_head,git_status'&&
    e.receipts.every(r=>r.outcome==='OK'&&r.decision==='ALLOW'&&/^[a-f0-9]{64}$/.test(r.resultSha256??''));
}
