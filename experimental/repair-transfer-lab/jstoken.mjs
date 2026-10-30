/** Minimal JavaScript tokenizer for the repair-transfer lab: enough to edit operators, numbers and '!' in real
 * project code without ever touching strings, template text, comments or regular-expression literals.
 * Tokens: {type: ident|keyword|num|str|template|regex|punct, value, start, end, line}. Not a parser. */
const KEYWORDS=new Set(['await','break','case','catch','class','const','continue','debugger','default','delete','do',
 'else','export','extends','finally','for','function','if','import','in','instanceof','let','new','of','return',
 'static','super','switch','this','throw','try','typeof','var','void','while','with','yield','async','null','true',
 'false','undefined']);
// keywords after which '/' starts a regular expression
const REGEX_AFTER_KEYWORD=new Set(['return','typeof','case','in','of','instanceof','new','delete','void','throw','else','do','yield','await']);
const PUNCT=['>>>=','...','===','!==','**=','<<=','>>=','>>>','&&=','||=','??=','=>','==','!=','<=','>=','&&','||','??',
 '?.','++','--','+=','-=','*=','/=','%=','&=','|=','^=','**','<<','>>','{','}','(',')','[',']',';',',','<','>','+','-',
 '*','/','%','&','|','^','!','~','?',':','=','.','@','#'];

export function tokenize(src) {
 if(typeof src!=='string')throw Error('TOKENIZE_INPUT');
 const out=[];let i=0,line=1;
 const push=(type,start,end)=>out.push({type,value:src.slice(start,end),start,end,line});
 const countLines=(a,b)=>{for(let k=a;k<b;k++)if(src[k]==='\n')line++;};
 function skipString(q,k){k++;while(k<src.length&&src[k]!==q){if(src[k]==='\\')k++;else if(src[k]==='\n'&&q!=='`')throw Error('UNTERMINATED_STRING');k++;}
  if(k>=src.length)throw Error('UNTERMINATED_STRING');return k+1;}
 function skipTemplate(k){ // k at '`'
  k++;
  while(k<src.length){
   const c=src[k];
   if(c==='\\'){k+=2;continue;}
   if(c==='`')return k+1;
   if(c==='$'&&src[k+1]==='{'){k=skipExpression(k+2);continue;}
   k++;
  }
  throw Error('UNTERMINATED_TEMPLATE');
 }
 function skipExpression(k){ // inside ${ ... }, returns index after matching '}'
  let depth=1;
  while(k<src.length){
   const c=src[k];
   if(c==="'"||c==='"'){k=skipString(c,k);continue;}
   if(c==='`'){k=skipTemplate(k);continue;}
   if(c==='/'&&src[k+1]==='/'){while(k<src.length&&src[k]!=='\n')k++;continue;}
   if(c==='/'&&src[k+1]==='*'){const e=src.indexOf('*/',k+2);if(e<0)throw Error('UNTERMINATED_COMMENT');k=e+2;continue;}
   if(c==='{')depth++;
   if(c==='}'){depth--;if(depth===0)return k+1;}
   k++;
  }
  throw Error('UNTERMINATED_TEMPLATE_EXPRESSION');
 }
 function regexAllowed(){
  const p=out.at(-1);
  if(!p)return true;
  if(p.type==='num'||p.type==='str'||p.type==='template'||p.type==='regex')return false;
  if(p.type==='ident')return false;
  if(p.type==='keyword')return REGEX_AFTER_KEYWORD.has(p.value);
  return !(p.value===')'||p.value===']'||p.value==='}'||p.value==='++'||p.value==='--');
 }
 while(i<src.length){
  const c=src[i];
  if(c==='\n'){line++;i++;continue;}
  if(/\s/.test(c)){i++;continue;}
  if(c==='/'&&src[i+1]==='/'){while(i<src.length&&src[i]!=='\n')i++;continue;}
  if(c==='/'&&src[i+1]==='*'){const e=src.indexOf('*/',i+2);if(e<0)throw Error('UNTERMINATED_COMMENT');countLines(i,e+2);i=e+2;continue;}
  if(c==='#'&&i===0&&src[1]==='!'){while(i<src.length&&src[i]!=='\n')i++;continue;}
  const start=i,startLine=line;
  if(c==="'"||c==='"'){i=skipString(c,i);push('str',start,i);continue;}
  if(c==='`'){i=skipTemplate(i);out.push({type:'template',value:src.slice(start,i),start,end:i,line:startLine});countLines(start,i);continue;}
  if(/[0-9]/.test(c)||(c==='.'&&/[0-9]/.test(src[i+1]??''))){
   const m=/^(0[xXoObB][0-9a-fA-F_]+n?|(?:\d[\d_]*)?\.?\d[\d_]*(?:[eE][+-]?\d+)?n?)/.exec(src.slice(i));
   i+=m[0].length;push('num',start,i);continue;
  }
  if(/[A-Za-z_$\u00c0-\uffff]/.test(c)){
   const m=/^[A-Za-z_$\u00c0-\uffff][\w$\u00c0-\uffff]*/.exec(src.slice(i));
   i+=m[0].length;push(KEYWORDS.has(m[0])?'keyword':'ident',start,i);continue;
  }
  if(c==='/'&&regexAllowed()){
   let k=i+1,inClass=false;
   while(k<src.length){const d=src[k];if(d==='\\'){k+=2;continue;}if(d==='\n')throw Error('UNTERMINATED_REGEX');
    if(d==='[')inClass=true;else if(d===']')inClass=false;else if(d==='/'&&!inClass)break;k++;}
   k++;while(k<src.length&&/[a-z]/.test(src[k]))k++;
   i=k;push('regex',start,i);continue;
  }
  const p=PUNCT.find(x=>src.startsWith(x,i));
  if(!p)throw Error('UNKNOWN_CHARACTER_AT_'+i);
  i+=p.length;push('punct',start,i);
 }
 return out;
}
