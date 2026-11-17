// Keep the authored animation proof while producing an event-driven runtime
// asset with exactly the same mesh, skin, materials and named morph payload.
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
const source=path.resolve(process.argv[2]??'assets/avatar/jarvis-research-v1.glb');
const destination=path.resolve(process.argv[3]??'assets/avatar/jarvis-runtime-v1.glb');
const reportPath=path.resolve(process.argv[4]??'docs/avatar-live/geometry-work/runtime-derivation.json');
const bytes=fs.readFileSync(source), jsonLength=bytes.readUInt32LE(12);
const original=JSON.parse(bytes.subarray(20,20+jsonLength));
const binary=bytes.subarray(28+jsonLength);
const runtime=structuredClone(original);delete runtime.animations;
const used=new Set();
for(const m of runtime.meshes)for(const p of m.primitives){
  used.add(p.indices);Object.values(p.attributes).forEach(i=>used.add(i));
  for(const t of p.targets??[])Object.values(t).forEach(i=>used.add(i));
}
for(const s of runtime.skins)used.add(s.inverseBindMatrices);
const last=Math.max(...used);
if([...used].some(i=>i>last)||[...used].length!==last+1)throw Error('Unexpected non-contiguous geometry accessor layout');
runtime.accessors=runtime.accessors.slice(0,last+1);
const lastView=Math.max(...runtime.accessors.map(a=>a.bufferView));
runtime.bufferViews=runtime.bufferViews.slice(0,lastView+1);
const binaryLength=Math.max(...runtime.bufferViews.map(v=>(v.byteOffset??0)+v.byteLength));
runtime.buffers[0].byteLength=binaryLength;
runtime.asset.generator+=' / event-driven runtime variant';
runtime.extras.animationPolicy='No authored playback; existing AvatarPose events drive controls';
let json=Buffer.from(JSON.stringify(runtime));json=Buffer.concat([json,Buffer.alloc((4-json.length%4)%4,32)]);
let data=binary.subarray(0,binaryLength);data=Buffer.concat([data,Buffer.alloc((4-data.length%4)%4)]);
const header=Buffer.alloc(20);header.writeUInt32LE(0x46546c67,0);header.writeUInt32LE(2,4);header.writeUInt32LE(28+json.length+data.length,8);header.writeUInt32LE(json.length,12);header.writeUInt32LE(0x4e4f534a,16);
const binaryHeader=Buffer.alloc(8);binaryHeader.writeUInt32LE(data.length);binaryHeader.writeUInt32LE(0x004e4942,4);
const result=Buffer.concat([header,json,binaryHeader,data]);fs.writeFileSync(destination,result);
const hash=v=>crypto.createHash('sha256').update(v).digest('hex');
const report={source:path.basename(source),sourceSha256:hash(bytes),runtime:path.basename(destination),runtimeSha256:hash(result),runtimeBytes:result.length,
  geometryBinaryPrefixSha256:hash(binary.subarray(0,binaryLength)),geometryBinaryPrefixBytes:binaryLength,
  meshesIdentical:JSON.stringify(original.meshes)===JSON.stringify(runtime.meshes),skinsIdentical:JSON.stringify(original.skins)===JSON.stringify(runtime.skins),nodesIdentical:JSON.stringify(original.nodes)===JSON.stringify(runtime.nodes),materialsIdentical:JSON.stringify(original.materials)===JSON.stringify(runtime.materials),
  sourceAnimations:original.animations.map(a=>a.name),runtimeAnimationCount:0,method:'Removed animation metadata and only trailing animation accessors, bufferViews and buffer bytes; retained contiguous geometry prefix byte-for-byte.'};
fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
