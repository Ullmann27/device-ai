/**
 * Conservative, nonexecuting Kotlin candidate preflight for the repair worker.
 * This guard catches only certain malformed candidates. A pass NEVER replaces
 * kotlinc, Gradle, Android lint or independent tests.
 */
import {tokenize} from "./kotlin-tokenizer.mjs";

const MAX_SOURCE=750_000;
const MAX_FILES=3;
const CLOSING=Object.freeze({")":"(","]":"[","}":"{"});
const OPENING=new Set(["(","[","{"]);
const IDENTIFIER=/^[A-Za-z0-9_./-]+\.(?:kt|kts)$/;

function literalTerminatorError(text){
 let i=0;
 const n=text.length;
 while(i<n){
  if(text.startsWith("//",i)){
   const next=text.indexOf("\n",i+2);
   i=next<0?n:next+1;
   continue;
  }
  if(text.startsWith("/*",i)){
   let depth=1;i+=2;
   while(i<n&&depth>0){
    if(text.startsWith("/*",i)){depth++;i+=2;}
    else if(text.startsWith("*/",i)){depth--;i+=2;}
    else i++;
   }
   if(depth!==0)return "UNTERMINATED_BLOCK_COMMENT";
   continue;
  }
  if(text.startsWith('"""',i)){
   i+=3;
   let closed=false;
   while(i<n){
    if(text.startsWith('"""',i)){
     let e=i+3;
     while(text[e]==='"')e++;
     i=e;closed=true;break;
    }
    i++;
   }
   if(!closed)return "UNTERMINATED_RAW_STRING";
   continue;
  }
  const ch=text[i];
  if(ch==='"'||ch==="'"){
   const quote=ch;
   i++;
   let closed=false;
   while(i<n){
    if(text[i]==="\n"||text[i]==="\r")break;
    if(text[i]==="\\"){i+=2;continue;}
    if(text[i]===quote){closed=true;i++;break;}
    i++;
   }
   if(!closed)return quote==='"'?"UNTERMINATED_STRING":"UNTERMINATED_CHAR_LITERAL";
   continue;
  }
  if(ch===String.fromCharCode(96)){
   i++;
   const end=text.indexOf(String.fromCharCode(96),i);
   const newline=text.indexOf("\n",i);
   if(end<0||(newline>=0&&newline<end))return "UNTERMINATED_ESCAPED_IDENTIFIER";
   i=end+1;continue;
  }
  i++;
 }
 return null;
}

function checkOne(source){
 if(typeof source!=="string"||!source.trim()||source.length>MAX_SOURCE)
  return "EMPTY_OR_OVERSIZED_KOTLIN_SOURCE";
 if(/[\u0000-\u0008\u000b\u000e-\u001f\u007f]/.test(source))
  return "KOTLIN_CONTROL_CHARACTER";
 const literal=literalTerminatorError(source);
 if(literal)return literal;
 let tokens;
 try {tokens=tokenize(source,{maxTokens:100_000});}
 catch {return "KOTLIN_TOKENIZER_LIMIT";}
 const stack=[];
 for(const token of tokens){
  if(token.type!=="op")continue;
  if(OPENING.has(token.text)){stack.push({symbol:token.text,line:token.line});continue;}
  const open=CLOSING[token.text];
  if(open!==undefined){
   const prior=stack.pop();
   if(!prior||prior.symbol!==open)return "KOTLIN_UNBALANCED_DELIMITER_AT_LINE_"+token.line;
  }
 }
 if(stack.length)return "KOTLIN_UNCLOSED_DELIMITER_AT_LINE_"+stack.at(-1).line;
 return null;
}

export function checkCandidateKotlinSyntax(fileMap){
 if(!fileMap||typeof fileMap!=="object"||Array.isArray(fileMap))
  throw Error("INVALID_KOTLIN_SYNTAX_REQUEST");
 const entries=Object.entries(fileMap);
 if(entries.length>MAX_FILES)throw Error("TOO_MANY_KOTLIN_CANDIDATES");
 const checked=[];
 for(const [file,source] of entries){
  if(!/\.(?:kt|kts)$/.test(file))continue;
  if(!IDENTIFIER.test(file)||file.includes("..")||file.startsWith("/"))
   throw Error("UNSAFE_KOTLIN_PATH");
  const reason=checkOne(source);
  if(reason)return {passed:false,checked,failedFile:file,reason};
  checked.push(file);
 }
 return {passed:true,checked,failedFile:null,reason:null};
}
