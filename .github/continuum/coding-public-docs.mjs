/** On-demand, bounded public programming references for Continuum Coding.
 * No model weights, secondary memory store, search engine scraping or paid API.
 * Static URLs only; never send a task, code, diagnostics, secrets or user data
 * to a documentation host. Fetched text is UNTRUSTED REFERENCE DATA.
 */
import {createHash} from 'node:crypto';

const sources=Object.freeze({
  'kotlin-null':Object.freeze({name:'Kotlin null safety',url:'https://kotlinlang.org/docs/null-safety.html'}),
  'kotlin-import':Object.freeze({name:'Kotlin packages and imports',url:'https://kotlinlang.org/docs/packages.html'}),
  'android-picker':Object.freeze({name:'Android Storage Access Framework',url:'https://developer.android.com/training/data-storage/shared/documents-files'}),
  'android-lifecycle':Object.freeze({name:'Android Activity lifecycle',url:'https://developer.android.com/guide/components/activities/activity-lifecycle'}),
  'node-stream':Object.freeze({name:'Node.js streams',url:'https://nodejs.org/api/stream.html'}),
  'node-error':Object.freeze({name:'Node.js errors',url:'https://nodejs.org/api/errors.html'}),
  'node-fs':Object.freeze({name:'Node.js file system',url:'https://nodejs.org/api/fs.html'})
});
export const PUBLIC_DOC_TOPICS=Object.freeze(Object.keys(sources));

export function planPublicCodingDocs({language,problem}={}) {
  if(!['kotlin','android','node'].includes(language))throw Error('DOCS_UNKNOWN_LANGUAGE');
  if(typeof problem!=='string'||problem.length>1200)throw Error('DOCS_INVALID_PROBLEM');
  const diagnostic=problem.toLowerCase();
  const plan=[];
  const add=id=>{if(!plan.includes(id))plan.push(id);};
  if(language==='node'){
    if(/stream|read\(|readable|backpressure|empty read|no progress|pipe/.test(diagnostic))add('node-stream');
    if(/enoent|eacces|permission|fs\.|file|path/.test(diagnostic))add('node-fs');
    if(/error|exception|uncaught|reject|timeout/.test(diagnostic))add('node-error');
    if(!plan.length)add('node-error');
  }else{
    if(/uri|document|file picker|saf|openinputstream|dateiauswahl|contentresolver/.test(diagnostic))
      add('android-picker');
    if(/activity|lifecycle|oncreate|onresume|onsaveinstancestate|zustand/.test(diagnostic))
      add('android-lifecycle');
    if(/nullable|null|npe|!!|type mismatch/.test(diagnostic))add('kotlin-null');
    if(/unresolved reference|import|package|symbol/.test(diagnostic))add('kotlin-import');
    if(!plan.length)add(language==='android'?'android-lifecycle':'kotlin-import');
  }
  return Object.freeze(plan.slice(0,2).map(id=>Object.freeze({id,name:sources[id].name,url:sources[id].url})));
}
export function exactPublicDocTopic(id) {
  if(typeof id!=='string'||!Object.hasOwn(sources,id))throw Error('DOCS_TOPIC_NOT_ALLOWED');
  return Object.freeze({id,...sources[id]});
}
const digest=value=>createHash('sha256').update(value).digest('hex');
const MAX_BYTES=262144;
function toText(bytes,contentType) {
  const raw=new TextDecoder('utf-8',{fatal:true}).decode(bytes);
  if(contentType.startsWith('text/plain'))return raw.replace(/\s+/g,' ').trim().slice(0,2800);
  if(!contentType.startsWith('text/html'))throw Error('DOCS_UNEXPECTED_CONTENT_TYPE');
  const area=/<main\b[^>]*>([\s\S]*?)<\/main>/i.exec(raw)?.[1] ??
    /<article\b[^>]*>([\s\S]*?)<\/article>/i.exec(raw)?.[1] ?? raw;
  const safe=area.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,' ')
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,' ')
    .replace(/<svg\b[^>]*>[\s\S]*?<\/svg>/gi,' ')
    .replace(/<[^>]*>/g,' ').replace(/&nbsp;|&#160;/gi,' ')
    .replace(/&lt;/gi,'<').replace(/&gt;/gi,'>')
    .replace(/&quot;/gi,'"').replace(/&amp;/gi,'&')
    .replace(/&#(\d+);/g,(_,n)=>Number(n)<=0x7f?String.fromCharCode(Number(n)):' ')
    .replace(/\s+/g,' ').trim();
  return safe.slice(0,2800);
}
/** fetchFn is injectable for offline tests. Owner approval must be explicit:
 * there is no automatic network fallback when a code generator is unavailable.
 */
export async function fetchPublicCodingDoc(id,{approved=false,fetchFn=fetch,timeoutMs=7000}={}) {
  const doc=exactPublicDocTopic(id);
  if(approved!==true)throw Error('DOCS_OWNER_APPROVAL_REQUIRED');
  if(!Number.isSafeInteger(timeoutMs)||timeoutMs<100||timeoutMs>10000)
    throw Error('DOCS_INVALID_TIMEOUT');
  const url=new URL(doc.url);
  if(url.protocol!=='https:'||!['kotlinlang.org','developer.android.com','nodejs.org'].includes(url.hostname)||
     url.username||url.password||url.search||url.hash)throw Error('DOCS_ENDPOINT_INVALID');
  const ac=new AbortController();
  const timeout=setTimeout(()=>ac.abort(),timeoutMs);
  try{
    // No request body, cookies, authentication headers, query text or code snippets.
    const response=await fetchFn(doc.url,{method:'GET',redirect:'manual',
      credentials:'omit',referrerPolicy:'no-referrer',cache:'no-store',
      headers:{Accept:'text/html, text/plain;q=0.5'},signal:ac.signal});
    if(response?.status!==200||response.redirected===true||
       response.url && response.url!==doc.url)throw Error('DOCS_UNAVAILABLE_OR_REDIRECTED');
    const type=(response.headers?.get?.('content-type')??'').toLowerCase();
    if(!/^(text\/html|text\/plain)(?:;|$)/.test(type))throw Error('DOCS_UNEXPECTED_CONTENT_TYPE');
    const stated=response.headers?.get?.('content-length');
    if(stated!=null&&(!/^\d+$/.test(stated)||Number(stated)>MAX_BYTES))
      throw Error('DOCS_RESPONSE_TOO_LARGE');
    const reader=response.body?.getReader?.();
    if(!reader)throw Error('DOCS_STREAM_UNAVAILABLE');
    let n=0;const chunks=[];
    try{
      while(true){
        const {value,done}=await reader.read();
        if(done)break;
        if(!(value instanceof Uint8Array))throw Error('DOCS_INVALID_CHUNK');
        n+=value.byteLength;
        if(n>MAX_BYTES)throw Error('DOCS_RESPONSE_TOO_LARGE');
        chunks.push(value);
      }
    }finally{try{await reader.cancel();}catch{}reader.releaseLock();}
    const bytes=Buffer.concat(chunks,n);
    const excerpt=toText(bytes,type);
    if(excerpt.length<40)throw Error('DOCS_NO_MEANINGFUL_TEXT');
    return Object.freeze({schema:'continuum-public-doc-excerpt',version:1,
      id,url:doc.url,title:doc.name,sourceSha256:digest(bytes),
      bytes:n,excerpt,trust:'UNTRUSTED_EXTERNAL_DOCUMENTATION',
      canExecute:false,canAuthorize:false,storedOnDisk:false});
  }finally{clearTimeout(timeout);}
}
