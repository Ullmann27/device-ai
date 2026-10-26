/** Research-only bounded statement translator. Not an ECMAScript AST parser.
 * Never executes candidate code or grants sandbox/model/merge authority.
 * Unsupported syntax, nonunique anchors and partial edits fail closed.
 */
import vm from 'node:vm';
const ID=/^[A-Za-z_$][A-Za-z0-9_$]*$/;
const TOK=/\s+|\d+(?:\.\d+)?|[A-Za-z_$][A-Za-z0-9_$]*|===|!==|=>|<=|>=|\.\.\.|&&|\|\||[+\-*/%<>!&|^~?:=,.;()[\]{}]/gy;
const RESERVED=new Set(['function','const','let','var','return','if','else','for','while','Math','Number','Array','Object','String','Boolean','true','false','null','undefined','sort','slice','min','max']);
function tokenize(source){
  if(typeof source!=='string'||source.length===0||source.length>16384)throw Error('SOURCE_SIZE');
  if(source.includes('//')||source.includes('/*')||source.includes('*/'))throw Error('UNSUPPORTED_COMMENT');
  const out=[];let i=0;
  while(i<source.length){
    TOK.lastIndex=i;const m=TOK.exec(source);
    if(!m)throw Error('UNSUPPORTED_TOKEN');
    if(!/^\s+$/.test(m[0])){
      out.push({value:m[0],start:i,end:TOK.lastIndex});
      if(out.length>1024)throw Error('TOKEN_BUDGET');
    }
    i=TOK.lastIndex;
  }
  return out;
}
function parse(source){
  const tokens=tokenize(source),v=tokens.map(t=>t.value);
  if(v[0]!=='function'||!ID.test(v[1]||'')||v[2]!=='(')throw Error('FUNCTION_HEADER');
  let depth=0,close=-1;
  for(let i=2;i<v.length;i++){
    if(v[i]==='(')depth++;
    else if(v[i]===')'&&--depth===0){close=i;break;}
  }
  if(close<0||v[close+1]!=='{'||v[v.length-1]!=='}')throw Error('FUNCTION_BODY');
  const params=[];
  for(let i=3;i<close;i++){
    if(i%2===1){if(!ID.test(v[i])||RESERVED.has(v[i]))throw Error('PARAMETER');params.push(v[i]);}
    else if(v[i]!==',')throw Error('PARAMETER_SEPARATOR');
  }
  if(params.length>12||new Set(params).size!==params.length)throw Error('PARAMETER_COUNT');
  const open=close+1;
  // A formal parameter named 'length' is legal, but do not mistake a
  // property name after '.' for a renamed variable in this limited grammar.
  const body=v.slice(open+1,-1);
  if(params.some(p=>body.some((t,i)=>t===p&&body[i-1]==='.')))throw Error('PARAMETER_PROPERTY_COLLISION');
  if(body.some(t=>t==='{'||t==='}'))throw Error('NESTED_BLOCK');
  const forward=new Map([[v[1],'__FN__']]),reverse=new Map([['__FN__',v[1]]]);
  params.forEach((p,i)=>{forward.set(p,'__P'+i+'__');reverse.set('__P'+i+'__',p);});
  let localIndex=0;
  for(let i=open+1;i<v.length-2;i++){
    if(['const','let','var'].includes(v[i])){
      const name=v[i+1];
      if(!ID.test(name||'')||RESERVED.has(name)||forward.has(name))throw Error('LOCAL');
      const alias='__L'+localIndex+++'__';
      if(localIndex>50)throw Error('LOCAL_BUDGET');
      forward.set(name,alias);reverse.set(alias,name);
    }
  }
  const statements=[];
  let start=open+1,paren=0,bracket=0;
  for(let i=open+1;i<v.length-1;i++){
    if(v[i]==='(')paren++;
    else if(v[i]===')')paren--;
    else if(v[i]==='[')bracket++;
    else if(v[i]===']')bracket--;
    if(paren<0||bracket<0)throw Error('BALANCE');
    if(v[i]===';'&&paren===0&&bracket===0){
      if(start===i)throw Error('EMPTY_STATEMENT');
      const chunk=tokens.slice(start,i+1);
      statements.push({tokens:chunk,key:chunk.map(t=>forward.get(t.value)||t.value).join(' '),start:chunk[0].start,end:chunk.at(-1).end});
      if(statements.length>80)throw Error('STATEMENT_BUDGET');
      start=i+1;
    }
  }
  if(start!==v.length-1||paren!==0||bracket!==0||!statements.length)throw Error('UNTERMINATED');
  return {source,statements,forward,reverse};
}
function syntax(source){
  try{new vm.Script('"use strict"; ('+source+');');return true;}catch{return false;}
}
function rewriteStatement(stmt,original,target){
  let out='',last=stmt.start;
  for(const t of stmt.tokens){
    out+=original.source.slice(last,t.start);
    const alias=original.forward.get(t.value);
    if(alias){
      const bound=target.reverse.get(alias);
      if(!bound)throw Error('UNBOUND_PARAMETER');
      out+=bound;
    }else out+=t.value;
    last=t.end;
  }
  return out+original.source.slice(last,stmt.end);
}
export function induceStructuredStatementOperator(taskId,buggyCode,fixedCode){
  if(typeof taskId!=='string'||!/^[-_A-Za-z0-9]{1,64}$/.test(taskId))throw Error('TASK_ID');
  if(!syntax(buggyCode)||!syntax(fixedCode))throw Error('SOURCE_SYNTAX');
  const a=parse(buggyCode),b=parse(fixedCode);
  const before=a.statements.map(s=>s.key),after=b.statements.map(s=>s.key);
  const plans=[];
  if(after.length===before.length+1){
    for(let i=0;i<before.length;i++){
      if(after.filter((_,j)=>j!==i).every((s,k)=>s===before[k])){
        const guard=b.statements[i],values=guard.tokens.map(t=>t.value);
        if(values[0]==='if'&&values.at(-1)===';'&&values.indexOf('return')>values.indexOf(')')&&
            !values.some(v=>['{','}','while','for'].includes(v)))
          plans.push({kind:'STATEMENT_PREPEND_GUARD',anchor:before[i],stmt:guard});
      }
    }
  }else if(after.length===before.length){
    const diffs=before.map((s,i)=>s===after[i]?-1:i).filter(i=>i>=0);
    if(diffs.length===1)plans.push({kind:'STATEMENT_ATOMIC_REWRITE',anchor:before[diffs[0]],stmt:b.statements[diffs[0]]});
  }
  if(plans.length!==1)throw Error('AMBIGUOUS_EDIT');
  const plan=plans[0];
  return Object.freeze({id:'STRUCT_OP_'+taskId,kind:plan.kind,applyToCode(source){
    try{
      if(!syntax(source))throw Error('SOURCE_SYNTAX');
      const target=parse(source),matches=target.statements.filter(s=>s.key===plan.anchor);
      if(matches.length!==1)throw Error('NON_UNIQUE_ANCHOR');
      const match=matches[0],newStatement=rewriteStatement(plan.stmt,b,target);
      const indent=source.slice(source.lastIndexOf('\n',match.start-1)+1,match.start);
      const inserted=plan.kind==='STATEMENT_PREPEND_GUARD'
        ?newStatement+'\n'+(/^[ \t]*$/.test(indent)?indent:''):newStatement;
      const end=plan.kind==='STATEMENT_PREPEND_GUARD'?match.start:match.end;
      const candidate=source.slice(0,match.start)+inserted+source.slice(end);
      if(!syntax(candidate)||candidate===source)throw Error('PATCH_INVALID');
      return Object.freeze({status:'CANDIDATE_UNVERIFIED',code:candidate});
    }catch{return Object.freeze({status:'REJECTED',code:null});}
  }});
}
