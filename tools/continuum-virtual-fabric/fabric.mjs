/**
 * Continuum Virtual Fabric — deterministic scheduler prototype.
 * Does not create computation, run a model, meter billable work or verify real hardware.
 * Node manifests and tenant identities are trusted fixtures only; production must attest them.
 */
const ID = /^[A-Za-z0-9_.:-]{1,128}$/;
const positiveInt = n => Number.isSafeInteger(n) && n > 0;
const nonnegativeInt = n => Number.isSafeInteger(n) && n >= 0;
const safeId = (s, label) => {
  if (typeof s !== 'string' || !ID.test(s)) throw new TypeError('Invalid ' + label);
  return s;
};
const assert = (condition, message) => {
  if (!condition) throw new TypeError(message);
};
const copy = value => structuredClone(value);

export class VirtualFabric {
  #nodes = new Map();
  #models = new Map();
  #tenants = new Map();
  #jobs = new Map();
  #queue = [];
  #now;
  #maxQueue;

  constructor({ now = () => Date.now(), maxQueue = 10_000 } = {}) {
    assert(typeof now === 'function', 'now must be a function');
    assert(positiveInt(maxQueue), 'maxQueue must be a positive integer');
    this.#now = now;
    this.#maxQueue = maxQueue;
  }

  registerNode({ id, cpuSlots, gpuSlots, ramMb, vramMb, commercialAllowed = false, expiresAtMs }) {
    safeId(id, 'node id');
    assert(!this.#nodes.has(id), 'Duplicate node');
    assert(positiveInt(cpuSlots) && nonnegativeInt(gpuSlots), 'Invalid compute capacity');
    assert(positiveInt(ramMb) && nonnegativeInt(vramMb), 'Invalid memory capacity');
    assert(typeof commercialAllowed === 'boolean', 'Invalid commercial allowance');
    assert(Number.isSafeInteger(expiresAtMs) && expiresAtMs > this.#now(), 'Node lease must be valid');
    const capacity = { cpuSlots, gpuSlots, ramMb, vramMb };
    this.#nodes.set(id, { id, capacity, used: { cpuSlots: 0, gpuSlots: 0, ramMb: 0, vramMb: 0 }, commercialAllowed, expiresAtMs });
    this.pump();
  }

  registerTenant({ id, maxRunning = 1, maxQueued = 100 }) {
    safeId(id, 'tenant id');
    assert(!this.#tenants.has(id), 'Duplicate tenant');
    assert(positiveInt(maxRunning) && nonnegativeInt(maxQueued), 'Invalid tenant quota');
    this.#tenants.set(id, { maxRunning, maxQueued });
  }

  registerModel({ id, cpuSlots = 1, gpuSlots = 0, ramMb, vramMb = 0 }) {
    safeId(id, 'model id');
    assert(!this.#models.has(id), 'Duplicate virtual model');
    assert(positiveInt(cpuSlots) && nonnegativeInt(gpuSlots), 'Invalid model slots');
    assert(positiveInt(ramMb) && nonnegativeInt(vramMb), 'Invalid model memory');
    assert(gpuSlots > 0 || vramMb === 0, 'VRAM requested without a GPU');
    this.#models.set(id, { id, resources: { cpuSlots, gpuSlots, ramMb, vramMb } });
  }

  submit({ id, tenantId, modelId, maxRuntimeMs, commercial = false }) {
    safeId(id, 'job id');
    safeId(tenantId, 'tenant id');
    safeId(modelId, 'model id');
    assert(!this.#jobs.has(id), 'Duplicate job');
    assert(this.#tenants.has(tenantId), 'Unknown tenant');
    assert(this.#models.has(modelId), 'Unknown model');
    assert(positiveInt(maxRuntimeMs) && maxRuntimeMs <= 3_600_000, 'Invalid runtime bound');
    assert(typeof commercial === 'boolean', 'Invalid commercial flag');
    const tenant = this.#tenants.get(tenantId);
    const tenantQueue = this.#queue.filter(jobId => this.#jobs.get(jobId).tenantId === tenantId).length;
    assert(tenantQueue < tenant.maxQueued, 'Tenant queue limit');
    assert(this.#queue.length < this.#maxQueue, 'Global queue limit');
    const job = {
      id, tenantId, modelId, maxRuntimeMs, commercial,
      status: 'QUEUED', nodeId: null,
      submittedAtMs: this.#now(), startedAtMs: null, finishedAtMs: null
    };
    this.#jobs.set(id, job);
    this.#queue.push(id);
    this.pump();
    return copy(job);
  }

  pump() {
    const now = this.#now();
    // Cooperative reclamation; physical workers must enforce their own hard deadlines.
    for (const job of this.#jobs.values()) {
      if (job.status !== 'RUNNING') continue;
      const node = this.#nodes.get(job.nodeId);
      if (now < job.startedAtMs + job.maxRuntimeMs && node && now < node.expiresAtMs) continue;
      if (node) this.#release(job);
      job.status = 'CANCELLED';
      job.finishedAtMs = now;
    }
    for (const id of [...this.#queue]) {
      const job = this.#jobs.get(id);
      const tenant = this.#tenants.get(job.tenantId);
      const running = [...this.#jobs.values()].filter(j => j.status === 'RUNNING' && j.tenantId === job.tenantId).length;
      if (running >= tenant.maxRunning) continue;
      const model = this.#models.get(job.modelId);
      const node = [...this.#nodes.values()].sort((a, b) => a.id.localeCompare(b.id)).find(n => {
        if (job.commercial && !n.commercialAllowed) return false;
        if (n.expiresAtMs - now < job.maxRuntimeMs) return false;
        return Object.keys(model.resources).every(key => model.resources[key] <= n.capacity[key] - n.used[key]);
      });
      if (!node) continue;
      for (const key of Object.keys(model.resources)) node.used[key] += model.resources[key];
      job.status = 'RUNNING';
      job.nodeId = node.id;
      job.startedAtMs = now;
      this.#queue.splice(this.#queue.indexOf(id), 1);
    }
    return this.snapshot();
  }

  complete(id) {
    this.pump();
    const job = this.#jobs.get(safeId(id, 'job id'));
    assert(job?.status === 'RUNNING', 'Only running jobs can complete');
    this.#release(job);
    job.status = 'COMPLETED';
    job.finishedAtMs = this.#now();
    this.pump();
    return copy(job);
  }

  cancel(id) {
    const job = this.#jobs.get(safeId(id, 'job id'));
    assert(job && (job.status === 'QUEUED' || job.status === 'RUNNING'), 'Job is not cancellable');
    if (job.status === 'RUNNING') this.#release(job);
    if (job.status === 'QUEUED') this.#queue.splice(this.#queue.indexOf(id), 1);
    job.status = 'CANCELLED';
    job.finishedAtMs = this.#now();
    this.pump();
    return copy(job);
  }

  revokeNode(id) {
    const nodeId = safeId(id, 'node id');
    assert(this.#nodes.has(nodeId), 'Unknown node');
    for (const job of this.#jobs.values()) {
      if (job.nodeId === nodeId && job.status === 'RUNNING') {
        this.#release(job);
        job.status = 'CANCELLED';
        job.finishedAtMs = this.#now();
      }
    }
    this.#nodes.delete(nodeId);
    this.pump();
  }

  #release(job) {
    const node = this.#nodes.get(job.nodeId);
    assert(node, 'Allocated node missing');
    const model = this.#models.get(job.modelId);
    for (const key of Object.keys(model.resources)) {
      node.used[key] -= model.resources[key];
      assert(node.used[key] >= 0, 'Negative resource accounting');
    }
  }

  getJob(id) {
    return copy(this.#jobs.get(safeId(id, 'job id')) ?? null);
  }

  snapshot() {
    return copy({
      nodes: [...this.#nodes.values()],
      logicalModels: this.#models.size,
      running: [...this.#jobs.values()].filter(j => j.status === 'RUNNING').length,
      queued: this.#queue.length,
      completed: [...this.#jobs.values()].filter(j => j.status === 'COMPLETED').length,
      cancelled: [...this.#jobs.values()].filter(j => j.status === 'CANCELLED').length
    });
  }
}
