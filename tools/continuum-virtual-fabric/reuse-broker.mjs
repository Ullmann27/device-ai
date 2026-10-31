/**
 * Continuum Proof-Gated Compute Reuse (PGCR) — isolated research prototype.
 * Consolidates identical, pure, deterministic calculations within one tenant.
 * Does NOT create FLOPS, execute neural inference, authenticate clients, or
 * supply a production receipt verifier. The default verification rejects all.
 */
const ID = /^[A-Za-z0-9._:-]{1,128}$/;
const DIGEST = /^[a-f0-9]{64}$/;
const requireId = (value, label) => {
  if (typeof value !== 'string' || !ID.test(value)) throw new TypeError('Invalid ' + label);
  return value;
};
const requireDigest = (value, label) => {
  if (typeof value !== 'string' || !DIGEST.test(value)) throw new TypeError('Invalid ' + label);
  return value;
};
export class ComputeReuseBroker {
  #inflight = new Map();
  #byId = new Map();
  #cache = new Map();
  #counts = {execute:0, joined:0, hit:0, verified:0, rejected:0, cancelled:0, evicted:0};
  #now;
  #verify;
  #ttl;
  #maxCache;
  #maxActive;
  #maxWaiters;
  constructor({now=()=>Date.now(), verifyReceipt=()=>false, ttlMs=60_000,
               maxCached=256, maxActive=10_000, maxWaiters=500}={}) {
    if (typeof now !== 'function' || typeof verifyReceipt !== 'function') throw new TypeError('Expected functions');
    if (![ttlMs,maxCached,maxActive,maxWaiters].every(x=>Number.isSafeInteger(x) && x>0)) throw new TypeError('Invalid capacity');
    this.#now=now; this.#verify=verifyReceipt; this.#ttl=ttlMs;
    this.#maxCache=maxCached; this.#maxActive=maxActive; this.#maxWaiters=maxWaiters;
  }
  submit({id, tenantId, modelRevision, inputDigest, parametersDigest, policyRevision,
          memoryEpoch=0, taskType='PURE', deterministic=false}) {
    requireId(id,'job id'); requireId(tenantId,'tenant id');
    requireDigest(modelRevision,'model revision');
    requireDigest(inputDigest,'input digest');
    requireDigest(parametersDigest,'parameters digest');
    requireDigest(policyRevision,'policy revision');
    if (!Number.isSafeInteger(memoryEpoch) || memoryEpoch<0) throw new TypeError('Invalid memory epoch');
    if (!['PURE','GENERATIVE'].includes(taskType) || typeof deterministic !== 'boolean') throw new TypeError('Invalid task classification');
    if (this.#byId.has(id)) throw new Error('Duplicate active job');
    if (this.#byId.size>=this.#maxActive) throw new Error('Active job quota exceeded');
    this.#prune();
    // Reusing stochastic generated text is forbidden; this path only
    // coalesces pure deterministic jobs with identical versioned context.
    const reusable=taskType==='PURE' && deterministic===true;
    const base=JSON.stringify([tenantId,modelRevision,inputDigest,parametersDigest,policyRevision,memoryEpoch,taskType]);
    const key=reusable ? base : JSON.stringify([base,id]);
    if (reusable) {
      const cached=this.#cache.get(key);
      if (cached) {
        this.#cache.delete(key); this.#cache.set(key,cached); // LRU touch
        this.#counts.hit++;
        return {action:'CACHE_HIT', jobId:id, result:cached.value};
      }
      const existing=this.#inflight.get(key);
      if (existing) {
        if (existing.waiters.size>=this.#maxWaiters) throw new Error('In-flight waiter quota exceeded');
        existing.waiters.add(id);
        this.#byId.set(id,key);
        this.#counts.joined++;
        return {action:'JOIN',jobId:id,leaderId:existing.leaderId};
      }
    }
    this.#inflight.set(key,{key,leaderId:id,waiters:new Set(),reusable,scope:tenantId,
                             modelRevision,inputDigest,parametersDigest,policyRevision,memoryEpoch,taskType});
    this.#byId.set(id,key);
    this.#counts.execute++;
    return {action:'EXECUTE',jobId:id};
  }
  complete(leaderId,receipt) {
    requireId(leaderId,'leader id');
    const key=this.#byId.get(leaderId);
    const entry=key===undefined?null:this.#inflight.get(key);
    if (!entry || entry.leaderId!==leaderId) throw new Error('Not an active leader');
    if (!receipt || typeof receipt.value !== 'string' || receipt.value.length>1_000_000 ||
        !this.#verify({...entry,waiters:undefined},receipt)) {
      this.#counts.rejected++;
      throw new Error('Receipt not independently verified');
    }
    const deliveredTo=[leaderId,...entry.waiters];
    this.#remove(entry);
    this.#counts.verified++;
    if (entry.reusable) {
      this.#cache.set(key,{value:receipt.value, expiresAt:this.#now()+this.#ttl});
      while (this.#cache.size>this.#maxCache) {
        this.#cache.delete(this.#cache.keys().next().value);
        this.#counts.evicted++;
      }
    }
    return {deliveredTo, result:receipt.value};
  }
  cancel(leaderId) {
    requireId(leaderId,'leader id');
    const key=this.#byId.get(leaderId);
    const entry=key===undefined?null:this.#inflight.get(key);
    if (!entry || entry.leaderId!==leaderId) throw new Error('Not an active leader');
    const cancelled=[leaderId,...entry.waiters];
    this.#remove(entry);
    this.#counts.cancelled++;
    return {cancelled};
  }
  #remove(entry) {
    this.#inflight.delete(entry.key);
    this.#byId.delete(entry.leaderId);
    for(const id of entry.waiters) this.#byId.delete(id);
  }
  #prune() {
    const now=this.#now();
    for(const [key,v] of this.#cache) if (v.expiresAt<=now) this.#cache.delete(key);
  }
  snapshot() {
    this.#prune();
    return {activeRequests:this.#byId.size, inflightCalculations:this.#inflight.size,
            cachedResults:this.#cache.size, ...this.#counts};
  }
}
