import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';

const SCHEMA = 'continuum-local-build-lease';

/**
 * One fail-closed, atomic local lease for every worker capable of Gradle work.
 * This protects cooperating processes running with the same OS identity.
 * A retained lock must be inspected manually after a crash; age alone never
 * gives another process permission to start a conflicting build. The owner-approved
 * releaseDeadContinuumBuildLease is that manual step for a holder that provably died.
 */
const defaultStateRoot = () => path.join(os.homedir(), '.local', 'state', 'device-ai-continuum', 'build-leases');
const leaseFile = (checkoutRoot, stateRoot) => {
  const root = fs.existsSync(checkoutRoot) ? fs.realpathSync(checkoutRoot) : path.resolve(checkoutRoot);
  return path.join(fs.realpathSync(stateRoot), crypto.createHash('sha256').update(root).digest('hex') + '.lock');
};

/**
 * Owner-approved release of a lease retained by a crashed worker (e.g. Termux killed by Android mid-build).
 * Age alone still never frees a lease: this runs only after an explicit owner decision, and even then it removes the
 * lock only if it names THIS host and a process that no longer exists. Anything else is refused and left untouched.
 * Returns {released, reason, holder}.
 */
export function releaseDeadContinuumBuildLease(checkoutRoot, { stateRoot = defaultStateRoot() } = {}) {
  if (!fs.existsSync(stateRoot)) return { released: false, reason: 'NO_LEASE', holder: null };
  const lock = leaseFile(checkoutRoot, stateRoot);
  let text, identity;
  try { identity = fs.lstatSync(lock); text = fs.readFileSync(lock, 'utf8'); }
  catch (error) { if (error.code === 'ENOENT') return { released: false, reason: 'NO_LEASE', holder: null }; throw error; }
  if (!identity.isFile() || identity.isSymbolicLink()) return { released: false, reason: 'UNSAFE_LOCK', holder: null };
  let holder;
  try { holder = JSON.parse(text); } catch { return { released: false, reason: 'MALFORMED_LOCK', holder: null }; }
  if (holder?.schema !== SCHEMA || holder.hostname !== os.hostname() || !Number.isSafeInteger(holder.pid) || holder.pid <= 0)
    return { released: false, reason: 'FOREIGN_OR_UNKNOWN_HOLDER', holder };
  try { process.kill(holder.pid, 0); return { released: false, reason: 'HOLDER_ALIVE', holder }; }
  catch (error) { if (error.code !== 'ESRCH') return { released: false, reason: 'HOLDER_ALIVE', holder }; }
  const aside = lock + '.dead-' + crypto.randomBytes(6).toString('hex');
  fs.renameSync(lock, aside);
  const moved = fs.lstatSync(aside);
  if (moved.ino !== identity.ino || moved.dev !== identity.dev || fs.readFileSync(aside, 'utf8') !== text) {
    try { fs.linkSync(aside, lock); } catch { /* a successor holds the lease now */ }
    fs.rmSync(aside, { force: true });
    return { released: false, reason: 'LOCK_CHANGED', holder };
  }
  fs.rmSync(aside, { force: true });
  return { released: true, reason: 'HOLDER_DEAD', holder };
}

export function acquireContinuumBuildLease(checkoutRoot, { stateRoot = defaultStateRoot() } = {}) {
  if (typeof checkoutRoot !== 'string' || !path.isAbsolute(checkoutRoot))
    throw Error('CONTINUUM_BUILD_LEASE_INVALID_CHECKOUT');
  const root = fs.existsSync(checkoutRoot) ? fs.realpathSync(checkoutRoot) : path.resolve(checkoutRoot);
  if (fs.existsSync(root) && !fs.statSync(root).isDirectory())
    throw Error('CONTINUUM_BUILD_LEASE_INVALID_CHECKOUT');
  if (typeof stateRoot !== 'string' || !path.isAbsolute(stateRoot))
    throw Error('CONTINUUM_BUILD_LEASE_INVALID_STATE');
  fs.mkdirSync(stateRoot, { recursive: true, mode: 0o700 });
  if (!fs.lstatSync(stateRoot).isDirectory() || fs.lstatSync(stateRoot).isSymbolicLink())
    throw Error('CONTINUUM_BUILD_LEASE_UNSAFE_STATE');
  const store = fs.realpathSync(stateRoot);
  if (store === root || store.startsWith(root + path.sep))
    throw Error('CONTINUUM_BUILD_LEASE_INSIDE_CHECKOUT');
  const id = crypto.createHash('sha256').update(root).digest('hex');
  const lock = path.join(store, id + '.lock');
  let fd;
  try { fd = fs.openSync(lock, 'wx', 0o600); }
  catch (error) {
    if (error.code === 'EEXIST') throw Error('CONTINUUM_BUILD_LEASE_BUSY');
    throw error;
  }
  const nonce = crypto.randomBytes(16).toString('hex');
  const payload = JSON.stringify({ schema: SCHEMA, version: 1, root, pid: process.pid,
    hostname: os.hostname(), startedAt: Date.now(), nonce }) + '\n';
  let identity;
  try {
    fs.writeFileSync(fd, payload, 'utf8');
    fs.fsyncSync(fd);
    identity = fs.fstatSync(fd);
  } catch (error) {
    fs.closeSync(fd);
    fs.rmSync(lock, { force: true });
    throw error;
  }
  fs.closeSync(fd);
  let released = false;
  return () => {
    if (released) return;
    try {
      const stat = fs.lstatSync(lock);
      if (!stat.isFile() || stat.isSymbolicLink() ||
          stat.ino !== identity.ino || stat.dev !== identity.dev ||
          fs.readFileSync(lock, 'utf8') !== payload)
        throw Error('CONTINUUM_BUILD_LEASE_OWNERSHIP_CHANGED');
      fs.unlinkSync(lock);
      released = true;
    } catch (error) {
      if (error.code === 'ENOENT') throw Error('CONTINUUM_BUILD_LEASE_DISAPPEARED');
      throw error;
    }
  };
}
