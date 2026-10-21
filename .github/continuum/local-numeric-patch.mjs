/** Restricted candidate language, not an OS sandbox or arbitrary JS validator. */
const IDENT=/^[A-Za-z_][A-Za-z0-9_]{0,40}$/;
const FUNCTION=/^\s*export\s+function\s+([A-Za-z_][A-Za-z0-9_]{0,40})\s*\(([^()]*)\)\s*\{\s*return\s+([^;{}]+);\s*\}\s*$/;
const PRECEDENCE=new Map([['||',1],['&&',2],['===',3],['!==',3],['<',4],['>',4],['<=',4],['>=',4],['+',5],['-',5],['*',6],['/',6],['%',6]]);
function inspect(source) {
  if(typeof source!=='string'||source.length>1600)return null;
  const match=FUNCTION.exec(source);if(!match)return null;
  const parameters=match[2].trim()?match[2].split(',').map(x=>x.trim()):[];
  if(parameters.length>4||new Set(parameters).size!==parameters.length||parameters.some(x=>!IDENT.test(x)))return null;
  const expression=match[3],tokens=[];
  if(/\+\+|--/.test(expression))return null;
  const lexer=/\s*(?:([0-9]+(?:\.[0-9]+)?)|([A-Za-z_][A-Za-z0-9_]*)|(===|!==|<=|>=|&&|\|\||[()+\-*/%<>!?:]))/y;
  let offset=0;
  while(offset<expression.length){
    if(!expression.slice(offset).trim())break;
    lexer.lastIndex=offset;const token=lexer.exec(expression);
    if(!token||tokens.length>=96)return null;
    offset=lexer.lastIndex;
    if(token[1]&&!Number.isFinite(Number(token[1])))return null;
    if(token[2]&&!parameters.includes(token[2]))return null;
    tokens.push({value:token[1]??token[2]??token[3],atom:Boolean(token[1]||token[2])});
  }
  let at=0;
  function expressionAt(min=0,depth=0){
    if(depth>16)throw Error('DEPTH');
    const token=tokens[at++];if(!token)throw Error('ATOM');
    if(['+','-','!'].includes(token.value))expressionAt(7,depth+1);
    else if(token.value==='('){expressionAt(0,depth+1);if(tokens[at++]?.value!==')')throw Error('PAREN');}
    else if(!token.atom)throw Error('ATOM');
    while(at<tokens.length){
      const precedence=PRECEDENCE.get(tokens[at].value);
      if(precedence===undefined||precedence<min)break;
      at++;expressionAt(precedence+1,depth+1);
    }
    if(min===0&&tokens[at]?.value==='?'){
      at++;expressionAt(0,depth+1);
      if(tokens[at++]?.value!==':')throw Error('TERNARY');
      expressionAt(0,depth+1);
    }
  }
  try{expressionAt();if(at!==tokens.length)return null;}catch{return null;}
  return {name:match[1],parameters};
}
export function isLocalNumericPatch(edit) {
  if(!/\.(?:mjs|js)$/.test(edit?.file??''))return false;
  const before=inspect(edit.oldText),after=inspect(edit.newText);
  return Boolean(before&&after&&before.name===after.name&&
    JSON.stringify(before.parameters)===JSON.stringify(after.parameters));
}
