#!/usr/bin/env node
/** Minimal local MCP server (stdio): two fixed read-only git queries of its working directory. No arguments, no shell. */
import {spawnSync} from 'node:child_process';
import readline from 'node:readline';
const TOOLS={git_head:['rev-parse','HEAD'],git_status:['status','--porcelain=v1','--branch']};
const out=m=>process.stdout.write(JSON.stringify(m)+'\n');
readline.createInterface({input:process.stdin}).on('line',line=>{
  let m;try{m=JSON.parse(line);}catch{return;}
  if(m.id===undefined)return;
  if(m.method==='initialize')return out({jsonrpc:'2.0',id:m.id,result:{protocolVersion:'2025-06-18',capabilities:{tools:{}},serverInfo:{name:'continuum-git-readonly',version:'1'}}});
  if(m.method==='tools/list')return out({jsonrpc:'2.0',id:m.id,result:{tools:Object.keys(TOOLS).map(name=>({name,inputSchema:{type:'object',properties:{},additionalProperties:false}}))}});
  if(m.method==='tools/call'){
    const cmd=TOOLS[m.params?.name];
    if(!cmd||Object.keys(m.params?.arguments??{}).length)return out({jsonrpc:'2.0',id:m.id,error:{code:-32602,message:'unknown tool or arguments'}});
    const r=spawnSync('git',cmd,{encoding:'utf8',timeout:10000,shell:false});
    return out({jsonrpc:'2.0',id:m.id,result:{isError:r.status!==0,content:[{type:'text',text:(r.status===0?r.stdout:'git failed').trim().slice(0,4000)}]}});
  }
  out({jsonrpc:'2.0',id:m.id,error:{code:-32601,message:'method not found'}});
});
