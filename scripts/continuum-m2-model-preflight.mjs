/**
 * Owner-selected coding-model transport gate. The imported original Gemma 4
 * E4B model is a LiteRT-LM file; a llama.cpp loopback cannot load it by changing
 * the model name. Never silently substitute Qwen or assert a model ran.
 *
 * Pure decision function; no network, filesystem, Git, model calls or secrets.
 */
export const OWNER_GEMMA_ID = 'gemma4-e4b-it';
export const OWNER_GEMMA_SHA256 = '0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0';
const hex64=/^[a-f0-9]{64}$/;
export const CODING_TRANSPORT = 'llama.cpp';
export const OWNER_ALTERNATE_CONSENT = 'OWNER_APPROVED_ALTERNATE_GGUF';

function refusal(code, detail) {
  return Object.freeze({ready:false, status:code,detail,
    actualInference:false,ownerGemmaExecuted:false,verifiedRepair:false});
}

/**
 * This is only a transport-admission check, NOT model-file verification.
 * A GGUF model name/hash is a configuration claim; an actual model must still
 * be loaded and tested by a separate run with receipts.
 */
export function inspectCodingModelPreflight({
  model, runtime='llama.cpp', format, sha256, alternateConsent
}={}) {
  if(typeof model!=='string'||!model.trim()||model.length>128||
     /[\x00-\x1f\x7f]/.test(model)) return refusal('MODEL_ID_REQUIRED','Explicit checked local model name required');
  const id=model.trim();
  if(runtime!=='llama.cpp') return refusal('UNSUPPORTED_CODING_TRANSPORT','Existing coding adapter uses local llama.cpp only');
  if(id===OWNER_GEMMA_ID||/\.litertlm$/i.test(id)||format==='litertlm')
    return refusal('GEMMA_LITERTLM_NOT_GGUF','Original selected Gemma requires LiteRT-LM, not the llama.cpp coding adapter');
  if(format!=='gguf')return refusal('EXPLICIT_GGUF_FORMAT_REQUIRED','Configured model format must be gguf');
  if(typeof sha256!=='string'||!hex64.test(sha256))
    return refusal('EXPLICIT_GGUF_DIGEST_REQUIRED','Supply recorded GGUF SHA-256; name alone is insufficient');
  if(sha256===OWNER_GEMMA_SHA256)
    return refusal('GEMMA_DIGEST_FORMAT_MISMATCH','Original Gemma LiteRT-LM digest cannot be used as GGUF proof');
  if(alternateConsent!==OWNER_ALTERNATE_CONSENT)
    return refusal('ALTERNATE_MODEL_CONSENT_REQUIRED','Any GGUF coding model needs explicit owner consent; no Qwen fallback');
  return Object.freeze({ready:true,status:'CONFIGURATION_ONLY_PENDING_RUNTIME_PROOF',
    configuredModel:id,configuredSha256:sha256,configuredFormat:'gguf',
    actualInference:false,ownerGemmaExecuted:false,verifiedRepair:false});
}

/** Fail before the request is transmitted; not a permission or runtime proof. */
export function requireCodingModelPreflight(config){
  const report=inspectCodingModelPreflight(config);
  if(!report.ready){
    const error=new Error(report.status);
    error.code=report.status;
    throw error;
  }
  return report;
}
