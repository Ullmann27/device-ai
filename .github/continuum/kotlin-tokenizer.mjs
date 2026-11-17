// Bounded Kotlin lexer for read-only structural analysis. It understands nested
// block comments, escaped/raw strings with ${...} templates, char literals and
// backtick identifiers, so identifiers inside comments or plain string text are
// never mistaken for code references, while template expressions are.
// Hard keywords only (plus import): soft keywords and modifiers such as `value`,
// `data`, `get` or `field` are valid identifiers and must stay references.
const KEYWORDS=new Set(('as break class continue do else false for fun if in interface is null object package '+
 'return super this throw true try typealias typeof val var when while import').split(' '));
export const isKeyword=word=>KEYWORDS.has(word);

const isIdStart=c=>/[\p{L}_]/u.test(c);
const isIdPart=c=>/[\p{L}\p{N}_]/u.test(c);

export function tokenize(source,{maxTokens=400000}={}){
  if(typeof source!=='string')throw Error('Source must be text');
  const out=[];let i=0,line=1,col=1;
  const n=source.length;
  const advance=k=>{for(let j=0;j<k;j++){if(source[i]==='\n'){line++;col=1;}else col++;i++;}};
  const push=(type,text,l,c)=>{if(out.length>=maxTokens)throw Error('Token limit exceeded');out.push({type,text,line:l,col:c});};
  // Template expression inside a string: lex the code between braces recursively.
  function templateExpression(){
    const l=line,c=col;advance(2);let depth=1;const start=i,sl=l,sc=c+2;
    while(i<n&&depth>0){
      const ch=source[i];
      if(ch==='"'){skipString(false);continue;}
      if(ch==='{')depth++;else if(ch==='}'){depth--;if(depth===0)break;}
      advance(1);
    }
    const inner=source.slice(start,i);
    for(const t of tokenize(inner,{maxTokens:maxTokens-out.length})){
      push(t.type,t.text,t.line+sl-1,t.line===1?t.col+sc-1:t.col);
    }
    if(i<n)advance(1);
  }
  function skipString(emit){
    const l=line,c=col;
    const raw=source.startsWith('"""',i);
    advance(raw?3:1);
    while(i<n){
      if(raw&&source.startsWith('"""',i)){
        while(source.startsWith('""""',i))advance(1); // trailing quotes belong to content
        advance(3);break;
      }
      const ch=source[i];
      if(!raw&&ch==='\\'){advance(2);continue;}
      if(!raw&&(ch==='"')){advance(1);break;}
      if(!raw&&ch==='\n')break; // unterminated: stop at line end, compiler reports it
      if(ch==='$'&&source[i+1]==='{'&&emit){templateExpression();continue;}
      if(ch==='$'&&emit&&isIdStart(source[i+1]??'')){
        advance(1);const tl=line,tc=col;let j=i;while(j<n&&isIdPart(source[j]))j++;
        push('id',source.slice(i,j),tl,tc);advance(j-i);continue;
      }
      advance(1);
    }
    if(emit)push('str','',l,c);
  }
  while(i<n){
    const ch=source[i];
    if(ch==='\n'||ch===' '||ch==='\t'||ch==='\r'||ch==='\f'){advance(1);continue;}
    if(ch==='/'&&source[i+1]==='/'){while(i<n&&source[i]!=='\n')advance(1);continue;}
    if(ch==='/'&&source[i+1]==='*'){
      let depth=0;
      do{
        if(source.startsWith('/*',i)){depth++;advance(2);}
        else if(source.startsWith('*/',i)){depth--;advance(2);}
        else advance(1);
      }while(i<n&&depth>0);
      continue;
    }
    if(ch==='"'){skipString(true);continue;}
    if(ch==="'"){
      const l=line,c=col;advance(1);
      while(i<n&&source[i]!=="'"&&source[i]!=='\n'){if(source[i]==='\\')advance(1);advance(1);}
      if(source[i]==="'")advance(1);
      push('char','',l,c);continue;
    }
    if(ch==='`'){
      const l=line,c=col;advance(1);const start=i;
      while(i<n&&source[i]!=='`'&&source[i]!=='\n')advance(1);
      push('id',source.slice(start,i),l,c);if(source[i]==='`')advance(1);continue;
    }
    if(/[0-9]/.test(ch)){
      const l=line,c=col;let j=i;
      while(j<n&&/[0-9a-zA-Z_.]/.test(source[j])){if(source[j]==='.'&&!/[0-9]/.test(source[j+1]??''))break;j++;}
      push('num',source.slice(i,j),l,c);advance(j-i);continue;
    }
    if(isIdStart(ch)){
      const l=line,c=col;let j=i;while(j<n&&isIdPart(source[j]))j++;
      const word=source.slice(i,j);push(KEYWORDS.has(word)?'kw':'id',word,l,c);advance(j-i);continue;
    }
    if(ch==='@'){
      const l=line,c=col;advance(1);
      if(isIdStart(source[i]??'')){let j=i;while(j<n&&isIdPart(source[j]))j++;push('annotation',source.slice(i,j),l,c);advance(j-i);}
      continue;
    }
    const three=source.slice(i,i+3),two=source.slice(i,i+2);
    const op=['===','!==','...','?.:'].includes(three)?three:
      ['::','->','?.','?:','!!','&&','||','==','!=','<=','>=','+=','-=','*=','/=','%=','++','--','..'].includes(two)?two:ch;
    push('op',op,line,col);advance(op.length);
  }
  return out;
}
