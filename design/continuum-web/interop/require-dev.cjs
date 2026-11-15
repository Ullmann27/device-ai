'use strict';
const path = require('node:path');

/** Prefer this package's npm-ci installation; the runtime fallback is opt-in. */
module.exports = function requireDev(name, runtimeName = name) {
  try {
    return require(name);
  } catch (error) {
    const missingRequestedModule = error.code === 'MODULE_NOT_FOUND' && error.message.includes(`'${name}'`);
    if (!missingRequestedModule) throw error;
    const runtimeModules = process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES;
    if (runtimeModules) return require(path.join(runtimeModules, runtimeName));
    error.message += '\nInstall the pinned development tools with: npm ci --prefix interop';
    throw error;
  }
};
