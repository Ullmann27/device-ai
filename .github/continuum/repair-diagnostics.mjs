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

/** JUnit XML (Gradle and `node --test --test-reporter=junit`). */
export function parseJUnit(xml,root){
  const out=[];let total=0,failed=0;
  const text=String(xml).slice(0,MAX_TEXT);
  for(const m of text.matchAll(/<testcase\b((?:[^>"]|"[^"]*")*?)(\/>|>([\s\S]*?)<\/testcase>)/g)){
    total++;
    const attrs=m[1],body=m[3]??'';
    // attribute values may contain '>' (e.g. assertion source), so match quoted values
    const failure=body.match(/<(failure|error)\b((?:[^>"]|"[^"]*")*)>([\s\S]*?)<\/\1>|<(failure|error)\b((?:[^>"]|"[^"]*")*)\/>/);
    if(!failure)continue;
    failed++;
    const attr=(src,name)=>xmlDecode((src.match(new RegExp('\\b'+name+'="([^"]*)"'))??[])[1]??'');
    const name=attr(attrs,'name'),classname=attr(attrs,'classname');
    const fattrs=failure[2]??failure[5]??'';
    const detail=xmlDecode(failure[3]??'');
    const message=(attr(fattrs,'message')||detail.split('\n')[0]).slice(0,400);
    // first stack frame inside the tree: Kotlin `(File.kt:12)` or JS `file:///x.mjs:12:5`
    let file=null,line=null,col=null;
    const js=detail.match(/file:\/\/(\/[^\s:)]+\.m?js):(\d+):(\d+)/);
    if(js){file=relativeInside(root,js[1]);line=Number(js[2]);col=Number(js[3]);}
    const kt=!file&&detail.match(/at ([\w.$]+)\.[\w$<>]+\(([\w]+\.kt):(\d+)\)/);
    if(kt){file=kt[2];line=Number(kt[3]);}
    let kind='TEST_FAILURE',symbol;
    const ref=(message+'\n'+detail).match(/ReferenceError(?: \[Error\])?: ([A-Za-z_$][\w$]*) is not defined/);
    if(ref){kind='REFERENCE_ERROR';symbol=ref[1];}
    else if(/SyntaxError/.test(message+detail))kind='JS_SYNTAX_ERROR';
    const d={tool:'junit',severity:'error',test:(classname?classname+'.':'')+name,file,line,col,message,kind,symbol};
    d.fingerprint=fingerprint(d);
    out.push(d);
    if(out.length>=MAX_DIAGNOSTICS)break;
  }
  return {total,failed,diagnostics:out};
}

/** Node error text (stderr or TAP comments) for modules that fail while loading. */
export function parseJsErrorText(text,root){
  const out=[];
  const clean=String(text).slice(-MAX_TEXT).split('\n').map(l=>l.replace(/^#\s?/,'')).join('\n');
  const runtime=/(ReferenceError|TypeError|SyntaxError)(?: \[Error\])?: ([^\n]+)\n\s*at [^\n]*?file:\/\/(\/[^\s:)]+\.m?js):(\d+):(\d+)/g;
  const parse=/file:\/\/(\/[^\s:)]+\.m?js):(\d+)\n[^\n]*\n[^\n]*\n\s*(SyntaxError): ([^\n]+)/g;
  for(const m of clean.matchAll(runtime)){
    const file=relativeInside(root,m[3]);if(!file)continue;
    const symbol=(m[2].match(/^([A-Za-z_$][\w$]*) is not defined/)??[])[1];
    const kind=m[1]==='ReferenceError'&&symbol?'REFERENCE_ERROR':m[1]==='SyntaxError'?'JS_SYNTAX_ERROR':'JS_RUNTIME_ERROR';
    const d={tool:'node',severity:'error',file,line:Number(m[4]),col:Number(m[5]),message:(m[1]+': '+m[2]).slice(0,400),kind,symbol};
    d.fingerprint=fingerprint(d);if(!out.some(x=>x.fingerprint===d.fingerprint&&x.line===d.line))out.push(d);
  }
  for(const m of clean.matchAll(parse)){
    const file=relativeInside(root,m[1]);if(!file)continue;
    const d={tool:'node',severity:'error',file,line:Number(m[2]),col:null,message:('SyntaxError: '+m[4]).slice(0,400),kind:'JS_SYNTAX_ERROR'};
    d.fingerprint=fingerprint(d);if(!out.some(x=>x.fingerprint===d.fingerprint))out.push(d);
  }
  return out.slice(0,MAX_DIAGNOSTICS);
}

export function readJUnitDirectory(dir,root){
  const result={total:0,failed:0,diagnostics:[]};
  if(!fs.existsSync(dir))return result;
  for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.xml')).sort().slice(0,2000)){
    const file=path.join(dir,name);
    if(fs.statSync(file).size>5_000_000)continue;
    const part=parseJUnit(fs.readFileSync(file,'utf8'),root);
    result.total+=part.total;result.failed+=part.failed;
    result.diagnostics.push(...part.diagnostics.slice(0,MAX_DIAGNOSTICS-result.diagnostics.length));
  }
  return result;
}
