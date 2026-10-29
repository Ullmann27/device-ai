import {tokenize} from './kotlin-tokenizer.mjs';
import {knownFixes} from './repair-engine.mjs';
import {sha256} from './repair-diagnostics.mjs';

/**
 * Solution developer + code generator. Deterministic strategies map a structured
 * diagnostic to concrete candidate edits. Every edit is a minimal hunk
 * {file, oldText, newText} that occurs exactly once in the base file, so the same
 * hunk can later be replayed from experience and reviewed as a normal diff.
 */
export const JDK_IMPORTS=Object.freeze({
  File:'java.io.File',FileOutputStream:'java.io.FileOutputStream',FileInputStream:'java.io.FileInputStream',
  IOException:'java.io.IOException',ByteArrayOutputStream:'java.io.ByteArrayOutputStream',
  ByteArrayInputStream:'java.io.ByteArrayInputStream',DataInputStream:'java.io.DataInputStream',
  DataOutputStream:'java.io.DataOutputStream',Files:'java.nio.file.Files',Paths:'java.nio.file.Paths',
  StandardCopyOption:'java.nio.file.StandardCopyOption',AtomicMoveNotSupportedException:'java.nio.file.AtomicMoveNotSupportedException',
  MessageDigest:'java.security.MessageDigest',SecureRandom:'java.security.SecureRandom',Locale:'java.util.Locale',
  UUID:'java.util.UUID',Random:'java.util.Random',AtomicBoolean:'java.util.concurrent.atomic.AtomicBoolean',
  AtomicInteger:'java.util.concurrent.atomic.AtomicInteger',AtomicLong:'java.util.concurrent.atomic.AtomicLong',
  TimeUnit:'java.util.concurrent.TimeUnit',Executors:'java.util.concurrent.Executors',
  ConcurrentHashMap:'java.util.concurrent.ConcurrentHashMap',CancellationException:'java.util.concurrent.CancellationException',
  URI:'java.net.URI',URL:'java.net.URL',Instant:'java.time.Instant',Duration:'java.time.Duration',
  Context:'android.content.Context',Intent:'android.content.Intent',Uri:'android.net.Uri',Bundle:'android.os.Bundle',
  Log:'android.util.Log',Toast:'android.widget.Toast',
  abs:'kotlin.math.abs',exp:'kotlin.math.exp',ln:'kotlin.math.ln',sqrt:'kotlin.math.sqrt',tanh:'kotlin.math.tanh',
  max:'kotlin.math.max',min:'kotlin.math.min',roundToInt:'kotlin.math.roundToInt'
});

export function editDistance(a,b){
  // Damerau-Levenshtein (optimal string alignment)
  const d=Array.from({length:a.length+1},(_,i)=>Array(b.length+1).fill(0).map((_,j)=>i===0?j:j===0?i:0));
  for(let i=1;i<=a.length;i++)for(let j=1;j<=b.length;j++){
    const cost=a[i-1]===b[j-1]?0:1;
    d[i][j]=Math.min(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+cost);
    if(i>1&&j>1&&a[i-1]===b[j-2]&&a[i-2]===b[j-1])d[i][j]=Math.min(d[i][j],d[i-2][j-2]+1);
  }
  return d[a.length][b.length];
}

/** Minimal unique line-based hunk turning `before` into `after`. */
export function hunk(file,before,after){
  if(before===after)return null;
  const a=before.split('\n'),b=after.split('\n');
  let p=0;while(p<a.length&&p<b.length&&a[p]===b[p])p++;
  let s=0;while(s<a.length-p&&s<b.length-p&&a[a.length-1-s]===b[b.length-1-s])s++;
  for(let ctx=0;ctx<=6;ctx++){
    const from=Math.max(0,p-ctx),toA=Math.min(a.length,a.length-s+ctx),toB=Math.min(b.length,b.length-s+ctx);
    const oldText=a.slice(from,toA).join('\n'),newText=b.slice(from,toB).join('\n');
    if(oldText&&before.split(oldText).length===2)return {file,oldText,newText,line:from+1};
  }
  return {file,oldText:before,newText:after,line:1};
}

/** Undo one --unified=0 hunk of the parent->current change; null if it does not fit. */
export function revertHunk(current,parent,h){
  const cur=current.split('\n'),par=parent.split('\n');
  const cs=h.newCount===0?h.newStart:h.newStart-1;
  const ps=h.oldCount===0?h.oldStart:h.oldStart-1;
  if(cs<0||ps<0||cs+h.newCount>cur.length||ps+h.oldCount>par.length)return null;
  return [...cur.slice(0,cs),...par.slice(ps,ps+h.oldCount),...cur.slice(cs+h.newCount)].join('\n');
}

export function applyEdits(readFile,edits){
  const out=new Map();
  for(const e of edits){
    const current=out.get(e.file)??readFile(e.file);
    if(current==null)throw Error('Missing file '+e.file);
    if(current.split(e.oldText).length!==2)throw Error('Hunk is not unique in '+e.file);
    out.set(e.file,current.replace(e.oldText,()=>e.newText));
  }
  return out;
}

const lineAt=(text,line)=>text.split('\n')[line-1];
const replaceLine=(text,line,value)=>{const rows=text.split('\n');rows[line-1]=value;return rows.join('\n');};

function candidate(strategy,d,description,prior,file,before,after){
  const h=hunk(file,before,after);if(!h)return null;
  const edits=[h];
  return {id:sha256(strategy+'\u0000'+JSON.stringify(edits)).slice(0,16),strategy,diagnostic:d.fingerprint,
    diagnosticKind:d.kind,description,prior,edits};
}

/** `\n` typed as two characters outside string/char literals (historical CI failure 48bd150). */
export function unescapeCodeNewlines(line){
  let out='',inStr=false,inChar=false,changed=false;
  for(let i=0;i<line.length;i++){
    const c=line[i];
    if(inStr){out+=c;if(c==='\\'){out+=line[++i]??'';continue;}if(c==='"')inStr=false;continue;}
    if(inChar){out+=c;if(c==='\\'){out+=line[++i]??'';continue;}if(c==="'")inChar=false;continue;}
    if(c==='"'){inStr=true;out+=c;continue;}
    if(c==="'"){inChar=true;out+=c;continue;}
    if(c==='/'&&line[i+1]==='/'){out+=line.slice(i);break;}
    if(c==='\\'&&line[i+1]==='n'){out+='\n';i++;changed=true;continue;}
    out+=c;
  }
  return changed?out:null;
}

function kotlinEscapedNewline(d,text){
  const line=lineAt(text,d.line);if(line==null)return [];
  const fixed=unescapeCodeNewlines(line);if(fixed==null)return [];
  return [candidate('KOTLIN_ESCAPED_NEWLINE',d,'Replace literal "\\n" escapes outside string literals with line breaks',0.8,
    d.file,text,replaceLine(text,d.line,fixed))];
}

function kotlinKnownFix(d,text){
  const line=lineAt(text,d.line);if(line==null)return [];
  return knownFixes(line).filter(f=>line.split(f.from).length===2).map(f=>
    candidate('KOTLIN_KNOWN_FIX:'+f.kind,d,`Known fix ${f.kind}: ${f.from} -> ${f.to}`,0.75,d.file,text,replaceLine(text,d.line,line.replace(f.from,f.to))));
}

export function insertImport(text,fqn){
  if(new RegExp('^import\\s+'+fqn.replace(/\./g,'\\.')+'\\s*$','m').test(text))return null;
  const rows=text.split('\n');
  let last=-1,pkg=-1;
  rows.forEach((r,i)=>{if(/^import\s/.test(r))last=i;if(/^package\s/.test(r))pkg=i;});
  if(last>=0){
    // keep simple lexical order within the import block
    let at=last+1;for(let i=pkg+1;i<=last;i++)if(/^import\s/.test(rows[i])&&rows[i].slice(7).trim()>fqn){at=i;break;}
    rows.splice(at,0,'import '+fqn);
  }else rows.splice(pkg+1,0,'','import '+fqn);
  return rows.join('\n');
}

function kotlinMissingImport(d,text,index){
  const options=new Set();
  const pkg=(text.match(/^package\s+([\w.]+)/m)??[])[1]??'';
  const isTest=/(^|\/)test\//.test(d.file);
  for(const decl of index?.byName.get(d.symbol)??[]){
    if(decl.container||decl.package===pkg||!decl.package)continue;
    if(decl.test&&!isTest)continue;
    if(decl.visibility==='private')continue;
    options.add(decl.package+'.'+decl.name);
  }
  if(JDK_IMPORTS[d.symbol])options.add(JDK_IMPORTS[d.symbol]);
  const simpleAlreadyImported=new RegExp('^import\\s+[\\w.]+\\.'+d.symbol+'\\s*$','m').test(text);
  if(simpleAlreadyImported)return [];
  return [...options].slice(0,3).map(fqn=>{
    const after=insertImport(text,fqn);
    return after&&candidate('KOTLIN_MISSING_IMPORT',d,'Add import '+fqn,options.size===1?0.7:0.45,d.file,text,after);
  }).filter(Boolean);
}

function vocabulary(text,index,language){
  const counts=new Map();
  const words=language==='kotlin'?tokenize(text).filter(t=>t.type==='id').map(t=>t.text)
    :[...text.replace(/\/\/[^\n]*|\/\*[\s\S]*?\*\//g,' ').matchAll(/[A-Za-z_$][\w$]*/g)].map(m=>m[0]);
  for(const w of words)counts.set(w,(counts.get(w)??0)+1);
  for(const name of index?.byName?.keys()??[])if(!counts.has(name))counts.set(name,0);
  return counts;
}

export function nearestNames(symbol,vocab,limit=3){
  const maxDistance=symbol.length<=4?1:2;
  return [...vocab.entries()].filter(([w])=>w!==symbol&&Math.abs(w.length-symbol.length)<=maxDistance&&w.length>=3)
    .map(([w,c])=>({w,c,dist:editDistance(symbol,w)}))
    .filter(x=>x.dist<=maxDistance&&(x.c>0||x.dist===1))
    .sort((x,y)=>x.dist-y.dist||y.c-x.c||x.w.localeCompare(y.w)).slice(0,limit).map(x=>x.w);
}

function replaceTokenAt(line,symbol,col){
  const at=col?col-1:-1;
  if(at>=0&&line.slice(at,at+symbol.length)===symbol&&!/[\w$]/.test(line[at+symbol.length]??''))return [at,at+symbol.length];
  const m=new RegExp('(^|[^\\w$])('+symbol.replace(/\$/g,'\\$')+')(?![\\w$])').exec(line);
  return m?[m.index+m[1].length,m.index+m[1].length+symbol.length]:null;
}

function typoReference(d,text,index,language){
  const line=lineAt(text,d.line);if(line==null||!d.symbol)return [];
  const span=replaceTokenAt(line,d.symbol,d.col);if(!span)return [];
  return nearestNames(d.symbol,vocabulary(text,index,language)).map((name,rank)=>
    candidate(language==='kotlin'?'KOTLIN_TYPO_REFERENCE':'JS_TYPO_REFERENCE',d,`Replace unresolved '${d.symbol}' with nearby name '${name}'`,
      0.65-rank*0.1,d.file,text,replaceLine(text,d.line,line.slice(0,span[0])+name+line.slice(span[1]))));
}

function kotlinNullableString(d,text){
  if(d.actual!=='String?'||d.expected!=='String')return [];
  const line=lineAt(text,d.line);if(line==null)return [];
  let i=d.col-1,depth=0;
  if(!/[A-Za-z_]/.test(line[i]??''))return [];
  for(;i<line.length;i++){
    const c=line[i];
    if(c==='('||c==='[')depth++;
    else if(c===')'||c===']'){if(depth===0)break;depth--;}
    else if(depth===0&&(c===','||c===' '||c===';'))break;
  }
  const fixed=line.slice(0,i)+'.orEmpty()'+line.slice(i);
  return [candidate('KOTLIN_NULLABLE_STRING_DEFAULT',d,'Use orEmpty() where a non-null String is required',0.4,d.file,text,replaceLine(text,d.line,fixed))];
}

export function exportedNames(text){
  const names=new Set();
  for(const m of text.matchAll(/^export\s+(?:async\s+)?(?:function\*?|const|let|class)\s+([A-Za-z_$][\w$]*)/gm))names.add(m[1]);
  for(const m of text.matchAll(/^export\s*\{([^}]*)\}/gm))for(const part of m[1].split(','))names.add(part.trim().split(/\s+as\s+/).pop());
  return names;
}

function jsMissingImport(d,text,siblings){
  const out=[];
  for(const [file,content] of siblings){
    if(file===d.file||!exportedNames(content).has(d.symbol))continue;
    const spec='./'+file.split('/').pop();
    const existing=new RegExp("^import\\s*\\{([^}]*)\\}\\s*from\\s*['\"]"+spec.replace(/\./g,'\\.')+"['\"];?","m").exec(text);
    let after;
    if(existing)after=text.replace(existing[0],existing[0].replace('{'+existing[1]+'}','{'+existing[1].replace(/\s+$/,'')+','+d.symbol+'}'));
    else{
      const rows=text.split('\n');let last=-1;rows.forEach((r,i)=>{if(/^import\s/.test(r))last=i;});
      rows.splice(last+1,0,`import {${d.symbol}} from '${spec}';`);after=rows.join('\n');
    }
    // extending an existing import from that module is the most likely intent
    out.push(candidate('JS_MISSING_IMPORT',d,`Import ${d.symbol} from ${spec}`,existing?0.75:0.6,d.file,text,after));
  }
  return out.slice(0,2);
}

/**
 * @param diagnostics structured diagnostics of the failing base
 * @param ctx {readFile(rel), index (Kotlin), jsSiblings Map, changedInBase [{file,parent}], experience []}
 */
export function proposeCandidates(diagnostics,ctx){
  const out=[];const seen=new Set();
  const add=c=>{if(c&&!seen.has(c.id)){seen.add(c.id);out.push(c);}};
  for(const d of diagnostics.slice(0,12)){
    // experience replay: a verified hunk for the same problem identity
    for(const past of ctx.experience??[]){
      // Ledger receipts without an independently accepted patch digest are
      // diagnostic history, not reusable code. Never replay pending/forged wins.
      if(past.outcome!=='VERIFIED'||!/^[a-f0-9]{64}$/.test(past.patchSha256??'')||
         past.diagnostic!==d.fingerprint||past.edits.some(edit=>edit.omitted)||
         (past.strategy==='LOCAL_MODEL_PATCH'&&(ctx.allowLocalModelPatches!==true||
           !past.edits.every(edit=>ctx.allowedLocalPaths?.includes(edit.file)))))continue;
      if(past.strategy==='LOCAL_MODEL_PATCH'&&(past.edits.length!==1||
         ctx.readFile(past.edits[0].file)!==past.edits[0].oldText))continue;
      try{applyEdits(ctx.readFile,past.edits);
        add({id:sha256('EXPERIENCE\u0000'+JSON.stringify(past.edits)).slice(0,16),strategy:past.strategy==='LOCAL_MODEL_PATCH'?'LOCAL_MODEL_PATCH':'EXPERIENCE_REPLAY',
          diagnostic:d.fingerprint,diagnosticKind:d.kind,description:'Replay patch verified for the same diagnostic in '+(past.baseCommit??'').slice(0,12),
          prior:0.9,edits:past.edits});}catch{}
    }
    if(!d.file)continue;
    const text=ctx.readFile(d.file);if(text==null)continue;
    const isKotlin=/\.kts?$/.test(d.file),isJs=/\.m?js$/.test(d.file);
    if(isKotlin&&d.kind==='SYNTAX_ERROR')kotlinEscapedNewline(d,text).forEach(add);
    if(isKotlin&&d.kind==='UNRESOLVED_REFERENCE'){
      kotlinKnownFix(d,text).forEach(add);
      const imports=kotlinMissingImport(d,text,ctx.index);imports.forEach(add);
      // A name that exists verbatim elsewhere is far more likely missing an import than misspelled.
      typoReference(d,text,ctx.index,'kotlin').map(c=>imports.length?{...c,prior:c.prior*0.5}:c).forEach(add);
    }
    if(isKotlin&&d.kind==='TYPE_MISMATCH')kotlinNullableString(d,text).forEach(add);
    if(isJs&&d.kind==='REFERENCE_ERROR'){
      const line=lineAt(text,d.line);
      const span=line&&d.symbol?replaceTokenAt(line,d.symbol,d.col):null;
      // Opt-in ranking precondition from the actual diagnostic location. Unknown
      // locations keep the old policy. This is not a semantic cause classifier.
      const contextKey=ctx.contextualExperience===true&&ctx.chain==='node'&&span?
        'node.js.'+(/^\s*\(/.test(line.slice(span[1]))?'call':'value'):null;
      const scoped=c=>contextKey?{...c,contextKey}:c;
      const imports=jsMissingImport(d,text,ctx.jsSiblings??new Map());imports.map(scoped).forEach(add);
      typoReference(d,text,null,'js').map(c=>scoped(imports.length?{...c,prior:c.prior*0.5}:c)).forEach(add);
    }
  }
  // Regression culprit search, smallest unit first: undo ONE hunk of the failing
  // commit (closest to a diagnostic first), so unrelated changes in the same
  // file survive. Whole-file revert remains the fallback.
  if(diagnostics.length){
    let hunkCandidates=0;
    for(const change of (ctx.changedInBase??[]).slice(0,4)){
      if(change.parent==null||!(change.hunks?.length>1))continue;
      const current=ctx.readFile(change.file);if(current==null||current===change.parent)continue;
      const lines=diagnostics.filter(d=>d.file===change.file&&d.line).map(d=>d.line);
      const distance=h=>lines.length?Math.min(...lines.map(l=>l>=h.newStart&&l<h.newStart+Math.max(1,h.newCount)?0:
        Math.min(Math.abs(l-h.newStart),Math.abs(l-(h.newStart+Math.max(0,h.newCount-1)))))):Infinity;
      const ordered=change.hunks.map((h,i)=>({h,i,d:distance(h)})).sort((a,b)=>a.d-b.d||a.i-b.i);
      for(const {h} of ordered.slice(0,3)){
        if(hunkCandidates>=4)break;
        const reverted=revertHunk(current,change.parent,h);
        if(reverted==null||reverted===current)continue;
        const near=diagnostics.find(d=>d.file===change.file)??diagnostics[0];
        add(candidate('REVERT_HUNK_TO_PARENT',near,`Restore lines ${h.newStart}-${h.newStart+Math.max(0,h.newCount-1)} of ${change.file} to the parent commit (smallest culprit)`,
          lines.length&&distance(h)===0?0.45:0.35,change.file,current,reverted));
        hunkCandidates++;
      }
    }
    for(const change of (ctx.changedInBase??[]).slice(0,4)){
      if(change.parent==null)continue;
      const current=ctx.readFile(change.file);if(current==null||current===change.parent)continue;
      const fp=diagnostics[0];
      add(candidate('REVERT_FILE_TO_PARENT',fp,`Restore ${change.file} to the parent commit (regression culprit search)`,0.25,
        change.file,current,change.parent));
    }
  }
  return out;
}


/**
 * Research-only inverse mutation planner. This function never writes files,
 * invokes tools, records training success or modifies a Git checkout.
 *
 * A generated mutation is NOT a proven bug: an isolated compiler run must first
 * establish green -> red, and replay of the restoration must establish red -> green.
 * Existing repair proof and owner-review gates remain authoritative.
 */
export function planInverseKotlinImportMutation({file,source,importFqn}={}){
  if(typeof file!=='string'||
     !/^app\/src\/main\/(?:java|kotlin)\/(?:[A-Za-z0-9_-]+\/)+[A-Za-z0-9_-]+\.kt$/.test(file))
    throw Error('INVERSE_MUTATION_UNSAFE_PATH');
  if(typeof source!=='string'||source.length===0||source.length>250_000)
    throw Error('INVERSE_MUTATION_INVALID_SOURCE');
  if(typeof importFqn!=='string')throw Error('INVERSE_MUTATION_INVALID_IMPORT');
  const entries=Object.entries(JDK_IMPORTS).filter(([,fqn])=>fqn===importFqn);
  if(entries.length!==1)return null;
  const [symbol]=entries[0];
  const rows=source.split('\n');
  const matches=[];
  for(let i=0;i<rows.length;i++)
    if(rows[i].trim()==='import '+importFqn)matches.push(i);
  if(matches.length!==1)return null;
  const importIndex=matches[0];
  // Lexer excludes comments and plain strings. A reference outside the import
  // is a necessary condition, not sufficient proof that removal breaks a build.
  const references=tokenize(source,{maxTokens:100_000}).filter(
    token=>token.type==='id'&&token.text===symbol&&token.line!==importIndex+1);
  if(references.length===0)return null;
  const mutatedRows=rows.slice();
  mutatedRows[importIndex]='';
  const mutated=mutatedRows.join('\n');
  const mutation=hunk(file,source,mutated);
  const restoration=hunk(file,mutated,source);
  if(!mutation||!restoration)return null;
  // Both edits must be exact and reversible. Do not emit full-file fallbacks.
  if(mutation.oldText===source||restoration.oldText===mutated)return null;
  const altered=applyEdits(p=>p===file?source:null,[mutation]).get(file);
  const restored=applyEdits(p=>p===file?altered:null,[restoration]).get(file);
  if(altered!==mutated||restored!==source)
    throw Error('INVERSE_MUTATION_NON_REVERSIBLE');
  return {
    schemaVersion:1,
    kind:'INVERSE_KOTLIN_MISSING_IMPORT',
    file,
    symbol,
    importFqn,
    baselineSha256:sha256(source),
    mutatedSha256:sha256(mutated),
    mutation,
    restoration,
    expectedDiagnosticKind:'UNRESOLVED_REFERENCE',
    proofStatus:'UNVERIFIED_REQUIRES_REAL_COMPILER_AND_TESTS'
  };
}
