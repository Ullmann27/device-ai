/** Observable diagnosis of a red Node test run (TAP): the context an agent can actually see. Never the injected
 * fault label. Kinds: RUNTIME_<Error> · LOAD_FAILURE · APP_ERROR · NUM_OFF1 · NUM · BOOL · STRUCT · STR · THROWS ·
 * UNWANTED_THROW · OTHER */
export function diagnoseTap(log) {
 if(typeof log!=='string')throw Error('DIAGNOSE_INPUT');
 const failing=(log.match(/^not ok \d+ - /gm)||[]).length;
 const block=/^not ok \d+ - [^\n]*\n((?:[ \t]+[^\n]*\n?)*)/m.exec(log)?.[1]??'';
 const field=k=>{const m=new RegExp('^\\s+'+k+':\\s*(.*)$','m').exec(block);return m?m[1].trim().replace(/^['"]|['"]$/g,''):null;};
 const name=field('name'),code=field('code'),operator=field('operator'),expected=field('expected'),actual=field('actual');
 let kind='OTHER';
 if(name&&/^(TypeError|ReferenceError|RangeError|SyntaxError)$/.test(name))kind='RUNTIME_'+name;
 else if(code==='ERR_TEST_FAILURE'&&field('error')==='test failed'&&/^\s+exitCode:/m.test(block))kind='LOAD_FAILURE';   // whole file red
 else if(/Missing expected (exception|rejection)/.test(block)||operator==='throws'||operator==='rejects')kind='THROWS';
 else if(/Got unwanted (exception|rejection)/.test(block)||operator==='doesNotThrow')kind='UNWANTED_THROW';
 else if(code==='ERR_TEST_FAILURE')kind='APP_ERROR';   // the module's own validation threw
 else if(code==='ERR_ASSERTION'||name==='AssertionError'){
  const num=v=>v!==null&&/^-?\d+(\.\d+)?$/.test(v);
  if(num(expected)&&num(actual))kind=Math.abs(Number(expected)-Number(actual))===1?'NUM_OFF1':'NUM';
  else if(['true','false'].includes(expected)&&['true','false'].includes(actual))kind='BOOL';
  else if(operator&&/deep/i.test(operator))kind='STRUCT';
  else if(expected!==null||actual!==null)kind='STR';
 }
 return Object.freeze({kind,failingBucket:failing<=1?'1':failing<=3?'2-3':'4+',failing});
}
