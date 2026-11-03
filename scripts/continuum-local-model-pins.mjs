/** Code-reviewed tuples only. Configuration claims never admit genuine inference. */
const tuple=Object.freeze({
 id:'momega1-qwen25coder15b-q4km-llamacpp-6cb3e672-linux-x64',
 status:'REVIEWED_LOCAL_MODEL_HOST',
 modelRepository:'Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF',
 modelRevision:'f86cb2c1fa58255f8052cc32aeede1b7482d4361',
 modelFilename:'qwen2.5-coder-1.5b-instruct-q4_k_m.gguf',
 modelBytes:1117320768,
 modelSha256:'cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046',
 modelLicense:'Apache-2.0',
 runtimeRepository:'ggml-org/llama.cpp',
 runtimeSourceCommit:'6cb3e672e8d10fd4feab1a048bf2967c40623133',
 runtimeTarget:'llama-completion',
 runtimePlatform:'linux-x64',
 runtimeSha256:'c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a',
 buildEvidenceSha256:'ff9b688bbdcda0cea5dbf36f63313a01f055259f241f07eaeb7776327b05f067',
 independentReviewEvidenceSha256:'88e886df6bd60e953605f49e2768529002c9925430a88b5d1467cf95ee40c9fe'
});
const sevenB=Object.freeze({...tuple,
 id:'momega1-qwen25coder7b-q4km-llamacpp-6cb3e672-linux-x64',
 status:'CANDIDATE_LOCAL_MODEL_HOST',
 modelRepository:'Qwen/Qwen2.5-Coder-7B-Instruct-GGUF',
 modelRevision:'13fb94bfda8c8cf22497dc57b78f391a9acb426a',
 modelFilename:'qwen2.5-coder-7b-instruct-q4_k_m.gguf',
 modelBytes:4683073536,
 modelSha256:'509287f78cb4d4cf6b3843734733b914b2c158e43e22a7f4bf5e963800894d3c',
 independentReviewEvidenceSha256:null
});
export const LOCAL_MODEL_RUNTIME_CANDIDATES=Object.freeze([tuple,sevenB]);
const sevenBProfile=Object.freeze({id:sevenB.id,maxFileBytes:4683073536,
 nativeAddressSpaceBytes:7516192768,maxJobMemoryBytes:8589934592,requiresJobMemoryLimit:true,disableWeightRepacking:true});
function matchesProvenance(pin,p){return !!p&&pin.modelRepository===p.repository&&
 pin.modelRevision===p.revision&&pin.modelFilename===p.filename&&pin.modelLicense===p.license&&
 pin.modelBytes===p.sizeBytes&&pin.modelSha256===p.sha256;}
/** Larger file admission is restricted to this complete owner-approved pin, not an env limit. */
export function getPinnedGgufFileLimit(provenance){return matchesProvenance(sevenB,provenance)?sevenBProfile.maxFileBytes:2147483648;}
/** Resource permission is distinct from independently reviewed genuine-inference admission. */
export function getLocalModelResourceProfile({provenance,runtimeSha256}={}){
 return matchesProvenance(sevenB,provenance)&&runtimeSha256===sevenB.runtimeSha256&&
 sevenB.runtimePlatform===`${process.platform}-${process.arch}`?sevenBProfile:null;
}
const sha=value=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value);
/** Only a reviewed exact binary/weights pair can advance beyond process evidence.
 * No setter, injected resolver, env override or owner boolean exists. */
export function getReviewedLocalModelPair({modelSha256,runtimeSha256}={}){
 if(!sha(modelSha256)||!sha(runtimeSha256))return null;
 return LOCAL_MODEL_RUNTIME_CANDIDATES.find(pin=>pin.status==='REVIEWED_LOCAL_MODEL_HOST'&&
  pin.modelSha256===modelSha256&&pin.runtimeSha256===runtimeSha256&&
  pin.runtimePlatform===`${process.platform}-${process.arch}`&&
  sha(pin.buildEvidenceSha256)&&sha(pin.independentReviewEvidenceSha256))??null;
}
