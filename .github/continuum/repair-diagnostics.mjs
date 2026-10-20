import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

/**
 * Diagnostician: turns the output of the agent's OWN reproduction run (compiler
 * text, JUnit XML) into structured diagnostics. Input is data only; nothing in
 * it is ever executed. Paths outside the checked-out tree are discarded.
 */
const MAX_TEXT=2_000_000, MAX_DIAGNOSTICS=60;

export const sha256=value=>crypto.createHash('sha256').update(value).digest('hex');

function relativeInside(root,file){
  const clean=file.replace(/^file:\/\//,'');
  const absolute=path.resolve(root,clean);
  const rel=path.relative(path.resolve(root),absolute);
  if(!rel||rel.startsWith('..')||path.isAbsolute(rel))return null;
  return rel.split(path.sep).join('/');
}

export function classifyKotlin(message){
  let m;
  if((m=message.match(/^Unresolved reference(?::|\s)\s*['`]?([A-Za-z_][A-Za-z_0-9]*)['`]?/)))return {kind:'UNRESOLVED_REFERENCE',symbol:m[1]};
  if(/^Syntax error/.test(message)||/^Expecting /.test(message))return {kind:'SYNTAX_ERROR'};
  if((m=message.match(/actual type is '([^']+)', but '([^']+)' was expected/)))return {kind:'TYPE_MISMATCH',actual:m[1],expected:m[2]};
  if(/^Only safe \(\?\.\) or non-null asserted/.test(message))return {kind:'NULLABLE_RECEIVER'};
  if(/is not abstract and does not implement abstract member/.test(message))return {kind:'MISSING_OVERRIDE'};
  return {kind:'COMPILER_ERROR'};
}

/** A stable identity for "the same problem": no line numbers, normalized numbers. */
export function fingerprint(d){
  const message=String(d.message??'').replace(/\d+/g,'#').replace(/\s+/g,' ').slice(0,200);
  return sha256([d.kind,d.file??'',d.symbol??'',message].join('\u0000')).slice(0,16);
}

/** Kotlin K2 (`e: file:///a/B.kt:8:70 msg`) and K1 (`e: /a/B.kt: (8, 70): msg`) errors. */
export function parseKotlinDiagnostics(text,root){
  const out=[];
  const source=String(text).slice(-MAX_TEXT);
  const patterns=[/^e: (?:file:\/\/)?(\/[^\n]*?\.kts?):(\d+):(\d+) ([^\n]+)$/gm,/^e: (?:file:\/\/)?(\/[^\n]*?\.kts?): \((\d+), (\d+)\): ([^\n]+)$/gm];
  for(const pattern of patterns)for(const m of source.matchAll(pattern)){
    const file=relativeInside(root,m[1]);if(!file)continue;
    const message=m[4].trim().slice(0,400);
    const d={tool:'kotlinc',severity:'error',file,line:Number(m[2]),col:Number(m[3]),message,...classifyKotlin(message)};
    d.fingerprint=fingerprint(d);
    if(!out.some(x=>x.file===d.file&&x.line===d.line&&x.col===d.col&&x.message===d.message))out.push(d);
    if(out.length>=MAX_DIAGNOSTICS)return out;
  }
  return out;
}

const xmlDecode=s=>s.replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&#(\d+);/g,(_,n)=>String.fromCharCode(Number(n))).replace(/&amp;/g,'&');

/**
 * V8 error headers as printed by util.inspect in Node's test reporters. The
 * bracketed tag differs between releases for the same error, e.g. a cause that
 * crossed the test-runner boundary prints as
 *   "ReferenceError [Error]: sqare is not defined"   (Node 20.20, 22.16)
 *   "ReferenceError: sqare is not defined"           (Node 22.22)
 * Only these specific types are recognized; generic "Error" and
 * "AssertionError" headers never become code diagnostics, so assertion text
 * that merely mentions "is not defined" or "SyntaxError" stays a TEST_FAILURE.
 */
const JS_ERROR_HEADER=/(?:^|[\s:\[{(])(?:Uncaught )?(ReferenceError|TypeError|SyntaxError|RangeError)(?: \[[A-Za-z_][\w ]{0,40}\])?: ([^\n]+)/g;
const FILE_FRAME=/file:\/\/(\/[^\s:)'"]+\.m?js):(\d+):(\d+)/g;

export function firstRepoFrame(text,root,from=0,to=text.length){
  FILE_FRAME.lastIndex=from;
  for(let m;(m=FILE_FRAME.exec(text))&&m.index<to;){
    const file=relativeInside(root,m[1]);
    if(file&&!file.includes('node_modules/'))return {file,line:Number(m[2]),col:Number(m[3])};
  }
  return null;
}

/** All specific JS error headers with the text span of their own stack. */
function errorHeaders(text){
  const headers=[];
  JS_ERROR_HEADER.lastIndex=0;
  for(let m;(m=JS_ERROR_HEADER.exec(text));)headers.push({type:m[1],message:m[2].trim().slice(0,400),start:m.index,end:m.index+m[0].length});
  // a header's frames end where the next header starts (bounded to ~60 lines)
  headers.forEach((h,i)=>{h.stackEnd=Math.min(headers[i+1]?.start??text.length,h.end+6000);});
  return headers;
}
const notDefined=(type,message)=>type==='ReferenceError'?(message.match(/^([A-Za-z_$][\w$]*) is not defined\b/)??[])[1]:undefined;

/** First specific JS error in a report text plus the first repository frame that follows it. */
export function parseJsError(text,root){
  const source=String(text).slice(0,MAX_TEXT);
  for(const h of errorHeaders(source)){
    const frame=firstRepoFrame(source,root,h.end,h.stackEnd);
    if(!frame)continue;
    return {type:h.type,message:h.type+': '+h.message,symbol:notDefined(h.type,h.message),...frame};
  }
  return null;
}

/** JUnit XML (Gradle and `node --test --test-reporter=junit`). */
export function parseJUnit(xml,root){
  const out=[],skippedCaseHashes=[];let total=0,failed=0,skipped=0,placeholders=0,crashed=0;
  const text=String(xml).slice(0,MAX_TEXT);
  const attr=(src,name)=>xmlDecode((src.match(new RegExp('\\b'+name+'="([^"]*)"'))??[])[1]??'');
  for(const m of text.matchAll(/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g)){
    total++;
    const attrs=m[1],body=m[3]??'';
    // `node --test` reports a test FILE as one testcase when it defines no tests
    // (passing) or crashes while loading (failing): it is not executed evidence.
    // Node 20 names it by absolute path, Node 22 relative to the working directory.
    const placeholder=/\bname="[^"]*\.test\.m?js"/.test(attrs);
    if(placeholder)placeholders++;
    if(/<skipped\b/.test(body)){
      skipped++;
      const name=attr(attrs,'name'),classname=attr(attrs,'classname');
      // Hash identities, never copy untrusted test names into public receipts.
      // Incomplete/duplicate identifiers will fail the ANSK contract later.
      skippedCaseHashes.push(name&&classname?sha256(classname+'\\0'+name):null);
      continue;
    }
    // attribute values may contain '>' (e.g. assertion source), so match quoted values
    const failure=body.match(/<(failure|error)\b((?:[^>"]|"[^"]*")*)>([\s\S]*?)<\/\1>|<(failure|error)\b((?:[^>"]|"[^"]*")*)\/>/);
    if(!failure)continue;
    failed++;if(placeholder)crashed++;
    const name=attr(attrs,'name'),classname=attr(attrs,'classname');
    const fattrs=failure[2]??failure[5]??'';
    const detail=xmlDecode(failure[3]??'');
    const message=(attr(fattrs,'message')||detail.split('\n')[0]).slice(0,400);
    const js=parseJsError(detail,root);
    let file=null,line=null,col=null,kind='TEST_FAILURE',symbol;
    if(js){
      ({file,line,col}=js);symbol=js.symbol;
      kind=js.type==='ReferenceError'&&js.symbol?'REFERENCE_ERROR':js.type==='SyntaxError'?'JS_SYNTAX_ERROR':'JS_RUNTIME_ERROR';
    }else{
      // assertion failures and other errors: first stack frame inside the tree
      const frame=firstRepoFrame(detail,root,0);
      if(frame)({file,line,col}=frame);
      else{const kt=detail.match(/at ([\w.$]+)\.[\w$<>]+\(([\w]+\.kt):(\d+)\)/);if(kt){file=kt[2];line=Number(kt[3]);}}
    }
    const d={tool:'junit',severity:'error',test:(classname?classname+'.':'')+name,file,line,col,message,kind,symbol};
    if(placeholder){const testFile=relativeInside(root,name);if(testFile)d.testFile=testFile;}
    d.fingerprint=fingerprint(d);
    out.push(d);
    if(out.length>=MAX_DIAGNOSTICS)break;
  }
  // executed = real test cases that ran (not skipped, not file placeholders);
  // passed = executed test cases without failure (a crashed file is not a test)
  const executed=total-skipped-placeholders;
  return {total,failed,skipped,placeholders,executed,passed:executed-(failed-crashed),diagnostics:out,skippedCaseHashes};
}

/** Node error text (stderr or TAP comments) for modules that fail while loading. */
export function parseJsErrorText(text,root){
  const out=[];
  const clean=String(text).slice(-MAX_TEXT).split('\n').map(l=>l.replace(/^#\s?/,'')).join('\n');
  const parse=/file:\/\/(\/[^\s:)]+\.m?js):(\d+)\n[^\n]*\n[^\n]*\n\s*(SyntaxError): ([^\n]+)/g;
  const add=(js,tool)=>{
    const kind=js.type==='ReferenceError'&&js.symbol?'REFERENCE_ERROR':js.type==='SyntaxError'?'JS_SYNTAX_ERROR':'JS_RUNTIME_ERROR';
    const d={tool,severity:'error',file:js.file,line:js.line,col:js.col,message:js.message,kind,symbol:js.symbol};
    d.fingerprint=fingerprint(d);if(!out.some(x=>x.fingerprint===d.fingerprint&&x.line===d.line))out.push(d);
  };
  // stderr / TAP comment blocks: "<Type>[ [tag]]: msg" followed by its frames
  for(const h of errorHeaders(clean)){
    const frame=firstRepoFrame(clean,root,h.end,h.stackEnd);
    if(frame)add({type:h.type,message:h.type+': '+h.message,symbol:notDefined(h.type,h.message),...frame},'node');
  }
  // TAP YAML diagnostics of a failed test: error/name/stack keys
  for(const m of clean.matchAll(/\n\s*error: '((?:[^'\n]|'')*)'\n(?:\s+\w+: [^\n]*\n)*?\s+name: '(ReferenceError|TypeError|SyntaxError|RangeError)'\n\s+stack: \|-\n((?:\s{4,}[^\n]*\n?)+)/g)){
    const frame=firstRepoFrame(m[3],root,0);if(!frame)continue;
    const message=m[1].replace(/''/g,"'").slice(0,400);
    add({type:m[2],message:m[2]+': '+message,symbol:notDefined(m[2],message),...frame},'node');
  }
  for(const m of clean.matchAll(parse)){
    const file=relativeInside(root,m[1]);if(!file)continue;
    const d={tool:'node',severity:'error',file,line:Number(m[2]),col:null,message:('SyntaxError: '+m[4]).slice(0,400),kind:'JS_SYNTAX_ERROR'};
    d.fingerprint=fingerprint(d);if(!out.some(x=>x.fingerprint===d.fingerprint))out.push(d);
  }
  return out.slice(0,MAX_DIAGNOSTICS);
}

export function readJUnitDirectory(dir,root){
  const result={total:0,failed:0,skipped:0,placeholders:0,executed:0,passed:0,diagnostics:[],skippedCaseHashes:[]};
  if(!fs.existsSync(dir))return result;
  for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.xml')).sort().slice(0,2000)){
    const file=path.join(dir,name);
    if(fs.statSync(file).size>5_000_000)continue;
    const part=parseJUnit(fs.readFileSync(file,'utf8'),root);
    for(const k of ['total','failed','skipped','placeholders','executed','passed'])result[k]+=part[k];
    result.diagnostics.push(...part.diagnostics.slice(0,MAX_DIAGNOSTICS-result.diagnostics.length));
    result.skippedCaseHashes.push(...part.skippedCaseHashes);
  }
  return result;
}
