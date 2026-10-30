/** Diagnosis aid for red JUnit/Node output: which failures carry a concrete HOST signature (encoding, network,
 * disk, memory, timeout) and which must be treated as code or test failures. Conservative by construction:
 * without a matching signature the verdict stays CODE_OR_TEST_FAILURE. A host verdict is a hypothesis to re-run
 * under a corrected environment, never a pass, never a reason to patch code and never a learning label.
 * Usage: node .github/continuum/test-failure-environment.mjs <junit.txt>   (prints JSON, exit 0) */
import fs from 'node:fs';
import path from 'node:path';

const SIGNATURES=Object.freeze([
 ['NETWORK_BLOCKED',/UnknownHostException|ConnectException|CONNECT tunnel failed|403 Forbidden|Could not resolve host|ENOTFOUND|ECONNREFUSED/],
 ['DISK_FULL',/No space left on device|ENOSPC/],
 ['MEMORY',/OutOfMemoryError|ENOMEM/],
 ['TIMEOUT',/TestTimedOutException|test timed out after|ETIMEDOUT/]
]);

/** A comparison whose printed expected and actual are identical but contain '?' lost its difference in a non-UTF-8
 * console/process encoding (the real strings differ only in characters the host could not encode). */
function encodingArtefact(text) {
 const m=/expected:<(.*)> but was:<(.*)>/.exec(text);
 return !!m&&m[1]===m[2]&&m[1].includes('?');
}

export function classifyJUnitFailures(output) {
 if(typeof output!=='string'||output.length>5_000_000)throw Error('INVALID_TEST_OUTPUT');
 const lines=output.split('\n'),failures=[];
 for(let i=0;i<lines.length;i++){
  const head=/^(\d+)\) (\S+)\(([\w.$]+)\)$/.exec(lines[i]);
  if(!head)continue;
  const body=[];
  for(let j=i+1;j<lines.length&&!/^\d+\) \S+\(/.test(lines[j])&&!/^(FAILURES!!!|Tests run:)/.test(lines[j]);j++)body.push(lines[j]);
  const text=body.slice(0,40).join('\n'),reasons=[];
  if(encodingArtefact(text))reasons.push('ENCODING_ARTEFACT');
  for(const [code,re] of SIGNATURES)if(re.test(text))reasons.push(code);
  failures.push({test:head[3]+'.'+head[2],verdict:reasons.length?'HOST_ENVIRONMENT_SUSPECTED':'CODE_OR_TEST_FAILURE',reasons});
 }
 const hints=[];
 if(/^Picked up JAVA_TOOL_OPTIONS/m.test(output))hints.push('JAVA_TOOL_OPTIONS_BANNER: child JVMs print it on stderr; tests that read child stderr may misread it');
 return Object.freeze({schema:1,failures,hints,
  hostSuspected:failures.filter(f=>f.verdict==='HOST_ENVIRONMENT_SUSPECTED').length,
  codeOrTest:failures.filter(f=>f.verdict==='CODE_OR_TEST_FAILURE').length,
  passGranted:false,learningLabel:null});
}

if(process.argv[1]&&path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname)){
 try{
  const file=process.argv[2];
  if(process.argv.length!==3)throw Error('USAGE: node test-failure-environment.mjs <junit.txt>');
  const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW|fs.constants.O_NONBLOCK);
  let text;try{const st=fs.fstatSync(fd);if(!st.isFile()||st.size>5_000_000)throw Error('INVALID_TEST_OUTPUT');text=fs.readFileSync(fd,'utf8');}
  finally{fs.closeSync(fd);}
  console.log(JSON.stringify(classifyJUnitFailures(text),null,2));
 }catch(e){console.error('TEST_FAILURE_CLASSIFICATION_UNAVAILABLE: '+e.message);process.exitCode=1;}
}
