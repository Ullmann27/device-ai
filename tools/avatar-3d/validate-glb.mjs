import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import validator from 'gltf-validator';

const filename = path.resolve(process.argv[2] ?? '../../assets/avatar/jarvis-research-v1.glb');
const output = path.resolve(process.argv[3] ?? '../../docs/avatar-live/geometry-work');
fs.mkdirSync(output,{recursive:true});
const runtimeMode=process.argv.includes('--runtime');
const bytes = fs.readFileSync(filename);
const result = await validator.validateBytes(new Uint8Array(bytes), {uri: path.basename(filename), maxIssues: 1000});
fs.writeFileSync(path.join(output, 'khronos-validator.json'), JSON.stringify(result, null, 2) + '\n');
const jsonLength = bytes.readUInt32LE(12);
const gltf = JSON.parse(bytes.subarray(20, 20 + jsonLength).toString());
const binary = bytes.subarray(20 + jsonLength + 8);
function read(index) {
  const a = gltf.accessors[index], v = gltf.bufferViews[a.bufferView];
  const width = {SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16}[a.type];
  const size = {5126:4,5125:4,5123:2}[a.componentType];
  const fn = {5126:'readFloatLE',5125:'readUInt32LE',5123:'readUInt16LE'}[a.componentType];
  return Array.from({length:a.count}, (_, i) => Array.from({length:width}, (_, j) => binary[fn]((v.byteOffset??0)+(a.byteOffset??0)+i*(v.byteStride??width*size)+j*size)));
}
const required = 'SIL PP FF TH DD KK CH SS NN RR AA E I O U BlinkLeft BlinkRight BrowUp BrowDown Smile JawOpen'.split(' ');
const checks = [];
const assert = (name, pass, details) => checks.push({name, pass, details});
assert('glb-header-size', bytes.readUInt32LE(0)===0x46546c67 && bytes.readUInt32LE(8)===bytes.length);
assert('khronos-zero-errors', result.issues.numErrors===0, result.issues);
for (const name of ['AvatarRoot','Neck','Head','EyeLeft','EyeRight','Jaw','Face','NeuralThreads']) assert('node-'+name, gltf.nodes.some(n=>n.name===name));
const morphStats = {};
for (const name of ['Face','NeuralThreads']) {
  const node = gltf.nodes.find(n=>n.name===name), mesh=gltf.meshes[node.mesh];
  assert(name+'-morph-names', JSON.stringify(mesh.extras.targetNames)===JSON.stringify(required));
  assert(name+'-all-primitives-morphed',mesh.primitives.every(p=>p.targets.length===21));
  const primitive=mesh.primitives[0], positions=read(primitive.attributes.POSITION);
  const weights=read(primitive.attributes.WEIGHTS_0);
  assert(name+'-normalized-skin',weights.every(row=>Math.abs(row.reduce((a,b)=>a+b,0)-1)<1e-5));
  morphStats[name] = required.map((n,i)=>{
    const deltas=read(primitive.targets[i].POSITION), magnitudes=deltas.map(v=>Math.hypot(...v));
    let changed=0, positiveX=0, negativeX=0;
    for(let j=0;j<magnitudes.length;j++) if(magnitudes[j]>1e-7){changed++;if(positions[j][0]>0)positiveX++;else if(positions[j][0]<0)negativeX++;}
    const item={name:n,changedVertices:changed,maxDeltaMetres:Math.max(...magnitudes),positiveX,negativeX};
    assert(name+'-'+n+'-nonzero',changed>0,item); return item;
  });
  const signatures=primitive.targets.map(t=>crypto.createHash('sha256').update(JSON.stringify(read(t.POSITION))).digest('hex'));
  assert(name+'-unique-target-deltas', new Set(signatures).size===21);
}
if(runtimeMode)assert('event-driven-no-authored-animation',!gltf.animations?.length);
else assert('independent-node-animation', ['Neck','Head','EyeLeft','EyeRight','Jaw'].every(name=>gltf.animations.some(a=>a.channels.some(c=>gltf.nodes[c.target.node].name===name&&c.target.path==='rotation'))));
assert('real-skin',gltf.skins.length>0&&gltf.skins[0].joints.length===3);
const report={asset:filename,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),validatorVersion:validator.version(),runtimeMode,checks,passed:checks.filter(c=>c.pass).length,failed:checks.filter(c=>!c.pass).length,morphStats,animations:(gltf.animations??[]).map(a=>({name:a.name,channels:a.channels.length})),limits:['Structural validity and nonzero targets do not certify phonetic accuracy, visual identity, collision-free blending or phone performance.']};
fs.writeFileSync(path.join(output,'structural-validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({sha256:report.sha256,passed:report.passed,failed:report.failed,khronosErrors:result.issues.numErrors,khronosWarnings:result.issues.numWarnings}));
if(report.failed)process.exitCode=1;
