/** Code-reviewed tuples only. Configuration claims never admit genuine inference. */
const tuple=Object.freeze({
 id:'momega1-qwen25coder15b-q4km-llamacpp-6cb3e672-linux-x64',
 status:'CANDIDATE_INDEPENDENT_REVIEW_REQUIRED',
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
 runtimeSha256:null,
 buildEvidenceSha256:null,
 independentReviewEvidenceSha256:null
});
export const LOCAL_MODEL_RUNTIME_CANDIDATES=Object.freeze([tuple]);
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
