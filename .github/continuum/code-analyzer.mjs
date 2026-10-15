import fs from 'node:fs';
import path from 'node:path';

/** Read-only structural Kotlin analyzer; no execution of repository code. */
const MAX_FILES=3000, MAX_BYTES=250000;
export function analyzeSources(files) {
  if(files.length>MAX_FILES)throw Error('Too many source files');
  const declarations=new Map(), references=new Map(), findings=[];
  for(const file of files){
    if(!file.path.endsWith('.kt')||file.content.length>MAX_BYTES)continue;
    const stripped=file.content.replace(/\/\*[\s\S]*?\*\//g,' ').replace(/\/\/[^\n]*/g,' ');
    for(const match of stripped.matchAll(/\b(?:class|object|interface)\s+([A-Z][A-Za-z0-9_]*)/g)){
      const name=match[1];if(!declarations.has(name))declarations.set(name,[]);
      declarations.get(name).push(file.path);
    }
    references.set(file.path,stripped);
  }
  for(const [name,owners] of declarations){
    const pattern=new RegExp('\\b'+name+'\\b','g');
    const consumers=[];
    for(const [filename,source] of references){
      if(owners.includes(filename))continue;
      if(pattern.test(source))consumers.push(filename);
      pattern.lastIndex=0;
    }
    if(!consumers.length && !name.endsWith('Test') && !owners.some(x=>x.includes('/test/')))
      findings.push({kind:'POSSIBLY_UNUSED',symbol:name,definedIn:owners,confidence:'low',
        note:'Textual references absent; reflection, DI, manifests and resources may still use this type.'});
  }
  return {schemaVersion:1,filesAnalyzed:references.size,declarations:declarations.size,
    findings:findings.slice(0,200)};
}
export function collectKotlin(root){
  const files=[],stack=[root];
  while(stack.length){
    const dir=stack.pop();
    for(const entry of fs.readdirSync(dir,{withFileTypes:true})){
      if(entry.isSymbolicLink())continue;
      const full=path.join(dir,entry.name);
      if(entry.isDirectory())stack.push(full);
      else if(entry.isFile()&&entry.name.endsWith('.kt')){
        if(files.length>=MAX_FILES)throw Error('Source file limit exceeded');
        if(fs.statSync(full).size<=MAX_BYTES)
          files.push({path:path.relative(root,full).replaceAll(path.sep,'/'),content:fs.readFileSync(full,'utf8')});
      }
    }
  }
  return files;
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
  const root=path.resolve(process.argv[2]??'app/src');
  const result=analyzeSources(collectKotlin(root));
  fs.mkdirSync('developer-agent-report',{recursive:true});
  fs.writeFileSync('developer-agent-report/code-analysis.json',JSON.stringify(result,null,2)+'\n');
  console.log('Analyzed',result.filesAnalyzed,'Kotlin files;',result.findings.length,'potentially unused declarations');
}
