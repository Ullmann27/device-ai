import fs from 'node:fs';
import path from 'node:path';

/** Resolve an intended state directory even before its final directories exist.
 * Existing ancestors are canonicalized, so a symlink cannot disguise a path
 * inside the checked-out source. This is a preflight; the worker must still
 * own and protect its state directory against concurrent filesystem changes.
 */
export function canonicalProspectiveState(stateDir) {
  if (typeof stateDir !== 'string' || !stateDir.trim() || stateDir.includes('\0')) {
    throw new Error('CCC1_STATE_PATH_INVALID');
  }
  const absolute = path.resolve(stateDir);
  let current = absolute;
  const missing = [];
  for (;;) {
    try {
      fs.lstatSync(current);
      const actual = fs.realpathSync(current);
      if (!fs.statSync(actual).isDirectory()) {
        throw new Error('CCC1_STATE_NOT_DIRECTORY');
      }
      return path.resolve(actual, ...missing.reverse());
    } catch (error) {
      if (error?.code === 'ENOTDIR') throw new Error('CCC1_STATE_NOT_DIRECTORY');
      if (error?.code !== 'ENOENT') throw error;
    }
    const parent = path.dirname(current);
    if (parent === current) throw new Error('CCC1_STATE_PATH_INVALID');
    missing.push(path.basename(current));
    current = parent;
  }
}

export function ensureCcc1StateOutsideSource(project, stateDir) {
  const source = fs.realpathSync(project);
  if (!fs.statSync(source).isDirectory()) throw new Error('CCC1_PROJECT_NOT_DIRECTORY');
  const state = canonicalProspectiveState(stateDir);
  if (state === source || state.startsWith(source + path.sep)) {
    throw new Error('CCC1_STATE_INSIDE_SOURCE');
  }
  return state;
}
