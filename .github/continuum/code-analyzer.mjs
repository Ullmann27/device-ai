import fs from 'node:fs';
import path from 'node:path';
import {tokenize} from './kotlin-tokenizer.mjs';

/**
 * Read-only structural Kotlin analysis (no execution of repository code).
 *
 * Builds a symbol index from a real lexer (comments and string text are not
 * references; ${...} templates are), resolves type names through package,
 * explicit/star imports and default imports, links each reference to its
 * enclosing declaration and computes reachability from Android entry points
 * declared in AndroidManifest.xml (production) and from test classes.
 *
 * Name resolution is NOT type inference: member calls (`x.foo()`) resolve by
 * name to every member called `foo`. That over-approximates reachability, so
 * an UNREFERENCED/TEST_ONLY finding is evidence, never proof.
 */
const MAX_FILES=3000, MAX_BYTES=1000000;
const DECL_KW=new Set(['class','interface','object','fun','val','var','typealias']);
const MODIFIERS=new Set(['private','protected','internal','public','override','open','abstract','final','data','enum',
 'sealed','inner','companion','inline','suspend','operator','infix','tailrec','external','const','lateinit','annotation',
 'value','expect','actual','vararg','crossinline','noinline','reified','fun']);

function declarationName(tokens,k){
  // tokens[k] is the declaration keyword; skip generics and receivers to the name.
  const kw=tokens[k].text;let j=k+1;
  if(tokens[j]?.text==='<'){let d=0;for(;j<tokens.length;j++){if(tokens[j].text==='<')d++;else if(tokens[j].text==='>'){d--;if(d===0){j++;break;}}}}
  if(kw==='val'||kw==='var'){
    if(tokens[j]?.text==='(')return null; // destructuring
  }
  if(kw==='fun'||kw==='val'||kw==='var'){
    // receiver: Type. or Type<..>. before the name
    let last=j;
    for(let m=j;m<tokens.length&&m<j+40;m++){
      const t=tokens[m];
      if(t.type==='id'&&tokens[m+1]?.text==='.'){last=m+2;continue;}
      if(t.text==='<'){let d=0;for(;m<tokens.length;m++){if(tokens[m].text==='<')d++;else if(tokens[m].text==='>'){d--;if(d===0)break;}}if(tokens[m+1]?.text==='.'){last=m+2;continue;}break;}
      if(t.text==='?'&&tokens[m+1]?.text==='.'){last=m+2;continue;}
      break;
    }
    j=last;
  }
  const t=tokens[j];
  return t&&t.type==='id'?{name:t.text,index:j}:null;
}

/** Parse one Kotlin file into package, imports, declarations and references. */
export function indexFile(file){
  const tokens=tokenize(file.content);
  let pkg='';const imports=[];const declarations=[];const references=[];
  const scope=[];// {decl, depth}
  let depth=0,paren=0;let pendingDecl=null;const declNameIdx=new Set();
  for(let k=0;k<tokens.length;k++){
    const t=tokens[k];
    if(t.type==='kw'&&t.text==='package'&&depth===0){
      const parts=[];let j=k+1;
      for(;tokens[j]&&tokens[j].line===t.line;j++)if(tokens[j].type==='id'||tokens[j].type==='kw')parts.push(tokens[j].text);
      pkg=parts.join('.');k=j-1;continue;
    }
    if(t.type==='kw'&&t.text==='import'&&depth===0){
      const parts=[];let star=false,alias=null;let j=k+1;
      for(;tokens[j]&&tokens[j].line===t.line;j++){
        const x=tokens[j];
        if(x.text==='as'&&tokens[j+1]?.type==='id'){alias=tokens[j+1].text;j++;continue;}
        if(x.text==='*')star=true;
        else if(x.type==='id'||x.type==='kw')parts.push(x.text);
      }
      imports.push({path:parts.join('.'),star,alias,line:t.line});k=j-1;continue;
    }
    if(t.type==='op'&&t.text==='('){paren++;continue;}
    if(t.type==='op'&&t.text===')'){paren=Math.max(0,paren-1);continue;}
    if(t.type==='op'&&t.text==='{'){
      depth++;
      // braces inside a header's parentheses are default-value lambdas, not the body
      if(pendingDecl&&paren===pendingDecl.paren){scope.push({decl:pendingDecl,depth});pendingDecl=null;}
      continue;
    }
    if(t.type==='op'&&t.text==='}'){
      if(scope.length&&scope[scope.length-1].depth===depth)scope.pop();
      depth=Math.max(0,depth-1);if(pendingDecl&&paren===pendingDecl.paren)pendingDecl=null;continue;
    }
    if(t.type==='kw'&&DECL_KW.has(t.text)){
      // `fun interface X`, `companion object` without name, `object :` expressions
      if(t.text==='fun'&&tokens[k+1]?.text==='interface')continue;
      const found=declarationName(tokens,k);
      if(t.text==='object'&&(!found||tokens[k+1]?.text===':'||tokens[k+1]?.text==='{'))continue;
      if(!found)continue;
      // a property at the header's nesting level ends a body-less class/fun header
      if((t.text==='val'||t.text==='var')&&pendingDecl&&paren===pendingDecl.paren)pendingDecl=null;
      const mods=new Set();for(let m=k-1;m>=0&&m>=k-8;m--){if((tokens[m].type==='kw'||tokens[m].type==='id')&&MODIFIERS.has(tokens[m].text))mods.add(tokens[m].text);else if(tokens[m].type==='annotation'){mods.add('@'+tokens[m].text);}else break;}
      // constructor properties belong to the class whose header is still open
      const header=pendingDecl&&['class','enum'].includes(pendingDecl.kind)&&paren>pendingDecl.paren?pendingDecl:null;
      const container=header??(scope.length?scope[scope.length-1].decl:null);
      // locals inside function bodies are not part of the API graph
      if(container&&container.kind==='fun'&&t.text!=='class'&&t.text!=='object'&&t.text!=='interface')continue;
      const kind=t.text==='typealias'?'typealias':t.text;
      const decl={name:found.name,kind:mods.has('enum')?'enum':kind,file:file.path,package:pkg,line:t.line,
        container:container?container.qualified:null,qualified:(container?container.qualified:(pkg?pkg+'.':''))+(container?'.':'')+found.name,
        override:mods.has('override'),visibility:mods.has('private')?'private':mods.has('internal')?'internal':'public',
        annotations:[...mods].filter(x=>x.startsWith('@')).map(x=>x.slice(1)),test:/(^|\/)test\//.test(file.path)};
      declarations.push(decl);declNameIdx.add(found.index);
      if(['class','interface','object','enum','fun'].includes(decl.kind)){pendingDecl=decl;decl.paren=paren;}
      continue;
    }
    if(t.type==='op'&&(t.text==='='||t.text===';')&&pendingDecl&&pendingDecl.kind==='fun'&&paren===pendingDecl.paren){
      // expression-bodied function: treat the rest of the line as its body
      const decl=pendingDecl;pendingDecl=null;const lineNo=t.line;
      for(let m=k+1;m<tokens.length&&tokens[m].line===lineNo;m++){
        if(tokens[m].type==='id')references.push({name:tokens[m].text,line:tokens[m].line,col:tokens[m].col,from:decl.qualified,qualifier:tokens[m-1]?.text==='.'||tokens[m-1]?.text==='::'?tokens[m-2]?.text:null});
      }
    }
    if(t.type==='id'&&!declNameIdx.has(k)){
      const owner=scope.length?scope[scope.length-1].decl.qualified:null;
      references.push({name:t.text,line:t.line,col:t.col,from:owner,qualifier:tokens[k-1]?.text==='.'||tokens[k-1]?.text==='::'?tokens[k-2]?.text:null});
    }
    if(t.type==='annotation'){
      references.push({name:t.text,line:t.line,col:t.col,from:scope.length?scope[scope.length-1].decl.qualified:null,qualifier:null});
    }
  }
  return {path:file.path,package:pkg,imports,declarations,references};
}

const DEFAULT_IMPORT_PREFIXES=['kotlin','kotlin.collections','kotlin.io','kotlin.text','kotlin.ranges','kotlin.sequences','java.lang'];

/** Index all files and resolve references to declarations. */
export function buildIndex(files){
  if(files.length>MAX_FILES)throw Error('Too many source files');
  const parsed=[];
  for(const file of files){
    if(!file.path.endsWith('.kt'))continue;
    if(Buffer.byteLength(file.content,'utf8')>MAX_BYTES)
      throw Error('Source byte limit exceeded: '+file.path);
    parsed.push(indexFile(file));
  }
  const byName=new Map();const byQualified=new Map();
  for(const f of parsed)for(const d of f.declarations){
    if(!byName.has(d.name))byName.set(d.name,[]);
    byName.get(d.name).push(d);byQualified.set(d.qualified,d);
  }
  const edges=new Map();// from qualified (or file:<path>) -> Set(qualified)
  const incoming=new Map();
  const addEdge=(from,to)=>{if(from===to)return;if(!edges.has(from))edges.set(from,new Set());edges.get(from).add(to);
    if(!incoming.has(to))incoming.set(to,new Set());incoming.get(to).add(from);};
  for(const f of parsed){
    const explicit=new Map();const stars=[];
    for(const imp of f.imports){
      if(imp.star)stars.push(imp.path);
      else explicit.set(imp.alias??imp.path.split('.').pop(),imp.path);
    }
    for(const ref of f.references){
      const candidates=byName.get(ref.name);if(!candidates)continue;
      const from=ref.from??('file:'+f.path);
      const imported=explicit.get(ref.name);
      let targets;
      if(imported)targets=candidates.filter(d=>d.qualified===imported||d.qualified.startsWith(imported+'.'));
      else{
        targets=candidates.filter(d=>{
          if(d.container)return true; // member or nested: name-based over-approximation
          if(d.package===f.package)return true;
          if(stars.includes(d.package))return true;
          return false;
        });
        // a reference qualified by a known type name narrows to that container
        if(ref.qualifier){const narrowed=targets.filter(d=>d.container&&d.container.split('.').pop()===ref.qualifier);if(narrowed.length)targets=narrowed;}
      }
      if(targets.length===0&&!imported){
        // fully qualified usage such as at.heinz.deviceai.learning.Foo(...)
        targets=candidates.filter(d=>!d.container&&ref.qualifier&&d.package.split('.').pop()===ref.qualifier);
      }
      for(const d of targets)addEdge(from,d.qualified);
    }
  }
  // Structural edges: a container keeps its overrides (called by framework or
  // interface dispatch), constructors' init blocks, and its companion members.
  for(const f of parsed)for(const d of f.declarations){
    if(d.container&&(d.override||d.kind==='object'))addEdge(d.container,d.qualified);
    if(d.container&&d.annotations.includes('Test'))addEdge(d.container,d.qualified);
    if(d.container)addEdge(d.qualified,d.container); // using a member needs its owner
  }
  return {files:parsed,byName,byQualified,edges,incoming};
}

/** AndroidManifest components (activities, services, providers, receivers). */
export function manifestRoots(xml,namespace){
  const roots=[];
  for(const m of xml.matchAll(/<(activity|service|provider|receiver)\b[^>]*?android:name="([^"]+)"/g)){
    const name=m[2].startsWith('.')?namespace+m[2]:m[2];
    roots.push({component:m[1],name});
  }
  return roots;
}

export function reachable(index,roots){
  const seen=new Set();const queue=[];
  for(const r of roots)if(index.byQualified.has(r)||r.startsWith('file:')){seen.add(r);queue.push(r);}
  while(queue.length){
    const cur=queue.shift();
    for(const next of index.edges.get(cur)??[])if(!seen.has(next)){seen.add(next);queue.push(next);}
  }
  return seen;
}

/** Shortest reference chain between two declarations (qualified or simple names). */
export function callChain(index,from,to,{maxDepth=24}={}){
  const resolve=name=>index.byQualified.has(name)?[name]:(index.byName.get(name.split('.').pop())??[])
    .filter(d=>d.qualified.endsWith(name)).map(d=>d.qualified);
  const sources=resolve(from),targets=new Set(resolve(to));
  if(!sources.length||!targets.size)return null;
  const prev=new Map();const queue=sources.map(s=>[s,0]);sources.forEach(s=>prev.set(s,null));
  while(queue.length){
    const [cur,d]=queue.shift();
    if(targets.has(cur)){const chain=[];for(let x=cur;x!==null;x=prev.get(x))chain.unshift(x);
      return chain.map(q=>{const decl=index.byQualified.get(q);return {symbol:q,file:decl?.file,line:decl?.line};});}
    if(d>=maxDepth)continue;
    for(const next of index.edges.get(cur)??[])if(!prev.has(next)){prev.set(next,cur);queue.push([next,d+1]);}
  }
  return null;
}

/**
 * Integration findings. Production roots: manifest components plus `fun main`
 * research tools. Test roots: declarations in /test/ sources.
 *   UNREFERENCED  no reference from anywhere (high confidence)
 *   TEST_ONLY     only reachable from tests, not from any app entry point (medium)
 *   DEAD_CLUSTER  referenced, but only from other unreachable code (medium)
 */
export function integrationFindings(index,{productionRoots=[]}={}){
  const all=[...index.byQualified.values()];
  const fileRoots=index.files.filter(f=>/(^|\/)test\//.test(f.path)).map(f=>'file:'+f.path);
  const testRoots=[...all.filter(d=>d.test).map(d=>d.qualified),...fileRoots];
  const mains=all.filter(d=>d.kind==='fun'&&d.name==='main'&&!d.container&&!d.test).map(d=>d.qualified);
  const prod=reachable(index,[...productionRoots,...mains]);
  const tests=reachable(index,testRoots);
  const findings=[];
  for(const d of all){
    if(d.test||d.container||prod.has(d.qualified))continue; // top-level production declarations only
    const refs=[...(index.incoming.get(d.qualified)??[])].filter(x=>x!==d.qualified);
    const kind=refs.length===0?'UNREFERENCED':tests.has(d.qualified)?'TEST_ONLY':'DEAD_CLUSTER';
    findings.push({kind,symbol:d.qualified,declaration:d.kind,file:d.file,line:d.line,
      confidence:kind==='UNREFERENCED'?'high':'medium',referencedBy:refs.slice(0,6),
      note:'Static name resolution; reflection, manifests outside this scan and dynamic class names are not modeled.'});
  }
  findings.sort((a,b)=>a.kind.localeCompare(b.kind)||a.file.localeCompare(b.file)||a.line-b.line);
  return {productionReachable:prod.size,testReachable:tests.size,findings};
}

/** Backwards-compatible entry used by the existing workflow and tests. */
export function analyzeSources(files,{productionRoots=null}={}){
  if(files.length>MAX_FILES)throw Error('Too many source files');
  const index=buildIndex(files);
  const declarations=[...index.byQualified.values()];
  let findings;
  if(productionRoots){
    findings=integrationFindings(index,{productionRoots}).findings;
  }else{
    // Without entry points only "no references at all" is meaningful.
    findings=declarations.filter(d=>!d.test&&!d.container&&!(index.incoming.get(d.qualified)?.size))
      .map(d=>({kind:'POSSIBLY_UNUSED',symbol:d.name,qualified:d.qualified,definedIn:[d.file],line:d.line,confidence:'low',
        note:'No references found; reflection, DI, manifests and resources may still use this declaration.'}));
  }
  return {schemaVersion:2,filesAnalyzed:index.files.length,declarations:declarations.length,findings:findings.slice(0,400)};
}

export function collectKotlin(root){
  const files=[],stack=[root];
  while(stack.length){
    const dir=stack.pop();
    for(const entry of fs.readdirSync(dir,{withFileTypes:true})){
      if(entry.isSymbolicLink())continue;
      const full=path.join(dir,entry.name);
      if(entry.isDirectory()){if(entry.name!=='build'&&!entry.name.startsWith('.'))stack.push(full);}
      else if(entry.isFile()&&entry.name.endsWith('.kt')){
        if(files.length>=MAX_FILES)throw Error('Source file limit exceeded');
        if(fs.statSync(full).size>MAX_BYTES)throw Error('Source byte limit exceeded: '+full);
        files.push({path:path.relative(root,full).replaceAll(path.sep,'/'),content:fs.readFileSync(full,'utf8')});
      }
    }
  }
  return files.sort((a,b)=>a.path.localeCompare(b.path));
}

export function analyzeRepository(root){
  const src=path.join(root,'app/src');
  const files=collectKotlin(src);
  const manifestFile=path.join(src,'main/AndroidManifest.xml');
  const gradle=fs.existsSync(path.join(root,'app/build.gradle.kts'))?fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8'):'';
  const namespace=(gradle.match(/namespace\s*=\s*"([^"]+)"/)??[])[1]??'';
  const roots=fs.existsSync(manifestFile)?manifestRoots(fs.readFileSync(manifestFile,'utf8'),namespace):[];
  const index=buildIndex(files);
  const integration=integrationFindings(index,{productionRoots:roots.map(r=>r.name)});
  return {schemaVersion:2,filesAnalyzed:index.files.length,declarations:index.byQualified.size,
    entryPoints:roots,...integration,index};
}

if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
  const args=process.argv.slice(2);
  const chainAt=args.indexOf('--chain');
  // the two symbols after --chain are not the repository path (without --chain every positional argument may be)
  const chainArgs=chainAt>=0?[chainAt+1,chainAt+2]:[];
  const root=path.resolve(args.find((a,i)=>!a.startsWith('--')&&!chainArgs.includes(i))??'.');
  const result=analyzeRepository(fs.existsSync(path.join(root,'app/src'))?root:path.resolve(root,'..','..'));
  if(chainAt>=0){
    const chain=callChain(result.index,args[chainAt+1],args[chainAt+2]);
    console.log(chain?chain.map(x=>`${x.symbol} (${x.file}:${x.line})`).join('\n  -> '):'No static reference chain found.');
  }else{
    const {index,...report}=result;
    fs.mkdirSync('developer-agent-report',{recursive:true});
    fs.writeFileSync('developer-agent-report/code-analysis.json',JSON.stringify(report,null,2)+'\n');
    const count=k=>report.findings.filter(f=>f.kind===k).length;
    console.log(`Analyzed ${report.filesAnalyzed} Kotlin files, ${report.declarations} declarations, ${report.entryPoints.length} manifest entry points.`);
    console.log(`Production-reachable: ${report.productionReachable}; UNREFERENCED ${count('UNREFERENCED')}, TEST_ONLY ${count('TEST_ONLY')}, DEAD_CLUSTER ${count('DEAD_CLUSTER')}.`);
  }
}
