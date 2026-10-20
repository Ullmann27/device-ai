import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';

const SCHEMA = 'continuum-local-build-lease';

/**
 * One fail-closed, atomic local lease for every worker capable of Gradle work.
 * This protects cooperating processes running with the same OS identity.
 * A retained lock must be inspected manually after a crash; age alone never
 * gives another process permission to start a conflicting build.
 */
export function acquireContinuumBuildLease(checkoutRoot, { stateRoot = path.join(os.homedir(), '.local', 'state', 'device-ai-continuum', 'build-leases') } = {}) {
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
