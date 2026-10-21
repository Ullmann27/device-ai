/**
 * Device AI Continuum: opt-in, offline-only inference boundary for a localhost
 * llama.cpp server. This module never starts a process, executes model output,
 * writes Git files, mutates memories or opens sockets outside 127.0.0.1.
 * Requires Node.js 20+ and a separately configured bearer secret.
 */
const LOOPBACK = 'http://127.0.0.1:8089';
const MAX_RESPONSE_BYTES = 32_768;
const MAX_FACTS = 12;
const MAX_PATCH_PATHS = 16;
const SHA256 = /^[a-f0-9]{64}$/;
const SAFE_PATH = /^(?!\/)(?!.*(?:^|\/)\.\.(?:\/|$))(?!.*\\)[A-Za-z0-9_.\/-]{1,180}$/;
const FORBIDDEN_PATH = /(^|\/)(?:\.git|\.github|scripts|test|fixtures)(\/|$)|(?:\.jks|\.keystore|\.pem|\.key|\.p12)$|(?:^|\/)(?:gradle\.properties|local\.properties|AndroidManifest\.xml)$/i;

function validateString(value, label, max) {
  if (typeof value !== 'string' || !value.trim() || value.length > max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(value)) {
    throw new TypeError('INVALID_'+label);
  }
  return value.trim();
}

function cleanFacts(facts) {
  if (!Array.isArray(facts) || facts.length > MAX_FACTS) throw new TypeError('INVALID_VERIFIED_FACTS');
  return facts.map((fact) => {
    if (fact?.verified !== true || !['owner','independent_test'].includes(fact.source) ||
        typeof fact.sha256 !== 'string' || !SHA256.test(fact.sha256)) {
      throw new TypeError('UNVERIFIED_FACT_REFUSED');
    }
    return validateString(fact.text, 'FACT_TEXT', 500);
  });
}

function validatePaths(paths) {
  if (!Array.isArray(paths) || paths.length < 1 || paths.length > MAX_PATCH_PATHS) {
    throw new TypeError('INVALID_ALLOWED_PATHS');
  }
  const unique = [...new Set(paths)];
  if (unique.length !== paths.length || unique.some(p => typeof p !== 'string' ||
      !SAFE_PATH.test(p) || FORBIDDEN_PATH.test(p) || !p.includes('/'))) {
    throw new TypeError('UNSAFE_ALLOWED_PATH');
  }
  return unique;
}

async function boundedJsonResponse(res) {
  if (!res.ok) throw new Error('LOCAL_HTTP_'+res.status);
  const declared = res.headers.get('content-length');
  if (declared && Number(declared) > MAX_RESPONSE_BYTES) throw new Error('LOCAL_RESPONSE_TOO_LARGE');
  const reader = res.body?.getReader();
  if (!reader) throw new Error('LOCAL_RESPONSE_EMPTY');
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const {done, value} = await reader.read();
      if (done) break;
      length += value.length;
      if (length > MAX_RESPONSE_BYTES) throw new Error('LOCAL_RESPONSE_TOO_LARGE');
      chunks.push(value);
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
  const joined = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { joined.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder('utf-8', {fatal:true}).decode(joined)); }
  catch { throw new Error('LOCAL_RESPONSE_NOT_JSON'); }
}

/**
 * Query a manually started local model. A verifiedFact is only accepted when
 * an upstream trusted caller explicitly established both provenance and review.
 * A supplied SHA is a reference, not proof that the fact is true.
 */
export async function queryLocalBrain({
  systemInstruction, userPrompt, verifiedFacts = [], mode = 'chat',
  allowedTrackedPaths = [], apiKey, model = 'local', timeoutMs = 25_000,
  fetchImpl = globalThis.fetch
} = {}) {
  if (typeof apiKey !== 'string' || !/^[A-Za-z0-9_-]{32,128}$/.test(apiKey)) {
    throw new TypeError('LOCAL_AUTH_REQUIRED');
  }
  if (typeof fetchImpl !== 'function') throw new TypeError('FETCH_UNAVAILABLE');
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 60_000) {
    throw new TypeError('INVALID_LOCAL_TIMEOUT');
  }
  if (!['chat','patch'].includes(mode)) throw new TypeError('INVALID_LOCAL_MODE');
  const system = validateString(systemInstruction, 'SYSTEM_PROMPT', 3000);
  const user = validateString(userPrompt, 'USER_PROMPT', 4000);
  const modelName = validateString(model, 'MODEL_ID', 128);
  const facts = cleanFacts(verifiedFacts);
  const paths = mode === 'patch' ? validatePaths(allowedTrackedPaths) : [];
  const factBlock = facts.length ? '\nEvidence (provided by a verified caller, not a guarantee of truth):\n' +
    facts.map(f => '- ' + f.replace(/[\r\n]+/g, ' ')).join('\n') : '';
  const patchSchema = mode === 'patch' ? {
    type: 'object', additionalProperties: false,
    properties: {
      file: {type: 'string', enum: paths},
      oldText: {type: 'string', minLength: 1, maxLength: 1600},
      newText: {type: 'string', minLength: 1, maxLength: 1600}
    }, required: ['file', 'oldText', 'newText']
  } : null;
  const body = {
    model: modelName,
    stream: false,
    temperature: mode === 'patch' ? 0.0 : 0.15,
    max_tokens: mode === 'patch' ? 400 : 384,
    messages: [
      {role: 'system', content: system + factBlock +
        (mode === 'patch' ? '\nReturn only the one bounded JSON patch suggestion. Never call tools or execute code.' : '')},
      {role: 'user', content: user}
    ]
  };
  if (patchSchema) body.response_format = {type: 'json_schema', schema: patchSchema};
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetchImpl(LOOPBACK + '/v1/chat/completions', {
      method: 'POST', redirect: 'error', cache: 'no-store', signal: controller.signal,
      headers: {'content-type': 'application/json', 'authorization': 'Bearer ' + apiKey},
      body: JSON.stringify(body)
    });
    const payload = await boundedJsonResponse(res);
    const text = payload?.choices?.[0]?.message?.content;
    if (typeof text !== 'string' || !text.trim() || text.length > 5000) {
      throw new Error('LOCAL_EMPTY_OR_OVERSIZED_OUTPUT');
    }
    if (mode === 'chat') {
      return Object.freeze({ok:true, mode:'LOCAL_LOOPBACK_CHAT', text:text.trim(), trusted:false});
    }
    let patch;
    try {patch = JSON.parse(text);} catch {throw new Error('LOCAL_PATCH_INVALID_JSON');}
    if (!patch || typeof patch !== 'object' || Array.isArray(patch) ||
        Object.keys(patch).sort().join(',') !== 'file,newText,oldText' ||
        !paths.includes(patch.file) ||
        typeof patch.oldText !== 'string' || typeof patch.newText !== 'string' ||
        !patch.oldText || !patch.newText ||
        patch.oldText.length > 1600 || patch.newText.length > 1600 ||
        patch.oldText === patch.newText ||
        /[\u0000]/.test(patch.oldText + patch.newText)) {
      throw new Error('LOCAL_PATCH_SCHEMA_REJECTED');
    }
    return Object.freeze({ok:true, mode:'LOCAL_LOOPBACK_PATCH_SHADOW',
      trusted:false, autoApply:false, patch:Object.freeze(patch)});
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}

/** An unauthenticated /health probe never sends memories, prompts or credentials. */
export async function checkLocalBrain({fetchImpl = globalThis.fetch, timeoutMs = 2000} = {}) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 5000) {
    throw new TypeError('INVALID_HEALTH_TIMEOUT');
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetchImpl(LOOPBACK + '/health', {
      method:'GET', redirect:'error', cache:'no-store', signal:controller.signal
    });
    if (res.status === 503) return {ready:false, status:'MODEL_LOADING'};
    const json = await boundedJsonResponse(res);
    return {ready:res.status===200 && json?.status === 'ok',
      status:json?.status === 'ok' ? 'LOCAL_MODEL_READY' : 'UNVERIFIED_HEALTH'};
  } catch {
    return {ready:false, status:'LOCAL_MODEL_UNAVAILABLE'};
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}
