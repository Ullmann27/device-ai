import http from 'node:http';

// Independent wire client: no MCP SDK or production request helpers are reused.
// Requests always use real TCP sockets. Hard client limits prevent hung reviews.
export function send(url, {method='POST', headers={}, body, timeoutMs=3000,
  maxResponseBytes=1048576}={}) {
  return new Promise((resolve,reject)=>{
    const data=body===undefined?undefined:Buffer.isBuffer(body)?body:
      Buffer.from(typeof body==='string'?body:JSON.stringify(body));
    const request=http.request(url,{method,headers:{
      ...(data?{'Content-Length':data.length}:{}),...headers
    }},response=>{
      const parts=[];let size=0;
      response.on('data',part=>{
        size+=part.length;
        if(size>maxResponseBytes){request.destroy(new Error('REVIEW_RESPONSE_LIMIT'));return;}
        parts.push(part);
      });
      response.on('error',reject);
      response.on('end',()=>{
        const text=Buffer.concat(parts).toString('utf8');let json;
        try{json=JSON.parse(text);}catch{}
        resolve({status:response.statusCode,headers:response.headers,text,json,bytes:size});
      });
    });
    request.setTimeout(timeoutMs,()=>request.destroy(new Error('REVIEW_REQUEST_TIMEOUT')));
    request.on('error',reject);
    request.end(data);
  });
}

export class WireClient {
  constructor(url,token,{version='2025-06-18'}={}){
    this.url=url;this.token=token;this.version=version;this.id=0;this.session=null;
  }
  headers(extra={}){
    return {'Authorization':'Bearer '+this.token,'Content-Type':'application/json',
      'Accept':'application/json, text/event-stream','MCP-Protocol-Version':this.version,
      ...(this.session?{'MCP-Session-Id':this.session}:{}),...extra};
  }
  async raw(body,options={}){
    return send(options.url??this.url,{...options,body,
      headers:options.replaceHeaders?options.headers:this.headers(options.headers)});
  }
  async rpc(method,params={},options={}){
    return this.raw({jsonrpc:'2.0',id:++this.id,method,params},options);
  }
  async initialize(version=this.version){
    const reply=await this.rpc('initialize',{protocolVersion:version,capabilities:{},
      clientInfo:{name:'continuum-independent-wire-review',version:'1'}});
    this.session=reply.headers['mcp-session-id']??null;
    return reply;
  }
  async ready(){
    const init=await this.initialize();
    if(init.status!==200||!init.json?.result)throw new Error('REVIEW_INITIALIZE_FAILED');
    const ack=await this.raw({jsonrpc:'2.0',method:'notifications/initialized'});
    if(ack.status!==202||ack.text!=='')throw new Error('REVIEW_INITIALIZED_FAILED');
    return init;
  }
  call(name,args={}){return this.rpc('tools/call',{name,arguments:args});}
}

export function toolResult(reply){
  const result=reply.json?.result;
  if(result?.structuredContent!==undefined)return result.structuredContent;
  const block=result?.content?.find(part=>part.type==='text');
  if(block){try{return JSON.parse(block.text);}catch{return block.text;}}
  return result;
}

export function denied(reply){
  return reply.status>=400||Boolean(reply.json?.error)||reply.json?.result?.isError===true;
}
